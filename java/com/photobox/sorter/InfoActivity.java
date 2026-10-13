package com.photobox.sorter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** 照片／影片詳細資訊：資訊（EXIF）、位置、相簿、裝置 四個分頁。 */
public class InfoActivity extends Activity implements Store.Listener {

    private static final int REQ_LOC = 6001;
    private static final String PERM_LOC = "android.permission.ACCESS_MEDIA_LOCATION";

    public static void open(Context c, long id) {
        Intent i = new Intent(c, InfoActivity.class);
        i.putExtra("id", id);
        c.startActivity(i);
    }

    private Item it;
    private int tab = 0;
    private final ArrayList<TextView> tabs = new ArrayList<TextView>();
    private LinearLayout body;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        it = Store.get().byId.get(getIntent().getLongExtra("id", -1));
        if (it == null) { finish(); return; }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(T.BG);
        root.setFitsSystemWindows(true);

        // ---- 上方：返回＋縮圖＋分頁 ----
        LinearLayout top = Ui.col(this);
        top.setBackgroundColor(T.SURFACE);
        LinearLayout bar = Ui.rowH(this);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        ImageView back = new ImageView(this);
        back.setImageDrawable(Icons.get(Icons.BACK, T.TEXT));
        int p = Ui.dp(this, 10);
        back.setPadding(p, p, p, p);
        back.setBackground(Ui.tappable(0, Ui.dp(this, 22)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView t = Ui.tv(this, it.video ? "影片資訊" : "照片資訊", 18f, T.TEXT, true);
        LinearLayout.LayoutParams tlp = Ui.lpWeight();
        tlp.leftMargin = Ui.dp(this, 6);
        bar.addView(t, tlp);
        top.addView(bar, Ui.lpW());

        FrameLayout thumbBox = new FrameLayout(this);
        thumbBox.setBackground(Ui.round(T.SURFACE2, Ui.dp(this, 12)));
        thumbBox.setClipToOutline(true);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Thumbs.grid(thumb, it, 384);
        thumbBox.addView(thumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (it.video) {
            ImageView play = new ImageView(this);
            play.setImageDrawable(Icons.get(Icons.PLAY, 0xFFFFFFFF));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
            plp.gravity = Gravity.CENTER;
            thumbBox.addView(play, plp);
        }
        thumbBox.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams thl = new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120));
        thl.gravity = Gravity.CENTER_HORIZONTAL;
        thl.topMargin = Ui.dp(this, 4);
        top.addView(thumbBox, thl);

        LinearLayout tabRow = Ui.rowH(this);
        tabRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 12));
        String[] names = { "資訊", "位置", "相簿", "裝置" };
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            TextView tv = Ui.tv(this, names[i], 14f, T.DIM, true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(k); }
            });
            LinearLayout.LayoutParams lp = Ui.lpWeight();
            if (i < names.length - 1) lp.rightMargin = Ui.dp(this, 6);
            tabRow.addView(tv, lp);
            tabs.add(tv);
        }
        top.addView(tabRow, Ui.lpW());
        root.addView(top, Ui.lpW());

        ScrollView sv = new ScrollView(this);
        body = Ui.col(this);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slp.weight = 1f;
        root.addView(sv, slp);

        setContentView(root);
        Store.get().addListener(this);
        select(0);
    }

    @Override protected void onDestroy() {
        Store.get().removeListener(this);
        super.onDestroy();
    }

    @Override public void onStoreChanged() { if (tab == 2) select(2); }

    private void select(int k) {
        tab = k;
        for (int i = 0; i < tabs.size(); i++) {
            boolean on = i == k;
            TextView tv = tabs.get(i);
            tv.setTextColor(on ? 0xFF0B0E16 : T.DIM);
            tv.setBackground(Ui.tappable(on ? T.PRIMARY : T.SURFACE2, Ui.dp(this, 10),
                    on ? T.PRIMARY : T.BORDER, Ui.dp(this, 1)));
        }
        body.removeAllViews();
        switch (k) {
            case 0: buildInfo(); break;
            case 1: buildLocation(); break;
            case 2: buildAlbums(); break;
            default: buildDevice(); break;
        }
    }

    // ---------- 共用列 ----------

    private void row(String label, String value) {
        if (value == null || value.length() == 0) return;
        LinearLayout r = Ui.rowH(this);
        r.setPadding(0, Ui.dp(this, 15), 0, Ui.dp(this, 15));
        TextView l = Ui.tv(this, label, 15f, T.TEXT, false);
        r.addView(l);
        TextView v = Ui.tv(this, value, 14.5f, T.DIM, false);
        v.setGravity(Gravity.END);
        v.setTextIsSelectable(true);
        LinearLayout.LayoutParams vlp = Ui.lpWeight();
        vlp.leftMargin = Ui.dp(this, 16);
        r.addView(v, vlp);
        body.addView(r, Ui.lpW());
        body.addView(Ui.divider(this));
    }

    private TextView note(String s) {
        TextView t = Ui.tv(this, s, 13f, T.FAINT, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        body.addView(t, Ui.lpW());
        return t;
    }

    private void loading() {
        TextView t = Ui.tv(this, "讀取中…", 14f, T.FAINT, false);
        t.setPadding(0, Ui.dp(this, 20), 0, 0);
        body.addView(t);
    }

    private void bg(final int forTab, final Runnable work) {
        new Thread(new Runnable() {
            @Override public void run() { work.run(); }
        }, "info-" + forTab).start();
    }

    // ---------- 資訊 ----------

    private void buildInfo() {
        row("檔案名稱", it.name);
        row("拍攝日期", it.timeText());
        loading();
        final int myTab = 0;
        bg(myTab, new Runnable() {
            @Override public void run() {
                final ArrayList<String[]> rows = it.video ? videoMeta() : exifMeta();
                main.post(new Runnable() {
                    @Override public void run() {
                        if (tab != myTab || isFinishing()) return;
                        body.removeAllViews();
                        row("檔案名稱", it.name);
                        row("拍攝日期", it.timeText());
                        for (int i = 0; i < rows.size(); i++) row(rows.get(i)[0], rows.get(i)[1]);
                        row("檔案大小", it.sizeText());
                        row("檔案類型", it.mime);
                        row("所在資料夾", it.bucket);
                    }
                });
            }
        });
    }

    private ArrayList<String[]> exifMeta() {
        ArrayList<String[]> out = new ArrayList<String[]>();
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(it.uri());
            if (in == null) throw new Exception("no stream");
            ExifInterface e = new ExifInterface(in);
            int w = e.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
            int h = e.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
            if (w <= 0 || h <= 0) { w = it.width; h = it.height; }
            out.add(new String[]{ "解析度", w > 0 ? (w + " × " + h + "（" + mp(w, h) + "）") : it.dimText() });
            double exp = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0);
            if (exp > 0) out.add(new String[]{ "曝光時間", exposure(exp) });
            double f = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0);
            if (f > 0) out.add(new String[]{ "光圈值", "f/" + trim(f) });
            String iso = e.getAttribute("PhotographicSensitivity");
            if (iso == null) iso = e.getAttribute("ISOSpeedRatings");
            if (iso != null && iso.length() > 0) out.add(new String[]{ "ISO 感光度", iso });
            String flash = e.getAttribute(ExifInterface.TAG_FLASH);
            if (flash != null) {
                int fv = e.getAttributeInt(ExifInterface.TAG_FLASH, 0);
                out.add(new String[]{ "閃光燈", (fv & 1) == 1 ? "有閃光燈" : "無閃光燈" });
            }
            String wb = e.getAttribute(ExifInterface.TAG_WHITE_BALANCE);
            if (wb != null) out.add(new String[]{ "白平衡", "0".equals(wb) ? "自動" : "手動" });
            double fl = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0);
            if (fl > 0) {
                int eq = e.getAttributeInt("FocalLengthIn35mmFilm", 0);
                out.add(new String[]{ "焦距", trim(fl) + " 公釐" + (eq > 0 ? "（等效 " + eq + " 公釐）" : "") });
            }
            String make = e.getAttribute(ExifInterface.TAG_MAKE);
            if (make != null) out.add(new String[]{ "相機製造商", make.trim() });
            String model = e.getAttribute(ExifInterface.TAG_MODEL);
            if (model != null) out.add(new String[]{ "相機機型", model.trim() });
            String lens = e.getAttribute("LensModel");
            if (lens != null && lens.trim().length() > 0) out.add(new String[]{ "鏡頭", lens.trim() });
            String sw = e.getAttribute(ExifInterface.TAG_SOFTWARE);
            if (sw != null && sw.trim().length() > 0) out.add(new String[]{ "軟體", sw.trim() });
        } catch (Throwable t) {
            out.add(new String[]{ "解析度", it.dimText() });
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
        return out;
    }

    private ArrayList<String[]> videoMeta() {
        ArrayList<String[]> out = new ArrayList<String[]>();
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, it.uri());
            String w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (w != null && h != null) {
                boolean swap = "90".equals(rot) || "270".equals(rot);
                out.add(new String[]{ "解析度", swap ? (h + " × " + w) : (w + " × " + h) });
            }
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) out.add(new String[]{ "長度", duration(Long.parseLong(d)) });
            String fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            if (fps != null) {
                try { out.add(new String[]{ "影格率", trim(Double.parseDouble(fps)) + " fps" }); }
                catch (Throwable ignored) { }
            }
            String br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (br != null) {
                try { out.add(new String[]{ "位元率", String.format(Locale.US, "%.1f Mbps", Long.parseLong(br) / 1e6) }); }
                catch (Throwable ignored) { }
            }
            String author = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);
            if (author != null && author.trim().length() > 0) out.add(new String[]{ "作者", author.trim() });
        } catch (Throwable t) {
            out.add(new String[]{ "解析度", it.dimText() });
        } finally {
            try { r.release(); } catch (Throwable ignored) { }
        }
        return out;
    }

    private static String mp(int w, int h) {
        return String.format(Locale.US, "%.1f MP", w * (double) h / 1e6);
    }

    private static String exposure(double s) {
        if (s >= 1) return trim(s) + " 秒";
        return "1/" + Math.round(1.0 / s) + " 秒";
    }

    private static String trim(double v) {
        if (Math.abs(v - Math.round(v)) < 0.005) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.1f", v);
    }

    private static String duration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    // ---------- 位置 ----------

    private void buildLocation() {
        if (checkSelfPermission(PERM_LOC) != PackageManager.PERMISSION_GRANTED) {
            emptyState(Icons.PIN, "需要「讀取照片位置」權限",
                    "Android 預設會把照片裡的 GPS 座標隱藏起來，必須額外允許，App 才看得到拍攝地點。");
            TextView btn = Ui.btn(this, "允許讀取照片位置", T.PRIMARY, 0xFF0B0E16);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { requestPermissions(new String[]{ PERM_LOC }, REQ_LOC); }
            });
            LinearLayout.LayoutParams lp = Ui.lpW();
            lp.topMargin = Ui.dp(this, 16);
            body.addView(btn, lp);
            return;
        }
        loading();
        final int myTab = 1;
        bg(myTab, new Runnable() {
            @Override public void run() {
                final double[] ll = readLocation();
                main.post(new Runnable() {
                    @Override public void run() {
                        if (tab != myTab || isFinishing()) return;
                        body.removeAllViews();
                        showLocation(ll);
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (req == REQ_LOC) {
            if (checkSelfPermission(PERM_LOC) != PackageManager.PERMISSION_GRANTED) {
                Ui.toast(this, "沒有取得權限，無法讀取拍攝地點");
            }
            if (tab == 1) select(1);
        }
    }

    /** @return {緯度, 經度, 高度(NaN 表示沒有)}；沒有位置回傳 null。 */
    private double[] readLocation() {
        Uri orig;
        try { orig = MediaStore.setRequireOriginal(it.uri()); }
        catch (Throwable t) { orig = it.uri(); }
        if (it.video) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, orig);
                String loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                return parseIso6709(loc);
            } catch (Throwable t) {
                return null;
            } finally { try { r.release(); } catch (Throwable ignored) { } }
        }
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(orig);
            if (in == null) return null;
            ExifInterface e = new ExifInterface(in);
            float[] ll = new float[2];
            if (!e.getLatLong(ll)) return null;
            if (ll[0] == 0f && ll[1] == 0f) return null;
            double alt = e.getAltitude(Double.NaN);
            return new double[]{ ll[0], ll[1], alt };
        } catch (Throwable t) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    static double[] parseIso6709(String s) { return Geo.parseIso6709(s); }

    private void showLocation(final double[] ll) {
        if (ll == null) {
            emptyState(Icons.PIN, "沒有拍攝地點",
                    "這個檔案沒有記錄 GPS 座標。通常是拍攝時沒開定位、相機設定關閉了位置標記，"
                  + "或是從通訊軟體下載的檔案（傳送時位置資訊常被移除）。");
            return;
        }
        final String coord = String.format(Locale.US, "%.6f, %.6f", ll[0], ll[1]);
        row("緯度", String.format(Locale.US, "%.6f°（%s）", ll[0], dms(ll[0], true)));
        row("經度", String.format(Locale.US, "%.6f°（%s）", ll[1], dms(ll[1], false)));
        if (!Double.isNaN(ll[2])) row("高度", String.format(Locale.US, "%.0f 公尺", ll[2]));

        TextView open = Ui.btn(this, "在地圖 App 中開啟", T.PRIMARY, 0xFF0B0E16);
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Uri u = Uri.parse("geo:" + ll[0] + "," + ll[1] + "?q=" + ll[0] + "," + ll[1]
                            + "(" + Uri.encode(it.name) + ")");
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, u), "用地圖開啟"));
                } catch (Throwable t) { Ui.toast(InfoActivity.this, "手機上沒有可以開啟地圖的 App"); }
            }
        });
        LinearLayout.LayoutParams lp = Ui.lpW();
        lp.topMargin = Ui.dp(this, 20);
        body.addView(open, lp);

        TextView copy = Ui.btnGhost(this, "複製座標", T.PRIMARY);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("座標", coord));
                    Ui.toast(InfoActivity.this, "已複製 " + coord);
                } catch (Throwable t) { Ui.toast(InfoActivity.this, "複製失敗"); }
            }
        });
        LinearLayout.LayoutParams lp2 = Ui.lpW();
        lp2.topMargin = Ui.dp(this, 10);
        body.addView(copy, lp2);
        note("地圖不直接顯示在這裡：這個 App 刻意沒有網路權限（照片不會被傳到任何地方），"
           + "而載入地圖需要連網。按上方按鈕會交給 Google 地圖等 App 顯示。");
    }

    private static String dms(double v, boolean lat) {
        String dir = lat ? (v >= 0 ? "北緯" : "南緯") : (v >= 0 ? "東經" : "西經");
        double a = Math.abs(v);
        int d = (int) a;
        double mf = (a - d) * 60;
        int m = (int) mf;
        double s = (mf - m) * 60;
        return String.format(Locale.US, "%s %d°%d′%.1f″", dir, d, m, s);
    }

    // ---------- 相簿 ----------

    private void buildAlbums() {
        ArrayList<Albums.Album> in = Albums.get().of(it.id);
        if (in.isEmpty()) {
            emptyState(Icons.ALERT, "不在任何相簿中", "相簿只是整理用的清單，不會搬動檔案。按下方按鈕就能把它放進相簿。");
        } else {
            TextView h = Ui.tv(this, "在這 " + in.size() + " 本相簿裡", 13f, T.DIM, true);
            h.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
            body.addView(h);
            for (int i = 0; i < in.size(); i++) body.addView(albumRow(in.get(i)), Ui.lpW());
        }
        TextView add = Ui.btn(this, in.isEmpty() ? "加入相簿…" : "加入／移出相簿…", T.PRIMARY, 0xFF0B0E16);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ArrayList<Item> one = new ArrayList<Item>();
                one.add(it);
                AlbumDialogs.addTo(InfoActivity.this, one, null);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lpW();
        lp.topMargin = Ui.dp(this, 18);
        body.addView(add, lp);
        note("實體位置：" + it.bucket + " 資料夾（可在「裝置」分頁看完整路徑）");
    }

    private View albumRow(final Albums.Album al) {
        LinearLayout r = Ui.rowH(this);
        r.setBackground(Ui.tappable(T.SURFACE, Ui.dp(this, 14), T.BORDER, Ui.dp(this, 1)));
        r.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        LinearLayout.LayoutParams rlp = Ui.lpW();
        rlp.bottomMargin = Ui.dp(this, 8);
        r.setLayoutParams(rlp);
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.round(T.SURFACE3, Ui.dp(this, 10)));
        box.setClipToOutline(true);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Item c = Albums.get().cover(al);
        if (c != null) Thumbs.grid(iv, c, 256);
        box.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        r.addView(box, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        LinearLayout texts = Ui.col(this);
        texts.addView(Ui.tv(this, al.name, 15f, T.TEXT, true));
        texts.addView(Ui.space(this, 4));
        texts.addView(Ui.tv(this, Albums.countText(Albums.get().counts(al)), 12f, T.FAINT, false));
        LinearLayout.LayoutParams tl = Ui.lpWeight();
        tl.leftMargin = Ui.dp(this, 12);
        r.addView(texts, tl);
        ImageView go = new ImageView(this);
        go.setImageDrawable(Icons.get(Icons.CHEVRON, T.FAINT));
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AlbumActivity.open(InfoActivity.this, al.id); }
        });
        return r;
    }

    // ---------- 裝置 ----------

    private void buildDevice() {
        loading();
        final int myTab = 3;
        bg(myTab, new Runnable() {
            @Override public void run() {
                final String[] d = deviceInfo();
                main.post(new Runnable() {
                    @Override public void run() {
                        if (tab != myTab || isFinishing()) return;
                        body.removeAllViews();
                        LinearLayout r = Ui.rowH(this_());
                        r.setPadding(0, Ui.dp(this_(), 16), 0, Ui.dp(this_(), 16));
                        ImageView ph = new ImageView(this_());
                        ph.setImageDrawable(Icons.get(Icons.PHONE, T.DIM));
                        r.addView(ph, new LinearLayout.LayoutParams(Ui.dp(this_(), 30), Ui.dp(this_(), 30)));
                        LinearLayout texts = Ui.col(this_());
                        texts.addView(Ui.tv(this_(), "此 " + deviceName(), 17f, T.TEXT, false));
                        texts.addView(Ui.space(this_(), 5));
                        TextView path = Ui.tv(this_(), d[0], 13f, T.DIM, false);
                        path.setTextIsSelectable(true);
                        texts.addView(path);
                        LinearLayout.LayoutParams tl = Ui.lpWeight();
                        tl.leftMargin = Ui.dp(this_(), 16);
                        r.addView(texts, tl);
                        body.addView(r, Ui.lpW());
                        body.addView(Ui.divider(this_()));
                        row("儲存位置", d[1]);
                        row("加入媒體庫", d[2]);
                        row("最後修改", d[3]);
                        row("建立者", d[4]);
                    }
                });
            }
        });
    }

    private Activity this_() { return this; }

    private static String deviceName() {
        String make = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.toLowerCase(Locale.US).startsWith(make.toLowerCase(Locale.US))) return model;
        return (make.length() > 0 ? Character.toUpperCase(make.charAt(0)) + make.substring(1) + " " : "") + model;
    }

    /** {完整路徑, 儲存位置, 加入時間, 修改時間, 建立者} */
    private String[] deviceInfo() {
        String[] out = { "/" + it.bucket + "/" + it.name, "", "", "", "" };
        Cursor c = null;
        try {
            String[] proj = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.OWNER_PACKAGE_NAME };
            c = getContentResolver().query(it.uri(), proj, null, null, null);
            if (c != null && c.moveToFirst()) {
                String data = c.getString(0);
                String rel = c.getString(1);
                String vol = c.getString(2);
                if (data != null && data.length() > 0) out[0] = data;
                else if (rel != null) {
                    String base = (vol == null || MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(vol))
                            ? "/storage/emulated/0/" : ("/storage/" + vol.toUpperCase(Locale.US) + "/");
                    out[0] = base + rel + it.name;
                }
                out[1] = (vol == null || MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(vol)) ? "手機內部儲存空間" : ("外部儲存（" + vol + "）");
                SimpleDateFormat f = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.TAIWAN);
                long add = c.getLong(3), mod = c.getLong(4);
                if (add > 0) out[2] = f.format(new Date(add * 1000L));
                if (mod > 0) out[3] = f.format(new Date(mod * 1000L));
                String owner = c.getString(5);
                if (owner != null && owner.length() > 0) out[4] = appLabel(owner);
            }
        } catch (Throwable ignored) {
        } finally { if (c != null) try { c.close(); } catch (Throwable ignored) { } }
        return out;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() + "（" + pkg + "）";
        } catch (Throwable t) {
            return pkg;   // Android 11 的套件可見性限制下常查不到名稱，直接顯示套件名
        }
    }

    // ---------- 空狀態 ----------

    private void emptyState(int icon, String title, String desc) {
        LinearLayout box = Ui.col(this);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.dp(this, 12), Ui.dp(this, 36), Ui.dp(this, 12), 0);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Icons.get(icon, T.FAINT));
        box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        box.addView(Ui.space(this, 14));
        TextView t = Ui.tv(this, title, 17f, T.TEXT, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        box.addView(Ui.space(this, 10));
        TextView d = Ui.tv(this, desc, 13.5f, T.DIM, false);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(d, Ui.lpW());
        body.addView(box, Ui.lpW());
    }
}
