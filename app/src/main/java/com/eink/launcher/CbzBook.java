package com.eink.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Comic book archive: one picture per page, decoded to the screen size. */
final class CbzBook extends Book {
    private ZipFile zip;
    private final List<String> names = new ArrayList<>();

    CbzBook(File f) {
        super(f);
    }

    @Override
    void open() throws Exception {
        zip = new ZipFile(file);
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory() && Covers.isImage(e.getName().toLowerCase(Locale.US))) names.add(e.getName());
        }
        Collections.sort(names);
        for (String n : names) add(null, zip.getEntry(n).getSize());
        if (names.isEmpty()) throw new IllegalStateException("no pictures");
    }

    @Override
    boolean isPictureBook() {
        return true;
    }

    @Override
    Bitmap picture(int chapter, int w, int h) throws Exception {
        byte[] data = Covers.readAll(zip.getInputStream(zip.getEntry(names.get(chapter))));
        if (data == null) return null;
        return HtmlText.decode(data, w, h, new int[]{Integer.MAX_VALUE});
    }

    @Override
    CharSequence text(int chapter, Resources res, int maxW, int maxH) {
        return "";
    }

    @Override
    void close() {
        try {
            if (zip != null) zip.close();
        } catch (Exception ignored) {
        }
    }
}
