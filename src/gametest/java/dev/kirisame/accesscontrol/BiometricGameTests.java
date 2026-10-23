package dev.kirisame.accesscontrol;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtReadView;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class BiometricGameTests {
    private void check(TestContext context, boolean value, String message) { context.assertTrue(value, Text.literal(message)); }
    private BlockPos biometric(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(4, 2, 1));
        context.getWorld().setBlockState(pos.south(), Blocks.IRON_BLOCK.getDefaultState());
        context.getWorld().setBlockState(pos, AccessControl.BIOMETRIC_READER.getDefaultState());
        return pos;
    }
    private BiometricReaderBlockEntity reader(TestContext context, BlockPos pos) {
        return (BiometricReaderBlockEntity) context.getWorld().getBlockEntity(pos);
    }
    private void scan(TestContext context, BlockPos pos, PlayerEntity player) {
        AccessControl.BIOMETRIC_READER.onUse(context.getWorld().getBlockState(pos), context.getWorld(), pos, player,
                new BlockHitResult(pos.toCenterPos(), Direction.NORTH, pos, false));
    }
    private void whitelist(TestContext context, BlockPos pos, ServerPlayerEntity player, String value) {
        player.setPosition(pos.toCenterPos());
        BiometricNetworking.configure(player, new BiometricNetworking.SetWhitelist(pos, context.getWorld().getRegistryKey().getValue(), value));
    }
    @GameTest public void allConfigurationRequiresCreativeAndOp(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        BlockPos bio = biometric(context);
        BlockPos card = context.getAbsolutePos(new BlockPos(2, 2, 1));
        world.setBlockState(card.south(), Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(card, AccessControl.READER.getDefaultState());
        BlockPos gate = context.getAbsolutePos(new BlockPos(3, 2, 5));
        for (int x = -1; x <= 1; x++) world.setBlockState(gate.add(x, -1, 0), Blocks.IRON_BLOCK.getDefaultState());
        var state = AccessControl.SUBWAY_GATE.getDefaultState();
        world.setBlockState(gate, state);
        AccessControl.SUBWAY_GATE.onPlaced(world, gate, state, null, new ItemStack(AccessControl.SUBWAY_GATE));
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.CREATIVE);
        var profile = new PlayerConfigEntry(player.getGameProfile());
        var dimension = world.getRegistryKey().getValue();
        check(context, !player.hasPermissionLevel(1), "Expected non-op initially");
        Runnable rejected = () -> {
            for (BlockPos target : List.of(card, gate)) {
                player.setPosition(target.toCenterPos());
                ReaderNetworking.configure(player, new ReaderNetworking.SetLevel(target, dimension, 4));
                check(context, world.getBlockState(target).get(ReaderBlock.REQUIRED_LEVEL) == 1, "Unauthorized level change");
            }
            whitelist(context, bio, player, player.getUuidAsString());
            check(context, reader(context, bio).players().isEmpty(), "Unauthorized whitelist change");
        };
        rejected.run(); // Creative without OP.
        server.getPlayerManager().addToOperators(profile, Optional.of(1), Optional.of(false));
        try {
            check(context, player.hasPermissionLevel(1), "Could not give test actor OP");
            for (GameMode mode : List.of(GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.SPECTATOR)) {
                player.changeGameMode(mode);
                check(context, !ReaderNetworking.canConfigure(player), "Non-creative OP may configure");
                rejected.run();
            }
            player.changeGameMode(GameMode.CREATIVE);
            for (BlockPos target : List.of(card, gate)) {
                player.setPosition(target.toCenterPos());
                ReaderNetworking.configure(player, new ReaderNetworking.SetLevel(target, dimension, 4));
                check(context, world.getBlockState(target).get(ReaderBlock.REQUIRED_LEVEL) == 4, "Creative OP config failed");
            }
            whitelist(context, bio, player, player.getUuidAsString());
            check(context, reader(context, bio).allows(player.getUuid()), "Creative OP whitelist save failed");
            player.changeGameMode(GameMode.SURVIVAL);
            whitelist(context, bio, player, "");
            check(context, reader(context, bio).allows(player.getUuid()), "Changing mode did not revoke editor permission");
        } finally { server.getPlayerManager().removeFromOperators(profile); }
        context.complete();
    }
    @GameTest(maxTicks = 55) public void biometricFeedbackAndPulse(TestContext context) {
        BlockPos pos = biometric(context);
        var allowed = context.createMockPlayer(GameMode.SURVIVAL);
        var denied = context.createMockPlayer(GameMode.SURVIVAL);
        allowed.setUuid(UUID.randomUUID()); denied.setUuid(UUID.randomUUID());
        scan(context, pos, allowed);
        check(context, !context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Empty whitelist granted access");
        reader(context, pos).setPlayers(Map.of(allowed.getUuid(), "Allowed"));
        scan(context, pos, allowed);
        check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Allowed identity got no power");
        check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.STATUS) == 1, "Missing success light");
        context.runAtTick(23, () -> {
            check(context, !context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Biometric pulse did not expire");
            scan(context, pos, denied);
            check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.STATUS) == 2, "Missing denial feedback");
            check(context, !context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Denied identity created power");
        });
        context.runAtTick(24, () -> scan(context, pos, allowed));
        context.runAtTick(25, () -> {
            scan(context, pos, denied);
            check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Denied identity interrupted valid pulse");
        });
        context.runAtTick(48, () -> {
            check(context, !context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Renewed pulse stuck on");
            check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.STATUS) == 0, "Feedback did not reset");
            context.complete();
        });
    }
    @GameTest public void bothHandsMustBeEmpty(TestContext context) {
        BlockPos pos = biometric(context);
        var player = context.createMockPlayer(GameMode.SURVIVAL);
        reader(context, pos).setPlayers(Map.of(player.getUuid(), "Allowed"));
        for (Hand hand : Hand.values()) {
            player.setStackInHand(hand, new ItemStack(AccessControl.CARDS.getLast()));
            scan(context, pos, player);
            check(context, !context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Held item bypassed empty-hands requirement");
            player.setStackInHand(hand, ItemStack.EMPTY);
        }
        scan(context, pos, player);
        check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Empty hands failed");
        context.complete();
    }
    @GameTest public void whitelistPersistsByUuid(TestContext context) {
        BlockPos pos = biometric(context);
        var original = reader(context, pos);
        UUID id = UUID.randomUUID();
        original.setPlayers(Map.of(id, "OldName"));
        var copy = new BiometricReaderBlockEntity(pos, context.getWorld().getBlockState(pos));
        copy.read(NbtReadView.create(ErrorReporter.EMPTY, context.getWorld().getRegistryManager(),
                original.createNbt(context.getWorld().getRegistryManager())));
        check(context, copy.allows(id), "Whitelist identity lost during save/load");
        check(context, !copy.allows(UUID.randomUUID()), "Display name or another identity granted access");
        original.setPlayers(Map.of());
        copy.read(NbtReadView.create(ErrorReporter.EMPTY, context.getWorld().getRegistryManager(),
                original.createNbt(context.getWorld().getRegistryManager())));
        check(context, !copy.allows(id), "Revoked entry reappeared after reload");
        context.complete();
    }
    @GameTest public void invalidWhitelistSaveIsAtomic(TestContext context) {
        BlockPos pos = biometric(context);
        var world = context.getWorld();
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.CREATIVE);
        var profile = new PlayerConfigEntry(player.getGameProfile());
        world.getServer().getPlayerManager().addToOperators(profile, Optional.of(1), Optional.of(false));
        try {
            UUID id = UUID.randomUUID();
            whitelist(context, pos, player, id.toString());
            check(context, reader(context, pos).allows(id), "Valid whitelist request rejected");
            whitelist(context, pos, player, UUID.randomUUID() + "\ninvalid");
            check(context, reader(context, pos).players().size() == 1 && reader(context, pos).allows(id), "Malformed save partially changed whitelist");
            player.setPosition(pos.toCenterPos().add(100, 0, 0));
            BiometricNetworking.configure(player, new BiometricNetworking.SetWhitelist(pos, world.getRegistryKey().getValue(), ""));
            check(context, reader(context, pos).allows(id), "Distant actor changed whitelist");
        } finally { world.getServer().getPlayerManager().removeFromOperators(profile); }
        context.complete();
    }
}
