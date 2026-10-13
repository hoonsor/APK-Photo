package com.photobox.sorter;

import android.content.Context;
import android.content.SharedPreferences;

/** 使用者設定：四個滑動方向各自對應的動作、是否顯示影片等。 */
public final class Prefs {

    public static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    public static final int ACT_TRASH = 0;   // 加入待刪清單
    public static final int ACT_KEEP  = 1;   // 標記為已保留
    public static final int ACT_MOVE  = 2;   // 移動到其他相簿
    public static final int ACT_SKIP  = 3;   // 跳過，之後再決定
    public static final int ACT_NONE  = 4;   // 不做任何事

    public static final String[] ACT_NAMES = {
            "加入待刪清單", "標記為已保留", "移動到其他相簿", "跳過（稍後再決定）", "停用這個方向"
    };

    private static final int[] DEFAULTS = { ACT_TRASH, ACT_KEEP, ACT_MOVE, ACT_SKIP };
    private static final String[] KEYS = { "act_left", "act_right", "act_up", "act_down" };
    public static final String[] DIR_NAMES = { "往左滑", "往右滑", "往上滑", "往下滑" };

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("photobox", Context.MODE_PRIVATE);
    }

    public static int action(Context c, int dir) {
        if (dir < 0 || dir > 3) return ACT_NONE;
        return sp(c).getInt(KEYS[dir], DEFAULTS[dir]);
    }

    public static void setAction(Context c, int dir, int act) {
        if (dir < 0 || dir > 3) return;
        sp(c).edit().putInt(KEYS[dir], act).apply();
    }

    public static void resetGestures(Context c) {
        SharedPreferences.Editor e = sp(c).edit();
        for (int i = 0; i < 4; i++) e.putInt(KEYS[i], DEFAULTS[i]);
        e.apply();
    }

    public static int color(int action) {
        switch (action) {
            case ACT_TRASH: return T.TRASH;
            case ACT_KEEP:  return T.KEEP;
            case ACT_MOVE:  return T.MOVE;
            case ACT_SKIP:  return T.SKIP;
            default:        return T.FAINT;
        }
    }

    public static int icon(int action) {
        switch (action) {
            case ACT_TRASH: return Icons.TRASH;
            case ACT_KEEP:  return Icons.CHECK;
            case ACT_MOVE:  return Icons.FOLDER;
            case ACT_SKIP:  return Icons.ARROW_DOWN;
            default:        return Icons.CLOSE;
        }
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
}
