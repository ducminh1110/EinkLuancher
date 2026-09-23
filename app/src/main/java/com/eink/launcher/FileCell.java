package com.eink.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;

import java.text.DateFormat;
import java.util.Date;

/**
 * Draws files the way the BOOX library does (reference screenshot #2):
 * cover with a small format badge ("EPUB") and the file name on two lines,
 * or a compact list row.
 */
final class FileCell {
    /** Cover aspect (h / w) and the row extra (text) in design units. */
    static final float COVER_RATIO = 1.34f, COVER_WIDTH = 0.74f, ROW_EXTRA = 64f;
    static final float LIST_ROW = 66f;

    private static final Paint border = Ui.line(1.2f);
    private static final Paint thick = Ui.line(3.2f);
    private static final Paint white = Ui.fill(Ui.WHITE);
    private static final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static final Paint sep = Ui.line(1f);
    private static final TextPaint title = Ui.text(13.5f, false);
    private static final TextPaint coverTitle = Ui.text(14f, true);
    private static final TextPaint badge = Ui.text(10.5f, true);
    private static final TextPaint rowTitle = Ui.text(15.5f, true);
    private static final TextPaint rowInfo = Ui.text(12.5f, false);
    private static final RectF r = new RectF();
    private static final Rect dst = new Rect();
    private static final Date date = new Date();
    private static DateFormat dateFormat;

    static {
        rowInfo.setColor(Ui.SOFT);
    }

    private FileCell() {}

    /** Cover size (px) for a grid cell. */
    static int coverW(float cellW, float cellH) {
        float w = Math.min(cellW * COVER_WIDTH, (cellH - Ui.px(ROW_EXTRA)) / COVER_RATIO);
        return Math.max(8, Math.round(w));
    }

    static int coverH(float cellW, float cellH) {
        return Math.round(coverW(cellW, cellH) * COVER_RATIO);
    }

    static void drawGrid(Canvas c, FileItem f, Bitmap cover, float l, float t, float rr, float b, boolean pressed) {
        float cw = rr - l, ch = b - t;
        int w = coverW(cw, ch), h = coverH(cw, ch);
        float x = l + (cw - w) / 2f;
        float y = t + Math.max(Ui.px(10), (ch - h - Ui.px(ROW_EXTRA)) * 0.4f + Ui.px(10));
        r.set(x, y, x + w, y + h);

        if (f.dir) {
            Icons.draw(c, Icons.FOLDER, r.centerX(), r.centerY(), w * 0.9f, Ui.INK, 1.1f);
        } else if (cover != null) {
            dst.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
            c.drawBitmap(cover, null, dst, bmp);
            c.drawRect(r, border);
        } else if (Library.isBook(f.name, f.ext)) {
            drawGeneratedCover(c, f, r);
        } else {
            c.drawRect(r, border);
            Icons.draw(c, glyphFor(f), r.centerX(), r.centerY() - h * 0.06f, w * 0.5f, Ui.INK, 1.5f);
        }
        if (pressed) {
            r.inset(-Ui.px(4), -Ui.px(4));
            c.drawRect(r, thick);
            r.inset(Ui.px(4), Ui.px(4));
        }
        if (!f.dir && f.ext.length() > 0) drawBadge(c, f.badge(), r.left + Ui.px(4), r.bottom - Ui.px(4));
        if (f.progress > 0) {
            String pct = Math.max(1, Math.round(f.progress * 100)) + "%";
            drawBadge(c, pct, r.right - Ui.px(4) - badge.measureText(pct) - Ui.px(8), r.bottom - Ui.px(4));
        }

        // File name, two lines, centered.
        float tw = cw - Ui.px(14);
        if (f.lines == null || f.linesWidth != tw) {
            f.lines = Ui.wrap2(f.title(), title, tw);
            f.linesWidth = tw;
        }
        float ty = r.bottom + Ui.px(18);
        c.drawText(f.lines[0], l + (cw - title.measureText(f.lines[0])) / 2f, Ui.baseline(title, ty), title);
        if (f.lines[1] != null) {
            c.drawText(f.lines[1], l + (cw - title.measureText(f.lines[1])) / 2f,
                    Ui.baseline(title, ty + Ui.px(18)), title);
        }
    }

