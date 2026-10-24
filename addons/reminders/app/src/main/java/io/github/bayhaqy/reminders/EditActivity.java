package io.github.bayhaqy.reminders;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Add or change one reminder with the remote: big hour and minute digits (Up/Down changes them, Left/Right moves
 * between them), the repeat and the weekday as chips, a label from presets or typed (or dictated with the mic key).
 */
public class EditActivity extends Activity {
    private Reminder r;
    private TextView hour, minute;
    private final List<TextView> repeats = new ArrayList<>(), days = new ArrayList<>(), labels = new ArrayList<>();
    private LinearLayout dayRow;
    private EditText custom;

    private static final int[] DAYS = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra(Scheduler.EXTRA_ID, 0);
        r = id == 0 ? null : Store.find(this, id);
        if (r == null) {
            r = new Reminder();
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MINUTE, 30);
            r.hour = c.get(Calendar.HOUR_OF_DAY);
            r.minute = c.get(Calendar.MINUTE) / 5 * 5;
            r.label = getResources().getStringArray(R.array.presets)[0];
            r.weekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        }
        LinearLayout page = Ui.page(this);
        page.addView(Ui.text(this, getString(r.id == 0 ? R.string.add : R.string.edit), 30, Ui.WHITE));

        page.addView(spaced(Ui.kicker(this, getString(R.string.edit_time)), 16));
        LinearLayout clock = Ui.row(this);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        hour = digit(true);
        minute = digit(false);
        clock.addView(hour);
        clock.addView(Ui.text(this, ":", 44, Ui.DIM));
        clock.addView(minute);
        page.addView(clock);

        page.addView(spaced(Ui.kicker(this, getString(R.string.edit_repeat)), 14));
        LinearLayout rep = Ui.row(this);
        int[] names = {R.string.repeat_once, R.string.repeat_daily, R.string.repeat_weekdays, R.string.repeat_weekly};
        for (int i = 0; i < names.length; i++) {
            int value = i;
            TextView chip = Ui.button(this, getString(names[i]), 15);
            chip.setOnClickListener(v -> { r.repeat = value; refresh(); });
            repeats.add(chip);
            rep.addView(chip, Ui.gap(this, 10));
        }
        page.addView(rep);

        dayRow = Ui.row(this);
        dayRow.setPadding(0, Ui.px(this, 10), 0, 0);
        String[] wd = new DateFormatSymbols(locale()).getShortWeekdays();
        for (int dow : DAYS) {
            TextView chip = Ui.button(this, wd[dow], 14);
            chip.setOnClickListener(v -> { r.weekday = dow; refresh(); });
            days.add(chip);
            dayRow.addView(chip, Ui.gap(this, 8));
        }
        page.addView(dayRow);

        page.addView(spaced(Ui.kicker(this, getString(R.string.edit_label)), 14));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(0, Ui.px(this, 6), Ui.px(this, 24), Ui.px(this, 6));
        LinearLayout presets = Ui.row(this);
        for (String p : getResources().getStringArray(R.array.presets)) {
            TextView chip = Ui.button(this, p, 15);
            chip.setOnClickListener(v -> { r.label = p; custom.setText(""); refresh(); });
            labels.add(chip);
            presets.addView(chip, Ui.gap(this, 10));
        }
        custom = new EditText(this);
        custom.setHint(R.string.label_hint);
        custom.setHintTextColor(Ui.DIM);
        custom.setTextColor(Ui.WHITE);
        custom.setTypeface(Ui.face(this));
        custom.setSingleLine(true);
        custom.setBackground(Ui.panel(this, Ui.CARD, Ui.LINE, 14));
        custom.setPadding(Ui.px(this, 16), Ui.px(this, 8), Ui.px(this, 16), Ui.px(this, 8));
        custom.setOnFocusChangeListener((v, has) -> custom.setBackground(Ui.panel(this, Ui.CARD, has ? Ui.WHITE : Ui.LINE, 14)));
        presets.addView(custom, new LinearLayout.LayoutParams(Ui.px(this, 240), -2));
        hs.addView(presets);
        page.addView(hs);

        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.px(this, 18), 0, 0);
        TextView save = Ui.button(this, getString(R.string.save), 17);
        save.setOnClickListener(v -> save());
        actions.addView(save, Ui.gap(this, 12));
        if (r.id != 0) {
            TextView del = Ui.button(this, getString(R.string.delete), 17);
            del.setOnClickListener(v -> { Store.delete(this, r.id); finish(); });
            actions.addView(del, Ui.gap(this, 12));
        }
        TextView cancel = Ui.button(this, getString(R.string.cancel), 17);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel);
        page.addView(actions);

        String presetMatch = null;
        for (String p : getResources().getStringArray(R.array.presets)) if (p.equals(r.label)) presetMatch = p;
        if (presetMatch == null) custom.setText(r.label);
        refresh();
        hour.requestFocus();
    }

    private TextView spaced(TextView t, float topDp) {
        t.setPadding(0, Ui.px(this, topDp), 0, Ui.px(this, 6));
        return t;
    }

    /** A big focusable number; Up and Down change it (holding the key repeats), OK moves on to the repeat row. */
    private TextView digit(boolean isHour) {
        TextView t = Ui.button(this, "", 44);
        t.setPadding(Ui.px(this, 22), Ui.px(this, 2), Ui.px(this, 22), Ui.px(this, 2));
        t.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                // Up and Down belong to the number, so OK is the way on to the next row.
                repeats.get(r.repeat).requestFocus();
                return true;
            }
            int step = code == KeyEvent.KEYCODE_DPAD_UP ? 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 0;
            if (step == 0) return false;
            if (isHour) r.hour = (r.hour + step + 24) % 24;
            else r.minute = (r.minute + step + 60) % 60;
            refresh();
            return true;
        });
        return t;
    }

    private void refresh() {
        hour.setText(String.format(Locale.ROOT, "%02d", r.hour));
        minute.setText(String.format(Locale.ROOT, "%02d", r.minute));
        for (int i = 0; i < repeats.size(); i++) select(repeats.get(i), i == r.repeat);
        dayRow.setVisibility(r.repeat == Reminder.WEEKLY ? android.view.View.VISIBLE : android.view.View.GONE);
        for (int i = 0; i < days.size(); i++) select(days.get(i), DAYS[i] == r.weekday);
        String[] p = getResources().getStringArray(R.array.presets);
        for (int i = 0; i < labels.size(); i++) select(labels.get(i), p[i].equals(r.label));
    }

    private static void select(TextView chip, boolean on) {
        chip.setSelected(on);
        Ui.style(chip);
    }

    private void save() {
        String typed = custom.getText().toString().trim();
        if (!typed.isEmpty()) r.label = typed;
        if (r.repeat == Reminder.ONCE) {
            // Today if that time is still ahead, else tomorrow.
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, r.hour);
            c.set(Calendar.MINUTE, r.minute);
            c.set(Calendar.SECOND, 0);
            if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
            r.date = Reminder.dateKey(c.getTimeInMillis(), TimeZone.getDefault());
        }
        r.enabled = true;
        Store.put(this, r);
        finish();
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }
}
