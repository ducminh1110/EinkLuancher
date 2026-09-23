package com.eink.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Finds books (and APK files for the Store screen) on all storages.
 * The result is cached in a text file; the scan itself runs on the low
 * priority IO thread and never blocks the UI.
 */
final class Library {
    interface Listener {
        void onLibraryChanged();
    }

    static final HashSet<String> BOOKS = new HashSet<>(Arrays.asList(
            "epub", "pdf", "mobi", "azw", "azw3", "prc", "fb2", "txt", "djvu", "djv", "cbz", "cbr",
            "rtf", "doc", "docx", "chm", "odt", "htm", "html"));
    private static final HashSet<String> SKIP = new HashSet<>(Arrays.asList(
            "android", "lost.dir", "dcim", "alarms", "notifications", "ringtones", "system volume information"));
    private static final String CACHE = "library.cache";
    private static final int MAX_FILES = 20000;

    private static List<FileItem> books = new ArrayList<>();
    private static List<FileItem> apks = new ArrayList<>();
    private static Listener listener;
    private static Listener storeListener;
    private static boolean scanning, cacheLoaded, rescanPending;
    private static long lastScan;

    private Library() {}

    static List<FileItem> books() {
        return books;
    }

    static List<FileItem> apks() {
        return apks;
    }

