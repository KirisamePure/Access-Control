package dev.kirisame.accesscontrol;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Iron semantics preserve two-block placement, support, hinges, loot and redstone. */
public class MetalDoorBlock extends DoorBlock {
    public static final MapCodec<MetalDoorBlock> CODEC = createCodec(MetalDoorBlock::new);
    public MetalDoorBlock(Settings settings) { super(BlockSetType.IRON, settings); }
    @Override public MapCodec<? extends DoorBlock> getCodec() { return CODEC; }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) { return ActionResult.PASS; }
    @Override protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) { return ActionResult.PASS; }
}
