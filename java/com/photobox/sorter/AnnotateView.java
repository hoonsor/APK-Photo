package com.photobox.sorter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * 照片標註畫布：畫筆、螢光筆、方框、圓框、箭頭、文字。
 *
 * 所有圖形都以「原圖像素座標」儲存，畫面上只是縮放顯示，所以存檔時解析度不會變差。
 * 復原／重做用「圖形清單快照」：每次變更前把目前清單存一份，圖形本身建立後就不再修改
 * （移動或改文字時是換成一個新的 Shape），因此快照之間不會互相影響。
 */
public class AnnotateView extends View {

    public static final int PEN = 0, MARKER = 1, RECT = 2, OVAL = 3, ARROW = 4, TEXT = 5;

    /** 細／中／粗，乘上照片短邊。 */
    private static final float[] STROKE = { 0.004f, 0.008f, 0.014f };
    private static final float[] TEXT_SIZE = { 0.035f, 0.05f, 0.075f };
    /** 螢光筆比畫筆粗幾倍。 */
    private static final float MARKER_MUL = 4f;
    private static final int MARKER_ALPHA = 0x66;

    public interface Listener {
        /** 清單有變動（給「復原／重做」按鈕更新狀態用）。 */
        void onChanged();
        /** 文字工具：點空白處 existing 為 null（新增），點到既有文字則為那段文字（修改）。座標為原圖像素。 */
        void onTextRequest(float bx, float by, Shape existing);
    }

    public static final class Shape {
        int type;
        int color;
        float width;
        Path path;
        float x0, y0, x1, y1;
        String text;
        float textSize;

        Shape copy() {
            Shape s = new Shape();
            s.type = type; s.color = color; s.width = width; s.path = path;
            s.x0 = x0; s.y0 = y0; s.x1 = x1; s.y1 = y1;
            s.text = text; s.textSize = textSize;
            return s;
        }
    }

    private Bitmap base;
    private final Matrix toView = new Matrix();
    private final Matrix toBmp = new Matrix();
    private ArrayList<Shape> shapes = new ArrayList<Shape>();
    private final ArrayList<ArrayList<Shape>> undo = new ArrayList<ArrayList<Shape>>();
    private final ArrayList<ArrayList<Shape>> redo = new ArrayList<ArrayList<Shape>>();

    private int tool = PEN;
    private int color = 0xFFFF5A5F;
    private int level = 1;
    private Listener listener;

    private Shape drawing;
    private float lastX, lastY;
    private Shape dragText, dragOrig;
    private float dragDx, dragDy;
    private float downVx, downVy;
    private boolean moved;
    private final float slop;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float[] pt = new float[2];

    public AnnotateView(Context c) {
        super(c);
        slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setFakeBoldText(true);
    }

    public void setListener(Listener l) { listener = l; }

    public void setBitmap(Bitmap b) {
        base = b;
        layoutMatrix();
        invalidate();
    }

    public boolean hasBitmap() { return base != null; }
    public void setTool(int t) { tool = t; }
    public int tool() { return tool; }
    public void setColor(int c) { color = c; }
    public int color() { return color; }
    public void setLevel(int l) { level = Math.max(0, Math.min(2, l)); }
    public int level() { return level; }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public boolean isDirty() { return !shapes.isEmpty(); }

    public void undo() {
        if (undo.isEmpty()) return;
        redo.add(shapes);
        shapes = undo.remove(undo.size() - 1);
        changed();
    }

    public void redo() {
        if (redo.isEmpty()) return;
        undo.add(shapes);
        shapes = redo.remove(redo.size() - 1);
        changed();
    }

    /** 變更之前呼叫：存一份目前清單，並清掉重做。 */
    private void snapshot() {
        undo.add(new ArrayList<Shape>(shapes));
        while (undo.size() > 60) undo.remove(0);
        redo.clear();
    }

    private void changed() {
        invalidate();
        if (listener != null) listener.onChanged();
    }

    private float minDim() {
        return base == null ? 1000f : Math.min(base.getWidth(), base.getHeight());
    }

    // ---------- 文字（由 EditorActivity 的輸入框回呼） ----------

    public void addText(float bx, float by, String s) {
        if (s == null || s.trim().length() == 0) return;
        Shape t = new Shape();
        t.type = TEXT;
        t.color = color;
        t.text = s;
        t.textSize = minDim() * TEXT_SIZE[level];
        // 點的位置當作文字中心，比較符合直覺
        text.setTextSize(t.textSize);
        t.x0 = bx - text.measureText(firstLine(s)) / 2f;
        t.y0 = by + t.textSize / 3f;
        snapshot();
        shapes.add(t);
        changed();
    }