    private static void drawBadge(Canvas c, String text, float left, float bottom) {
        if (text.length() > 5) text = text.substring(0, 5);
        float pad = Ui.px(4);
        float tw = badge.measureText(text);
        float h = Ui.px(16);
        c.drawRect(left, bottom - h, left + tw + 2 * pad, bottom, white);
        c.drawRect(left, bottom - h, left + tw + 2 * pad, bottom, border);
        c.drawText(text, left + pad, Ui.baseline(badge, bottom - h / 2f), badge);
    }

    /** Cover for books without an embedded image (PDF, TXT, DjVu on 4.4). */
    private static void drawGeneratedCover(Canvas c, FileItem f, RectF r) {
        c.drawRect(r, white);
        c.drawRect(r, border);
        float in = Ui.px(6);
        c.drawRect(r.left + in, r.top + in, r.right - in, r.bottom - in, border);
        float maxW = r.width() - in * 4;
        String name = Text.stripExt(f.title());
        // Up to 4 greedy lines, centered in the upper part.
        float y = r.top + r.height() * 0.28f;
        String rest = name;
        for (int line = 0; line < 4 && rest.length() > 0; line++) {
            int n = coverTitle.breakText(rest, true, maxW, null);
            if (n <= 0) break;
            String s;
            if (n < rest.length()) {
                int sp = rest.lastIndexOf(' ', n);
                if (sp > n / 2) n = sp;
                s = rest.substring(0, n).trim();
                if (line == 3) s = Ui.ellipsize(rest.trim(), coverTitle, maxW);
            } else {
                s = rest;
            }
            c.drawText(s, r.centerX() - coverTitle.measureText(s) / 2f, Ui.baseline(coverTitle, y), coverTitle);
            y += Ui.px(19);
            rest = rest.substring(Math.min(rest.length(), n)).trim();
        }
        float ly = r.bottom - r.height() * 0.28f;
        c.drawLine(r.centerX() - r.width() * 0.2f, ly, r.centerX() + r.width() * 0.2f, ly, border);
        Icons.draw(c, Icons.BOOK, r.centerX(), ly + Ui.px(16), Ui.px(18), Ui.INK, 1.6f);
    }

    static int glyphFor(FileItem f) {
        if (f.dir) return Icons.FOLDER;
        switch (f.ext) {
            case "apk": return Icons.APK;
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp": return Icons.IMAGE;
            case "mp3": case "wav": case "ogg": case "m4a": case "flac": case "aac": case "amr": return Icons.MUSIC;
            case "mp4": case "mkv": case "avi": case "3gp": case "webm": case "mov": return Icons.VIDEO;
            default:
                return Library.isBook(f.name, f.ext) ? Icons.BOOK : Icons.FILE;
        }
    }

    static void drawRow(Canvas c, FileItem f, Bitmap thumb, float l, float t, float rr, float b, boolean pressed,
                        String info) {
        float h = b - t;
        float th = h - Ui.px(16), tw = th / COVER_RATIO;
        float x = l + Ui.px(18);
        r.set(x, t + (h - th) / 2f, x + tw, t + (h + th) / 2f);
        if (thumb != null) {
            dst.set(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
            c.drawBitmap(thumb, null, dst, bmp);
            c.drawRect(r, border);
        } else {
            Icons.draw(c, glyphFor(f), r.centerX(), r.centerY(), tw * 1.25f, Ui.INK, 1.6f);
        }
        float tx = r.right + Ui.px(16);
        float avail = rr - tx - Ui.px(18);
        String name = Ui.ellipsize(f.title(), rowTitle, avail);
        c.drawText(name, tx, Ui.baseline(rowTitle, t + h * 0.36f), rowTitle);
        if (info != null) {
            c.drawText(Ui.ellipsize(info, rowInfo, avail), tx, Ui.baseline(rowInfo, t + h * 0.7f), rowInfo);
        }
        if (pressed) {
            c.drawRect(l + Ui.px(4), t + Ui.px(2), rr - Ui.px(4), b - Ui.px(2), thick);
        }
        c.drawLine(l + Ui.px(18), b - 0.5f, rr - Ui.px(18), b - 0.5f, sep);
    }

    static String info(android.content.Context ctx, FileItem f) {
        if (dateFormat == null) dateFormat = android.text.format.DateFormat.getMediumDateFormat(ctx);
        date.setTime(f.modified);
        if (f.dir) return ctx.getString(R.string.folder) + "  ·  " + dateFormat.format(date);
        return f.badge() + "  ·  " + Text.size(f.size) + "  ·  " + dateFormat.format(date);
    }
}
