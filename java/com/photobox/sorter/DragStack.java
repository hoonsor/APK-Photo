package com.photobox.sorter;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 把多選的照片「疊成一疊」拖曳出去（拖到左側相簿欄放開即可加入）。
 *
 * 拖曳影像一開始是散開的幾張卡片，約 0.22 秒內收攏成一疊並出現張數徽章，
 * 用 View.updateDragShadow() 逐格更新做出「被疊起來」的動畫。
 */
public final class DragStack {

    private DragStack() {}

    /** 拖曳時放在 localState 的資料，放開時由相簿卡片取出。 */
    public static final class Payload {
        public final ArrayList<Item> items;
        Payload(List<Item> items) { this.items = new ArrayList<Item>(items); }
    }

    public static boolean start(final View source, List<Item> items) {
        if (items == null || items.isEmpty()) return false;
        Context c = source.getContext();
        final Shadow shadow = new Shadow(c, items);
        try {
            ClipData clip = ClipData.newPlainText("photobox-items", String.valueOf(items.size()));
            boolean ok = source.startDragAndDrop(clip, shadow, new Payload(items), 0);
            if (!ok) return false;
        } catch (Throwable t) {
            return false;
        }
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(220);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                shadow.progress = (Float) an.getAnimatedValue();
                try { source.updateDragShadow(shadow); } catch (Throwable ignored) { }
            }
        });
        va.start();
        return true;
    }

    static final class Shadow extends View.DragShadowBuilder {
        private final int size, card, count;
        private final Bitmap[] bmps;
        private final float density;
        float progress = 0f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        Shadow(Context c, List<Item> items) {
            super();
            density = c.getResources().getDisplayMetrics().density;
            size = (int) (132 * density);
            card = (int) (84 * density);
            count = items.size();
            int n = Math.min(3, items.size());
            bmps = new Bitmap[n];
            for (int i = 0; i < n; i++) bmps[i] = Thumbs.anyCachedGrid(items.get(i));
        }

        @Override public void onProvideShadowMetrics(Point outSize, Point touch) {
            outSize.set(size, size);
            touch.set(size / 2, size / 2);
        }

        @Override public void onDrawShadow(Canvas c) {
            float cx = size / 2f, cy = size / 2f + 4 * density;
            // 散開 → 收攏：角度與位移隨 progress 變小
            float[] rot = { -16f, 13f, 0f };
            float[] dx = { -26f, 24f, 0f };
            float[] fin = { -8f, 7f, 0f };
            for (int i = bmps.length - 1; i >= 0; i--) {
                int slot = 2 - i;   // 第一張選取的放在最上面、不歪
                float r = rot[slot] + (fin[slot] - rot[slot]) * progress;
                float ox = dx[slot] * (1f - progress) * density;
                c.save();
                c.translate(cx + ox, cy);
                c.rotate(r);
                RectF box = new RectF(-card / 2f, -card / 2f, card / 2f, card / 2f);
                p.setColor(0x66000000);
                c.drawRoundRect(new RectF(box.left + 2 * density, box.top + 4 * density,
                        box.right + 2 * density, box.bottom + 4 * density), 9 * density, 9 * density, p);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(box, 9 * density, 9 * density, p);
                RectF inner = new RectF(box.left + 3 * density, box.top + 3 * density,
                        box.right - 3 * density, box.bottom - 3 * density);
                Bitmap b = bmps[i];
                if (b != null && !b.isRecycled()) {
                    int s = Math.min(b.getWidth(), b.getHeight());
                    Rect src = new Rect((b.getWidth() - s) / 2, (b.getHeight() - s) / 2,
                            (b.getWidth() + s) / 2, (b.getHeight() + s) / 2);
                    c.save();
                    android.graphics.Path clip = new android.graphics.Path();
                    clip.addRoundRect(inner, 7 * density, 7 * density, android.graphics.Path.Direction.CW);
                    c.clipPath(clip);
                    c.drawBitmap(b, src, inner, p);
                    c.restore();
                } else {
                    p.setColor(T.SURFACE3);
                    c.drawRoundRect(inner, 7 * density, 7 * density, p);
                }
                c.restore();
            }
            // 張數徽章
            float br = 15 * density;
            float bx = size - br - 4 * density, by = br + 4 * density;
            p.setColor(T.PRIMARY);
            p.setAlpha((int) (255 * Math.min(1f, progress * 1.4f)));
            c.drawCircle(bx, by, br, p);
            p.setAlpha(255);
            p.setColor(0xFF0B0E16);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            String t = count > 999 ? "999+" : String.valueOf(count);
            p.setTextSize((t.length() >= 3 ? 11 : 14) * density);
            if (progress > 0.3f) c.drawText(t, bx, by + p.getTextSize() * 0.36f, p);
        }
    }
}
