package com.eink.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Built-in e-book reader (EPUB, FB2, MOBI/AZW3, TXT, HTML, CBZ). Runs in its
 * own task so Home returns to the launcher while the book stays open in
 * Recents; the reading position is saved on every page turn.
 */
public final class ReaderActivity extends Activity implements ReaderView.Host {
    private ReaderView view;
    private Book book;
    private File file;
    private int turns;
    private int openGen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Prefs.init(this);
        Locales.apply(this);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new ReaderView(this, this);
        setContentView(view);
        open(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        open(intent);
    }

    private void open(Intent intent) {
        savePosition();
        closeBook();
        view.showMenu(false);
        view.setMessage(getString(R.string.reader_opening));
        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) {
            finish();
            return;
        }
        final Uri u = uri;
        final int gen = ++openGen;
        Worker.book(new Runnable() {
            @Override
            public void run() {
                File f = null;
                Book b = null;
                String err = null;
                try {
                    f = toFile(u);
                    if (f == null || !f.canRead()) throw new IllegalStateException("unreadable");
                    b = Book.create(f);
                    b.open();
                } catch (SecurityException e) {
                    err = getString(R.string.reader_drm);
                } catch (Throwable e) {
                    android.util.Log.w("EinkReader", "cannot open " + u, e);
                    err = getString(R.string.reader_error);
                    if (b != null) b.close();
                    b = null;
                }
                final File ff = f;
                final Book fb = b;
                final String ferr = err;
                Worker.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != openGen || isFinishing()) {
                            if (fb != null) fb.close();
                            return;
                        }
                        file = ff;
                        if (ferr != null) {
                            view.setMessage(ferr + "\n" + getString(R.string.reader_tap_menu));
                            return;
                        }
                        book = fb;
                        int[] pos = Prefs.position(ff.getPath());
                        view.setBook(fb, pos[0], pos[1]);
                    }
                });
            }
        });
    }

    /** file:// is used directly; content:// (e.g. from a downloads app) is copied to the cache once. */
    private File toFile(Uri u) throws Exception {
        if ("file".equals(u.getScheme())) return new File(u.getPath());
        String name = u.getLastPathSegment();
        if (name == null) name = "book";
        String type = getContentResolver().getType(u);
        String ext = Text.ext(name);
        if (!Book.supports(name, ext)) {
            if (type != null && type.contains("epub")) ext = "epub";
            else if (type != null && type.contains("fictionbook")) ext = "fb2";
            else if (type != null && type.contains("mobipocket")) ext = "mobi";
            else if (type != null && type.contains("html")) ext = "html";
            else ext = "txt";
            name = name + "." + ext;
        }
        File out = new File(getCacheDir(), "shared-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
        InputStream in = getContentResolver().openInputStream(u);
        FileOutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[16 * 1024];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                os.write(buf, 0, r);
                total += r;
                if (total > 64L * 1024 * 1024) throw new IllegalStateException("too big");
            }
        } finally {
            Apps.close(in);
            Apps.close(os);
        }
        return out;
    }

    private void savePosition() {
        if (book != null && file != null) {
            Prefs.savePosition(file.getPath(), view.chapter(), view.offset(), view.progress());
        }
    }

    private void closeBook() {
        if (book != null) {
            final Book b = book;
            Worker.book(new Runnable() {
                @Override
                public void run() {
                    b.close();
                }
            });
        }
        book = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBook();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ImageCache.trim(level >= TRIM_MEMORY_MODERATE);
    }

    // ---------------------------------------------------------------- host

    @Override
    public void onPositionChanged(boolean userTurn) {
        savePosition();
        if (userTurn) {
            turns++;
            int n = Prefs.refreshEvery();
            if (n > 0 && turns % n == 0) view.flash();
        }
    }

    @Override
    public void onEndOfBook() {
        Toast.makeText(this, R.string.end_of_book, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMenuAction(int action) {
        switch (action) {
            case ReaderView.ACT_BACK:
                finish();
                break;
            case ReaderView.ACT_TOC:
                if (book != null) showToc();
                break;
            case ReaderView.ACT_GOTO:
                if (book == null) break;
                EinkMenu.input(this, getString(R.string.goto_position) + " (0-100%)",
                        String.valueOf(Math.round(view.progress() * 100)), new EinkMenu.TextCallback() {
                            @Override
                            public void onText(String t) {
                                try {
                                    float p = Float.parseFloat(t.replace("%", "").trim()) / 100f;
                                    view.gotoProgress(Math.max(0, Math.min(1f, p)));
                                    view.showMenu(false);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        });
                break;
            case ReaderView.ACT_OPEN_WITH:
                if (file == null) break;
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.fromFile(file), Library.mime(file.getName(), Text.ext(file.getName())));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Library.start(this, Intent.createChooser(i, file.getName()));
                break;
            case ReaderView.ACT_REFRESH:
                view.showMenu(false);
                view.flash();
                break;
        }
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean vol = Prefs.b(Prefs.VOLUME_KEYS, true);
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_SPACE:
                view.next();
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                view.prev();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (!vol) break;
                view.next();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (!vol) break;
                view.prev();
                return true;
            case KeyEvent.KEYCODE_MENU:
                view.showMenu(!view.isMenuShown());
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                && Prefs.b(Prefs.VOLUME_KEYS, true)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (view.isMenuShown()) view.showMenu(false);
        else finish();
    }

    // ----------------------------------------------------------------- TOC

    /** Table of contents as a paged e-ink list (no scrolling). */
    private void showToc() {
        final Dialog d = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);
        ToolbarView tb = new ToolbarView(this);
        tb.set(getString(R.string.toc), true);
        tb.setListener(new ToolbarView.Listener() {
            @Override
            public void onToolbarAction(int iconId) {}

            @Override
            public void onToolbarBack() {
                d.dismiss();
            }
        });
        root.addView(tb);
        final PagedGridView list = new PagedGridView(this);
        list.setLayout(1, 58f, false);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final FooterView footer = new FooterView(this, FooterView.DOTS);
        root.addView(footer);
        final int current = view.chapter();
        final TextPaint normal = Ui.text(16f, false), bold = Ui.text(16f, true), num = Ui.text(13f, false);
        final Paint sep = Ui.line(1f), mark = Ui.fill(Ui.BLACK), thick = Ui.line(3f);
        list.setAdapter(new PagedGridView.Adapter() {
            @Override
            public int getCount() {
                return book == null ? 0 : book.count();
            }

            @Override
            public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
                float cy = (t + b) / 2f;
                TextPaint p = pos == current ? bold : normal;
                String n = String.valueOf(pos + 1);
                float nw = num.measureText(n);
                if (pos == current) c.drawRect(l, t + Ui.px(10), l + Ui.px(5), b - Ui.px(10), mark);
                c.drawText(Ui.ellipsize(view.chapterTitle(pos), p, r - l - nw - Ui.px(60)), l + Ui.px(20),
                        Ui.baseline(p, cy), p);
                c.drawText(n, r - Ui.px(20) - nw, Ui.baseline(num, cy), num);
                if (pressed) c.drawRect(l + Ui.px(6), t + Ui.px(3), r - Ui.px(6), b - Ui.px(3), thick);
                c.drawLine(l + Ui.px(18), b - 0.5f, r - Ui.px(18), b - 0.5f, sep);
            }

            @Override
            public void onCellClick(int pos) {
                d.dismiss();
                view.showMenu(false);
                view.gotoChapter(pos, 0);
            }

            @Override
            public boolean onCellLongClick(int pos) {
                return false;
            }

            @Override
            public void onPageShown(int first, int last) {}
        });
        list.setPageListener(new PagedGridView.PageListener() {
            @Override
            public void onPageChanged(PagedGridView v, int page, int count, boolean byUser) {
                footer.setPage(page, count);
            }
        });
        footer.setListener(new FooterView.Listener() {
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
        });
        d.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface di, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                    list.nextPage();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP) {
                    list.prevPage();
                    return true;
                }
                return false;
            }
        });
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setWindowAnimations(0);
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        d.show();
        // Open the list on the page that contains the current chapter.
        root.post(new Runnable() {
            @Override
            public void run() {
                list.setPage(current / list.perPage(), false);
            }
        });
    }
}
