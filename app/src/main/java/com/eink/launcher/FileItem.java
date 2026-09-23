package com.eink.launcher;

import java.io.File;

/** A file or folder shown in Library / Storage / Store. */
final class FileItem {
    final File file;
    final String name;
    final boolean dir;
    final String ext;
    long size;
    long modified;
    /** Last time the book was opened from the launcher (for "Recent" sort). */
    long opened;
    /** Reading progress 0..1 saved by the built-in reader. */
    float progress;
    /** Optional display name (e.g. "SD card" for a storage root). */
    String label;

    // Draw caches (UI thread only).
    String[] lines;
    float linesWidth = -1;
    String searchKey;

    FileItem(File f) {
        this(f, f.isDirectory());
    }

    FileItem(File f, boolean dir) {
        file = f;
        name = f.getName();
        this.dir = dir;
        ext = dir ? "" : Text.ext(name);
        if (!dir) {
            size = f.length();
        }
        modified = f.lastModified();
    }

    FileItem(String path, long size, long modified) {
        file = new File(path);
        name = file.getName();
        dir = false;
        ext = Text.ext(name);
        this.size = size;
        this.modified = modified;
    }

    String title() {
        return label != null ? label : name;
    }

    String searchKey() {
        if (searchKey == null) searchKey = Text.fold(title());
        return searchKey;
    }

    /** Short format badge text: "EPUB", "PDF"... */
    String badge() {
        if ("zip".equals(ext) && name.toLowerCase(java.util.Locale.US).endsWith(".fb2.zip")) return "FB2";
        return ext.toUpperCase(java.util.Locale.US);
    }
}
