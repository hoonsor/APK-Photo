package com.photobox.sorter;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * 「更多」分頁，也是左上 ☰ 選單的內容：資料夾、重複／相似照片、備份與還原、設定、說明。
 * 子畫面在這個分頁內切換，返回鍵回到選單。
 */
public class MorePage extends Page {

    private static final int SUB_MENU = 0, SUB_FOLDERS = 1, SUB_SETTINGS = 2, SUB_BACKUP = 3, SUB_HELP = 4;

    private FrameLayout holder;
    private int sub = SUB_MENU;
    private TextView backupInfo;

    public MorePage(MainActivity a) { super(a); }

    @Override
    public View build() {
        holder = new FrameLayout(a);
        holder.setBackgroundColor(T.BG);
        show(SUB_MENU);
        return holder;
    }

    // ---------- 選單（分頁與 ☰ 共用） ----------

    /** 把選單項目填進 container。fromDrawer 為 true 時點選後會關閉側邊選單。 */
    public void fillMenu(LinearLayout container, final boolean fromDrawer) {
        LinearLayout head = Ui.col(a);
        head.setPadding(Ui.dp(a, 20), Ui.dp(a, 22), Ui.dp(a, 20), Ui.dp(a, 18));
        head.addView(Ui.tv(a, "照片整理箱", 21f, T.TEXT, true));
        head.addView(Ui.space(a, 6));
        int photos = 0, videos = 0;
        ArrayList<Item> all = Store.get().all;
        for (int i = 0; i < all.size(); i++) if (all.get(i).video) videos++; else photos++;
        int albums = Albums.get().normal().size();
        head.addView(Ui.tv(a, photos + " 張照片　·　" + videos + " 部影片　·　" + albums + " 本相簿",
                12.5f, T.DIM, false));
        container.addView(head, Ui.lpW());
        container.addView(Ui.divider(a));

        container.addView(menuRow(Icons.FOLDER, "資料夾", "手機裡實際的資料夾（相機、截圖、LINE…）", go(SUB_FOLDERS, fromDrawer)), Ui.lpW());
        container.addView(menuRow(Icons.COPIES, "重複／相似照片", "找出連拍、重複存檔的照片", new Runnable() {
            @Override public void run() {
                if (fromDrawer) a.closeDrawer();
                a.startActivity(new Intent(a, DupActivity.class));
            }
        }), Ui.lpW());
        container.addView(menuRow(Icons.BACKUP, "備份與還原", "相簿與整理進度存成檔案，換手機或重裝也不怕", go(SUB_BACKUP, fromDrawer)), Ui.lpW());
        container.addView(Ui.divider(a));
        container.addView(menuRow(Icons.SLIDERS, "設定", "滑動手勢、影片、刪除方式", go(SUB_SETTINGS, fromDrawer)), Ui.lpW());
        container.addView(menuRow(Icons.HELP, "說明", "相簿與資料夾的差別、常見問題", go(SUB_HELP, fromDrawer)), Ui.lpW());
    }

    private Runnable go(final int target, final boolean fromDrawer) {
        return new Runnable() {
            @Override public void run() {
                if (fromDrawer) a.closeDrawer();
                a.select(MainActivity.TAB_MORE);
                show(target);
            }
        };
    }

    private View menuRow(int icon, String title, String desc, final Runnable r) {
        LinearLayout row = Ui.rowH(a);
        row.setPadding(Ui.dp(a, 20), Ui.dp(a, 15), Ui.dp(a, 16), Ui.dp(a, 15));
        row.setBackground(Ui.ripple(Ui.cd(0x00000000), 0x22FFFFFF));
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(Icons.get(icon, T.PRIMARY));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
        LinearLayout texts = Ui.col(a);
        texts.addView(Ui.tv(a, title, 16f, T.TEXT, false));
        if (desc != null) {
            texts.addView(Ui.space(a, 4));
            TextView d = Ui.tv(a, desc, 12f, T.FAINT, false);
            texts.addView(d);
        }
        LinearLayout.LayoutParams tl = Ui.lpWeight();
        tl.leftMargin = Ui.dp(a, 18);
        row.addView(texts, tl);
        ImageView ch = new ImageView(a);
        ch.setImageDrawable(Icons.get(Icons.CHEVRON, T.FAINT));
        row.addView(ch, new LinearLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 16)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        return row;
    }

