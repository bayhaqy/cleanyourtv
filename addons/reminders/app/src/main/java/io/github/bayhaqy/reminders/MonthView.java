package io.github.bayhaqy.reminders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A month you can walk with the remote. Left/Right move a day and cross into the next or previous month, Down moves
 * a week (into next month from the last row), Up moves a week and leaves for the buttons above from the first row,
 * Channel +/- jump a month, OK picks the day (again clears it). Today has a red ring, the picked day a red disc and
 * every day with a reminder a dot.
 */
final class MonthView extends View {
    interface Listener {
        /** The shown month changed (year, 0-based month). */
        void onMonth(int year, int month);

        /** A day was picked (yyyymmdd), or 0 when the pick was cleared. */
        void onPick(int dateKey);
    }

    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG), day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG), picked = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float d;
    private final TimeZone tz = TimeZone.getDefault();
    private List<Reminder> reminders = Collections.emptyList();
    private Listener listener;
    /** The day under the cursor (its month is the month shown), and the picked day or 0. */
    private int at, pick;

    MonthView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
        for (Paint p : new Paint[] {head, day}) {
            p.setTypeface(Ui.face(c));
            p.setTextAlign(Paint.Align.CENTER);
        }
        head.setColor(Ui.DIM);
        head.setTextSize(10 * d);
        head.setLetterSpacing(0.12f);
        day.setTextSize(14 * d);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * d);
        ring.setColor(Ui.RED);
        dot.setColor(Ui.RED);
        cursor.setColor(Ui.WHITE);
        picked.setColor(0xE6B3001B);
        at = Reminder.dateKey(System.currentTimeMillis(), tz);
    }

    void setListener(Listener l) { listener = l; }

    void set(List<Reminder> list) {
        reminders = list;
        invalidate();
    }

    int year() { return at / 10000; }

    int month() { return (at / 100) % 100 - 1; }

    /** Shows a month, keeping the cursor's day where the month allows. */
    void show(int year, int month) {
        int key = year * 10000 + (month + 1) * 100 + 1;
        int max = Reminder.calendar(key, tz).getActualMaximum(Calendar.DAY_OF_MONTH);
        moveTo(key - 1 + Math.min(at % 100, max));
    }

    void stepMonth(int delta) {
        Calendar c = Reminder.calendar(at, tz);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, delta);
        show(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
    }

    void today() {
        moveTo(Reminder.dateKey(System.currentTimeMillis(), tz));
    }

    void clearPick() {
        pick = 0;
        invalidate();
    }

    private void moveTo(int key) {
        boolean monthChanged = key / 100 != at / 100;
        at = key;
        invalidate();
        if (monthChanged && listener != null) listener.onMonth(year(), month());
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        int dom = at % 100;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: moveTo(Reminder.shift(at, -1, tz)); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveTo(Reminder.shift(at, 1, tz)); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: moveTo(Reminder.shift(at, 7, tz)); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (dom <= 7) return false;   // the first row: up to the month buttons
                moveTo(Reminder.shift(at, -7, tz));
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_PAGE_UP: stepMonth(-1); return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_PAGE_DOWN: stepMonth(1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                pick = pick == at ? 0 : at;
                invalidate();
                if (listener != null) listener.onPick(pick);
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        int year = year(), month = month(), today = Reminder.dateKey(System.currentTimeMillis(), tz);
        float top = 12 * d, cw = getWidth() / 7f, rh = (getHeight() - top - 6 * d) / 6.4f;
        String[] wd = new DateFormatSymbols(loc).getShortWeekdays();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                Calendar.SATURDAY, Calendar.SUNDAY};
        for (int i = 0; i < 7; i++) {
            String s = wd[order[i]];
            c.drawText(s.substring(0, Math.min(3, s.length())).toUpperCase(loc), cw * (i + 0.5f), top, head);
        }
        Calendar first = Reminder.calendar(year * 10000 + (month + 1) * 100 + 1, tz);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        float r = Math.min(cw, rh) * 0.42f;
        for (int n = 1; n <= days; n++) {
            int key = year * 10000 + (month + 1) * 100 + n;
            int cell = offset + n - 1;
            float cx = cw * (cell % 7 + 0.5f), cy = top + rh * (cell / 7 + 1), mid = cy - day.getTextSize() * 0.35f;
            boolean under = key == at && isFocused();
            if (under) {
                box.set(cx - r * 1.1f, mid - r, cx + r * 1.1f, mid + r);
                c.drawRoundRect(box, 8 * d, 8 * d, cursor);
            } else if (key == pick) {
                c.drawCircle(cx, mid, r, picked);
            }
            day.setColor(under ? 0xFF0A0A0C : key < today ? 0x73FFFFFF : Ui.WHITE);
            c.drawText(String.valueOf(n), cx, cy, day);
            if (key == today && !under) c.drawCircle(cx, mid, r, ring);
            for (Reminder rem : reminders) {
                if (rem.occursOn(year, month + 1, n, tz)) {
                    dot.setColor(under ? Ui.RED : key == pick ? Ui.WHITE : Ui.RED);
                    c.drawCircle(cx, cy + 6 * d, 2.2f * d, dot);
                    break;
                }
            }
        }
    }
}
