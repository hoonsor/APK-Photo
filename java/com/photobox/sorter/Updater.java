package com.photobox.sorter;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * App 內更新（移植自鬧鐘 APP 的 update 套件）：查 GitHub 最新正式版 → 下載 APK 到快取 →
 * 用 PackageInstaller 安裝。網路權限只用在這裡，照片與相簿資料不會經過任何網路。
 * check／download／install 都是同步函式，必須在背景執行緒呼叫。
 */
public final class Updater {
    private Updater() {}

    static final String OWNER = "hoonsor";
    static final String REPO = "APK-Photo";
    /** CI 產生的 APK 檔名前綴（PhotoSortBox-vX.Y.Z.apk） */
    static final String APK_PREFIX = "PhotoSortBox-";
    static final String LATEST_API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/" + OWNER + "/" + REPO + "/releases";

    private static final String TAG = "Updater";
    private static final String PREFS = "app_update";
    private static final String KEY_LAST_DAY = "lastCheckDay";
    private static final String KEY_SKIPPED = "skippedTag";
    private static final String DIR = "update";

    /** GitHub Release 中更新需要的資訊 */
    public static final class Release {
        public String tag;       // 例如 "v1.2.0"
        public String notes;     // 更新說明（Release 內文）
        public String apkName;
        public String apkUrl;
        public long apkSize;     // 未知時為 0
        public String pageUrl;   // 下載失敗時改用瀏覽器

        public String version() {
            return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        }
    }

    /** 檢查結果：release 不為 null 代表有新版；error 不為 null 代表檢查失敗；兩者皆 null 代表已是最新版 */
    public static final class Result {
        public Release release;
        public String error;
    }

    /** 下載中按「取消」 */
    public static final class Cancelled extends Exception {
        Cancelled() { super("已取消"); }
    }

    public interface Progress {
        /** 0–1；大小未知時為 -1（背景執行緒呼叫） */
        void onProgress(float p);
        boolean cancelled();
    }

    public interface ErrorListener { void onInstallError(String text); }

    /** 安裝失敗時通知畫面（主執行緒呼叫）；對話框關閉時設回 null */
    static volatile ErrorListener installErrorListener;

    public static String currentVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** LAB 版（build.sh --lab，套件名 .lab）：Releases 只有正式版，套件名不同裝不上，所以不檢查 */
    static boolean isLab(Context c) {
        return c.getPackageName().endsWith(".lab");
    }

