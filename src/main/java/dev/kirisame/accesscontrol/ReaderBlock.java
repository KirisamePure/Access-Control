package dev.kirisame.accesscontrol;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import net.minecraft.world.tick.ScheduledTickView;

public final class ReaderBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final MapCodec<ReaderBlock> CODEC = createCodec(ReaderBlock::new);
    public static final IntProperty REQUIRED_LEVEL = IntProperty.of("required_level", 1, 4);
    public static final IntProperty STATUS = IntProperty.of("status", 0, 2);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    public ReaderBlock(Settings settings) {
        super(settings);
        setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH)
                .with(REQUIRED_LEVEL, 1).with(STATUS, 0).with(POWERED, false));
    }
    @Override public MapCodec<ReaderBlock> getCodec() { return CODEC; }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ReaderBlockEntity(pos, state); }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, REQUIRED_LEVEL, STATUS, POWERED);
    }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction side = context.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : context.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }
    @Override protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        Direction facing = state.get(FACING);
        return world.getBlockState(pos.offset(facing.getOpposite())).isSideSolidFullSquare(
                world, pos.offset(facing.getOpposite()), facing);
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world,
            ScheduledTickView tickView, BlockPos pos, Direction direction, BlockPos neighborPos,
            BlockState neighborState, Random random) {
        return direction == state.get(FACING).getOpposite() && !canPlaceAt(state, world, pos)
                ? Blocks.AIR.getDefaultState()
                : super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> Block.createCuboidShape(4, 3, 13, 12, 13, 16);
            case SOUTH -> Block.createCuboidShape(4, 3, 0, 12, 13, 3);
            case EAST -> Block.createCuboidShape(0, 3, 4, 3, 13, 12);
            case WEST -> Block.createCuboidShape(13, 3, 4, 16, 13, 12);
            default -> throw new IllegalStateException("Non-horizontal facing");
        };
    }
    @Override protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        return swipe(stack, state, world, pos, player);
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        // Let vanilla try a card in the off hand before showing the empty-hand hint.
        if (player.getOffHandStack().getItem() instanceof AccessCardItem) return ActionResult.PASS;
        return swipe(ItemStack.EMPTY, state, world, pos, player);
    }
    private ActionResult swipe(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (player.isSpectator()) return ActionResult.PASS;
        if (!(stack.getItem() instanceof AccessCardItem card)) {
            if (player.getOffHandStack().getItem() instanceof AccessCardItem) return ActionResult.PASS;
            player.sendMessage(Text.translatable("message.accesscontrol.use_card").formatted(Formatting.GRAY), true);
            return ActionResult.SUCCESS;
        }
        boolean allowed = AccessRules.grants(card.level(), state.get(REQUIRED_LEVEL));
        if (!(world.getBlockEntity(pos) instanceof ReaderBlockEntity reader)) return ActionResult.FAIL;
        long now = world.getTime();
        reader.feedbackUntil = now + AccessRules.PULSE_TICKS;
        if (allowed) reader.powerUntil = now + AccessRules.PULSE_TICKS;
        reader.markDirty();
        // A denied swipe never creates power or interrupts an existing authorized pulse.
        BlockState next = state.with(STATUS, allowed ? 1 : 2);
        if (allowed) next = next.with(POWERED, true);
        world.setBlockState(pos, next, Block.NOTIFY_ALL);
        world.updateNeighbors(pos.offset(state.get(FACING).getOpposite()), this);
        world.scheduleBlockTick(pos, this, AccessRules.PULSE_TICKS);
        world.playSound(null, pos, allowed ? AccessControl.GRANTED : AccessControl.DENIED,
                SoundCategory.BLOCKS, 0.65f, 1);
        player.sendMessage(Text.translatable(allowed ? "message.accesscontrol.granted" : "message.accesscontrol.denied",
                state.get(REQUIRED_LEVEL)).formatted(allowed ? Formatting.GREEN : Formatting.RED), true);
        return ActionResult.SUCCESS;
    }
    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        long now = world.getTime();
        ReaderBlockEntity reader = world.getBlockEntity(pos) instanceof ReaderBlockEntity entity ? entity : null;
        boolean powered = reader != null && reader.powerUntil > now;
        int status = reader != null && reader.feedbackUntil > now ? state.get(STATUS) : 0;
        world.setBlockState(pos, state.with(POWERED, powered).with(STATUS, status), Block.NOTIFY_ALL);
        world.updateNeighbors(pos.offset(state.get(FACING).getOpposite()), this);
        if (reader != null) {
            long next = Long.MAX_VALUE;
            if (reader.powerUntil > now) next = Math.min(next, reader.powerUntil);
            if (reader.feedbackUntil > now) next = Math.min(next, reader.feedbackUntil);
            if (next != Long.MAX_VALUE) world.scheduleBlockTick(pos, this, (int) Math.max(1, next - now));
        }
    }
    @Override protected boolean emitsRedstonePower(BlockState state) { return true; }
    @Override protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }
    @Override protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return state.get(POWERED) && direction == state.get(FACING) ? 15 : 0;
    }
    @Override protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (state.get(POWERED)) {
            world.updateNeighbors(pos, this);
            world.updateNeighbors(pos.offset(state.get(FACING).getOpposite()), this);
        }
        super.onStateReplaced(state, world, pos, moved);
    }
}
