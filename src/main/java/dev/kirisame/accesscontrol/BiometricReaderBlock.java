package dev.kirisame.accesscontrol;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BiometricReaderBlock extends ReaderBlock {
    public static final MapCodec<BiometricReaderBlock> CODEC = createCodec(BiometricReaderBlock::new);
    public BiometricReaderBlock(Settings settings) { super(settings); }
    @Override public MapCodec<BiometricReaderBlock> getCodec() { return CODEC; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new BiometricReaderBlockEntity(pos, state); }
    @Override protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hit) { return scan(state, world, pos, player); }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return scan(state, world, pos, player);
    }
    private ActionResult scan(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (player.isSpectator()) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) {
            player.sendMessage(Text.translatable("message.accesscontrol.empty_hands").formatted(Formatting.GRAY), true);
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof BiometricReaderBlockEntity reader)) return ActionResult.FAIL;
        boolean allowed = reader.allows(player.getUuid());
        return feedback(allowed, state, world, pos, player,
                Text.translatable(allowed ? "message.accesscontrol.bio_granted" : "message.accesscontrol.bio_denied"));
    }
}
