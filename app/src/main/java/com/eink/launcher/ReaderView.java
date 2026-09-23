package com.eink.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * The reading surface. A chapter is laid out once with a StaticLayout and
 * cut into pages of whole lines; turning a page only changes which lines
 * are drawn, so it is a single, instant e-ink update (no animation).
 *
 * Tap left third = previous page, right third = next page, center = menu.
 */
final class ReaderView extends View {
    interface Host {
        void onMenuAction(int action);

        /** Called after every page change (user turn or chapter load). */
        void onPositionChanged(boolean userTurn);

        void onEndOfBook();
    }

    static final int ACT_BACK = 1, ACT_TOC = 2, ACT_GOTO = 3, ACT_OPEN_WITH = 4, ACT_REFRESH = 5;
    private static final int ACT_PREV_CH = 10, ACT_NEXT_CH = 11, ACT_SMALLER = 12, ACT_LARGER = 13,
            ACT_FONT = 14, ACT_SPACING = 15, ACT_MARGIN = 16;

    static final float[] SPACINGS = {1.0f, 1.15f, 1.3f, 1.5f, 1.8f};
    static final float[] MARGINS = {12f, 26f, 44f};

    private final Host host;
    private Book book;
    private int chapter;
    private CharSequence text;
    private int textW, textH;
    private float textMult;
    private StaticLayout layout;
    private int[] pages = new int[0];
    private int page;
    private Bitmap picture;
    private boolean loading, flash;
    private String message;
    private int loadGen;
    private int pendingOffset;
    private boolean menu;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint footer = Ui.text(12.5f, false);
    private final TextPaint msgPaint = Ui.text(16f, false);
    private final TextPaint menuTitle = Ui.text(16.5f, true);
    private final TextPaint btnText = Ui.text(15f, false);
    private final Paint line = Ui.line(1.4f);
    private final Paint white = Ui.fill(Ui.WHITE);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF[] hitRects = new RectF[16];
    private final int[] hitActs = new int[16];
    private int hits;
    private final Rect dst = new Rect();
    private final RectF tmp = new RectF();
    private float downX, downY;

