package dev.che1s.betalands.integration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MapColorTest {
    @Test
    public void parsesQuotedHexAndBareHexColors() {
        assertEquals(0x40b4ff, MapColor.parse("#40B4FF"));
        assertEquals(0xffa640, MapColor.parse("FFA640"));
        assertEquals(0x123456, MapColor.parse("0x123456"));
    }

    @Test
    public void treatsAutoAndInvalidValuesAsAutomatic() {
        assertEquals(MapColor.AUTO, MapColor.parse("auto"));
        assertEquals(MapColor.AUTO, MapColor.parse(""));
        assertEquals(MapColor.AUTO, MapColor.parse("#12345"));
        assertEquals(MapColor.AUTO, MapColor.parse("not-a-color"));
    }
}
