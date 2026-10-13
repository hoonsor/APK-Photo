package com.photobox.sorter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 相簿與整理標記的匯出／匯入（JSON，透過系統檔案選擇器存取，不需要任何儲存權限）。
 *
 * 只存「清單」不存照片：每個項目記錄 id、檔名、大小、拍攝時間，還原時由 BackupMatch 比對。
 */
public final class Backup {

    private Backup() {}

    private static final String FORMAT = "photobox-backup";
    private static final int VERSION = 1;

    public static long lastExport(Context c) {
        return c.getSharedPreferences("photobox", Context.MODE_PRIVATE).getLong("last_export", 0);
    }

    // ---------- 匯出 ----------

    public static void exportTo(MainActivity a, Uri uri) {
        OutputStream os = null;
        try {
            Store s = Store.get();
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", VERSION);
            root.put("app", appVersion(a));
            root.put("exported", System.currentTimeMillis());

            // 媒體表：相簿與標記都用索引引用，避免重複寫檔名
            JSONArray media = new JSONArray();
            final HashMap<Long, Integer> ref = new HashMap<Long, Integer>();

            JSONArray albums = new JSONArray();
            ArrayList<Albums.Album> list = Albums.get().ordered();
            int albumCount = 0, itemCount = 0;
            for (int i = 0; i < list.size(); i++) {
                Albums.Album al = list.get(i);
                JSONObject o = new JSONObject();
                o.put("name", al.name);
                o.put("kind", al.kind);
                o.put("created", al.created);
                JSONArray items = new JSONArray();
                for (Long id : al.items.keySet()) {
                    Item it = s.byId.get(id);
                    if (it == null) continue;   // 已不存在的不匯出
                    items.put(refOf(it, media, ref));
                    itemCount++;
                }
                o.put("items", items);
                Item cover = al.cover > 0 ? s.byId.get(al.cover) : null;
                if (cover != null && al.items.containsKey(al.cover)) o.put("cover", refOf(cover, media, ref));
                albums.put(o);
                if (!al.isFav()) albumCount++;
            }
            root.put("albums", albums);

            JSONArray states = new JSONArray();
            for (java.util.Map.Entry<Long, Integer> e : s.state.entrySet()) {
                Item it = s.byId.get(e.getKey());
                if (it == null || e.getValue() == Db.STATE_NONE) continue;
                JSONArray pair = new JSONArray();
                pair.put(refOf(it, media, ref));
                pair.put(e.getValue().intValue());
                states.put(pair);
            }
            root.put("states", states);
            root.put("media", media);

            os = a.getContentResolver().openOutputStream(uri, "wt");
            if (os == null) throw new Exception("無法寫入");
            os.write(root.toString(1).getBytes(StandardCharsets.UTF_8));
            os.flush();
            a.getSharedPreferences("photobox", Context.MODE_PRIVATE).edit()
                    .putLong("last_export", System.currentTimeMillis()).apply();
            Ui.toast(a, "已匯出：" + albumCount + " 本相簿、" + itemCount + " 筆相簿內容、"
                    + states.length() + " 個整理標記");
            if (a.morePage != null) a.morePage.refreshBackupInfo();
        } catch (Throwable t) {
            Ui.toast(a, "匯出失敗：" + t.getMessage());
        } finally {
            if (os != null) try { os.close(); } catch (Throwable ignored) { }
        }
    }

    private static int refOf(Item it, JSONArray media, HashMap<Long, Integer> ref) throws Exception {
        Integer r = ref.get(it.id);
        if (r != null) return r;
        JSONArray m = new JSONArray();
        m.put(it.id);
        m.put(it.name);
        m.put(it.size);
        m.put(it.timeMs);
        media.put(m);
        int idx = media.length() - 1;
        ref.put(it.id, idx);
        return idx;
    }