    static boolean isScanning() {
        return scanning;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    static void setStoreListener(Listener l) {
        storeListener = l;
    }

    static boolean isBook(String name, String ext) {
        if (BOOKS.contains(ext)) return true;
        return "zip".equals(ext) && name.toLowerCase(Locale.US).endsWith(".fb2.zip");
    }

    // --------------------------------------------------------------- storages

    /** All readable storage roots (internal first). */
    static List<FileItem> roots(Context c) {
        LinkedHashMap<String, FileItem> out = new LinkedHashMap<>();
        File primary = Environment.getExternalStorageDirectory();
        addRoot(out, primary, c.getString(Environment.isExternalStorageRemovable()
                ? R.string.sd_card : R.string.internal_storage));
        List<String> cands = new ArrayList<>();
        String env = System.getenv("SECONDARY_STORAGE");
        if (env != null) cands.addAll(Arrays.asList(env.split(":")));
        try {
            // Hidden but present on 4.x; unlike getExternalFilesDirs() it creates no folders.
            Object sm = c.getSystemService(Context.STORAGE_SERVICE);
            String[] vols = (String[]) sm.getClass().getMethod("getVolumePaths").invoke(sm);
            if (vols != null) cands.addAll(Arrays.asList(vols));
        } catch (Throwable ignored) {
        }
        cands.addAll(Arrays.asList("/storage/sdcard1", "/storage/extSdCard", "/mnt/extsd", "/mnt/external_sd",
                "/mnt/sdcard/external_sd", "/mnt/ext_sdcard", "/storage/external_SD", "/mnt/usb_storage",
                "/storage/usbdisk"));
        for (String p : cands) {
            if (p == null || p.trim().isEmpty()) continue;
            File f = new File(p.trim());
            addRoot(out, f, out.size() == 1 ? c.getString(R.string.sd_card) : f.getName());
        }
        return new ArrayList<>(out.values());
    }

    private static void addRoot(LinkedHashMap<String, FileItem> out, File f, String label) {
        try {
            if (f == null || !f.isDirectory() || !f.canRead()) return;
            String[] kids = f.list();
            if (kids == null) return;
            String canon = f.getCanonicalPath();
            if (out.containsKey(canon)) return;
            FileItem it = new FileItem(f, true);
            it.label = label;
            out.put(canon, it);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------- scan

    static void loadCache(Context c) {
        if (cacheLoaded) return;
        cacheLoaded = true;
        File f = new File(c.getFilesDir(), CACHE);
        if (!f.exists()) return;
        List<FileItem> b = new ArrayList<>(), a = new ArrayList<>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 8192);
            String head = r.readLine();
            if (head != null && head.startsWith("v1\t")) {
                lastScan = Long.parseLong(head.substring(3));
                String line;
                while ((line = r.readLine()) != null) {
                    String[] p = line.split("\t");
                    if (p.length < 4) continue;
                    FileItem it = new FileItem(p[1], Long.parseLong(p[2]), Long.parseLong(p[3]));
                    if ("A".equals(p[0])) a.add(it);
                    else b.add(it);
                }
            }
        } catch (Exception ignored) {
        } finally {
            Apps.close(r);
        }
        books = b;
        apks = a;
    }

    /** Rescans storages unless a scan ran within {@code maxAgeMs}. */
    static void scan(Context ctx, long maxAgeMs) {
        if (scanning) {
            if (maxAgeMs == 0) rescanPending = true;
            return;
        }
        if (maxAgeMs > 0 && System.currentTimeMillis() - lastScan < maxAgeMs) return;
        scanning = true;
        final Context app = ctx.getApplicationContext();
        final List<FileItem> roots = roots(app);
        final List<File> folders = folders();
        notifyListeners();
        Worker.io(new Runnable() {
            @Override
            public void run() {
                final List<FileItem> b = new ArrayList<>(), a = new ArrayList<>();
                ArrayDeque<File> dirs = new ArrayDeque<>();
                ArrayDeque<Integer> depth = new ArrayDeque<>();
                ArrayDeque<Boolean> apkOnly = new ArrayDeque<>();
                if (folders.isEmpty()) {
                    // Whole storage.
                    for (FileItem r : roots) {
                        dirs.push(r.file);
                        depth.push(0);
                        apkOnly.push(false);
                    }
                } else {
                    // Only the chosen book folders; APKs (Store tab) still come from Download.
                    for (File f : folders) {
                        dirs.push(f);
                        depth.push(0);
                        apkOnly.push(false);
                    }
                    for (FileItem r : roots) {
                        for (String n : new String[]{"Download", "download", "Downloads"}) {
                            File d = new File(r.file, n);
                            if (d.isDirectory()) {
                                dirs.push(d);
                                depth.push(7);
                                apkOnly.push(true);
                            }
                        }
                    }
                }
                HashSet<String> seen = new HashSet<>();
                while (!dirs.isEmpty() && b.size() + a.size() < MAX_FILES) {
                    File dir = dirs.pop();
                    int d = depth.pop();
                    boolean onlyApk = apkOnly.pop();
                    File[] list = dir.listFiles();
                    if (list == null) continue;
                    for (File f : list) {
                        String name = f.getName();
                        if (name.startsWith(".")) continue;
                        if (f.isDirectory()) {
                            if (d < 9 && !SKIP.contains(name.toLowerCase(Locale.US))) {
                                dirs.push(f);
                                depth.push(d + 1);
                                apkOnly.push(onlyApk);
                            }
                            continue;
                        }
                        String ext = Text.ext(name);
                        boolean book = !onlyApk && isBook(name, ext);
                        if (!book && !"apk".equals(ext)) continue;
                        String key = name + "|" + f.length();
                        if (!seen.add(key)) continue; // same file visible through two mount points
                        FileItem it = new FileItem(f, false);
                        if (book) b.add(it);
                        else a.add(it);
                    }
                }
                final long now = System.currentTimeMillis();
                save(app, b, a, now);
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        books = b;
                        apks = a;
                        lastScan = now;
                        scanning = false;
                        notifyListeners();
                        if (rescanPending) {
                            rescanPending = false;
                            scan(app, 0);
                        }
                    }
                });
            }
        });
    }

    // ----------------------------------------------------------- book folders

    /** Folders chosen in "Book folders"; empty = scan the whole storage. */
    static List<File> folders() {
        List<File> out = new ArrayList<>();
        for (String p : Prefs.libFolders()) {
            File f = new File(p);
            if (f.isDirectory()) out.add(f);
        }
        return out;
    }

    /** Applies a new folder selection: hide books outside it right away, then rescan. */
    static void applyFolders(Context c) {
        List<File> folders = folders();
        if (!folders.isEmpty()) {
            List<FileItem> nb = new ArrayList<>();
            for (FileItem f : books) {
                if (inFolders(f.file, folders)) nb.add(f);
            }
            books = nb;
        }
        notifyListeners();
        scan(c, 0);
    }

    private static boolean inFolders(File f, List<File> folders) {
        String p = f.getPath();
        for (File d : folders) {
            String dp = d.getPath();
            if (p.startsWith(dp.endsWith("/") ? dp : dp + "/")) return true;
        }
        return false;
    }

    /** "SD card/Books" style name for a path. */
    static String displayPath(Context c, File f) {
        String p = f.getPath();
        for (FileItem r : roots(c)) {
            String rp = r.file.getPath();
            if (p.equals(rp)) return r.label;
            if (p.startsWith(rp + "/")) return r.label + p.substring(rp.length());
        }
        return p;
    }

    private static void notifyListeners() {
        if (listener != null) listener.onLibraryChanged();
        if (storeListener != null) storeListener.onLibraryChanged();
    }

    private static void save(Context c, List<FileItem> b, List<FileItem> a, long time) {
        File tmp = new File(c.getFilesDir(), CACHE + ".tmp");
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            w.write("v1\t" + time + "\n");
            for (FileItem f : b) w.write("B\t" + f.file.getPath() + "\t" + f.size + "\t" + f.modified + "\n");
            for (FileItem f : a) w.write("A\t" + f.file.getPath() + "\t" + f.size + "\t" + f.modified + "\n");
            w.close();
            w = null;
            tmp.renameTo(new File(c.getFilesDir(), CACHE));
        } catch (Exception ignored) {
        } finally {
            Apps.close(w);
        }
    }

    static void forget(File f) {
        List<FileItem> nb = new ArrayList<>(books), na = new ArrayList<>(apks);
        String p = f.getPath();
        for (int i = nb.size() - 1; i >= 0; i--) if (nb.get(i).file.getPath().startsWith(p)) nb.remove(i);
        for (int i = na.size() - 1; i >= 0; i--) if (na.get(i).file.getPath().startsWith(p)) na.remove(i);
        books = nb;
        apks = na;
        notifyListeners();
    }

    // ------------------------------------------------------------------- open

    static String mime(String name, String ext) {
        switch (ext) {
            case "epub": return "application/epub+zip";
            case "mobi": case "prc": return "application/x-mobipocket-ebook";
            case "azw": case "azw3": return "application/vnd.amazon.ebook";
            case "fb2": return "application/x-fictionbook+xml";
            case "djvu": case "djv": return "image/vnd.djvu";
            case "cbz": return "application/x-cbz";
            case "cbr": return "application/x-cbr";
            case "chm": return "application/vnd.ms-htmlhelp";
            case "apk": return "application/vnd.android.package-archive";
            case "txt": return "text/plain";
            case "zip":
                if (name.toLowerCase(Locale.US).endsWith(".fb2.zip")) return "application/x-zip-compressed-fb2";
                break;
        }
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m != null ? m : "*/*";
    }

    static void open(Activity a, File f) {
        String ext = Text.ext(f.getName());
        if (Prefs.b(Prefs.BUILTIN_READER, true) && Book.supports(f.getName(), ext)) {
            Prefs.markOpened(f.getPath());
            Intent r = new Intent(a, ReaderActivity.class);
            r.setData(Uri.fromFile(f));
            r.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(a, r)) return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(f), mime(f.getName(), ext));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!"apk".equals(ext)) Prefs.markOpened(f.getPath());
        if (start(a, i)) return;
        i.setDataAndType(Uri.fromFile(f), "*/*");
        if (start(a, Intent.createChooser(i, f.getName()))) return;
        Toast.makeText(a, R.string.no_app_to_open, Toast.LENGTH_SHORT).show();
    }

    /** Starts an activity without the window animation (animations ghost on e-ink). */
    static boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i, ActivityOptions.makeCustomAnimation(a, 0, 0).toBundle());
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }
}
