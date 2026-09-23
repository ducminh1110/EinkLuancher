package com.eink.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/** Page header: optional back arrow + title on the left, action icons on the right. */
final class ToolbarView extends View {
    static final float HEIGHT = 66f;
    private static final float ICON = 27f, PITCH = 49f, RIGHT = 47f, CY = 40f;

    interface Listener {
        void onToolbarAction(int iconId);

        void onToolbarBack();
    }

    private final TextPaint titlePaint = Ui.text(16.5f, true);
    private String title = "";
    private boolean back;
    private int[] actions = new int[0];
    private int pressed = -2; // -1 = back/title, >=0 action index
    private Listener listener;

    ToolbarView(Context c) {
        super(c);
        setBackgroundColor(Ui.WHITE);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void set(String title, boolean back, int... actions) {
        this.title = title == null ? "" : title;
        this.back = back;
        this.actions = actions;
        invalidate();
    }

    void setTitle(String title, boolean back) {
        this.title = title == null ? "" : title;
        this.back = back;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(HEIGHT));
    }

    private float actionX(int i) {
        int fromRight = actions.length - 1 - i;
        return getWidth() - Ui.px(RIGHT) - fromRight * Ui.px(PITCH);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cy = Ui.px(CY);
        float x = Ui.px(18);
        if (back) {
            Icons.draw(c, Icons.BACK, x + Ui.px(11), cy, Ui.px(pressed == -1 ? 26 : 24), Ui.INK, 1.9f);
            x += Ui.px(34);
        }
        float limit = actions.length > 0 ? actionX(0) - Ui.px(PITCH / 2f + 6) : getWidth() - Ui.px(16);
        if (title.length() > 0) {
            String t = Ui.ellipsize(title, titlePaint, limit - x);
            c.drawText(t, x, Ui.baseline(titlePaint, cy), titlePaint);
        }
        for (int i = 0; i < actions.length; i++) {
            float size = Ui.px(pressed == i ? ICON + 3 : ICON);
            Icons.draw(c, actions[i], actionX(i), cy, size, Ui.INK, 1.8f);
        }
    }

    private int hit(float x) {
        for (int i = 0; i < actions.length; i++) {
            if (Math.abs(x - actionX(i)) < Ui.px(PITCH / 2f)) return i;
        }
        if (back && x < getWidth() / 2f) return -1;
        return -2;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit(e.getX());
                if (pressed != -2) invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int h = hit(e.getX());
                int p = pressed;
                pressed = -2;
                invalidate();
                if (h == p && listener != null) {
                    if (h == -1) listener.onToolbarBack();
                    else if (h >= 0) listener.onToolbarAction(actions[h]);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -2;
                invalidate();
                return true;
        }
        return true;
    }

    /** Screen X of an action icon, used to anchor popup menus under it. */
    int anchorX(int iconId) {
        for (int i = 0; i < actions.length; i++) if (actions[i] == iconId) return (int) actionX(i);
        return getWidth();
    }
}
