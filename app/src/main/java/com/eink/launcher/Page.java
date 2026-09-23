package com.eink.launcher;

import android.view.View;

/** One screen behind a bottom tab. Views are built lazily on first show. */
abstract class Page {
    final LauncherActivity act;
    private View root;

    Page(LauncherActivity act) {
        this.act = act;
    }

    abstract View create();

    final View view() {
        if (root == null) root = create();
        return root;
    }

    final boolean isCreated() {
        return root != null;
    }

    void onShow() {}

    void onHide() {}

    /** Launcher came back to the foreground while this page is visible. */
    void onResume() {}

    /** Back key; return true if consumed (e.g. leave a sub folder). */
    boolean onBack() {
        return false;
    }

    /** Tab tapped again or Home pressed: go back to the page's start. */
    void onReselect() {}

    boolean pageNext() {
        return false;
    }

    boolean pagePrev() {
        return false;
    }

    final String str(int id) {
        return act.getString(id);
    }

    final String str(int id, Object... args) {
        return act.getString(id, args);
    }
}
