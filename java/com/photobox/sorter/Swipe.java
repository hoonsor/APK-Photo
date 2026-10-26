package com.photobox.sorter;

/**
 * 單張檢視的滑動手勢判斷。
 *
 * 只有照片在「剛好符合螢幕、沒有放大」時才會用到：左右滑換張、往上滑加入待刪、往下滑編輯。
 * 照片放大時滑動一律是拖曳，由 ZoomImageView 自己處理，不會走到這裡。
 *
 * 刻意不引用任何 Android 類別，可以在桌機 JVM 上獨立測試。
 */
public final class Swipe {

    private Swipe() {}

    /** 放開手指時的結果。 */
    public static final int NONE = 0, NEXT = 1, PREV = 2, UP = 3, DOWN = 4;

    /** 拖曳鎖定的方向：一旦判斷是橫向或直向，整個手勢都只沿那個方向移動，避免斜滑時兩邊都觸發。 */
    public static final int AXIS_NONE = 0, AXIS_X = 1, AXIS_Y = 2;

    /** 左右換張：要滑過畫面寬度的這個比例。 */
    public static final float TH_X = 0.22f;
    /** 上下動作：要滑過畫面高度的這個比例（比左右小，因為手機直拿時上下距離長）。 */
    public static final float TH_Y = 0.16f;

    /** 手指移動超過 slop 之後決定鎖定哪個方向；還沒超過時回傳 AXIS_NONE。 */
    public static int lockAxis(float dx, float dy, float slop) {
        if (Math.abs(dx) < slop && Math.abs(dy) < slop) return AXIS_NONE;
        return Math.abs(dx) >= Math.abs(dy) ? AXIS_X : AXIS_Y;
    }

    /**
     * 放開時要做什麼。
     * 距離超過門檻，或快速甩動（速度超過 flingV、方向一致、且至少滑了門檻的 40%）都算數。
     *
     * @param dx,dy  從按下到放開的位移（px）；dx &lt; 0 是手指往左
     * @param vx,vy  放開時的速度（px/s）
     * @param w,h    畫面寬高（px）
     */
    public static int decide(int axis, float dx, float dy, float vx, float vy,
                             float w, float h, float flingV) {
        if (axis == AXIS_X) {
            float th = w * TH_X;
            if (passed(dx, vx, th, flingV)) return dx < 0 ? NEXT : PREV;
        } else if (axis == AXIS_Y) {
            float th = h * TH_Y;
            if (passed(dy, vy, th, flingV)) return dy < 0 ? UP : DOWN;
        }
        return NONE;
    }

    private static boolean passed(float d, float v, float th, float flingV) {
        if (Math.abs(d) > th) return true;
        boolean sameDir = (d < 0 && v < 0) || (d > 0 && v > 0);
        return sameDir && Math.abs(v) > flingV && Math.abs(d) > th * 0.4f;
    }

    /** 拖曳進度 0～1（1＝已過門檻），給「放開加入待刪／編輯」提示淡入用。 */
    public static float progress(int axis, float dx, float dy, float w, float h) {
        float p;
        if (axis == AXIS_X) p = w <= 0 ? 0 : Math.abs(dx) / (w * TH_X);
        else if (axis == AXIS_Y) p = h <= 0 ? 0 : Math.abs(dy) / (h * TH_Y);
        else p = 0;
        return Math.min(1f, p);
    }
}