    private static String appVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Throwable t) { return "?"; }
    }

    // ---------- 匯入 ----------

    public static void importFrom(final MainActivity a, Uri uri) {
        final JSONObject root;
        final long[] mapped;
        try {
            root = new JSONObject(readAll(a, uri));
            if (!FORMAT.equals(root.optString("format"))) {
                Ui.toast(a, "這不是照片整理箱的備份檔");
                return;
            }
            JSONArray media = root.getJSONArray("media");
            int n = media.length();
            long[] bId = new long[n], bSize = new long[n], bTime = new long[n];
            String[] bName = new String[n];
            for (int i = 0; i < n; i++) {
                JSONArray m = media.getJSONArray(i);
                bId[i] = m.getLong(0);
                bName[i] = m.getString(1);
                bSize[i] = m.getLong(2);
                bTime[i] = m.getLong(3);
            }
            ArrayList<Item> all = Store.get().all;
            int nc = all.size();
            long[] cId = new long[nc], cSize = new long[nc], cTime = new long[nc];
            String[] cName = new String[nc];
            for (int j = 0; j < nc; j++) {
                Item it = all.get(j);
                cId[j] = it.id; cName[j] = it.name; cSize[j] = it.size; cTime[j] = it.timeMs;
            }
            mapped = BackupMatch.match(bId, bName, bSize, bTime, cId, cName, cSize, cTime);
        } catch (Throwable t) {
            Ui.toast(a, "讀取備份失敗：" + t.getMessage());
            return;
        }

        int ok = 0;
        for (int i = 0; i < mapped.length; i++) if (mapped[i] >= 0) ok++;
        JSONArray albums = root.optJSONArray("albums");
        JSONArray states = root.optJSONArray("states");
        int na = 0;
        if (albums != null) for (int i = 0; i < albums.length(); i++) {
            if (albums.optJSONObject(i) != null && albums.optJSONObject(i).optInt("kind") != Albums.KIND_FAV) na++;
        }
        String msg = "備份內容：" + na + " 本相簿、" + (states == null ? 0 : states.length()) + " 個整理標記。\n\n"
                + "在這支手機上找到 " + ok + " / " + mapped.length + " 個對應的照片或影片"
                + (ok < mapped.length ? "（其餘可能已刪除，或還沒從舊手機搬過來）" : "") + "。\n\n"
                + "還原會「合併」到目前的資料：同名相簿把內容併在一起，不會刪掉任何現有相簿。";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("從備份還原？")
                .setMessage(msg)
                .setPositiveButton("還原", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { apply(a, root, mapped); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void apply(MainActivity a, JSONObject root, long[] mapped) {
        int albumsTouched = 0, added = 0, marked = 0;
        try {
            Albums A = Albums.get();
            JSONArray albums = root.optJSONArray("albums");
            if (albums != null) for (int i = 0; i < albums.length(); i++) {
                JSONObject o = albums.getJSONObject(i);
                String name = o.optString("name", "").trim();
                int kind = o.optInt("kind", Albums.KIND_NORMAL);
                Albums.Album target;
                if (kind == Albums.KIND_FAV) {
                    target = A.ensureFavorites(a);
                } else {
                    if (name.length() == 0) name = A.uniqueName("還原的相簿");
                    target = null;
                    ArrayList<Albums.Album> ex = A.normal();
                    for (int k = 0; k < ex.size(); k++) if (ex.get(k).name.equals(name)) { target = ex.get(k); break; }
                    if (target == null) target = A.create(a, name);
                }
                if (target == null) continue;
                albumsTouched++;
                JSONArray items = o.optJSONArray("items");
                ArrayList<Long> ids = new ArrayList<Long>();
                if (items != null) for (int k = 0; k < items.length(); k++) {
                    int r = items.optInt(k, -1);
                    if (r >= 0 && r < mapped.length && mapped[r] >= 0) ids.add(mapped[r]);
                }
                added += A.addIds(a, target, ids);
                if (o.has("cover") && target.cover <= 0) {
                    int r = o.optInt("cover", -1);
                    if (r >= 0 && r < mapped.length && mapped[r] >= 0) A.setCover(a, target, mapped[r]);
                }
            }
            JSONArray states = root.optJSONArray("states");
            if (states != null) {
                ArrayList<Item> kept = new ArrayList<Item>(), pend = new ArrayList<Item>();
                for (int i = 0; i < states.length(); i++) {
                    JSONArray pair = states.getJSONArray(i);
                    int r = pair.getInt(0), st = pair.getInt(1);
                    if (r < 0 || r >= mapped.length || mapped[r] < 0) continue;
                    Item it = Store.get().byId.get(mapped[r]);
                    if (it == null) continue;
                    if (st == Db.STATE_KEPT) kept.add(it); else if (st == Db.STATE_PENDING) pend.add(it);
                }
                if (!kept.isEmpty()) Store.get().setStates(a, kept, Db.STATE_KEPT);
                if (!pend.isEmpty()) Store.get().setStates(a, pend, Db.STATE_PENDING);
                marked = kept.size() + pend.size();
            }
        } catch (Throwable t) {
            Ui.toast(a, "還原到一半發生錯誤：" + t.getMessage());
        }
        Store.get().notifyChanged();
        Ui.toast(a, "已還原：" + albumsTouched + " 本相簿（新加入 " + added + " 筆）、" + marked + " 個整理標記");
    }

    private static String readAll(Context c, Uri uri) throws Exception {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("無法開啟檔案");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                total += n;
                if (total > 64L * 1024 * 1024) throw new Exception("檔案太大，不像是備份檔");
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }
}