    public void replaceText(Shape old, String s) {
        int i = shapes.indexOf(old);
        if (i < 0) return;
        snapshot();
        if (s == null || s.trim().length() == 0) {
            shapes.remove(i);
        } else {
            Shape n = old.copy();
            n.text = s;
            shapes.set(i, n);
        }
        changed();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    // ---------- 版面 ----------

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutMatrix();
    }

    private void layoutMatrix() {
        toView.reset();
        if (base == null || getWidth() == 0 || getHeight() == 0) return;
        float s = Math.min((float) getWidth() / base.getWidth(), (float) getHeight() / base.getHeight());
        toView.postScale(s, s);
        toView.postTranslate((getWidth() - base.getWidth() * s) / 2f, (getHeight() - base.getHeight() * s) / 2f);
        toView.invert(toBmp);
    }

    @Override protected void onDraw(Canvas c) {
        if (base == null) return;
        c.save();
        c.concat(toView);
        c.drawBitmap(base, 0, 0, bmpPaint);
        RectF clip = new RectF(0, 0, base.getWidth(), base.getHeight());
        c.clipRect(clip);
        drawShapes(c);
        c.restore();
    }

    private void drawShapes(Canvas c) {
        for (int i = 0; i < shapes.size(); i++) drawShape(c, shapes.get(i));
        if (drawing != null) drawShape(c, drawing);
    }

    private void drawShape(Canvas c, Shape s) {
        switch (s.type) {
            case PEN:
            case MARKER:
                stroke.setColor(s.type == MARKER ? Ui.withAlpha(s.color, MARKER_ALPHA) : s.color);
                stroke.setStrokeWidth(s.width);
                stroke.setStrokeCap(s.type == MARKER ? Paint.Cap.SQUARE : Paint.Cap.ROUND);
                c.drawPath(s.path, stroke);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                break;
            case RECT:
            case OVAL: {
                stroke.setColor(s.color);
                stroke.setStrokeWidth(s.width);
                RectF r = new RectF(Math.min(s.x0, s.x1), Math.min(s.y0, s.y1),
                        Math.max(s.x0, s.x1), Math.max(s.y0, s.y1));
                if (s.type == RECT) c.drawRect(r, stroke);
                else c.drawOval(r, stroke);
                break;
            }
            case ARROW:
                drawArrow(c, s);
                break;
            case TEXT:
                drawText(c, s);
                break;
        }
    }

