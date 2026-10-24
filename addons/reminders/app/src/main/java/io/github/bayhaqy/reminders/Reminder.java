package io.github.bayhaqy.reminders;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** One reminder and its schedule. No Android types, so the timing rules are unit tested on the JVM. */
final class Reminder {
    static final int ONCE = 0, DAILY = 1, WEEKDAYS = 2, WEEKLY = 3;

    long id;
    String label = "";
    int hour, minute;
    int repeat = ONCE;
    /** Calendar.DAY_OF_WEEK, for WEEKLY. */
    int weekday = Calendar.MONDAY;
    /** yyyymmdd, for ONCE. */
    int date;
    boolean enabled = true;

    /** The next time this fires strictly after now, in epoch millis, or -1 when it never will again. */
    long next(long now, TimeZone tz) {
        if (!enabled) return -1;
        Calendar c = Calendar.getInstance(tz);
        if (repeat == ONCE) {
            c.clear();
            c.set(date / 10000, (date / 100) % 100 - 1, date % 100, hour, minute, 0);
            return c.getTimeInMillis() > now ? c.getTimeInMillis() : -1;
        }
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i <= 8; i++) {
            if (c.getTimeInMillis() > now && matchesDay(c.get(Calendar.DAY_OF_WEEK))) return c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    /** Whether it fires on this local date (for the calendar's dots); month is 1 to 12. */
    boolean occursOn(int year, int month, int day, TimeZone tz) {
        if (!enabled) return false;
        if (repeat == ONCE) return date == year * 10000 + month * 100 + day;
        Calendar c = Calendar.getInstance(tz);
        c.clear();
        c.set(year, month - 1, day);
        return matchesDay(c.get(Calendar.DAY_OF_WEEK));
    }

    private boolean matchesDay(int dow) {
        switch (repeat) {
            case DAILY: return true;
            case WEEKDAYS: return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY;
            case WEEKLY: return dow == weekday;
            default: return false;
        }
    }

    String timeText() {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    static int dateKey(long millis, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }
}
