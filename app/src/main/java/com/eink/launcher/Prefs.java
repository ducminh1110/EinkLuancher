package com.eink.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Launcher preferences (thin wrapper over SharedPreferences). */
final class Prefs {
    static final String LANG = "lang";                 // "en", "vi", "sys"
    static final String FULLSCREEN = "fullscreen";     // custom BOOX status bar
    static final String APP_COLS = "app_cols";         // 0 = auto
    static final String ICON_STYLE = "icon_style";     // 0 = line art, 1 = original (gray)
    static final String BADGE = "badge";               // badge on user-installed apps
    static final String APP_SORT = "app_sort";         // 0 = name, 1 = newest
    static final String HIDDEN = "hidden_apps";
    static final String LIB_SORT = "lib_sort";         // 0 recent,1 name,2 date,3 size,4 type
    static final String LIB_VIEW = "lib_view";         // 0 grid, 1 list
    static final String STO_SORT = "sto_sort";         // 0 name,1 date,2 size,3 type
    static final String STO_VIEW = "sto_view";         // 0 grid, 1 list
    static final String SHOW_HIDDEN_FILES = "show_hidden_files";
    static final String REFRESH_EVERY = "refresh_every"; // full e-ink refresh every N page turns (0 = off)
    static final String VOLUME_KEYS = "volume_keys";
    static final String START_TAB = "start_tab";
    static final String LIB_COLS = "lib_cols";
    static final String BUILTIN_READER = "builtin_reader";
    static final String READER_SIZE = "reader_size";
    static final String READER_FACE = "reader_face";       // 0 sans, 1 serif, 2 mono
    static final String READER_SPACING = "reader_spacing";
    static final String READER_MARGIN = "reader_margin";

    private static SharedPreferences sp;
    private static SharedPreferences recent;

    private Prefs() {}

    static void init(Context c) {
        if (sp == null) {
            sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
            recent = c.getSharedPreferences("recent", Context.MODE_PRIVATE);
        }
    }

    static int i(String k, int def) {
        return sp.getInt(k, def);
    }

    static boolean b(String k, boolean def) {
        return sp.getBoolean(k, def);
    }

    static float f(String k, float def) {
        return sp.getFloat(k, def);
    }

    static void put(String k, float v) {
        sp.edit().putFloat(k, v).apply();
    }

    static String str(String k, String def) {
        return sp.getString(k, def);
    }

    static void put(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    static void put(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
    }

    static void put(String k, String v) {
        sp.edit().putString(k, v).apply();
    }

    static Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(HIDDEN, new HashSet<String>()));
    }

    static void setHidden(Set<String> set) {
        sp.edit().putStringSet(HIDDEN, set).apply();
    }

    static long lastOpened(String path) {
        return recent.getLong(path, 0);
    }

    static void markOpened(String path) {
        recent.edit().putLong(path, System.currentTimeMillis()).apply();
    }

    /** Reading position: {chapter, char offset}. */
    static int[] position(String path) {
        String v = recent.getString("pos:" + path, null);
        if (v == null) return new int[]{0, 0};
        try {
            int i = v.indexOf(':');
            return new int[]{Integer.parseInt(v.substring(0, i)), Integer.parseInt(v.substring(i + 1))};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    static void savePosition(String path, int chapter, int offset, float progress) {
        recent.edit().putString("pos:" + path, chapter + ":" + offset)
                .putFloat("prog:" + path, progress)
                .putLong(path, System.currentTimeMillis())
                .apply();
    }

    static float progress(String path) {
        return recent.getFloat("prog:" + path, 0f);
    }

    static String lang() {
        return str(LANG, "en");
    }

    static int startTab() {
        return i(START_TAB, 3);
    }

    static int refreshEvery() {
        return i(REFRESH_EVERY, 0);
    }
}
