package io.github.bayhaqy.reminders;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full screen over whatever is playing: the label large, the time, Snooze 10 minutes or Done. Sounds for a minute. */
public class AlertActivity extends Activity {
    private static final long SOUND_MS = 60_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Ringtone tone;
    private long id;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id = getIntent().getLongExtra(Scheduler.EXTRA_ID, 0);
        Reminder r = Store.find(this, id);
        String label = r == null ? getString(R.string.default_label) : r.label;

        LinearLayout page = Ui.page(this);
        page.setGravity(Gravity.CENTER);
        TextView kicker = Ui.kicker(this, getString(R.string.alert_kicker));
        kicker.setTextColor(Ui.RED);
        kicker.setGravity(Gravity.CENTER);
        page.addView(kicker);
        TextView big = Ui.text(this, label, 54, Ui.WHITE);
        big.setGravity(Gravity.CENTER);
        big.setPadding(0, Ui.px(this, 12), 0, Ui.px(this, 6));
        page.addView(big);
        TextView time = Ui.text(this, r == null ? "" : r.timeText(), 26, Ui.DIM);
        time.setGravity(Gravity.CENTER);
        page.addView(time);

        LinearLayout actions = Ui.row(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.px(this, 36), 0, 0);
        TextView snooze = Ui.button(this, getString(R.string.snooze), 19);
        snooze.setOnClickListener(v -> {
            Scheduler.snooze(this, id, 10);
            finish();
        });
        actions.addView(snooze, Ui.gap(this, 16));
        TextView done = Ui.button(this, getString(R.string.done), 19);
        done.setOnClickListener(v -> finish());
        actions.addView(done);
        page.addView(actions);
        snooze.requestFocus();
        ring();
    }

    private void ring() {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        tone = uri == null ? null : RingtoneManager.getRingtone(this, uri);
        if (tone == null) return;
        tone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        if (Build.VERSION.SDK_INT >= 28) tone.setLooping(true);
        tone.play();
        handler.postDelayed(this::silence, SOUND_MS);
    }

    private void silence() {
        if (tone != null && tone.isPlaying()) tone.stop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        silence();
        super.onDestroy();
    }
}
