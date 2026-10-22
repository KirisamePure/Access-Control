package dev.kirisame.accesscontrol;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class SlidingDoorBlockEntity extends BlockEntity {
    private float progress;
    private float previousProgress;

    public SlidingDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AccessControl.SLIDING_ENTITY, pos, state);
        // Chunk loads begin at the synchronized endpoint, with no replay of old transitions.
        progress = previousProgress = state.get(DoorBlock.OPEN) ? 1 : 0;
    }
    public void clientTick(BlockState state) {
        previousProgress = progress;
        progress = MathHelper.clamp(progress + (state.get(DoorBlock.OPEN) ? 0.1f : -0.1f), 0, 1);
    }
    public float slide(float tickProgress) {
        return AccessRules.smoothSlide(MathHelper.lerp(tickProgress, previousProgress, progress));
    }
}
