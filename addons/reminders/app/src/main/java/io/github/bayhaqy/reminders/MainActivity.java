package io.github.bayhaqy.reminders;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** The list of reminders with quick-add buttons, and this month's calendar beside it. */
public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView status, next;
    private MonthView month;
    private TextView add, listTitle, monthTitle;
    /** The day picked on the calendar (yyyymmdd) that the list is filtered to, or 0 for every reminder. */
    private int pickKey;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = Ui.page(this);

        LinearLayout head = Ui.row(this);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(this, getString(R.string.app_name), 34, Ui.WHITE));
        TextView sub = Ui.kicker(this, getString(R.string.subtitle));
        sub.setPadding(Ui.px(this, 16), 0, 0, Ui.px(this, 8));
        head.addView(sub);
        page.addView(head);

        LinearLayout quick = Ui.row(this);
        quick.setPadding(0, Ui.px(this, 18), 0, Ui.px(this, 6));
        add = Ui.button(this, getString(R.string.add), 16);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_DATE, pickKey)));
        quick.addView(add, Ui.gap(this, 14));
        quick.addView(quickButton(R.string.quick_15, 15), Ui.gap(this, 10));
        quick.addView(quickButton(R.string.quick_30, 30), Ui.gap(this, 10));
        quick.addView(quickButton(R.string.quick_60, 60), Ui.gap(this, 10));
        TextView tomorrow = Ui.button(this, getString(R.string.quick_tomorrow), 15);
        tomorrow.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 7);
            c.set(Calendar.MINUTE, 0);
            quickAdd(c.getTimeInMillis());
        });
        quick.addView(tomorrow);
        page.addView(quick);
        status = Ui.text(this, "", 13, Ui.DIM);
        page.addView(status);

        LinearLayout body = Ui.row(this);
        body.setPadding(0, Ui.px(this, 14), 0, 0);
        LinearLayout left = Ui.row(this);
        left.setOrientation(LinearLayout.VERTICAL);
        listTitle = Ui.kicker(this, getString(R.string.list_title));
        left.addView(listTitle);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(Ui.px(this, 6), Ui.px(this, 8), Ui.px(this, 6), Ui.px(this, 24));
        list = Ui.row(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        left.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.3f));

        LinearLayout right = Ui.row(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.px(this, 22), Ui.px(this, 18), Ui.px(this, 22), Ui.px(this, 18));
        right.setBackground(Ui.panel(this, Ui.CARD, Ui.LINE, 18));
        LinearLayout nav = Ui.row(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        month = new MonthView(this);
        TextView prev = Ui.button(this, "‹", 18);
        prev.setOnClickListener(v -> month.stepMonth(-1));
        nav.addView(prev, Ui.gap(this, 8));
        monthTitle = Ui.button(this, "", 16);
        monthTitle.setSingleLine(true);
        monthTitle.setPadding(Ui.px(this, 6), Ui.px(this, 10), Ui.px(this, 6), Ui.px(this, 10));
        monthTitle.setOnClickListener(v -> MonthPicker.show(this, month.year(), month.month(), month::show));
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView nextMonth = Ui.button(this, "›", 18);
        nextMonth.setOnClickListener(v -> month.stepMonth(1));
        LinearLayout.LayoutParams nlp = Ui.gap(this, 8);
        nlp.setMarginStart(Ui.px(this, 8));
        nav.addView(nextMonth, nlp);
        TextView today = Ui.button(this, getString(R.string.today), 14);
        today.setOnClickListener(v -> month.today());
        nav.addView(today);
        right.addView(nav);
        TextView calHint = Ui.text(this, getString(R.string.calendar_hint), 11, Ui.DIM);
        calHint.setPadding(0, Ui.px(this, 6), 0, 0);
        right.addView(calHint);
        month.setListener(new MonthView.Listener() {
            @Override public void onMonth(int y, int m) { monthTitle.setText(monthName(y, m)); }

            @Override public void onPick(int key) {
                pickKey = key;
                refresh();
            }
        });
        monthTitle.setText(monthName(month.year(), month.month()));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        mlp.topMargin = Ui.px(this, 10);
        right.addView(month, mlp);
        next = Ui.text(this, "", 14, Ui.WHITE);
        right.addView(next);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.setMarginStart(Ui.px(this, 24));
        body.addView(right, rlp);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        add.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private TextView quickButton(int label, int minutes) {
        TextView b = Ui.button(this, getString(label), 15);
        b.setOnClickListener(v -> quickAdd(System.currentTimeMillis() + minutes * 60_000L));
        return b;
    }

    private void quickAdd(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        Reminder r = new Reminder();
        r.label = getString(R.string.default_label);
        r.hour = c.get(Calendar.HOUR_OF_DAY);
        r.minute = c.get(Calendar.MINUTE);
        r.date = Reminder.dateKey(at, TimeZone.getDefault());
        Store.put(this, r);
        status.setText(getString(R.string.created, r.timeText()));
        refresh();
    }

    private void refresh() {
        List<Reminder> all = Store.load(this);
        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        List<Reminder> sorted = new ArrayList<>(all);
        Collections.sort(sorted, (a, b) -> {
            long x = a.next(now, tz), y = b.next(now, tz);
            if (x < 0 && y < 0) return Long.compare(b.id, a.id);
            if (x < 0) return 1;
            if (y < 0) return -1;
            return Long.compare(x, y);
        });
        list.removeAllViews();
        if (pickKey != 0) {
            // Filtered to one day: that day's reminders by time of day, and a way back to all of them.
            sorted.removeIf(r -> !r.occursOn(pickKey / 10000, (pickKey / 100) % 100, pickKey % 100, tz));
            sorted.sort((a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
            listTitle.setText(getString(R.string.list_for,
                    DateFormat.format(DateFormat.getBestDateTimePattern(locale(), "EEEdMMMyyyy"),
                            Reminder.calendar(pickKey, tz)).toString()));
            TextView showAll = Ui.button(this, getString(R.string.show_all), 14);
            showAll.setOnClickListener(v -> {
                pickKey = 0;
                month.clearPick();
                refresh();
            });
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
            alp.bottomMargin = Ui.px(this, 10);
            list.addView(showAll, alp);
            if (sorted.isEmpty()) list.addView(Ui.text(this, getString(R.string.none_on_day), 15, Ui.DIM));
        } else {
            listTitle.setText(getString(R.string.list_title));
            if (sorted.isEmpty()) list.addView(Ui.text(this, getString(R.string.empty), 15, Ui.DIM));
        }
        for (Reminder r : sorted) list.addView(row(r, r.next(now, tz)));
        month.set(all);
        Reminder first = sorted.isEmpty() ? null : sorted.get(0);
        long at = first == null ? -1 : first.next(now, tz);
        next.setText(at < 0 ? getString(R.string.none_next) : getString(R.string.next, first.label, when(at)));
    }

    private View row(Reminder r, long at) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(this, 18), Ui.px(this, 10), Ui.px(this, 18), Ui.px(this, 10));
        row.setFocusable(true);
        row.setClickable(true);
        TextView time = Ui.text(this, r.timeText(), 28, r.enabled ? Ui.WHITE : Ui.DIM);
        row.addView(time, Ui.gap(this, 18));
        LinearLayout words = Ui.row(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.text(this, r.label, 17, r.enabled ? Ui.WHITE : Ui.DIM);
        label.setSingleLine(true);
        words.addView(label);
        TextView sub = Ui.text(this, describe(r, at), 12, Ui.DIM);
        words.addView(sub);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setBackground(Ui.panel(this, Ui.CARD, Ui.LINE, 16));
        row.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? Ui.panel(this, Ui.WHITE, 0, 16) : Ui.panel(this, Ui.CARD, Ui.LINE, 16));
            int on = r.enabled ? Ui.WHITE : Ui.DIM;
            time.setTextColor(has ? 0xFF0A0A0C : on);
            label.setTextColor(has ? 0xFF0A0A0C : on);
            sub.setTextColor(has ? Ui.RED : Ui.DIM);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(120).start();
        });
        row.setOnClickListener(v -> startActivity(new Intent(this, EditActivity.class).putExtra(Scheduler.EXTRA_ID, r.id)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.px(this, 10);
        row.setLayoutParams(lp);
        return row;
    }

    private String describe(Reminder r, long at) {
        String rep;
        switch (r.repeat) {
            case Reminder.DAILY: rep = getString(R.string.repeat_daily); break;
            case Reminder.WEEKDAYS: rep = getString(R.string.repeat_weekdays); break;
            case Reminder.WEEKLY:
                rep = getString(R.string.every, new DateFormatSymbols(locale()).getWeekdays()[r.weekday]);
                break;
            default: rep = getString(R.string.repeat_once); break;
        }
        if (!r.enabled || at < 0) return rep + "  ·  " + getString(R.string.off);
        return rep + "  ·  " + when(at);
    }

    /** "Sab 24 Okt 14.30" in the TV's language. */
    private String when(long at) {
        String skeleton = DateFormat.getBestDateTimePattern(locale(), "EEEdMMMHHmm");
        return DateFormat.format(skeleton, at).toString();
    }

    private String monthName(int year, int month) {
        String n = new DateFormatSymbols(locale()).getMonths()[month];
        return n.substring(0, 1).toUpperCase(locale()) + n.substring(1) + " " + year;
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }
}
