package dev.kirisame.accesscontrol.client;

import dev.kirisame.accesscontrol.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public final class SlidingDoorRenderer implements BlockEntityRenderer<SlidingDoorBlockEntity, SlidingDoorRenderer.State> {
    public record Face(List<PanelClipping.Vertex> vertices, Direction normal) { }
    public static final class State extends BlockEntityRenderState {
        List<Face> faces = List.of();
        boolean translucent;
        public List<Face> faces() { return faces; }
    }
    private final BlockRenderManager models;
    public SlidingDoorRenderer(BlockEntityRendererFactory.Context context) { models = context.renderManager(); }
    @Override public State createRenderState() { return new State(); }

    @Override public void updateRenderState(SlidingDoorBlockEntity entity, State state, float tickProgress,
            Vec3d cameraPos, @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(entity, state, tickProgress, cameraPos, crumblingOverlay);
        BlockState door = entity.getCachedState();
        Direction facing = door.get(DoorBlock.FACING);
        List<Face> faces = new ArrayList<>();
        float slide = entity.slide(tickProgress);
        state.translucent = door.isOf(AccessControl.GLASS_DOOR);
        if (door.isOf(AccessControl.SUBWAY_GATE)) {
            switch (door.get(SubwayGateBlock.PART)) {
                case LEFT -> append(entity, door, AccessControl.GATE_LEFT_POST, 0, null, faces);
                case RIGHT -> append(entity, door, AccessControl.GATE_RIGHT_POST, 0, null, faces);
                case CENTER -> {
                    append(entity, door, AccessControl.GATE_LEFT_WING, slide * .5f, facing.rotateYCounterclockwise(), faces);
                    append(entity, door, AccessControl.GATE_RIGHT_WING, slide * .5f, facing.rotateYClockwise(), faces);
                }
            }
        } else {
            DoorPanelBlock panel = door.isOf(AccessControl.GLASS_DOOR) ? AccessControl.GLASS_PANEL
                    : door.isOf(AccessControl.INDUSTRIAL_DOOR) ? AccessControl.INDUSTRIAL_PANEL
                    : door.isOf(AccessControl.TECH_WINDOW_DOOR) ? AccessControl.TECH_WINDOW_PANEL : AccessControl.TECH_PANEL;
            Direction direction = door.get(DoorBlock.HINGE) == DoorHinge.LEFT
                    ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
            append(entity, door, panel, slide, direction, faces);
        }
        // Capture geometry during extraction; rendering never reads the live world.
        state.faces = List.copyOf(faces);
    }

    private void append(SlidingDoorBlockEntity entity, BlockState door, DoorPanelBlock panel, float offset,
            @Nullable Direction direction, List<Face> output) {
        float dx = direction == null ? 0 : direction.getOffsetX() * offset;
        float dz = direction == null ? 0 : direction.getOffsetZ() * offset;
        List<PanelClipping.Bounds> occluders = new ArrayList<>();
        if (direction != null && offset > 0 && entity.getWorld() != null) {
            BlockPos target = entity.getPos().offset(direction);
            var world = entity.getWorld();
            // Subtract solid volumes, including slabs. A tiny margin removes coplanar
            // door/wall faces rather than trying to hide z-fighting with a depth offset.
            for (Box box : world.getBlockState(target).getCollisionShape(world, target).getBoundingBoxes()) {
                Box b = box.offset(direction.getOffsetX(), 0, direction.getOffsetZ()).expand(.0005);
                occluders.add(new PanelClipping.Bounds((float) b.minX, (float) b.minY, (float) b.minZ,
                        (float) b.maxX, (float) b.maxY, (float) b.maxZ));
            }
        }
        BlockState modelState = panel.getDefaultState().with(DoorPanelBlock.FACING, door.get(DoorBlock.FACING))
                .with(DoorPanelBlock.HALF, door.get(DoorBlock.HALF));
        for (var part : models.getModel(modelState).getParts(Random.create(42))) {
            for (BakedQuad quad : part.getQuads(null)) appendQuad(quad, dx, dz, occluders, output);
            for (Direction side : Direction.values())
                for (BakedQuad quad : part.getQuads(side)) appendQuad(quad, dx, dz, occluders, output);
        }
    }
    private void appendQuad(BakedQuad quad, float dx, float dz, List<PanelClipping.Bounds> boxes, List<Face> output) {
        int[] data = quad.vertexData();
        int stride = data.length / 4;
        List<PanelClipping.Vertex> vertices = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int base = i * stride;
            vertices.add(new PanelClipping.Vertex(Float.intBitsToFloat(data[base]) + dx,
                    Float.intBitsToFloat(data[base + 1]), Float.intBitsToFloat(data[base + 2]) + dz,
                    Float.intBitsToFloat(data[base + 4]), Float.intBitsToFloat(data[base + 5])));
        }
        List<List<PanelClipping.Vertex>> polygons = List.of(vertices);
        for (var box : boxes) {
            List<List<PanelClipping.Vertex>> next = new ArrayList<>();
            for (var polygon : polygons) next.addAll(PanelClipping.subtract(polygon, box));
            polygons = next;
        }
        for (var polygon : polygons) output.add(new Face(List.copyOf(polygon), quad.face()));
    }

    @Override public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.faces.isEmpty()) return;
        var layer = state.translucent ? RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                : RenderLayer.getEntityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        List<Face> faces = state.faces;
        int light = state.lightmapCoordinates;
        queue.getBatchingQueue(0).submitCustom(matrices, layer, (entry, consumer) -> {
            for (Face face : faces) {
                List<PanelClipping.Vertex> v = face.vertices();
                for (int i = 1; i < v.size() - 1; i++) {
                    emit(consumer, entry, v.getFirst(), face.normal(), light);
                    emit(consumer, entry, v.get(i), face.normal(), light);
                    emit(consumer, entry, v.get(i + 1), face.normal(), light);
                    emit(consumer, entry, v.get(i + 1), face.normal(), light);
                }
            }
        });
    }
    private static void emit(VertexConsumer consumer, MatrixStack.Entry matrix, PanelClipping.Vertex v, Direction normal, int light) {
        float shade = switch (normal) { case DOWN -> .5f; case UP -> 1; case EAST, WEST -> .8f; default -> .9f; };
        consumer.vertex(matrix.getPositionMatrix(), v.x(), v.y(), v.z()).color(shade, shade, shade, 1)
                .texture(v.u(), v.v()).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(matrix, normal.getOffsetX(), normal.getOffsetY(), normal.getOffsetZ());
    }
    @Override public boolean rendersOutsideBoundingBox() { return true; }
}
