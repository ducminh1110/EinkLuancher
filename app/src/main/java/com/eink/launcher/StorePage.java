package com.eink.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Store: installed app stores (Play Store, F-Droid, APKPure...) followed by
 * the APK files found on the storage, ready to be installed with one tap.
 */
final class StorePage extends Page implements PagedGridView.Adapter, ToolbarView.Listener,
        PagedGridView.PageListener, FooterView.Listener, Library.Listener {

    private ToolbarView toolbar;
    private PagedGridView grid;
    private FooterView footer;
    private final List<AppItem> stores = new ArrayList<>();
    private final List<FileItem> apks = new ArrayList<>();

    StorePage(LauncherActivity a) {
        super(a);
    }

    @Override
    View create() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);
        toolbar = new ToolbarView(act);
        toolbar.set(str(R.string.tab_store), false, Icons.REFRESH);
        toolbar.setListener(this);
        root.addView(toolbar);
        grid = new PagedGridView(act);
        grid.setAutoLayout(150f, 3, AppCell.MIN_ROW);
        grid.setPageListener(this);
        grid.setEmptyText(str(R.string.store_empty));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new FooterView(act, FooterView.DOTS);
        footer.setListener(this);
        root.addView(footer);
        Library.setStoreListener(this);
        grid.setAdapter(this);
        return root;
    }

    @Override
    void onShow() {
        loadStores();
        Library.loadCache(act);
        onLibraryChanged();
        Library.scan(act, 10 * 60 * 1000L);
    }

    @Override
    void onResume() {
        loadStores();
        onLibraryChanged();
    }

    private void loadStores() {
        stores.clear();
        PackageManager pm = act.getPackageManager();
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + act.getPackageName()));
        HashSet<String> seen = new HashSet<>();
        try {
            for (ResolveInfo ri : pm.queryIntentActivities(market, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                ComponentName cn = launch != null ? launch.getComponent() : null;
                String cls = cn != null ? cn.getClassName() : ri.activityInfo.name;
                AppItem a = new AppItem(pkg, cls, String.valueOf(ri.loadLabel(pm)));
                if (a.glyph < 0) a.glyph = Icons.BAG;
                stores.add(a);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onLibraryChanged() {
        if (grid == null) return;
        apks.clear();
        apks.addAll(Library.apks());
        grid.notifyDataChanged();
    }

    // ------------------------------------------------------------- adapter

    @Override
    public int getCount() {
        return stores.size() + apks.size();
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        if (pos < stores.size()) {
            AppItem a = stores.get(pos);
            boolean line = Prefs.i(Prefs.ICON_STYLE, 0) == 0;
            Bitmap icon = line ? null : ImageCache.icon(a, AppCell.iconSize(AppCell.scale(r - l, b - t)));
            AppCell.draw(c, l, t, r, b, pressed, icon != null ? -1 : a.glyph, icon, a.label, false);
        } else {
            FileItem f = apks.get(pos - stores.size());
            AppCell.draw(c, l, t, r, b, pressed, Icons.APK, null, Text.stripExt(f.name), false);
        }
    }

    @Override
    public void onPageShown(int first, int last) {
        if (Prefs.i(Prefs.ICON_STYLE, 0) == 0) return;
        final int size = AppCell.iconSize(AppCell.scale(grid.getCellWidth(), grid.getCellHeight()));
        for (int i = first; i <= last && i < stores.size(); i++) {
            final int pos = i;
            ImageCache.requestIcon(stores.get(i), size, new Runnable() {
                @Override
                public void run() {
                    grid.invalidateCell(pos);
                }
            });
        }
    }

    @Override
    public void onCellClick(int pos) {
        if (pos < stores.size()) {
            AppItem a = stores.get(pos);
            Intent i = act.getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=reader")).setPackage(a.pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!Library.start(act, i)) act.toast(str(R.string.cannot_open_app));
        } else {
            Library.open(act, apks.get(pos - stores.size()).file);
        }
    }

    @Override
    public boolean onCellLongClick(int pos) {
        if (pos < stores.size()) return false;
        final FileItem f = apks.get(pos - stores.size());
        EinkMenu m = new EinkMenu(act);
        m.add(str(R.string.install), new EinkMenu.Action() {
            @Override
            public void run() {
                Library.open(act, f.file);
            }
        });
        m.add(str(R.string.delete), new EinkMenu.Action() {
            @Override
            public void run() {
                EinkMenu.confirm(act, str(R.string.delete), f.name, new EinkMenu.Action() {
                    @Override
                    public void run() {
                        if (f.file.delete()) Library.forget(f.file);
                        else act.toast(str(R.string.operation_failed));
                    }
                });
            }
        });
        m.showCentered(grid, f.name);
        return true;
    }

    @Override
    public void onToolbarAction(int id) {
        if (id == Icons.REFRESH) {
            loadStores();
            Library.scan(act, 0);
            grid.notifyDataChanged();
        }
    }

    @Override
    public void onToolbarBack() {}

    @Override
    public void onPageChanged(PagedGridView v, int page, int count, boolean byUser) {
        footer.setPage(page, count);
        if (byUser) act.onPageTurn();
    }

    @Override
    public void onFooterPrev() {
        grid.prevPage();
    }

    @Override
    public void onFooterNext() {
        grid.nextPage();
    }

    @Override
    public void onFooterToggleView() {}

    @Override
    boolean pageNext() {
        return grid != null && grid.nextPage();
    }

    @Override
    boolean pagePrev() {
        return grid != null && grid.prevPage();
    }

    @Override
    void onReselect() {
        if (grid != null) grid.setPage(0, false);
    }
}
