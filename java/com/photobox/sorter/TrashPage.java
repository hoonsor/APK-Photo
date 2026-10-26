package com.photobox.sorter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

public class TrashPage extends Page {

    private ArrayList<Item> items = new ArrayList<Item>();
    private final HashSet<Long> selected = new HashSet<Long>();
    private GridView grid;
    private GridAdapter adapter;
    private TextView title, sub, empty, selInfo;
    private TextView btnRestore, btnEmpty, btnPurge;
    private TextView sysInfo;
    private int cell;

    public TrashPage(MainActivity a) { super(a); }

    @Override
    public View build() {
        int cols = 3;
        cell = (a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 12 * 2)
                - Ui.dp(a, 4) * (cols - 1)) / cols;

        LinearLayout root = Ui.col(a);
        root.setBackgroundColor(T.BG);

        LinearLayout head = Ui.col(a);
        head.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 10));
        title = Ui.tv(a, "待刪清單", 21f, T.TEXT, true);
        head.addView(title, Ui.lpW());
        head.addView(Ui.space(a, 6));
        sub = Ui.tv(a, "", 13.5f, T.DIM, false);
        sub.setLineSpacing(Ui.dp(a, 3), 1f);
        head.addView(sub, Ui.lpW());
        head.addView(Ui.space(a, 8));
        sysInfo = Ui.tv(a, "", 13f, T.DIM, false);
        sysInfo.setLineSpacing(Ui.dp(a, 3), 1f);
        head.addView(sysInfo, Ui.lpW());
        root.addView(head, Ui.lpW());

        selInfo = Ui.tv(a, "", 12.5f, T.FAINT, false);
        selInfo.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 8));
        root.addView(selInfo, Ui.lpW());

        FrameLayout wrap = new FrameLayout(a);
        grid = new GridView(a);
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(Ui.dp(a, 4));
        grid.setVerticalSpacing(Ui.dp(a, 4));
        grid.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), Ui.dp(a, 12));
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        adapter = new GridAdapter();
        grid.setAdapter(adapter);
        wrap.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Ui.tv(a, "待刪清單是空的\n\n在照片頁點開照片、往上滑就會加進來，\n確認之後再一次送進系統垃圾桶。",
                15f, T.FAINT, false);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(Ui.dp(a, 5), 1f);
        wrap.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        wlp.weight = 1f;
        root.addView(wrap, wlp);

        LinearLayout bar = Ui.rowH(a);
        bar.setPadding(Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 12));

        btnRestore = Ui.btnGhost(a, "全部還原", T.KEEP);
        shrink(btnRestore);
        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { restore(); }
        });
        LinearLayout.LayoutParams lp = Ui.lpWeight();
        lp.rightMargin = Ui.dp(a, 7);
        bar.addView(btnRestore, lp);

        btnEmpty = Ui.btn(a, "清空待刪清單", T.TRASH, 0xFF1A0C0C);
        shrink(btnEmpty);
        btnEmpty.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmEmpty(); }
        });
        LinearLayout.LayoutParams lp2 = Ui.lpWeight();
        lp2.rightMargin = Ui.dp(a, 7);
        bar.addView(btnEmpty, lp2);

        btnPurge = Ui.btnGhost(a, "清空系統垃圾桶", T.TRASH);
        shrink(btnPurge);
        btnPurge.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmPurge(); }
        });
        bar.addView(btnPurge, Ui.lpWeight());

        root.addView(bar, Ui.lpW());

        return root;
    }

    /** 三顆按鈕並排時縮一下內距與字級。 */
    private void shrink(TextView t) {
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));
        t.setMaxLines(2);
    }

    private ArrayList<Item> target() {
        if (selected.isEmpty()) return new ArrayList<Item>(items);
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < items.size(); i++) {
            if (selected.contains(Long.valueOf(items.get(i).id))) out.add(items.get(i));
        }
        return out;
    }

    private void restore() {
        ArrayList<Item> t = target();
        if (t.isEmpty()) { Ui.toast(a, "待刪清單是空的"); return; }
        Store.get().setStates(a, t, Db.STATE_NONE);
        selected.clear();
        Ui.toast(a, "已還原 " + t.size() + " 個項目");
        refresh();
    }

    private void confirmEmpty() {
        final ArrayList<Item> t = target();
        if (t.isEmpty()) { Ui.toast(a, "待刪清單是空的"); return; }
        long bytes = 0;
        for (int i = 0; i < t.size(); i++) bytes += t.get(i).size;

        final boolean perm = Prefs.permanentDelete(a);
        String msg = "共 " + t.size() + " 個項目，約 " + Item.formatSize(bytes) + "。\n\n"
                + (perm ? "將直接永久刪除，無法復原。"
                        : "會送進系統相簿的垃圾桶，30 天內都可以在相簿 App 裡還原。")
                + "\n\n系統接著會再跳出一次確認視窗。";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(perm ? "永久刪除？" : "送進系統垃圾桶？")
                .setMessage(msg)
                .setPositiveButton(perm ? "永久刪除" : "送進垃圾桶", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (perm) a.deleteForever(t, null);
                        else a.sendToSystemTrash(t, null);
                        selected.clear();
                    }
                })
                .setNeutralButton("改用另一種方式", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (perm) a.sendToSystemTrash(t, null);
                        else a.deleteForever(t, null);
                        selected.clear();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 系統垃圾桶 ----------

    private void confirmPurge() {
        final ArrayList<Item> known = Db.get(a).loadTrashed();
        Ui.toast(a, "正在確認系統垃圾桶…");
        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Item> live = Media.refreshTrashed(a, known);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() { showPurgeDialog(live); }
                });
            }
        }, "purge-check").start();
    }

    private void showPurgeDialog(final ArrayList<Item> live) {
        if (live.isEmpty()) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("目前沒有可以清的項目")
                    .setMessage("這支 App 沒有紀錄到任何被送進系統垃圾桶的照片。\n\n"
                            + "另外要說明：Android 只允許「系統相簿」看見其他 App 丟進垃圾桶的檔案，"
                            + "所以如果那些照片是你直接在相簿 App 裡刪掉的，這裡就看不到，"
                            + "需要到相簿 App 的「最近刪除」自行清空。")
                    .setPositiveButton("開啟相簿 App", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { openGallery(); }
                    })
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }
        long bytes = 0;
        for (int i = 0; i < live.size(); i++) bytes += live.get(i).size;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("永久清除系統垃圾桶？")
                .setMessage("共 " + live.size() + " 個項目，約 " + Item.formatSize(bytes) + "。\n\n"
                        + "這些是透過這支 App 送進系統垃圾桶的照片。清除後空間立刻釋放，"
                        + "但就再也救不回來了。\n\n系統接著會再跳出一次確認視窗。")
                .setPositiveButton("永久清除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        a.purgeSystemTrash(live, null);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openGallery() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Throwable t) { Ui.toast(a, "找不到可開啟的相簿 App"); }
    }

    public void refresh() {
        if (root == null) return;
        items = Store.get().pending();
        long bytes = 0;
        for (int i = 0; i < items.size(); i++) bytes += items.get(i).size;
        title.setText("待刪清單");
        sub.setText(items.size() + " 個項目　·　約 " + Item.formatSize(bytes) + " 可釋放\n"
                + (Prefs.permanentDelete(a) ? "目前設定：永久刪除" : "目前設定：送進系統垃圾桶（30 天內可還原）"));
        ArrayList<Item> sys = Db.get(a).loadTrashed();
        long sysBytes = 0;
        for (int i = 0; i < sys.size(); i++) sysBytes += sys.get(i).size;
        if (sys.isEmpty()) {
            sysInfo.setText("系統垃圾桶：這支 App 還沒送過任何項目過去");
            btnPurge.setAlpha(0.5f);
        } else {
            sysInfo.setText("系統垃圾桶：本 App 送出 " + sys.size() + " 個項目　·　約 "
                    + Item.formatSize(sysBytes) + "\n30 天後系統會自動清除，也可以按右下角立刻清掉");
            btnPurge.setAlpha(1f);
        }
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        boolean any = !selected.isEmpty();
        selInfo.setTextColor(any ? T.PRIMARY : T.FAINT);
        selInfo.setText(any
                ? ("已挑選 " + selected.size() + " 個項目，按鈕只會套用到這些")
                : "點一下照片可以單獨挑選；沒有挑選時按鈕會套用到全部");
        btnRestore.setText(selected.isEmpty() ? "全部還原" : "還原挑選的");
        btnEmpty.setText(selected.isEmpty() ? "清空待刪清單" : "刪除挑選的");
        btnEmpty.setAlpha(items.isEmpty() ? 0.5f : 1f);
        btnRestore.setAlpha(items.isEmpty() ? 0.5f : 1f);
        adapter.notifyDataSetChanged();
    }

    private class GridAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return items.get(p).id; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            FrameLayout f = (FrameLayout) cv;
            if (f == null) {
                f = new FrameLayout(a);
                f.setBackgroundColor(T.SURFACE2);
                ImageView iv = new ImageView(a);
                iv.setId(10);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                f.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                View sel = new View(a);
                sel.setId(11);
                f.addView(sel, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ImageView chk = new ImageView(a);
                chk.setId(12);
                chk.setImageDrawable(Icons.get(Icons.CHECK, 0xFFFFFFFF));
                chk.setBackground(Ui.round(T.PRIMARY, Ui.dp(a, 11)));
                chk.setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                        Ui.dp(a, 22), Ui.dp(a, 22));
                clp.gravity = Gravity.TOP | Gravity.END;
                clp.topMargin = Ui.dp(a, 6);
                clp.rightMargin = Ui.dp(a, 6);
                f.addView(chk, clp);
                TextView size = Ui.tv(a, "", 11f, 0xFFFFFFFF, true);
                size.setId(13);
                size.setBackgroundColor(0x99000000);
                size.setPadding(Ui.dp(a, 5), Ui.dp(a, 2), Ui.dp(a, 5), Ui.dp(a, 2));
                FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.gravity = Gravity.BOTTOM | Gravity.START;
                f.addView(size, slp);
                f.setLayoutParams(new android.widget.AbsListView.LayoutParams(cell, cell));
            }
            final Item it = items.get(pos);
            ImageView iv = (ImageView) f.findViewById(10);
            Thumbs.grid(iv, it, 256);
            boolean on = selected.contains(Long.valueOf(it.id));
            f.findViewById(11).setBackgroundColor(on ? Ui.withAlpha(T.PRIMARY, 0x66) : 0x00000000);
            f.findViewById(12).setVisibility(on ? View.VISIBLE : View.GONE);
            ((TextView) f.findViewById(13)).setText(it.sizeText());
            f.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Long k = Long.valueOf(it.id);
                    if (selected.contains(k)) selected.remove(k); else selected.add(k);
                    refresh();
                }
            });
            f.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    Intent in = new Intent(a, ViewerActivity.class);
                    in.putExtra("id", it.id);
                    a.startActivity(in);
                    return true;
                }
            });
            return f;
        }
    }

    @Override public void onShow() { refresh(); }
    @Override public void onStoreChanged() { refresh(); }
    @Override public boolean onBack() {
        if (!selected.isEmpty()) { selected.clear(); refresh(); return true; }
        return false;
    }
}
