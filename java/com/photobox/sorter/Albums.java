package com.photobox.sorter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 虛擬相簿。
 *
 * 相簿只是 App 內的一份清單（albums / album_items 兩張表），不會搬動或複製任何檔案，
 * 所以同一張照片可以同時在好幾本相簿裡，照片與影片也可以放在同一本。
 * 實體資料夾（MediaStore 的 BUCKET）仍然叫「資料夾」，兩者刻意分開命名。
 *
 * 所有寫入都在主執行緒同步完成（資料量小），寫完呼叫 Store.notifyChanged()。
 */
public final class Albums {

    public static final int KIND_NORMAL = 0;
    public static final int KIND_FAV = 1;
    public static final String FAV_NAME = "喜好項目";

    public static final class Album {
        public long id;
        public String name = "";
        public int kind;
        public long cover;
        public long created, updated;
        /** media_id → 加入時間，依加入順序。 */
        public final LinkedHashMap<Long, Long> items = new LinkedHashMap<Long, Long>();

        public boolean isFav() { return kind == KIND_FAV; }
    }

    private static Albums I;
    public static synchronized Albums get() {
        if (I == null) I = new Albums();
        return I;
    }

    private final ArrayList<Album> list = new ArrayList<Album>();
    private final HashMap<Long, Album> byId = new HashMap<Long, Album>();

    // ---------- 載入 ----------

    /** 背景執行緒呼叫：讀出整份相簿資料。 */
    public static ArrayList<Album> read(Context c) {
        ArrayList<Album> out = new ArrayList<Album>();
        HashMap<Long, Album> m = new HashMap<Long, Album>();
        Cursor cur = null;
        try {
            SQLiteDatabase db = Db.get(c).getReadableDatabase();
            cur = db.rawQuery("SELECT id,name,kind,cover,created,updated FROM albums", null);
            while (cur.moveToNext()) {
                Album a = new Album();
                a.id = cur.getLong(0);
                a.name = cur.getString(1) == null ? "" : cur.getString(1);
                a.kind = cur.getInt(2);
                a.cover = cur.getLong(3);
                a.created = cur.getLong(4);
                a.updated = cur.getLong(5);
                out.add(a);
                m.put(a.id, a);
            }
            cur.close();
            cur = db.rawQuery("SELECT album_id,media_id,ts FROM album_items ORDER BY ts ASC", null);
            while (cur.moveToNext()) {
                Album a = m.get(cur.getLong(0));
                if (a != null) a.items.put(cur.getLong(1), cur.getLong(2));
            }
        } catch (Throwable ignored) {
        } finally { if (cur != null) try { cur.close(); } catch (Throwable ignored) { } }
        return out;
    }

    /** 主執行緒呼叫：套用 read() 的結果。 */
    public void apply(ArrayList<Album> fresh) {
        list.clear();
        byId.clear();
        list.addAll(fresh);
        for (int i = 0; i < fresh.size(); i++) byId.put(fresh.get(i).id, fresh.get(i));
    }

    // ---------- 查詢 ----------

    /** 顯示順序：喜好項目永遠第一，其餘依建立時間新到舊。 */
    public ArrayList<Album> ordered() {
        ArrayList<Album> out = new ArrayList<Album>(list);
        Collections.sort(out, new Comparator<Album>() {
            @Override public int compare(Album x, Album y) {
                if (x.isFav() != y.isFav()) return x.isFav() ? -1 : 1;
                return Long.compare(y.created, x.created);
            }
        });
        return out;
    }

    /** 不含喜好項目的一般相簿（給「加入相簿」選單用時，喜好另外處理）。 */
    public ArrayList<Album> normal() {
        ArrayList<Album> out = ordered();
        for (int i = out.size() - 1; i >= 0; i--) if (out.get(i).isFav()) out.remove(i);
        return out;
    }

    public Album byId(long id) { return byId.get(id); }

    public Album favorites() {
        for (int i = 0; i < list.size(); i++) if (list.get(i).isFav()) return list.get(i);
        return null;
    }

    public boolean nameTaken(String name, Album except) {
        String n = name == null ? "" : name.trim();
        for (int i = 0; i < list.size(); i++) {
            Album a = list.get(i);
            if (a != except && a.name.equals(n)) return true;
        }
        return false;
    }

