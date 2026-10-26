package com.photobox.sorter;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 共用的照片牆：照片頁、相簿內頁、挑選照片都用它。
 *
 * - 排版交給 Flow（月份標題列＋每天第一張前的日期格）
 * - 長按進入多選；多選時點一下切換選取
 * - 格子大小跟著實際寬度算，所以左側出現相簿欄時會自動縮小重排
 */
public class MediaGrid extends FrameLayout {

    public interface Listener {
        void onOpen(Item it, ArrayList<Item> context);
        void onSelectionChanged(int count);
        /** 長按進入多選。回傳 false 表示這個畫面不支援多選。 */
        boolean onEnterSelect();
        void onExitSelect();
    }

    public interface HeaderAction {
        void onHeaderAction(int ym);
    }

    private int cols;
    public static final int MIN_COLS = 2, MAX_COLS = 7;
    private boolean pinchEnabled = true;
    private boolean dateSelectEnabled = true;

    /** 多選中長按「已選取」的項目時呼叫；回傳 true 表示已開始拖曳。 */
    public interface DragStarter { boolean onStartDrag(View source, ArrayList<Item> selected); }
    private DragStarter dragStarter;
    public void setDragStarter(DragStarter d) { dragStarter = d; }
    public void setDateSelectEnabled(boolean on) { dateSelectEnabled = on; }
    private OnColumnsChanged colsListener;

    public interface OnColumnsChanged { void onColumns(int cols); }
    private final ListView list;
    private final TextView empty;
    private final Adapter adapter = new Adapter();
    private ArrayList<Item> items = new ArrayList<Item>();
    private ArrayList<Flow.Row> rows = new ArrayList<Flow.Row>();
    private final HashSet<Long> selected = new HashSet<Long>();
    private boolean selectMode = false;
    private boolean pickMode = false;   // 挑選模式：一開始就是多選，點一下就是選
    private boolean stateDots = true;
    private Listener listener;
    private String headerActionLabel;
    private HeaderAction headerAction;
    private int cell = 0;
    private int lastWidth = 0;
    private OnScrollYm scrollYm;

    public interface OnScrollYm { void onYm(int ym); }

