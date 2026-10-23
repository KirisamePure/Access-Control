package dev.kirisame.accesscontrol;

import java.util.*;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

public final class BiometricNetworking {
    private BiometricNetworking() { }
    public record OpenConfig(BlockPos pos, Identifier dimension, String whitelist, String online) implements CustomPayload {
        public static final Id<OpenConfig> ID = new Id<>(AccessControl.id("open_biometric"));
        public static final PacketCodec<RegistryByteBuf, OpenConfig> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, OpenConfig::pos, Identifier.PACKET_CODEC, OpenConfig::dimension,
                PacketCodecs.string(8192), OpenConfig::whitelist, PacketCodecs.string(16384), OpenConfig::online, OpenConfig::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record SetWhitelist(BlockPos pos, Identifier dimension, String members) implements CustomPayload {
        public static final Id<SetWhitelist> ID = new Id<>(AccessControl.id("set_biometric_whitelist"));
        public static final PacketCodec<RegistryByteBuf, SetWhitelist> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, SetWhitelist::pos, Identifier.PACKET_CODEC, SetWhitelist::dimension,
                PacketCodecs.string(BiometricWhitelist.MAX_INPUT), SetWhitelist::members, SetWhitelist::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenConfig.ID, OpenConfig.CODEC);
        PayloadTypeRegistry.playC2S().register(SetWhitelist.ID, SetWhitelist.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetWhitelist.ID, (payload, context) -> configure(context.player(), payload));
    }
    private static boolean accessible(ServerPlayerEntity player, BlockPos pos, Identifier dimension) {
        var world = player.getEntityWorld();
        return ReaderNetworking.canConfigure(player) && world.getRegistryKey().getValue().equals(dimension)
                && world.isInBuildLimit(pos) && world.isChunkLoaded(pos) && player.squaredDistanceTo(pos.toCenterPos()) <= 64;
    }
    public static void open(ServerPlayerEntity player, BlockPos pos) {
        var world = player.getEntityWorld();
        if (!accessible(player, pos, world.getRegistryKey().getValue())) { reject(player); return; }
        if (!(world.getBlockEntity(pos) instanceof BiometricReaderBlockEntity reader)) return;
        Map<UUID, String> online = new LinkedHashMap<>();
        world.getServer().getPlayerManager().getPlayerList().stream()
                .sorted(Comparator.comparing(p -> p.getGameProfile().name(), String.CASE_INSENSITIVE_ORDER))
                .limit(128).forEach(p -> online.put(p.getUuid(), p.getGameProfile().name()));
        ServerPlayNetworking.send(player, new OpenConfig(pos, world.getRegistryKey().getValue(), encodeProfiles(reader.players()), encodeProfiles(online)));
    }
    static void configure(ServerPlayerEntity player, SetWhitelist payload) {
        if (!accessible(player, payload.pos(), payload.dimension())) { reject(player); return; }
        var world = player.getEntityWorld();
        if (!(world.getBlockEntity(payload.pos()) instanceof BiometricReaderBlockEntity reader)
                || !world.getBlockState(payload.pos()).isOf(AccessControl.BIOMETRIC_READER)) { reject(player); return; }
        final Set<UUID> ids;
        try { ids = BiometricWhitelist.parse(payload.members()); }
        catch (IllegalArgumentException exception) { reject(player); return; }
        Map<UUID, String> entries = new LinkedHashMap<>();
        for (UUID id : ids) {
            var online = world.getServer().getPlayerManager().getPlayer(id);
            entries.put(id, online != null ? online.getGameProfile().name() : reader.players().getOrDefault(id, id.toString()));
        }
        reader.setPlayers(entries);
        player.sendMessage(Text.translatable("message.accesscontrol.whitelist_saved", entries.size()).formatted(Formatting.GREEN), true);
    }
    private static void reject(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("message.accesscontrol.config_rejected").formatted(Formatting.RED), true);
    }
    public static String encodeProfiles(Map<UUID, String> entries) {
        return entries.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining("\n"));
    }
    public static Map<UUID, String> decodeProfiles(String text) {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            String[] fields = line.split(" ", 2);
            try { result.put(UUID.fromString(fields[0]), fields.length > 1 ? fields[1] : fields[0]); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
}
