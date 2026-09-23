package com.eink.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/** Settings screen (reference screenshot #3) + the launcher's own settings. */
final class SettingsPage extends Page implements PagedGridView.Adapter, PagedGridView.PageListener,
        FooterView.Listener {

    private static final class Entry {
        final int label, icon;
        final String action;
        final int internal;

        Entry(int label, int icon, String action, int internal) {
            this.label = label;
            this.icon = icon;
            this.action = action;
            this.internal = internal;
        }
    }

    private static final int IN_LAUNCHER = 1, IN_REFRESH = 2;

    private final List<Entry> entries = new ArrayList<>();
    private FrameLayout frame;
    private View mainView;
    private LauncherSettings launcher;
    private PagedGridView grid;
    private FooterView footer;
    private DeviceCard card;
    private final TextPaint label = Ui.text(14.5f, false);

    SettingsPage(LauncherActivity a) {
        super(a);
        entries.add(new Entry(R.string.set_language, Icons.LANGUAGE, Settings.ACTION_LOCALE_SETTINGS, 0));
        entries.add(new Entry(R.string.set_date, Icons.DATE, Settings.ACTION_DATE_SETTINGS, 0));
        entries.add(new Entry(R.string.set_power, Icons.POWER, Intent.ACTION_POWER_USAGE_SUMMARY, 0));
        entries.add(new Entry(R.string.set_networks, Icons.NETWORK, Settings.ACTION_WIFI_SETTINGS, 0));
        entries.add(new Entry(R.string.set_bluetooth, Icons.BLUETOOTH, Settings.ACTION_BLUETOOTH_SETTINGS, 0));
        entries.add(new Entry(R.string.set_password, Icons.LOCK, Settings.ACTION_SECURITY_SETTINGS, 0));
        entries.add(new Entry(R.string.set_accounts, Icons.ACCOUNT, Settings.ACTION_SYNC_SETTINGS, 0));
        entries.add(new Entry(R.string.set_display, Icons.DISPLAY, Settings.ACTION_DISPLAY_SETTINGS, 0));
        entries.add(new Entry(R.string.set_sound, Icons.SOUND, Settings.ACTION_SOUND_SETTINGS, 0));
        entries.add(new Entry(R.string.set_storage, Icons.SDCARD, Settings.ACTION_INTERNAL_STORAGE_SETTINGS, 0));
        entries.add(new Entry(R.string.set_apps, Icons.GRID, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, 0));
        entries.add(new Entry(R.string.set_accessibility, Icons.ACCESSIBILITY, Settings.ACTION_ACCESSIBILITY_SETTINGS, 0));
        entries.add(new Entry(R.string.set_launcher, Icons.SLIDERS, null, IN_LAUNCHER));
        entries.add(new Entry(R.string.set_refresh, Icons.ROTATE, null, IN_REFRESH));
        entries.add(new Entry(R.string.set_about, Icons.INFO, Settings.ACTION_DEVICE_INFO_SETTINGS, 0));
        entries.add(new Entry(R.string.set_all, Icons.GEAR, Settings.ACTION_SETTINGS, 0));
    }

    @Override
    View create() {
        frame = new FrameLayout(act);
        frame.setBackgroundColor(Ui.WHITE);

        LinearLayout main = new LinearLayout(act);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(Ui.WHITE);
        Header header = new Header(act);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openLauncherSettings();
            }
        });
        main.addView(header);
        card = new DeviceCard(act);
        main.addView(card);
        grid = new PagedGridView(act);
        grid.setLayout(4, 112f, false);
        grid.setPageListener(this);
        main.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new FooterView(act, FooterView.DOTS);
        footer.setListener(this);
        main.addView(footer);
        grid.setAdapter(this);
        mainView = main;
        frame.addView(main);
        return frame;
    }

    void openLauncherSettings() {
        view();
        if (launcher == null) {
            launcher = new LauncherSettings(act, this);
            frame.addView(launcher.view());
        }
        launcher.refresh();
        launcher.view().setVisibility(View.VISIBLE);
        mainView.setVisibility(View.GONE);
    }

    void closeLauncherSettings() {
        if (launcher == null) return;
        launcher.view().setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
    }

    private boolean inLauncherSettings() {
        return launcher != null && launcher.view().getVisibility() == View.VISIBLE;
    }

    @Override
    void onShow() {
        if (card != null) card.update();
    }

    @Override
    void onResume() {
        if (card != null) card.update();
    }

    @Override
    boolean onBack() {
        if (inLauncherSettings()) {
            closeLauncherSettings();
            return true;
        }
        return false;
    }

    @Override
    void onReselect() {
        closeLauncherSettings();
        if (grid != null) grid.setPage(0, false);
    }

    @Override
    boolean pageNext() {
        if (inLauncherSettings()) return launcher.pageNext();
        return grid != null && grid.nextPage();
    }

    @Override
    boolean pagePrev() {
        if (inLauncherSettings()) return launcher.pagePrev();
        return grid != null && grid.prevPage();
    }

    // -------------------------------------------------------------- grid

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        Entry e = entries.get(pos);
        float cx = (l + r) / 2f, h = b - t;
        float iy = t + h * 0.36f;
        Icons.draw(c, e.icon, cx, iy, Ui.px(pressed ? 38 : 34), Ui.INK, 1.6f);
        String[] lines = Ui.wrap2(str(e.label), label, r - l - Ui.px(8));
        float ly = iy + Ui.px(36);
        c.drawText(lines[0], cx - label.measureText(lines[0]) / 2f, Ui.baseline(label, ly), label);
        if (lines[1] != null) {
            c.drawText(lines[1], cx - label.measureText(lines[1]) / 2f, Ui.baseline(label, ly + Ui.px(19)), label);
        }
    }

    @Override
    public void onCellClick(int pos) {
        Entry e = entries.get(pos);
        switch (e.internal) {
            case IN_LAUNCHER:
                openLauncherSettings();
                return;
            case IN_REFRESH:
                act.fullRefresh();
                return;
        }
        Intent i = new Intent(e.action);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!Library.start(act, i)) {
            Intent all = new Intent(Settings.ACTION_SETTINGS);
            all.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Library.start(act, all);
        }
    }

    /** Lets the user pick the default launcher (Home settings on 4.4, chooser otherwise). */
    void chooseHome() {
        Intent i = new Intent("android.settings.HOME_SETTINGS");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Library.start(act, i)) return;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        Library.start(act, Intent.createChooser(home, str(R.string.set_default_home)));
    }

    @Override
    public boolean onCellLongClick(int pos) {
        return false;
    }

    @Override
    public void onPageShown(int first, int last) {}

    @Override
    public void onPageChanged(PagedGridView v, int page, int count, boolean byUser) {
        footer.setPage(page, count);
        if (byUser) act.onPageTurn();
    }

    @Override
    public void onFooterPrev() {
        grid.prevPage();
    }

    @Override
    public void onFooterNext() {
        grid.nextPage();
    }

    @Override
    public void onFooterToggleView() {}

    // ---------------------------------------------------------- header

    /** "Account" row: avatar, launcher name, device name. */
    private final class Header extends View {
        private final TextPaint title = Ui.text(17f, true);
        private final TextPaint sub = Ui.text(13f, false);

        Header(Context c) {
            super(c);
            sub.setColor(Ui.SOFT);
            setClickable(true);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(84));
        }

        @Override
        protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f;
            Icons.draw(c, Icons.ACCOUNT, Ui.px(44), cy, Ui.px(48), Ui.INK, 1.4f);
            float x = Ui.px(82);
            float avail = getWidth() - x - Ui.px(60);
            c.drawText(Ui.ellipsize(str(R.string.app_name), title, avail), x, Ui.baseline(title, cy - Ui.px(11)), title);
            String s = str(R.string.settings_subtitle);
            c.drawText(Ui.ellipsize(s, sub, avail), x, Ui.baseline(sub, cy + Ui.px(13)), sub);
            Icons.draw(c, Icons.CHEVRON, getWidth() - Ui.px(30), cy, Ui.px(22), Ui.INK, 1.8f);
        }
    }

    // ------------------------------------------------------ device card

    /** Rounded card with device icon, model, Android version, CPU, RAM and storage. */
    private final class DeviceCard extends View {
        private final Paint stroke = Ui.line(1.4f);
        private final TextPaint bold = Ui.text(15f, true);
        private final TextPaint small = Ui.text(12.5f, false);
        private final RectF r = new RectF(), ib = new RectF();
        private final String[] lines = new String[4];
        private String cpu;

        DeviceCard(Context c) {
            super(c);
        }

        void update() {
            ActivityManager am = (ActivityManager) act.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (cpu == null) cpu = readCpu();
            lines[0] = str(R.string.info_model) + ": " + Build.MANUFACTURER + " " + Build.MODEL;
            lines[1] = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")  ·  " + cpu;
            lines[2] = str(R.string.info_ram) + ": " + Text.size(mi.availMem) + " " + str(R.string.info_free)
                    + " / " + Text.size(mi.totalMem);
            try {
                StatFs st = new StatFs(Environment.getExternalStorageDirectory().getPath());
                lines[3] = str(R.string.info_storage) + ": " + Text.size(st.getAvailableBytes()) + " "
                        + str(R.string.info_free) + " / " + Text.size(st.getTotalBytes());
            } catch (Exception e) {
                lines[3] = str(R.string.info_storage) + ": -";
            }
            invalidate();
        }

        private String readCpu() {
            String hw = null;
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/cpuinfo"), 2048);
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.startsWith("Hardware")) hw = l.substring(l.indexOf(':') + 1).trim();
                }
            } catch (Exception ignored) {
            } finally {
                Apps.close(br);
            }
            int cores = Runtime.getRuntime().availableProcessors();
            @SuppressWarnings("deprecation") String abi = Build.CPU_ABI;
            return (hw != null && hw.length() > 0 ? hw + " " : "") + abi + " ×" + cores;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(138));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (lines[0] == null) update();
            r.set(Ui.px(18), Ui.px(6), getWidth() - Ui.px(18), getHeight() - Ui.px(10));
            c.drawRoundRect(r, Ui.px(8), Ui.px(8), stroke);
            float box = Ui.px(70);
            float bx = r.left + Ui.px(14), by = r.centerY() - box / 2f;
            ib.set(bx, by, bx + box, by + box);
            c.drawRoundRect(ib, Ui.px(6), Ui.px(6), stroke);
            Icons.draw(c, Icons.DEVICE, ib.centerX(), ib.centerY(), box * 0.72f, Ui.INK, 1.3f);
            float x = ib.right + Ui.px(16);
            float avail = r.right - x - Ui.px(12);
            float y = r.top + Ui.px(22);
            c.drawText(Ui.ellipsize(Build.MODEL, bold, avail), x, Ui.baseline(bold, y), bold);
            for (String l : lines) {
                y += Ui.px(19.5f);
                if (l != null) c.drawText(Ui.ellipsize(l, small, avail), x, Ui.baseline(small, y), small);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) update();
            return true;
        }
    }
}
