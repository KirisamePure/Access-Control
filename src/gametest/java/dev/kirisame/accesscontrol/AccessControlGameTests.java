package dev.kirisame.accesscontrol;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

public final class AccessControlGameTests {
    private BlockPos reader(TestContext context, int required) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        context.getWorld().setBlockState(pos.south(), Blocks.IRON_BLOCK.getDefaultState());
        context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
        context.getWorld().setBlockState(pos, AccessControl.READER.getDefaultState().with(ReaderBlock.REQUIRED_LEVEL, required));
        return pos;
    }
    private void swipe(TestContext context, BlockPos pos, int level) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        AccessControl.READER.onUseWithItem(new ItemStack(AccessControl.CARDS.get(level - 1)),
                context.getWorld().getBlockState(pos), context.getWorld(), pos, player, Hand.MAIN_HAND,
                new BlockHitResult(pos.toCenterPos(), Direction.NORTH, pos, false));
    }
    private void check(TestContext context, boolean condition, String message) {
        context.assertTrue(condition, Text.literal(message));
    }

    @GameTest public void allCardClearances(TestContext context) {
        for (int required = 1; required <= 4; required++) {
            for (int card = 1; card <= 4; card++) {
                BlockPos pos = reader(context, required);
                swipe(context, pos, card);
                BlockState state = context.getWorld().getBlockState(pos);
                boolean expected = card >= required;
                check(context, state.get(ReaderBlock.POWERED) == expected, "Incorrect power for card " + card + "/reader " + required);
                check(context, state.get(ReaderBlock.STATUS) == (expected ? 1 : 2), "Incorrect visual feedback");
                check(context, state.getWeakRedstonePower(context.getWorld(), pos, Direction.NORTH) == (expected ? 15 : 0), "Incorrect redstone output");
            }
        }
        context.complete();
    }

    @GameTest(maxTicks = 60) public void renewedPulseSurvivesOldTimer(TestContext context) {
        BlockPos pos = reader(context, 3);
        swipe(context, pos, 4);
        context.runAtTick(5, () -> {
            swipe(context, pos, 1);
            check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Denied swipe interrupted authorized pulse");
        });
        context.runAtTick(19, () -> swipe(context, pos, 3));
        context.runAtTick(22, () -> check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.POWERED), "Old timer truncated renewed pulse"));
        context.runAtTick(42, () -> {
            BlockState state = context.getWorld().getBlockState(pos);
            check(context, !state.get(ReaderBlock.POWERED), "Pulse did not switch off");
            check(context, state.get(ReaderBlock.STATUS) == 0, "Reader did not return to idle");
            context.complete();
        });
    }

    @GameTest public void configurationPreservesPulse(TestContext context) {
        BlockPos pos = reader(context, 1);
        swipe(context, pos, 1);
        var world = context.getWorld();
        world.setBlockState(pos, world.getBlockState(pos).with(ReaderBlock.REQUIRED_LEVEL, 4));
        check(context, world.getBlockState(pos).get(ReaderBlock.POWERED), "Config change interrupted pulse");
        check(context, world.getBlockState(pos).get(ReaderBlock.REQUIRED_LEVEL) == 4, "Config was not stored");
        context.complete();
    }

    @GameTest public void nonOperatorCannotForgeConfiguration(TestContext context) {
        BlockPos pos = reader(context, 4);
        var player = context.createMockCreativeServerPlayerInWorld();
        check(context, !player.hasPermissionLevel(1), "Mock player unexpectedly has OP");
        ReaderNetworking.configure(player, new ReaderNetworking.SetLevel(pos,
                context.getWorld().getRegistryKey().getValue(), 1));
        check(context, context.getWorld().getBlockState(pos).get(ReaderBlock.REQUIRED_LEVEL) == 4,
                "Non-operator packet lowered clearance");
        context.complete();
    }

    @GameTest(maxTicks = 30) public void allDoorsRequireRedstoneAndSyncHalves(TestContext context) {
        MetalDoorBlock[] doors = {AccessControl.IRON_DOOR, AccessControl.IRON_WINDOW_DOOR,
                AccessControl.TECH_DOOR, AccessControl.TECH_WINDOW_DOOR};
        var world = context.getWorld();
        BlockPos[] positions = new BlockPos[4];
        for (int i = 0; i < doors.length; i++) {
            BlockPos pos = positions[i] = context.getAbsolutePos(new BlockPos(1 + i % 2 * 4, 2, 1 + i / 2 * 4));
            world.setBlockState(pos.down(), Blocks.IRON_BLOCK.getDefaultState());
            world.setBlockState(pos, doors[i].getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            world.setBlockState(pos.up(), doors[i].getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            doors[i].onUse(world.getBlockState(pos), world, pos, context.createMockPlayer(GameMode.SURVIVAL),
                    new BlockHitResult(pos.toCenterPos(), Direction.NORTH, pos, false));
            check(context, !world.getBlockState(pos).get(DoorBlock.OPEN), "Door opened by hand");
            world.setBlockState(pos.east(), Blocks.REDSTONE_BLOCK.getDefaultState());
        }
        context.runAtTick(3, () -> {
            for (BlockPos pos : positions) {
                check(context, world.getBlockState(pos).get(DoorBlock.OPEN), "Powered lower door stayed closed");
                check(context, world.getBlockState(pos.up()).get(DoorBlock.OPEN), "Upper half did not follow lower");
                world.setBlockState(pos.east(), Blocks.AIR.getDefaultState());
            }
        });
        context.runAtTick(6, () -> {
            for (BlockPos pos : positions) {
                check(context, !world.getBlockState(pos).get(DoorBlock.OPEN), "Door did not close after power loss");
                check(context, !world.getBlockState(pos.up()).get(DoorBlock.OPEN), "Upper door stayed open");
            }
            context.complete();
        });
    }
}
