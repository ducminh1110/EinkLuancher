package com.eink.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** A single clickable line icon. */
final class IconButton extends View {
    private final int icon;
    private final float size;
    private boolean pressed;

    IconButton(Context c, int icon, float sizeDesign) {
        super(c);
        this.icon = icon;
        this.size = sizeDesign;
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas c) {
        Icons.draw(c, icon, getWidth() / 2f, getHeight() / 2f, Ui.px(pressed ? size + 3 : size), Ui.INK, 1.8f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        boolean p = a == MotionEvent.ACTION_DOWN || (pressed && a == MotionEvent.ACTION_MOVE);
        if (p != pressed) {
            pressed = p;
            invalidate();
        }
        return super.onTouchEvent(e);
    }
}
