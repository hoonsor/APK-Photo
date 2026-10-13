package com.photobox.sorter;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** 重複與相似照片偵測。所有運算都在本機完成。 */
public class DupActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;
    private volatile boolean scanning = false;

    private LinearLayout introBox, scanBox;
    private ListView list;
    private GroupAdapter adapter;
    private ProgressBar bar;
    private TextView scanText, introText, bottomText, startBtn;
    private LinearLayout bottomBar;

    private final ArrayList<ArrayList<Item>> groups = new ArrayList<ArrayList<Item>>();
    private final HashSet<Long> selected = new HashSet<Long>();
    private int thumbPx;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        thumbPx = Ui.dp(this, 96);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(T.BG);
        root.setFitsSystemWindows(true);

        // 標題列
        LinearLayout head = Ui.rowH(this);
        head.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        ImageView back = new ImageView(this);
        back.setImageDrawable(Icons.get(Icons.BACK, T.TEXT));
        int p = Ui.dp(this, 10);
        back.setPadding(p, p, p, p);
        back.setBackground(Ui.ripple(new android.graphics.drawable.ColorDrawable(0), 0x33FFFFFF));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        TextView t = Ui.tv(this, "重複與相似照片", 18f, T.TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dp(this, 4);
        head.addView(t, tlp);
        root.addView(head, Ui.lpW());

        FrameLayout body = new FrameLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        blp.weight = 1f;
        root.addView(body, blp);

        // 說明 / 開始
        ScrollView sc = new ScrollView(this);
        introBox = Ui.col(this);
        introBox.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 20));
        introText = Ui.tv(this,
                "會比對每一張照片的內容特徵，把連拍、重複儲存、修圖前後這類「長得幾乎一樣」的照片分組列出來。\n\n"
              + "第一次掃描需要讀取每張照片的縮圖，張數多時會花一點時間；結果會存起來，之後再掃就很快。\n\n"
              + "分析全部在手機本機完成，不會上傳任何東西。", 14.5f, T.DIM, false);
        introText.setLineSpacing(Ui.dp(this, 5), 1f);
        introBox.addView(introText, Ui.lpW());
        introBox.addView(Ui.space(this, 22));
        startBtn = Ui.btn(this, "開始掃描", T.PRIMARY, 0xFF0B0E16);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startScan(); }
        });
        introBox.addView(startBtn, Ui.lpW());
        sc.addView(introBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 掃描中
        scanBox = Ui.col(this);
        scanBox.setGravity(Gravity.CENTER);
        scanBox.setPadding(Ui.dp(this, 30), 0, Ui.dp(this, 30), 0);
        scanText = Ui.tv(this, "", 15f, T.TEXT, true);
        scanText.setGravity(Gravity.CENTER);
        scanBox.addView(scanText, Ui.lpW());
        scanBox.addView(Ui.space(this, 16));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(T.PRIMARY));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(T.SURFACE3));
        scanBox.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 5)));
        scanBox.addView(Ui.space(this, 20));
        TextView cancel = Ui.btnGhost(this, "取消掃描", T.DIM);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancelled = true; }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(this, 160),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        scanBox.addView(cancel, clp);
        scanBox.setVisibility(View.GONE);
        body.addView(scanBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 結果
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        adapter = new GroupAdapter();
        list.setAdapter(adapter);
        list.setVisibility(View.GONE);
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部
        bottomBar = Ui.rowH(this);
        bottomBar.setBackgroundColor(T.SURFACE);
        bottomBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 14));
        bottomText = Ui.tv(this, "", 13f, T.DIM, false);
        bottomBar.addView(bottomText, Ui.lpWeight());
        TextView apply = Ui.btn(this, "加入待刪清單", T.TRASH, 0xFF1A0C0C);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applySelection(); }
        });
        bottomBar.addView(apply);
        bottomBar.setVisibility(View.GONE);
        root.addView(bottomBar, Ui.lpW());

        setContentView(root);
    }

    @Override protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }

    // ---------- 掃描 ----------

    private void startScan() {
        if (scanning) return;
        final ArrayList<Item> src = new ArrayList<Item>();
        ArrayList<Item> all = Store.get().all;
        for (int i = 0; i < all.size(); i++) {
            if (!all.get(i).video) src.add(all.get(i));
        }
        if (src.size() < 2) { Ui.toast(this, "照片太少，不用比對"); return; }

        scanning = true;
        cancelled = false;
        introBox.setVisibility(View.GONE);
        ((View) introBox.getParent()).setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        scanBox.setVisibility(View.VISIBLE);
        scanText.setText("準備中…");
        bar.setProgress(0);

        new Thread(new Runnable() {
            @Override public void run() { doScan(src); }
        }, "dup-scan").start();
    }

    private void doScan(ArrayList<Item> src) {
        final HashMap<Long, long[]> cache = Db.get(this).loadHashes();
        final ArrayList<long[]> fresh = new ArrayList<long[]>();
        final long[][] hashes = new long[src.size()][];
        int done = 0;

        for (int i = 0; i < src.size(); i++) {
            if (cancelled) break;
            Item it = src.get(i);
            long[] h = cache.get(Long.valueOf(it.id));
            if (h == null) {
                Bitmap bmp = Thumbs.tiny(this, it, 64);
                if (bmp != null) {
                    h = hashOf(bmp);
                    try { bmp.recycle(); } catch (Throwable ignored) { }
                    fresh.add(new long[]{ it.id, h[0], h[1] });
                    if (fresh.size() >= 200) {
                        Db.get(this).putHashes(new ArrayList<long[]>(fresh));
                        fresh.clear();
                    }
                }
            }
            hashes[i] = h;
            done++;
            if (done % 12 == 0 || done == src.size()) {
                final int d = done, total = src.size();
                main.post(new Runnable() {
                    @Override public void run() {
                        scanText.setText("分析照片中…　" + d + " / " + total);
                        bar.setProgress((int) (1000L * d / total));
                    }
                });
            }
        }
        if (!fresh.isEmpty()) Db.get(this).putHashes(fresh);

        if (cancelled) {
            main.post(new Runnable() {
                @Override public void run() {
                    scanning = false;
                    scanBox.setVisibility(View.GONE);
                    ((View) introBox.getParent()).setVisibility(View.VISIBLE);
                    introBox.setVisibility(View.VISIBLE);
                    Ui.toast(DupActivity.this, "已取消");
                }
            });
            return;
        }

        main.post(new Runnable() {
            @Override public void run() { scanText.setText("比對相似度…"); }
        });

        final ArrayList<ArrayList<Item>> found = group(src, hashes);
        main.post(new Runnable() {
            @Override public void run() {
                scanning = false;
                scanBox.setVisibility(View.GONE);
                groups.clear();
                groups.addAll(found);
                selected.clear();
                autoSelect();
                if (groups.isEmpty()) {
                    ((View) introBox.getParent()).setVisibility(View.VISIBLE);
                    introBox.setVisibility(View.VISIBLE);
                    introText.setText("太好了，沒有找到明顯重複或相似的照片。\n\n"
                            + "如果之後又拍了連拍，可以再回來掃一次。");
                    startBtn.setText("重新掃描");
                } else {
                    list.setVisibility(View.VISIBLE);
                    bottomBar.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                    updateBottom();
                }
            }
        });
    }

    /** 回傳 {aHash, dHash} */
    private static long[] hashOf(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 9, 8, true);
        int[] px = new int[9 * 8];
        s.getPixels(px, 0, 9, 0, 0, 9, 8);
        int[] g = new int[9 * 8];
        long sum = 0;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int v = (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 151 + (c & 0xFF) * 28) >> 8;
            g[i] = v;
        }
        // aHash: 取左邊 8x8 的平均
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) sum += g[y * 9 + x];
        long mean = sum / 64;
        long a = 0;
        int bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (g[y * 9 + x] > mean) a |= (1L << bit);
                bit++;
            }
        }
        // dHash: 相鄰像素比較
        long d = 0;
        bit = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if (g[y * 9 + x] > g[y * 9 + x + 1]) d |= (1L << bit);
                bit++;
            }
        }
        if (s != src) s.recycle();
        return new long[]{ a, d };
    }

    private ArrayList<ArrayList<Item>> group(ArrayList<Item> src, long[][] hashes) {
        int n = src.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        // 以 4 個 16-bit 分段做候選配對，避免 N² 比對
        for (int band = 0; band < 4; band++) {
            HashMap<Integer, ArrayList<Integer>> buckets = new HashMap<Integer, ArrayList<Integer>>();
            for (int i = 0; i < n; i++) {
                if (hashes[i] == null) continue;
                int key = (int) ((hashes[i][0] >>> (band * 16)) & 0xFFFFL) | (band << 20);
                ArrayList<Integer> b = buckets.get(Integer.valueOf(key));
                if (b == null) { b = new ArrayList<Integer>(); buckets.put(Integer.valueOf(key), b); }
                b.add(Integer.valueOf(i));
            }
            for (ArrayList<Integer> b : buckets.values()) {
                if (b.size() < 2) continue;
                int limit = Math.min(b.size(), 400);
                for (int x = 0; x < b.size(); x++) {
                    int cmp = 0;
                    for (int y = x + 1; y < b.size() && cmp < limit; y++, cmp++) {
                        int i = b.get(x).intValue(), j = b.get(y).intValue();
                        if (find(parent, i) == find(parent, j)) continue;
                        if (similar(src.get(i), src.get(j), hashes[i], hashes[j])) {
                            union(parent, i, j);
                        }
                    }
                }
            }
        }

        HashMap<Integer, ArrayList<Item>> map = new HashMap<Integer, ArrayList<Item>>();
        for (int i = 0; i < n; i++) {
            if (hashes[i] == null) continue;
            Integer r = Integer.valueOf(find(parent, i));
            ArrayList<Item> g = map.get(r);
            if (g == null) { g = new ArrayList<Item>(); map.put(r, g); }
            g.add(src.get(i));
        }
        ArrayList<ArrayList<Item>> out = new ArrayList<ArrayList<Item>>();
        for (ArrayList<Item> g : map.values()) {
            if (g.size() >= 2) {
                Collections.sort(g, new Comparator<Item>() {
                    @Override public int compare(Item a, Item b) { return Long.compare(b.timeMs, a.timeMs); }
                });
                out.add(g);
            }
        }
        Collections.sort(out, new Comparator<ArrayList<Item>>() {
            @Override public int compare(ArrayList<Item> a, ArrayList<Item> b) {
                long sa = 0, sb = 0;
                for (int i = 1; i < a.size(); i++) sa += a.get(i).size;
                for (int i = 1; i < b.size(); i++) sb += b.get(i).size;
                return Long.compare(sb, sa);
            }
        });
        return out;
    }

    private static boolean similar(Item a, Item b, long[] ha, long[] hb) {
        if (ha == null || hb == null) return false;
        int da = Long.bitCount(ha[0] ^ hb[0]);
        if (da > 6) return false;
        int dd = Long.bitCount(ha[1] ^ hb[1]);
        if (dd > 10) return false;
        if (a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0) {
            float ra = (float) a.width / a.height;
            float rb = (float) b.width / b.height;
            if (Math.abs(ra - rb) > 0.18f) return false;
        }
        return true;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; }
        return i;
    }

    private static void union(int[] p, int i, int j) {
        int a = find(p, i), b = find(p, j);
        if (a != b) p[b] = a;
    }

    /** 每組自動保留「最好的一張」，其餘預設選取。 */
    private void autoSelect() {
        for (int i = 0; i < groups.size(); i++) {
            ArrayList<Item> g = groups.get(i);
            int best = bestIndex(g);
            for (int j = 0; j < g.size(); j++) {
                if (j != best) selected.add(Long.valueOf(g.get(j).id));
            }
        }
    }

    private int bestIndex(List<Item> g) {
        int best = 0;
        for (int i = 1; i < g.size(); i++) {
            Item a = g.get(best), b = g.get(i);
            long pa = (long) a.width * a.height, pb = (long) b.width * b.height;
            if (pb > pa || (pb == pa && b.size > a.size)
                    || (pb == pa && b.size == a.size && b.timeMs > a.timeMs)) best = i;
        }
        return best;
    }

    private void updateBottom() {
        long bytes = 0;
        int n = 0;
        for (int i = 0; i < groups.size(); i++) {
            ArrayList<Item> g = groups.get(i);
            for (int j = 0; j < g.size(); j++) {
                if (selected.contains(Long.valueOf(g.get(j).id))) { n++; bytes += g.get(j).size; }
            }
        }
        bottomText.setText("找到 " + groups.size() + " 組　·　已選 " + n + " 張\n可釋放約 " + Item.formatSize(bytes));
    }

    private void applySelection() {
        ArrayList<Item> items = new ArrayList<Item>();
        for (int i = 0; i < groups.size(); i++) {
            ArrayList<Item> g = groups.get(i);
            for (int j = 0; j < g.size(); j++) {
                if (selected.contains(Long.valueOf(g.get(j).id))) items.add(g.get(j));
            }
        }
        if (items.isEmpty()) { Ui.toast(this, "還沒選取任何照片"); return; }
        Store.get().setStates(this, items, Db.STATE_PENDING);
        Ui.toast(this, "已把 " + items.size() + " 張加入待刪清單");
        finish();
    }

    // ---------- Adapter ----------

    private class GroupAdapter extends BaseAdapter {
        @Override public int getCount() { return groups.size(); }
        @Override public Object getItem(int p) { return groups.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            final ArrayList<Item> g = groups.get(pos);
            LinearLayout box = Ui.col(DupActivity.this);
            box.setBackground(Ui.round(T.SURFACE, Ui.dp(DupActivity.this, 16)));
            box.setPadding(Ui.dp(DupActivity.this, 12), Ui.dp(DupActivity.this, 12),
                    Ui.dp(DupActivity.this, 12), Ui.dp(DupActivity.this, 12));

            long total = 0;
            for (int i = 0; i < g.size(); i++) total += g.get(i).size;
            LinearLayout head = Ui.rowH(DupActivity.this);
            head.addView(Ui.tv(DupActivity.this, "第 " + (pos + 1) + " 組　·　" + g.size() + " 張",
                    14.5f, T.TEXT, true));
            head.addView(Ui.grow(DupActivity.this));
            head.addView(Ui.tv(DupActivity.this, Item.formatSize(total), 12.5f, T.FAINT, false));
            box.addView(head, Ui.lpW());
            box.addView(Ui.space(DupActivity.this, 4));
            box.addView(Ui.tv(DupActivity.this, g.get(0).dayText() + " 起　·　綠框是建議保留的那張",
                    12f, T.FAINT, false), Ui.lpW());
            box.addView(Ui.space(DupActivity.this, 10));

            HorizontalScrollView sv = new HorizontalScrollView(DupActivity.this);
            sv.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = Ui.rowH(DupActivity.this);
            int keepIdx = bestIndex(g);
            for (int i = 0; i < g.size(); i++) {
                final Item it = g.get(i);
                boolean isKeep = (i == keepIdx);
                FrameLayout f = new FrameLayout(DupActivity.this);
                f.setBackgroundColor(T.SURFACE2);
                ImageView iv = new ImageView(DupActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Thumbs.grid(iv, it, 256);
                f.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                final View overlay = new View(DupActivity.this);
                boolean on = selected.contains(Long.valueOf(it.id));
                overlay.setBackground(Ui.round(on ? Ui.withAlpha(T.TRASH, 0x59) : 0x00000000, 0,
                        on ? T.TRASH : (isKeep ? T.KEEP : T.BORDER), Ui.dp(DupActivity.this, 2.5f)));
                f.addView(overlay, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                TextView tag = Ui.tv(DupActivity.this, on ? "刪" : (isKeep ? "留" : ""), 11.5f,
                        0xFFFFFFFF, true);
                tag.setBackground(Ui.round(on ? T.TRASH : T.KEEP, Ui.dp(DupActivity.this, 9)));
                tag.setPadding(Ui.dp(DupActivity.this, 6), Ui.dp(DupActivity.this, 2),
                        Ui.dp(DupActivity.this, 6), Ui.dp(DupActivity.this, 2));
                tag.setVisibility(on || isKeep ? View.VISIBLE : View.GONE);
                FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.gravity = Gravity.TOP | Gravity.START;
                tlp.topMargin = Ui.dp(DupActivity.this, 5);
                tlp.leftMargin = Ui.dp(DupActivity.this, 5);
                f.addView(tag, tlp);

                TextView info = Ui.tv(DupActivity.this, it.dimText() + "\n" + it.sizeText(),
                        10f, 0xFFFFFFFF, false);
                info.setBackgroundColor(0x99000000);
                info.setPadding(Ui.dp(DupActivity.this, 4), Ui.dp(DupActivity.this, 2),
                        Ui.dp(DupActivity.this, 4), Ui.dp(DupActivity.this, 2));
                FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ilp.gravity = Gravity.BOTTOM;
                f.addView(info, ilp);

                f.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Long k = Long.valueOf(it.id);
                        if (selected.contains(k)) selected.remove(k); else selected.add(k);
                        adapter.notifyDataSetChanged();
                        updateBottom();
                    }
                });
                f.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        Intent in = new Intent(DupActivity.this, ViewerActivity.class);
                        in.putExtra("id", it.id);
                        startActivity(in);
                        return true;
                    }
                });
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(thumbPx, thumbPx);
                flp.rightMargin = Ui.dp(DupActivity.this, 6);
                strip.addView(f, flp);
            }
            sv.addView(strip, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(sv, Ui.lpW());

            LinearLayout wrap = Ui.col(DupActivity.this);
            wrap.setPadding(Ui.dp(DupActivity.this, 12), Ui.dp(DupActivity.this, 6),
                    Ui.dp(DupActivity.this, 12), Ui.dp(DupActivity.this, 6));
            wrap.addView(box, Ui.lpW());
            return wrap;
        }
    }
}
