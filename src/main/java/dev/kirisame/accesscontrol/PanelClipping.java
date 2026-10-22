package dev.kirisame.accesscontrol;

import java.util.ArrayList;
import java.util.List;

/** Subtracts solid volumes from textured polygons without moving or stretching UVs. */
public final class PanelClipping {
    private PanelClipping() { }
    public record Vertex(float x, float y, float z, float u, float v) {
        float axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
        Vertex lerp(Vertex other, float t) {
            return new Vertex(x + (other.x - x) * t, y + (other.y - y) * t,
                    z + (other.z - z) * t, u + (other.u - u) * t, v + (other.v - v) * t);
        }
    }
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float min(int axis) { return axis == 0 ? minX : axis == 1 ? minY : minZ; }
        float max(int axis) { return axis == 0 ? maxX : axis == 1 ? maxY : maxZ; }
    }

    public static List<List<Vertex>> subtract(List<Vertex> polygon, Bounds box) {
        List<List<Vertex>> visible = new ArrayList<>();
        List<Vertex> inside = polygon;
        for (int axis = 0; axis < 3 && inside.size() >= 3; axis++) {
            for (int side = 0; side < 2 && inside.size() >= 3; side++) {
                float boundary = side == 0 ? box.min(axis) : box.max(axis);
                boolean greater = side == 0;
                List<Vertex> outside = clip(inside, axis, boundary, greater, false);
                if (outside.size() >= 3) visible.add(List.copyOf(outside));
                inside = clip(inside, axis, boundary, greater, true);
            }
        }
        return visible;
    }

    private static List<Vertex> clip(List<Vertex> polygon, int axis, float boundary, boolean greater, boolean keepInside) {
        List<Vertex> result = new ArrayList<>();
        if (polygon.isEmpty()) return result;
        Vertex previous = polygon.getLast();
        boolean wasInside = accepts(previous.axis(axis), boundary, greater, keepInside);
        for (Vertex current : polygon) {
            boolean isInside = accepts(current.axis(axis), boundary, greater, keepInside);
            if (wasInside != isInside) {
                float t = (boundary - previous.axis(axis)) / (current.axis(axis) - previous.axis(axis));
                result.add(previous.lerp(current, t));
            }
            if (isInside) result.add(current);
            previous = current;
            wasInside = isInside;
        }
        return result;
    }

    private static boolean accepts(float value, float boundary, boolean greater, boolean inside) {
        boolean within = greater ? value >= boundary : value <= boundary;
        return inside == within;
    }
}
