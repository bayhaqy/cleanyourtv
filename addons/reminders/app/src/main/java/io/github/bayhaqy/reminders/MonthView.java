package io.github.bayhaqy.reminders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** This month at a glance: today ringed in red, a dot under every day with a reminder. Display only. */
final class MonthView extends View {
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Reminder> reminders = java.util.Collections.emptyList();
    private final float d;

    MonthView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[] {title, head, day}) {
            p.setTypeface(Ui.face(c));
            p.setTextAlign(Paint.Align.CENTER);
        }
        title.setColor(Ui.WHITE);
        title.setTextSize(17 * d);
        title.setTextAlign(Paint.Align.LEFT);
        head.setColor(Ui.DIM);
        head.setTextSize(10 * d);
        head.setLetterSpacing(0.12f);
        day.setTextSize(14 * d);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.5f * d);
        ring.setColor(Ui.RED);
        dot.setColor(Ui.RED);
    }

    void set(List<Reminder> list) {
        reminders = list;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        TimeZone tz = TimeZone.getDefault();
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        Calendar now = Calendar.getInstance(tz);
        int year = now.get(Calendar.YEAR), month = now.get(Calendar.MONTH), today = now.get(Calendar.DAY_OF_MONTH);
        String name = new DateFormatSymbols(loc).getMonths()[month];
        c.drawText(name.substring(0, 1).toUpperCase(loc) + name.substring(1) + " " + year, 0, title.getTextSize(), title);

        float top = title.getTextSize() + 18 * d, cw = getWidth() / 7f, rh = (getHeight() - top - 14 * d) / 6.5f;
        // Monday first, as Indonesian and most calendars print it.
        String[] wd = new DateFormatSymbols(loc).getShortWeekdays();
        int[] order = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY,
                Calendar.SATURDAY, Calendar.SUNDAY};
        for (int i = 0; i < 7; i++) {
            String s = wd[order[i]];
            c.drawText(s.substring(0, Math.min(3, s.length())).toUpperCase(loc), cw * (i + 0.5f), top, head);
        }
        Calendar first = Calendar.getInstance(tz);
        first.clear();
        first.set(year, month, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int n = 1; n <= days; n++) {
            int cell = offset + n - 1, col = cell % 7, row = cell / 7;
            float cx = cw * (col + 0.5f), cy = top + rh * (row + 1);
            day.setColor(n < today ? 0x66FFFFFF : Ui.WHITE);
            c.drawText(String.valueOf(n), cx, cy, day);
            if (n == today) c.drawCircle(cx, cy - day.getTextSize() * 0.35f, day.getTextSize() * 0.95f, ring);
            for (Reminder r : reminders) {
                if (r.occursOn(year, month + 1, n, tz)) {
                    c.drawCircle(cx, cy + 6 * d, 2.2f * d, dot);
                    break;
                }
            }
        }
    }
}
