package com.eink.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Page footer. Two styles:
 * <ul>
 * <li>DOTS: the centered "■ □ □" indicator of the Apps screen;</li>
 * <li>INFO: "Total: Folders 0 / Files 42" on the left, view toggle + "2/7" on the right
 * (Library / Storage screens).</li>
 * </ul>
 * Tapping the left/right half flips pages, handy on devices without page keys.
 */
final class FooterView extends View {
    static final int DOTS = 0, INFO = 1;
    static final float HEIGHT = 56f;

    interface Listener {
        void onFooterPrev();

        void onFooterNext();

        void onFooterToggleView();
    }

    private final int style;
    private final TextPaint text = Ui.text(13.5f, false);
    private final Paint dotStroke = Ui.line(1.3f);
    private final Paint dotFill = Ui.fill(Ui.BLACK);
    private int page, count = 1;
    private String info = "";
    private int viewIcon = -1;
    private Listener listener;

    FooterView(Context c, int style) {
        super(c);
        this.style = style;
        setBackgroundColor(Ui.WHITE);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setPage(int page, int count) {
        if (this.page != page || this.count != count) {
            this.page = page;
            this.count = count;
            invalidate();
        }
    }

    void setInfo(String info) {
        if (info == null) info = "";
        if (!info.equals(this.info)) {
            this.info = info;
            invalidate();
        }
    }

    /** Icon shown before the page counter (GRID / LIST), or -1 for none. */
    void setViewIcon(int icon) {
        viewIcon = icon;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(style == DOTS ? 52 : HEIGHT));
    }

    private float viewIconX() {
        String p = (page + 1) + "/" + count;
        return getWidth() - Ui.px(16) - text.measureText(p) - Ui.px(26);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cy = style == DOTS ? Ui.px(24) : getHeight() / 2f;
        if (style == DOTS) {
            if (count <= 1) return;
            if (count > 12) {
                String t = (page + 1) + " / " + count;
                c.drawText(t, (w - text.measureText(t)) / 2f, Ui.baseline(text, cy), text);
                return;
            }
            float sq = Ui.px(9), pitch = Ui.px(20);
            float x = w / 2f - (count - 1) * pitch / 2f;
            for (int i = 0; i < count; i++) {
                float l = x + i * pitch - sq / 2f;
                if (i == page) c.drawRect(l, cy - sq / 2f, l + sq, cy + sq / 2f, dotFill);
                else {
                    float in = dotStroke.getStrokeWidth() / 2f;
                    c.drawRect(l + in, cy - sq / 2f + in, l + sq - in, cy + sq / 2f - in, dotStroke);
                }
            }
            return;
        }
        String p = (page + 1) + "/" + count;
        float pw = text.measureText(p);
        c.drawText(p, w - Ui.px(16) - pw, Ui.baseline(text, cy), text);
        float right = w - Ui.px(16) - pw - Ui.px(12);
        if (viewIcon >= 0) {
            Icons.draw(c, viewIcon, viewIconX(), cy, Ui.px(22), Ui.INK, 1.8f);
            right = viewIconX() - Ui.px(20);
        }
        String t = Ui.ellipsize(info, text, right - Ui.px(18));
        c.drawText(t, Ui.px(18), Ui.baseline(text, cy), text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP || listener == null) return true;
        float x = e.getX();
        if (style == INFO && viewIcon >= 0 && Math.abs(x - viewIconX()) < Ui.px(24)) {
            listener.onFooterToggleView();
        } else if (x < getWidth() / 2f) {
            listener.onFooterPrev();
        } else {
            listener.onFooterNext();
        }
        return true;
    }
}
