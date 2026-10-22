package dev.kirisame.accesscontrol;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;

public class SlidingDoorBlock extends MetalDoorBlock implements BlockEntityProvider {
    public static final MapCodec<SlidingDoorBlock> CODEC = createCodec(SlidingDoorBlock::new);
    public SlidingDoorBlock(Settings settings) { super(settings); }
    @Override public MapCodec<? extends SlidingDoorBlock> getCodec() { return CODEC; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SlidingDoorBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClient() || type != AccessControl.SLIDING_ENTITY) return null;
        return (w, p, s, entity) -> ((SlidingDoorBlockEntity) entity).clientTick(s);
    }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.INVISIBLE; }

    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Never inherit the iron door's 90-degree rotated open outline.
        return super.getOutlineShape(state.with(OPEN, false), world, pos, context);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(OPEN) ? VoxelShapes.empty() : getOutlineShape(state, world, pos, context);
    }

    @Override protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block source,
            WireOrientation orientation, boolean notify) {
        if (world.isClient()) return;
        BlockPos otherPos = state.get(HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
        boolean powered = world.isReceivingRedstonePower(pos) || world.isReceivingRedstonePower(otherPos);
        if (powered == state.get(POWERED)) return;
        // Updating one half propagates OPEN and POWERED to its partner via DoorBlock.
        world.setBlockState(pos, state.with(POWERED, powered).with(OPEN, powered), Block.NOTIFY_ALL);
        if (powered != state.get(OPEN)) {
            BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
            world.playSound(null, lower, powered ? AccessControl.PNEUMATIC_OPEN : AccessControl.PNEUMATIC_CLOSE,
                    SoundCategory.BLOCKS, 0.7f, 1);
        }
    }
}
