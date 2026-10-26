package com.photobox.sorter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements Store.Listener {

    public static final int REQ_PERM = 1001;
    public static final int REQ_TRASH = 2001;
    public static final int REQ_DELETE = 2002;
    public static final int REQ_WRITE = 2003;
    public static final int REQ_PURGE = 2004;
    public static final int REQ_EXPORT = 3001;
    public static final int REQ_IMPORT = 3002;

    // v1.3.0 起沒有「整理」分頁：整理改在單張檢視用上下滑完成（見 ViewerActivity）
    public static final int TAB_PHOTOS = 0, TAB_ALBUMS = 1, TAB_TRASH = 2, TAB_MORE = 3;

    private FrameLayout content;
    private LinearLayout nav;
    private final ArrayList<Page> pages = new ArrayList<Page>();
    private final ArrayList<View> navItems = new ArrayList<View>();
    private int current = -1;
    private View permView;
    private LinearLayout rootCol;

    private List<Item> awaitingTrash;
    private List<Item> awaitingMove;
    private List<Item> awaitingPurge;
    private String awaitingMovePath;
    private Runnable afterAction;

    public BrowsePage browsePage;
    public TrashPage trashPage;
    public SettingsPage settingsPage;
    public AlbumsPage albumsPage;
    public MorePage morePage;

    private FrameLayout frame;
    private FrameLayout drawer;
    private LinearLayout drawerPanel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rootCol = Ui.col(this);
        rootCol.setBackgroundColor(T.BG);
        rootCol.setFitsSystemWindows(true);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        clp.weight = 1f;
        rootCol.addView(content, clp);

        nav = buildNav();
        rootCol.addView(nav, Ui.lpW());

        frame = new FrameLayout(this);
        frame.addView(rootCol, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        Store.get().addListener(this);
        autoCheckUpdate();

        if (!Media.granted(this)) {
            showPermissionScreen();
        } else {
            startApp();
        }
    }

    @Override protected void onDestroy() {
        UpdateDialog.activityDestroyed(this);
        Store.get().removeListener(this);
        super.onDestroy();
    }

    /** App 內更新：每天最多查一次 GitHub，有新版才跳對話框；任何錯誤都安靜略過 */
    private void autoCheckUpdate() {
        final android.content.Context app = getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                Updater.cleanup(app);
                final Updater.Release r = Updater.autoCheck(app);
                if (r == null) return;
                runOnUiThread(new Runnable() {
                    @Override public void run() { UpdateDialog.show(MainActivity.this, r, true); }
                });
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        Thumbs.trimMemory();
    }

    // ---------- 權限 ----------

    private void showPermissionScreen() {
        nav.setVisibility(View.GONE);
        content.removeAllViews();

        LinearLayout box = Ui.col(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(Icons.get(Icons.IMAGE, T.PRIMARY));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(icon, ilp);
        box.addView(Ui.space(this, 20));

        boolean partial = Media.partialOnly(this);
        TextView t1 = Ui.tv(this, partial ? "目前只拿到部分照片" : "需要存取你的相片", 22, T.TEXT, true);
        t1.setGravity(Gravity.CENTER);
        box.addView(t1, Ui.lpW());
        box.addView(Ui.space(this, 10));

        TextView t2 = Ui.tv(this, partial
                ? ("你剛才選的是「選取部分照片」，所以這個 App 只看得到那幾張，沒辦法幫你整理整個相簿。\n\n"
                 + "請點下面的按鈕，或到系統權限設定把「相片和影片」改成「允許全部」。")
                : ("這個 App 只在你的手機本機運作，不會上傳任何照片；網路只用來檢查 App 更新。\n\n"
                 + "請允許存取「所有照片」，否則只能整理你手動挑選的那幾張。"), 14.5f, T.DIM, false);
        t2.setGravity(Gravity.CENTER);
        t2.setLineSpacing(Ui.dp(this, 4), 1f);
        box.addView(t2, Ui.lpW());
        box.addView(Ui.space(this, 28));

        TextView btn = Ui.btn(this, partial ? "重新授權（選擇「允許全部」）" : "允許存取相片",
                T.PRIMARY, 0xFF0B0E16);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                requestPermissions(Media.perms(), REQ_PERM);
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 220), ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(btn, blp);

        box.addView(Ui.space(this, 12));
        TextView open = Ui.btnGhost(this, "開啟系統權限設定", T.DIM);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Throwable t) { Ui.toast(MainActivity.this, "無法開啟設定"); }
            }
        });
        box.addView(open, blp);

        permView = box;
        content.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (req == REQ_PERM) {
            if (Media.granted(this)) {
                startApp();
            } else {
                showPermissionScreen();
                Ui.toast(this, Media.partialOnly(this)
                        ? "只拿到部分照片，請改成「允許全部」"
                        : "沒有相片存取權限，無法整理照片");
            }
        }
    }

    private void startApp() {
        if (permView != null) {
            content.removeView(permView);
            permView = null;
        }
        nav.setVisibility(View.VISIBLE);
        if (pages.isEmpty()) {
            browsePage = new BrowsePage(this);
            trashPage = new TrashPage(this);
            settingsPage = new SettingsPage(this);
            albumsPage = new AlbumsPage(this);
            morePage = new MorePage(this);
            // 順序必須與 TAB_* 常數一致
            pages.add(browsePage);
            pages.add(albumsPage);
            pages.add(trashPage);
            pages.add(morePage);
        }
        styleNav(TAB_PHOTOS);
        select(TAB_PHOTOS);
        Store.get().load(this, null);
    }

    public void reload() {
        Store.get().load(this, null);
    }

    // ---------- 底部導覽 ----------

    private LinearLayout buildNav() {
        LinearLayout outer = Ui.col(this);
        outer.setBackgroundColor(T.SURFACE);

        View line = new View(this);
        line.setBackgroundColor(T.BORDER);
        outer.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.8f))));

        LinearLayout bar = Ui.rowH(this);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 9));

        String[] labels = { "照片", "相簿", "待刪", "更多" };
        int[] icons = { Icons.IMAGE, Icons.ALBUM, Icons.TRASH, Icons.MORE };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout cell = Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 7));

            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Icons.get(icons[i], NAV_OFF));
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
            cell.addView(Ui.space(this, 4));
            TextView tv = Ui.tv(this, labels[i], 11.5f, NAV_OFF, true);
            cell.addView(tv);

            cell.setTag(new Object[]{ iv, tv, Integer.valueOf(icons[i]) });
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(idx); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.weight = 1f;
            lp.leftMargin = Ui.dp(this, 2);
            lp.rightMargin = Ui.dp(this, 2);
            bar.addView(cell, lp);
            navItems.add(cell);
        }
        outer.addView(bar, Ui.lpW());
        return outer;
    }

    private static final int NAV_OFF = 0xFFAEB8C7;

    private void styleNav(int idx) {
        for (int i = 0; i < navItems.size(); i++) {
            View cell = navItems.get(i);
            Object[] tag = (Object[]) cell.getTag();
            ImageView iv = (ImageView) tag[0];
            TextView tv = (TextView) tag[1];
            int ic = ((Integer) tag[2]).intValue();
            boolean on = (i == idx);
            int color = on ? T.PRIMARY : NAV_OFF;
            cell.setBackground(Ui.tappable(
                    on ? Ui.withAlpha(T.PRIMARY, 0x26) : T.SURFACE2,
                    Ui.dp(this, 14),
                    on ? Ui.withAlpha(T.PRIMARY, 0x8C) : T.BORDER,
                    Ui.dp(this, on ? 1.4f : 1f)));
            iv.setImageDrawable(Icons.get(ic, color));
            tv.setTextColor(color);
        }
    }

    public void select(int idx) {
        if (idx < 0 || idx >= pages.size()) return;
        if (current == idx) {
            pages.get(idx).onShow();
            return;
        }
        if (current >= 0) pages.get(current).onHide();
        current = idx;
        content.removeAllViews();
        content.addView(pages.get(idx).view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pages.get(idx).onShow();

        styleNav(idx);
    }

    /** 多選時把底部導覽藏起來，讓工具列與照片有更多空間。 */
    public void setNavVisible(boolean on) {
        if (nav != null) nav.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    public void goBrowseWithAlbum(String album) {
        select(TAB_PHOTOS);
        if (browsePage != null) browsePage.applyAlbum(album);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateDialog.resume();
    }

    /**
     * 逐張整理：從 start 這張打開單張檢視，左右滑的範圍是 items（取代原本的整理分頁）。
     * 在單張檢視裡往上滑加入待刪、往下滑編輯。
     */
    public void reviewFrom(Item start, java.util.List<Item> items, String label) {
        if (start == null || items == null || items.isEmpty()) { Ui.toast(this, "這裡沒有照片"); return; }
        ViewerActivity.open(this, start, items);
        if (label != null) Ui.toast(this, "逐張整理「" + label + "」，共 " + items.size() + " 張：往上滑待刪、往下滑編輯");
    }

    @Override public void onBackPressed() {
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) { closeDrawer(); return; }
        if (current >= 0 && current < pages.size() && pages.get(current).onBack()) return;
        if (current != TAB_PHOTOS && !pages.isEmpty()) { select(TAB_PHOTOS); return; }
        super.onBackPressed();
    }

    @Override public void onStoreChanged() {
        for (int i = 0; i < pages.size(); i++) {
            try { pages.get(i).onStoreChanged(); } catch (Throwable ignored) { }
        }
    }

    // ---------- 系統確認流程 ----------

    /** 把照片送進系統相簿垃圾桶（可 30 天內還原）。 */
    public void sendToSystemTrash(List<Item> items, Runnable done) {
        if (items == null || items.isEmpty()) return;
        awaitingTrash = new ArrayList<Item>(items);
        afterAction = done;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (int i = 0; i < items.size(); i++) uris.add(items.get(i).uri());
        Media.requestTrash(this, uris, true, REQ_TRASH);
    }

    /** 永久刪除。 */
    public void deleteForever(List<Item> items, Runnable done) {
        if (items == null || items.isEmpty()) return;
        awaitingTrash = new ArrayList<Item>(items);
        afterAction = done;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (int i = 0; i < items.size(); i++) uris.add(items.get(i).uri());
        Media.requestDelete(this, uris, REQ_DELETE);
    }

    /** 移動到其他相簿。 */
    public void moveToAlbum(List<Item> items, String album, Runnable done) {
        if (items == null || items.isEmpty()) return;
        awaitingMove = new ArrayList<Item>(items);
        awaitingMovePath = Media.relativePathFor(items.get(0).video, album);
        afterAction = done;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (int i = 0; i < items.size(); i++) uris.add(items.get(i).uri());
        Media.requestWrite(this, uris, REQ_WRITE);
    }

    /** 永久清掉先前送進系統垃圾桶的項目。 */
    public void purgeSystemTrash(List<Item> items, Runnable done) {
        if (items == null || items.isEmpty()) return;
        awaitingPurge = new ArrayList<Item>(items);
        afterAction = done;
        ArrayList<Uri> uris = new ArrayList<Uri>();
        for (int i = 0; i < items.size(); i++) uris.add(items.get(i).uri());
        Media.requestDelete(this, uris, REQ_PURGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXPORT || req == REQ_IMPORT) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                if (req == REQ_EXPORT) Backup.exportTo(this, data.getData());
                else Backup.importFrom(this, data.getData());
            }
            return;
        }
        if (req == REQ_PURGE) {
            if (res == Activity.RESULT_OK && awaitingPurge != null) {
                int n = awaitingPurge.size();
                Db.get(this).removeTrashed(awaitingPurge);
                Ui.toast(this, "已永久清除 " + n + " 個項目");
            } else {
                Ui.toast(this, "已取消，垃圾桶內容沒有變動");
            }
            awaitingPurge = null;
            if (trashPage != null) trashPage.refresh();
            runAfter();
        } else if (req == REQ_TRASH || req == REQ_DELETE) {
            if (res == Activity.RESULT_OK && awaitingTrash != null) {
                int n = awaitingTrash.size();
                if (req == REQ_TRASH) Db.get(this).addTrashed(awaitingTrash);
                Store.get().forget(this, awaitingTrash);
                Ui.toast(this, req == REQ_TRASH
                        ? ("已把 " + n + " 個項目移到系統垃圾桶，30 天內都救得回來")
                        : ("已永久刪除 " + n + " 個項目"));
                Store.get().clearUndo();
            } else {
                Ui.toast(this, "已取消，沒有任何照片被刪除");
            }
            awaitingTrash = null;
            runAfter();
        } else if (req == REQ_WRITE) {
            if (res == Activity.RESULT_OK && awaitingMove != null) {
                int ok = Media.moveAfterGrant(this, awaitingMove, awaitingMovePath);
                Ui.toast(this, ok > 0 ? ("已移動 " + ok + " 個項目") : "移動失敗，系統不允許變更這些檔案");
                reload();
            } else {
                Ui.toast(this, "已取消移動");
            }
            awaitingMove = null;
            runAfter();
        }
    }

    private void runAfter() {
        Runnable r = afterAction;
        afterAction = null;
        if (r != null) try { r.run(); } catch (Throwable ignored) { }
    }

    public Drawable icon(int kind, int color) { return Icons.get(kind, color); }

    // ---------- 左側選單（☰） ----------

    public void openDrawer() {
        if (drawer == null) buildDrawer();
        drawerPanel.removeAllViews();
        morePage.fillMenu(drawerPanel, true);
        drawer.setVisibility(View.VISIBLE);
        drawer.setAlpha(0f);
        drawer.animate().alpha(1f).setDuration(160).start();
        drawerPanel.setTranslationX(-drawerPanel.getLayoutParams().width);
        drawerPanel.animate().translationX(0).setDuration(200).start();
    }

    public void closeDrawer() {
        if (drawer == null || drawer.getVisibility() != View.VISIBLE) return;
        drawerPanel.animate().translationX(-drawerPanel.getLayoutParams().width).setDuration(180).start();
        drawer.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() { drawer.setVisibility(View.GONE); }
        }).start();
    }

    private void buildDrawer() {
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0x99000000);
        drawer.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        drawerPanel = Ui.col(this);
        drawerPanel.setBackgroundColor(T.SURFACE);
        drawerPanel.setClickable(true);
        drawerPanel.setFitsSystemWindows(true);
        int w = Math.min(Ui.dp(this, 320), getResources().getDisplayMetrics().widthPixels * 80 / 100);
        drawer.addView(drawerPanel, new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        drawer.setVisibility(View.GONE);
        frame.addView(drawer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