    /** 解析 GET /releases/latest 的回應；草稿、預先發行版、沒有 APK 時回傳 null */
    static Release parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null;
        String tag = o.optString("tag_name");
        if (tag.trim().isEmpty()) return null;
        JSONArray assets = o.optJSONArray("assets");
        if (assets == null) return null;
        String[] names = new String[assets.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject a = assets.optJSONObject(i);
            names[i] = a == null ? null : a.optString("name");
        }
        int idx = UpdateVersion.pickApk(names, APK_PREFIX);
        if (idx < 0) return null;
        JSONObject apk = assets.getJSONObject(idx);
        String url = apk.optString("browser_download_url");
        if (!url.startsWith("https://")) return null;
        Release r = new Release();
        r.tag = tag;
        r.notes = o.isNull("body") ? "" : o.optString("body").trim();
        r.apkName = names[idx];
        r.apkUrl = url;
        r.apkSize = apk.optLong("size", 0L);
        r.pageUrl = o.optString("html_url");
        return r;
    }

    /** 查詢最新正式版；所有錯誤都放進 Result.error，不丟例外 */
    public static Result check(Context c) {
        Result res = new Result();
        if (isLab(c)) {
            res.error = "LAB 版不提供 App 內更新，請用 build.sh --lab 重新建置";
            return res;
        }
        try {
            Release r = parse(httpGet(LATEST_API, c));
            if (r == null) res.error = "最新版沒有可下載的 APK";
            else if (UpdateVersion.isNewer(r.tag, currentVersion(c))) res.release = r;
        } catch (Exception e) {
            Log.w(TAG, "檢查更新失敗", e);
            res.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        return res;
    }

    /**
     * 開啟 App 時的自動檢查：每天最多成功查詢一次、略過使用者選「略過此版」的版本。
     * 有新版時回傳 Release，其他情況（含網路錯誤）一律安靜回傳 null。
     */
    public static Release autoCheck(Context c) {
        if (isLab(c)) return null;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long today = System.currentTimeMillis() / 86400000L + 1; // +1 避免與預設值 0 混淆
        if (p.getLong(KEY_LAST_DAY, 0L) == today) return null;
        Result res = check(c);
        if (res.error != null) return null; // 沒網路時下次開啟再試
        p.edit().putLong(KEY_LAST_DAY, today).apply();
        if (res.release == null) return null;
        return res.release.tag.equals(p.getString(KEY_SKIPPED, null)) ? null : res.release;
    }

    public static void skip(Context c, String tag) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SKIPPED, tag).apply();
    }

    private static String httpGet(String url, Context c) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", REPO + "/" + currentVersion(c));
        try {
            int code = conn.getResponseCode();
            if (code == 404) throw new IllegalStateException("還沒有發布任何正式版");
            if (code == 403 || code == 429) throw new IllegalStateException("GitHub 查詢次數已達上限，請稍後再試");
            if (code < 200 || code > 299) throw new IllegalStateException("GitHub 回應 " + code);
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    // ---------- 安裝權限 ----------

    public static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    /** 開啟本 App 的「安裝不明應用程式」設定頁 */
    public static void openInstallPermissionSettings(Context c) {
        Uri pkg = Uri.parse("package:" + c.getPackageName());
        try {
            c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            // 少數廠牌沒有這個頁面，退回一般的應用程式資訊頁
            try {
                c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    // ---------- 下載與安裝 ----------

    /** 清掉之前下載的 APK（App 啟動或下載前呼叫） */
    public static void cleanup(Context c) {
        File dir = new File(c.getCacheDir(), DIR);
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir.delete();
    }

    /** 下載 APK；下載完確認套件名稱相同、版本比目前新，避免裝到錯的檔案 */
    public static File download(Context c, Release r, Progress prog) throws Exception {
        cleanup(c);
        File dir = new File(c.getCacheDir(), DIR);
        dir.mkdirs();
        File part = new File(dir, "download.part");
        HttpURLConnection conn = (HttpURLConnection) new URL(r.apkUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", REPO + "/" + currentVersion(c));
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) throw new IllegalStateException("下載失敗（HTTP " + code + "）");
            long total = conn.getContentLengthLong();
            if (total <= 0) total = r.apkSize;
            InputStream in = conn.getInputStream();
            OutputStream out = new FileOutputStream(part);
            try {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while (true) {
                    if (prog.cancelled()) throw new Cancelled();
                    n = in.read(buf);
                    if (n < 0) break;
                    out.write(buf, 0, n);
                    done += n;
                    prog.onProgress(total > 0 ? Math.min(1f, (float) done / total) : -1f);
                }
            } finally {
                out.close();
                in.close();
            }
            if (r.apkSize > 0 && part.length() != r.apkSize) throw new IllegalStateException("下載不完整，請再試一次");
        } catch (Exception e) {
            part.delete();
            throw e;
        } finally {
            conn.disconnect();
        }
        File apk = new File(dir, "update.apk");
        if (!part.renameTo(apk)) throw new IllegalStateException("無法儲存下載的檔案");
        PackageInfo info = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (info == null) throw new IllegalStateException("下載的檔案不是有效的 APK");
        if (!c.getPackageName().equals(info.packageName)) throw new IllegalStateException("APK 套件名稱不符（" + info.packageName + "）");
        if (!UpdateVersion.isNewer(info.versionName, currentVersion(c))) {
            throw new IllegalStateException("APK 版本（" + info.versionName + "）沒有比目前新");
        }
        return apk;
    }

    /** 把 APK 寫進 PackageInstaller session 並送出；系統會跳出安裝確認畫面 */
    public static void install(Context c, File apk) throws Exception {
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        params.setSize(apk.length());
        int id = installer.createSession(params);
        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(id);
            InputStream in = new FileInputStream(apk);
            OutputStream out = session.openWrite("base.apk", 0, apk.length());
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                session.fsync(out);
            } finally {
                out.close();
                in.close();
            }
            Intent intent = new Intent(c, UpdateInstallReceiver.class).setPackage(c.getPackageName());
            // Android 12+ 必須 MUTABLE，系統才能把安裝結果放進 extras
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, intent, flags);
            session.commit(pending.getIntentSender());
            session.close();
            session = null;
        } catch (Exception e) {
            if (session != null) session.close();
            try { installer.abandonSession(id); } catch (Exception ignored) { }
            throw e;
        }
    }

    static void reportFailure(final Context c, int status, String message) {
        final String text;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return; // 使用者按了取消，不用提示
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                text = "安裝失敗：新版的簽章與目前安裝的不同（v1.1.8 以前用的是舊金鑰）。"
                        + "請先在「更多 → 備份與還原」匯出備份，解除安裝後再安裝新版，然後匯入備份。";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                text = "安裝失敗：手機儲存空間不足";
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                text = "安裝被系統阻擋，請確認已允許「安裝不明應用程式」";
                break;
            default:
                text = "安裝失敗：" + (message != null ? message : "未知錯誤（" + status + "）");
        }
        Log.w(TAG, "安裝失敗 status=" + status + " message=" + message);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                ErrorListener l = installErrorListener;
                if (l != null) l.onInstallError(text);
                Toast.makeText(c, text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
