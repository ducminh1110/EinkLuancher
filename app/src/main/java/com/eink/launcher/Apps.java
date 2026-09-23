package com.eink.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Installed apps. The list is cached in a small text file so the Apps screen
 * appears instantly on boot; the real PackageManager query runs afterwards
 * on a background thread and only reloads labels of apps that changed.
 */
final class Apps {
    interface Listener {
        void onAppsChanged();
    }

    private static final String CACHE = "apps.cache";
    private static List<AppItem> all = new ArrayList<>();
    private static Listener listener;
    private static boolean loading, pending;

    private Apps() {}

    static List<AppItem> all() {
        return all;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static void loadCache(Context c) {
        File f = new File(c.getFilesDir(), CACHE);
        if (!f.exists()) return;
        List<AppItem> list = new ArrayList<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 8192);
            String head = r.readLine();
            if (head == null || !head.startsWith("v1\t")) return;
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 6) continue;
                AppItem a = new AppItem(p[0], p[1], p[2]);
                a.installed = parse(p[3]);
                a.updated = parse(p[4]);
                a.system = "1".equals(p[5]);
                list.add(a);
            }
        } catch (Exception ignored) {
        } finally {
            close(r);
        }
        sort(list);
        all = list;
    }

    /** Re-queries the PackageManager in the background. */
    static void refresh(final Context ctx) {
        if (loading) {
            pending = true;
            return;
        }
        loading = true;
        final Context app = ctx.getApplicationContext();
        final String locale = Locale.getDefault().toString();
        final HashMap<String, AppItem> old = new HashMap<>();
        for (AppItem a : all) old.put(a.key(), a);
        final boolean sameLocale = locale.equals(cachedLocale(app));
        Worker.io(new Runnable() {
            @Override
            public void run() {
                final List<AppItem> list = query(app, old, sameLocale);
                sort(list);
                save(app, list, locale);
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        // Keep already loaded icons.
                        for (AppItem a : list) {
                            AppItem o = old.get(a.key());
                            if (o != null && o.updated == a.updated) {
                                a.icon = o.icon;
                                a.iconSize = o.iconSize;
                            }
                        }
                        all = list;
                        if (listener != null) listener.onAppsChanged();
                        if (pending) {
                            pending = false;
                            refresh(app);
                        }
                    }
                });
            }
        });
    }

    private static List<AppItem> query(Context c, HashMap<String, AppItem> old, boolean sameLocale) {
        PackageManager pm = c.getPackageManager();
        HashMap<String, PackageInfo> infos = new HashMap<>();
        try {
            for (PackageInfo pi : pm.getInstalledPackages(0)) infos.put(pi.packageName, pi);
        } catch (Exception ignored) {
        }
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(i, 0);
        } catch (Exception e) {
            ris = new ArrayList<>();
        }
        String self = c.getPackageName();
        List<AppItem> out = new ArrayList<>(ris.size());
        for (ResolveInfo ri : ris) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName)) continue;
            PackageInfo pi = infos.get(ai.packageName);
            long updated = pi != null ? pi.lastUpdateTime : 0;
            String key = ai.packageName + "/" + ai.name;
            AppItem prev = old.get(key);
            String label;
            if (prev != null && sameLocale && prev.updated == updated && updated != 0) {
                label = prev.label;
            } else {
                CharSequence cs = ri.loadLabel(pm);
                label = cs == null ? ai.packageName : cs.toString().trim();
            }
            AppItem a = new AppItem(ai.packageName, ai.name, label);
            a.updated = updated;
            a.installed = pi != null ? pi.firstInstallTime : 0;
            int flags = ai.applicationInfo != null ? ai.applicationInfo.flags : 0;
            a.system = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            out.add(a);
        }
        return out;
    }

    static void sort(List<AppItem> list) {
        if (Prefs.i(Prefs.APP_SORT, 0) == 1) {
            Collections.sort(list, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem a, AppItem b) {
                    return a.installed == b.installed ? 0 : (a.installed > b.installed ? -1 : 1);
                }
            });
        } else {
            final Collator col = Collator.getInstance();
            col.setStrength(Collator.PRIMARY);
            Collections.sort(list, new Comparator<AppItem>() {
                @Override
                public int compare(AppItem a, AppItem b) {
                    return col.compare(a.label, b.label);
                }
            });
        }
    }

    static void resort() {
        List<AppItem> copy = new ArrayList<>(all);
        sort(copy);
        all = copy;
        if (listener != null) listener.onAppsChanged();
    }

    private static String cachedLocale(Context c) {
        File f = new File(c.getFilesDir(), CACHE);
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 256);
            String head = r.readLine();
            if (head != null && head.startsWith("v1\t")) return head.substring(3);
        } catch (Exception ignored) {
        } finally {
            close(r);
        }
        return "";
    }

    private static void save(Context c, List<AppItem> list, String locale) {
        File tmp = new File(c.getFilesDir(), CACHE + ".tmp");
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            w.write("v1\t" + locale + "\n");
            for (AppItem a : list) {
                w.write(a.pkg + "\t" + a.cls + "\t" + clean(a.label) + "\t" + a.installed + "\t"
                        + a.updated + "\t" + (a.system ? "1" : "0") + "\n");
            }
            w.close();
            w = null;
            tmp.renameTo(new File(c.getFilesDir(), CACHE));
        } catch (Exception ignored) {
        } finally {
            close(w);
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
