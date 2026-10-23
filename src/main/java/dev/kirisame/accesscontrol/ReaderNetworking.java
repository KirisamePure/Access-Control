package dev.kirisame.accesscontrol;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ReaderNetworking {
    private ReaderNetworking() { }
    public static boolean canConfigure(ServerPlayerEntity player) {
        return player.hasPermissionLevel(1) && player.interactionManager.getGameMode() == net.minecraft.world.GameMode.CREATIVE;
    }

    public record OpenConfig(BlockPos pos, Identifier dimension, int level) implements CustomPayload {
        public static final Id<OpenConfig> ID = new Id<>(AccessControl.id("open_config"));
        public static final PacketCodec<RegistryByteBuf, OpenConfig> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenConfig::pos, Identifier.PACKET_CODEC, OpenConfig::dimension,
                PacketCodecs.VAR_INT, OpenConfig::level, OpenConfig::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record SetLevel(BlockPos pos, Identifier dimension, int level) implements CustomPayload {
        public static final Id<SetLevel> ID = new Id<>(AccessControl.id("set_level"));
        public static final PacketCodec<RegistryByteBuf, SetLevel> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, SetLevel::pos, Identifier.PACKET_CODEC, SetLevel::dimension,
                PacketCodecs.VAR_INT, SetLevel::level, SetLevel::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenConfig.ID, OpenConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(SetLevel.ID, SetLevel.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetLevel.ID, (payload, context) -> configure(context.player(), payload));
        BiometricNetworking.register();
    }

    static void configure(ServerPlayerEntity player, SetLevel payload) {
        var world = player.getEntityWorld();
        // Every condition is checked server-side; a modified client cannot bypass clearance.
        if (!canConfigure(player)
                || !AccessRules.validLevel(payload.level())
                || !world.getRegistryKey().getValue().equals(payload.dimension())
                || !world.isInBuildLimit(payload.pos()) || !world.isChunkLoaded(payload.pos())
                || player.squaredDistanceTo(payload.pos().toCenterPos()) > 64) {
            player.sendMessage(Text.translatable("message.accesscontrol.config_rejected").formatted(Formatting.RED), true);
            return;
        }
        BlockState state = world.getBlockState(payload.pos());
        if (!state.isOf(AccessControl.READER) && !state.isOf(AccessControl.SUBWAY_GATE)) {
            player.sendMessage(Text.translatable("message.accesscontrol.config_rejected").formatted(Formatting.RED), true);
            return;
        }
        if (state.isOf(AccessControl.SUBWAY_GATE)) {
            BlockPos center = SubwayGateBlock.center(state, payload.pos());
            AccessControl.SUBWAY_GATE.configure(world, center, payload.level());
        } else {
            world.setBlockState(payload.pos(), state.with(ReaderBlock.REQUIRED_LEVEL, payload.level()), Block.NOTIFY_ALL);
        }
        player.sendMessage(Text.translatable("message.accesscontrol.config_saved", payload.level()).formatted(Formatting.GREEN), true);
    }

    public static void open(ServerPlayerEntity player, BlockPos pos) {
        if (!canConfigure(player)) {
            player.sendMessage(Text.translatable("message.accesscontrol.op_only").formatted(Formatting.RED), true);
            return;
        }
        var world = player.getEntityWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isOf(AccessControl.BIOMETRIC_READER)) {
            BiometricNetworking.open(player, pos);
            return;
        }
        if (state.isOf(AccessControl.READER) || state.isOf(AccessControl.SUBWAY_GATE)) {
            ServerPlayNetworking.send(player, new OpenConfig(pos, world.getRegistryKey().getValue(), state.get(ReaderBlock.REQUIRED_LEVEL)));
        }
    }
}
