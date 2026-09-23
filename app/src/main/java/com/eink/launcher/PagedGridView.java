package com.eink.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A grid that shows one page at a time and is flipped by swipes or keys
 * instead of scrolling. On e-ink this is essential: a page flip is a single
 * screen update, whereas scrolling produces dozens of ghosting partial updates.
 *
 * All cells are drawn by an {@link Adapter} directly on this single view, so
 * a whole page costs one view instead of dozens (fast layout, tiny memory).
 */
final class PagedGridView extends View {

    interface Adapter {
        int getCount();

        void drawCell(Canvas c, int position, float left, float top, float right, float bottom, boolean pressed);

        void onCellClick(int position);

        boolean onCellLongClick(int position);

        /** Called whenever a new range of cells becomes visible (lazy loading). */
        void onPageShown(int first, int last);
    }

    interface PageListener {
        void onPageChanged(PagedGridView v, int page, int pageCount, boolean byUser);
    }

    private Adapter adapter;
    private PageListener pageListener;
    private int fixedCols = 4;
    private float minRowHeight = 160f; // design units
    private float colWidthHint = 0f;   // design units; >0 => auto columns
    private int minCols = 1;
    private boolean fixedRowHeight;
    private float rowRatio;            // >0 => min row height = cellW * ratio + minRowHeight

    private int cols = 1, rows = 1;
    private float cellW, cellH;
    private int page;
    private String emptyText = "";
    private final TextPaint emptyPaint = Ui.text(15f, false);

    private int pressedPos = -1;
    private float downX, downY;
    private boolean moved, longFired;
    private final int touchSlop;
    private final int tapTimeout = ViewConfiguration.getTapTimeout();
    private final int longTimeout = ViewConfiguration.getLongPressTimeout();

