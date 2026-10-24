package io.github.bayhaqy.reminders;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.Locale;

/** Jump to any month: the year in big digits (Up/Down, hold to run), then one of twelve month buttons. */
final class MonthPicker {
    interface Done {
        void pick(int year, int month);
    }

    private MonthPicker() {}

    static void show(Activity a, int year, int month, Done done) {
        Dialog dialog = new Dialog(a, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xF50A0A0C));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setClipChildren(false);
        TextView kicker = Ui.kicker(a, a.getString(R.string.pick_month));
        kicker.setGravity(Gravity.CENTER);
        col.addView(kicker);

        int[] y = {year};
        TextView[] current = {null};
        TextView yearView = Ui.button(a, String.valueOf(year), 40);
        yearView.setPadding(Ui.px(a, 28), Ui.px(a, 2), Ui.px(a, 28), Ui.px(a, 2));
        yearView.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                // Up and Down belong to the year, so OK goes on to the months.
                if (current[0] != null) current[0].requestFocus();
                return true;
            }
            int step = code == KeyEvent.KEYCODE_DPAD_UP ? 1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? -1 : 0;
            if (step == 0) return false;
            y[0] = Math.max(1970, Math.min(2200, y[0] + step));
            yearView.setText(String.valueOf(y[0]));
            return true;
        });
        LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(-2, -2);
        ylp.topMargin = Ui.px(a, 12);
        ylp.bottomMargin = Ui.px(a, 6);
        col.addView(yearView, ylp);
        TextView hint = Ui.text(a, a.getString(R.string.pick_year_hint), 12, Ui.DIM);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint);

        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(4);
        grid.setClipChildren(false);
        grid.setPadding(0, Ui.px(a, 18), 0, 0);
        Locale loc = a.getResources().getConfiguration().getLocales().get(0);
        String[] names = new DateFormatSymbols(loc).getMonths();
        for (int m = 0; m < 12; m++) {
            int mm = m;
            TextView b = Ui.button(a, names[m].substring(0, 1).toUpperCase(loc) + names[m].substring(1), 16);
            b.setMinWidth(Ui.px(a, 150));
            b.setSelected(m == month);
            Ui.style(b);
            b.setOnClickListener(v -> {
                dialog.dismiss();
                done.pick(y[0], mm);
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(Ui.px(a, 6), Ui.px(a, 6), Ui.px(a, 6), Ui.px(a, 6));
            grid.addView(b, lp);
            if (m == month) current[0] = b;
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(grid, glp);
        dialog.setContentView(col, new android.view.ViewGroup.LayoutParams(-1, -1));
        dialog.show();
        dialog.getWindow().setLayout(-1, -1);
        if (current[0] != null) current[0].requestFocus();
    }
}
