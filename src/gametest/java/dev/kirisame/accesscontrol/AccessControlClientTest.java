package dev.kirisame.accesscontrol;

import dev.kirisame.accesscontrol.client.ReaderConfigScreen;
import dev.kirisame.accesscontrol.client.SlidingDoorRenderer;
import dev.kirisame.accesscontrol.client.BiometricConfigScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
            server.runCommand("fill -8 90 -12 12 90 12 minecraft:smooth_stone");
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

            // Expanded scene: glass, industrial wall pocket, and the subway flap lane.
            BlockPos pocket = new BlockPos(0, 91, -7);
            BlockPos gate = new BlockPos(5, 91, -7);
            server.runOnServer(s -> {
                var world = s.getOverworld();
                for (int i = 0; i < 2; i++) {
                    BlockPos pos = i == 0 ? new BlockPos(-3, 91, -7) : pocket;
                    BlockState state = (i == 0 ? AccessControl.GLASS_DOOR : AccessControl.INDUSTRIAL_DOOR)
                            .getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
                    world.setBlockState(pos, state.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    world.setBlockState(pos.up(), state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                }
                world.setBlockState(pocket.east(), Blocks.SMOOTH_STONE.getDefaultState());
                world.setBlockState(pocket.east().up(), Blocks.SMOOTH_STONE.getDefaultState());
                var state = AccessControl.SUBWAY_GATE.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
                world.setBlockState(gate, state);
                AccessControl.SUBWAY_GATE.onPlaced(world, gate, state, null, new net.minecraft.item.ItemStack(AccessControl.SUBWAY_GATE));
            });
            server.runCommand("fill -4 91 -9 -2 93 -9 minecraft:orange_concrete");
            server.runCommand("tp @a 1.5 91 0.5 180 2");
            context.waitTicks(20);
            game.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("expansion_closed");
            server.runCommand("setblock 0 91 -6 minecraft:redstone_block");
            context.waitTicks(15);
            context.runOnClient(client -> {
                for (int y = 0; y < 2; y++) {
                    var entity = client.world.getBlockEntity(pocket.up(y));
                    var state = (SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher().getRenderState(entity, 1, null);
                    if (!state.faces().isEmpty()) throw new AssertionError("Door still draws geometry inside a full wall");
                }
                client.inGameHud.getChatHud().clear(false);
            });
            context.takeScreenshot("door_hidden_inside_wall");
            server.runCommand("setblock 1 91 -7 minecraft:stone_slab[type=bottom]");
            context.waitTicks(5);
            context.runOnClient(client -> {
                var state = (SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher()
                        .getRenderState(client.world.getBlockEntity(pocket), 1, null);
                if (state.faces().isEmpty()) throw new AssertionError("Slab incorrectly hides the entire panel");
                for (var face : state.faces()) for (var vertex : face.vertices())
                    if (vertex.y() < .5f) throw new AssertionError("Door intersects the pocket slab");
                client.inGameHud.getChatHud().clear(false);
            });
            context.takeScreenshot("door_partially_hidden_by_slab");
            server.runCommand("tp @a 4.75 91 -3.5 180 12");
            context.waitTicks(5);
            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTick();
            context.getInput().pressKey(options -> options.useKey);
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitForScreen(ReaderConfigScreen.class);
            context.clickScreenButton("L3");
            context.clickScreenButton("screen.accesscontrol.save");
            context.waitFor(client -> client.world.getBlockState(gate).get(ReaderBlock.REQUIRED_LEVEL) == 3);
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.useKey);
            context.waitFor(client -> client.world.getBlockState(gate).get(DoorBlock.OPEN));
            context.waitTicks(12);
            context.runOnClient(client -> {
                var state = (SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher()
                        .getRenderState(client.world.getBlockEntity(gate), 1, null);
                if (!state.faces().isEmpty()) throw new AssertionError("Retracted wings should be hidden by the cabinets");
                client.inGameHud.getChatHud().clear(false);
            });
            context.takeScreenshot("subway_gate_card_open");
            context.waitTicks(25);
            BlockPos adjacent = gate.east(3);
            int before = context.computeOnClient(client -> ((SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher()
                    .getRenderState(client.world.getBlockEntity(gate.east()), 1, null)).faces().size());
            server.runOnServer(s -> {
                var state = AccessControl.SUBWAY_GATE.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH);
                s.getOverworld().setBlockState(adjacent, state);
                AccessControl.SUBWAY_GATE.onPlaced(s.getOverworld(), adjacent, state, null,
                        new net.minecraft.item.ItemStack(AccessControl.SUBWAY_GATE));
            });
            context.waitTicks(10);
            context.runOnClient(client -> {
                var cabinet = gate.east();
                if (!SubwayGateBlock.connectsOutward(client.world.getBlockState(cabinet), client.world, cabinet))
                    throw new AssertionError("Client did not detect neighboring lane");
                var state = (SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher()
                        .getRenderState(client.world.getBlockEntity(cabinet), 1, null);
                if (state.faces().size() <= before) throw new AssertionError("Connector has collision but no visible model");
            });
            server.runCommand("tp @a 6.5 91 -1.5 180 6");
            context.waitTicks(5);
            context.runOnClient(client -> client.inGameHud.getChatHud().clear(false));
            context.takeScreenshot("adjacent_gates_connected");
            server.runOnServer(s -> s.getOverworld().breakBlock(adjacent, false));
            context.waitTicks(5);
            context.runOnClient(client -> {
                var state = (SlidingDoorRenderer.State) client.getBlockEntityRenderDispatcher()
                        .getRenderState(client.world.getBlockEntity(gate.east()), 1, null);
                if (state.faces().size() != before) throw new AssertionError("Connector did not disappear when adjacent gate was removed");
            });

            BlockPos biometric = new BlockPos(10, 92, 1);
            server.runOnServer(s -> {
                s.getOverworld().setBlockState(biometric.north(), Blocks.IRON_BLOCK.getDefaultState());
                s.getOverworld().setBlockState(biometric, AccessControl.BIOMETRIC_READER.getDefaultState().with(ReaderBlock.FACING, Direction.SOUTH));
            });
            server.runCommand("tp @a 10.5 91 4.5 180 0");
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.useKey);
            context.waitFor(client -> client.world.getBlockState(biometric).get(ReaderBlock.STATUS) == 2);
            context.runOnClient(client -> {
                if (client.world.getBlockState(biometric).get(ReaderBlock.POWERED)) throw new AssertionError("Empty whitelist grants access");
                client.inGameHud.getChatHud().clear(false);
            });
            context.takeScreenshot("biometric_denied");
            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTick();
            context.getInput().pressKey(options -> options.useKey);
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitForScreen(BiometricConfigScreen.class);
            String name = context.computeOnClient(client -> client.player.getName().getString());
            context.clickScreenButton("[ ] " + name);
            java.util.UUID offline = java.util.UUID.fromString("a0b1c2d3-0000-4000-8000-000000000001");
            context.runOnClient(client -> {
                for (var widget : client.currentScreen.children()) if (widget instanceof TextFieldWidget field) field.setText(offline.toString());
            });
            context.clickScreenButton("screen.accesscontrol.add");
            context.takeScreenshot("biometric_whitelist_config");
            context.clickScreenButton("screen.accesscontrol.save");
            context.waitTicks(5);
            server.runOnServer(s -> {
                var entity = (BiometricReaderBlockEntity) s.getOverworld().getBlockEntity(biometric);
                if (entity.players().size() != 2 || !entity.allows(offline)
                        || !entity.allows(s.getPlayerManager().getPlayerList().getFirst().getUuid()))
                    throw new AssertionError("Whitelist screen failed to save both online player and offline UUID");
            });
            context.getInput().pressKey(options -> options.useKey);
            context.waitFor(client -> client.world.getBlockState(biometric).get(ReaderBlock.POWERED));
            context.takeScreenshot("biometric_granted");
            context.waitTicks(25);

            // Open as creative OP, then switch mode before clicking Save.
            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTick();
            context.getInput().pressKey(options -> options.useKey);
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitForScreen(BiometricConfigScreen.class);
            server.runCommand("gamemode survival @a");
            context.waitTicks(3);
            context.clickScreenButton("[+] " + name);
            context.clickScreenButton("screen.accesscontrol.save");
            context.runOnClient(client -> ClientPlayNetworking.send(new ReaderNetworking.SetLevel(reader,
                    client.world.getRegistryKey().getValue(), 1)));
            context.waitTicks(5);
            server.runOnServer(s -> {
                var entity = (BiometricReaderBlockEntity) s.getOverworld().getBlockEntity(biometric);
                if (entity.players().size() != 2) throw new AssertionError("Survival OP changed whitelist from an already-open screen");
                if (s.getOverworld().getBlockState(reader).get(ReaderBlock.REQUIRED_LEVEL) != 4)
                    throw new AssertionError("Survival OP forged a card-reader clearance update");
            });
            context.getInput().holdKey(options -> options.sneakKey);
            context.waitTick();
            context.getInput().pressKey(options -> options.useKey);
            context.getInput().releaseKey(options -> options.sneakKey);
            context.waitTicks(5);
            context.runOnClient(client -> {
                if (client.currentScreen != null) throw new AssertionError("Survival OP opened configuration");
            });
        }
    }
}
