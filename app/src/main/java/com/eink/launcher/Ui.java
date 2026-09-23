package com.eink.launcher;

import android.content.Context;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;

/**
 * Shared metrics and paints.
 *
 * All sizes are expressed in "design units" measured on the reference BOOX
 * screenshot (650 px wide) and scaled by {@link #s} so the launcher keeps the
 * exact same proportions on 600x800, 758x1024, 1072x1448 ... e-ink panels.
 */
final class Ui {
    static final int BLACK = 0xFF000000;
    static final int INK = 0xFF111111;
    static final int WHITE = 0xFFFFFFFF;
    static final int SOFT = 0xFF555555;

    /** Scale factor: design unit -> pixels. */
    static float s = 1f;

    private Ui() {}

    static void init(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        s = Math.min(dm.widthPixels, dm.heightPixels) / 650f;
        if (s < 0.5f) s = 0.5f;
    }

    static float px(float design) {
        return design * s;
    }

    static int ipx(float design) {
        return Math.round(design * s);
    }

    /** Text paint; "medium" fakes Roboto Medium (not shipped on 4.4) with a hairline stroke. */
    static TextPaint text(float sizeDesign, boolean medium) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(INK);
        p.setTextSize(px(sizeDesign));
        if (medium) {
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeWidth(px(sizeDesign) * 0.04f);
        }
        return p;
    }

    static Paint line(float widthDesign) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(INK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, px(widthDesign)));
        return p;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    /** Baseline that vertically centers a single text line on cy. */
    static float baseline(Paint p, float cy) {
        return cy - (p.descent() + p.ascent()) / 2f;
    }

    static String ellipsize(String text, TextPaint p, float width) {
        if (text == null) return "";
        if (p.measureText(text) <= width) return text;
        return TextUtils.ellipsize(text, p, width, TextUtils.TruncateAt.END).toString();
    }

    /**
     * Wraps a text on at most two lines; the second line is ellipsized in the
     * middle like the BOOX library ("Christ...uocchio.epub").
     */
    static String[] wrap2(String text, TextPaint p, float width) {
        if (text == null) text = "";
        if (p.measureText(text) <= width) return new String[]{text, null};
        int n = p.breakText(text, true, width, null);
        if (n <= 0) n = 1;
        // Prefer breaking at a space/separator if one is reasonably close.
        int brk = n;
        for (int i = n; i > n / 2 && i > 0; i--) {
            char ch = text.charAt(i - 1);
            if (ch == ' ' || ch == '-' || ch == '_' || ch == '.') {
                brk = i;
                break;
            }
        }
        String first = text.substring(0, brk).trim();
        String rest = text.substring(brk).trim();
        if (p.measureText(rest) > width) {
            rest = TextUtils.ellipsize(rest, p, width, TextUtils.TruncateAt.MIDDLE).toString();
        }
        return new String[]{first, rest};
    }
}
