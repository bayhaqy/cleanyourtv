package com.gathin.tvhome;

/**
 * The plain logic behind the glance line (config.xml glance_line): Indonesian words for Open-Meteo's WMO
 * weather codes, the glyph to draw, the US AQI colour band and the next of the five daily prayers.
 * No Android types, so it is unit tested on the JVM.
 */
final class GlanceText {
    static final int GLYPH_NONE = 0, GLYPH_SUN = 1, GLYPH_MOON = 2, GLYPH_PARTLY = 3, GLYPH_CLOUD = 4,
            GLYPH_FOG = 5, GLYPH_RAIN = 6, GLYPH_STORM = 7;

    /** Fajr, Dhuhr, Asr, Maghrib, Isha, in that order everywhere in this class. */
    static final String[] PRAYERS = {"SUBUH", "DZUHUR", "ASHAR", "MAGHRIB", "ISYA"};

    private GlanceText() {}

    static String condition(int wmo) {
        switch (wmo) {
            case 0: return "Cerah";
            case 1: return "Cerah berawan";
            case 2: return "Berawan sebagian";
            case 3: return "Berawan";
            case 45: case 48: return "Kabut";
            case 51: case 53: case 55: case 56: case 57: return "Gerimis";
            case 61: case 63: case 66: case 80: case 81: return "Hujan";
            case 65: case 67: case 82: return "Hujan lebat";
            case 71: case 73: case 75: case 77: case 85: case 86: return "Salju";
            case 95: case 96: case 99: return "Badai petir";
            default: return "";
        }
    }

    static int glyph(int wmo, boolean day) {
        if (wmo == 0 || wmo == 1) return day ? GLYPH_SUN : GLYPH_MOON;
        if (wmo == 2) return GLYPH_PARTLY;
        if (wmo == 3) return GLYPH_CLOUD;
        if (wmo == 45 || wmo == 48) return GLYPH_FOG;
        if (wmo >= 95) return GLYPH_STORM;
        if (wmo >= 51 && wmo <= 86) return GLYPH_RAIN;
        return GLYPH_NONE;
    }

    /** US AQI bands: good, moderate, sensitive groups, unhealthy, very unhealthy, hazardous. */
    static int aqiColor(int aqi) {
        if (aqi <= 50) return 0xFF4ADE80;
        if (aqi <= 100) return 0xFFFACC15;
        if (aqi <= 150) return 0xFFFB923C;
        if (aqi <= 200) return 0xFFF87171;
        if (aqi <= 300) return 0xFFC084FC;
        return 0xFFBE123C;
    }

    /**
     * {index into PRAYERS, minutes until it} for the first prayer at or after minuteOfDay, or tomorrow's
     * Subuh (taken as today's time, a minute off at most) once Isha has passed. Null when the times are unusable.
     */
    static int[] nextPrayer(String[] hhmm, int minuteOfDay) {
        if (hhmm == null || hhmm.length != PRAYERS.length) return null;
        int[] mins = new int[hhmm.length];
        for (int i = 0; i < hhmm.length; i++) {
            mins[i] = parse(hhmm[i]);
            if (mins[i] < 0) return null;
        }
        for (int i = 0; i < mins.length; i++) {
            if (mins[i] >= minuteOfDay) return new int[] {i, mins[i] - minuteOfDay};
        }
        return new int[] {0, 24 * 60 - minuteOfDay + mins[0]};
    }

    /** "−2:49" before a prayer, "SEKARANG" in its first minute. */
    static String countdown(int minutes) {
        if (minutes <= 0) return "SEKARANG";
        return "−" + (minutes / 60) + ":" + (minutes % 60 < 10 ? "0" : "") + (minutes % 60);
    }

    /** "HH:mm" (Aladhan may append " (WIB)") to minutes since midnight, or -1. */
    static int parse(String s) {
        if (s == null || s.length() < 5 || s.charAt(2) != ':') return -1;
        try {
            int h = Integer.parseInt(s.substring(0, 2)), m = Integer.parseInt(s.substring(3, 5));
            return h < 24 && m < 60 ? h * 60 + m : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
