package dev.kirisame.accesscontrol;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

/** Absolute server deadlines avoid old scheduled ticks shortening a new swipe. */
public class ReaderBlockEntity extends BlockEntity {
    long powerUntil;
    long feedbackUntil;

    public ReaderBlockEntity(BlockPos pos, BlockState state) {
        this(AccessControl.READER_ENTITY, pos, state);
    }
    protected ReaderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override protected void readData(ReadView view) {
        super.readData(view);
        powerUntil = view.getLong("power_until", 0);
        feedbackUntil = view.getLong("feedback_until", 0);
    }
    @Override protected void writeData(WriteView view) {
        super.writeData(view);
        view.putLong("power_until", powerUntil);
        view.putLong("feedback_until", feedbackUntil);
    }
}