    public MediaGrid(Context c, int cols) {
        super(c);
        this.cols = cols;
        list = new ListView(c);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setScrollingCacheEnabled(false);
        // 內建快速捲動軸會吃掉最右邊一欄的點擊，改用年／月跳轉與浮動標籤
        list.setFastScrollEnabled(false);
        list.setVerticalScrollBarEnabled(true);
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 12));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) { scrollState = s; }
            @Override public void onScroll(AbsListView v, int first, int vis, int total) {
                if (scrollYm == null) return;
                int idx = first - list.getHeaderViewsCount();
                if (idx >= 0 && idx < rows.size()) scrollYm.onYm(rows.get(idx).ym);
            }
        });
        addView(list, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setupGestures(c);

        empty = Ui.tv(c, "沒有任何項目", 15f, T.FAINT, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 必須在 setItems 之前呼叫（ListView 的限制）。 */
    public void addHeader(View v) {
        list.addHeaderView(v, null, false);
    }

    public void setListener(Listener l) { listener = l; }
    public void setHeaderAction(String label, HeaderAction a) { headerActionLabel = label; headerAction = a; }
    public void setStateDots(boolean on) { stateDots = on; }
    public void setOnScrollYm(OnScrollYm l) { scrollYm = l; }
    public void setEmptyText(String s) { empty.setText(s); }
    public void setEmptyVisibleWithHeader(boolean b) { emptyWithHeader = b; }
    private boolean emptyWithHeader = false;

    public void setPickMode(boolean on) {
        pickMode = on;
        selectMode = on;
        list.setAdapter(null);
        list.setAdapter(adapter);
    }

    public ListView listView() { return list; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w != lastWidth) {
            lastWidth = w;
            computeCell(w);
            post(new Runnable() { @Override public void run() { adapter.notifyDataSetChanged(); } });
        }
    }

    private void computeCell(int w) {
        int gap = Ui.dp(getContext(), 3);
        int inner = w - list.getPaddingLeft() - list.getPaddingRight();
        cell = Math.max(Ui.dp(getContext(), 40), (inner - gap * (cols - 1)) / cols);
    }

    /**
     * @param monthHeaders 月份標題整列
     * @param dateCells    每天第一張前插入日期格
     */
    private boolean lastMonthHeaders, lastDateCells;
    /** yyyymmdd → 那一天的項目 id（點日期格一次選整天用）。 */
    private final java.util.HashMap<Integer, ArrayList<Long>> dayIds = new java.util.HashMap<Integer, ArrayList<Long>>();

    public void setItems(List<Item> src, boolean monthHeaders, boolean dateCells) {
        lastMonthHeaders = monthHeaders;
        lastDateCells = dateCells;
        items = new ArrayList<Item>(src);
        dayIds.clear();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (int i = 0; i < items.size(); i++) {
            cal.setTimeInMillis(items.get(i).timeMs);
            int k = cal.get(java.util.Calendar.YEAR) * 10000 + (cal.get(java.util.Calendar.MONTH) + 1) * 100
                    + cal.get(java.util.Calendar.DAY_OF_MONTH);
            ArrayList<Long> l = dayIds.get(k);
            if (l == null) { l = new ArrayList<Long>(); dayIds.put(k, l); }
            l.add(items.get(i).id);
        }
        if (!monthHeaders && !dateCells) {
            rows = Flow.plain(items.size(), cols);
        } else {
            long[] t = new long[items.size()];
            for (int i = 0; i < t.length; i++) t[i] = items.get(i).timeMs;
            rows = Flow.build(t, cols, monthHeaders, dateCells);
        }
        // 已不存在的選取一併移除
        if (!selected.isEmpty()) {
            HashSet<Long> live = new HashSet<Long>();
            for (int i = 0; i < items.size(); i++) live.add(items.get(i).id);
            selected.retainAll(live);
        }
        if (list.getAdapter() == null) list.setAdapter(adapter);
        else adapter.notifyDataSetChanged();
        boolean noItems = items.isEmpty();
        empty.setVisibility(noItems && (emptyWithHeader || list.getHeaderViewsCount() == 0) ? VISIBLE : GONE);
    }

    public ArrayList<Item> items() { return items; }

    // ---------- 多選 ----------

    public boolean inSelect() { return selectMode; }

    /** 用「空的選取」進入多選（例如往右滑開相簿欄）時，全部取消也不自動離開多選。 */
    private boolean sticky = false;

    /** 水平滑動：1 往右滑、-1 往左滑。 */
    public interface SwipeListener { void onSwipe(int dir); }
    private SwipeListener swipeListener;
    public void setSwipeListener(SwipeListener l) { swipeListener = l; }

    /**
     * 跟著手指的水平拖曳（照片頁拉出相簿欄用）。設定之後就不再走 SwipeListener 的「放開才判斷」。
     * onHDragStart 回傳 false 表示這次不接手（例如已經打開還往右拉），事件照常交給清單捲動。
     * dx 是從按下到現在的水平位移（螢幕座標，不受照片牆本身位移影響）；vx 是放開時的速度（px/s）。
     */
    public interface HDragListener {
        boolean onHDragStart(int dir);
        void onHDrag(float dx);
        void onHDragEnd(float dx, float vx);
    }
    private HDragListener hDragListener;
    public void setHDragListener(HDragListener l) { hDragListener = l; }

    private float hDownX, hDownY;
    /** 0＝還沒判斷、1＝水平拖曳中（已接手）、2＝不是水平拖曳（交給清單） */
    private int hState = 0;
    private android.view.VelocityTracker hVt;

    public void enterSelect(Item first) {
        sticky = first == null;
        if (listener != null && !listener.onEnterSelect()) return;
        selectMode = true;
        selected.clear();
        if (first != null) selected.add(first.id);
        adapter.notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selected.size());
    }

    public void exitSelect() {
        if (pickMode) { selected.clear(); adapter.notifyDataSetChanged(); fire(); return; }
        selectMode = false;
        selected.clear();
        adapter.notifyDataSetChanged();
        if (listener != null) listener.onExitSelect();
    }

    public void selectAll() {
        for (int i = 0; i < items.size(); i++) selected.add(items.get(i).id);
        adapter.notifyDataSetChanged();
        fire();
    }

    public boolean allSelected() { return !items.isEmpty() && selected.size() >= items.size(); }

    private void fire() { if (listener != null) listener.onSelectionChanged(selected.size()); }

    public ArrayList<Item> selectedItems() {
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < items.size(); i++) if (selected.contains(items.get(i).id)) out.add(items.get(i));
        return out;
    }

    public long selectedBytes() {
        long b = 0;
        for (int i = 0; i < items.size(); i++) if (selected.contains(items.get(i).id)) b += items.get(i).size;
        return b;
    }

    public int selectedCount() { return selected.size(); }

    public void refreshCells() { adapter.notifyDataSetChanged(); }

    // ---------- 捲動 ----------

    /** ym 傳 y*100+13 代表跳到那一年的第一筆。回傳是否找到。 */
    public boolean jumpTo(int ym) {
        int ty = ym / 100, tm = ym % 100;
        for (int i = 0; i < rows.size(); i++) {
            int r = rows.get(i).ym;
            if (r == 0) continue;
            boolean hit = tm == 13 ? (r / 100 == ty) : (r == ym);
            if (hit) {
                list.setSelectionFromTop(i + list.getHeaderViewsCount(), 0);
                return true;
            }
        }
        return false;
    }

    // ---------- Adapter ----------

    private static final int ID_IMG = 10, ID_DOT = 11, ID_PLAY = 12, ID_SEL = 13, ID_CHECK = 14,
            ID_DATE = 20, ID_D_YM = 21, ID_D_DAY = 22, ID_D_WEEK = 23;

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return rows.get(p).type; }

        @Override public View getView(int pos, View cv, ViewGroup parent) {
            Flow.Row r = rows.get(pos);
            return r.type == Flow.ROW_HEADER ? header(r, cv) : cells(r, cv);
        }
    }

    private View header(Flow.Row r, View cv) {
        Context c = getContext();
        LinearLayout v = (LinearLayout) cv;
        if (v == null) {
            v = Ui.rowH(c);
            v.setPadding(Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 8));
            TextView title = Ui.tv(c, "", 16f, T.TEXT, true);
            title.setId(1);
            v.addView(title);
            TextView cnt = Ui.tv(c, "", 12.5f, T.FAINT, false);
            cnt.setId(2);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.leftMargin = Ui.dp(c, 8);
            v.addView(cnt, clp);
            v.addView(Ui.grow(c));
            TextView go = Ui.tv(c, "", 12.5f, T.PRIMARY, true);
            go.setId(3);
            go.setBackground(Ui.tappable(Ui.withAlpha(T.PRIMARY, 0x22), Ui.dp(c, 12)));
            go.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
            v.addView(go);
        }
        ((TextView) v.findViewById(1)).setText((r.ym / 100) + " 年 " + (r.ym % 100) + " 月");
        ((TextView) v.findViewById(2)).setText(r.count + " 項");
        TextView go = (TextView) v.findViewById(3);
        final int ym = r.ym;
        boolean show = headerAction != null && !selectMode;
        go.setVisibility(show ? VISIBLE : GONE);
        go.setText(headerActionLabel == null ? "" : headerActionLabel);
        go.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View x) { if (headerAction != null) headerAction.onHeaderAction(ym); }
        });
        return v;
    }

    private View cells(Flow.Row r, View cv) {
        final Context c = getContext();
        if (cell <= 0) computeCell(lastWidth > 0 ? lastWidth : c.getResources().getDisplayMetrics().widthPixels);
        LinearLayout v = (LinearLayout) cv;
        if (v == null) {
            v = Ui.rowH(c);
            v.setPadding(0, 0, 0, Ui.dp(c, 3));
            for (int i = 0; i < cols; i++) v.addView(buildCell(c));
        }
        int gap = Ui.dp(c, 3);
        for (int i = 0; i < cols; i++) {
            FrameLayout f = (FrameLayout) v.getChildAt(i);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) f.getLayoutParams();
            // 用權重平均分配這一列的實際寬度（格子再自己變成正方形），
            // 不依賴事先算好的像素寬度：左側相簿欄出現、寬度改變時也不會把最右一欄擠出畫面
            if (lp == null || lp.weight != 1f || lp.width != 0) {
                lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < cols - 1) lp.rightMargin = gap;
                f.setLayoutParams(lp);
            }
            bindCell(f, r.cells[i], r.dayKeys[i]);
        }
        return v;
    }

    /** 高度永遠等於寬度的格子。 */
    /**
     * 高度永遠等於寬度的格子；並依「實際格子大小」等比例調整勾選圓圈、影片圖示與日期格字級，
     * 每列張數變多（格子變小）時不會擠爆或被裁掉。
     */
    static class SquareFrame extends FrameLayout {
        private int scaledFor = -1;
        SquareFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int size = MeasureSpec.getSize(w);
            int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            super.onMeasure(exact, exact);
        }
        @Override protected void onSizeChanged(final int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || w == scaledFor) return;
            scaledFor = w;
            post(new Runnable() { @Override public void run() { applyScale(w); } });
        }

        private void applyScale(int px) {
            float d = getResources().getDisplayMetrics().density;
            // 勾選圓圈：約格子的 22%，限制在 11～24dp；貼齊右下角
            int ck = (int) Math.max(11 * d, Math.min(24 * d, px * 0.22f));
            int margin = (int) Math.max(2 * d, px * 0.04f);
            View check = findViewById(ID_CHECK);
            if (check != null) {
                LayoutParams lp = (LayoutParams) check.getLayoutParams();
                lp.width = lp.height = ck;
                lp.rightMargin = lp.bottomMargin = margin;
                int pad = Math.max(1, ck / 6);
                check.setPadding(pad, pad, pad, pad);
                check.setLayoutParams(lp);
            }
            View play = findViewById(ID_PLAY);
            if (play != null) {
                int ps = (int) Math.max(13 * d, Math.min(20 * d, px * 0.18f));
                LayoutParams lp = (LayoutParams) play.getLayoutParams();
                lp.width = lp.height = ps;
                lp.leftMargin = lp.bottomMargin = margin;
                int pad = Math.max(1, ps / 7);
                play.setPadding(pad, pad, pad, pad);
                play.setLayoutParams(lp);
            }
            View dot = findViewById(ID_DOT);
            if (dot != null) {
                int ds = (int) Math.max(6 * d, Math.min(9 * d, px * 0.08f));
                LayoutParams lp = (LayoutParams) dot.getLayoutParams();
                lp.width = lp.height = ds;
                lp.leftMargin = lp.topMargin = margin;
                dot.setLayoutParams(lp);
            }
            // 日期格：三行字依格子高度分配（年/月 18%、日 32%、星期 18%），確保整格放得下
            TextView ym = (TextView) findViewById(ID_D_YM);
            TextView day = (TextView) findViewById(ID_D_DAY);
            TextView wk = (TextView) findViewById(ID_D_WEEK);
            if (ym != null && day != null && wk != null) {
                float small = Math.min(px * 0.17f, 12.5f * getResources().getDisplayMetrics().scaledDensity);
                float big = Math.min(px * 0.32f, 28f * getResources().getDisplayMetrics().scaledDensity);
                ym.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, small);
                wk.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, small);
                day.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, big);
                ViewGroup date = (ViewGroup) ym.getParent();
                int gap = (int) (px * 0.035f);
                for (int i = 0; i < date.getChildCount(); i++) {
                    View ch = date.getChildAt(i);
                    if (ch instanceof TextView) continue;
                    ViewGroup.LayoutParams lp = ch.getLayoutParams();
                    lp.height = gap;
                    ch.setLayoutParams(lp);
                }
            }
        }
    }

    private FrameLayout buildCell(Context c) {
        FrameLayout f = new SquareFrame(c);
        f.setBackgroundColor(T.SURFACE2);

        ImageView iv = new ImageView(c);
        iv.setId(ID_IMG);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View dot = new View(c);
        dot.setId(ID_DOT);
        LayoutParams dlp = new LayoutParams(Ui.dp(c, 9), Ui.dp(c, 9));
        dlp.gravity = Gravity.TOP | Gravity.START;
        dlp.topMargin = Ui.dp(c, 5);
        dlp.leftMargin = Ui.dp(c, 5);
        f.addView(dot, dlp);

        ImageView play = new ImageView(c);
        play.setId(ID_PLAY);
        play.setImageDrawable(Icons.get(Icons.PLAY, 0xFFFFFFFF));
        play.setBackground(oval(0x8C000000, 0, 0));
        int pp = Ui.dp(c, 3);
        play.setPadding(pp, pp, pp, pp);
        LayoutParams plp = new LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
        plp.gravity = Gravity.BOTTOM | Gravity.START;
        plp.leftMargin = Ui.dp(c, 5);
        plp.bottomMargin = Ui.dp(c, 5);
        f.addView(play, plp);

        View sel = new View(c);
        sel.setId(ID_SEL);
        f.addView(sel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView check = new ImageView(c);
        check.setId(ID_CHECK);
        int cp = Ui.dp(c, 3);
        check.setPadding(cp, cp, cp, cp);
        LayoutParams klp = new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
        klp.gravity = Gravity.BOTTOM | Gravity.END;
        klp.rightMargin = Ui.dp(c, 5);
        klp.bottomMargin = Ui.dp(c, 5);
        f.addView(check, klp);

        // 日期格
        LinearLayout date = Ui.col(c);
        date.setId(ID_DATE);
        date.setGravity(Gravity.CENTER);
        date.setBackgroundColor(T.SURFACE);
        TextView ym = Ui.tv(c, "", 11.5f, T.DIM, false);
        ym.setId(ID_D_YM);
        TextView day = Ui.tv(c, "", 26f, T.TEXT, true);
        day.setId(ID_D_DAY);
        TextView wk = Ui.tv(c, "", 11.5f, T.DIM, false);
        wk.setId(ID_D_WEEK);
        date.addView(ym);
        date.addView(Ui.space(c, 3));
        date.addView(day);
        date.addView(Ui.space(c, 3));
        date.addView(wk);
        f.addView(date, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return f;
    }

    private static android.graphics.drawable.GradientDrawable oval(int fill, int stroke, int strokePx) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(fill);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    private void bindCell(FrameLayout f, int idx, int dayKey) {
        final Context c = getContext();
        ImageView iv = (ImageView) f.findViewById(ID_IMG);
        View dot = f.findViewById(ID_DOT);
        View play = f.findViewById(ID_PLAY);
        View sel = f.findViewById(ID_SEL);
        ImageView check = (ImageView) f.findViewById(ID_CHECK);
        View date = f.findViewById(ID_DATE);

        if (idx == Flow.EMPTY) {
            f.setVisibility(INVISIBLE);
            iv.setImageBitmap(null);
            iv.setTag(null);
            return;
        }
        f.setVisibility(VISIBLE);
        if (idx == Flow.DATE) {
            iv.setImageBitmap(null);
            iv.setTag(null);
            iv.setVisibility(GONE);
            dot.setVisibility(GONE);
            play.setVisibility(GONE);
            sel.setBackgroundColor(0);
            check.setVisibility(GONE);
            date.setVisibility(VISIBLE);
            ((TextView) f.findViewById(ID_D_YM)).setText((dayKey / 10000) + "/" + ((dayKey / 100) % 100));
            TextView dayTv = (TextView) f.findViewById(ID_D_DAY);
            dayTv.setText(String.valueOf(dayKey % 100));
            ((TextView) f.findViewById(ID_D_WEEK)).setText(Flow.weekday(dayKey));
            // 多選中：整天都選了的日期格用主色標示，提示「再點一次可取消整天」
            ArrayList<Long> ids = dayIds.get(dayKey);
            boolean whole = selectMode && ids != null && !ids.isEmpty() && selected.containsAll(ids);
            date.setBackgroundColor(whole ? Ui.withAlpha(T.PRIMARY, 0x55) : T.SURFACE);
            dayTv.setTextColor(whole ? 0xFFFFFFFF : T.TEXT);
            return;
        }

        final Item it = items.get(idx);
        date.setVisibility(GONE);
        iv.setVisibility(VISIBLE);
        Thumbs.grid(iv, it, thumbPx());

        int st = stateDots ? Store.get().stateOf(it) : Db.STATE_NONE;
        if (st == Db.STATE_PENDING) {
            dot.setVisibility(VISIBLE);
            dot.setBackground(oval(T.TRASH, 0xCC000000, Ui.dp(c, 1)));
        } else if (st == Db.STATE_KEPT) {
            dot.setVisibility(VISIBLE);
            dot.setBackground(oval(T.KEEP, 0xCC000000, Ui.dp(c, 1)));
        } else {
            dot.setVisibility(GONE);
        }
        play.setVisibility(it.video ? VISIBLE : GONE);

        boolean on = selected.contains(it.id);
        sel.setBackgroundColor(on ? Ui.withAlpha(T.PRIMARY, 0x66) : 0);
        if (selectMode) {
            check.setVisibility(VISIBLE);
            // 參考常用相簿 App：未選是半透明灰底＋淡色勾，已選是主色實心＋深色勾；都是正圓，大小跟著格子縮放
            check.setImageDrawable(Icons.get(Icons.CHECK, on ? 0xFF0B0E16 : 0xCCFFFFFF));
            check.setBackground(on ? oval(T.PRIMARY, 0xFFFFFFFF, Ui.dp(c, 1.2f))
                    : oval(0x66000000, 0xB3FFFFFF, Ui.dp(c, 1f)));
        } else {
            check.setVisibility(GONE);
        }

        f.setTag(Integer.valueOf(idx));
    }

    // ---------- 觸控：點選、長按、雙指縮放 ----------
    //
    // 不在每個格子上掛 OnClickListener，而是由整個照片牆統一判斷「手指落在哪一格」：
    // 1) 點到格子的任何位置都算（不必點到右下角的圓圈）
    // 2) 點在格子間的縫隙或左右邊緣的留白，會歸到最近的那一格（手機側邊感應較差時也點得到）
    // 3) 雙指縮放時取消清單捲動，避免縮放和捲動打架

    private GestureDetector gestures;
    private ScaleGestureDetector scaler;
    private boolean multiTouch = false;
    private int scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
    private boolean downWhileFlinging = false;
    private boolean cancelledChild = false;
    private float scaleAcc = 1f;

    private void setupGestures(Context c) {
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (swipeListener == null || hDragListener != null || e1 == null || multiTouch) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                // 明顯的水平滑動才算，避免和上下捲動混淆
                if (Math.abs(dx) < Ui.dp(getContext(), 80) || Math.abs(dx) < Math.abs(dy) * 1.8f) return false;
                if (Math.abs(vx) < Ui.dp(getContext(), 300)) return false;
                swipeListener.onSwipe(dx > 0 ? 1 : -1);
                return true;
            }

            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (downWhileFlinging) return false;
                int[] h = hitCell(e.getX(), e.getY());
                if (h != null && h[0] == Flow.DATE && dateSelectEnabled) { selectDay(h[1]); return true; }
                int idx = h != null && h[0] >= 0 && h[0] < items.size() ? h[0] : -1;
                if (idx < 0) return false;
                Item it = items.get(idx);
                if (selectMode) {
                    if (selected.contains(it.id)) selected.remove(it.id); else selected.add(it.id);
                    if (selected.isEmpty() && !pickMode && !sticky) { exitSelect(); return true; }
                    adapter.notifyDataSetChanged();
                    fire();
                } else if (listener != null) {
                    listener.onOpen(it, items);
                }
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (multiTouch || downWhileFlinging) return;
                int idx = hitItem(e.getX(), e.getY());
                if (idx < 0) return;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Item it = items.get(idx);
                if (!selectMode) { enterSelect(it); return; }
                // 多選中長按「已選取」的照片：把選取的整疊拖到相簿欄
                if (selected.contains(it.id) && dragStarter != null
                        && dragStarter.onStartDrag(MediaGrid.this, selectedItems())) return;
                rangeSelectTo(it);   // 長按未選取的：把最近的已選到這一格之間全部選起來
            }
        });
        gestures.setIsLongpressEnabled(true);

        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { scaleAcc = 1f; return pinchEnabled; }
            @Override public boolean onScale(ScaleGestureDetector d) {
                scaleAcc *= d.getScaleFactor();
                // 張開手指 → 照片變大、每列變少；捏合 → 每列變多
                if (scaleAcc > 1.22f) { scaleAcc = 1f; changeColumns(cols - 1); }
                else if (scaleAcc < 0.82f) { scaleAcc = 1f; changeColumns(cols + 1); }
                return true;
            }
        });
    }

    /** 水平拖曳：判斷、接手、回報。回傳 true 表示這個事件已被拖曳用掉。 */
    private boolean handleHDrag(MotionEvent ev) {
        if (hDragListener == null) return false;
        int action = ev.getActionMasked();
        float rx = ev.getRawX(), ry = ev.getRawY();
        if (action == MotionEvent.ACTION_DOWN) {
            hDownX = rx;
            hDownY = ry;
            hState = 0;
            if (hVt != null) hVt.recycle();
            hVt = android.view.VelocityTracker.obtain();
        }
        if (hVt != null) {
            MotionEvent m = MotionEvent.obtain(ev);
            m.setLocation(rx, ry);
            hVt.addMovement(m);
            m.recycle();
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN && hState != 1) { hState = 2; return false; }
        float dx = rx - hDownX, dy = ry - hDownY;
        if (hState == 0 && action == MotionEvent.ACTION_MOVE && !multiTouch) {
            float slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) hState = 2;       // 上下捲動
            else if (Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                if (hDragListener.onHDragStart(dx > 0 ? 1 : -1)) {
                    hState = 1;
                    // 接手：取消清單的捲動、長按與點擊
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    gestures.onTouchEvent(cancel);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    hDownX = rx;   // 從接手的位置開始算，畫面才不會一下子跳一段
                    dx = 0;
                } else {
                    hState = 2;
                }
            }
        }
        if (hState != 1) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            hDragListener.onHDrag(dx);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float vx = 0;
            if (hVt != null && action == MotionEvent.ACTION_UP) {
                hVt.computeCurrentVelocity(1000);
                vx = hVt.getXVelocity();
            }
            hState = 0;
            hDragListener.onHDragEnd(dx, vx);
        }
        return true;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (handleHDrag(ev)) return true;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            multiTouch = false;
            cancelledChild = false;
            // 清單正在慣性捲動時，這一下只是「停住」，不要當成點開照片
            downWhileFlinging = scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING;
        }
        if (pinchEnabled) scaler.onTouchEvent(ev);
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true;
            MotionEvent cancel = MotionEvent.obtain(ev);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            gestures.onTouchEvent(cancel);
            if (!cancelledChild) { super.dispatchTouchEvent(cancel); cancelledChild = true; }
            cancel.recycle();
        }
        if (multiTouch) {
            return true;   // 縮放期間不把事件交給清單，結束（全部手指離開）後恢復
        }
        gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** 手指座標（相對於照片牆）→ 項目索引；不是項目（日期格、標題、空白）回傳 -1。 */
    private int hitItem(float x, float y) {
        int[] h = hitCell(x, y);
        if (h == null) return -1;
        return h[0] >= 0 && h[0] < items.size() ? h[0] : -1;
    }

    /** @return {格子內容（項目索引、Flow.DATE、Flow.EMPTY）, 日期格的 yyyymmdd}；不在格子列上回傳 null。 */
    private int[] hitCell(float x, float y) {
        int headers = list.getHeaderViewsCount();
        int first = list.getFirstVisiblePosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (y < row.getTop() + list.getTop() || y >= row.getBottom() + list.getTop()) continue;
            int pos = first + i - headers;
            if (pos < 0 || pos >= rows.size()) return null;
            Flow.Row r = rows.get(pos);
            if (r.type != Flow.ROW_CELLS) return null;
            // 列內最後一點縫隙（列與列之間）也算進這一列
            int gap = Ui.dp(getContext(), 3);
            float rx = x - list.getLeft() - list.getPaddingLeft();
            float inner = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
            float colW = (inner + gap) / cols;   // 每格寬＋右側縫隙
            int col = (int) Math.floor(rx / colW);
            if (col < 0) col = 0;
            if (col >= cols) col = cols - 1;
            return new int[]{ r.cells[col], r.dayKeys[col] };
        }
        return null;
    }

    /**
     * 點日期格：一次選取那一天的全部項目（沒在多選中就先進入多選）。
     * 那一天已經全部選取時，再點一次就全部取消。
     */
    private void selectDay(int dayKey) {
        ArrayList<Long> ids = dayIds.get(dayKey);
        if (ids == null) return;
        if (ids.isEmpty()) return;
        if (!selectMode) {
            if (listener != null && !listener.onEnterSelect()) return;
            selectMode = true;
            selected.clear();
        }
        boolean all = selected.containsAll(ids);
        if (all) selected.removeAll(ids); else selected.addAll(ids);
        if (selected.isEmpty() && !pickMode && !sticky) { exitSelect(); return; }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        adapter.notifyDataSetChanged();
        fire();
    }

    // ---------- 每列張數 ----------

    public void setPinchEnabled(boolean on) { pinchEnabled = on; }
    public void setOnColumnsChanged(OnColumnsChanged l) { colsListener = l; }
    public int columns() { return cols; }

    /** 改變每列張數，並盡量讓目前畫面最上方那張照片留在原位。 */
    public void changeColumns(int n) {
        if (n < MIN_COLS) n = MIN_COLS;
        if (n > MAX_COLS) n = MAX_COLS;
        if (n == cols) return;
        int anchorItem = firstVisibleItem();
        cols = n;
        computeCell(lastWidth > 0 ? lastWidth : getWidth());
        setItems(items, lastMonthHeaders, lastDateCells);
        list.setAdapter(adapter);   // 換欄數時舊的列 View 不能重用
        if (anchorItem >= 0) {
            for (int i = 0; i < rows.size(); i++) {
                Flow.Row r = rows.get(i);
                if (r.type != Flow.ROW_CELLS) continue;
                boolean hit = false;
                for (int k = 0; k < r.cells.length; k++) if (r.cells[k] == anchorItem) hit = true;
                if (hit) { list.setSelectionFromTop(i + list.getHeaderViewsCount(), 0); break; }
            }
        }
        Prefs.setGridCols(getContext(), cols);
        if (colsListener != null) colsListener.onColumns(cols);
    }

    private int firstVisibleItem() {
        int headers = list.getHeaderViewsCount();
        int first = list.getFirstVisiblePosition() - headers;
        for (int i = Math.max(0, first); i < rows.size() && i < first + 12; i++) {
            Flow.Row r = rows.get(i);
            if (r.type != Flow.ROW_CELLS) continue;
            for (int k = 0; k < r.cells.length; k++) if (r.cells[k] >= 0) return r.cells[k];
        }
        return -1;
    }

    /** 縮圖解析度跟著格子大小走，欄數少時不會糊。 */
    private int thumbPx() {
        if (cell <= 256) return 256;
        if (cell <= 384) return 384;
        return 512;
    }

    /** 多選時長按：從目前最接近的已選項目，連續選到這一格（方便一次選一整段）。 */
    private void rangeSelectTo(Item target) {
        int t = items.indexOf(target);
        if (t < 0) return;
        int anchor = -1, best = Integer.MAX_VALUE;
        for (int i = 0; i < items.size(); i++) {
            if (!selected.contains(items.get(i).id)) continue;
            int d = Math.abs(i - t);
            if (d < best) { best = d; anchor = i; }
        }
        if (anchor < 0) { selected.add(target.id); }
        else {
            int lo = Math.min(anchor, t), hi = Math.max(anchor, t);
            for (int i = lo; i <= hi; i++) selected.add(items.get(i).id);
        }
        adapter.notifyDataSetChanged();
        fire();
    }
}
