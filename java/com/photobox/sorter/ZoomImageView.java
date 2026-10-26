package com.photobox.sorter;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.ImageView;

/**
 * 可雙指縮放、拖曳、雙擊放大的圖片檢視元件。
 *
 * 手勢規則（倬 2026-10-26 決定）：
 * - 照片「剛好符合螢幕、沒有放大」時：左右滑換張、往上滑、往下滑交給 GestureListener，
 *   拖曳過程中照片會跟著手指走，放開沒過門檻就彈回。
 * - 照片放大時：單指滑動一律是拖曳（看照片的其他部分），不會換張也不會觸發上下動作。
 */
public class ZoomImageView extends ImageView {

    public interface GestureListener {
        /** 1＝下一張、-1＝上一張。 */
        void onPage(int dir);
        void onSwipeUp();
        void onSwipeDown();
        /** 沒放大時拖曳中：axis 為 Swipe.AXIS_X／AXIS_Y，progress 0～1（1＝放開就會觸發）。 */
        void onDragProgress(int axis, float dx, float dy, float progress);
        /** 拖曳結束（不論有沒有觸發動作）。 */
        void onDragEnd();
    }

    public interface TapListener { void onSingleTap(); }

    private final Matrix matrix = new Matrix();
    private final float[] vals = new float[9];
    private ScaleGestureDetector scaleDet;
    private GestureDetector gestureDet;
    private float baseScale = 1f;
    private float maxScale = 6f;
    private GestureListener gestureListener;
    private TapListener tapListener;

    private float downX, downY;
    private boolean armed;
    private int axis = Swipe.AXIS_NONE;
    private VelocityTracker vt;
    /** 換張／加入待刪的飛出動畫進行中：這段時間不接受新的滑動，避免動畫被打斷而漏掉動作。 */
    private boolean flying;
    private final float slop;
    private final float flingV;

    public ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop() * 1.5f;
        flingV = vc.getScaledMinimumFlingVelocity() * 6f;
        init(c);
    }

    public void setGestureListener(GestureListener l) { gestureListener = l; }
    public void setTapListener(TapListener l) { tapListener = l; }

    private void init(Context c) {
        scaleDet = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float f = d.getScaleFactor();
                float cur = currentScale();
                if (cur * f < baseScale * 0.9f) f = baseScale * 0.9f / cur;
                if (cur * f > maxScale) f = maxScale / cur;
                matrix.postScale(f, f, d.getFocusX(), d.getFocusY());
                clamp();
                setImageMatrix(matrix);
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                // 縮到比原本小時放開，彈回剛好符合螢幕，之後的滑動才會換張
                if (currentScale() < baseScale) reset();
            }
        });
        gestureDet = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (currentScale() > baseScale * 1.2f) reset();
                else {
                    float f = baseScale * 2.6f / currentScale();
                    matrix.postScale(f, f, e.getX(), e.getY());
                    clamp();
                    setImageMatrix(matrix);
                }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (tapListener != null) tapListener.onSingleTap();
                return true;
            }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (!atFit()) {
                    matrix.postTranslate(-dx, -dy);
                    clamp();
                    setImageMatrix(matrix);
                    return true;
                }
                return false;
            }
        });
    }

    @Override public void setImageBitmap(android.graphics.Bitmap bm) {
        super.setImageBitmap(bm);
        post(new Runnable() { @Override public void run() { reset(); } });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        reset();
    }

    public void reset() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = getDrawable().getIntrinsicWidth();
        float dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float s = Math.min(getWidth() / dw, getHeight() / dh);
        baseScale = s;
        maxScale = s * 8f;
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate((getWidth() - dw * s) / 2f, (getHeight() - dh * s) / 2f);
        setImageMatrix(matrix);
    }

    /** 照片是否剛好符合螢幕（沒有放大）。只有這時候滑動才會換張或觸發上下動作。 */
    public boolean atFit() {
        return currentScale() <= baseScale * 1.02f;
    }

    private float currentScale() {
        matrix.getValues(vals);
        return vals[Matrix.MSCALE_X];
    }

    private void clamp() {
        if (getDrawable() == null) return;
        RectF r = new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        matrix.postTranslate(dx, dy);
    }

    // ---------- 觸控 ----------

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaleDet.onTouchEvent(e);
        gestureDet.onTouchEvent(e);
        // 照片本身會跟著手指移動，所以用螢幕座標（raw）計算位移，不受 translation 影響
        float rx = e.getRawX(), ry = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!flying) {
                    animate().cancel();
                    setTranslationX(0);
                    setTranslationY(0);
                    setAlpha(1f);
                }
                downX = rx;
                downY = ry;
                axis = Swipe.AXIS_NONE;
                armed = !flying && atFit() && gestureListener != null;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                track(e);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // 第二根手指放下＝要縮放，取消滑動
                if (armed) {
                    armed = false;
                    if (axis != Swipe.AXIS_NONE) springBack();
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!armed || scaleDet.isInProgress()) break;
                track(e);
                float dx = rx - downX, dy = ry - downY;
                if (axis == Swipe.AXIS_NONE) axis = Swipe.lockAxis(dx, dy, slop);
                if (axis == Swipe.AXIS_X) setTranslationX(dx);
                else if (axis == Swipe.AXIS_Y) setTranslationY(dy);
                if (axis != Swipe.AXIS_NONE) {
                    gestureListener.onDragProgress(axis, dx, dy,
                            Swipe.progress(axis, dx, dy, getWidth(), getHeight()));
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (armed && axis != Swipe.AXIS_NONE) {
                    track(e);
                    vt.computeCurrentVelocity(1000);
                    int r = Swipe.decide(axis, rx - downX, ry - downY,
                            vt.getXVelocity(), vt.getYVelocity(), getWidth(), getHeight(), flingV);
                    finishSwipe(r);
                }
                armed = false;
                axis = Swipe.AXIS_NONE;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (armed && axis != Swipe.AXIS_NONE) {
                    springBack();
                    gestureListener.onDragEnd();
                }
                armed = false;
                axis = Swipe.AXIS_NONE;
                break;
        }
        return true;
    }

    private void track(MotionEvent e) {
        if (vt == null) return;
        MotionEvent ev = MotionEvent.obtain(e);
        ev.setLocation(e.getRawX(), e.getRawY());
        vt.addMovement(ev);
        ev.recycle();
    }

    private void finishSwipe(int r) {
        final GestureListener l = gestureListener;
        l.onDragEnd();
        if (r == Swipe.NEXT || r == Swipe.PREV) {
            final int dir = r == Swipe.NEXT ? 1 : -1;
            flying = true;
            animate().translationX(-dir * getWidth()).alpha(0.2f).setDuration(140)
                    .withEndAction(new Runnable() {
                        @Override public void run() { flying = false; l.onPage(dir); fadeBackIn(); }
                    }).start();
        } else if (r == Swipe.UP) {
            flying = true;
            animate().translationY(-getHeight() * 0.9f).alpha(0f).setDuration(170)
                    .withEndAction(new Runnable() {
                        @Override public void run() { flying = false; l.onSwipeUp(); fadeBackIn(); }
                    }).start();
        } else if (r == Swipe.DOWN) {
            springBack();
            l.onSwipeDown();
        } else {
            springBack();
        }
    }

    /** 換張或加入待刪之後，新照片在原位淡入。 */
    private void fadeBackIn() {
        setTranslationX(0);
        setTranslationY(0);
        setAlpha(0f);
        animate().alpha(1f).setDuration(130).start();
    }

    private void springBack() {
        animate().translationX(0).translationY(0).alpha(1f).setDuration(180).start();
    }
}
