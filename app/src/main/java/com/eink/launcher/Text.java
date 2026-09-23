package com.eink.launcher;

import java.text.Normalizer;
import java.util.Locale;

/** Small text helpers. */
final class Text {
    private Text() {}

    /** Lower-case and strip diacritics so "tieng viet" matches "Tiếng Việt". */
    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (c == 'đ' || c == 'Đ') c = 'd';
            b.append(c);
        }
        return b.toString().toLowerCase(Locale.US);
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1048576f);
        return String.format(Locale.US, "%.2f GB", bytes / 1073741824f);
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
