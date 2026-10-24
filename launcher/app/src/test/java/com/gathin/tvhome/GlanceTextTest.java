package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.*;

public class GlanceTextTest {
    private static final String[] TIMES = {"04:10", "11:38", "14:51", "17:47", "18:58"};

    private static int at(int h, int m) { return h * 60 + m; }

    @Test public void conditionWordsAreIndonesian() {
        assertEquals("Cerah", GlanceText.condition(0));
        assertEquals("Berawan", GlanceText.condition(3));
        assertEquals("Kabut", GlanceText.condition(45));
        assertEquals("Gerimis", GlanceText.condition(53));
        assertEquals("Hujan", GlanceText.condition(63));
        assertEquals("Hujan lebat", GlanceText.condition(82));
        assertEquals("Badai petir", GlanceText.condition(95));
        assertEquals("", GlanceText.condition(-1));
    }

    @Test public void glyphFollowsCodeAndDaylight() {
        assertEquals(GlanceText.GLYPH_SUN, GlanceText.glyph(0, true));
        assertEquals(GlanceText.GLYPH_MOON, GlanceText.glyph(0, false));
        assertEquals(GlanceText.GLYPH_PARTLY, GlanceText.glyph(2, true));
        assertEquals(GlanceText.GLYPH_CLOUD, GlanceText.glyph(3, true));
        assertEquals(GlanceText.GLYPH_FOG, GlanceText.glyph(48, true));
        assertEquals(GlanceText.GLYPH_RAIN, GlanceText.glyph(61, false));
        assertEquals(GlanceText.GLYPH_STORM, GlanceText.glyph(96, true));
    }

    @Test public void aqiBandsUseUsScale() {
        assertEquals(0xFF4ADE80, GlanceText.aqiColor(42));
        assertEquals(0xFFFACC15, GlanceText.aqiColor(100));
        assertEquals(0xFFFB923C, GlanceText.aqiColor(101));
        assertEquals(0xFFF87171, GlanceText.aqiColor(165));
        assertEquals(0xFFC084FC, GlanceText.aqiColor(250));
        assertEquals(0xFFBE123C, GlanceText.aqiColor(400));
    }

    @Test public void nextPrayerIsTheFirstStillAhead() {
        int[] n = GlanceText.nextPrayer(TIMES, at(8, 49));
        assertArrayEquals(new int[] {1, 169}, n);              // Dzuhur in 2:49
        assertArrayEquals(new int[] {2, 0}, GlanceText.nextPrayer(TIMES, at(14, 51)));
        assertArrayEquals(new int[] {0, 60}, GlanceText.nextPrayer(TIMES, at(3, 10)));
    }

    @Test public void afterIshaItIsTomorrowsSubuh() {
        assertArrayEquals(new int[] {0, 60 + 4 * 60 + 10 - 59}, GlanceText.nextPrayer(TIMES, at(23, 59)));
        assertArrayEquals(new int[] {0, 5 * 60 + 1 + 4 * 60 + 10}, GlanceText.nextPrayer(TIMES, at(18, 59)));
    }

    @Test public void badTimesGiveNothing() {
        assertNull(GlanceText.nextPrayer(new String[] {"04:10"}, at(8, 0)));
        assertNull(GlanceText.nextPrayer(new String[] {"x", "11:38", "14:51", "17:47", "18:58"}, at(8, 0)));
        assertNull(GlanceText.nextPrayer(null, at(8, 0)));
    }

    @Test public void countdownIsHoursAndMinutes() {
        assertEquals("−2:49", GlanceText.countdown(169));
        assertEquals("−0:05", GlanceText.countdown(5));
        assertEquals("−10:00", GlanceText.countdown(600));
        assertEquals("SEKARANG", GlanceText.countdown(0));
    }

    @Test public void prayerNames() {
        assertEquals("SUBUH", GlanceText.PRAYERS[0]);
        assertEquals("ISYA", GlanceText.PRAYERS[4]);
    }
}
