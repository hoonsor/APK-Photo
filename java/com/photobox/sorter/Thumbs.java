package com.photobox.sorter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 縮圖與大圖的非同步載入，含記憶體快取。 */
public final class Thumbs {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static LruCache<String, Bitmap> small;
    private static LruCache<String, Bitmap> big;
    private static ThreadPoolExecutor gridPool;
    private static ThreadPoolExecutor fullPool;

    private Thumbs() {}

    private static synchronized void init() {
        if (small != null) return;
        long max = Runtime.getRuntime().maxMemory();
        int smallKb = (int) Math.max(8 * 1024, max / 1024 / 6);
        int bigKb = (int) Math.max(16 * 1024, max / 1024 / 5);
        small = new LruCache<String, Bitmap>(smallKb) {
            @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount() / 1024; }
        };
        big = new LruCache<String, Bitmap>(bigKb) {
            @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount() / 1024; }
        };
        BlockingQueue<Runnable> q1 = new LinkedBlockingDeque<Runnable>();
        gridPool = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS, q1);
        gridPool.allowCoreThreadTimeOut(true);
        BlockingQueue<Runnable> q2 = new LinkedBlockingDeque<Runnable>();
        fullPool = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS, q2);
        fullPool.allowCoreThreadTimeOut(true);
    }

    /** 任何解析度的網格縮圖快取（給拖曳影像用，沒有就回傳 null，不觸發載入）。 */
    public static Bitmap anyCachedGrid(Item it) {
        init();
        int[] sizes = { 256, 384, 512 };
        for (int i = 0; i < sizes.length; i++) {
            Bitmap b = small.get(it.id + "@" + sizes[i]);
            if (b != null) return b;
        }
        return null;
    }

    public static Bitmap cachedGrid(Item it, int px) {
        init();
        return small.get(it.id + "@" + px);
    }

    /** 網格縮圖。 */
    public static void grid(final ImageView iv, final Item it, final int px) {
        init();
        final String key = it.id + "@" + px;
        final Context ctx = iv.getContext().getApplicationContext();
        iv.setTag(key);
        Bitmap cached = small.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageBitmap(null);
        gridPool.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    b = ctx.getContentResolver().loadThumbnail(it.uri(), new Size(px, px), null);
                } catch (Throwable t) {
                    try { b = decode(ctx, it, px); } catch (Throwable ignored) { }
                }
                final Bitmap res = b;
                if (res != null) small.put(key, res);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (res != null && key.equals(iv.getTag())) iv.setImageBitmap(res);
                    }
                });
            }
        });
    }

    /** 大圖（整理卡片、檢視器）。 */
    public static void full(final ImageView iv, final Item it, final int maxPx,
                            final Runnable onDone) {
        init();
        final String key = it.id + "#" + maxPx;
        final Context ctx = iv.getContext().getApplicationContext();
        iv.setTag(key);
        Bitmap cached = big.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            if (onDone != null) onDone.run();
            return;
        }
        Bitmap quick = small.get(it.id + "@" + 256);
        if (quick != null) iv.setImageBitmap(quick); else iv.setImageBitmap(null);
        fullPool.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try {
                    if (it.video) {
                        b = ctx.getContentResolver().loadThumbnail(
                                it.uri(), new Size(maxPx, maxPx), null);
                    } else {
                        b = decode(ctx, it, maxPx);
                    }
                } catch (Throwable t) {
                    try {
                        b = ctx.getContentResolver().loadThumbnail(
                                it.uri(), new Size(768, 768), null);
                    } catch (Throwable ignored) { }
                }
                final Bitmap res = b;
                if (res != null) big.put(key, res);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (res != null && key.equals(iv.getTag())) iv.setImageBitmap(res);
                        if (onDone != null) onDone.run();
                    }
                });
            }
        });
    }

    /** 預先載入，讓下一張卡片不會有空白。 */
    public static void preload(final Context c, final Item it, final int maxPx) {
        init();
        final String key = it.id + "#" + maxPx;
        if (big.get(key) != null) return;
        final Context ctx = c.getApplicationContext();
        fullPool.execute(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap b = it.video
                            ? ctx.getContentResolver().loadThumbnail(it.uri(), new Size(maxPx, maxPx), null)
                            : decode(ctx, it, maxPx);
                    if (b != null) big.put(key, b);
                } catch (Throwable ignored) { }
            }
        });
    }

    /** 給相似度分析用的極小灰階取樣，同步執行。 */
    public static Bitmap tiny(Context c, Item it, int px) {
        try {
            return c.getContentResolver().loadThumbnail(it.uri(), new Size(px, px), null);
        } catch (Throwable t) {
            try { return decode(c, it, px); } catch (Throwable ignored) { }
        }
        return null;
    }

    private static Bitmap decode(Context c, Item it, int maxPx) throws Exception {
        InputStream in = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            in = c.getContentResolver().openInputStream(it.uri());
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            in = null;
            int s = 1;
            while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = Math.max(1, s);
            in = c.getContentResolver().openInputStream(it.uri());
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in, null, d);
            if (b == null) return null;
            if (it.orientation % 360 != 0) {
                Matrix m = new Matrix();
                m.postRotate(it.orientation);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    public static void trimMemory() {
        if (small != null) small.evictAll();
        if (big != null) big.evictAll();
    }
}
