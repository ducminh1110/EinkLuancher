package com.eink.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/** Bottom navigation: Library / Store / Storage / Apps / Settings. */
final class TabBarView extends View {
    static final float HEIGHT = 87f;

    interface Listener {
        void onTabSelected(int index, boolean reselected);
    }

    private final int[] icons;
    private final String[] labels;
    private final TextPaint label = Ui.text(14.5f, false);
    private final Paint rule = Ui.line(1.8f);
    private final Paint bar = Ui.fill(Ui.BLACK);
    private int selected;
    private int pressed = -1;
    private Listener listener;

    TabBarView(Context c, int[] icons, String[] labels) {
        super(c);
        this.icons = icons;
        this.labels = labels;
        setBackgroundColor(Ui.WHITE);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setSelected(int i) {
        if (selected != i) {
            selected = i;
            invalidate();
        }
    }

    int getSelected() {
        return selected;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(HEIGHT));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float tw = w / icons.length;
        float half = rule.getStrokeWidth() / 2f;
        c.drawLine(0, half, w, half, rule);
        for (int i = 0; i < icons.length; i++) {
            float cx = tw * i + tw / 2f;
            boolean sel = i == selected;
            if (sel) {
                c.drawRect(cx - Ui.px(22), 0, cx + Ui.px(22), Ui.px(4.5f), bar);
            }
            Icons.drawTab(c, icons[i], cx, Ui.px(34), Ui.px(pressed == i ? 29 : 27), sel);
            String t = Ui.ellipsize(labels[i], label, tw - Ui.px(6));
            c.drawText(t, cx - label.measureText(t) / 2f, Ui.baseline(label, Ui.px(67)), label);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int idx = (int) (e.getX() / (getWidth() / (float) icons.length));
        if (idx < 0) idx = 0;
        if (idx >= icons.length) idx = icons.length - 1;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = idx;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                boolean hit = pressed == idx;
                pressed = -1;
                if (hit && listener != null) {
                    boolean re = idx == selected;
                    setSelected(idx);
                    listener.onTabSelected(idx, re);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
        }
        return true;
    }
}
