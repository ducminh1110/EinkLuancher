package com.eink.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/** Applies the language chosen in the launcher settings (English by default, like the BOOX UI). */
final class Locales {
    private Locales() {}

    static void apply(Context c) {
        String l = Prefs.lang();
        Locale loc = "sys".equals(l) ? Resources.getSystem().getConfiguration().locale : new Locale(l);
        Resources res = c.getResources();
        Configuration cfg = res.getConfiguration();
        if (loc.equals(cfg.locale)) return;
        Locale.setDefault(loc);
        Configuration n = new Configuration(cfg);
        n.locale = loc;
        res.updateConfiguration(n, res.getDisplayMetrics());
    }
}
