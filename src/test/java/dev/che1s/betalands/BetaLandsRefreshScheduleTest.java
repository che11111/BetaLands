package dev.che1s.betalands;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class BetaLandsRefreshScheduleTest {
    @Test
    public void schedulesDynmapAtNextLocalThreeAm() {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        now.clear();
        now.set(2026, Calendar.NOVEMBER, 10, 2, 30, 0);
        assertEquals(30L * 60L * 20L,
                BetaLandsPlugin.ticksUntilNextRefresh(3, 0, now));

        now.set(Calendar.HOUR_OF_DAY, 3);
        now.set(Calendar.MINUTE, 0);
        assertEquals(24L * 60L * 60L * 20L,
                BetaLandsPlugin.ticksUntilNextRefresh(3, 0, now));

        now.set(Calendar.HOUR_OF_DAY, 4);
        assertEquals(23L * 60L * 60L * 20L,
                BetaLandsPlugin.ticksUntilNextRefresh(3, 0, now));
    }
}