    // ---------- 子畫面切換 ----------

    public void show(int which) {
        if (holder == null) { view(); }
        sub = which;
        holder.removeAllViews();
        View v;
        switch (which) {
            case SUB_FOLDERS: v = withHeader("資料夾", buildFolders()); break;
            case SUB_SETTINGS: {
                View s = a.settingsPage.view();
                if (s.getParent() != null) ((ViewGroup) s.getParent()).removeView(s);
                v = withHeader("設定", s);
                a.settingsPage.onShow();
                break;
            }
            case SUB_BACKUP: v = withHeader("備份與還原", buildBackup()); break;
            case SUB_HELP: v = withHeader("說明", buildHelp()); break;
            default: {
                ScrollView sv = new ScrollView(a);
                LinearLayout col = Ui.col(a);
                fillMenu(col, false);
                sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                v = sv;
            }
        }
        holder.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View withHeader(String title, View content) {
        LinearLayout col = Ui.col(a);
        LinearLayout bar = Ui.rowH(a);
        bar.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 4));
        ImageView back = new ImageView(a);
        back.setImageDrawable(Icons.get(Icons.BACK, T.TEXT));
        int p = Ui.dp(a, 10);
        back.setPadding(p, p, p, p);
        back.setBackground(Ui.tappable(0, Ui.dp(a, 22)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { show(SUB_MENU); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        TextView t = Ui.tv(a, title, 19f, T.TEXT, true);
        LinearLayout.LayoutParams tl = Ui.lpWeight();
        tl.leftMargin = Ui.dp(a, 6);
        bar.addView(t, tl);
        col.addView(bar, Ui.lpW());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        clp.weight = 1f;
        col.addView(content, clp);
        return col;
    }

    // ---------- 資料夾 ----------

    private View buildFolders() {
        final ArrayList<String[]> folders = Store.get().albums();
        final HashMap<String, Item> covers = new HashMap<String, Item>();
        ArrayList<Item> all = Store.get().all;   // 已依時間新到舊排序，第一個遇到的就是最新
        for (int i = 0; i < all.size(); i++) {
            if (!covers.containsKey(all.get(i).bucket)) covers.put(all.get(i).bucket, all.get(i));
        }
        LinearLayout col = Ui.col(a);
        TextView hint = Ui.tv(a, "點資料夾在照片頁只看那個資料夾；長按可從它開始整理。", 12.5f, T.FAINT, false);
        hint.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 8));
        col.addView(hint, Ui.lpW());

        final GridView gv = new GridView(a);
        gv.setNumColumns(3);
        gv.setHorizontalSpacing(Ui.dp(a, 10));
        gv.setVerticalSpacing(Ui.dp(a, 12));
        gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gv.setPadding(Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 24));
        gv.setClipToPadding(false);
        gv.setSelector(Ui.cd(0));
        gv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return folders.size(); }
            @Override public Object getItem(int p) { return folders.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int pos, View cv, ViewGroup parent) {
                int w = (gv.getWidth() - gv.getPaddingLeft() - gv.getPaddingRight() - Ui.dp(a, 20)) / 3;
                if (w <= 0) w = Ui.dp(a, 110);
                LinearLayout card = Ui.col(a);
                card.setBackground(Ui.tappable(T.SURFACE, Ui.dp(a, 14), T.BORDER, Ui.dp(a, 1)));
                FrameLayout box = new FrameLayout(a);
                box.setBackground(Ui.round(T.SURFACE3, Ui.dp(a, 13)));
                box.setClipToOutline(true);
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                final String name = folders.get(pos)[0];
                Item c = covers.get(name);
                if (c != null) Thumbs.grid(iv, c, 256);
                box.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ImageView fi = new ImageView(a);
                fi.setImageDrawable(Icons.get(Icons.FOLDER, 0xFFFFFFFF));
                int bp = Ui.dp(a, 4);
                fi.setPadding(bp, bp, bp, bp);
                fi.setBackground(Ui.round(0x99000000, Ui.dp(a, 12)));
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24));
                flp.leftMargin = flp.topMargin = Ui.dp(a, 6);
                box.addView(fi, flp);
                card.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w));
                TextView n = Ui.tv(a, name, 13.5f, T.TEXT, true);
                n.setMaxLines(2);
                n.setPadding(Ui.dp(a, 9), Ui.dp(a, 8), Ui.dp(a, 9), 0);
                card.addView(n, Ui.lpW());
                TextView cnt = Ui.tv(a, folders.get(pos)[1] + " 項", 11.5f, T.FAINT, false);
                cnt.setPadding(Ui.dp(a, 9), Ui.dp(a, 4), Ui.dp(a, 9), Ui.dp(a, 10));
                card.addView(cnt, Ui.lpW());
                card.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { a.goBrowseWithAlbum(name); }
                });
                card.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        a.goSortWith(name, 0);
                        Ui.toast(a, "從「" + name + "」資料夾開始整理");
                        return true;
                    }
                });
                return card;
            }
        });
        gv.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            int lastW = -1;
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != lastW) { lastW = r - l; v.post(new Runnable() {
                    @Override public void run() { ((BaseAdapter) gv.getAdapter()).notifyDataSetChanged(); }
                }); }
            }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        glp.weight = 1f;
        col.addView(gv, glp);
        return col;
    }

    // ---------- 備份 ----------

    private LinearLayout card() {
        LinearLayout l = Ui.col(a);
        l.setBackground(Ui.round(T.SURFACE, Ui.dp(a, 18)));
        l.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 16));
        return l;
    }

    private TextView para(String s) {
        TextView t = Ui.tv(a, s, 13.5f, T.DIM, false);
        t.setLineSpacing(Ui.dp(a, 4), 1f);
        return t;
    }

    private View buildBackup() {
        ScrollView sv = new ScrollView(a);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 28));

        LinearLayout c1 = card();
        c1.addView(Ui.tv(a, "為什麼需要備份", 16f, T.TEXT, true));
        c1.addView(Ui.space(a, 8));
        c1.addView(para("相簿、喜好項目、已保留／待刪的標記，都只存在這個 App 裡面。"
                + "解除安裝 App（例如換新版時簽章不同、必須先移除舊版）或換手機，這些整理成果就會消失。\n\n"
                + "備份會把它們存成一個小檔案（不含照片本身），你可以放在下載資料夾、Google 雲端硬碟或 Dropbox。"), Ui.lpW());
        col.addView(c1, Ui.lpW());
        col.addView(Ui.space(a, 14));

        LinearLayout c2 = card();
        backupInfo = Ui.tv(a, "", 13f, T.FAINT, false);
        backupInfo.setLineSpacing(Ui.dp(a, 3), 1f);
        c2.addView(backupInfo, Ui.lpW());
        c2.addView(Ui.space(a, 12));
        TextView exp = Ui.btn(a, "匯出備份檔", T.PRIMARY, 0xFF0B0E16);
        exp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, "照片整理箱備份_"
                            + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".json");
                    a.startActivityForResult(i, MainActivity.REQ_EXPORT);
                } catch (Throwable t) { Ui.toast(a, "無法開啟檔案選擇器"); }
            }
        });
        c2.addView(exp, Ui.lpW());
        c2.addView(Ui.space(a, 10));
        TextView imp = Ui.btnGhost(a, "從備份檔還原", T.PRIMARY);
        imp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "text/plain", "application/octet-stream" });
                    a.startActivityForResult(i, MainActivity.REQ_IMPORT);
                } catch (Throwable t) { Ui.toast(a, "無法開啟檔案選擇器"); }
            }
        });
        c2.addView(imp, Ui.lpW());
        c2.addView(Ui.space(a, 12));
        c2.addView(para("還原是「合併」：同名相簿會把內容併在一起，不會刪掉你現在已有的相簿。"
                + "照片是用檔名、大小、拍攝時間比對的，所以換手機後只要照片有一起搬過去就對得上。"), Ui.lpW());
        col.addView(c2, Ui.lpW());
        refreshBackupInfo();

        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    public void refreshBackupInfo() {
        if (backupInfo == null) return;
        int albums = Albums.get().normal().size();
        Albums.Album fav = Albums.get().favorites();
        int favN = fav == null ? 0 : Albums.get().counts(fav)[0] + Albums.get().counts(fav)[1];
        long last = Backup.lastExport(a);
        backupInfo.setText("目前有 " + albums + " 本相簿、" + favN + " 個喜好項目、"
                + (Store.get().count(Db.STATE_KEPT) + Store.get().count(Db.STATE_PENDING)) + " 個整理標記。\n"
                + (last > 0 ? "上次匯出：" + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN).format(new Date(last))
                            : "還沒有匯出過備份。"));
    }

    // ---------- 說明 ----------

    private View buildHelp() {
        ScrollView sv = new ScrollView(a);
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 28));
        String[][] qa = {
                { "相簿和資料夾有什麼不同？",
                  "資料夾是檔案真正存放的位置（例如 Camera、Screenshots、LINE）。\n"
                + "相簿是這個 App 裡的整理清單：把照片加進相簿不會搬動或複製檔案，"
                + "同一張照片可以同時在好幾本相簿裡，照片和影片也能放在一起。刪除相簿也不會刪到照片。" },
                { "怎麼把照片放進相簿？",
                  "在照片頁長按任一張進入多選，繼續點選其他張（長按可一次選一整段），"
                + "再點左側欄的相簿；或點「建立相簿」直接建立新的。\n"
                + "點日期格（年/月、日、星期那一格）可以一次選取那一天的全部照片，再點一次取消。\n"
                + "在照片頁往右滑可以直接打開相簿整理（左側相簿欄滑出來），往左滑收起。\n"
                + "選好後長按任一張已選取的照片，會疊成一疊，拖到左側的相簿上放開就加入；拖到「建立相簿」會建立新相簿。\n"
                + "也可以在相簿裡按「＋ 新增照片」，或在單張檢視時按「加入相簿」。" },
                { "怎麼分享到 LINE、Dropbox、雲端硬碟？",
                  "多選後按右上角的分享圖示，或在相簿裡按「分享相簿」，會跳出系統分享選單，"
                + "選 LINE、Dropbox、Google 雲端硬碟等即可。" },
                { "怎麼從某一張照片開始整理？",
                  "在「整理」分頁按上方的「從某張開始」，點想開始的那一張，再選往舊或往新、只整理未整理的或全部。"
                + "也可以在照片頁點開那一張，按右上角 ⋮ →「從這張開始整理」。整理頁上方會顯示目前的方向。" },
                { "刪除會不會不小心刪錯？",
                  "不會直接刪。照片會先進「待刪」清單，按清空時才交給 Android 系統確認，"
                + "預設還會先進系統垃圾桶，30 天內都救得回來。" },
                { "為什麼看不到地圖？",
                  "內嵌地圖要把位置傳給地圖服務，這個 App 的網路只用來檢查 App 更新，所以「位置」只顯示座標，"
                + "按「在地圖 App 中開啟」交給 Google 地圖顯示。" },
                { "影片播不出來？",
                  "少數格式（例如部分 HEVC 或 10-bit HDR 影片）手機內建播放器不支援，"
                + "App 會自動改用其他播放器開啟；也可以在檢視畫面右上角選「用其他 App 播放」。" },
        };
        for (int i = 0; i < qa.length; i++) {
            LinearLayout c = card();
            c.addView(Ui.tv(a, qa[i][0], 15.5f, T.TEXT, true));
            c.addView(Ui.space(a, 8));
            c.addView(para(qa[i][1]), Ui.lpW());
            col.addView(c, Ui.lpW());
            col.addView(Ui.space(a, 12));
        }
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    // ---------- 生命週期 ----------

    @Override public void onShow() {
        if (sub == SUB_MENU) show(SUB_MENU);
        if (sub == SUB_BACKUP) refreshBackupInfo();
    }

    @Override public boolean onBack() {
        if (sub != SUB_MENU) { show(SUB_MENU); return true; }
        return false;
    }

    @Override public void onStoreChanged() {
        if (root == null) return;
        if (sub == SUB_MENU) show(SUB_MENU);
        else if (sub == SUB_BACKUP) refreshBackupInfo();
        else if (sub == SUB_SETTINGS) a.settingsPage.onStoreChanged();
    }
}
