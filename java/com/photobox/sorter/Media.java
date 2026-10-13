package com.photobox.sorter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 所有與系統媒體庫往來的入口。 */
public final class Media {

    private Media() {}

    public static String[] perms() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO" };
        }
        return new String[]{ "android.permission.READ_EXTERNAL_STORAGE" };
    }

    public static boolean granted(Context c) {
        String[] p = perms();
        for (int i = 0; i < p.length; i++) {
            if (c.checkSelfPermission(p[i]) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** Android 14 起使用者可能只授權「部分照片」。 */
    public static boolean partialOnly(Context c) {
        if (Build.VERSION.SDK_INT < 34) return false;
        boolean full = c.checkSelfPermission("android.permission.READ_MEDIA_IMAGES")
                == PackageManager.PERMISSION_GRANTED;
        boolean partial = c.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                == PackageManager.PERMISSION_GRANTED;
        return !full && partial;
    }

    private static final String[] PROJ = new String[]{
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.ORIENTATION,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH };

    private static Uri filesUri() {
        return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
    }

    private static String typeSelection(boolean includeVideo) {
        if (includeVideo) {
            return MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ","
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
        }
        return MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
    }

    public static ArrayList<Item> query(Context c, boolean includeVideo) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(filesUri(), PROJ, typeSelection(includeVideo), null,
                    MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC");
            return readItems(cur);
        } catch (Throwable t) {
            return new ArrayList<Item>();
        } finally {
            if (cur != null) try { cur.close(); } catch (Throwable ignored) { }
        }
    }

    /**
     * 查詢系統垃圾桶裡的項目。
     * 注意：Android 只讓檔案的擁有者 App 與系統相簿看得見被丟進垃圾桶的項目，
     * 一般 App 通常查不到別的 App 拍的照片，所以這裡可能回傳空清單。
     */
    public static ArrayList<Item> queryTrashed(Context c, boolean includeVideo) {
        Cursor cur = null;
        try {
            Bundle args = new Bundle();
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY);
            args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, typeSelection(includeVideo));
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC");
            cur = c.getContentResolver().query(filesUri(), PROJ, args, null);
            return readItems(cur);
        } catch (Throwable t) {
            return new ArrayList<Item>();
        } finally {
            if (cur != null) try { cur.close(); } catch (Throwable ignored) { }
        }
    }

    /** 確認這些 id 是否仍在垃圾桶中（已被系統清掉的就不必再刪）。 */
    public static ArrayList<Item> refreshTrashed(Context c, List<Item> known) {
        ArrayList<Item> out = new ArrayList<Item>();
        if (known == null || known.isEmpty()) return out;
        ArrayList<Item> visible = queryTrashed(c, true);
        if (!visible.isEmpty()) {
            java.util.HashSet<Long> live = new java.util.HashSet<Long>();
            for (int i = 0; i < visible.size(); i++) live.add(Long.valueOf(visible.get(i).id));
            for (int i = 0; i < known.size(); i++) {
                if (live.contains(Long.valueOf(known.get(i).id))) out.add(known.get(i));
            }
            // 系統看得到、但本 App 沒記錄到的，也一併納入
            for (int i = 0; i < visible.size(); i++) {
                boolean dup = false;
                for (int j = 0; j < out.size(); j++) {
                    if (out.get(j).id == visible.get(i).id) { dup = true; break; }
                }
                if (!dup) out.add(visible.get(i));
            }
            return out;
        }
        // 查不到就沿用本 App 自己的紀錄
        out.addAll(known);
        return out;
    }

    private static ArrayList<Item> readItems(Cursor cur) {
        ArrayList<Item> out = new ArrayList<Item>();
        if (cur == null) return out;
        int iId = cur.getColumnIndex(MediaStore.Files.FileColumns._ID);
        if (iId < 0) return out;
        int iName = cur.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
        int iBucket = cur.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME);
        int iTaken = cur.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN);
        int iMod = cur.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);
        int iAdd = cur.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED);
        int iSize = cur.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
        int iW = cur.getColumnIndex(MediaStore.Files.FileColumns.WIDTH);
        int iH = cur.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT);
        int iO = cur.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION);
        int iMime = cur.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE);
        int iType = cur.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE);
        int iRel = cur.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH);
        Calendar cal = Calendar.getInstance();
        while (cur.moveToNext()) {
            Item it = new Item();
            it.id = cur.getLong(iId);
            it.name = safe(cur, iName);
            it.bucket = safe(cur, iBucket);
            if (it.bucket.length() == 0) it.bucket = "未分類";
            long taken = iTaken >= 0 ? cur.getLong(iTaken) : 0L;
            long mod = iMod >= 0 ? cur.getLong(iMod) : 0L;
            long add = iAdd >= 0 ? cur.getLong(iAdd) : 0L;
            if (taken > 0) it.timeMs = taken;
            else if (mod > 0) it.timeMs = mod * 1000L;
            else it.timeMs = add * 1000L;
            if (it.timeMs <= 0) it.timeMs = System.currentTimeMillis();
            it.size = iSize >= 0 ? cur.getLong(iSize) : 0L;
            it.width = iW >= 0 ? cur.getInt(iW) : 0;
            it.height = iH >= 0 ? cur.getInt(iH) : 0;
            it.orientation = iO >= 0 ? cur.getInt(iO) : 0;
            it.mime = safe(cur, iMime);
            it.relPath = safe(cur, iRel);
            it.video = iType >= 0
                    && cur.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            cal.setTimeInMillis(it.timeMs);
            it.year = cal.get(Calendar.YEAR);
            it.month = cal.get(Calendar.MONTH) + 1;
            it.ym = it.year * 100 + it.month;
            out.add(it);
        }
        return out;
    }

    private static String safe(Cursor c, int idx) {
        if (idx < 0) return "";
        try {
            String s = c.getString(idx);
            return s == null ? "" : s;
        } catch (Throwable t) { return ""; }
    }

    // ---- 需要使用者確認的系統請求 ----

    public static void requestTrash(Activity a, List<Uri> uris, boolean trash, int reqCode) {
        try {
            PendingIntent pi = MediaStore.createTrashRequest(a.getContentResolver(), uris, trash);
            a.startIntentSenderForResult(pi.getIntentSender(), reqCode, null, 0, 0, 0);
        } catch (Throwable t) {
            Ui.toast(a, "系統不允許這次操作：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ("\n" + t.getMessage())));
        }
    }

    public static void requestDelete(Activity a, List<Uri> uris, int reqCode) {
        try {
            PendingIntent pi = MediaStore.createDeleteRequest(a.getContentResolver(), uris);
            a.startIntentSenderForResult(pi.getIntentSender(), reqCode, null, 0, 0, 0);
        } catch (Throwable t) {
            Ui.toast(a, "系統不允許這次操作：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ("\n" + t.getMessage())));
        }
    }

    public static void requestWrite(Activity a, List<Uri> uris, int reqCode) {
        try {
            PendingIntent pi = MediaStore.createWriteRequest(a.getContentResolver(), uris);
            a.startIntentSenderForResult(pi.getIntentSender(), reqCode, null, 0, 0, 0);
        } catch (Throwable t) {
            Ui.toast(a, "系統不允許這次操作：" + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ("\n" + t.getMessage())));
        }
    }

    /** 取得寫入許可之後，把檔案搬到另一個相簿資料夾。 */
    public static int moveAfterGrant(Context c, List<Item> items, String relativePath) {
        int ok = 0;
        ContentResolver r = c.getContentResolver();
        for (int i = 0; i < items.size(); i++) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
                if (r.update(items.get(i).uri(), v, null, null) > 0) ok++;
            } catch (Throwable ignored) { }
        }
        return ok;
    }

    public static String relativePathFor(boolean video, String album) {
        String root = video ? "Movies" : "Pictures";
        String a = album == null ? "" : album.trim();
        if (a.length() == 0) return root + "/";
        return root + "/" + a + "/";
    }
}
