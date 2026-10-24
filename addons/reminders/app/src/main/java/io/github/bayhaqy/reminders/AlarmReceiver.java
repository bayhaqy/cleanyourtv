package io.github.bayhaqy.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** A reminder is due: wake the screen, show the alert over whatever is on, and line up the next occurrence. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        long id = intent.getLongExtra(Scheduler.EXTRA_ID, 0);
        boolean snooze = intent.getBooleanExtra(Scheduler.EXTRA_SNOOZE, false);
        Reminder r = Store.find(c, id);
        if (r == null) return;

        // Bring the panel out of standby; the alert keeps it on while it is shown.
        PowerManager pm = c.getSystemService(PowerManager.class);
        @SuppressWarnings("deprecation")
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP, "reminders:alert");
        wl.acquire(10_000);

        c.startActivity(new Intent(c, AlertActivity.class)
                .putExtra(Scheduler.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));

        if (!snooze) {
            if (r.repeat == Reminder.ONCE) {
                r.enabled = false;
                Store.put(c, r);
            } else {
                Scheduler.schedule(c, r);
            }
        }
    }
}
