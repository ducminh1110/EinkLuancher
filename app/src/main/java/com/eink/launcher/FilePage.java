package com.eink.launcher;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Common base of the Library and Storage screens: cover grid / list, paging, file actions. */
abstract class FilePage extends Page implements PagedGridView.Adapter, ToolbarView.Listener,
        PagedGridView.PageListener, FooterView.Listener {

    ToolbarView toolbar;
    SearchBar searchBar;
    PagedGridView grid;
    FooterView footer;
    final List<FileItem> items = new ArrayList<>();
    String query = "";

    FilePage(LauncherActivity a) {
        super(a);
    }

    abstract boolean listMode();

    abstract void setListMode(boolean list);

    abstract int searchHint();

    /** Rebuilds {@link #items} from the page's data source (filter + sort). */
    abstract void rebuild();

    abstract void setupToolbar();

    @Override
    View create() {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);

        FrameLayout header = new FrameLayout(act);
        toolbar = new ToolbarView(act);
        toolbar.setListener(this);
        header.addView(toolbar);
        searchBar = new SearchBar(act, toolbar, searchHint());
        searchBar.setListener(new SearchBar.Listener() {
            @Override
            public void onQuery(String folded) {
                query = folded;
                refresh(true);
            }
        });
        header.addView(searchBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.ipx(ToolbarView.HEIGHT)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.ipx(ToolbarView.HEIGHT)));

        grid = new PagedGridView(act);
        grid.setPageListener(this);
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        footer = new FooterView(act, FooterView.INFO);
        footer.setListener(this);
        root.addView(footer);

        setupToolbar();
        applyViewMode();
        rebuild();
        grid.setAdapter(this);
        return root;
    }

    int columns() {
        return Math.max(2, Math.min(5, Prefs.i(Prefs.LIB_COLS, 3)));
    }

    void applyViewMode() {
        if (listMode()) grid.setLayout(1, FileCell.LIST_ROW, false);
        else grid.setCoverLayout(columns(), FileCell.COVER_WIDTH * FileCell.COVER_RATIO, FileCell.ROW_EXTRA + 26);
        footer.setViewIcon(listMode() ? Icons.LIST : Icons.GRID);
    }

    /** Re-filters and redraws; {@code toFirst} jumps back to page 1. */
    void refresh(boolean toFirst) {
        if (grid == null) return;
        rebuild();
        if (toFirst) grid.setPage(0, false);
        grid.notifyDataChanged();
    }

    // ------------------------------------------------------------- adapter

    @Override
    public int getCount() {
        return items.size();
    }

    int coverW() {
        return FileCell.coverW(grid.getCellWidth(), grid.getCellHeight());
    }

    int coverH() {
        return FileCell.coverH(grid.getCellWidth(), grid.getCellHeight());
    }

    /** Covers are always cached at the grid size and scaled down for list rows. */
    private int cacheW() {
        return FileCell.coverW(grid.getWidth() / (float) columns(), 10000f);
    }

    private int cacheH() {
        return Math.round(cacheW() * FileCell.COVER_RATIO);
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        FileItem f = items.get(pos);
        Bitmap cover = f.dir ? null : ImageCache.cover(f, cacheW(), cacheH());
        if (listMode()) FileCell.drawRow(c, f, cover, l, t, r, b, pressed, FileCell.info(act, f));
        else FileCell.drawGrid(c, f, cover, l, t, r, b, pressed);
    }

    @Override
    public void onPageShown(int first, int last) {
        ImageCache.newCoverPage();
        int w = cacheW(), h = cacheH();
        for (int i = first; i <= last && i < items.size(); i++) {
            final FileItem f = items.get(i);
            if (f.dir || ImageCache.cover(f, w, h) != null || ImageCache.hasNoCover(f, w, h)) continue;
            final int pos = i;
            ImageCache.requestCover(f, w, h, new Runnable() {
                @Override
                public void run() {
                    if (pos < items.size() && items.get(pos) == f) grid.invalidateCell(pos);
                }
            });
        }
    }

    @Override
    public boolean onCellLongClick(int pos) {
        final FileItem f = items.get(pos);
        if (f.label != null) return false; // storage roots
        EinkMenu m = new EinkMenu(act);
        if (!f.dir) {
            m.add(str(R.string.open_with), new EinkMenu.Action() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.fromFile(f.file), "*/*");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Library.start(act, Intent.createChooser(i, f.name));
                }
            });
        }
        extraActions(m, f);
        m.add(str(R.string.rename), new EinkMenu.Action() {
            @Override
            public void run() {
                EinkMenu.input(act, str(R.string.rename), f.name, new EinkMenu.TextCallback() {
                    @Override
                    public void onText(String text) {
                        File to = new File(f.file.getParentFile(), text);
                        if (!to.exists() && f.file.renameTo(to)) {
                            Library.forget(f.file);
                            onFilesChanged();
                        } else {
                            act.toast(str(R.string.operation_failed));
                        }
                    }
                });
            }
        });
        m.add(str(R.string.delete), new EinkMenu.Action() {
            @Override
            public void run() {
                EinkMenu.confirm(act, str(R.string.delete), f.name, new EinkMenu.Action() {
                    @Override
                    public void run() {
                        if (deleteRecursive(f.file)) {
                            Library.forget(f.file);
                            onFilesChanged();
                        } else {
                            act.toast(str(R.string.operation_failed));
                        }
                    }
                });
            }
        });
        m.add(str(R.string.details), new EinkMenu.Action() {
            @Override
            public void run() {
                EinkMenu.confirm(act, f.name, f.file.getPath() + "\n" + FileCell.info(act, f), new EinkMenu.Action() {
                    @Override
                    public void run() {}
                });
            }
        });
        m.showCentered(grid, f.name);
        return true;
    }

    /** Page specific entries for the long-press menu. */
    void extraActions(EinkMenu m, FileItem f) {}

    /** Called after rename/delete/new folder. */
    void onFilesChanged() {
        refresh(false);
    }

    static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
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
    public void onFooterToggleView() {
        setListMode(!listMode());
        applyViewMode();
        grid.setPage(0, false);
        grid.notifyDataChanged();
    }

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
    void onHide() {
        if (searchBar != null) searchBar.close();
    }

    @Override
    public void onToolbarBack() {}
}
