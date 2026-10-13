package com.photobox.sorter;

import android.content.ContentUris;
import android.net.Uri;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Item {
    public long id;
    public String name = "";
    public String bucket = "";
    public long timeMs;
    public long size;
    public int width, height, orientation;
    public boolean video;
    public String mime = "";
    /** 例如 "Pictures/LINE/"（RELATIVE_PATH），查不到時為空字串。 */
    public String relPath = "";

    /** 年月 key，例如 202609 */
    public int ym;
    public int year, month;

    private static final SimpleDateFormat FULL =
            new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN);
    private static final SimpleDateFormat DAY =
            new SimpleDateFormat("yyyy/MM/dd", Locale.TAIWAN);

    /**
     * 媒體項目 URI。
     * 注意：必須用 images/media 或 video/media 這種「媒體項目」URI，
     * MediaStore 的 createTrashRequest / createDeleteRequest / createWriteRequest
     * 不接受 Files 表的 file/<id> URI（會丟 IllegalArgumentException）。
     */
    public Uri uri() {
        Uri base = video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        return ContentUris.withAppendedId(base, id);
    }

    /** 查詢用的 Files 表 URI。 */
    public Uri fileUri() {
        return ContentUris.withAppendedId(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), id);
    }

    public String timeText() { return FULL.format(new Date(timeMs)); }
    public String dayText()  { return DAY.format(new Date(timeMs)); }

    public String sizeText() { return formatSize(size); }

    public static String formatSize(long b) {
        if (b <= 0) return "0 B";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public String dimText() {
        if (width > 0 && height > 0) return width + " × " + height;
        return "－";
    }

    /**
     * 從 LINE 存下來的照片／影片。
     * LINE 會存到 Pictures/LINE、Movies/LINE（部分版本為 DCIM/LINE），資料夾名稱就是「LINE」。
     */
    public boolean isLine() {
        String p = relPath == null ? "" : relPath.toLowerCase(Locale.US);
        if (p.startsWith("line/") || p.contains("/line/")) return true;
        String b = bucket == null ? "" : bucket.trim().toLowerCase(Locale.US);
        return b.equals("line") || b.startsWith("line ") || b.startsWith("line_");
    }

    public boolean isScreenshot() {
        String b = bucket == null ? "" : bucket.toLowerCase(Locale.US);
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        return b.contains("screenshot") || b.contains("截圖") || b.contains("截屏")
                || n.startsWith("screenshot") || n.startsWith("screen_");
    }
}
