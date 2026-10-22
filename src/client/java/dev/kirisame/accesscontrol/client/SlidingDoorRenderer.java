package dev.kirisame.accesscontrol.client;

import dev.kirisame.accesscontrol.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity, SlidingDoorRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        BlockState panel;
        float offsetX;
        float offsetZ;
    }
    public SlidingDoorRenderer(BlockEntityRendererFactory.Context context) { }
    @Override public State createRenderState() { return new State(); }

    @Override public void updateRenderState(SlidingDoorBlockEntity entity, State state, float tickProgress,
            Vec3d cameraPos, @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        BlockState door = entity.getCachedState();
        Direction facing = door.get(DoorBlock.FACING);
        DoorPanelBlock panel = door.isOf(AccessControl.TECH_WINDOW_DOOR)
                ? AccessControl.TECH_WINDOW_PANEL : AccessControl.TECH_PANEL;
        state.panel = panel.getDefaultState().with(DoorPanelBlock.FACING, facing)
                .with(DoorPanelBlock.HALF, door.get(DoorBlock.HALF));
        Direction slideDirection = door.get(DoorBlock.HINGE) == DoorHinge.LEFT
                ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
        float offset = entity.slide(tickProgress);
        state.offsetX = slideDirection.getOffsetX() * offset;
        state.offsetZ = slideDirection.getOffsetZ() * offset;
    }
    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        matrices.push();
        matrices.translate(state.offsetX, 0, state.offsetZ);
        queue.getBatchingQueue(0).submitBlock(matrices, state.panel, state.lightmapCoordinates, OverlayTexture.DEFAULT_UV, 0);
        matrices.pop();
    }
    @Override public boolean rendersOutsideBoundingBox() { return true; }
}