    /** 這個項目所在的所有相簿。 */
    public ArrayList<Album> of(long mediaId) {
        ArrayList<Album> out = new ArrayList<Album>();
        ArrayList<Album> all = ordered();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).items.containsKey(mediaId)) out.add(all.get(i));
        }
        return out;
    }

    public boolean isFav(long mediaId) {
        Album f = favorites();
        return f != null && f.items.containsKey(mediaId);
    }

    /** 相簿裡仍然存在於手機上的項目，依拍攝時間新到舊。 */
    public ArrayList<Item> items(Album a) {
        ArrayList<Item> out = new ArrayList<Item>();
        if (a == null) return out;
        Store s = Store.get();
        for (Long id : a.items.keySet()) {
            Item it = s.byId.get(id);
            if (it != null) out.add(it);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item x, Item y) { return Long.compare(y.timeMs, x.timeMs); }
        });
        return out;
    }

    /** {照片數, 影片數}（只算仍存在的）。 */
    public int[] counts(Album a) {
        int p = 0, v = 0;
        Store s = Store.get();
        for (Long id : a.items.keySet()) {
            Item it = s.byId.get(id);
            if (it == null) continue;
            if (it.video) v++; else p++;
        }
        return new int[]{ p, v };
    }

    public static String countText(int[] pv) {
        return pv[0] + " 張照片、" + pv[1] + " 部影片";
    }

    /** 封面：使用者指定的那張；沒指定或已不存在時，用最新的一張。 */
    public Item cover(Album a) {
        if (a == null) return null;
        Store s = Store.get();
        if (a.cover > 0) {
            Item c = s.byId.get(a.cover);
            if (c != null && a.items.containsKey(a.cover)) return c;
        }
        Item best = null;
        for (Long id : a.items.keySet()) {
            Item it = s.byId.get(id);
            if (it != null && (best == null || it.timeMs > best.timeMs)) best = it;
        }
        return best;
    }

    // ---------- 寫入 ----------

    private SQLiteDatabase w(Context c) { return Db.get(c).getWritableDatabase(); }

    public Album create(Context c, String name) {
        return create(c, name, KIND_NORMAL);
    }

    private Album create(Context c, String name, int kind) {
        Album a = new Album();
        a.name = name == null ? "" : name.trim();
        a.kind = kind;
        a.created = a.updated = System.currentTimeMillis();
        try {
            ContentValues v = new ContentValues();
            v.put("name", a.name);
            v.put("kind", kind);
            v.put("cover", 0);
            v.put("created", a.created);
            v.put("updated", a.updated);
            a.id = w(c).insert("albums", null, v);
        } catch (Throwable t) { a.id = -1; }
        if (a.id <= 0) return null;
        list.add(a);
        byId.put(a.id, a);
        Store.get().notifyChanged();
        return a;
    }

    public Album ensureFavorites(Context c) {
        Album f = favorites();
        if (f != null) return f;
        return create(c, FAV_NAME, KIND_FAV);
    }

    public void rename(Context c, Album a, String name) {
        a.name = name.trim();
        touch(c, a, "name", a.name);
    }

    public void setCover(Context c, Album a, long mediaId) {
        a.cover = mediaId;
        touch(c, a, "cover", mediaId);
    }

    private void touch(Context c, Album a, String col, Object val) {
        a.updated = System.currentTimeMillis();
        try {
            ContentValues v = new ContentValues();
            if (val instanceof String) v.put(col, (String) val);
            else v.put(col, ((Number) val).longValue());
            v.put("updated", a.updated);
            w(c).update("albums", v, "id=?", new String[]{ String.valueOf(a.id) });
        } catch (Throwable ignored) { }
        Store.get().notifyChanged();
    }

    /** 複製相簿：內容與封面相同的一本新相簿（喜好項目複製後是一般相簿）。 */
    public Album duplicate(Context c, Album src, String name) {
        Album a = create(c, name, KIND_NORMAL);
        if (a == null) return null;
        ArrayList<Long> ids = new ArrayList<Long>(src.items.keySet());
        addIds(c, a, ids);
        if (src.cover > 0) setCover(c, a, src.cover);
        Store.get().notifyChanged();
        return a;
    }

    /** 刪除相簿本身；裡面的照片與影片完全不受影響。 */
    public void delete(Context c, Album a) {
        SQLiteDatabase db = null;
        try {
            db = w(c);
            db.beginTransaction();
            String[] arg = { String.valueOf(a.id) };
            db.delete("album_items", "album_id=?", arg);
            db.delete("albums", "id=?", arg);
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally { try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { } }
        list.remove(a);
        byId.remove(a.id);
        Store.get().notifyChanged();
    }

    /** @return 新加入的數量（已在相簿裡的不重複計算）。 */
    public int add(Context c, Album a, List<Item> items) {
        ArrayList<Long> ids = new ArrayList<Long>(items.size());
        for (int i = 0; i < items.size(); i++) ids.add(items.get(i).id);
        int n = addIds(c, a, ids);
        Store.get().notifyChanged();
        return n;
    }

    int addIds(Context c, Album a, List<Long> ids) {
        int n = 0;
        SQLiteDatabase db = null;
        long now = System.currentTimeMillis();
        try {
            db = w(c);
            db.beginTransaction();
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.get(i);
                if (a.items.containsKey(id)) continue;
                ContentValues v = new ContentValues();
                v.put("album_id", a.id);
                v.put("media_id", id.longValue());
                v.put("ts", now + i);   // +i 讓同一批加入的順序穩定
                db.insertWithOnConflict("album_items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                a.items.put(id, now + i);
                n++;
            }
            ContentValues u = new ContentValues();
            u.put("updated", now);
            db.update("albums", u, "id=?", new String[]{ String.valueOf(a.id) });
            db.setTransactionSuccessful();
            a.updated = now;
        } catch (Throwable ignored) {
        } finally { try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { } }
        return n;
    }

    public int remove(Context c, Album a, List<Item> items) {
        int n = 0;
        SQLiteDatabase db = null;
        try {
            db = w(c);
            db.beginTransaction();
            for (int i = 0; i < items.size(); i++) {
                long id = items.get(i).id;
                if (a.items.remove(id) == null) continue;
                db.delete("album_items", "album_id=? AND media_id=?",
                        new String[]{ String.valueOf(a.id), String.valueOf(id) });
                n++;
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally { try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { } }
        Store.get().notifyChanged();
        return n;
    }

    /** @return 切換後是否為喜好。 */
    public boolean toggleFav(Context c, Item it) {
        Album f = ensureFavorites(c);
        if (f == null) return false;
        ArrayList<Item> one = new ArrayList<Item>();
        one.add(it);
        if (f.items.containsKey(it.id)) { remove(c, f, one); return false; }
        add(c, f, one);
        return true;
    }

    /** 給新相簿一個不重複的預設名稱。 */
    public String uniqueName(String base) {
        if (!nameTaken(base, null)) return base;
        for (int i = 2; i < 1000; i++) {
            String n = base + " (" + i + ")";
            if (!nameTaken(n, null)) return n;
        }
        return base + " " + System.currentTimeMillis();
    }
}
