package dev.che1s.betalands.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Rect2iTest {
    @Test
    public void normalizesAndRoundTrips() {
        Rect2i rectangle = new Rect2i(20, 30, -5, 10);
        assertEquals(-5, rectangle.getMinX());
        assertEquals(20, rectangle.getMaxX());
        assertEquals(10, rectangle.getMinZ());
        assertEquals(30, rectangle.getMaxZ());
        assertEquals(rectangle.serialize(), Rect2i.parse(rectangle.serialize()).serialize());
    }

    @Test
    public void detectsBoundaryIntersections() {
        Rect2i first = new Rect2i(0, 0, 15, 15);
        assertTrue(first.intersects(new Rect2i(15, 15, 30, 30)));
        assertFalse(first.intersects(new Rect2i(16, 0, 31, 15)));
        assertEquals(256L, first.area());
    }
}
