package io.github.bayhaqy.reminders;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class ReminderTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Jakarta");

    /** 2026-10-24 is a Saturday. */
    private static long at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(y, m - 1, d, h, min, 0);
        return c.getTimeInMillis();
    }

    private static Reminder r(int repeat, int h, int m) {
        Reminder r = new Reminder();
        r.repeat = repeat;
        r.hour = h;
        r.minute = m;
        return r;
    }

    @Test public void dailyIsTodayIfStillAheadElseTomorrow() {
        long now = at(2026, 10, 24, 13, 0);
        assertEquals(at(2026, 10, 24, 20, 0), r(Reminder.DAILY, 20, 0).next(now, TZ));
        assertEquals(at(2026, 10, 25, 7, 0), r(Reminder.DAILY, 7, 0).next(now, TZ));
        assertEquals(at(2026, 10, 25, 13, 0), r(Reminder.DAILY, 13, 0).next(now, TZ));   // this very minute: next day
    }

    @Test public void weekdaysSkipTheWeekend() {
        assertEquals(at(2026, 10, 26, 7, 0), r(Reminder.WEEKDAYS, 7, 0).next(at(2026, 10, 24, 13, 0), TZ));
        assertEquals(at(2026, 10, 30, 7, 0), r(Reminder.WEEKDAYS, 7, 0).next(at(2026, 10, 30, 6, 0), TZ));
        assertEquals(at(2026, 11, 2, 7, 0), r(Reminder.WEEKDAYS, 7, 0).next(at(2026, 10, 30, 8, 0), TZ));
    }

    @Test public void weeklyFindsItsDay() {
        Reminder w = r(Reminder.WEEKLY, 8, 30);
        w.weekday = Calendar.WEDNESDAY;
        assertEquals(at(2026, 10, 28, 8, 30), w.next(at(2026, 10, 24, 13, 0), TZ));
        assertEquals(at(2026, 11, 4, 8, 30), w.next(at(2026, 10, 28, 9, 0), TZ));
    }

    @Test public void onceFiresOnItsDateOnly() {
        Reminder o = r(Reminder.ONCE, 9, 0);
        o.date = 20261030;
        assertEquals(at(2026, 10, 30, 9, 0), o.next(at(2026, 10, 24, 13, 0), TZ));
        assertEquals(-1, o.next(at(2026, 10, 30, 9, 30), TZ));
    }

    @Test public void disabledNeverFires() {
        Reminder d = r(Reminder.DAILY, 20, 0);
        d.enabled = false;
        assertEquals(-1, d.next(at(2026, 10, 24, 13, 0), TZ));
        assertFalse(d.occursOn(2026, 10, 24, TZ));
    }

    @Test public void occursOnMarksTheCalendar() {
        assertTrue(r(Reminder.DAILY, 7, 0).occursOn(2026, 10, 24, TZ));
        assertFalse(r(Reminder.WEEKDAYS, 7, 0).occursOn(2026, 10, 24, TZ));
        assertTrue(r(Reminder.WEEKDAYS, 7, 0).occursOn(2026, 10, 26, TZ));
        Reminder w = r(Reminder.WEEKLY, 8, 0);
        w.weekday = Calendar.SATURDAY;
        assertTrue(w.occursOn(2026, 10, 31, TZ));
        Reminder o = r(Reminder.ONCE, 9, 0);
        o.date = 20261030;
        assertTrue(o.occursOn(2026, 10, 30, TZ));
        assertFalse(o.occursOn(2026, 10, 31, TZ));
    }

    @Test public void dateKeyAndTimeText() {
        assertEquals(20261030, Reminder.dateKey(at(2026, 10, 30, 23, 59), TZ));
        assertEquals("07:05", r(Reminder.DAILY, 7, 5).timeText());
    }
}
