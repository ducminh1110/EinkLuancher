package com.eink.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Full screen, paged (no scrolling) folder chooser in the launcher's e-ink style. */
final class FolderPicker implements PagedGridView.Adapter, ToolbarView.Listener, FooterView.Listener {
    interface Callback {
        void onPicked(File dir);
    }

    private final Activity act;
    private final Callback cb;
    private final Dialog dialog;
    private final ToolbarView toolbar;
    private final PagedGridView list;
    private final FooterView footer;
    private final List<FileItem> items = new ArrayList<>();
    private List<FileItem> roots;
    private FileItem root;   // storage the user is in
    private File dir;        // null = list of storages
    private final TextPaint name = Ui.text(16f, true);
    private final Paint sep = Ui.line(1f), thick = Ui.line(3f);

    static void show(Activity act, File start, Callback cb) {
        new FolderPicker(act, start, cb);
    }

    private FolderPicker(Activity act, File start, Callback cb) {
        this.act = act;
        this.cb = cb;
        dialog = new Dialog(act, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.WHITE);
        toolbar = new ToolbarView(act);
        toolbar.setListener(this);
        box.addView(toolbar);
        list = new PagedGridView(act);
        list.setLayout(1, 60f, false);
        list.setEmptyText(act.getString(R.string.no_subfolders));
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        footer = new FooterView(act, FooterView.DOTS);
        footer.setListener(this);
        box.addView(footer);
        box.addView(EinkMenu.buttons(act, act.getString(R.string.cancel), act.getString(R.string.select_folder),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                }, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pick();
                    }
                }));
        list.setPageListener(new PagedGridView.PageListener() {
            @Override
            public void onPageChanged(PagedGridView v, int page, int count, boolean byUser) {
                footer.setPage(page, count);
            }
        });
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                        || keyCode == KeyEvent.KEYCODE_VOLUME_UP;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                        list.nextPage();
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_UP:
                    case KeyEvent.KEYCODE_PAGE_UP:
                        list.prevPage();
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                        if (!up()) dialog.dismiss();
                        return true;
                }
                return false;
            }
        });

        roots = Library.roots(act);
        if (start != null && start.isDirectory()) {
            for (FileItem r : roots) {
                String rp = r.file.getPath();
                if (start.getPath().equals(rp) || start.getPath().startsWith(rp + "/")) {
                    root = r;
                    dir = start;
                }
            }
        }
        if (dir == null && roots.size() == 1) {
            root = roots.get(0);
            dir = root.file;
        }
        list.setAdapter(this);
        load();

        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setWindowAnimations(0);
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        dialog.show();
    }

    private void load() {
        items.clear();
        if (dir == null) {
            items.addAll(roots);
            toolbar.set(act.getString(R.string.choose_folder), true);
        } else {
            File[] kids = dir.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.isDirectory() && !k.getName().startsWith(".")) items.add(new FileItem(k, true));
                }
            }
            final Collator col = Collator.getInstance();
            col.setStrength(Collator.PRIMARY);
            Collections.sort(items, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem a, FileItem b) {
                    return col.compare(a.name, b.name);
                }
            });
            toolbar.set(Library.displayPath(act, dir), true);
        }
        list.setPage(0, false);
        list.notifyDataChanged();
    }

    /** @return false when already at the top (the dialog should close). */
    private boolean up() {
        if (dir == null) return false;
        if (root != null && dir.getPath().equals(root.file.getPath())) {
            if (roots.size() <= 1) return false;
            dir = null;
            root = null;
        } else {
            dir = dir.getParentFile();
        }
        load();
        return true;
    }

    private void pick() {
        if (dir == null) {
            android.widget.Toast.makeText(act, R.string.open_folder_first, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        dialog.dismiss();
        cb.onPicked(dir);
    }

    // ------------------------------------------------------------ adapter

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public void drawCell(Canvas c, int pos, float l, float t, float r, float b, boolean pressed) {
        FileItem f = items.get(pos);
        float cy = (t + b) / 2f;
        Icons.draw(c, f.label != null ? Icons.SDCARD : Icons.FOLDER, l + Ui.px(38), cy, Ui.px(30), Ui.INK, 1.6f);
        c.drawText(Ui.ellipsize(f.title(), name, r - l - Ui.px(120)), l + Ui.px(70), Ui.baseline(name, cy), name);
        Icons.draw(c, Icons.CHEVRON, r - Ui.px(26), cy, Ui.px(18), Ui.INK, 1.8f);
        if (pressed) c.drawRect(l + Ui.px(6), t + Ui.px(3), r - Ui.px(6), b - Ui.px(3), thick);
        c.drawLine(l + Ui.px(18), b - 0.5f, r - Ui.px(18), b - 0.5f, sep);
    }

    @Override
    public void onCellClick(int pos) {
        FileItem f = items.get(pos);
        if (f.label != null) root = f;
        dir = f.file;
        load();
    }

    @Override
    public boolean onCellLongClick(int pos) {
        return false;
    }

    @Override
    public void onPageShown(int first, int last) {}

    @Override
    public void onToolbarAction(int iconId) {}

    @Override
    public void onToolbarBack() {
        if (!up()) dialog.dismiss();
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
