package com.photobox.sorter;

import android.content.Context;
import android.content.SharedPreferences;

/** 使用者設定：是否顯示影片、震動、刪除方式、編輯器工具等。單張檢視的滑動手勢是固定的（v1.3.0 起），不再可設定。 */
public final class Prefs {

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("photobox", Context.MODE_PRIVATE);
    }

    public static boolean showVideo(Context c) { return sp(c).getBoolean("show_video", true); }
    public static void setShowVideo(Context c, boolean v) { sp(c).edit().putBoolean("show_video", v).apply(); }

    public static boolean hapticOn(Context c) { return sp(c).getBoolean("haptic", true); }
    public static void setHaptic(Context c, boolean v) { sp(c).edit().putBoolean("haptic", v).apply(); }

    /** 照片牆每列張數（雙指縮放調整，所有照片牆共用）。 */
    public static int gridCols(Context c) {
        int n = sp(c).getInt("grid_cols", 4);
        return Math.max(MediaGrid.MIN_COLS, Math.min(MediaGrid.MAX_COLS, n));
    }
    public static void setGridCols(Context c, int n) { sp(c).edit().putInt("grid_cols", n).apply(); }

    public static boolean permanentDelete(Context c) { return sp(c).getBoolean("perm_delete", false); }
    public static void setPermanentDelete(Context c, boolean v) { sp(c).edit().putBoolean("perm_delete", v).apply(); }

    /** 單張檢視的手勢說明只在第一次打開時提示一次。 */
    public static boolean viewerHintShown(Context c) { return sp(c).getBoolean("viewer_hint_v130", false); }
    public static void setViewerHintShown(Context c) { sp(c).edit().putBoolean("viewer_hint_v130", true).apply(); }

    /** 編輯器：記住上次用的工具、顏色、粗細。 */
    public static int editorTool(Context c) { return sp(c).getInt("editor_tool", 0); }
    public static void setEditorTool(Context c, int t) { sp(c).edit().putInt("editor_tool", t).apply(); }
    public static int editorColor(Context c, int def) { return sp(c).getInt("editor_color", def); }
    public static void setEditorColor(Context c, int v) { sp(c).edit().putInt("editor_color", v).apply(); }
    public static int editorSize(Context c, int def) { return sp(c).getInt("editor_size", def); }
    public static void setEditorSize(Context c, int v) { sp(c).edit().putInt("editor_size", v).apply(); }
}
