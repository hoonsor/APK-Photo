package com.photobox.sorter;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** 以 Path 手繪的向量圖示組，24x24 座標系，可任意上色與縮放。 */
public final class Icons {

    public static final int SORT = 0, GRID = 1, TRASH = 2, SLIDERS = 3, BACK = 4, SEARCH = 5,
            CLOSE = 6, UNDO = 7, STAR = 8, FOLDER = 9, SHARE = 10, INFO = 11, PLAY = 12,
            COPIES = 13, CHECK = 14, CHEVRON = 15, ARROW_DOWN = 16, IMAGE = 17, CALENDAR = 18,
            ARROW_LEFT = 19, ARROW_RIGHT = 20, ARROW_UP = 21, PLUS = 22, RESTORE = 23,
            MENU = 24, MORE = 25, STAR_O = 26, PIN = 27, PHONE = 28, ALERT = 29, ALBUM = 30,
            BACKUP = 31, HELP = 32, MINUS = 33, ALBUM_ADD = 34, PAUSE = 35;

    private Icons() {}

    public static Drawable get(int kind, int color) {
        return new IconDrawable(kind, color);
    }

    // ---------- Drawable ----------

    public static class IconDrawable extends Drawable {
        private final Path fill = new Path();
        private final Path stroke = new Path();
        private final Paint pf = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Path tf = new Path();
        private final Path ts = new Path();
        private float strokeW = 2.1f;

        IconDrawable(int kind, int color) {
            pf.setStyle(Paint.Style.FILL);
            pf.setColor(color);
            ps.setStyle(Paint.Style.STROKE);
            ps.setColor(color);
            ps.setStrokeCap(Paint.Cap.ROUND);
            ps.setStrokeJoin(Paint.Join.ROUND);
            build(kind, fill, stroke);
        }

        public void setColor(int color) {
            pf.setColor(color);
            ps.setColor(color);
            invalidateSelf();
        }

        @Override public void draw(Canvas c) {
            RectF b = new RectF(getBounds());
            if (b.width() <= 0 || b.height() <= 0) return;
            float s = Math.min(b.width(), b.height()) / 24f;
            m.reset();
            m.setScale(s, s);
            m.postTranslate(b.left + (b.width() - 24f * s) / 2f,
                            b.top + (b.height() - 24f * s) / 2f);
            fill.transform(m, tf);
            stroke.transform(m, ts);
            ps.setStrokeWidth(strokeW * s);
            if (!tf.isEmpty()) c.drawPath(tf, pf);
            if (!ts.isEmpty()) c.drawPath(ts, ps);
        }

