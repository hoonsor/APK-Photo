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

        // ---- 手勢（v1.3.0 起固定，不再可設定） ----
        LinearLayout card1 = card();
        card1.addView(sectionTitle("單張檢視的手勢", "在照片頁點開任一張照片後使用"));
        String[][] g = {
                { "往左滑／往右滑", "下一張／上一張" },
                { "往上滑", "加入待刪清單（10 秒內可以按「復原」）" },
                { "往下滑", "編輯照片（文字、螢光筆、框線、箭頭）" },
                { "照片放大時", "滑動是拖曳，縮回原大小（或點兩下）才會觸發上面的動作" }
        };
        int[] gi = { Icons.ARROW_LEFT, Icons.ARROW_UP, Icons.ARROW_DOWN, Icons.SEARCH };
        for (int i = 0; i < g.length; i++) {
            LinearLayout row = Ui.rowH(a);
            row.setBackground(Ui.round(T.SURFACE2, Ui.dp(a, 12)));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
            ImageView arrow = new ImageView(a);
            arrow.setImageDrawable(Icons.get(gi[i], T.DIM));
            row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
            LinearLayout tx = Ui.col(a);
            tx.addView(Ui.tv(a, g[i][0], 14.5f, T.TEXT, true), Ui.lpW());
            tx.addView(Ui.space(a, 2));
            tx.addView(Ui.tv(a, g[i][1], 12.5f, T.DIM, false), Ui.lpW());
            LinearLayout.LayoutParams tlp = Ui.lpWeight();
            tlp.leftMargin = Ui.dp(a, 10);
            row.addView(tx, tlp);
            LinearLayout.LayoutParams rlp = Ui.lpW();
            rlp.bottomMargin = Ui.dp(a, 8);
            card1.addView(row, rlp);
        }
        root.addView(card1, Ui.lpW());
        root.addView(Ui.space(a, 14));

        // ---- 內容 ----
        LinearLayout card2 = card();
        card2.addView(sectionTitle("內容與操作", null));
        card2.addView(switchRow("顯示影片", "關閉後照片頁與相簿都只顯示照片",
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

        // ---- App 更新 ----
        LinearLayout card5 = card();
        card5.addView(sectionTitle("App 更新", "每天第一次開啟時自動檢查一次"));
        card5.addView(Ui.tv(a, "目前版本 v" + Updater.currentVersion(a), 13.5f, T.DIM, false), Ui.lpW());
        card5.addView(Ui.space(a, 10));
        final TextView updateStatus = Ui.tv(a, "", 13f, T.DIM, false);
        updateStatus.setVisibility(View.GONE);
        final TextView checkBtn = Ui.btnGhost(a, "檢查更新", T.PRIMARY);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkUpdate(checkBtn, updateStatus); }
        });
        card5.addView(checkBtn, Ui.lpW());
        card5.addView(Ui.space(a, 8));
        card5.addView(updateStatus, Ui.lpW());
        TextView updNote = Ui.tv(a,
                "新版從 GitHub 下載後由系統安裝，相簿與整理進度都會保留。"
              + "網路只用來檢查與下載更新，照片不會經過網路。", 12f, T.FAINT, false);
        updNote.setLineSpacing(Ui.dp(a, 2), 1f);
        card5.addView(updNote, Ui.lpW());
        root.addView(card5, Ui.lpW());
        root.addView(Ui.space(a, 14));

        // ---- 關於 ----
        LinearLayout card4 = card();
        card4.addView(sectionTitle("關於", null));
        card4.addView(Ui.tv(a, "照片整理箱　v" + Updater.currentVersion(a), 14f, T.TEXT, true), Ui.lpW());
        card4.addView(Ui.space(a, 6));
        TextView about = Ui.tv(a,
                "所有運算都在這支手機上完成，照片不會被上傳到任何地方；網路權限只用來檢查與下載 App 更新。\n\n"
              + "刪除一律透過 Android 系統的確認流程，App 自己無法偷偷刪除你的檔案。\n\n"
              + "相簿只是整理用的清單，不會搬動或複製檔案。", 13f, T.DIM, false);
        about.setLineSpacing(Ui.dp(a, 4), 1f);
        card4.addView(about, Ui.lpW());
        root.addView(card4, Ui.lpW());

        return sv;
    }

    private void checkUpdate(final TextView btn, final TextView status) {
        btn.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setTextColor(T.DIM);
        status.setText("檢查中…");
        final android.content.Context app = a.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final Updater.Result r = Updater.check(app);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        btn.setEnabled(true);
                        if (r.release != null) {
                            status.setVisibility(View.GONE);
                            UpdateDialog.show(a, r.release, false);
                        } else if (r.error != null) {
                            status.setTextColor(T.MOVE);
                            status.setText("無法檢查更新：" + r.error);
                        } else {
                            status.setTextColor(T.KEEP);
                            status.setText("已經是最新版");
                        }
                    }
                });
            }
        }, "update-check").start();
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

    @Override public void onShow() { refreshStats(); }
    @Override public void onStoreChanged() { refreshStats(); }
}
