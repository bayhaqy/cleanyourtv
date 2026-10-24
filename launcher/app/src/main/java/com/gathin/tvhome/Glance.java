package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The glance line under the clock (config.xml glance_line): weather and US AQI from Open-Meteo, the next prayer
 * from Aladhan, and RAM and storage. Network is used only from refreshIfDue(), which the stats thread calls while
 * Home is resumed: weather at most every 30 minutes (5 after a failure), prayer times once a day. The last good
 * values are kept in preferences, so the line survives a restart or a dropped connection.
 */
final class Glance {
    private static final long WEATHER_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L;
    private static final String[] KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final int LABEL = 0x8CFFFFFF, VALUE = 0xF2FFFFFF, MUTED = 0xA6FFFFFF;

    private final SharedPreferences prefs;
    private final String lat, lon, method;

    Glance(Context context) {
        prefs = context.getSharedPreferences("glance", Context.MODE_PRIVATE);
        lat = context.getString(R.string.latitude).trim();
        lon = context.getString(R.string.longitude).trim();
        method = context.getString(R.string.prayer_method).trim();
    }

    /** Off the main thread only. */
    void refreshIfDue() {
        if (lat.isEmpty() || lon.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("w_ts", 0) > WEATHER_MS && now - prefs.getLong("w_try", 0) > RETRY_MS) {
            prefs.edit().putLong("w_try", now).apply();
            try {
                JSONObject cur = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day&timezone=auto").getJSONObject("current");
                SharedPreferences.Editor e = prefs.edit()
                        .putInt("w_temp", (int) Math.round(cur.getDouble("temperature_2m")))
                        .putInt("w_code", cur.getInt("weather_code"))
                        .putBoolean("w_day", cur.optInt("is_day", 1) == 1)
                        .putLong("w_ts", now);
                try {
                    JSONObject aq = get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat
                            + "&longitude=" + lon + "&current=us_aqi").getJSONObject("current");
                    e.putInt("aqi", aq.isNull("us_aqi") ? -1 : aq.getInt("us_aqi"));
                } catch (Exception ignored) {
                    // Weather without the AQI is still worth showing.
                }
                e.apply();
            } catch (Exception ignored) {
                // Keep the last values; the next try is RETRY_MS away.
            }
        }
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Calendar.getInstance().getTime());
        if (!today.equals(prefs.getString("p_day", "")) && now - prefs.getLong("p_try", 0) > RETRY_MS) {
            prefs.edit().putLong("p_try", now).apply();
            try {
                JSONObject t = get("https://api.aladhan.com/v1/timings/" + today + "?latitude=" + lat + "&longitude="
                        + lon + "&method=" + method).getJSONObject("data").getJSONObject("timings");
                SharedPreferences.Editor e = prefs.edit().putString("p_day", today);
                for (String k : KEYS) e.putString("p_" + k, t.getString(k));
                e.apply();
            } catch (Exception ignored) {
                // Yesterday's times stay; they are a minute or two off at most.
            }
        }
    }

    /** The whole line; ramPercent below 0 and an empty diskFree leave those sections out. */
    CharSequence line(int ramPercent, String diskFree) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (prefs.contains("w_ts")) {
            int code = prefs.getInt("w_code", -1);
            int glyph = GlanceText.glyph(code, prefs.getBoolean("w_day", true));
            if (glyph != GlanceText.GLYPH_NONE) {
                span(b, " ", new GlanceSpans.Glyph(glyph));
                b.append(' ');
            }
            span(b, prefs.getInt("w_temp", 0) + "°", new ForegroundColorSpan(VALUE), new RelativeSizeSpan(1.18f));
            String words = GlanceText.condition(code);
            if (!words.isEmpty()) span(b, "  " + words, new ForegroundColorSpan(MUTED));
            int aqi = prefs.getInt("aqi", -1);
            if (aqi >= 0) {
                divider(b);
                label(b, "AQI");
                span(b, " " + aqi, new ForegroundColorSpan(VALUE));
                span(b, " ", new GlanceSpans.Mark(true, GlanceText.aqiColor(aqi)));
            }
        }
        String[] times = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) times[i] = prefs.getString("p_" + KEYS[i], null);
        Calendar now = Calendar.getInstance();
        int[] next = GlanceText.nextPrayer(times, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
        if (next != null) {
            divider(b);
            label(b, GlanceText.PRAYERS[next[0]]);
            span(b, " " + times[next[0]].substring(0, 5), new ForegroundColorSpan(VALUE));
            span(b, "  " + GlanceText.countdown(next[1]), new ForegroundColorSpan(MUTED));
        }
        if (ramPercent >= 0) {
            divider(b);
            label(b, "RAM");
            span(b, " " + ramPercent + "%", new ForegroundColorSpan(VALUE));
        }
        if (!diskFree.isEmpty()) {
            divider(b);
            label(b, "DISK");
            span(b, " " + diskFree, new ForegroundColorSpan(VALUE));
        }
        return b;
    }

    private static void divider(SpannableStringBuilder b) {
        if (b.length() > 0) span(b, " ", new GlanceSpans.Mark(false, 0x40FFFFFF));
    }

    private static void label(SpannableStringBuilder b, String text) {
        span(b, text, new ForegroundColorSpan(LABEL), new RelativeSizeSpan(0.74f));
    }

    private static void span(SpannableStringBuilder b, String text, Object... spans) {
        int start = b.length();
        b.append(text);
        for (Object s : spans) b.setSpan(s, start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }
}
