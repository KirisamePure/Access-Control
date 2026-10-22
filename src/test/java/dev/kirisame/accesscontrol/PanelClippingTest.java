package dev.kirisame.accesscontrol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.kirisame.accesscontrol.PanelClipping.*;

class PanelClippingTest {
    private List<Vertex> face(float offset) {
        return List.of(new Vertex(offset, 0, 0, 0, 0), new Vertex(1 + offset, 0, 0, 1, 0),
                new Vertex(1 + offset, 1, 0, 1, 1), new Vertex(offset, 1, 0, 0, 1));
    }
    private float area(List<Vertex> polygon) {
        float sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            var a = polygon.get(i); var b = polygon.get((i + 1) % polygon.size());
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(sum) / 2;
    }
    @Test void fullOpenDoorIsHiddenIncludingCoplanarWallFace() {
        assertTrue(PanelClipping.subtract(face(1), new Bounds(1, 0, 0, 2, 1, 1)).isEmpty());
    }
    @Test void partialSlideRetainsExposedTextureWithoutStretching() {
        var polygons = PanelClipping.subtract(face(.5f), new Bounds(1, -1, -1, 2, 2, 1));
        assertEquals(.5f, polygons.stream().map(this::area).reduce(0f, Float::sum), .00001f);
        for (var polygon : polygons) for (var v : polygon) {
            assertTrue(v.x() <= 1);
            assertEquals(v.x() - .5f, v.u(), .00001f);
        }
    }
    @Test void slabOnlyHidesItsOccupiedHalf() {
        var polygons = PanelClipping.subtract(face(1), new Bounds(1, 0, -.1f, 2, .5f, 1));
        assertEquals(.5f, polygons.stream().map(this::area).reduce(0f, Float::sum), .00001f);
        for (var polygon : polygons) for (var v : polygon) assertTrue(v.y() >= .5f);
    }
    @Test void subtractingCentralVolumeKeepsAreaWithoutDuplicateFragments() {
        var polygons = PanelClipping.subtract(face(0), new Bounds(.25f, .25f, -.1f, .75f, .75f, .1f));
        assertEquals(.75f, polygons.stream().map(this::area).reduce(0f, Float::sum), .00001f);
    }
    @Test void emptyPocketNeverHidesDoor() {
        var polygons = PanelClipping.subtract(face(0), new Bounds(2, 2, 2, 3, 3, 3));
        assertEquals(1, polygons.stream().map(this::area).reduce(0f, Float::sum), .00001f);
    }
    @Test void fullCoverageWorksOnEveryNormalAxisAndNegativeSide() {
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
            final int normalAxis = axis;
            final int direction = sign;
            var polygon = face(0).stream().map(v -> {
                float[] p = {v.x(), v.y(), 0};
                p[normalAxis] += direction;
                return new Vertex(p[0], p[1], p[2], v.u(), v.v());
            }).toList();
            float[] min = {-.01f, -.01f, -.01f};
            float[] max = {1.01f, 1.01f, .01f};
            min[axis] += sign; max[axis] += sign;
            assertTrue(PanelClipping.subtract(polygon, new Bounds(min[0], min[1], min[2], max[0], max[1], max[2])).isEmpty());
        }
    }
}
