package com.eink.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.Date;

/**
 * BOOX-like status bar: time on the left, Wi-Fi + battery on the right, thin
 * rule underneath. Only redraws when something visible actually changes
 * (once a minute for the clock), which keeps e-ink refreshes to a minimum.
 */
final class StatusBarView extends View {
    static final float HEIGHT = 50f;

    private final TextPaint timePaint = Ui.text(14f, false);
    private final TextPaint pctPaint = Ui.text(13f, false);
    private final Paint rule = Ui.line(1.4f);
    private final Paint battStroke = Ui.line(1.6f);
    private final Paint battFill = Ui.fill(Ui.BLACK);
    private final RectF r = new RectF();

    private java.text.DateFormat timeFormat;
    private String time = "";
    private int battery = -1;
    private boolean charging;
    private int wifiLevel = -1; // -1 = off, 0..3 when on
    private boolean running;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                onBattery(intent);
            } else if (Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
                timeFormat = null;
                updateTime();
            } else if (Intent.ACTION_TIME_TICK.equals(a)) {
                updateTime();
            } else {
                updateWifi();
            }
        }
    };

    StatusBarView(Context c) {
        super(c);
        setBackgroundColor(Ui.WHITE);
    }

    void start() {
        if (running) return;
        running = true;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        Intent sticky = getContext().registerReceiver(receiver, f);
        if (sticky != null && Intent.ACTION_BATTERY_CHANGED.equals(sticky.getAction())) onBattery(sticky);
        timeFormat = null;
        updateTime();
        updateWifi();
    }

    void stop() {
        if (!running) return;
        running = false;
        try {
            getContext().unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void updateTime() {
        if (timeFormat == null) timeFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        String t = timeFormat.format(new Date());
        if (!t.equals(time)) {
            time = t;
            invalidate();
        }
    }

    private void onBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? level * 100 / scale : level;
        int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        boolean ch = st == BatteryManager.BATTERY_STATUS_CHARGING;
        if (pct != battery || ch != charging) {
            battery = pct;
            charging = ch;
            invalidate();
        }
    }

    private void updateWifi() {
        int lvl = -1;
        try {
            WifiManager wm = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1
                        && info.getSupplicantState() == android.net.wifi.SupplicantState.COMPLETED) {
                    lvl = WifiManager.calculateSignalLevel(info.getRssi(), 4);
                } else {
                    lvl = 0;
                }
            }
        } catch (Exception ignored) {
        }
        if (lvl != wifiLevel) {
            wifiLevel = lvl;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.ipx(HEIGHT));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cy = (h - Ui.px(2)) / 2f;
        c.drawText(time, Ui.px(16), Ui.baseline(timePaint, cy), timePaint);

        float x = w - Ui.px(15);
        if (battery >= 0) {
            String pct = battery + "%";
            float tw = pctPaint.measureText(pct);
            c.drawText(pct, x - tw, Ui.baseline(pctPaint, cy), pctPaint);
            x -= tw + Ui.px(6);
            // battery body
            float bw = Ui.px(27), bh = Ui.px(14);
            float nib = Ui.px(2.6f);
            r.set(x - bw - nib, cy - bh / 2f, x - nib, cy + bh / 2f);
            c.drawRoundRect(r, Ui.px(2.5f), Ui.px(2.5f), battStroke);
            c.drawRect(x - nib, cy - Ui.px(3), x, cy + Ui.px(3), battFill);
            float in = Ui.px(3f);
            float fillW = (r.width() - 2 * in) * Math.min(100, battery) / 100f;
            if (fillW > 0) c.drawRect(r.left + in, r.top + in, r.left + in + fillW, r.bottom - in, battFill);
            if (charging) {
                Icons.draw(c, Icons.BOLT, r.centerX(), cy, bh * 1.25f, Ui.WHITE);
            }
            x = r.left - Ui.px(14);
        }
        if (wifiLevel >= 0) {
            float size = Ui.px(25);
            Icons.draw(c, Icons.NETWORK, x - size / 2f, cy - Ui.px(1), size, wifiLevel > 0 ? Ui.INK : Ui.SOFT, 2.1f);
        }
        float ry = h - rule.getStrokeWidth() / 2f;
        c.drawLine(0, ry, w, ry, rule);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            expandNotifications();
        }
        return true;
    }

    @android.annotation.SuppressLint("WrongConstant") // hidden "statusbar" service, present on 4.4
    private void expandNotifications() {
        try {
            Object sb = getContext().getSystemService("statusbar");
            Class<?> cls = Class.forName("android.app.StatusBarManager");
            cls.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable t) {
            try {
                Object sb = getContext().getSystemService("statusbar");
                Class.forName("android.app.StatusBarManager").getMethod("expand").invoke(sb);
            } catch (Throwable ignored) {
            }
        }
    }
}