    ReaderView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Ui.WHITE);
        paint.setColor(Ui.BLACK);
        for (int i = 0; i < hitRects.length; i++) hitRects[i] = new RectF();
        applyPaint();
    }

    // ------------------------------------------------------------ settings

    static float fontSize() {
        return Prefs.f(Prefs.READER_SIZE, 21f);
    }

    static int face() {
        return Prefs.i(Prefs.READER_FACE, 1);
    }

    static int spacingIdx() {
        return Math.max(0, Math.min(SPACINGS.length - 1, Prefs.i(Prefs.READER_SPACING, 2)));
    }

    static int marginIdx() {
        return Math.max(0, Math.min(MARGINS.length - 1, Prefs.i(Prefs.READER_MARGIN, 1)));
    }

    private void applyPaint() {
        paint.setTextSize(Ui.px(fontSize()));
        int f = face();
        paint.setTypeface(f == 0 ? Typeface.SANS_SERIF : f == 2 ? Typeface.MONOSPACE : Typeface.SERIF);
    }

    private float ml() {
        return Ui.px(MARGINS[marginIdx()]);
    }

    private float mt() {
        return Ui.px(24);
    }

    private float footerH() {
        return Ui.px(40);
    }

    private int areaW() {
        return (int) (getWidth() - 2 * ml());
    }

    private int areaH() {
        return (int) (getHeight() - mt() - footerH());
    }

    // --------------------------------------------------------------- state

    Book book() {
        return book;
    }

    int chapter() {
        return chapter;
    }

    boolean isMenuShown() {
        return menu;
    }

    void showMenu(boolean show) {
        if (menu != show) {
            menu = show;
            invalidate();
        }
    }

    void setMessage(String m) {
        message = m;
        invalidate();
    }

    int pageCount() {
        return picture != null || book == null || book.isPictureBook() ? 1 : Math.max(1, pages.length);
    }

    int offset() {
        if (layout == null || pages.length == 0) return 0;
        return layout.getLineStart(pages[Math.min(page, pages.length - 1)]);
    }

    float progress() {
        if (book == null || book.count() == 0) return 0;
        float inChapter = (page + 1) / (float) pageCount();
        return (book.sizeBefore(chapter) + book.sizes.get(chapter) * inChapter) / (float) book.totalSize();
    }

    String chapterTitle(int i) {
        String t = book == null || i >= book.count() ? null : book.chapters.get(i);
        return t != null ? t : getContext().getString(R.string.chapter_n, i + 1);
    }

    void setBook(Book b, int ch, int offset) {
        book = b;
        message = null;
        chapter = Math.max(0, Math.min(ch, b.count() - 1));
        text = null;
        layout = null;
        picture = null;
        load(chapter, offset, false, -1);
    }

    /** Re-applies font settings and re-flows the current chapter at the same position. */
    void reflow() {
        applyPaint();
        if (book != null) load(chapter, offset(), false, -1);
        else invalidate();
    }

    void gotoChapter(int ch, float fraction) {
        if (book == null) return;
        load(Math.max(0, Math.min(ch, book.count() - 1)), 0, false, fraction);
    }

    void gotoProgress(float p) {
        if (book == null) return;
        long target = (long) (p * book.totalSize());
        long acc = 0;
        for (int i = 0; i < book.count(); i++) {
            long s = book.sizes.get(i);
            if (acc + s >= target || i == book.count() - 1) {
                gotoChapter(i, s > 0 ? Math.max(0, Math.min(1f, (target - acc) / (float) s)) : 0);
                return;
            }
            acc += s;
        }
    }

    private void load(final int ch, final int offset, final boolean last, final float fraction) {
        final int w = areaW(), h = areaH();
        if (w <= 0 || h <= 0 || book == null) {
            chapter = ch; // not laid out yet: onSizeChanged() loads it
            pendingOffset = offset;
            return;
        }
        loading = true;
        invalidate();
        final int gen = ++loadGen;
        final float mult = SPACINGS[spacingIdx()];
        final TextPaint tp = new TextPaint(paint);
        final boolean reuse = ch == chapter && text != null && w == textW && h == textH && mult == textMult;
        final CharSequence old = reuse ? text : null;
        final Book b = book;
        final int picW = getWidth(), picH = (int) (getHeight() - footerH());
        Worker.book(new Runnable() {
            @Override
            public void run() {
                CharSequence t = old;
                StaticLayout l = null;
                int[] pg = null;
                Bitmap pic = null;
                String err = null;
                try {
                    if (b.isPictureBook()) {
                        pic = b.picture(ch, picW, picH);
                    } else {
                        // Line spacing as a constant extra (not a multiplier) so that tall
                        // pictures do not get 30% blank space added to their line.
                        float add = (mult - 1f) * (tp.descent() - tp.ascent());
                        if (t == null) t = b.text(ch, getResources(), w, (int) (h - add) - 4);
                        l = new StaticLayout(t, tp, w, Layout.Alignment.ALIGN_NORMAL, 1f, add, false);
                        pg = paginate(l, h);
                    }
                } catch (OutOfMemoryError e) {
                    t = null;
                    l = null;
                    ImageCache.trim(true);
                    err = getContext().getString(R.string.reader_error);
                } catch (Throwable e) {
                    android.util.Log.w("EinkReader", "cannot load chapter " + ch, e);
                    err = getContext().getString(R.string.reader_error);
                }
                final CharSequence ft = t;
                final StaticLayout fl = l;
                final int[] fpg = pg;
                final Bitmap fpic = pic;
                final String ferr = err;
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen) return;
                        loading = false;
                        if (ferr != null) {
                            message = ferr;
                            invalidate();
                            return;
                        }
                        message = null;
                        chapter = ch;
                        text = ft;
                        textW = w;
                        textH = h;
                        textMult = mult;
                        layout = fl;
                        picture = fpic;
                        pages = fpg != null ? fpg : new int[]{0};
                        if (fraction >= 0) page = Math.round(fraction * (pages.length - 1));
                        else if (last) page = pages.length - 1;
                        else page = pageFor(offset);
                        page = Math.max(0, Math.min(page, pages.length - 1));
                        invalidate();
                        host.onPositionChanged(false);
                    }
                });
            }
        });
    }

    private static int[] paginate(StaticLayout l, int h) {
        int n = l.getLineCount();
        int[] out = new int[Math.max(1, n)];
        int count = 0, line = 0;
        while (line < n) {
            out[count++] = line;
            int top = l.getLineTop(line);
            int last = l.getLineForVertical(top + h);
            if (l.getLineBottom(last) > top + h) last--;
            if (last < line) last = line;
            line = last + 1;
        }
        if (count == 0) return new int[]{0};
        int[] res = new int[count];
        System.arraycopy(out, 0, res, 0, count);
        return res;
    }

    private int pageFor(int offset) {
        if (layout == null || offset <= 0) return 0;
        int ln = layout.getLineForOffset(Math.min(offset, layout.getText().length()));
        int p = 0;
        for (int i = 0; i < pages.length; i++) if (pages[i] <= ln) p = i;
        return p;
    }

    // -------------------------------------------------------------- paging

    boolean next() {
        if (book == null || loading) return false;
        if (page < pageCount() - 1) {
            page++;
            invalidate();
            host.onPositionChanged(true);
            return true;
        }
        if (chapter < book.count() - 1) {
            load(chapter + 1, 0, false, -1);
            host.onPositionChanged(true);
            return true;
        }
        host.onEndOfBook();
        return false;
    }

    boolean prev() {
        if (book == null || loading) return false;
        if (page > 0) {
            page--;
            invalidate();
            host.onPositionChanged(true);
            return true;
        }
        if (chapter > 0) {
            load(chapter - 1, 0, true, -1);
            host.onPositionChanged(true);
            return true;
        }
        return false;
    }

    void flash() {
        flash = true;
        invalidate();
        postDelayed(new Runnable() {
            @Override
            public void run() {
                flash = false;
                invalidate();
            }
        }, 220);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (book != null) load(chapter, layout != null ? offset() : pendingOffset, false, -1);
    }

    // ------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        if (flash) {
            c.drawColor(Ui.BLACK);
            return;
        }
        float w = getWidth(), h = getHeight();
        if (picture != null) {
            float avail = h - footerH();
            float s = Math.min(w / picture.getWidth(), avail / picture.getHeight());
            int pw = Math.round(picture.getWidth() * s), ph = Math.round(picture.getHeight() * s);
            int x = Math.round((w - pw) / 2f), y = Math.round((avail - ph) / 2f);
            dst.set(x, y, x + pw, y + ph);
            c.drawBitmap(picture, null, dst, bmpPaint);
        } else if (layout != null && pages.length > 0) {
            int first = pages[page];
            int lastLine = (page + 1 < pages.length ? pages[page + 1] : layout.getLineCount()) - 1;
            int top = layout.getLineTop(first), bottom = layout.getLineBottom(Math.max(first, lastLine));
            int save = c.save();
            c.translate(ml(), mt() - top);
            c.clipRect(0, top, textW, bottom);
            layout.draw(c);
            c.restoreToCount(save);
        }
        if (message != null || (layout == null && picture == null)) {
            String m = message != null ? message : getContext().getString(R.string.reader_opening);
            float y = h / 2f;
            for (String part : m.split("\n")) {
                String t = Ui.ellipsize(part, msgPaint, w - Ui.px(40));
                c.drawText(t, (w - msgPaint.measureText(t)) / 2f, Ui.baseline(msgPaint, y), msgPaint);
                y += Ui.px(26);
            }
        }
        drawFooter(c, w, h);
        hits = 0;
        if (menu) drawMenu(c, w, h);
    }

    private void drawFooter(Canvas c, float w, float h) {
        if (book == null) return;
        float cy = h - footerH() / 2f;
        String right = loading ? "…" : (page + 1) + "/" + pageCount() + "  ·  "
                + String.format(Locale.US, "%d%%", Math.round(progress() * 100));
        float rw = footer.measureText(right);
        c.drawText(right, w - ml() - rw, Ui.baseline(footer, cy), footer);
        String left = Ui.ellipsize(book.count() > 1 ? chapterTitle(chapter) : book.title, footer,
                w - 2 * ml() - rw - Ui.px(20));
        c.drawText(left, ml(), Ui.baseline(footer, cy), footer);
    }

    private void addHit(float l, float t, float r, float b, int act) {
        if (hits >= hitRects.length) return;
        hitRects[hits].set(l, t, r, b);
        hitActs[hits++] = act;
    }

    private void button(Canvas c, float l, float t, float r, float b, String label, int act) {
        tmp.set(l, t, r, b);
        c.drawRoundRect(tmp, Ui.px(6), Ui.px(6), line);
        String s = Ui.ellipsize(label, btnText, r - l - Ui.px(8));
        c.drawText(s, (l + r - btnText.measureText(s)) / 2f, Ui.baseline(btnText, (t + b) / 2f), btnText);
        addHit(l, t, r, b, act);
    }

    private void drawMenu(Canvas c, float w, float h) {
        Context ctx = getContext();
        // Top bar
        float tb = Ui.px(66);
        c.drawRect(0, 0, w, tb, white);
        c.drawLine(0, tb, w, tb, line);
        Icons.draw(c, Icons.BACK, Ui.px(30), tb / 2f, Ui.px(24), Ui.INK, 1.9f);
        addHit(0, 0, Ui.px(64), tb, ACT_BACK);
        Icons.draw(c, Icons.LIST, w - Ui.px(40), tb / 2f, Ui.px(26), Ui.INK, 1.8f);
        addHit(w - Ui.px(80), 0, w, tb, ACT_TOC);
        String title = book != null ? book.title : "";
        c.drawText(Ui.ellipsize(title, menuTitle, w - Ui.px(150)), Ui.px(62), Ui.baseline(menuTitle, tb / 2f), menuTitle);

        // Bottom panel
        float top = h - Ui.px(220);
        c.drawRect(0, top, w, h, white);
        c.drawLine(0, top, w, top, line);
        float r1 = top + Ui.px(38);
        int save = c.save();
        c.scale(-1, 1, Ui.px(34), r1);
        Icons.draw(c, Icons.CHEVRON, Ui.px(34), r1, Ui.px(24), Ui.INK, 1.9f);
        c.restoreToCount(save);
        addHit(0, top, Ui.px(70), top + Ui.px(76), ACT_PREV_CH);
        Icons.draw(c, Icons.CHEVRON, w - Ui.px(34), r1, Ui.px(24), Ui.INK, 1.9f);
        addHit(w - Ui.px(70), top, w, top + Ui.px(76), ACT_NEXT_CH);
        if (book != null) {
            String info = ctx.getString(R.string.chapter_n, chapter + 1) + " / " + book.count() + "  ·  "
                    + (page + 1) + "/" + pageCount() + "  ·  " + Math.round(progress() * 100) + "%";
            String s = Ui.ellipsize(info, btnText, w - Ui.px(150));
            c.drawText(s, (w - btnText.measureText(s)) / 2f, Ui.baseline(btnText, r1), btnText);
        }

        float pad = Ui.px(16), gap = Ui.px(10);
        float bt = top + Ui.px(76), bb = bt + Ui.px(60);
        float bw = (w - 2 * pad - 4 * gap) / 5f;
        String[] faces = {ctx.getString(R.string.font_sans), ctx.getString(R.string.font_serif), ctx.getString(R.string.font_mono)};
        String[] margins = {"S", "M", "L"};
        String[] labels = {"A−", "A+", faces[Math.max(0, Math.min(2, face()))],
                "↕ " + SPACINGS[spacingIdx()], "↔ " + margins[marginIdx()]};
        int[] acts = {ACT_SMALLER, ACT_LARGER, ACT_FONT, ACT_SPACING, ACT_MARGIN};
        for (int i = 0; i < 5; i++) {
            float l = pad + i * (bw + gap);
            button(c, l, bt, l + bw, bb, labels[i], acts[i]);
        }
        float ct = bb + Ui.px(12), cb = ct + Ui.px(56);
        float cw = (w - 2 * pad - 2 * gap) / 3f;
        String[] l2 = {ctx.getString(R.string.goto_position), ctx.getString(R.string.open_with), ctx.getString(R.string.set_refresh)};
        int[] a2 = {ACT_GOTO, ACT_OPEN_WITH, ACT_REFRESH};
        for (int i = 0; i < 3; i++) {
            float l = pad + i * (cw + gap);
            button(c, l, ct, l + cw, cb, l2[i], a2[i]);
        }
    }

    private boolean inMenuPanels(float y) {
        return y < Ui.px(66) || y > getHeight() - Ui.px(220);
    }

    private void menuAction(int act) {
        switch (act) {
            case ACT_PREV_CH:
                if (book != null && chapter > 0) gotoChapter(chapter - 1, 0);
                return;
            case ACT_NEXT_CH:
                if (book != null && chapter < book.count() - 1) gotoChapter(chapter + 1, 0);
                return;
            case ACT_SMALLER:
                Prefs.put(Prefs.READER_SIZE, Math.max(12f, fontSize() - 1.5f));
                reflow();
                return;
            case ACT_LARGER:
                Prefs.put(Prefs.READER_SIZE, Math.min(48f, fontSize() + 1.5f));
                reflow();
                return;
            case ACT_FONT:
                Prefs.put(Prefs.READER_FACE, (face() + 1) % 3);
                reflow();
                return;
            case ACT_SPACING:
                Prefs.put(Prefs.READER_SPACING, (spacingIdx() + 1) % SPACINGS.length);
                reflow();
                return;
            case ACT_MARGIN:
                Prefs.put(Prefs.READER_MARGIN, (marginIdx() + 1) % MARGINS.length);
                reflow();
                return;
            default:
                host.onMenuAction(act);
        }
    }

    // --------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP: {
                float x = e.getX(), y = e.getY();
                float dx = x - downX, dy = y - downY;
                if (Math.abs(dx) > Ui.px(60) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0) next();
                    else prev();
                    return true;
                }
                if (Math.abs(dx) > Ui.px(20) || Math.abs(dy) > Ui.px(20)) return true;
                if (menu) {
                    for (int i = 0; i < hits; i++) {
                        if (hitRects[i].contains(x, y)) {
                            menuAction(hitActs[i]);
                            return true;
                        }
                    }
                    if (!inMenuPanels(y)) showMenu(false);
                    return true;
                }
                float w = getWidth();
                if (x < w * 0.3f) prev();
                else if (x > w * 0.7f) next();
                else showMenu(true);
                return true;
            }
        }
        return true;
    }
}
