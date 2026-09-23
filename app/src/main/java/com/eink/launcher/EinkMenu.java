package com.eink.launcher;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * E-ink friendly popup menu and dialogs: pure white, black 2px border, no
 * dimming, no shadows, no animations (all of which ghost badly on e-paper).
 */
final class EinkMenu {
    interface Action {
        void run();
    }

    private final Context ctx;
    private final List<String> labels = new ArrayList<>();
    private final List<Boolean> checks = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private PopupWindow popup;

    EinkMenu(Context c) {
        ctx = c;
    }

    EinkMenu add(String label, Action a) {
        return add(label, false, a);
    }

    EinkMenu add(String label, boolean checked, Action a) {
        labels.add(label);
        checks.add(checked);
        actions.add(a);
        return this;
    }

    static GradientDrawable frame() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.WHITE);
        g.setStroke(Math.max(2, Ui.ipx(2)), Ui.BLACK);
        g.setCornerRadius(Ui.px(6));
        return g;
    }

    /** Shows the menu under the toolbar, right edge aligned to {@code anchorX}. */
    void show(View anchor, int anchorX) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(frame());
        int pad = Ui.ipx(4);
        box.setPadding(pad, pad, pad, pad);
        TextPaint tp = Ui.text(16f, false);
        float maxW = Ui.px(190);
        for (String l : labels) maxW = Math.max(maxW, tp.measureText(l) + Ui.px(80));
        int width = (int) Math.min(maxW, anchor.getRootView().getWidth() - Ui.px(24));
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            Row r = new Row(ctx, labels.get(i), checks.get(i), i < labels.size() - 1);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    Action a = actions.get(idx);
                    if (a != null) a.run();
                }
            });
            box.addView(r, new LinearLayout.LayoutParams(width - 2 * pad, Ui.ipx(54)));
        }
        popup = new PopupWindow(box, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int x = Math.max(Ui.ipx(8), loc[0] + anchorX + Ui.ipx(22) - width);
        int y = loc[1] + anchor.getHeight() - Ui.ipx(6);
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, y);
    }

    /** Shows the menu as a centered list with a title. */
    void showCentered(View parent, String title) {
        final Dialog d = dialog(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(frame());
        int pad = Ui.ipx(6);
        box.setPadding(pad, pad, pad, pad);
        if (title != null) box.addView(title(ctx, title));
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            Row r = new Row(ctx, labels.get(i), checks.get(i), i < labels.size() - 1);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    Action a = actions.get(idx);
                    if (a != null) a.run();
                }
            });
            box.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.ipx(54)));
        }
        showDialog(d, box);
    }

    void dismiss() {
        if (popup != null) popup.dismiss();
        popup = null;
    }

    /** One menu row: text + optional check mark, hairline separator. */
    private static final class Row extends View {
        private final String text;
        private final boolean checked, separator;
        private final TextPaint tp = Ui.text(16f, false);
        private final Paint sep = Ui.line(1f);
        private boolean pressed;

        Row(Context c, String text, boolean checked, boolean separator) {
            super(c);
            this.text = text;
            this.checked = checked;
            this.separator = separator;
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth();
            if (pressed) c.drawColor(Ui.BLACK);
            tp.setColor(pressed ? Ui.WHITE : Ui.INK);
            float right = checked ? w - Ui.px(44) : w - Ui.px(14);
            c.drawText(Ui.ellipsize(text, tp, right - Ui.px(16)), Ui.px(16), Ui.baseline(tp, h / 2f), tp);
            if (checked) Icons.draw(c, Icons.CHECK, w - Ui.px(26), h / 2f, Ui.px(22), pressed ? Ui.WHITE : Ui.INK, 2.2f);
            if (separator && !pressed) c.drawLine(Ui.px(12), h - 0.5f, w - Ui.px(12), h - 0.5f, sep);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
            } else if (a == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                invalidate();
            } else if (a == MotionEvent.ACTION_UP) {
                pressed = false;
                invalidate();
            }
            return super.onTouchEvent(e);
        }
    }

    // ---------------------------------------------------------------- dialogs

    interface TextCallback {
        void onText(String text);
    }

    static Dialog dialog(Context c) {
        Dialog d = new Dialog(c, R.style.Dialog_Eink);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    static void showDialog(Dialog d, View content) {
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setWindowAnimations(0);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) Math.min(Ui.px(520), content.getContext().getResources().getDisplayMetrics().widthPixels - Ui.px(40));
            w.setAttributes(lp);
        }
        d.show();
    }

    static TextView title(Context c, String t) {
        TextView tv = new TextView(c);
        tv.setText(t);
        tv.setTextColor(Ui.BLACK);
        tv.getPaint().setFakeBoldText(true);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(17));
        tv.setPadding(Ui.ipx(16), Ui.ipx(14), Ui.ipx(16), Ui.ipx(10));
        return tv;
    }

    private static TextView button(Context c, String t, View.OnClickListener l) {
        TextView b = new TextView(c);
        b.setText(t);
        b.setTextColor(Ui.BLACK);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(16));
        b.setBackgroundDrawable(frame());
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout buttons(Context c, String cancel, String ok, View.OnClickListener onCancel,
                                        View.OnClickListener onOk) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.ipx(12), Ui.ipx(14), Ui.ipx(12), Ui.ipx(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.ipx(50), 1f);
        lp.leftMargin = Ui.ipx(6);
        lp.rightMargin = Ui.ipx(6);
        row.addView(button(c, cancel, onCancel), lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, Ui.ipx(50), 1f);
        lp2.leftMargin = Ui.ipx(6);
        lp2.rightMargin = Ui.ipx(6);
        row.addView(button(c, ok, onOk), lp2);
        return row;
    }

    static void confirm(Context c, String title, String message, final Action ok) {
        final Dialog d = dialog(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(frame());
        box.addView(title(c, title));
        if (message != null) {
            TextView m = new TextView(c);
            m.setText(message);
            m.setTextColor(Ui.INK);
            m.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(15));
            m.setPadding(Ui.ipx(16), 0, Ui.ipx(16), Ui.ipx(4));
            box.addView(m);
        }
        box.addView(buttons(c, c.getString(R.string.cancel), c.getString(R.string.ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        d.dismiss();
                    }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        d.dismiss();
                        ok.run();
                    }
                }));
        showDialog(d, box);
    }

    static void input(final Context c, String title, String initial, final TextCallback cb) {
        final Dialog d = dialog(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundDrawable(frame());
        box.addView(title(c, title));
        final EditText et = new EditText(c);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setTextColor(Ui.BLACK);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(16));
        et.setBackgroundDrawable(frame());
        et.setPadding(Ui.ipx(12), Ui.ipx(10), Ui.ipx(12), Ui.ipx(10));
        if (initial != null) {
            et.setText(initial);
            int dot = initial.lastIndexOf('.');
            et.setSelection(0, dot > 0 ? dot : initial.length());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.ipx(16);
        lp.rightMargin = Ui.ipx(16);
        box.addView(et, lp);
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                String t = et.getText().toString().trim();
                hideKeyboard(et);
                d.dismiss();
                if (t.length() > 0) cb.onText(t);
            }
        };
        box.addView(buttons(c, c.getString(R.string.cancel), c.getString(R.string.ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideKeyboard(et);
                        d.dismiss();
                    }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        done.run();
                    }
                }));
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                done.run();
                return true;
            }
        });
        showDialog(d, box);
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        et.requestFocus();
    }

    static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
