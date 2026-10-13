package com.photobox.sorter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsPage extends Page {

    private LinearLayout gestureBox;
    private TextView statsText;

    public SettingsPage(MainActivity a) { super(a); }

    @Override
    public View build() {
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(T.BG);
        sv.setFillViewport(true);

        LinearLayout root = Ui.col(a);
        root.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 28));
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(Ui.tv(a, "設定", 21f, T.TEXT, true), Ui.lpW());
        root.addView(Ui.space(a, 16));

        // ---- 手勢 ----
        LinearLayout card1 = card();
        card1.addView(sectionTitle("滑動手勢", "每個方向要做什麼，都可以自己決定"));
        card1.addView(Ui.space(a, 6));
        gestureBox = Ui.col(a);
        card1.addView(gestureBox, Ui.lpW());
        card1.addView(Ui.space(a, 8));
        TextView reset = Ui.btnGhost(a, "恢復預設配置", T.DIM);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.resetGestures(a);
                rebuildGestures();
                Ui.toast(a, "已恢復預設");
            }
        });
        card1.addView(reset, Ui.lpW());
        root.addView(card1, Ui.lpW());
        root.addView(Ui.space(a, 14));

        // ---- 內容 ----
        LinearLayout card2 = card();
        card2.addView(sectionTitle("內容與操作", null));
        card2.addView(switchRow("顯示影片", "關閉後照片頁、相簿與整理都只顯示照片",
                Prefs.showVideo(a), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setShowVideo(a, on);
                a.reload();
            }
        }));
        card2.addView(Ui.divider(a));
        card2.addView(switchRow("滑動時震動回饋", null,
                Prefs.hapticOn(a), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setHaptic(a, on);
            }
        }));
        card2.addView(Ui.divider(a));
        card2.addView(switchRow("清空時直接永久刪除",
                "預設為關閉：照片會先進系統垃圾桶，30 天內都救得回來",
                Prefs.permanentDelete(a), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                Prefs.setPermanentDelete(a, on);
                if (a.trashPage != null) a.trashPage.refresh();
            }
        }));
        root.addView(card2, Ui.lpW());
        root.addView(Ui.space(a, 14));

        // ---- 資料 ----
        LinearLayout card3 = card();
        card3.addView(sectionTitle("整理進度", null));
        statsText = Ui.tv(a, "", 13.5f, T.DIM, false);
        statsText.setLineSpacing(Ui.dp(a, 4), 1f);
        card3.addView(statsText, Ui.lpW());
        card3.addView(Ui.space(a, 12));

        TextView rescan = Ui.btnGhost(a, "重新掃描媒體庫", T.PRIMARY);
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.reload();
                Ui.toast(a, "重新掃描中…");
            }
        });
        card3.addView(rescan, Ui.lpW());
        card3.addView(Ui.space(a, 8));

        TextView clear = Ui.btnGhost(a, "清除所有整理紀錄", T.TRASH);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("清除整理紀錄？")
                        .setMessage("所有「已保留」和「待刪」的標記都會被清空，"
                                + "照片本身不會有任何變動，等於全部重新開始整理。")
                        .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Db.get(a).clearAllStates();
                                Store.get().state.clear();
                                Store.get().clearUndo();
                                Store.get().notifyChanged();
                                if (a.sortPage != null) a.sortPage.rebuildDeck();
                                refreshStats();
                                Ui.toast(a, "已清除");
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        card3.addView(clear, Ui.lpW());
        root.addView(card3, Ui.lpW());
        root.addView(Ui.space(a, 14));

        // ---- 關於 ----
        LinearLayout card4 = card();
        card4.addView(sectionTitle("關於", null));
        card4.addView(Ui.tv(a, "照片整理箱　v1.1.8", 14f, T.TEXT, true), Ui.lpW());
        card4.addView(Ui.space(a, 6));
        TextView about = Ui.tv(a,
                "所有運算都在這支手機上完成，沒有網路權限，照片不會被上傳到任何地方。\n\n"
              + "刪除一律透過 Android 系統的確認流程，App 自己無法偷偷刪除你的檔案。\n\n"
              + "相簿只是整理用的清單，不會搬動或複製檔案。", 13f, T.DIM, false);
        about.setLineSpacing(Ui.dp(a, 4), 1f);
        card4.addView(about, Ui.lpW());
        root.addView(card4, Ui.lpW());

        rebuildGestures();
        return sv;
    }

    private LinearLayout card() {
        LinearLayout l = Ui.col(a);
        l.setBackground(Ui.round(T.SURFACE, Ui.dp(a, 18)));
        l.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 16));
        return l;
    }

    private LinearLayout sectionTitle(String title, String desc) {
        LinearLayout l = Ui.col(a);
        l.addView(Ui.tv(a, title, 16f, T.TEXT, true), Ui.lpW());
        if (desc != null) {
            l.addView(Ui.space(a, 4));
            l.addView(Ui.tv(a, desc, 12.5f, T.FAINT, false), Ui.lpW());
        }
        l.addView(Ui.space(a, 10));
        return l;
    }

    private void rebuildGestures() {
        if (gestureBox == null) return;
        gestureBox.removeAllViews();
        for (int d = 0; d < 4; d++) {
            final int dir = d;
            int act = Prefs.action(a, dir);

            LinearLayout row = Ui.rowH(a);
            row.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 12)));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickAction(dir); }
            });

            ImageView arrow = new ImageView(a);
            int ai = Icons.ARROW_LEFT;
            if (dir == Prefs.RIGHT) ai = Icons.ARROW_RIGHT;
            if (dir == Prefs.UP) ai = Icons.ARROW_UP;
            if (dir == Prefs.DOWN) ai = Icons.ARROW_DOWN;
            arrow.setImageDrawable(Icons.get(ai, T.DIM));
            row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));

            TextView name = Ui.tv(a, Prefs.DIR_NAMES[dir], 14.5f, T.TEXT, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dp(a, 10);
            row.addView(name, nlp);
            row.addView(Ui.grow(a));

            TextView val = Ui.tv(a, Prefs.ACT_NAMES[act], 13.5f, Prefs.color(act), true);
            row.addView(val);

            ImageView chev = new ImageView(a);
            chev.setImageDrawable(Icons.get(Icons.CHEVRON, T.FAINT));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 16));
            clp.leftMargin = Ui.dp(a, 6);
            row.addView(chev, clp);

            LinearLayout.LayoutParams rlp = Ui.lpW();
            rlp.bottomMargin = Ui.dp(a, 8);
            gestureBox.addView(row, rlp);
        }
    }

    private void pickAction(final int dir) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Prefs.DIR_NAMES[dir] + " 要做什麼？")
                .setItems(Prefs.ACT_NAMES, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Prefs.setAction(a, dir, w);
                        rebuildGestures();
                        if (a.sortPage != null) a.sortPage.onShow();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private LinearLayout switchRow(String title, String desc, boolean on,
                                   CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = Ui.rowH(a);
        row.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        LinearLayout texts = Ui.col(a);
        texts.addView(Ui.tv(a, title, 14.5f, T.TEXT, false), Ui.lpW());
        if (desc != null) {
            texts.addView(Ui.space(a, 3));
            TextView d = Ui.tv(a, desc, 12f, T.FAINT, false);
            d.setLineSpacing(Ui.dp(a, 2), 1f);
            texts.addView(d, Ui.lpW());
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.weight = 1f;
        tlp.rightMargin = Ui.dp(a, 12);
        row.addView(texts, tlp);

        Switch sw = new Switch(a);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void refreshStats() {
        if (statsText == null) return;
        Store s = Store.get();
        statsText.setText("照片與影片總數：" + s.all.size() + "\n"
                + "已保留：" + s.count(Db.STATE_KEPT) + "　·　"
                + "待刪：" + s.count(Db.STATE_PENDING) + "　·　"
                + "尚未整理：" + s.count(Db.STATE_NONE));
    }

    @Override public void onShow() { rebuildGestures(); refreshStats(); }
    @Override public void onStoreChanged() { refreshStats(); }
}
