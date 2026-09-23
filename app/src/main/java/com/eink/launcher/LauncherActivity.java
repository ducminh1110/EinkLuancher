package com.eink.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/**
 * Single-activity e-ink launcher for Android 4.4 (API 19) devices with very
 * little RAM (256 MB, ARM Cortex-A9).
 *
 * Design rules followed everywhere:
 * <ul>
 * <li>no animations, no scrolling: content is paged (one e-ink refresh per page);</li>
 * <li>very few views: grids draw all their cells on one canvas;</li>
 * <li>vector line icons instead of bitmaps, bitmaps only for the visible page;</li>
 * <li>pages and their data are created lazily on first use;</li>
 * <li>software rendering (no GL context), no support libraries.</li>
 * </ul>
 */
public final class LauncherActivity extends Activity implements TabBarView.Listener {
    static final int TAB_LIBRARY = 0, TAB_STORE = 1, TAB_STORAGE = 2, TAB_APPS = 3, TAB_SETTINGS = 4;

    private StatusBarView statusBar;
    private TabBarView tabs;
    private FrameLayout content;
    private View flash;
    private final Page[] pages = new Page[5];
    private int current = -1;
    private int pageTurns;
    private boolean started, firstResume = true;
    private String builtLocale;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Apps.refresh(LauncherActivity.this);
        }
    };

    private final Runnable rescan = new Runnable() {
        @Override
        public void run() {
            Library.scan(LauncherActivity.this, 0);
            if (current == TAB_STORAGE) pages[TAB_STORAGE].onResume();
        }
    };

    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Worker.cancelUi(rescan);
            Worker.uiDelayed(rescan, 1500);
        }
    };

    private final Runnable hideFlash = new Runnable() {
        @Override
        public void run() {
            flash.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Prefs.init(this);
        applyLocale();
        builtLocale = getResources().getConfiguration().locale.toString();
        Ui.init(this);
        ImageCache.init(this);
        Apps.loadCache(this);

        buildUi();
        applyFullscreen();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
        IntentFilter ef = new IntentFilter();
        ef.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        ef.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        registerReceiver(packageReceiver, ef);
        IntentFilter mf = new IntentFilter();
        mf.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_REMOVED);
        mf.addAction(Intent.ACTION_MEDIA_EJECT);
        mf.addDataScheme("file");
        registerReceiver(mediaReceiver, mf);

        showTab(Math.max(0, Math.min(4, Prefs.startTab())));
        Apps.refresh(this);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.WHITE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        statusBar = new StatusBarView(this);
        col.addView(statusBar);
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tabs = new TabBarView(this,
                new int[]{Icons.T_LIBRARY, Icons.T_STORE, Icons.T_STORAGE, Icons.T_APPS, Icons.T_SETTINGS},
                new String[]{getString(R.string.tab_library), getString(R.string.tab_store),
                        getString(R.string.tab_storage), getString(R.string.tab_apps), getString(R.string.tab_settings)});
        tabs.setListener(this);
        col.addView(tabs);
        root.addView(col);
        flash = new View(this);
        flash.setBackgroundColor(Ui.BLACK);
        flash.setVisibility(View.GONE);
        root.addView(flash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        pages[TAB_LIBRARY] = new LibraryPage(this);
        pages[TAB_STORE] = new StorePage(this);
        pages[TAB_STORAGE] = new StoragePage(this);
        pages[TAB_APPS] = new AppsPage(this);
        pages[TAB_SETTINGS] = new SettingsPage(this);
    }

    // ------------------------------------------------------------------ tabs

    void showTab(int i) {
        if (current == i) return;
        if (current >= 0) {
            pages[current].onHide();
            pages[current].view().setVisibility(View.GONE);
        }
        current = i;
        tabs.setSelected(i);
        View v = pages[i].view();
        if (v.getParent() == null) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        v.setVisibility(View.VISIBLE);
        pages[i].onShow();
    }

    @Override
    public void onTabSelected(int index, boolean reselected) {
        if (reselected) {
            pages[index].onReselect();
        } else {
            showTab(index);
            onPageTurn();
        }
    }

    void openLauncherSettings() {
        showTab(TAB_SETTINGS);
        ((SettingsPage) pages[TAB_SETTINGS]).openLauncherSettings();
    }

    void onAppLayoutChanged() {
        if (pages[TAB_APPS].isCreated()) ((AppsPage) pages[TAB_APPS]).layoutChanged();
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (Prefs.b(Prefs.FULLSCREEN, true)) statusBar.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        statusBar.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        if (current >= 0) pages[current].onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusBar.stop();
        try {
            unregisterReceiver(packageReceiver);
            unregisterReceiver(mediaReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        Apps.setListener(null);
        Library.setListener(null);
        Library.setStoreListener(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            boolean alreadyHome = hasWindowFocus()
                    && (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0;
            if (alreadyHome) {
                // Home pressed while on the launcher: back to the start tab, first page.
                int st = Math.max(0, Math.min(4, Prefs.startTab()));
                pages[current].onReselect();
                if (st != current) {
                    showTab(st);
                    pages[st].onReselect();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (current >= 0) pages[current].onBack();
        // A launcher never finishes on Back.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        View focus = getCurrentFocus();
        boolean typing = focus instanceof EditText;
        boolean vol = Prefs.b(Prefs.VOLUME_KEYS, true);
        int dir = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_DOWN:
                dir = 1;
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
                dir = -1;
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (vol) dir = 1;
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (vol) dir = -1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!typing) dir = 1;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!typing) dir = -1;
                break;
        }
        if (dir != 0 && current >= 0) {
            boolean moved = dir > 0 ? pages[current].pageNext() : pages[current].pagePrev();
            if (moved) onPageTurn();
            return true; // also swallow volume keys at the ends (no volume panel ghosting)
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                && Prefs.b(Prefs.VOLUME_KEYS, true)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) ImageCache.trim(true);
        else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) ImageCache.trim(false);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ImageCache.trim(true);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyLocale();
        if (!getResources().getConfiguration().locale.toString().equals(builtLocale)) {
            restart();
        }
    }

    // --------------------------------------------------------------- helpers

    private void applyLocale() {
        Locales.apply(this);
    }

    void restart() {
        recreate();
    }

    void applyFullscreen() {
        boolean fs = Prefs.b(Prefs.FULLSCREEN, true);
        if (fs) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        statusBar.setVisibility(fs ? View.VISIBLE : View.GONE);
        if (fs && started) statusBar.start();
        else statusBar.stop();
    }

    /** Counts page turns and triggers the optional anti-ghosting full refresh. */
    void onPageTurn() {
        pageTurns++;
        int n = Prefs.refreshEvery();
        if (n > 0 && pageTurns % n == 0) fullRefresh();
    }

    /**
     * Generic e-ink full refresh: flash the screen black for a moment. Works on
     * any e-paper controller without vendor specific APIs.
     */
    void fullRefresh() {
        Worker.cancelUi(hideFlash);
        flash.setVisibility(View.VISIBLE);
        flash.bringToFront();
        Worker.uiDelayed(hideFlash, 220);
    }

    /** Snowflake button: kill background processes to free RAM on 256 MB devices. */
    void freeMemory() {
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        final long before = mi.availMem;
        String self = getPackageName();
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs != null) {
            for (ActivityManager.RunningAppProcessInfo p : procs) {
                if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue;
                if (p.pkgList == null) continue;
                for (String pkg : p.pkgList) {
                    if (pkg.equals(self)) continue;
                    try {
                        am.killBackgroundProcesses(pkg);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        ImageCache.trim(false);
        Worker.uiDelayed(new Runnable() {
            @Override
            public void run() {
                am.getMemoryInfo(mi);
                long freed = Math.max(0, mi.availMem - before);
                toast(getString(R.string.memory_freed, Text.size(freed), Text.size(mi.availMem)));
            }
        }, 700);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
