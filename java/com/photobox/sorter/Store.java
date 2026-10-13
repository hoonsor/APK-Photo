package com.photobox.sorter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 全 App 共用的照片清單與整理狀態。 */
public class Store {

    public interface Listener { void onStoreChanged(); }

    private static Store I;
    public static synchronized Store get() {
        if (I == null) I = new Store();
        return I;
    }

    public final ArrayList<Item> all = new ArrayList<Item>();
    public final HashMap<Long, Item> byId = new HashMap<Long, Item>();
    public final HashMap<Long, Integer> state = new HashMap<Long, Integer>();
    public boolean loaded = false;
    public volatile boolean loading = false;

    private final ArrayList<Listener> listeners = new ArrayList<Listener>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedList<long[]> undoStack = new LinkedList<long[]>();

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void notifyChanged() {
        for (int i = listeners.size() - 1; i >= 0; i--) {
            try { listeners.get(i).onStoreChanged(); } catch (Throwable ignored) { }
        }
    }

    public void load(final Context c, final Runnable done) {
        if (loading) return;
        loading = true;
        final Context app = c.getApplicationContext();
        final boolean video = Prefs.showVideo(app);
        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Item> items = Media.query(app, video);
                Collections.sort(items, new Comparator<Item>() {
                    @Override public int compare(Item a, Item b) {
                        return Long.compare(b.timeMs, a.timeMs);
                    }
                });
                final HashMap<Long, Integer> st = Db.get(app).loadStates();
                final ArrayList<Albums.Album> al = Albums.read(app);
                main.post(new Runnable() {
                    @Override public void run() {
                        all.clear();
                        byId.clear();
                        all.addAll(items);
                        for (int i = 0; i < items.size(); i++) byId.put(items.get(i).id, items.get(i));
                        state.clear();
                        // 只保留仍然存在的項目狀態
                        for (java.util.Map.Entry<Long, Integer> e : st.entrySet()) {
                            if (byId.containsKey(e.getKey())) state.put(e.getKey(), e.getValue());
                        }
                        Albums.get().apply(al);
                        loaded = true;
                        loading = false;
                        notifyChanged();
                        if (done != null) done.run();
                    }
                });
            }
        }, "store-load").start();
    }

    public int stateOf(Item it) {
        Integer s = state.get(it.id);
        return s == null ? Db.STATE_NONE : s.intValue();
    }

    public void setState(Context c, Item it, int s, boolean recordUndo) {
        if (recordUndo) pushUndo(it.id, stateOf(it));
        if (s == Db.STATE_NONE) state.remove(it.id); else state.put(it.id, s);
        Db.get(c).setState(it.id, s);
    }

    public void setStates(Context c, List<Item> items, int s) {
        ArrayList<Long> ids = new ArrayList<Long>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            ids.add(it.id);
            if (s == Db.STATE_NONE) state.remove(it.id); else state.put(it.id, s);
        }
        Db.get(c).setStates(ids, s);
        notifyChanged();
    }

    /** 從清單中整個移除（照片已被刪除／丟進系統垃圾桶）。 */
    public void forget(Context c, List<Item> items) {
        ArrayList<Long> ids = new ArrayList<Long>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            ids.add(it.id);
            state.remove(it.id);
            byId.remove(it.id);
        }
        Db.get(c).setStates(ids, Db.STATE_NONE);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (!byId.containsKey(all.get(i).id)) all.remove(i);
        }
        notifyChanged();
    }

    // ---- 復原 ----

    private void pushUndo(long id, int prev) {
        undoStack.addFirst(new long[]{ id, prev });
        while (undoStack.size() > 80) undoStack.removeLast();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    public Item undo(Context c) {
        if (undoStack.isEmpty()) return null;
        long[] e = undoStack.removeFirst();
        Item it = byId.get(e[0]);
        int prev = (int) e[1];
        if (prev == Db.STATE_NONE) state.remove(e[0]); else state.put(e[0], prev);
        Db.get(c).setState(e[0], prev);
        return it;
    }

    public void clearUndo() { undoStack.clear(); }

    // ---- 從指定的某一張開始整理 ----

    private long sortStartId = 0;
    private boolean sortNewer = false;
    private int sortInclude = -1;   // -1 自動（舊行為）、0 只看未整理、1 全部

    /** 舊行為：往舊的整理，包含與否依那張是否已整理過自動決定。 */
    public void requestSortStart(long id) { requestSortStart(id, false, -1); }

    /**
     * @param newer   true＝從這張往較新的照片整理；false＝往較舊的
     * @param include 0＝只整理還沒整理過的；1＝全部（含已保留／待刪）；-1＝自動
     */
    public void requestSortStart(long id, boolean newer, int include) {
        sortStartId = id;
        sortNewer = newer;
        sortInclude = include;
    }

    /** @return {id, newer(1/0), include}；沒有請求時回傳 null。 */
    public long[] consumeSortStartRequest() {
        if (sortStartId <= 0) return null;
        long[] r = { sortStartId, sortNewer ? 1 : 0, sortInclude };
        sortStartId = 0;
        return r;
    }

    // ---- 統計 ----

    public int count(int s) {
        int n = 0;
        for (int i = 0; i < all.size(); i++) if (stateOf(all.get(i)) == s) n++;
        return n;
    }

    public ArrayList<Item> pending() {
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < all.size(); i++) {
            if (stateOf(all.get(i)) == Db.STATE_PENDING) out.add(all.get(i));
        }
        return out;
    }

    public long pendingBytes() {
        long b = 0;
        ArrayList<Item> p = pending();
        for (int i = 0; i < p.size(); i++) b += p.get(i).size;
        return b;
    }

    /** 相簿名稱 -> 張數，依張數排序。 */
    public ArrayList<String[]> albums() {
        HashMap<String, Integer> m = new HashMap<String, Integer>();
        for (int i = 0; i < all.size(); i++) {
            String b = all.get(i).bucket;
            Integer n = m.get(b);
            m.put(b, n == null ? 1 : n + 1);
        }
        ArrayList<String[]> out = new ArrayList<String[]>();
        for (java.util.Map.Entry<String, Integer> e : m.entrySet()) {
            out.add(new String[]{ e.getKey(), String.valueOf(e.getValue()) });
        }
        Collections.sort(out, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) {
                return Integer.parseInt(b[1]) - Integer.parseInt(a[1]);
            }
        });
        return out;
    }

    /** 有照片的年份（新到舊）。 */
    public ArrayList<Integer> years(List<Item> src) {
        ArrayList<Integer> ys = new ArrayList<Integer>();
        for (int i = 0; i < src.size(); i++) {
            int y = src.get(i).year;
            if (!ys.contains(Integer.valueOf(y))) ys.add(Integer.valueOf(y));
        }
        Collections.sort(ys, Collections.reverseOrder());
        return ys;
    }

    // ---- 篩選 ----

    public static class Filter {
        public String text = "";
        public int stateWanted = -1;      // -1 全部, 0 未整理, 1 已保留, 2 待刪
        public boolean onlyScreenshot = false;
        public boolean onlyVideo = false;
        public boolean onlyLarge = false; // > 8 MB
        public boolean onlyLine = false;  // 從 LINE 存下來的
        public String album = null;
        public int sort = 0;              // 0 最新 1 最舊 2 最大

        public boolean isDefault() {
            return (text == null || text.trim().length() == 0) && stateWanted == -1
                    && !onlyScreenshot && !onlyVideo && !onlyLarge && !onlyLine && album == null;
        }
    }

    private static final Pattern DATE_Q = Pattern.compile(
            "^(\\d{4})(?:[-/.年](\\d{1,2})(?:[-/.月](\\d{1,2}))?)?日?$");

    /** 讓 20230715 / 202307 這類純數字日期也能被辨識。 */
    private static String normalizeDate(String q) {
        String t = q.trim().replace(" ", "");
        if (t.matches("^\\d{6}$")) return t.substring(0, 4) + "-" + t.substring(4);
        if (t.matches("^\\d{8}$")) return t.substring(0, 4) + "-" + t.substring(4, 6) + "-" + t.substring(6);
        return t;
    }

    public ArrayList<Item> filter(Filter f) {
        ArrayList<Item> out = new ArrayList<Item>();
        String q = f.text == null ? "" : f.text.trim();
        int qy = 0, qm = 0, qd = 0;
        boolean dateMode = false;
        if (q.length() > 0) {
            Matcher m = DATE_Q.matcher(normalizeDate(q));
            if (m.matches()) {
                dateMode = true;
                qy = Integer.parseInt(m.group(1));
                if (m.group(2) != null) qm = Integer.parseInt(m.group(2));
                if (m.group(3) != null) qd = Integer.parseInt(m.group(3));
            }
        }
        String lq = q.toLowerCase(Locale.US);
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < all.size(); i++) {
            Item it = all.get(i);
            int st = stateOf(it);
            if (f.stateWanted >= 0 && st != f.stateWanted) continue;
            if (f.onlyVideo && !it.video) continue;
            if (f.onlyScreenshot && !it.isScreenshot()) continue;
            if (f.onlyLine && !it.isLine()) continue;
            if (f.onlyLarge && it.size < 8L * 1024 * 1024) continue;
            if (f.album != null && !f.album.equals(it.bucket)) continue;
            if (q.length() > 0) {
                if (dateMode) {
                    if (it.year != qy) continue;
                    if (qm > 0 && it.month != qm) continue;
                    if (qd > 0) {
                        cal.setTimeInMillis(it.timeMs);
                        if (cal.get(Calendar.DAY_OF_MONTH) != qd) continue;
                    }
                } else {
                    String hay = (it.name + " " + it.bucket).toLowerCase(Locale.US);
                    if (!hay.contains(lq)) continue;
                }
            }
            out.add(it);
        }

        if (f.sort == 1) {
            Collections.sort(out, new Comparator<Item>() {
                @Override public int compare(Item a, Item b) { return Long.compare(a.timeMs, b.timeMs); }
            });
        } else if (f.sort == 2) {
            Collections.sort(out, new Comparator<Item>() {
                @Override public int compare(Item a, Item b) { return Long.compare(b.size, a.size); }
            });
        }
        return out;
    }

    /** 整理模式要處理的清單：尚未整理過的照片。 */
    public ArrayList<Item> deck(String album, int ym) {
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < all.size(); i++) {
            Item it = all.get(i);
            if (stateOf(it) != Db.STATE_NONE) continue;
            if (album != null && !album.equals(it.bucket)) continue;
            if (ym > 0 && it.ym != ym) continue;
            out.add(it);
        }
        return out;
    }
}
