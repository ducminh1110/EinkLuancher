package com.eink.launcher;

import android.content.ComponentName;
import android.graphics.Bitmap;

import java.util.Locale;

/** One launchable activity. */
final class AppItem {
    final String pkg;
    final String cls;
    String label;
    long installed;
    long updated;
    boolean system;
    /** Line-art glyph for well known apps, or -1 to use the real (grayscale) icon. */
    int glyph;

    // Draw caches (UI thread only).
    String labelDraw;
    float labelDrawWidth = -1;
    Bitmap icon;
    int iconSize;
    boolean iconLoading;
    String searchKey;

    AppItem(String pkg, String cls, String label) {
        this.pkg = pkg;
        this.cls = cls;
        this.label = label == null ? pkg : label;
        this.glyph = glyphFor(pkg, this.label);
    }

    String key() {
        return pkg + "/" + cls;
    }

    ComponentName component() {
        return new ComponentName(pkg, cls);
    }

    String searchKey() {
        if (searchKey == null) searchKey = Text.fold(label);
        return searchKey;
    }

    // ------------------------------------------------------------ glyph mapping

    private static final Object[][] RULES = {
        // keyword (matched against package + label, lower case), glyph
        {"vending", Icons.PLAYSTORE}, {"play store", Icons.PLAYSTORE},
        {"browser", Icons.GLOBE}, {"chrome", Icons.GLOBE}, {"firefox", Icons.GLOBE}, {"opera", Icons.GLOBE},
        {"gallery", Icons.IMAGE}, {"photos", Icons.IMAGE}, {"album", Icons.IMAGE},
        {"screensaver", Icons.SCREENSAVER}, {"daydream", Icons.SCREENSAVER}, {"wallpaper", Icons.SCREENSAVER},
        {"soundrecorder", Icons.MIC}, {"recorder", Icons.MIC},
        {"deskclock", Icons.CLOCK}, {"alarm", Icons.CLOCK}, {"clock", Icons.CLOCK},
        {"calendar", Icons.CALENDAR},
        {"dictionary", Icons.DICTIONARY}, {"dict", Icons.DICTIONARY}, {"translat", Icons.LANGUAGE},
        {"music", Icons.MUSIC}, {"audio", Icons.MUSIC}, {"podcast", Icons.MUSIC},
        {"camera", Icons.CAMERA},
        {"calculator", Icons.CALCULATOR}, {"calc", Icons.CALCULATOR},
        {"email", Icons.MAIL}, {"android.gm", Icons.MAIL}, {"mail", Icons.MAIL},
        {"contacts", Icons.PERSON}, {"people", Icons.PERSON},
        {"dialer", Icons.PHONE}, {"android.phone", Icons.PHONE},
        {"mms", Icons.CHAT}, {"messag", Icons.CHAT}, {"sms", Icons.CHAT}, {"telegram", Icons.CHAT},
        {"providers.downloads", Icons.DOWNLOAD}, {"download", Icons.DOWNLOAD},
        {"filemanager", Icons.FOLDER}, {"file manager", Icons.FOLDER}, {"documentsui", Icons.FOLDER},
        {"explorer", Icons.FOLDER}, {"files", Icons.FOLDER},
        {"quicksearchbox", Icons.SEARCH}, {"search", Icons.SEARCH},
        {"maps", Icons.MAP},
        {"video", Icons.VIDEO}, {"movie", Icons.VIDEO}, {"youtube", Icons.VIDEO}, {"player", Icons.VIDEO},
        {"memo", Icons.NOTE}, {"note", Icons.NOTE}, {"keep", Icons.NOTE},
        {"koreader", Icons.BOOK}, {"reader", Icons.BOOK}, {"kindle", Icons.BOOK}, {"book", Icons.BOOK},
        {"moon+", Icons.BOOK}, {"fbreader", Icons.BOOK}, {"librera", Icons.BOOK}, {"pdf", Icons.BOOK},
        {"rss", Icons.RSS}, {"feed", Icons.RSS}, {"pushread", Icons.RSS}, {"news", Icons.RSS},
        {"assistant", Icons.AI}, {"chatgpt", Icons.AI}, {"openai", Icons.AI}, {"gemini", Icons.AI},
        {"claude", Icons.AI},
        {"drop", Icons.TRANSFER}, {"transfer", Icons.TRANSFER}, {"share", Icons.TRANSFER},
        {"bluetooth", Icons.BLUETOOTH},
        {"android.settings", Icons.GEAR}, {"settings", Icons.GEAR},
        {"store", Icons.BAG}, {"market", Icons.BAG}, {"fdroid", Icons.BAG},
        {"weather", Icons.DISPLAY},
    };

    static int glyphFor(String pkg, String label) {
        String hay = (pkg + " " + label).toLowerCase(Locale.US);
        for (Object[] r : RULES) {
            if (hay.contains((String) r[0])) return (Integer) r[1];
        }
        return -1;
    }
}
