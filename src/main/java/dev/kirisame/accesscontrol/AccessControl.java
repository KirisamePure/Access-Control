package dev.kirisame.accesscontrol;

import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class AccessControl implements ModInitializer {
    public static final String MOD_ID = "accesscontrol";
    public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }

    private static AbstractBlock.Settings settings(String name) {
        return AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                .registryKey(RegistryKey.of(RegistryKeys.BLOCK, id(name)))
                .strength(5, 6).nonOpaque().sounds(BlockSoundGroup.METAL);
    }

    public static final ReaderBlock READER = registerBlock("card_reader", new ReaderBlock(
            settings("card_reader").strength(3).luminance(state -> state.get(ReaderBlock.STATUS) == 0 ? 0 : 10)));
    public static final MetalDoorBlock IRON_DOOR = registerBlock("sealed_iron_door", new MetalDoorBlock(settings("sealed_iron_door")));
    public static final MetalDoorBlock IRON_WINDOW_DOOR = registerBlock("windowed_iron_door", new MetalDoorBlock(settings("windowed_iron_door")));
    public static final SlidingDoorBlock TECH_DOOR = registerBlock("sliding_tech_door", new SlidingDoorBlock(settings("sliding_tech_door")));
    public static final SlidingDoorBlock TECH_WINDOW_DOOR = registerBlock("windowed_sliding_tech_door", new SlidingDoorBlock(settings("windowed_sliding_tech_door")));
    // Render-only states: no item, recipe or world placement. A visible model lets the BER
    // submit the moving panel without recursively drawing an invisible door block.
    public static final DoorPanelBlock TECH_PANEL = registerBlock("tech_panel", new DoorPanelBlock(settings("tech_panel")));
    public static final DoorPanelBlock TECH_WINDOW_PANEL = registerBlock("tech_window_panel", new DoorPanelBlock(settings("tech_window_panel")));

    public static final List<AccessCardItem> CARDS = List.of(card(1), card(2), card(3), card(4));
    public static final BlockEntityType<SlidingDoorBlockEntity> SLIDING_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, id("sliding_door"),
            FabricBlockEntityTypeBuilder.create(SlidingDoorBlockEntity::new, TECH_DOOR, TECH_WINDOW_DOOR).build());
    public static final BlockEntityType<ReaderBlockEntity> READER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, id("card_reader"),
            FabricBlockEntityTypeBuilder.create(ReaderBlockEntity::new, READER).build());
    public static final SoundEvent GRANTED = sound("reader_granted");
    public static final SoundEvent DENIED = sound("reader_denied");
    public static final SoundEvent PNEUMATIC_OPEN = sound("pneumatic_open");
    public static final SoundEvent PNEUMATIC_CLOSE = sound("pneumatic_close");

    private static <T extends Block> T registerBlock(String name, T block) {
        return Registry.register(Registries.BLOCK, id(name), block);
    }
    private static Item.Settings itemSettings(String name) {
        return new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, id(name)));
    }
    private static AccessCardItem card(int level) {
        String name = "access_card_" + level;
        return Registry.register(Registries.ITEM, id(name), new AccessCardItem(level, itemSettings(name)));
    }
    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, id(name), SoundEvent.of(id(name)));
    }

    @Override
    public void onInitialize() {
        List<Block> blocks = List.of(READER, IRON_DOOR, IRON_WINDOW_DOOR, TECH_DOOR, TECH_WINDOW_DOOR);
        for (Block block : blocks) {
            Identifier id = Registries.BLOCK.getId(block);
            var itemSettings = itemSettings(id.getPath()).useBlockPrefixedTranslationKey();
            Registry.register(Registries.ITEM, id, block instanceof MetalDoorBlock
                    ? new TallBlockItem(block, itemSettings) : new BlockItem(block, itemSettings));
        }
        Registry.register(Registries.ITEM_GROUP, id("equipment"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.accesscontrol"))
                .icon(() -> new ItemStack(CARDS.get(3)))
                .entries((context, entries) -> {
                    CARDS.forEach(entries::add);
                    blocks.forEach(entries::add);
                }).build());
        ReaderNetworking.register();
        // The callback runs before vanilla's sneak bypass, including while holding a block.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator() || !player.isSneaking()
                    || !world.getBlockState(hit.getBlockPos()).isOf(READER)) return ActionResult.PASS;
            if (hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity serverPlayer) {
                ReaderNetworking.open(serverPlayer, hit.getBlockPos());
            }
            return ActionResult.SUCCESS;
        });
    }
}
