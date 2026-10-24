package io.github.bayhaqy.reminders;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The shared look: near-black with a red glow, glass cards, white-on-focus controls, Outfit throughout. */
final class Ui {
    static final int RED = 0xFFE11D2E, WHITE = 0xFFF5F5F7, DIM = 0xFF8A8A93, LINE = 0xFF26262B, CARD = 0xCC141417;

    private Ui() {}

    static Typeface face(Context c) {
        return c.getResources().getFont(R.font.outfit);
    }

    static int px(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /** A page: the backdrop, then a vertical column with the usual margins. */
    static LinearLayout page(Activity a) {
        FrameLayout root = new FrameLayout(a);
        root.addView(new Backdrop(a), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        root.setClipChildren(false);
        col.setPadding(px(a, 48), px(a, 32), px(a, 48), px(a, 24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        a.setContentView(root);
        return col;
    }

    /** A row whose children may grow past its edges when focused (the 1.06 scale) without being cut off. */
    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face(c));
        return t;
    }

    static TextView kicker(Context c, CharSequence s) {
        TextView t = text(c, s, 11, DIM);
        t.setLetterSpacing(0.22f);
        t.setAllCaps(true);
        return t;
    }

    static GradientDrawable panel(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(c, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, px(c, 1)), stroke);
        return g;
    }

    /** A focusable control: glass at rest, red when selected, white with dark text when focused. */
    static TextView button(Context c, CharSequence label, float sp) {
        TextView b = text(c, label, sp, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(c, 20), px(c, 10), px(c, 20), px(c, 10));
        b.setFocusable(true);
        b.setClickable(true);
        style(b);
        b.setOnFocusChangeListener((v, has) -> {
            style((TextView) v);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        return b;
    }

    /** Restyles after a focus or selection change. */
    static void style(TextView b) {
        Context c = b.getContext();
        if (b.isFocused()) {
            b.setBackground(panel(c, WHITE, 0, 14));
            b.setTextColor(0xFF0A0A0C);
        } else if (b.isSelected()) {
            b.setBackground(panel(c, 0xE6B3001B, 0x66FF6B78, 14));
            b.setTextColor(WHITE);
        } else {
            b.setBackground(panel(c, CARD, LINE, 14));
            b.setTextColor(WHITE);
        }
    }

    static LinearLayout.LayoutParams gap(Context c, float endDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(px(c, endDp));
        return lp;
    }

    /** Near-black with one red glow at the top right and a faint white one at the bottom left. */
    static final class Backdrop extends View {
        private final Paint p = new Paint();

        Backdrop(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(0xFF0A0A0C);
            p.setShader(new RadialGradient(w * 0.85f, h * 0.05f, w * 0.55f, 0x66B3001B, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.05f, h, w * 0.4f, 0x2EFFFFFF, 0, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
    }
}
