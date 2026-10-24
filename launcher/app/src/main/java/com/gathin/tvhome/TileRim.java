package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A tile's glass edge, drawn over the banner as its foreground: a hairline rim that is bright along the top and fades
 * towards the bottom, like light catching the edge of a pane. Same corners as the tile's outline (AppAdapter.cornerRadius).
 */
final class TileRim extends Drawable {
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;

    TileRim(float density) {
        this.density = density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, density));
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rim.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x5CFFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float half = rim.getStrokeWidth() / 2f;
        rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
        float r = AppAdapter.cornerRadius(b.width(), density) - half;
        c.drawRoundRect(rect, r, r, rim);
    }

    @Override public void setAlpha(int alpha) { rim.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { rim.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