    private final Runnable showPress = new Runnable() {
        @Override
        public void run() {
            if (pendingPos >= 0) {
                pressedPos = pendingPos;
                invalidateCell(pressedPos);
            }
        }
    };
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pendingPos >= 0 && adapter != null) {
                longFired = true;
                int p = pendingPos;
                clearPress();
                adapter.onCellLongClick(p);
            }
        }
    };
    private int pendingPos = -1;

    PagedGridView(Context c) {
        super(c);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        setBackgroundColor(Ui.WHITE);
    }

    /** Fixed number of columns, rows computed from the available height. */
    void setLayout(int cols, float minRowHeightDesign, boolean fixedRowHeight) {
        this.fixedCols = Math.max(1, cols);
        this.colWidthHint = 0;
        this.rowRatio = 0;
        this.minRowHeight = minRowHeightDesign;
        this.fixedRowHeight = fixedRowHeight;
        relayout();
    }

    /** Columns computed from the width (at least minCols). */
    void setAutoLayout(float colWidthDesign, int minCols, float minRowHeightDesign) {
        this.colWidthHint = colWidthDesign;
        this.rowRatio = 0;
        this.minCols = minCols;
        this.minRowHeight = minRowHeightDesign;
        this.fixedRowHeight = false;
        relayout();
    }

    /** Fixed columns; row height follows the cell width (book covers). */
    void setCoverLayout(int cols, float ratio, float extraDesign) {
        this.fixedCols = Math.max(1, cols);
        this.colWidthHint = 0;
        this.rowRatio = ratio;
        this.minRowHeight = extraDesign;
        this.fixedRowHeight = false;
        relayout();
    }

    void setAdapter(Adapter a) {
        adapter = a;
        page = 0;
        relayout();
    }

    void setPageListener(PageListener l) {
        pageListener = l;
    }

    void setEmptyText(String t) {
        emptyText = t == null ? "" : t;
    }

    int getCols() {
        return cols;
    }

    int getRows() {
        return rows;
    }

    float getCellWidth() {
        return cellW;
    }

    float getCellHeight() {
        return cellH;
    }

    int perPage() {
        return Math.max(1, cols * rows);
    }

    int getPage() {
        return page;
    }

    int getPageCount() {
        int n = adapter == null ? 0 : adapter.getCount();
        return Math.max(1, (n + perPage() - 1) / perPage());
    }

    int firstVisible() {
        return page * perPage();
    }

    int lastVisible() {
        int n = adapter == null ? 0 : adapter.getCount();
        return Math.min(n, firstVisible() + perPage()) - 1;
    }

    /** Call after the adapter's data changed. Keeps the current page if possible. */
    void notifyDataChanged() {
        int pc = getPageCount();
        if (page >= pc) page = pc - 1;
        if (page < 0) page = 0;
        invalidate();
        dispatchPage(false);
    }

    boolean setPage(int p, boolean byUser) {
        int pc = getPageCount();
        if (p < 0 || p >= pc || p == page) return false;
        page = p;
        clearPress();
        invalidate();
        dispatchPage(byUser);
        return true;
    }

    boolean nextPage() {
        return setPage(page + 1, true);
    }

    boolean prevPage() {
        return setPage(page - 1, true);
    }

    private void dispatchPage(boolean byUser) {
        if (pageListener != null) pageListener.onPageChanged(this, page, getPageCount(), byUser);
        if (adapter != null && getWidth() > 0) adapter.onPageShown(firstVisible(), lastVisible());
    }

    private void relayout() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (colWidthHint > 0) {
            cols = Math.max(minCols, (int) (w / Ui.px(colWidthHint)));
        } else {
            cols = fixedCols;
        }
        cellW = w / (float) cols;
        float minH = rowRatio > 0 ? cellW * rowRatio + Ui.px(minRowHeight) : Ui.px(minRowHeight);
        rows = Math.max(1, (int) (h / minH));
        cellH = fixedRowHeight ? minH : h / (float) rows;
        notifyDataChanged();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayout();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (adapter == null) return;
        int n = adapter.getCount();
        if (n == 0) {
            if (emptyText.length() > 0) {
                String[] lines = emptyText.split("\n");
                float y = getHeight() / 2f - (lines.length - 1) * Ui.px(12);
                for (String l : lines) {
                    String t = Ui.ellipsize(l, emptyPaint, getWidth() - Ui.px(32));
                    c.drawText(t, (getWidth() - emptyPaint.measureText(t)) / 2f, Ui.baseline(emptyPaint, y), emptyPaint);
                    y += Ui.px(24);
                }
            }
            return;
        }
        int first = firstVisible(), last = lastVisible();
        for (int i = first; i <= last; i++) {
            int k = i - first;
            float l = (k % cols) * cellW, t = (k / cols) * cellH;
            adapter.drawCell(c, i, l, t, l + cellW, t + cellH, i == pressedPos);
        }
    }

    void invalidateCell(int pos) {
        int first = firstVisible();
        if (pos < first || pos > lastVisible()) return;
        int k = pos - first;
        float l = (k % cols) * cellW, t = (k / cols) * cellH;
        invalidate((int) l - 2, (int) t - 2, (int) (l + cellW) + 2, (int) (t + cellH) + 2);
    }

    private int positionAt(float x, float y) {
        if (adapter == null) return -1;
        int c = (int) (x / cellW), r = (int) (y / cellH);
        if (c < 0 || c >= cols || r < 0 || r >= rows) return -1;
        int pos = firstVisible() + r * cols + c;
        return pos <= lastVisible() ? pos : -1;
    }

    private void clearPress() {
        removeCallbacks(showPress);
        removeCallbacks(longPress);
        int old = pressedPos;
        pressedPos = -1;
        pendingPos = -1;
        if (old >= 0) invalidateCell(old);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                moved = false;
                longFired = false;
                pendingPos = positionAt(downX, downY);
                if (pendingPos >= 0) {
                    postDelayed(showPress, tapTimeout);
                    postDelayed(longPress, longTimeout);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && (Math.abs(e.getX() - downX) > touchSlop || Math.abs(e.getY() - downY) > touchSlop)) {
                    moved = true;
                    clearPress();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (longFired) return true;
                if (moved) {
                    float min = Ui.px(40);
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > min) {
                        if (dx < 0) nextPage();
                        else prevPage();
                    } else if (Math.abs(dy) > min) {
                        if (dy < 0) nextPage();
                        else prevPage();
                    }
                    return true;
                }
                int p = pendingPos;
                clearPress();
                if (p >= 0 && p == positionAt(e.getX(), e.getY()) && adapter != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    adapter.onCellClick(p);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                clearPress();
                return true;
        }
        return true;
    }
}