    private void drawArrow(Canvas c, Shape s) {
        float dx = s.x1 - s.x0, dy = s.y1 - s.y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) return;
        float head = Math.min(len * 0.6f, Math.max(s.width * 4.5f, minDim() * 0.03f));
        float ux = dx / len, uy = dy / len;
        // 線畫到箭頭底部，避免圓頭從尖端露出來
        float bx = s.x1 - ux * head * 0.8f, by = s.y1 - uy * head * 0.8f;
        stroke.setColor(s.color);
        stroke.setStrokeWidth(s.width);
        c.drawLine(s.x0, s.y0, bx, by, stroke);
        double ang = Math.toRadians(26);
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        float lx = s.x1 - head * (ux * cos - uy * sin), ly = s.y1 - head * (uy * cos + ux * sin);
        float rx = s.x1 - head * (ux * cos + uy * sin), ry = s.y1 - head * (uy * cos - ux * sin);
        Path p = new Path();
        p.moveTo(s.x1, s.y1);
        p.lineTo(lx, ly);
        p.lineTo(rx, ry);
        p.close();
        fill.setColor(s.color);
        c.drawPath(p, fill);
    }

    private void drawText(Canvas c, Shape s) {
        text.setTextSize(s.textSize);
        text.setColor(s.color);
        // 深色陰影讓文字在亮色照片上也看得清楚
        text.setShadowLayer(s.textSize * 0.08f, 0, s.textSize * 0.03f, 0xAA000000);
        String[] lines = s.text.split("\n", -1);
        float y = s.y0;
        for (int i = 0; i < lines.length; i++) {
            c.drawText(lines[i], s.x0, y, text);
            y += s.textSize * 1.2f;
        }
        text.clearShadowLayer();
    }

    private RectF textBounds(Shape s) {
        text.setTextSize(s.textSize);
        String[] lines = s.text.split("\n", -1);
        float w = 0;
        for (int i = 0; i < lines.length; i++) w = Math.max(w, text.measureText(lines[i]));
        float pad = s.textSize * 0.3f;
        return new RectF(s.x0 - pad, s.y0 - s.textSize - pad,
                s.x0 + w + pad, s.y0 + s.textSize * 1.2f * (lines.length - 1) + s.textSize * 0.3f + pad);
    }

    private Shape hitText(float bx, float by) {
        for (int i = shapes.size() - 1; i >= 0; i--) {
            Shape s = shapes.get(i);
            if (s.type == TEXT && textBounds(s).contains(bx, by)) return s;
        }
        return null;
    }

    // ---------- 觸控 ----------

    private void toBmp(MotionEvent e) {
        pt[0] = e.getX();
        pt[1] = e.getY();
        toBmp.mapPoints(pt);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (base == null) return false;
        if (e.getPointerCount() > 1) {   // 第二根手指放下：放棄這一筆
            drawing = null;
            dragText = null;
            invalidate();
            return true;
        }
        toBmp(e);
        float x = pt[0], y = pt[1];
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downVx = e.getX();
                downVy = e.getY();
                moved = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                if (tool == TEXT) {
                    Shape hit = hitText(x, y);
                    if (hit != null) {
                        beforeMove = hit;
                        dragOrig = hit;
                        dragText = hit.copy();
                        dragDx = x - hit.x0;
                        dragDy = y - hit.y0;
                    }
                    return true;
                }
                drawing = new Shape();
                drawing.type = tool;
                drawing.color = color;
                drawing.width = minDim() * STROKE[level] * (tool == MARKER ? MARKER_MUL : 1f);
                if (tool == PEN || tool == MARKER) {
                    drawing.path = new Path();
                    drawing.path.moveTo(x, y);
                    lastX = x;
                    lastY = y;
                } else {
                    drawing.x0 = drawing.x1 = x;
                    drawing.y0 = drawing.y1 = y;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(e.getX() - downVx, e.getY() - downVy) > slop) moved = true;
                if (tool == TEXT) {
                    if (dragText != null && moved) {
                        dragText.x0 = x - dragDx;
                        dragText.y0 = y - dragDy;
                        int i = shapes.indexOf(dragOrig);
                        if (i >= 0) {
                            // 拖曳期間直接替換顯示，放開時才記一筆復原
                            shapes.set(i, dragText);
                            dragOrig = dragText;
                            dragText = dragText.copy();
                        }
                        invalidate();
                    }
                    return true;
                }
                if (drawing == null) return true;
                if (drawing.path != null) {
                    // 用中點做二次曲線，筆跡比較平滑
                    drawing.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
                    lastX = x;
                    lastY = y;
                } else {
                    drawing.x1 = x;
                    drawing.y1 = y;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (tool == TEXT) {
                    finishText(x, y);
                    return true;
                }
                if (drawing != null) {
                    if (drawing.path != null) {
                        if (!moved) drawing.path.lineTo(x + 0.5f, y + 0.5f);   // 點一下也留下一個點
                        else drawing.path.lineTo(x, y);
                        commitDrawing();
                    } else if (moved) {
                        commitDrawing();
                    } else {
                        drawing = null;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                drawing = null;
                dragText = null;
                invalidate();
                return true;
        }
        return true;
    }

    private void commitDrawing() {
        snapshot();
        shapes.add(drawing);
        drawing = null;
        changed();
    }

    /** 開始拖曳文字前的那個 Shape，放開時用來記復原。 */
    private Shape beforeMove;

    private void finishText(float x, float y) {
        if (dragText == null) {
            if (!moved && listener != null) listener.onTextRequest(x, y, null);
            return;
        }
        Shape cur = dragOrig;
        dragText = null;
        if (!moved) {
            if (listener != null) listener.onTextRequest(x, y, cur);
            return;
        }
        // 移動完成：把「移動前」的清單記進復原
        int i = shapes.indexOf(cur);
        if (i >= 0) {
            ArrayList<Shape> prev = new ArrayList<Shape>(shapes);
            prev.set(i, beforeMove);
            undo.add(prev);
            redo.clear();
        }
        beforeMove = null;
        changed();
    }

    /** 輸出：原圖＋所有標註，解析度與載入的原圖相同。 */
    public Bitmap render() {
        if (base == null) return null;
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        for (int i = 0; i < shapes.size(); i++) drawShape(c, shapes.get(i));
        return out;
    }
}
