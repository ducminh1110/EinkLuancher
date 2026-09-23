package com.eink.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * Draws one BOOX-style app tile: rounded square frame with a line glyph (or
 * the grayscale app icon) and the label underneath, plus the optional
 * round badge on the bottom-right corner of the frame.
 *
 * Proportions are taken from the reference screenshot: 78 px frame, 203 px
 * row pitch, 156 px column pitch, label 30 px below the frame.
 */
final class AppCell {
    static final float ROW = 203f, COL = 156f, MIN_ROW = 160f;

    private static final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final RectF r = new RectF();
    private static final android.graphics.Rect dst = new android.graphics.Rect();
    private static TextPaint label;
    private static float labelScale = -1;

    static {
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(Ui.INK);
        white.setColor(Ui.WHITE);
        white.setStyle(Paint.Style.FILL);
    }

    private AppCell() {}

    /** Content scale for a grid cell (1 = reference size). */
    static float scale(float cellW, float cellH) {
        float k = Math.min(cellH / Ui.px(ROW), cellW / Ui.px(COL - 6));
        return Math.max(0.62f, Math.min(1.12f, k));
    }

    static int iconSize(float k) {
        return Math.round(Ui.px(46) * k);
    }

    static TextPaint labelPaint(float k) {
        if (label == null || labelScale != k) {
            label = Ui.text(15.5f * Math.max(0.85f, Math.min(1f, k)), true);
            labelScale = k;
        }
        return label;
    }

    /**
     * @param glyph   line icon id or -1
     * @param icon    bitmap icon or null (used when glyph == -1)
     * @param text    label (already ellipsized by the caller if cached, else ellipsized here)
     */
    static void draw(Canvas c, float l, float t, float rr, float b, boolean pressed, int glyph,
                     Bitmap icon, String text, boolean badge) {
        float cw = rr - l, ch = b - t;
        float k = scale(cw, ch);
        float f = Ui.px(78) * k;
        float content = Ui.px(118) * k;
        float top = t + Math.max(Ui.px(6), (ch - content) * 0.16f);
        float cx = l + cw / 2f;
        r.set(cx - f / 2f, top, cx + f / 2f, top + f);
        float rad = Ui.px(17) * k;
        frame.setStrokeWidth(Math.max(1.5f, Ui.px(pressed ? 4.2f : 2.1f)));
        c.drawRoundRect(r, rad, rad, frame);

        if (glyph >= 0) {
            Icons.draw(c, glyph, cx, r.centerY(), Ui.px(50) * k, Ui.INK, 1.45f);
        } else if (icon != null) {
            int s = icon.getWidth();
            int x = Math.round(cx - s / 2f), y = Math.round(r.centerY() - s / 2f);
            dst.set(x, y, x + s, y + s);
            c.drawBitmap(icon, null, dst, bmp);
        }

        if (badge) {
            float bx = r.right - Ui.px(1) * k, by = r.bottom - Ui.px(13) * k, br = Ui.px(12.5f) * k;
            c.drawCircle(bx, by, br, white);
            frame.setStrokeWidth(Math.max(1f, Ui.px(1.6f)));
            c.drawCircle(bx, by, br, frame);
            Icons.draw(c, Icons.BADGE, bx, by, br * 1.7f, Ui.INK, 1.9f);
        }

        TextPaint tp = labelPaint(k);
        String s = Ui.ellipsize(text, tp, cw - Ui.px(8));
        c.drawText(s, cx - tp.measureText(s) / 2f, Ui.baseline(tp, r.bottom + Ui.px(30) * k), tp);
    }
}
