package com.eink.launcher;

import android.view.View;

import java.io.File;
import java.util.List;

/**
 * "Book folders" chooser: the Library can scan only some folders (e.g.
 * /sdcard/Books) instead of the whole storage - faster scans on slow SD
 * cards and no stray documents in the Library.
 */
final class BookFolders {
    private static final int MAX = 8;

    private BookFolders() {}

    /** Short description for settings rows / the Library footer. */
    static String summary(LauncherActivity act) {
        List<String> f = Prefs.libFolders();
        if (f.isEmpty()) return act.getString(R.string.lib_all_storage);
        if (f.size() == 1) return Library.displayPath(act, new File(f.get(0)));
        return act.getString(R.string.lib_n_folders, f.size());
    }

    static void show(final LauncherActivity act, final View anchor, final Runnable changed) {
        final List<String> folders = Prefs.libFolders();
        EinkMenu m = new EinkMenu(act);
        for (final String p : folders) {
            m.add(Library.displayPath(act, new File(p)), true, new EinkMenu.Action() {
                @Override
                public void run() {
                    EinkMenu.confirm(act, act.getString(R.string.lib_remove_folder),
                            Library.displayPath(act, new File(p)), new EinkMenu.Action() {
                                @Override
                                public void run() {
                                    folders.remove(p);
                                    save(act, folders, changed);
                                }
                            });
                }
            });
        }
        if (folders.size() < MAX) {
            m.add(act.getString(R.string.lib_add_folder), new EinkMenu.Action() {
                @Override
                public void run() {
                    File start = folders.isEmpty() ? null : new File(folders.get(folders.size() - 1));
                    FolderPicker.show(act, start, new FolderPicker.Callback() {
                        @Override
                        public void onPicked(File dir) {
                            add(act, dir, false, changed);
                        }
                    });
                }
            });
        }
        m.add(act.getString(R.string.lib_scan_all), folders.isEmpty(), new EinkMenu.Action() {
            @Override
            public void run() {
                folders.clear();
                save(act, folders, changed);
            }
        });
        m.showCentered(anchor, act.getString(R.string.lib_folders));
    }

    /** Adds a folder, or makes it the only one when {@code replace} is true. */
    static void add(LauncherActivity act, File dir, boolean replace, Runnable changed) {
        List<String> folders = replace ? new java.util.ArrayList<String>() : Prefs.libFolders();
        String p = dir.getPath();
        if (!folders.contains(p)) {
            if (folders.size() >= MAX) folders.remove(0);
            folders.add(p);
        }
        save(act, folders, changed);
        act.toast(act.getString(R.string.lib_folder_set, summary(act)));
    }

    private static void save(LauncherActivity act, List<String> folders, Runnable changed) {
        Prefs.setLibFolders(folders);
        Library.applyFolders(act);
        if (changed != null) changed.run();
    }
}
