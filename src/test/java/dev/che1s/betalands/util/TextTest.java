package dev.che1s.betalands.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextTest {
    @Test
    public void landNamesAllowOnlyAsciiLettersDigitsUnderscoreAndDash() {
        assertTrue(Text.validName("Home_01-Test", 2, 24));
        assertTrue(Text.validName("AB", 2, 24));
        assertFalse(Text.validName("中文", 2, 24));
        assertFalse(Text.validName("Home Test", 2, 24));
        assertFalse(Text.validName("Home.Test", 2, 24));
        assertFalse(Text.validName("AAA.bb", 2, 24));
        assertFalse(Text.validName("Home!", 2, 24));
        assertFalse(Text.validName("A", 2, 24));
    }

    @Test
    public void normalizedTownyNamesPreserveCase() {
        assertEquals("Qing", Text.slugPreservingCase(" Qing "));
        assertEquals("SEN-44444", Text.slugPreservingCase("SEN-44444."));
        assertEquals("Home-Test", Text.slugPreservingCase("Home Test!"));
        assertEquals("AA-aa", Text.slugPreservingCase("AA--aa"));
    }
}
