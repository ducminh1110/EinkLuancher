package com.eink.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** The launcher's own options, shown as a paged list inside the Settings tab. */
final class LauncherSettings implements PagedGridView.Adapter, PagedGridView.PageListener,
        ToolbarView.Listener, FooterView.Listener {

    private interface Row {
        String label();

        String value();

        void click();
    }

    private final LauncherActivity act;
    private final SettingsPage owner;
    private final LinearLayout root;
    private final PagedGridView list;
    private final FooterView footer;
    private final List<Row> rows = new ArrayList<>();
    private final TextPaint labelPaint = Ui.text(16f, true);
    private final TextPaint valuePaint = Ui.text(14f, false);
    private final Paint sep = Ui.line(1f);
    private final Paint thick = Ui.line(3f);

    LauncherSettings(LauncherActivity a, SettingsPage owner) {
        this.act = a;
        this.owner = owner;
        valuePaint.setColor(Ui.SOFT);
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);
        ToolbarView tb = new ToolbarView(a);
        tb.set(a.getString(R.string.launcher_settings), true);
        tb.setListener(this);
        root.addView(tb);
        list = new PagedGridView(a);
        list.setLayout(1, 64f, false);
        list.setPageListener(this);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new FooterView(a, FooterView.DOTS);
        footer.setListener(this);
        root.addView(footer);
        buildRows();
        list.setAdapter(this);
    }

    View view() {
        return root;
    }

    void refresh() {
        list.invalidate();
    }

    boolean pageNext() {
        return list.nextPage();
    }

    boolean pagePrev() {
        return list.prevPage();
    }

    private String s(int id) {
        return act.getString(id);
    }

    private String onOff(boolean b) {
        return s(b ? R.string.on : R.string.off);
    }

    private void buildRows() {
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_language);
            }

            public String value() {
                String l = Prefs.lang();
                return "vi".equals(l) ? "Tiếng Việt" : "sys".equals(l) ? s(R.string.system_default) : "English";
            }

            public void click() {
                final String cur = Prefs.lang();
                EinkMenu m = new EinkMenu(act);
                String[][] opts = {{"en", "English"}, {"vi", "Tiếng Việt"}, {"sys", s(R.string.system_default)}};
                for (final String[] o : opts) {
                    m.add(o[1], o[0].equals(cur), new EinkMenu.Action() {
                        public void run() {
                            if (o[0].equals(cur)) return;
                            Prefs.put(Prefs.LANG, o[0]);
                            act.restart();
                        }
                    });
                }
                m.showCentered(list, s(R.string.opt_language));
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_statusbar);
            }

            public String value() {
                return onOff(Prefs.b(Prefs.FULLSCREEN, true));
            }

            public void click() {
                Prefs.put(Prefs.FULLSCREEN, !Prefs.b(Prefs.FULLSCREEN, true));
                act.applyFullscreen();
                list.invalidate();
            }
        });
        rows.add(new Row() {
            final int[] tabs = {R.string.tab_library, R.string.tab_store, R.string.tab_storage, R.string.tab_apps,
                    R.string.tab_settings};

            public String label() {
                return s(R.string.opt_start_tab);
            }

            public String value() {
                return s(tabs[Math.max(0, Math.min(4, Prefs.startTab()))]);
            }

            public void click() {
                EinkMenu m = new EinkMenu(act);
                for (int i = 0; i < tabs.length; i++) {
                    final int t = i;
                    m.add(s(tabs[i]), i == Prefs.startTab(), new EinkMenu.Action() {
                        public void run() {
                            Prefs.put(Prefs.START_TAB, t);
                            list.invalidate();
                        }
                    });
                }
                m.showCentered(list, s(R.string.opt_start_tab));
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_app_columns);
            }

            public String value() {
                int c = Prefs.i(Prefs.APP_COLS, 0);
                return c == 0 ? s(R.string.auto) : String.valueOf(c);
            }

            public void click() {
                EinkMenu m = new EinkMenu(act);
                int cur = Prefs.i(Prefs.APP_COLS, 0);
                for (final int o : new int[]{0, 3, 4, 5, 6}) {
                    m.add(o == 0 ? s(R.string.auto) : String.valueOf(o), o == cur, new EinkMenu.Action() {
                        public void run() {
                            Prefs.put(Prefs.APP_COLS, o);
                            act.onAppLayoutChanged();
                            list.invalidate();
                        }
                    });
                }
                m.showCentered(list, s(R.string.opt_app_columns));
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_icon_style);
            }

            public String value() {
                return s(Prefs.i(Prefs.ICON_STYLE, 0) == 0 ? R.string.icon_line : R.string.icon_original);
            }

            public void click() {
                Prefs.put(Prefs.ICON_STYLE, Prefs.i(Prefs.ICON_STYLE, 0) == 0 ? 1 : 0);
                act.onAppLayoutChanged();
                list.invalidate();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_badge);
            }

            public String value() {
                return onOff(Prefs.b(Prefs.BADGE, true));
            }

            public void click() {
                Prefs.put(Prefs.BADGE, !Prefs.b(Prefs.BADGE, true));
                act.onAppLayoutChanged();
                list.invalidate();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_builtin_reader);
            }

            public String value() {
                return onOff(Prefs.b(Prefs.BUILTIN_READER, true));
            }

            public void click() {
                Prefs.put(Prefs.BUILTIN_READER, !Prefs.b(Prefs.BUILTIN_READER, true));
                list.invalidate();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_refresh);
            }

            public String value() {
                int n = Prefs.refreshEvery();
                return n == 0 ? s(R.string.off) : act.getString(R.string.every_n_pages, n);
            }

            public void click() {
                EinkMenu m = new EinkMenu(act);
                int cur = Prefs.refreshEvery();
                for (final int o : new int[]{0, 1, 3, 5, 10}) {
                    m.add(o == 0 ? s(R.string.off) : act.getString(R.string.every_n_pages, o), o == cur,
                            new EinkMenu.Action() {
                                public void run() {
                                    Prefs.put(Prefs.REFRESH_EVERY, o);
                                    list.invalidate();
                                }
                            });
                }
                m.showCentered(list, s(R.string.opt_refresh));
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_volume_keys);
            }

            public String value() {
                return onOff(Prefs.b(Prefs.VOLUME_KEYS, true));
            }

            public void click() {
                Prefs.put(Prefs.VOLUME_KEYS, !Prefs.b(Prefs.VOLUME_KEYS, true));
                list.invalidate();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.set_default_home);
            }

            public String value() {
                return "";
            }

            public void click() {
                owner.chooseHome();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.rescan);
            }

            public String value() {
                return Library.isScanning() ? s(R.string.scanning_short)
                        : act.getString(R.string.total_books, Library.books().size());
            }

            public void click() {
                Library.scan(act, 0);
                act.toast(s(R.string.scanning));
                list.invalidate();
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_clear_cache);
            }

            public String value() {
                return "";
            }

            public void click() {
                ImageCache.clearDisk();
                act.onAppLayoutChanged();
                act.toast(s(R.string.done));
            }
        });
        rows.add(new Row() {
            public String label() {
                return s(R.string.opt_about);
            }

            public String value() {
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                String ver = "1.0";
                try {
                    ver = act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
                } catch (Exception ignored) {
                }
                return "v" + ver + "  ·  " + act.getString(R.string.heap_used, Text.size(used));
            }

            public void click() {
                list.invalidate();
            }
        });
    }

    // ------------------------------------------------------------ adapter

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        Row row = rows.get(pos);
        float cy = (t + b) / 2f;
        float x = l + Ui.px(20);
        String v = row.value();
        float vw = v.length() > 0 ? valuePaint.measureText(v) : 0;
        float right = r - Ui.px(44);
        String lab = Ui.ellipsize(row.label(), labelPaint, right - vw - Ui.px(16) - x);
        c.drawText(lab, x, Ui.baseline(labelPaint, cy), labelPaint);
        if (vw > 0) c.drawText(v, right - vw, Ui.baseline(valuePaint, cy), valuePaint);
        Icons.draw(c, Icons.CHEVRON, r - Ui.px(24), cy, Ui.px(18), Ui.INK, 1.8f);
        if (pressed) c.drawRect(l + Ui.px(6), t + Ui.px(3), r - Ui.px(6), b - Ui.px(3), thick);
        c.drawLine(l + Ui.px(18), b - 0.5f, r - Ui.px(18), b - 0.5f, sep);
    }

    @Override
    public void onCellClick(int pos) {
        rows.get(pos).click();
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
    public void onToolbarAction(int iconId) {}

    @Override
    public void onToolbarBack() {
        owner.closeLauncherSettings();
    }

    @Override
    public void onFooterPrev() {
        list.prevPage();
    }

    @Override
    public void onFooterNext() {
        list.nextPage();
    }

    @Override
    public void onFooterToggleView() {}
}
