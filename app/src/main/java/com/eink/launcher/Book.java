package com.eink.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A book split into chapters. Only one chapter is ever loaded at a time,
 * which keeps memory flat whatever the size of the book.
 */
abstract class Book {
    final File file;
    String title;
    final List<String> chapters = new ArrayList<>();
    /** Relative weight (bytes) of each chapter, for the overall progress. */
    final List<Long> sizes = new ArrayList<>();

    Book(File f) {
        file = f;
        title = Text.stripExt(f.getName());
    }

    /** Reads the book structure (chapter list). Runs on a background thread. */
    abstract void open() throws Exception;

    /** Styled text of a chapter. maxW/maxH bound embedded pictures. */
    abstract CharSequence text(int chapter, Resources res, int maxW, int maxH) throws Exception;

    /** Picture books (CBZ) draw one image per chapter instead of text. */
    boolean isPictureBook() {
        return false;
    }

    Bitmap picture(int chapter, int w, int h) throws Exception {
        return null;
    }

    void close() {}

    int count() {
        return chapters.size();
    }

    long totalSize() {
        long t = 0;
        for (long s : sizes) t += s;
        return Math.max(1, t);
    }

    long sizeBefore(int chapter) {
        long t = 0;
        for (int i = 0; i < chapter && i < sizes.size(); i++) t += sizes.get(i);
        return t;
    }

    void add(String title, long size) {
        chapters.add(title);
        sizes.add(Math.max(1, size));
    }

    // ------------------------------------------------------------ factory

    static boolean supports(String name, String ext) {
        switch (ext) {
            case "epub": case "fb2": case "txt": case "mobi": case "azw": case "azw3": case "prc":
            case "htm": case "html": case "cbz":
                return true;
            case "zip":
                return name.toLowerCase(Locale.US).endsWith(".fb2.zip");
            default:
                return false;
        }
    }

    static Book create(File f) {
        String ext = Text.ext(f.getName());
        switch (ext) {
            case "epub": return new EpubBook(f);
            case "fb2": case "zip": return new Fb2Book(f);
            case "mobi": case "azw": case "azw3": case "prc": return new MobiBook(f);
            case "cbz": return new CbzBook(f);
            case "htm": case "html": return new TxtBook(f, true);
            default: return new TxtBook(f, false);
        }
    }
}
