package dev.kirisame.accesscontrol;

import dev.kirisame.accesscontrol.client.ReaderConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Boots the real client, renders both doors, and round-trips configuration packets. */
public final class AccessControlClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().adjustSettings(creator -> creator.setGameMode(WorldCreator.Mode.CREATIVE)).create()) {
            var server = game.getServer();
            server.runCommand("fill -8 90 -4 12 90 12 minecraft:smooth_stone");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("tp @a 2.5 91 8.5 180 0");
            server.runCommand("give @a accesscontrol:access_card_1");
            server.runCommand("give @a accesscontrol:access_card_2");
            server.runCommand("give @a accesscontrol:access_card_3");
            server.runCommand("give @a accesscontrol:access_card_4");
            BlockPos reader = new BlockPos(9, 92, 1);
            server.runOnServer(s -> {
                var world = s.getOverworld();
                MetalDoorBlock[] doors = {AccessControl.IRON_DOOR, AccessControl.IRON_WINDOW_DOOR,
                        AccessControl.TECH_DOOR, AccessControl.TECH_WINDOW_DOOR};
                for (int i = 0; i < doors.length; i++) {
                    BlockPos pos = new BlockPos(-3 + i * 3, 91, 1);
                    BlockState state = doors[i].getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
                    world.setBlockState(pos, state.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    world.setBlockState(pos.up(), state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                }
                world.setBlockState(reader.north(), Blocks.IRON_BLOCK.getDefaultState());
                world.setBlockState(reader, AccessControl.READER.getDefaultState().with(ReaderBlock.FACING, Direction.SOUTH));
            });
            context.waitTicks(30);
            game.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("accesscontrol_doors_closed");
            server.runCommand("setblock 4 91 1 minecraft:redstone_block");
            server.runCommand("setblock 7 91 1 minecraft:redstone_block");
            context.waitTicks(5);
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("accesscontrol_doors_moving");
            context.waitTicks(15);
            context.takeScreenshot("accesscontrol_doors_open");
            server.runCommand("setblock 4 91 1 minecraft:air");
            server.runCommand("setblock 7 91 1 minecraft:air");
            context.waitTicks(20);
            server.runCommand("tp @a 9.5 91 4.5 180 0");
            context.waitTicks(5);
            // Use real input: sneak-use must work while a card is held.
            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTick();
            context.getInput().pressKey(options -> options.useKey);
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitForScreen(ReaderConfigScreen.class);
            context.takeScreenshot("accesscontrol_reader_config");
            context.clickScreenButton("L4");
            context.clickScreenButton("screen.accesscontrol.save");
            context.waitFor(client -> client.world.getBlockState(reader).get(ReaderBlock.REQUIRED_LEVEL) == 4);
            context.runOnClient(client -> ClientPlayNetworking.send(
                    new ReaderNetworking.SetLevel(reader, client.world.getRegistryKey().getValue(), 99)));
            context.waitTicks(5);
            server.runOnServer(s -> {
                if (s.getOverworld().getBlockState(reader).get(ReaderBlock.REQUIRED_LEVEL) != 4)
                    throw new AssertionError("Invalid clearance packet changed the reader");
            });
            context.setScreen(() -> null);
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.useKey);
            context.waitFor(client -> client.world.getBlockState(reader).get(ReaderBlock.STATUS) == 2);
            server.runOnServer(s -> {
                if (s.getOverworld().getBlockState(reader).get(ReaderBlock.POWERED))
                    throw new AssertionError("L1 card powered an L4 reader");
            });
            context.takeScreenshot("accesscontrol_denied");
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(3));
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.useKey);
            context.waitFor(client -> client.world.getBlockState(reader).get(ReaderBlock.POWERED));
            context.takeScreenshot("accesscontrol_granted");
        }
    }
}
