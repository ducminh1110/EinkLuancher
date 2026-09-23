package com.eink.launcher;

import java.io.File;
import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Storage: a paged file browser ("← Storage/Books", reference screenshot #2). */
final class StoragePage extends FilePage {
    /** null = list of storage roots. */
    private FileItem root;
    private File dir;
    private List<FileItem> listing = new ArrayList<>();
    private final ArrayDeque<Integer> pageStack = new ArrayDeque<>();
    private int folders, files;
    private int listGen;
    private int restorePage;

    StoragePage(LauncherActivity a) {
        super(a);
    }

    @Override
    boolean listMode() {
        return Prefs.i(Prefs.STO_VIEW, 0) == 1;
    }

    @Override
    void setListMode(boolean list) {
        Prefs.put(Prefs.STO_VIEW, list ? 1 : 0);
    }

    @Override
    int searchHint() {
        return R.string.search_files;
    }

    @Override
    void setupToolbar() {
        toolbar.set(str(R.string.tab_storage), false, Icons.SEARCH, Icons.NEW_FOLDER, Icons.SORT, Icons.MENU);
    }

    @Override
    void onShow() {
        load(false);
    }

    @Override
    void onResume() {
        load(false);
    }

    private void updateTitle() {
        String t = str(R.string.tab_storage);
        boolean back = dir != null;
        if (dir != null && root != null) {
            String rel = dir.getPath().substring(Math.min(dir.getPath().length(), root.file.getPath().length()));
            boolean multi = Library.roots(act).size() > 1;
            t = (multi ? t + "/" + root.label : t) + rel;
            if (!multi && dir.equals(root.file)) back = false;
        }
        toolbar.setTitle(t, back);
    }

    /** Lists the current folder on the IO thread. */
    private void load(final boolean toFirst) {
        updateTitle();
        final int gen = ++listGen;
        final File d = dir;
        final boolean showHidden = Prefs.b(Prefs.SHOW_HIDDEN_FILES, false);
        if (d == null) {
            List<FileItem> roots = Library.roots(act);
            if (roots.size() == 1) {
                // Single storage: open it directly.
                root = roots.get(0);
                dir = root.file;
                load(toFirst);
                return;
            }
            listing = roots;
            refresh(toFirst);
            if (restorePage > 0) grid.setPage(restorePage, false);
            restorePage = 0;
            return;
        }
        Worker.io(new Runnable() {
            @Override
            public void run() {
                final List<FileItem> out = new ArrayList<>();
                File[] list = d.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (!showHidden && f.getName().startsWith(".")) continue;
                        out.add(new FileItem(f));
                    }
                }
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != listGen) return;
                        listing = out;
                        refresh(toFirst);
                        if (restorePage > 0) grid.setPage(restorePage, false);
                        restorePage = 0;
                    }
                });
            }
        });
    }

    @Override
    void rebuild() {
        items.clear();
        folders = 0;
        files = 0;
        for (FileItem f : listing) {
            if (query.length() > 0 && !f.searchKey().contains(query)) continue;
            items.add(f);
            if (f.dir) folders++;
            else files++;
        }
        if (dir != null) sort();
        grid.setEmptyText(str(query.length() > 0 ? R.string.no_results : R.string.empty_folder));
        footer.setInfo(str(R.string.total_folders_files, folders, files));
    }

    private void sort() {
        final int mode = Prefs.i(Prefs.STO_SORT, 0);
        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(items, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                switch (mode) {
                    case 1:
                        return LibraryPage.cmpLong(b.modified, a.modified);
                    case 2:
                        if (!a.dir) return LibraryPage.cmpLong(b.size, a.size);
                        break;
                    case 3: {
                        int c = a.ext.compareTo(b.ext);
                        if (c != 0) return c;
                        break;
                    }
                }
                return col.compare(a.name, b.name);
            }
        });
    }

    @Override
    public void onCellClick(int pos) {
        FileItem f = items.get(pos);
        if (f.dir) {
            if (f.label != null) root = f;
            pageStack.push(grid.getPage());
            dir = f.file;
            if (searchBar.isOpen()) searchBar.close();
            load(true);
        } else {
            Library.open(act, f.file);
        }
    }

    private boolean up() {
        if (dir == null) return false;
        if (root != null && dir.equals(root.file)) {
            if (Library.roots(act).size() <= 1) return false;
            dir = null;
            root = null;
        } else {
            File p = dir.getParentFile();
            dir = p;
            if (p == null) root = null;
        }
        // Come back to the page we left once the listing is loaded.
        restorePage = pageStack.isEmpty() ? 0 : pageStack.pop();
        load(true);
        return true;
    }

    @Override
    boolean onBack() {
        if (super.onBack()) return true;
        return up();
    }

    @Override
    public void onToolbarBack() {
        up();
    }

    @Override
    void onReselect() {
        if (searchBar != null) searchBar.close();
        pageStack.clear();
        dir = null;
        root = null;
        load(true);
    }

    @Override
    void extraActions(EinkMenu m, final FileItem f) {
        if (!f.dir) return;
        m.add(str(R.string.lib_set_folder), new EinkMenu.Action() {
            @Override
            public void run() {
                BookFolders.add(act, f.file, true, null);
            }
        });
        if (!Prefs.libFolders().isEmpty() && !Prefs.libFolders().contains(f.file.getPath())) {
            m.add(str(R.string.lib_add_to_folders), new EinkMenu.Action() {
                @Override
                public void run() {
                    BookFolders.add(act, f.file, false, null);
                }
            });
        }
    }

    @Override
    void onFilesChanged() {
        load(false);
    }

    @Override
    public void onToolbarAction(int id) {
        if (id == Icons.SEARCH) {
            searchBar.open();
        } else if (id == Icons.NEW_FOLDER) {
            if (dir == null) return;
            EinkMenu.input(act, str(R.string.new_folder), null, new EinkMenu.TextCallback() {
                @Override
                public void onText(String text) {
                    if (new File(dir, text).mkdirs()) load(false);
                    else act.toast(str(R.string.operation_failed));
                }
            });
        } else if (id == Icons.SORT) {
            int cur = Prefs.i(Prefs.STO_SORT, 0);
            int[] labels = {R.string.sort_name, R.string.sort_date, R.string.sort_size, R.string.sort_type};
            EinkMenu m = new EinkMenu(act);
            for (int i = 0; i < labels.length; i++) {
                final int mode = i;
                m.add(str(labels[i]), i == cur, new EinkMenu.Action() {
                    @Override
                    public void run() {
                        Prefs.put(Prefs.STO_SORT, mode);
                        refresh(true);
                    }
                });
            }
            m.show(toolbar, toolbar.anchorX(Icons.SORT));
        } else if (id == Icons.MENU) {
            EinkMenu m = new EinkMenu(act);
            m.add(str(R.string.view_list), listMode(), new EinkMenu.Action() {
                @Override
                public void run() {
                    onFooterToggleView();
                }
            });
            final boolean hidden = Prefs.b(Prefs.SHOW_HIDDEN_FILES, false);
            m.add(str(R.string.show_hidden_files), hidden, new EinkMenu.Action() {
                @Override
                public void run() {
                    Prefs.put(Prefs.SHOW_HIDDEN_FILES, !hidden);
                    load(false);
                }
            });
            m.add(str(R.string.refresh), new EinkMenu.Action() {
                @Override
                public void run() {
                    load(false);
                }
            });
            m.show(toolbar, toolbar.anchorX(Icons.MENU));
        }
    }
}
