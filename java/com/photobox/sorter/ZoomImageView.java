package com.photobox.sorter;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** 可雙指縮放、拖曳、雙擊放大的圖片檢視元件。 */
public class ZoomImageView extends ImageView {

    public interface SwipeListener { void onSwipe(int dir); }   // -1 上一張, 1 下一張
    public interface TapListener { void onSingleTap(); }

    private final Matrix matrix = new Matrix();
    private final float[] vals = new float[9];
    private ScaleGestureDetector scaleDet;
    private GestureDetector gestureDet;
    private float baseScale = 1f;
    private float maxScale = 6f;
    private SwipeListener swipeListener;
    private TapListener tapListener;
    private float downX;
    private boolean swipeArmed;

    public ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        init(c);
    }

    public void setSwipeListener(SwipeListener l) { swipeListener = l; }
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
                if (currentScale() > baseScale * 1.02f) {
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

    @Override public boolean onTouchEvent(MotionEvent e) {
        scaleDet.onTouchEvent(e);
        gestureDet.onTouchEvent(e);
        boolean atFit = currentScale() <= baseScale * 1.02f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                swipeArmed = atFit;
                break;
            case MotionEvent.ACTION_UP:
                if (swipeArmed && atFit && swipeListener != null) {
                    float d = e.getX() - downX;
                    if (Math.abs(d) > getWidth() * 0.22f) {
                        swipeListener.onSwipe(d < 0 ? 1 : -1);
                    }
                }
                swipeArmed = false;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                swipeArmed = false;
                break;
        }
        return true;
    }
}
