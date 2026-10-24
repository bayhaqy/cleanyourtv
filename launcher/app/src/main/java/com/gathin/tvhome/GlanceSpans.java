package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Inline marks for the glance line, drawn with the text's own paint so they sit on its baseline. */
final class GlanceSpans {
    private GlanceSpans() {}

    /** A thin line-art weather glyph, about 1.3 em square, in the text colour. */
    static final class Glyph extends ReplacementSpan {
        private final int kind;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int kind) {
            this.kind = kind;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(paint.getTextSize() * 1.55f);
        }

        @Override
        public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint paint) {
            float s = paint.getTextSize() * 1.3f;
            float cx = x + s / 2f, cy = y - paint.getTextSize() * 0.36f;
            stroke.setColor(paint.getColor());
            stroke.setStrokeWidth(Math.max(1.5f, s * 0.065f));
            c.save();
            c.translate(cx - s / 2f, cy - s / 2f);
            switch (kind) {
                case GlanceText.GLYPH_SUN: sun(c, s * 0.5f, s * 0.5f, s, 1f); break;
                case GlanceText.GLYPH_MOON: moon(c, s); break;
                case GlanceText.GLYPH_PARTLY:
                    Path cl = cloud(s, s * 0.12f, s * 0.14f);
                    c.save();
                    c.clipOutPath(cl);
                    sun(c, s * 0.36f, s * 0.36f, s, 0.72f);
                    c.restore();
                    c.drawPath(cl, stroke);
                    break;
                case GlanceText.GLYPH_CLOUD: c.drawPath(cloud(s, 0, s * 0.04f), stroke); break;
                case GlanceText.GLYPH_FOG:
                    c.drawPath(cloud(s, 0, -s * 0.12f), stroke);
                    c.drawLine(s * 0.14f, s * 0.84f, s * 0.86f, s * 0.84f, stroke);
                    c.drawLine(s * 0.28f, s * 0.98f, s * 0.72f, s * 0.98f, stroke);
                    break;
                case GlanceText.GLYPH_RAIN:
                    c.drawPath(cloud(s, 0, -s * 0.14f), stroke);
                    for (int i = 0; i < 3; i++) {
                        float dx = s * (0.32f + 0.18f * i);
                        c.drawLine(dx, s * 0.80f, dx - s * 0.07f, s * 0.98f, stroke);
                    }
                    break;
                case GlanceText.GLYPH_STORM:
                    c.drawPath(cloud(s, 0, -s * 0.14f), stroke);
                    Path bolt = new Path();
                    bolt.moveTo(s * 0.55f, s * 0.72f);
                    bolt.lineTo(s * 0.43f, s * 0.88f);
                    bolt.lineTo(s * 0.56f, s * 0.88f);
                    bolt.lineTo(s * 0.45f, s * 1.04f);
                    c.drawPath(bolt, stroke);
                    break;
                default: break;
            }
            c.restore();
        }

        private void sun(Canvas c, float cx, float cy, float s, float k) {
            float r = s * 0.17f * k;
            c.drawCircle(cx, cy, r, stroke);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float in = r + s * 0.09f * k, out = r + s * 0.18f * k;
                c.drawLine(cx + (float) Math.cos(a) * in, cy + (float) Math.sin(a) * in,
                        cx + (float) Math.cos(a) * out, cy + (float) Math.sin(a) * out, stroke);
            }
        }

        private void moon(Canvas c, float s) {
            Path disc = new Path(), bite = new Path();
            disc.addCircle(s * 0.48f, s * 0.52f, s * 0.30f, Path.Direction.CW);
            bite.addCircle(s * 0.64f, s * 0.40f, s * 0.25f, Path.Direction.CW);
            disc.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(disc, stroke);
        }

        /** A cloud outline: three puffs on a flat base, nudged by dx, dy. */
        private static Path cloud(float s, float dx, float dy) {
            Path p = new Path(), part = new Path();
            p.addCircle(s * 0.36f + dx, s * 0.58f + dy, s * 0.16f, Path.Direction.CW);
            part.addCircle(s * 0.56f + dx, s * 0.48f + dy, s * 0.21f, Path.Direction.CW);
            p.op(part, Path.Op.UNION);
            part.reset();
            part.addCircle(s * 0.74f + dx, s * 0.60f + dy, s * 0.14f, Path.Direction.CW);
            p.op(part, Path.Op.UNION);
            part.reset();
            part.addRoundRect(new RectF(s * 0.20f + dx, s * 0.58f + dy, s * 0.88f + dx, s * 0.74f + dy),
                    s * 0.08f, s * 0.08f, Path.Direction.CW);
            p.op(part, Path.Op.UNION);
            return p;
        }
    }

    /** A small filled dot in one colour (the AQI band), or with dot false a hairline divider between sections. */
    static final class Mark extends ReplacementSpan {
        private final boolean dot;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Mark(boolean dot, int color) {
            this.dot = dot;
            paint.setColor(color);
            paint.setStrokeWidth(1f);
        }

        @Override
        public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(p.getTextSize() * (dot ? 0.75f : 2.2f));
        }

        @Override
        public void draw(Canvas c, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                         Paint p) {
            float t = p.getTextSize(), cy = y - t * 0.36f;
            if (dot) c.drawCircle(x + t * 0.45f, cy, t * 0.2f, paint);
            else c.drawLine(x + t * 1.1f, cy - t * 0.62f, x + t * 1.1f, cy + t * 0.62f, paint);
        }
    }
}
