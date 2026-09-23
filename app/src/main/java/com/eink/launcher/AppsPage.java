package com.eink.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The Apps screen (reference screenshot #1). */
final class AppsPage extends Page implements PagedGridView.Adapter, ToolbarView.Listener,
        PagedGridView.PageListener, FooterView.Listener, Apps.Listener {

    private ToolbarView toolbar;
    private SearchBar searchBar;
    private PagedGridView grid;
    private FooterView footer;
    private final List<AppItem> items = new ArrayList<>();
    private String query = "";

    AppsPage(LauncherActivity a) {
        super(a);
    }

    @Override
    View create() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);

        FrameLayout header = new FrameLayout(act);
        toolbar = new ToolbarView(act);
        toolbar.set("", false, Icons.SEARCH, Icons.SNOWFLAKE, Icons.MENU);
        toolbar.setListener(this);
        header.addView(toolbar);
        searchBar = new SearchBar(act, toolbar, R.string.search_apps);
        searchBar.setListener(new SearchBar.Listener() {
            @Override
            public void onQuery(String folded) {
                query = folded;
                rebuild();
                grid.setPage(0, false);
                grid.notifyDataChanged();
            }
        });
        header.addView(searchBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.ipx(ToolbarView.HEIGHT)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.ipx(ToolbarView.HEIGHT)));

        grid = new PagedGridView(act);
        applyColumns();
        grid.setPageListener(this);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        footer = new FooterView(act, FooterView.DOTS);
        footer.setListener(this);
        root.addView(footer);

        Apps.setListener(this);
        rebuild();
        grid.setAdapter(this);
        return root;
    }

    void applyColumns() {
        int cols = Prefs.i(Prefs.APP_COLS, 0);
        if (cols > 0) grid.setLayout(cols, AppCell.MIN_ROW, false);
        else grid.setAutoLayout(150f, 3, AppCell.MIN_ROW);
    }

    /** Columns / icon style / badge setting changed. */
    void layoutChanged() {
        applyColumns();
        grid.notifyDataChanged();
    }

    private void rebuild() {
        items.clear();
        Set<String> hidden = Prefs.hidden();
        for (AppItem a : Apps.all()) {
            if (query.length() > 0) {
                if (!a.searchKey().contains(query)) continue;
            } else if (hidden.contains(a.key())) {
                continue;
            }
            items.add(a);
        }
        grid.setEmptyText(query.length() > 0 ? str(R.string.no_results) : str(R.string.loading_apps));
    }

    @Override
    public void onAppsChanged() {
        if (grid == null) return;
        rebuild();
        grid.notifyDataChanged();
    }

    // ------------------------------------------------------------- adapter

    @Override
    public int getCount() {
        return items.size();
    }

    private boolean lineStyle() {
        return Prefs.i(Prefs.ICON_STYLE, 0) == 0;
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        AppItem a = items.get(pos);
        int glyph = lineStyle() ? a.glyph : -1;
        Bitmap icon = null;
        if (glyph < 0) {
            icon = ImageCache.icon(a, AppCell.iconSize(AppCell.scale(r - l, b - t)));
            if (icon == null) glyph = a.glyph; // show the line glyph until the real icon is ready
        }
        boolean badge = !a.system && Prefs.b(Prefs.BADGE, true);
        AppCell.draw(c, l, t, r, b, pressed, glyph, icon, a.label, badge);
    }

    @Override
    public void onPageShown(int first, int last) {
        ImageCache.newIconPage();
        final int size = AppCell.iconSize(AppCell.scale(grid.getCellWidth(), grid.getCellHeight()));
        boolean line = lineStyle();
        for (int i = first; i <= last && i < items.size(); i++) {
            final AppItem a = items.get(i);
            if (line && a.glyph >= 0) continue;
            if (ImageCache.icon(a, size) != null) continue;
            final int pos = i;
            ImageCache.requestIcon(a, size, new Runnable() {
                @Override
                public void run() {
                    if (pos < items.size() && items.get(pos) == a) grid.invalidateCell(pos);
                }
            });
        }
    }

    @Override
    public void onCellClick(int pos) {
        AppItem a = items.get(pos);
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(a.component());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!Library.start(act, i)) act.toast(str(R.string.cannot_open_app));
        if (query.length() > 0) searchBar.close();
    }

    @Override
    public boolean onCellLongClick(int pos) {
        final AppItem a = items.get(pos);
        EinkMenu m = new EinkMenu(act);
        m.add(str(R.string.app_info), new EinkMenu.Action() {
            @Override
            public void run() {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Library.start(act, i);
            }
        });
        if (!a.system) {
            m.add(str(R.string.uninstall), new EinkMenu.Action() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Library.start(act, i);
                }
            });
        }
        final Set<String> hidden = Prefs.hidden();
        final boolean isHidden = hidden.contains(a.key());
        m.add(str(isHidden ? R.string.unhide_app : R.string.hide_app), new EinkMenu.Action() {
            @Override
            public void run() {
                if (isHidden) hidden.remove(a.key());
                else hidden.add(a.key());
                Prefs.setHidden(hidden);
                onAppsChanged();
            }
        });
        m.showCentered(grid, a.label);
        return true;
    }

    // ------------------------------------------------------------- toolbar

    @Override
    public void onToolbarAction(int id) {
        if (id == Icons.SEARCH) searchBar.open();
        else if (id == Icons.SNOWFLAKE) act.freeMemory();
        else if (id == Icons.MENU) showMenu();
    }

    @Override
    public void onToolbarBack() {}

    private void showMenu() {
        int sort = Prefs.i(Prefs.APP_SORT, 0);
        EinkMenu m = new EinkMenu(act);
        m.add(str(R.string.sort_by_name), sort == 0, new EinkMenu.Action() {
            @Override
            public void run() {
                Prefs.put(Prefs.APP_SORT, 0);
                Apps.resort();
            }
        });
        m.add(str(R.string.sort_by_newest), sort == 1, new EinkMenu.Action() {
            @Override
            public void run() {
                Prefs.put(Prefs.APP_SORT, 1);
                Apps.resort();
            }
        });
        m.add(str(R.string.line_icons), lineStyle(), new EinkMenu.Action() {
            @Override
            public void run() {
                Prefs.put(Prefs.ICON_STYLE, lineStyle() ? 1 : 0);
                grid.notifyDataChanged();
            }
        });
        m.add(str(R.string.columns) + ": " + colsLabel(), new EinkMenu.Action() {
            @Override
            public void run() {
                showColumnsMenu();
            }
        });
        final Set<String> hidden = Prefs.hidden();
        m.add(str(R.string.hidden_apps) + " (" + hidden.size() + ")", new EinkMenu.Action() {
            @Override
            public void run() {
                showHiddenMenu();
            }
        });
        m.add(str(R.string.refresh), new EinkMenu.Action() {
            @Override
            public void run() {
                Apps.refresh(act);
            }
        });
        m.add(str(R.string.launcher_settings), new EinkMenu.Action() {
            @Override
            public void run() {
                act.openLauncherSettings();
            }
        });
        m.show(toolbar, toolbar.anchorX(Icons.MENU));
    }

    private String colsLabel() {
        int c = Prefs.i(Prefs.APP_COLS, 0);
        return c == 0 ? str(R.string.auto) : String.valueOf(c);
    }

    private void showColumnsMenu() {
        int cur = Prefs.i(Prefs.APP_COLS, 0);
        EinkMenu m = new EinkMenu(act);
        int[] opts = {0, 3, 4, 5, 6};
        for (final int o : opts) {
            m.add(o == 0 ? str(R.string.auto) : String.valueOf(o), o == cur, new EinkMenu.Action() {
                @Override
                public void run() {
                    Prefs.put(Prefs.APP_COLS, o);
                    applyColumns();
                }
            });
        }
        m.showCentered(grid, str(R.string.columns));
    }

    private void showHiddenMenu() {
        final Set<String> hidden = Prefs.hidden();
        if (hidden.isEmpty()) {
            act.toast(str(R.string.no_hidden_apps));
            return;
        }
        EinkMenu m = new EinkMenu(act);
        for (final AppItem a : Apps.all()) {
            if (!hidden.contains(a.key())) continue;
            m.add(a.label, true, new EinkMenu.Action() {
                @Override
                public void run() {
                    hidden.remove(a.key());
                    Prefs.setHidden(hidden);
                    onAppsChanged();
                }
            });
        }
        m.showCentered(grid, str(R.string.tap_to_unhide));
    }

    // ---------------------------------------------------------------- paging

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
    boolean onBack() {
        return searchBar != null && searchBar.close();
    }

    @Override
    void onReselect() {
        if (searchBar != null) searchBar.close();
        if (grid != null) grid.setPage(0, false);
    }

    @Override
    void onHide() {
        if (searchBar != null) searchBar.close();
    }
}