        @Override public void setAlpha(int a) { pf.setAlpha(a); ps.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { pf.setColorFilter(cf); ps.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return 24; }
        @Override public int getIntrinsicHeight() { return 24; }

        private void build(int kind, Path f, Path s) {
            switch (kind) {
                case SORT: {
                    Path back = new Path();
                    rr(back, 5.5f, 5.5f, 15.5f, 19.5f, 2.2f);
                    Matrix r = new Matrix();
                    r.setRotate(-14f, 12f, 12f);
                    back.transform(r);
                    f.addPath(back);
                    Path front = new Path();
                    rr(front, 8.5f, 4.5f, 19f, 19.5f, 2.4f);
                    hole(front, 10.3f, 6.3f, 17.2f, 17.7f, 1.4f);
                    f.addPath(front);
                    break;
                }
                case GRID:
                    rr(f, 3f, 3f, 10.6f, 10.6f, 2.2f);
                    rr(f, 13.4f, 3f, 21f, 10.6f, 2.2f);
                    rr(f, 3f, 13.4f, 10.6f, 21f, 2.2f);
                    rr(f, 13.4f, 13.4f, 21f, 21f, 2.2f);
                    break;
                case TRASH: {
                    rr(f, 3.6f, 5.2f, 20.4f, 7.4f, 1.1f);
                    rr(f, 9f, 2.4f, 15f, 4.6f, 1f);
                    Path body = new Path();
                    rr(body, 5.6f, 8.6f, 18.4f, 21.6f, 2.4f);
                    hole(body, 9f, 11.2f, 10.6f, 19f, 0.8f);
                    hole(body, 13.4f, 11.2f, 15f, 19f, 0.8f);
                    f.addPath(body);
                    break;
                }
                case SLIDERS: {
                    rr(f, 3f, 6f, 21f, 7.7f, 0.85f);
                    rr(f, 3f, 11.15f, 21f, 12.85f, 0.85f);
                    rr(f, 3f, 16.3f, 21f, 18f, 0.85f);
                    ring(f, 8.6f, 6.85f, 3.1f, 1.6f);
                    ring(f, 15.4f, 12f, 3.1f, 1.6f);
                    ring(f, 10.2f, 17.15f, 3.1f, 1.6f);
                    break;
                }
                case BACK:
                    s.moveTo(15.2f, 4.6f); s.lineTo(7.6f, 12f); s.lineTo(15.2f, 19.4f);
                    break;
                case CHEVRON:
                    s.moveTo(8.8f, 4.6f); s.lineTo(16.4f, 12f); s.lineTo(8.8f, 19.4f);
                    break;
                case SEARCH: {
                    ring(f, 10.6f, 10.6f, 6.4f, 4.5f);
                    Path h = new Path();
                    rr(h, 14.6f, 16.1f, 21.4f, 18.3f, 1.1f);
                    Matrix r = new Matrix();
                    r.setRotate(45f, 15.2f, 17.2f);
                    h.transform(r);
                    f.addPath(h);
                    break;
                }
                case CLOSE: {
                    Path a = new Path();
                    rr(a, 4.4f, 10.9f, 19.6f, 13.1f, 1.1f);
                    Path b = new Path(a);
                    Matrix r = new Matrix();
                    r.setRotate(45f, 12f, 12f);
                    a.transform(r);
                    r.setRotate(-45f, 12f, 12f);
                    b.transform(r);
                    f.addPath(a); f.addPath(b);
                    break;
                }
                case UNDO:
                case RESTORE: {
                    RectF o = new RectF(4.2f, 4.2f, 19.8f, 19.8f);
                    s.addArc(o, 125f, 265f);
                    double ang = Math.toRadians(125f);
                    float cx = 12f + 7.8f * (float) Math.cos(ang);
                    float cy = 12f + 7.8f * (float) Math.sin(ang);
                    f.moveTo(cx - 3.4f, cy - 1.6f);
                    f.lineTo(cx + 1.4f, cy + 2.6f);
                    f.lineTo(cx - 3.9f, cy + 3.6f);
                    f.close();
                    break;
                }
                case STAR: {
                    for (int i = 0; i < 10; i++) {
                        double a = Math.toRadians(-90 + i * 36);
                        float r = (i % 2 == 0) ? 9.2f : 4.0f;
                        float x = 12f + r * (float) Math.cos(a);
                        float y = 12.4f + r * (float) Math.sin(a);
                        if (i == 0) f.moveTo(x, y); else f.lineTo(x, y);
                    }
                    f.close();
                    break;
                }
                case FOLDER: {
                    f.moveTo(3.2f, 7.2f);
                    f.cubicTo(3.2f, 6f, 4f, 5.4f, 5f, 5.4f);
                    f.lineTo(9.4f, 5.4f);
                    f.lineTo(11.6f, 7.8f);
                    f.lineTo(19f, 7.8f);
                    f.cubicTo(20f, 7.8f, 20.8f, 8.5f, 20.8f, 9.6f);
                    f.lineTo(20.8f, 17.6f);
                    f.cubicTo(20.8f, 18.7f, 20f, 19.4f, 19f, 19.4f);
                    f.lineTo(5f, 19.4f);
                    f.cubicTo(4f, 19.4f, 3.2f, 18.7f, 3.2f, 17.6f);
                    f.close();
                    break;
                }
                case SHARE: {
                    f.addCircle(6.2f, 12f, 2.9f, Path.Direction.CW);
                    f.addCircle(17.4f, 6.2f, 2.9f, Path.Direction.CW);
                    f.addCircle(17.4f, 17.8f, 2.9f, Path.Direction.CW);
                    s.moveTo(8.6f, 10.8f); s.lineTo(15f, 7.4f);
                    s.moveTo(8.6f, 13.2f); s.lineTo(15f, 16.6f);
                    break;
                }
                case INFO: {
                    ring(f, 12f, 12f, 9.2f, 7.3f);
                    f.addCircle(12f, 7.6f, 1.35f, Path.Direction.CW);
                    rr(f, 10.8f, 10.4f, 13.2f, 17.4f, 1.2f);
                    break;
                }
                case PLAY:
                    f.moveTo(8.6f, 5.6f); f.lineTo(18.6f, 12f); f.lineTo(8.6f, 18.4f);
                    f.close();
                    break;
                case COPIES: {
                    Path a = new Path();
                    rr(a, 3.2f, 3.2f, 15.2f, 15.2f, 2.4f);
                    hole(a, 5.2f, 5.2f, 13.2f, 13.2f, 1.4f);
                    Path b = new Path();
                    rr(b, 8.8f, 8.8f, 20.8f, 20.8f, 2.4f);
                    hole(b, 10.8f, 10.8f, 18.8f, 18.8f, 1.4f);
                    f.addPath(a); f.addPath(b);
                    break;
                }
                case CHECK:
                    s.moveTo(4.8f, 12.4f); s.lineTo(9.8f, 17.4f); s.lineTo(19.2f, 6.8f);
                    break;
                case ARROW_DOWN:
                    s.moveTo(12f, 4.6f); s.lineTo(12f, 18.4f);
                    f.moveTo(5.6f, 13.2f); f.lineTo(18.4f, 13.2f); f.lineTo(12f, 20.4f);
                    f.close();
                    break;
                case ARROW_UP:
                    s.moveTo(12f, 19.4f); s.lineTo(12f, 5.6f);
                    f.moveTo(5.6f, 10.8f); f.lineTo(18.4f, 10.8f); f.lineTo(12f, 3.6f);
                    f.close();
                    break;
                case ARROW_LEFT:
                    s.moveTo(19.4f, 12f); s.lineTo(5.6f, 12f);
                    f.moveTo(10.8f, 5.6f); f.lineTo(10.8f, 18.4f); f.lineTo(3.6f, 12f);
                    f.close();
                    break;
                case ARROW_RIGHT:
                    s.moveTo(4.6f, 12f); s.lineTo(18.4f, 12f);
                    f.moveTo(13.2f, 5.6f); f.lineTo(13.2f, 18.4f); f.lineTo(20.4f, 12f);
                    f.close();
                    break;
                case PLUS:
                    rr(f, 10.8f, 4.4f, 13.2f, 19.6f, 1.2f);
                    rr(f, 4.4f, 10.8f, 19.6f, 13.2f, 1.2f);
                    break;
                case CALENDAR: {
                    Path b = new Path();
                    rr(b, 3.4f, 5f, 20.6f, 20.8f, 2.6f);
                    hole(b, 5.4f, 9.6f, 18.6f, 18.8f, 1.2f);
                    f.addPath(b);
                    rr(f, 7f, 2.6f, 8.8f, 6.6f, 0.9f);
                    rr(f, 15.2f, 2.6f, 17f, 6.6f, 0.9f);
                    f.addCircle(9f, 13.2f, 1.25f, Path.Direction.CW);
                    f.addCircle(12f, 13.2f, 1.25f, Path.Direction.CW);
                    f.addCircle(15f, 13.2f, 1.25f, Path.Direction.CW);
                    f.addCircle(9f, 16.6f, 1.25f, Path.Direction.CW);
                    f.addCircle(12f, 16.6f, 1.25f, Path.Direction.CW);
                    break;
                }
                case MENU:
                    rr(f, 3.6f, 5.4f, 20.4f, 7.4f, 1f);
                    rr(f, 3.6f, 11f, 20.4f, 13f, 1f);
                    rr(f, 3.6f, 16.6f, 20.4f, 18.6f, 1f);
                    break;
                case MORE:
                    f.addCircle(12f, 5.4f, 1.9f, Path.Direction.CW);
                    f.addCircle(12f, 12f, 1.9f, Path.Direction.CW);
                    f.addCircle(12f, 18.6f, 1.9f, Path.Direction.CW);
                    break;
                case STAR_O: {
                    Path st = new Path();
                    for (int i = 0; i < 10; i++) {
                        double a = Math.toRadians(-90 + i * 36);
                        float r = (i % 2 == 0) ? 9.2f : 4.0f;
                        float x = 12f + r * (float) Math.cos(a);
                        float y = 12.4f + r * (float) Math.sin(a);
                        if (i == 0) st.moveTo(x, y); else st.lineTo(x, y);
                    }
                    st.close();
                    s.addPath(st);
                    strokeW = 1.8f;
                    break;
                }
                case PIN: {
                    Path pin = new Path();
                    pin.moveTo(12f, 21.6f);
                    pin.cubicTo(7.2f, 15.6f, 4.8f, 12.2f, 4.8f, 9.4f);
                    pin.cubicTo(4.8f, 5.4f, 8f, 2.4f, 12f, 2.4f);
                    pin.cubicTo(16f, 2.4f, 19.2f, 5.4f, 19.2f, 9.4f);
                    pin.cubicTo(19.2f, 12.2f, 16.8f, 15.6f, 12f, 21.6f);
                    pin.close();
                    pin.addCircle(12f, 9.4f, 2.8f, Path.Direction.CCW);
                    f.addPath(pin);
                    break;
                }
                case PHONE: {
                    Path ph = new Path();
                    rr(ph, 6.2f, 2.2f, 17.8f, 21.8f, 2.6f);
                    hole(ph, 8f, 4.6f, 16f, 17.4f, 0.8f);
                    f.addPath(ph);
                    break;
                }
                case ALERT: {
                    Path tri = new Path();
                    tri.moveTo(12f, 2.8f); tri.lineTo(22f, 20.6f); tri.lineTo(2f, 20.6f); tri.close();
                    tri.addRoundRect(new RectF(10.9f, 8.6f, 13.1f, 15.4f), 1f, 1f, Path.Direction.CCW);
                    tri.addCircle(12f, 17.7f, 1.25f, Path.Direction.CCW);
                    f.addPath(tri);
                    break;
                }
                case ALBUM: {
                    rr(f, 6.4f, 2.6f, 17.6f, 4.4f, 0.9f);
                    rr(f, 4.6f, 5.4f, 19.4f, 7.2f, 0.9f);
                    Path b = new Path();
                    rr(b, 2.8f, 8.4f, 21.2f, 21.4f, 2.4f);
                    hole(b, 4.8f, 10.4f, 19.2f, 19.4f, 1.1f);
                    f.addPath(b);
                    f.moveTo(5.8f, 18.6f); f.lineTo(10.2f, 13.6f); f.lineTo(13.2f, 16.6f);
                    f.lineTo(15.4f, 14.6f); f.lineTo(18.2f, 18.6f); f.close();
                    break;
                }
                case ALBUM_ADD: {
                    Path b = new Path();
                    rr(b, 2.6f, 5.4f, 16.6f, 19.4f, 2.4f);
                    hole(b, 4.6f, 7.4f, 14.6f, 17.4f, 1.1f);
                    f.addPath(b);
                    rr(f, 6.4f, 11.4f, 12.8f, 13.4f, 0.8f);
                    rr(f, 8.6f, 9.2f, 10.6f, 15.6f, 0.8f);
                    rr(f, 18.2f, 7.8f, 20.2f, 21.4f, 1f);
                    rr(f, 7.8f, 19.4f, 20.2f, 21.4f, 1f);
                    break;
                }
                case BACKUP: {
                    f.moveTo(12f, 2.6f); f.lineTo(18.2f, 9f); f.lineTo(13.2f, 9f);
                    f.lineTo(13.2f, 15.4f); f.lineTo(10.8f, 15.4f); f.lineTo(10.8f, 9f);
                    f.lineTo(5.8f, 9f); f.close();
                    rr(f, 3.4f, 18.4f, 20.6f, 20.8f, 1.2f);
                    rr(f, 3.4f, 14.2f, 5.6f, 20.8f, 1.1f);
                    rr(f, 18.4f, 14.2f, 20.6f, 20.8f, 1.1f);
                    break;
                }
                case HELP: {
                    ring(f, 12f, 12f, 9.2f, 7.3f);
                    s.moveTo(9.4f, 9.6f);
                    s.cubicTo(9.4f, 6.4f, 14.8f, 6.4f, 14.8f, 9.6f);
                    s.cubicTo(14.8f, 11.6f, 12f, 11.6f, 12f, 13.8f);
                    f.addCircle(12f, 16.9f, 1.3f, Path.Direction.CW);
                    break;
                }
                case MINUS:
                    rr(f, 4.4f, 10.8f, 19.6f, 13.2f, 1.2f);
                    break;
                case PAUSE:
                    rr(f, 6.4f, 5f, 10.2f, 19f, 1.2f);
                    rr(f, 13.8f, 5f, 17.6f, 19f, 1.2f);
                    break;
                case IMAGE:
                default: {
                    Path b = new Path();
                    rr(b, 2.8f, 4.4f, 21.2f, 19.6f, 2.6f);
                    hole(b, 4.8f, 6.4f, 19.2f, 17.6f, 1.2f);
                    f.addPath(b);
                    f.addCircle(9f, 10.2f, 1.9f, Path.Direction.CW);
                    f.moveTo(5.2f, 17.2f);
                    f.lineTo(10.4f, 11.6f);
                    f.lineTo(13.8f, 15.2f);
                    f.lineTo(16.2f, 12.8f);
                    f.lineTo(18.8f, 17.2f);
                    f.close();
                    break;
                }
            }
        }
    }

    // ---------- helpers ----------

    private static void rr(Path p, float l, float t, float r, float b, float rad) {
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
    }

    /** 反向繞行：在 WINDING 填充下會挖出空洞。 */
    private static void hole(Path p, float l, float t, float r, float b, float rad) {
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CCW);
    }

    private static void ring(Path p, float cx, float cy, float outer, float inner) {
        Path q = new Path();
        q.addCircle(cx, cy, outer, Path.Direction.CW);
        q.addCircle(cx, cy, inner, Path.Direction.CCW);
        p.addPath(q);
    }
}
