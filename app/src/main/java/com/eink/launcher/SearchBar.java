package com.eink.launcher;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Search field that replaces a page's toolbar while searching. */
final class SearchBar extends LinearLayout {
    interface Listener {
        /** Folded (lower case, no diacritics) query. */
        void onQuery(String folded);
    }

    private final EditText field;
    private final View toolbar;
    private Listener listener;

    SearchBar(Context c, View toolbar, int hint) {
        super(c);
        this.toolbar = toolbar;
        setOrientation(HORIZONTAL);
        setBackgroundColor(Ui.WHITE);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.ipx(14), Ui.ipx(10), Ui.ipx(6), 0);
        setVisibility(GONE);
        field = new EditText(c);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setHint(hint);
        field.setTextColor(Ui.BLACK);
        field.setHintTextColor(Ui.SOFT);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(16));
        field.setBackgroundDrawable(EinkMenu.frame());
        field.setPadding(Ui.ipx(12), Ui.ipx(6), Ui.ipx(12), Ui.ipx(6));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (listener != null) listener.onQuery(Text.fold(s.toString().trim()));
            }
        });
        field.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int id, android.view.KeyEvent e) {
                EinkMenu.hideKeyboard(field);
                return true;
            }
        });
        addView(field, new LayoutParams(0, Ui.ipx(44), 1f));
        IconButton close = new IconButton(c, Icons.CLOSE, 24);
        close.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
            }
        });
        addView(close, new LayoutParams(Ui.ipx(52), Ui.ipx(52)));
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    void open() {
        setVisibility(VISIBLE);
        toolbar.setVisibility(INVISIBLE);
        field.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    /** @return true if the bar was open. */
    boolean close() {
        if (!isOpen()) return false;
        EinkMenu.hideKeyboard(field);
        field.setText("");
        setVisibility(GONE);
        toolbar.setVisibility(VISIBLE);
        return true;
    }
}
