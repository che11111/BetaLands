package dev.che1s.betalands.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import dev.che1s.betalands.model.Rect2i;

public class ClaimBoundaryTest {
    @Test
    public void mergesAdjacentRectanglesIntoOneOuterBoundary() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(0, 0, 15, 15),
                new Rect2i(16, 0, 31, 15)));

        assertEquals(1, components.size());
        ClaimBoundary.Boundary outer = components.get(0).getOuter();
        assertEquals(4, outer.size());
        assertEquals(512.0D, Math.abs(outer.getSignedArea()), 0.0D);
        assertTrue(components.get(0).getHoles().isEmpty());
    }

    @Test
    public void keepsDisconnectedAreasSeparate() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(0, 0, 15, 15),
                new Rect2i(32, 0, 47, 15)));

        assertEquals(2, components.size());
    }

    @Test
    public void preservesInteriorHole() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(0, 0, 47, 15),
                new Rect2i(0, 16, 15, 31),
                new Rect2i(32, 16, 47, 31),
                new Rect2i(0, 32, 47, 47)));

        assertEquals(1, components.size());
        ClaimBoundary.Component component = components.get(0);
        assertEquals(2304.0D, component.getOuter().getSignedArea(), 0.0D);
        assertEquals(1, component.getHoles().size());
        assertEquals(-256.0D, component.getHoles().get(0).getSignedArea(), 0.0D);
    }

    @Test
    public void leavesDiagonalOnlyAreasDisconnected() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(0, 0, 15, 15),
                new Rect2i(16, 16, 31, 31)));

        assertEquals(2, components.size());
    }

    @Test
    public void keepsConcaveNotchOutOfOuterBoundary() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(0, 0, 31, 15),
                new Rect2i(0, 16, 15, 31)));

        assertEquals(1, components.size());
        ClaimBoundary.Component component = components.get(0);
        assertEquals(6, component.getOuter().size());
        assertEquals(768.0D, component.getOuter().getSignedArea(), 0.0D);
        assertTrue(component.getHoles().isEmpty());
    }

    @Test
    public void emitsOnlyAxisAlignedOutlineEdges() {
        List<ClaimBoundary.Component> components = ClaimBoundary.fromAreas(Arrays.asList(
                new Rect2i(-80, -48, -65, -33),
                new Rect2i(-64, -48, -49, -33),
                new Rect2i(-80, -64, -65, -49),
                new Rect2i(-48, -32, -33, -17)));

        for (ClaimBoundary.Component component : components) {
            assertAxisAligned(component.getOuter());
            for (ClaimBoundary.Boundary hole : component.getHoles()) {
                assertAxisAligned(hole);
            }
        }
    }

    private void assertAxisAligned(ClaimBoundary.Boundary boundary) {
        for (int index = 0; index < boundary.size(); index++) {
            int next = (index + 1) % boundary.size();
            double x1 = boundary.getX(index);
            double z1 = boundary.getZ(index);
            double x2 = boundary.getX(next);
            double z2 = boundary.getZ(next);
            assertTrue(x1 == x2 || z1 == z2);
            assertFalse(x1 == x2 && z1 == z2);
        }
    }
}
