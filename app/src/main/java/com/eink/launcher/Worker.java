package com.eink.launcher;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

/**
 * Low-priority background threads shared by the whole launcher: one for
 * disk/package work (scans, listings), one for images (icons, covers) and one
 * for the built-in reader (created only when a book is opened).
 * No thread pools, no executors - minimal memory and CPU contention on a
 * single-core-class Cortex-A9.
 */
final class Worker {
    private static Handler io, img, book;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Worker() {}

    private static Handler start(String name) {
        HandlerThread t = new HandlerThread(name, Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        return new Handler(t.getLooper());
    }

    static synchronized Handler io() {
        if (io == null) io = start("eink-io");
        return io;
    }

    static synchronized Handler img() {
        if (img == null) img = start("eink-img");
        return img;
    }

    /** Book loading gets its own thread so a library scan never delays page turns. */
    static synchronized Handler book() {
        if (book == null) book = start("eink-book");
        return book;
    }

    static void book(Runnable r) {
        book().post(r);
    }

    static void io(Runnable r) {
        io().post(r);
    }

    static void img(Runnable r) {
        img().post(r);
    }

    static void ui(Runnable r) {
        MAIN.post(r);
    }

    static void uiDelayed(Runnable r, long ms) {
        MAIN.postDelayed(r, ms);
    }

    static void cancelUi(Runnable r) {
        MAIN.removeCallbacks(r);
    }
}
