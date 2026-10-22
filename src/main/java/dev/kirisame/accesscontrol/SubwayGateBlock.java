package dev.kirisame.accesscontrol;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.world.block.WireOrientation;
import net.minecraft.world.tick.ScheduledTickView;

/** A single-place 3-wide, 2-high lane: two reader cabinets and retractable amber wings. */
public final class SubwayGateBlock extends SlidingDoorBlock {
    public enum Part implements StringIdentifiable {
        CENTER(0), LEFT(-1), RIGHT(1);
        final int offset;
        Part(int offset) { this.offset = offset; }
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);
    public static final MapCodec<SubwayGateBlock> CODEC = createCodec(SubwayGateBlock::new);
    public SubwayGateBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(PART, Part.CENTER).with(ReaderBlock.REQUIRED_LEVEL, 1));
    }
    @Override public MapCodec<SubwayGateBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(PART, ReaderBlock.REQUIRED_LEVEL);
    }
    public static BlockPos center(BlockState state, BlockPos pos) {
        BlockPos lower = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
        return lower.offset(state.get(FACING).rotateYClockwise(), -state.get(PART).offset);
    }
    /** Position-dependent, so existing lanes join without replacing their saved states. */
    public static boolean connectsOutward(BlockState state, BlockView world, BlockPos pos) {
        Part part = state.get(PART);
        if (part == Part.CENTER) return false;
        Direction outward = outward(state);
        BlockState neighbor = world.getBlockState(pos.offset(outward));
        return neighbor.isOf(state.getBlock()) && neighbor.get(PART) != Part.CENTER
                && neighbor.get(HALF) == state.get(HALF) && outward(neighbor) == outward.getOpposite();
    }
    private static Direction outward(BlockState state) {
        Direction right = state.get(FACING).rotateYClockwise();
        return state.get(PART) == Part.RIGHT ? right : right.getOpposite();
    }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) return null;
        World world = context.getWorld();
        for (Part part : Part.values()) {
            BlockPos lower = context.getBlockPos().offset(state.get(FACING).rotateYClockwise(), part.offset);
            if (!world.isInBuildLimit(lower.up()) || !world.getWorldBorder().contains(lower)
                    || !world.getBlockState(lower.down()).isSideSolidFullSquare(world, lower.down(), Direction.UP)) return null;
            for (int y = 0; y < 2; y++) {
                if (!world.getBlockState(lower.up(y)).canReplace(context)) return null;
            }
        }
        return state.with(PART, Part.CENTER);
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // Install atomically: structural neighbor checks must see the complete lane.
        for (Part part : Part.values()) for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            BlockPos target = pos.offset(state.get(FACING).rotateYClockwise(), part.offset)
                    .up(half == DoubleBlockHalf.UPPER ? 1 : 0);
            world.setBlockState(target, state.with(PART, part).with(HALF, half), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        neighborUpdate(state, world, pos, this, null, false);
    }
    private boolean intact(BlockState state, WorldView world, BlockPos pos) {
        BlockPos center = center(state, pos);
        for (Part part : Part.values()) for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            BlockPos target = center.offset(state.get(FACING).rotateYClockwise(), part.offset)
                    .up(half == DoubleBlockHalf.UPPER ? 1 : 0);
            BlockState other = world.getBlockState(target);
            if (!other.isOf(this) || other.get(PART) != part || other.get(HALF) != half
                    || other.get(FACING) != state.get(FACING)) return false;
        }
        return true;
    }
    @Override protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world,
            ScheduledTickView ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
        if (!intact(state, world, pos)) return Blocks.AIR.getDefaultState();
        if (state.get(HALF) == DoubleBlockHalf.LOWER
                && !world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) return Blocks.AIR.getDefaultState();
        return state;
    }
    @Override protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block source, WireOrientation orientation, boolean notify) {
        if (world.isClient() || !intact(state, world, pos)) return;
        BlockPos center = center(state, pos);
        boolean powered = world.getBlockEntity(center) instanceof SlidingDoorBlockEntity entity
                && entity.gatePowerUntil > world.getTime();
        for (Part part : Part.values()) for (int y = 0; y < 2; y++) {
            powered |= world.isReceivingRedstonePower(center.offset(state.get(FACING).rotateYClockwise(), part.offset).up(y));
        }
        if (powered == state.get(POWERED)) return;
        for (Part part : Part.values()) for (int y = 0; y < 2; y++) {
            BlockPos target = center.offset(state.get(FACING).rotateYClockwise(), part.offset).up(y);
            world.setBlockState(target, world.getBlockState(target).with(OPEN, powered).with(POWERED, powered),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        world.playSound(null, center, powered ? AccessControl.PNEUMATIC_OPEN : AccessControl.PNEUMATIC_CLOSE,
                SoundCategory.BLOCKS, 0.65f, 1.15f);
    }
    public void configure(World world, BlockPos center, int level) {
        BlockState state = world.getBlockState(center);
        if (!state.isOf(this) || !intact(state, world, center)) return;
        for (Part part : Part.values()) for (int y = 0; y < 2; y++) {
            BlockPos target = center.offset(state.get(FACING).rotateYClockwise(), part.offset).up(y);
            world.setBlockState(target, world.getBlockState(target).with(ReaderBlock.REQUIRED_LEVEL, level),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }
    @Override protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (player.isSpectator()) return ActionResult.PASS;
        if (!(stack.getItem() instanceof AccessCardItem card)) return ActionResult.PASS;
        BlockPos center = center(state, pos);
        boolean granted = AccessRules.grants(card.level(), state.get(ReaderBlock.REQUIRED_LEVEL));
        if (granted && intact(state, world, pos) && world.getBlockEntity(center) instanceof SlidingDoorBlockEntity entity) {
            entity.gatePowerUntil = world.getTime() + AccessRules.PULSE_TICKS;
            entity.markDirty();
            neighborUpdate(world.getBlockState(center), world, center, this, null, false);
            world.scheduleBlockTick(center, this, AccessRules.PULSE_TICKS);
        }
        world.playSound(null, pos, granted ? AccessControl.GRANTED : AccessControl.DENIED, SoundCategory.BLOCKS, .65f, 1);
        player.sendMessage(Text.translatable(granted ? "message.accesscontrol.granted" : "message.accesscontrol.denied",
                state.get(ReaderBlock.REQUIRED_LEVEL)).formatted(granted ? Formatting.GREEN : Formatting.RED), true);
        return ActionResult.SUCCESS;
    }
    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockPos center = center(state, pos);
        neighborUpdate(state, world, pos, this, null, false);
        if (world.getBlockEntity(center) instanceof SlidingDoorBlockEntity entity && entity.gatePowerUntil > world.getTime())
            world.scheduleBlockTick(center, this, (int) Math.max(1, entity.gatePowerUntil - world.getTime()));
    }
    @Override public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos center = center(state, pos);
        if (!world.isClient()) {
            if (!player.isCreative() && !pos.equals(center)) Block.dropStack(world, pos, new ItemStack(this));
            for (Part part : Part.values()) for (int y = 0; y < 2; y++) {
                BlockPos target = center.offset(state.get(FACING).rotateYClockwise(), part.offset).up(y);
                if (!target.equals(pos) && world.getBlockState(target).isOf(this))
                    world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
            }
        }
        return super.onBreak(world, pos, state, player);
    }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        boolean upper = state.get(HALF) == DoubleBlockHalf.UPPER;
        VoxelShape shape = switch (state.get(PART)) {
            case LEFT -> Block.createCuboidShape(8, 0, 1, 16, upper ? 6 : 16, 15);
            case RIGHT -> Block.createCuboidShape(0, 0, 1, 8, upper ? 6 : 16, 15);
            case CENTER -> Block.createCuboidShape(0, upper ? 0 : 4, 7, 16, upper ? 4 : 16, 9);
        };
        if (connectsOutward(state, world, pos)) {
            // Join the outer half of each cabinet; the two halves close the bypass
            // between neighboring lanes, even while either lane is open.
            VoxelShape connector = state.get(PART) == Part.LEFT
                    ? Block.createCuboidShape(0, 0, 1, 8, upper ? 6 : 16, 15)
                    : Block.createCuboidShape(8, 0, 1, 16, upper ? 6 : 16, 15);
            shape = VoxelShapes.union(shape, connector);
        }
        return rotate(shape, state.get(FACING));
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(PART) != Part.CENTER) return getOutlineShape(state, world, pos, context);
        if (state.get(OPEN)) return VoxelShapes.empty();
        return rotate(Block.createCuboidShape(0, 0, 5, 16, state.get(HALF) == DoubleBlockHalf.LOWER ? 24 : 8, 11), state.get(FACING));
    }
    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        int turns = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        for (int i = 0; i < turns; i++) {
            VoxelShape[] next = {VoxelShapes.empty()};
            shape.forEachBox((a, b, c, d, e, f) -> next[0] = VoxelShapes.union(next[0], VoxelShapes.cuboid(1 - f, b, a, 1 - c, e, d)));
            shape = next[0];
        }
        return shape;
    }
}
