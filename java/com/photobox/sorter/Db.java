package com.photobox.sorter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

/** 整理狀態與相似度雜湊的本機資料庫。 */
public class Db extends SQLiteOpenHelper {

    public static final int STATE_NONE = 0;
    public static final int STATE_KEPT = 1;
    public static final int STATE_PENDING = 2;   // 待刪清單

    private static Db instance;

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) { super(c, "photobox.db", null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE status(media_id INTEGER PRIMARY KEY, state INTEGER NOT NULL, ts INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE ahash(media_id INTEGER PRIMARY KEY, hash INTEGER NOT NULL, sig INTEGER NOT NULL)");
        db.execSQL(SQL_TRASHED);
        db.execSQL(SQL_ALBUMS);
        db.execSQL(SQL_ALBUM_ITEMS);
    }

    /** 虛擬相簿（v1.1.0 起，DB 版本 3）：只是 App 內的清單，不會搬動檔案。 */
    private static final String SQL_ALBUMS =
            "CREATE TABLE IF NOT EXISTS albums(id INTEGER PRIMARY KEY AUTOINCREMENT,"
          + " name TEXT NOT NULL, kind INTEGER NOT NULL DEFAULT 0,"
          + " cover INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL DEFAULT 0,"
          + " updated INTEGER NOT NULL DEFAULT 0)";
    private static final String SQL_ALBUM_ITEMS =
            "CREATE TABLE IF NOT EXISTS album_items(album_id INTEGER NOT NULL,"
          + " media_id INTEGER NOT NULL, ts INTEGER NOT NULL DEFAULT 0,"
          + " PRIMARY KEY(album_id, media_id))";

    private static final String SQL_TRASHED =
            "CREATE TABLE IF NOT EXISTS trashed(media_id INTEGER PRIMARY KEY, name TEXT,"
          + " size INTEGER NOT NULL DEFAULT 0, video INTEGER NOT NULL DEFAULT 0,"
          + " ts INTEGER NOT NULL DEFAULT 0)";

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        try { db.execSQL(SQL_TRASHED); } catch (Throwable ignored) { }
        try { db.execSQL(SQL_ALBUMS); } catch (Throwable ignored) { }
        try { db.execSQL(SQL_ALBUM_ITEMS); } catch (Throwable ignored) { }
    }

    // ---- 本 App 送進系統垃圾桶的項目 ----

    public void addTrashed(java.util.List<Item> items) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            long now = System.currentTimeMillis();
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                ContentValues v = new ContentValues();
                v.put("media_id", it.id);
                v.put("name", it.name);
                v.put("size", it.size);
                v.put("video", it.video ? 1 : 0);
                v.put("ts", now);
                db.insertWithOnConflict("trashed", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { }
        }
    }

    /** 回傳曾經送進系統垃圾桶的項目（以 Item 形式重建，供刪除請求使用）。 */
    public java.util.ArrayList<Item> loadTrashed() {
        java.util.ArrayList<Item> out = new java.util.ArrayList<Item>();
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery(
                    "SELECT media_id,name,size,video,ts FROM trashed ORDER BY ts DESC", null);
            while (cur.moveToNext()) {
                Item it = new Item();
                it.id = cur.getLong(0);
                it.name = cur.getString(1) == null ? "" : cur.getString(1);
                it.size = cur.getLong(2);
                it.video = cur.getInt(3) == 1;
                it.timeMs = cur.getLong(4);
                out.add(it);
            }
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return out;
    }

    public void removeTrashed(java.util.List<Item> items) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (int i = 0; i < items.size(); i++) {
                db.delete("trashed", "media_id=?", new String[]{ String.valueOf(items.get(i).id) });
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { }
        }
    }

    public void clearTrashed() {
        try { getWritableDatabase().delete("trashed", null, null); } catch (Throwable ignored) { }
    }

    public HashMap<Long, Integer> loadStates() {
        HashMap<Long, Integer> m = new HashMap<Long, Integer>();
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery("SELECT media_id,state FROM status", null);
            while (cur.moveToNext()) m.put(cur.getLong(0), cur.getInt(1));
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return m;
    }

    public void setState(long id, int state) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            if (state == STATE_NONE) {
                db.delete("status", "media_id=?", new String[]{String.valueOf(id)});
            } else {
                ContentValues v = new ContentValues();
                v.put("media_id", id);
                v.put("state", state);
                v.put("ts", System.currentTimeMillis());
                db.insertWithOnConflict("status", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
        } catch (Throwable ignored) { }
    }

    public void setStates(java.util.List<Long> ids, int state) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                if (state == STATE_NONE) {
                    db.delete("status", "media_id=?", new String[]{String.valueOf(id)});
                } else {
                    ContentValues v = new ContentValues();
                    v.put("media_id", id);
                    v.put("state", state);
                    v.put("ts", System.currentTimeMillis());
                    db.insertWithOnConflict("status", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { }
        }
    }

    public void clearAllStates() {
        try { getWritableDatabase().delete("status", null, null); } catch (Throwable ignored) { }
    }

    // ---- 相似度雜湊快取 ----

    public HashMap<Long, long[]> loadHashes() {
        HashMap<Long, long[]> m = new HashMap<Long, long[]>();
        Cursor cur = null;
        try {
            cur = getReadableDatabase().rawQuery("SELECT media_id,hash,sig FROM ahash", null);
            while (cur.moveToNext()) m.put(cur.getLong(0), new long[]{cur.getLong(1), cur.getLong(2)});
        } catch (Throwable ignored) {
        } finally { if (cur != null) cur.close(); }
        return m;
    }

    public void putHashes(java.util.List<long[]> rows) {
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            for (int i = 0; i < rows.size(); i++) {
                long[] r = rows.get(i);
                ContentValues v = new ContentValues();
                v.put("media_id", r[0]);
                v.put("hash", r[1]);
                v.put("sig", r[2]);
                db.insertWithOnConflict("ahash", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            try { if (db != null) db.endTransaction(); } catch (Throwable ignored) { }
        }
    }
}
