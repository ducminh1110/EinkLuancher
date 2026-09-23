package com.eink.launcher;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;

/** Library: every book found on the device, newest read first. */
final class LibraryPage extends FilePage implements Library.Listener {
    private static final long RESCAN_AGE = 10 * 60 * 1000L;

    LibraryPage(LauncherActivity a) {
        super(a);
    }

    @Override
    boolean listMode() {
        return Prefs.i(Prefs.LIB_VIEW, 0) == 1;
    }

    @Override
    void setListMode(boolean list) {
        Prefs.put(Prefs.LIB_VIEW, list ? 1 : 0);
    }

    @Override
    int searchHint() {
        return R.string.search_books;
    }

    @Override
    void setupToolbar() {
        toolbar.set(str(R.string.tab_library), false, Icons.SEARCH, Icons.REFRESH, Icons.SORT, Icons.MENU);
        Library.setListener(this);
    }

    @Override
    void onShow() {
        Library.loadCache(act);
        refresh(false);
        Library.scan(act, RESCAN_AGE);
    }

    @Override
    void onResume() {
        // A book was probably just read: re-sort by "recently opened".
        refresh(false);
    }

    @Override
    public void onLibraryChanged() {
        refresh(false);
    }

    @Override
    void rebuild() {
        items.clear();
        for (FileItem f : Library.books()) {
            if (query.length() > 0 && !f.searchKey().contains(query)) continue;
            f.opened = Prefs.lastOpened(f.file.getPath());
            f.progress = Prefs.progress(f.file.getPath());
            items.add(f);
        }
        sort();
        if (Library.isScanning() && items.isEmpty()) grid.setEmptyText(str(R.string.scanning));
        else if (query.length() > 0) grid.setEmptyText(str(R.string.no_results));
        else if (!Prefs.libFolders().isEmpty()) grid.setEmptyText(str(R.string.no_books_in_folders));
        else grid.setEmptyText(str(R.string.no_books));
        String where = Prefs.libFolders().isEmpty() ? "" : "  ·  " + BookFolders.summary(act);
        footer.setInfo(str(R.string.total_books, Library.books().size()) + where
                + (Library.isScanning() ? "  ·  " + str(R.string.scanning_short) : ""));
    }

    private void sort() {
        final int mode = Prefs.i(Prefs.LIB_SORT, 0);
        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                switch (mode) {
                    case 0: {
                        if (a.opened != b.opened) return a.opened > b.opened ? -1 : 1;
                        return cmpLong(b.modified, a.modified);
                    }
                    case 2:
                        return cmpLong(b.modified, a.modified);
                    case 3:
                        return cmpLong(b.size, a.size);
                    case 4: {
                        int c = a.ext.compareTo(b.ext);
                        if (c != 0) return c;
                        return col.compare(a.name, b.name);
                    }
                    default:
                        return col.compare(a.name, b.name);
                }
            }
        });
    }

    static int cmpLong(long a, long b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    @Override
    public void onCellClick(int pos) {
        Library.open(act, items.get(pos).file);
    }

    @Override
    public void onToolbarAction(int id) {
        if (id == Icons.SEARCH) {
            searchBar.open();
        } else if (id == Icons.REFRESH) {
            Library.scan(act, 0);
            act.toast(str(R.string.scanning));
        } else if (id == Icons.SORT) {
            showSortMenu();
        } else if (id == Icons.MENU) {
            showMenu();
        }
    }

    private void showSortMenu() {
        int cur = Prefs.i(Prefs.LIB_SORT, 0);
        int[] labels = {R.string.sort_recent, R.string.sort_name, R.string.sort_date, R.string.sort_size, R.string.sort_type};
        EinkMenu m = new EinkMenu(act);
        for (int i = 0; i < labels.length; i++) {
            final int mode = i;
            m.add(str(labels[i]), i == cur, new EinkMenu.Action() {
                @Override
                public void run() {
                    Prefs.put(Prefs.LIB_SORT, mode);
                    refresh(true);
                }
            });
        }
        m.show(toolbar, toolbar.anchorX(Icons.SORT));
    }

    private void showMenu() {
        EinkMenu m = new EinkMenu(act);
        m.add(str(R.string.view_list), listMode(), new EinkMenu.Action() {
            @Override
            public void run() {
                onFooterToggleView();
            }
        });
        int cols = columns();
        for (final int c : new int[]{2, 3, 4}) {
            m.add(str(R.string.columns) + ": " + c, !listMode() && cols == c, new EinkMenu.Action() {
                @Override
                public void run() {
                    Prefs.put(Prefs.LIB_COLS, c);
                    Prefs.put(Prefs.LIB_VIEW, 0);
                    applyViewMode();
                    refresh(true);
                }
            });
        }
        m.add(str(R.string.lib_folders_menu), new EinkMenu.Action() {
            @Override
            public void run() {
                BookFolders.show(act, grid, null);
            }
        });
        m.add(str(R.string.rescan), new EinkMenu.Action() {
            @Override
            public void run() {
                Library.scan(act, 0);
            }
        });
        m.show(toolbar, toolbar.anchorX(Icons.MENU));
    }

    @Override
    void onReselect() {
        if (searchBar != null) searchBar.close();
        if (grid != null) grid.setPage(0, false);
    }
}
