package io.github.bayhaqy.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.TimeZone;

/**
 * One AlarmManager alarm clock per reminder (exact, and it wakes the TV from standby), plus one per snooze.
 * Nothing runs between alarms.
 */
final class Scheduler {
    static final String EXTRA_ID = "id", EXTRA_SNOOZE = "snooze";

    private Scheduler() {}

    static void scheduleAll(Context c) {
        for (Reminder r : Store.load(c)) schedule(c, r);
    }

    static void schedule(Context c, Reminder r) {
        long at = r.next(System.currentTimeMillis(), TimeZone.getDefault());
        PendingIntent pi = pending(c, r.id, false);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (at < 0) {
            am.cancel(pi);
            return;
        }
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show(c)), pi);
    }

    static void snooze(Context c, long id, int minutes) {
        long at = System.currentTimeMillis() + minutes * 60_000L;
        c.getSystemService(AlarmManager.class).setAlarmClock(new AlarmManager.AlarmClockInfo(at, show(c)), pending(c, id, true));
    }

    static void cancel(Context c, long id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pending(c, id, false));
        am.cancel(pending(c, id, true));
    }

    private static PendingIntent pending(Context c, long id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra(EXTRA_ID, id).putExtra(EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(c, (int) (id * 2 + (snooze ? 1 : 0)), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent show(Context c) {
        return PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    }
}
