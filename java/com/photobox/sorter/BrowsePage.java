package com.photobox.sorter;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * 照片頁（App 預設進入的畫面）。
 *
 * 照片牆在每一天的第一張前面插入日期格；長按任一張進入多選，
 * 左側滑出「加入相簿」欄，點一下就把選取的項目放進那本相簿。
 */
public class BrowsePage extends Page {

    private static final int COLS = 4;

    private final Store.Filter filter = new Store.Filter();
    private ArrayList<Item> result = new ArrayList<Item>();
    private int selectedYear = 0;
    private static boolean dragHintShown = false;
    private static boolean swipeHintShown = false;
    private int sidebarWidth;

    private EditText search;
    private MediaGrid grid;
    private LinearLayout head, selHead, chipBar, yearBar, monthBar, selBar, sidebarList;
    private View chipScroll, yearScroll, monthScroll;
    private ScrollView sidebar;
    private FrameLayout gridWrap;
    private boolean sidebarShown = false;
    private TextView summary, floatLabel, selCount;

    public BrowsePage(MainActivity a) { super(a); }

    @Override
    public View build() {
        LinearLayout root = Ui.col(a);
        root.setBackgroundColor(T.BG);

        // ---- 標題列：選單＋搜尋＋相似照片 ----
        head = Ui.rowH(a);
        head.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 6));
        head.addView(iconBtn(Icons.MENU, T.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { a.openDrawer(); }
        }), new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

        search = new EditText(a);
        search.setHint("搜尋：資料夾、檔名，或 2023 / 2023-07");
        search.setTextSize(14f);
        search.setTextColor(T.TEXT);
        search.setHintTextColor(T.FAINT);
        search.setSingleLine(true);
        search.setBackground(Ui.round(T.SURFACE2, Ui.dp(a, 14), T.BORDER, Ui.dp(a, 1)));
        search.setPadding(Ui.dp(a, 12), Ui.dp(a, 11), Ui.dp(a, 12), Ui.dp(a, 11));
        Drawable si = Icons.get(Icons.SEARCH, T.FAINT);
        si.setBounds(0, 0, Ui.dp(a, 18), Ui.dp(a, 18));
        search.setCompoundDrawablesRelative(si, null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(a, 8));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a1, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                filter.text = e.toString();
                refresh(true);
            }
        });
        LinearLayout.LayoutParams slp = Ui.lpWeight();
        slp.leftMargin = Ui.dp(a, 4);
        head.addView(search, slp);

        View dup = boxedIcon(Icons.COPIES, T.PRIMARY, new View.OnClickListener() {
            @Override public void onClick(View v) { a.startActivity(new Intent(a, DupActivity.class)); }
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44));
        dlp.leftMargin = Ui.dp(a, 8);
        head.addView(dup, dlp);
        root.addView(head, Ui.lpW());

        // ---- 多選時的標題列 ----
        selHead = buildSelHead();
        selHead.setVisibility(View.GONE);
        root.addView(selHead, Ui.lpW());

        // ---- 篩選膠囊、年份、月份 ----
        chipBar = Ui.rowH(a);
        chipScroll = scroller(chipBar);
        root.addView(chipScroll, Ui.lpW());
        yearBar = Ui.rowH(a);
        yearScroll = scroller(yearBar);
        root.addView(yearScroll, Ui.lpW());
        monthBar = Ui.rowH(a);
        monthScroll = scroller(monthBar);
        root.addView(monthScroll, Ui.lpW());

        summary = Ui.tv(a, "", 12.5f, T.DIM, false);
        summary.setPadding(Ui.dp(a, 14), Ui.dp(a, 4), Ui.dp(a, 14), Ui.dp(a, 6));
        root.addView(summary, Ui.lpW());

        // ---- 主體：左側相簿欄（多選時）＋照片牆 ----
        LinearLayout body = Ui.rowH(a);
        body.setGravity(Gravity.TOP);

        sidebar = new ScrollView(a);
        sidebar.setBackgroundColor(T.SURFACE);
        sidebar.setVerticalScrollBarEnabled(false);
        sidebarList = Ui.col(a);
        sidebarList.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 16));
        sidebar.addView(sidebarList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sidebar.setVisibility(View.GONE);
        sidebar.setOnDragListener(new View.OnDragListener() {
            @Override public boolean onDrag(View v, android.view.DragEvent e) {
                if (e.getAction() == android.view.DragEvent.ACTION_DRAG_STARTED)
                    return e.getLocalState() instanceof DragStack.Payload;
                if (e.getAction() == android.view.DragEvent.ACTION_DRAG_LOCATION) autoScroll(e.getY());
                // 放在相簿之間的空白處不算成功，讓系統把拖曳影像收回去
                return e.getAction() != android.view.DragEvent.ACTION_DROP;
            }
        });
        sidebarWidth = Math.max(Ui.dp(a, 108), a.getResources().getDisplayMetrics().widthPixels * 30 / 100);
        body.addView(sidebar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        // 內容固定為完整寬度，相簿欄寬度動畫時內容是「被推進來」而不是被壓扁
        sidebarList.setLayoutParams(new FrameLayout.LayoutParams(sidebarWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        gridWrap = new FrameLayout(a);
        grid = new MediaGrid(a, Prefs.gridCols(a));
        grid.setEmptyText("沒有符合的照片");
        grid.setHeaderAction("逐張整理", new MediaGrid.HeaderAction() {
            @Override public void onHeaderAction(int ym) { reviewMonth(ym); }
        });
        grid.setListener(new MediaGrid.Listener() {
            @Override public void onOpen(Item it, ArrayList<Item> ctx) { ViewerActivity.open(a, it, ctx); }
            @Override public void onSelectionChanged(int n) { updateSelBar(); }
            @Override public boolean onEnterSelect() { showSelectUi(true); return true; }
            @Override public void onExitSelect() { showSelectUi(false); }
        });
        grid.setSwipeListener(new MediaGrid.SwipeListener() {
            @Override public void onSwipe(int dir) {
                if (dir > 0 && !grid.inSelect()) {
                    grid.enterSelect(null);   // 往右滑：打開相簿整理（左側相簿欄滑進來）
                    if (!swipeHintShown) {
                        swipeHintShown = true;
                        Ui.toast(a, "點選照片後點左側相簿，或長按已選的照片拖過去；往左滑可收起");
                    }
                } else if (dir < 0 && grid.inSelect()) {
                    if (grid.selectedCount() == 0) grid.exitSelect();
                    else Ui.toast(a, "還有 " + grid.selectedCount() + " 個已選取的項目，按左上 ✕ 才會取消");
                }
            }
        });
        grid.setDragStarter(new MediaGrid.DragStarter() {
            @Override public boolean onStartDrag(View source, ArrayList<Item> sel) {
                if (sidebar.getVisibility() != View.VISIBLE) return false;
                boolean ok = DragStack.start(source, sel);
                if (ok && !dragHintShown) {
                    dragHintShown = true;
                    Ui.toast(a, "拖到左側相簿上放開即可加入；拖到「建立相簿」會建立新相簿");
                }
                return ok;
            }
        });
        grid.setOnScrollYm(new MediaGrid.OnScrollYm() {
            @Override public void onYm(int ym) {
                if (ym > 0) floatLabel.setText((ym / 100) + "/" + String.format("%02d", ym % 100));
            }
        });
        gridWrap.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        floatLabel = Ui.tv(a, "", 12.5f, T.TEXT, true);
        floatLabel.setBackground(Ui.round(0xE61E242F, Ui.dp(a, 14)));
        floatLabel.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.TOP | Gravity.END;
        flp.topMargin = Ui.dp(a, 8);
        flp.rightMargin = Ui.dp(a, 22);
        gridWrap.addView(floatLabel, flp);
        body.addView(gridWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        blp.weight = 1f;
        root.addView(body, blp);

        // ---- 多選工具列 ----
        selBar = buildSelBar();
        selBar.setVisibility(View.GONE);
        root.addView(selBar, Ui.lpW());

        return root;
    }

    private ImageView iconBtn(int icon, int color, View.OnClickListener cl) {
        ImageView v = new ImageView(a);
        v.setImageDrawable(Icons.get(icon, color));
        int p = Ui.dp(a, 10);
        v.setPadding(p, p, p, p);
        v.setBackground(Ui.tappable(0x00000000, Ui.dp(a, 22)));
        v.setOnClickListener(cl);
        return v;
    }

    private ImageView boxedIcon(int icon, int color, View.OnClickListener cl) {
        ImageView v = new ImageView(a);
        v.setImageDrawable(Icons.get(icon, color));
        int p = Ui.dp(a, 11);
        v.setPadding(p, p, p, p);
        v.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 14), T.BORDER, Ui.dp(a, 1)));
        v.setOnClickListener(cl);
        return v;
    }

    private HorizontalScrollView scroller(View child) {
        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        sv.setClipToPadding(false);
        sv.addView(child, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private TextView chip(String label, boolean active, View.OnClickListener cl) {
        TextView t = Ui.tv(a, label, 13f, active ? 0xFF0B0E16 : T.DIM, true);
        t.setBackground(Ui.tappable(active ? T.PRIMARY : T.SURFACE2, Ui.dp(a, 16),
                active ? T.PRIMARY : T.BORDER, Ui.dp(a, 1)));
        t.setPadding(Ui.dp(a, 13), Ui.dp(a, 7), Ui.dp(a, 13), Ui.dp(a, 7));
        t.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 7);
        t.setLayoutParams(lp);
        return t;
    }

    // ---------- 多選 ----------

    private LinearLayout buildSelHead() {
        LinearLayout l = Ui.rowH(a);
        l.setBackgroundColor(T.SURFACE);
        l.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 8));
        l.addView(iconBtn(Icons.CLOSE, T.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { grid.exitSelect(); }
        }), new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        selCount = Ui.tv(a, "", 14f, T.TEXT, true);
        selCount.setMaxLines(1);
        LinearLayout.LayoutParams clp = Ui.lpWeight();
        clp.leftMargin = Ui.dp(a, 4);
        l.addView(selCount, clp);

        TextView all = miniChip("全選", T.PRIMARY);
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { grid.selectAll(); }
        });
        l.addView(all);

        TextView share = Ui.sharePill(a);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Share.items(a, grid.selectedItems()); }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(a, 8);
        l.addView(share, ilp);
        return l;
    }

    private LinearLayout buildSelBar() {
        LinearLayout row = Ui.rowH(a);
        row.setBackgroundColor(T.SURFACE);
        row.setPadding(Ui.dp(a, 10), Ui.dp(a, 9), Ui.dp(a, 10), Ui.dp(a, 11));
        row.addView(actionBtn("標為保留", T.KEEP, Db.STATE_KEPT), weightLp(true));
        row.addView(actionBtn("清除標記", T.DIM, Db.STATE_NONE), weightLp(true));
        TextView move = Ui.btnGhost(a, "移到資料夾", T.MOVE);
        slim(move);
        move.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveSelection(); }
        });
        row.addView(move, weightLp(true));
        TextView trash = Ui.btn(a, "加入待刪", T.TRASH, 0xFF1A0C0C);
        slim(trash);
        trash.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyToSelection(Db.STATE_PENDING); }
        });
        row.addView(trash, weightLp(false));
        return row;
    }

    private LinearLayout.LayoutParams weightLp(boolean gap) {
        LinearLayout.LayoutParams lp = Ui.lpWeight();
        if (gap) lp.rightMargin = Ui.dp(a, 6);
        return lp;
    }

    private void slim(TextView t) {
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        t.setPadding(Ui.dp(a, 2), Ui.dp(a, 11), Ui.dp(a, 2), Ui.dp(a, 11));
        t.setMaxLines(1);
    }

    private TextView actionBtn(String label, int color, final int state) {
        TextView t = Ui.btnGhost(a, label, color);
        slim(t);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyToSelection(state); }
        });
        return t;
    }

    private TextView miniChip(String label, int color) {
        TextView t = Ui.tv(a, label, 13f, color, true);
        t.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 13), T.BORDER, Ui.dp(a, 1)));
        t.setPadding(Ui.dp(a, 12), Ui.dp(a, 7), Ui.dp(a, 12), Ui.dp(a, 7));
        return t;
    }

    private void showSelectUi(boolean on) {
        head.setVisibility(on ? View.GONE : View.VISIBLE);
        selHead.setVisibility(on ? View.VISIBLE : View.GONE);
        chipScroll.setVisibility(on ? View.GONE : View.VISIBLE);
        yearScroll.setVisibility(on ? View.GONE : View.VISIBLE);
        monthScroll.setVisibility(on || selectedYear == 0 ? View.GONE : View.VISIBLE);
        summary.setVisibility(on ? View.GONE : View.VISIBLE);
        selBar.setVisibility(on ? View.VISIBLE : View.GONE);
        a.setNavVisible(!on);
        if (on) rebuildSidebar();
        animateSidebar(on);
        updateSelBar();
    }

    /**
     * 相簿欄從左側滑進來，照片牆同步被推到右邊；收起時反向。
     *
     * 只在開始（展開）或結束（收起）時排版一次，中間全部用 translationX 位移，交給 GPU 搬動已經畫好的畫面。
     * v1.3.0 以前是每一格畫面都改相簿欄寬度，照片牆每格都要整個重排（格子大小、日期字級、縮圖重新縮放），
     * 手機做不完就掉格，倬回報「動畫有點卡」。
     */
    private void animateSidebar(final boolean show) {
        final int full = sidebarWidth;
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sidebar.getLayoutParams();
        android.view.animation.Interpolator ip = new android.view.animation.DecelerateInterpolator(1.8f);
        sidebar.animate().cancel();
        gridWrap.animate().cancel();
        if (show) {
            float start = sidebarShown ? sidebar.getTranslationX() : -full;
            sidebarShown = true;
            if (lp.width != full) { lp.width = full; sidebar.setLayoutParams(lp); }   // 唯一一次排版
            sidebar.setVisibility(View.VISIBLE);
            sidebar.scrollTo(0, 0);
            sidebar.setTranslationX(start);
            gridWrap.setTranslationX(start);
            sidebar.animate().translationX(0).setDuration(240).setInterpolator(ip).start();
            gridWrap.animate().translationX(0).setDuration(240).setInterpolator(ip).start();
        } else {
            if (!sidebarShown) return;
            sidebarShown = false;
            sidebar.animate().translationX(-full).setDuration(220).setInterpolator(ip).start();
            gridWrap.animate().translationX(-full).setDuration(220).setInterpolator(ip)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (sidebarShown) return;   // 動畫途中又被打開
                            sidebar.setVisibility(View.GONE);
                            lp.width = 0;
                            sidebar.setLayoutParams(lp);   // 唯一一次排版：照片牆恢復全寬
                            sidebar.setTranslationX(0);
                            gridWrap.setTranslationX(0);
                        }
                    }).start();
        }
    }

    private void updateSelBar() {
        if (selCount == null) return;
        selCount.setText("已選 " + grid.selectedCount() + " 個　·　" + Item.formatSize(grid.selectedBytes()));
    }

    /** 左側相簿欄：最上面是「建立相簿」，下面是各相簿（封面＋名稱＋數量）。 */
    private void rebuildSidebar() {
        sidebarList.removeAllViews();
        TextView t = Ui.tv(a, "加入相簿", 12.5f, T.DIM, true);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), 0, Ui.dp(a, 8));
        sidebarList.addView(t);

        sidebarList.addView(sideCard(null), Ui.lpW());
        ArrayList<Albums.Album> albums = Albums.get().ordered();
        for (int i = 0; i < albums.size(); i++) sidebarList.addView(sideCard(albums.get(i)), Ui.lpW());
    }

    private View sideCard(final Albums.Album al) {
        LinearLayout card = Ui.col(a);
        card.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 12), T.BORDER, Ui.dp(a, 1)));
        card.setPadding(Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 5), Ui.dp(a, 8));
        LinearLayout.LayoutParams clp = Ui.lpW();
        clp.bottomMargin = Ui.dp(a, 8);
        card.setLayoutParams(clp);

        FrameLayout thumb = new FrameLayout(a);
        thumb.setBackground(Ui.round(T.SURFACE3, Ui.dp(a, 9)));
        thumb.setClipToOutline(true);
        int h = Ui.dp(a, al == null ? 46 : 78);   // 「建立相簿」做小一點，相簿欄能多放幾本
        ImageView iv = new ImageView(a);
        iv.setScaleType(al == null ? ImageView.ScaleType.CENTER : ImageView.ScaleType.CENTER_CROP);
        if (al == null) {
            iv.setImageDrawable(sized(Icons.PLUS, T.PRIMARY, 22));
        } else {
            Item cover = Albums.get().cover(al);
            if (cover != null) Thumbs.grid(iv, cover, 256);
            else iv.setImageDrawable(sized(al.isFav() ? Icons.STAR : Icons.ALBUM, T.FAINT, 28));
        }
        thumb.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        card.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        card.addView(Ui.space(a, 6));

        TextView name = Ui.tv(a, al == null ? "建立相簿" : al.name, 13f, al == null ? T.PRIMARY : T.TEXT, true);
        name.setMaxLines(2);
        name.setPadding(Ui.dp(a, 2), 0, Ui.dp(a, 2), 0);
        card.addView(name, Ui.lpW());
        if (al != null) {
            int[] pv = Albums.get().counts(al);
            TextView cnt = Ui.tv(a, (pv[0] + pv[1]) + " 項", 11f, T.FAINT, false);
            cnt.setPadding(Ui.dp(a, 2), Ui.dp(a, 3), 0, 0);
            card.addView(cnt);
        }
        attachDrop(card, al);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final ArrayList<Item> items = grid.selectedItems();
                if (items.isEmpty()) { Ui.toast(a, "先點選要加入的照片"); return; }
                if (al == null) {
                    AlbumDialogs.create(a, items, new AlbumDialogs.AlbumCallback() {
                        @Override public void onAlbum(Albums.Album x) { grid.exitSelect(); }
                    });
                } else {
                    AlbumDialogs.addAndReport(a, al, items);
                    grid.exitSelect();
                }
            }
        });
        return card;
    }

    private Drawable sized(int icon, int color, int dp) { return Ui.icon(a, icon, color, dp); }

    // ---------- 拖放：把選取的照片拖到相簿卡片上 ----------

    private void attachDrop(final View card, final Albums.Album al) {
        final Drawable normal = card.getBackground();
        card.setOnDragListener(new View.OnDragListener() {
            @Override public boolean onDrag(View v, android.view.DragEvent e) {
                switch (e.getAction()) {
                    case android.view.DragEvent.ACTION_DRAG_STARTED:
                        return e.getLocalState() instanceof DragStack.Payload;
                    case android.view.DragEvent.ACTION_DRAG_ENTERED:
                        // 外框畫在卡片範圍內，不再放大卡片（放大會超出相簿欄被裁掉）
                        v.setBackground(Ui.round(Ui.withAlpha(T.PRIMARY, 0x40), Ui.dp(a, 12), T.PRIMARY, Ui.dp(a, 2.5f)));
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                        return true;
                    case android.view.DragEvent.ACTION_DRAG_LOCATION:
                        autoScroll(v.getTop() - sidebar.getScrollY() + e.getY());
                        return true;
                    case android.view.DragEvent.ACTION_DRAG_EXITED:
                    case android.view.DragEvent.ACTION_DRAG_ENDED:
                        v.setBackground(normal);
                        return true;
                    case android.view.DragEvent.ACTION_DROP: {
                        v.setBackground(normal);
                        Object st = e.getLocalState();
                        if (!(st instanceof DragStack.Payload)) return false;
                        final ArrayList<Item> items = ((DragStack.Payload) st).items;
                        // 等系統把拖曳結束的動畫跑完再動畫面，避免重建側欄時打斷拖放
                        v.post(new Runnable() {
                            @Override public void run() {
                                if (al == null) {
                                    AlbumDialogs.create(a, items, new AlbumDialogs.AlbumCallback() {
                                        @Override public void onAlbum(Albums.Album x) { grid.exitSelect(); }
                                    });
                                } else {
                                    AlbumDialogs.addAndReport(a, al, items);
                                    grid.exitSelect();
                                }
                            }
                        });
                        return true;
                    }
                }
                return false;
            }
        });
    }

    /** 拖到相簿欄上下邊緣時自動捲動，才拖得到下面的相簿。y 為相簿欄可視範圍內的座標。 */
    private void autoScroll(float y) {
        int edge = Ui.dp(a, 70);
        int h = sidebar.getHeight();
        if (y < edge) sidebar.scrollBy(0, -Ui.dp(a, 14));
        else if (y > h - edge) sidebar.scrollBy(0, Ui.dp(a, 14));
    }

    private void applyToSelection(int state) {
        ArrayList<Item> items = grid.selectedItems();
        if (items.isEmpty()) { Ui.toast(a, "還沒選取任何照片"); return; }
        Store.get().setStates(a, items, state);
        String verb = state == Db.STATE_PENDING ? "已加入待刪清單 "
                : state == Db.STATE_KEPT ? "已標記為保留 " : "已清除標記 ";
        Ui.toast(a, verb + items.size() + " 個項目");
        grid.exitSelect();
        refresh(false);
    }

    /** 實體搬移到另一個資料夾（會改變檔案位置，需系統確認）。 */
    private void moveSelection() {
        final ArrayList<Item> items = grid.selectedItems();
        if (items.isEmpty()) { Ui.toast(a, "還沒選取任何照片"); return; }
        ArrayList<String[]> al = Store.get().albums();
        final ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < al.size(); i++) names.add(al.get(i)[0]);
        if (names.isEmpty()) { Ui.toast(a, "找不到可用的資料夾"); return; }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("把 " + items.size() + " 個項目的檔案搬到資料夾")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        a.moveToAlbum(items, names.get(w), null);
                        grid.exitSelect();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 篩選列 ----------

    private void rebuildChips() {
        chipBar.removeAllViews();
        final int[] states = { -1, Db.STATE_NONE, Db.STATE_KEPT, Db.STATE_PENDING };
        final String[] names = { "全部", "未整理", "已保留", "待刪" };
        for (int i = 0; i < states.length; i++) {
            final int s = states[i];
            chipBar.addView(chip(names[i], filter.stateWanted == s, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    filter.stateWanted = s;
                    refresh(true);
                }
            }));
        }
        chipBar.addView(chip("截圖", filter.onlyScreenshot, new View.OnClickListener() {
            @Override public void onClick(View v) { filter.onlyScreenshot = !filter.onlyScreenshot; refresh(true); }
        }));
        chipBar.addView(chip("LINE", filter.onlyLine, new View.OnClickListener() {
            @Override public void onClick(View v) { filter.onlyLine = !filter.onlyLine; refresh(true); }
        }));
        chipBar.addView(chip("影片", filter.onlyVideo, new View.OnClickListener() {
            @Override public void onClick(View v) { filter.onlyVideo = !filter.onlyVideo; refresh(true); }
        }));
        chipBar.addView(chip("大檔案", filter.onlyLarge, new View.OnClickListener() {
            @Override public void onClick(View v) { filter.onlyLarge = !filter.onlyLarge; refresh(true); }
        }));
        chipBar.addView(chip(filter.album == null ? "資料夾" : ("資料夾：" + filter.album),
                filter.album != null, new View.OnClickListener() {
            @Override public void onClick(View v) { pickFolder(); }
        }));
        String[] sortNames = { "最新", "最舊", "最大" };
        chipBar.addView(chip("排序：" + sortNames[filter.sort], false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                filter.sort = (filter.sort + 1) % 3;
                refresh(true);
            }
        }));
    }

    private void rebuildTimeline() {
        yearBar.removeAllViews();
        monthBar.removeAllViews();
        ArrayList<Integer> ys = Store.get().years(result);
        if (ys.isEmpty() || filter.sort == 2) {
            monthScroll.setVisibility(View.GONE);
            yearScroll.setVisibility(View.GONE);
            return;
        }
        yearScroll.setVisibility(grid.inSelect() ? View.GONE : View.VISIBLE);

        yearBar.addView(chip("全部年份", selectedYear == 0, new View.OnClickListener() {
            @Override public void onClick(View v) { selectedYear = 0; rebuildTimeline(); }
        }));
        for (int i = 0; i < ys.size(); i++) {
            final int y = ys.get(i).intValue();
            yearBar.addView(chip(y + " 年", selectedYear == y, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    selectedYear = (selectedYear == y) ? 0 : y;
                    rebuildTimeline();
                    if (selectedYear != 0) jumpTo(y * 100 + 13);
                }
            }));
        }
        if (selectedYear == 0) {
            monthScroll.setVisibility(View.GONE);
            return;
        }
        monthScroll.setVisibility(grid.inSelect() ? View.GONE : View.VISIBLE);
        boolean[] has = new boolean[13];
        for (int i = 0; i < result.size(); i++) {
            Item it = result.get(i);
            if (it.year == selectedYear && it.month >= 1 && it.month <= 12) has[it.month] = true;
        }
        for (int m = 12; m >= 1; m--) {
            if (!has[m]) continue;
            final int ym = selectedYear * 100 + m;
            monthBar.addView(chip(m + " 月", false, new View.OnClickListener() {
                @Override public void onClick(View v) { jumpTo(ym); }
            }));
        }
    }

    private void jumpTo(int ym) {
        if (!grid.jumpTo(ym)) Ui.toast(a, "這個範圍沒有符合目前篩選的照片");
    }

    private void pickFolder() {
        ArrayList<String[]> al = Store.get().albums();
        final ArrayList<String> names = new ArrayList<String>();
        names.add("全部資料夾");
        for (int i = 0; i < al.size(); i++) names.add(al.get(i)[0] + "（" + al.get(i)[1] + "）");
        final ArrayList<String> keys = new ArrayList<String>();
        keys.add(null);
        for (int i = 0; i < al.size(); i++) keys.add(al.get(i)[0]);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("只看某個資料夾")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        filter.album = keys.get(w);
                        refresh(true);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 月份標題的「逐張整理」：從那個月最新的一張打開單張檢視，範圍是目前照片牆上的全部（含篩選）。 */
    private void reviewMonth(int ym) {
        ArrayList<Item> items = grid.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).ym == ym) {
                a.reviewFrom(items.get(i), items, (ym / 100) + " 年 " + (ym % 100) + " 月起");
                return;
            }
        }
        Ui.toast(a, "這個月份沒有照片");
    }

    /** 從「更多 → 資料夾」跳過來時使用。 */
    public void applyAlbum(String folder) {
        filter.album = folder;
        if (search != null) search.setText("");
        refresh(true);
    }

    // ---------- 資料 ----------

    public void refresh(boolean rebuildChips) {
        if (root == null) return;
        result = Store.get().filter(filter);
        boolean bySize = filter.sort == 2;
        grid.setItems(result, !bySize, !bySize);
        if (rebuildChips) rebuildChips();
        rebuildTimeline();
        long bytes = 0;
        for (int i = 0; i < result.size(); i++) bytes += result.get(i).size;
        summary.setText("共 " + result.size() + " 個項目　·　" + Item.formatSize(bytes)
                + "　·　未整理 " + Store.get().count(Db.STATE_NONE)
                + "　·　待刪 " + Store.get().count(Db.STATE_PENDING));
        floatLabel.setVisibility(result.isEmpty() || bySize ? View.GONE : View.VISIBLE);
        if (grid.inSelect()) { rebuildSidebar(); updateSelBar(); }
    }

    // ---------- 生命週期 ----------

    @Override public void onShow() {
        if (grid != null) grid.changeColumns(Prefs.gridCols(a));   // 在相簿裡縮放過也同步
        refresh(true);
    }

    @Override public void onHide() { if (grid != null && grid.inSelect()) grid.exitSelect(); }

    @Override public boolean onBack() {
        if (grid != null && grid.inSelect()) { grid.exitSelect(); return true; }
        if (!filter.isDefault()) {
            filter.text = "";
            filter.stateWanted = -1;
            filter.onlyScreenshot = filter.onlyVideo = filter.onlyLarge = filter.onlyLine = false;
            filter.album = null;
            if (search != null) search.setText("");
            refresh(true);
            return true;
        }
        return false;
    }

    @Override public void onStoreChanged() {
        if (root != null) refresh(false);
    }
}
