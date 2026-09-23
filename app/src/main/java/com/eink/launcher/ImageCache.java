package com.eink.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory + disk cache for app icons and book covers.
 *
 * Bitmaps are stored as RGB_565 on a white background (half the memory of
 * ARGB_8888) and the memory budget is derived from the per-app heap limit,
 * so on a 256 MB device the whole launcher stays around a few MB of pixels.
 * Only the icons/covers of the visible page are ever requested.
 */
final class ImageCache {
    private static LruCache<String, Bitmap> icons, covers;
    private static final HashSet<String> inflight = new HashSet<>();
    private static File iconDir, coverDir;
    @android.annotation.SuppressLint("StaticFieldLeak") // application context only
    private static Context app;
    /** Keys needed by the page currently on screen; queued work for anything else is skipped. */
    private static volatile Set<String> wantedIcons = newSet(), wantedCovers = newSet();

    private static final ColorMatrixColorFilter GRAY;

    static {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        float c = 1.25f, t = (1 - c) * 128f;
        cm.postConcat(new ColorMatrix(new float[]{
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0}));
        GRAY = new ColorMatrixColorFilter(cm);
    }

    private ImageCache() {}

    private static Set<String> newSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /** A new page of icons is shown: forget what the previous page asked for. */
    static void newIconPage() {
        wantedIcons = newSet();
    }

    static void newCoverPage() {
        wantedCovers = newSet();
    }

    static void init(Context c) {
        if (icons != null) return;
        app = c.getApplicationContext();
        int mc = ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        int budget = mc * 1024 * 1024 / 8;
        icons = new Lru(Math.min(budget / 3, 2 * 1024 * 1024));
        covers = new Lru(Math.min(budget * 2 / 3, 5 * 1024 * 1024));
        iconDir = new File(c.getCacheDir(), "icons");
        coverDir = new File(c.getCacheDir(), "covers");
        iconDir.mkdirs();
        coverDir.mkdirs();
    }

    private static final class Lru extends LruCache<String, Bitmap> {
        Lru(int bytes) {
            super(Math.max(bytes, 256 * 1024));
        }

        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    }

    static void trim(boolean all) {
        if (covers != null) covers.evictAll();
        if (all && icons != null) icons.evictAll();
    }

    static void clearDisk() {
        trim(true);
        noCover.clear();
        deleteChildren(iconDir);
        deleteChildren(coverDir);
    }

    private static void deleteChildren(File dir) {
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static String hash(String s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
        return Long.toHexString(h) + Integer.toHexString(s.hashCode());
    }

    // ------------------------------------------------------------------ icons

    static Bitmap icon(AppItem a, int size) {
        return icons.get(a.key() + "@" + size);
    }

    static void requestIcon(final AppItem a, final int size, final Runnable done) {
        final String key = a.key() + "@" + size;
        if (icons.get(key) != null) return;
        wantedIcons.add(key);
        if (inflight.contains(key)) return;
        inflight.add(key);
        Worker.img(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = wantedIcons.contains(key) ? loadIcon(a, size) : null;
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        inflight.remove(key);
                        if (b != null) {
                            icons.put(key, b);
                            if (done != null) done.run();
                        }
                    }
                });
            }
        });
    }

    private static Bitmap loadIcon(AppItem a, int size) {
        File f = new File(iconDir, hash(a.key() + a.updated + "@" + size) + ".png");
        if (f.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) return b;
        }
        Drawable d;
        try {
            d = app.getPackageManager().getActivityIcon(a.component());
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (Throwable t) {
            return null;
        }
        if (d == null) return null;
        try {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(Ui.WHITE);
            d = d.mutate();
            d.setBounds(0, 0, size, size);
            d.setColorFilter(GRAY);
            d.draw(c);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(f);
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception ignored) {
            } finally {
                Apps.close(out);
            }
            return b;
        } catch (OutOfMemoryError oom) {
            trim(true);
            return null;
        }
    }

    // ----------------------------------------------------------------- covers

    private static final HashSet<String> noCover = new HashSet<>();

    static String coverKey(FileItem f, int w, int h) {
        return f.file.getPath() + "|" + f.size + "|" + f.modified + "@" + w + "x" + h;
    }

    /** Returns the cached cover, or null when not loaded / not available. */
    static Bitmap cover(FileItem f, int w, int h) {
        return covers.get(coverKey(f, w, h));
    }

    static boolean hasNoCover(FileItem f, int w, int h) {
        return noCover.contains(coverKey(f, w, h));
    }

    static void requestCover(final FileItem f, final int w, final int h, final Runnable done) {
        final String key = coverKey(f, w, h);
        if (covers.get(key) != null || noCover.contains(key)) return;
        if (!Covers.supports(f.ext)) {
            noCover.add(key);
            return;
        }
        wantedCovers.add(key);
        if (inflight.contains(key)) return;
        inflight.add(key);
        Worker.img(new Runnable() {
            @Override
            public void run() {
                if (!wantedCovers.contains(key)) {
                    Worker.ui(new Runnable() {
                        @Override
                        public void run() {
                            inflight.remove(key);
                        }
                    });
                    return;
                }
                final Bitmap b = loadCover(f, w, h, key);
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        inflight.remove(key);
                        if (b != null) covers.put(key, b);
                        else noCover.add(key);
                        if (done != null) done.run();
                    }
                });
            }
        });
    }

    private static Bitmap loadCover(FileItem f, int w, int h, String key) {
        String name = hash(key);
        File jpg = new File(coverDir, name + ".jpg");
        File none = new File(coverDir, name + ".none");
        if (none.exists()) return null;
        if (jpg.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(jpg.getPath(), o);
            if (b != null) return b;
        }
        Bitmap b = null;
        try {
            b = Covers.extract(f.file, f.ext, w, h);
        } catch (OutOfMemoryError oom) {
            trim(false);
        } catch (Throwable ignored) {
        }
        FileOutputStream out = null;
        try {
            if (b != null) {
                out = new FileOutputStream(jpg);
                b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } else {
                none.createNewFile();
            }
        } catch (Exception ignored) {
        } finally {
            Apps.close(out);
        }
        return b;
    }
}
