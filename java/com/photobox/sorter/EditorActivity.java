package com.photobox.sorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 簡易照片編輯器：打字、螢光筆、畫筆、方框、圓框、箭頭、馬賽克（框選／筆刷），可復原／重做。
 * 存檔一律「另存新照片」（放在原照片同一個資料夾、拍攝時間與原照片相同，所以在照片牆上會排在原照片旁邊），
 * 不覆蓋原檔：覆蓋需要系統逐張確認，而且改壞了救不回來。
 */
public class EditorActivity extends Activity {

    /** 載入原圖的最長邊上限：再大會吃掉太多記憶體（4096×3072 的 ARGB 約 50MB，存檔時還要再複製一份）。 */
    private static final int MAX_PX = 4096;

    private static final int[] COLORS = {
            0xFFFF5A5F,   // 紅
            0xFFFFB020,   // 橘黃
            0xFFFFE14D,   // 螢光黃
            0xFF3DDC97,   // 綠
            0xFF4DA3FF,   // 藍
            0xFFB07CFF,   // 紫
            0xFFFFFFFF,   // 白
            0xFF111111    // 黑
    };
    private static final String[] TOOL_NAMES = { "畫筆", "螢光筆", "方框", "圓框", "箭頭", "文字", "馬賽克框", "馬賽克筆" };
    private static final int[] TOOL_ICONS = { Icons.EDIT, Icons.MARKER, Icons.RECT, Icons.OVAL, Icons.ARROW_DIAG, Icons.TEXT,
            Icons.MOSAIC, Icons.MOSAIC_BRUSH };

    private Item item;
    private AnnotateView canvas;
    private ProgressBar loading;
    private ImageView undoBtn, redoBtn;
    private TextView saveBtn, sizeText;
    private SeekBar sizeBar;
    private SizePreview sizePreview;
    private LinearLayout toolRow, colorRow;
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        item = Store.get().byId.get(getIntent().getLongExtra("id", -1));
        if (item == null || item.video) { finish(); return; }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(0xFF000000);
        root.setFitsSystemWindows(true);

        root.addView(buildTop(), Ui.lpW());

        FrameLayout stage = new FrameLayout(this);
        canvas = new AnnotateView(this);
        canvas.setTool(Prefs.editorTool(this));
        canvas.setColor(Prefs.editorColor(this, COLORS[0]));
        canvas.setSize(Prefs.editorSize(this, AnnotateView.SIZE_DEFAULT));
        canvas.setListener(new AnnotateView.Listener() {
            @Override public void onChanged() { updateButtons(); }
            @Override public void onTextRequest(float bx, float by, AnnotateView.Shape existing) {
                askText(bx, by, existing);
            }
        });
        stage.addView(canvas, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loading = new ProgressBar(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
        plp.gravity = Gravity.CENTER;
        stage.addView(loading, plp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slp.weight = 1f;
        root.addView(stage, slp);

        root.addView(buildBottom(), Ui.lpW());
        setContentView(root);
        updateButtons();
        load();
    }

    // ---------- 版面 ----------

    private ImageView barIcon(int icon, View.OnClickListener cl) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(Icons.get(icon, 0xFFFFFFFF));
        int p = Ui.dp(this, 10);
        v.setPadding(p, p, p, p);
        v.setBackground(Ui.ripple(new android.graphics.drawable.ColorDrawable(0), 0x44FFFFFF));
        v.setOnClickListener(cl);
        return v;
    }

    private LinearLayout buildTop() {
        LinearLayout l = Ui.rowH(this);
        l.setBackgroundColor(T.SURFACE);
        l.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        l.addView(barIcon(Icons.CLOSE, new View.OnClickListener() {
            @Override public void onClick(View v) { askLeave(); }
        }), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        TextView title = Ui.tv(this, "編輯照片", 17f, T.TEXT, true);
        LinearLayout.LayoutParams tl = Ui.lpWeight();
        tl.leftMargin = Ui.dp(this, 6);
        l.addView(title, tl);
        undoBtn = barIcon(Icons.UNDO, new View.OnClickListener() {
            @Override public void onClick(View v) { canvas.undo(); }
        });
        l.addView(undoBtn, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        redoBtn = barIcon(Icons.REDO, new View.OnClickListener() {
            @Override public void onClick(View v) { canvas.redo(); }
        });
        l.addView(redoBtn, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        saveBtn = Ui.btn(this, "另存", T.PRIMARY, 0xFF0B0E16);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 76), ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = Ui.dp(this, 6);
        l.addView(saveBtn, sl);
        return l;
    }

    private LinearLayout buildBottom() {
        LinearLayout box = Ui.col(this);
        box.setBackgroundColor(T.SURFACE);
        box.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

        // 顏色：橫向捲動
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        colorRow = Ui.rowH(this);
        colorRow.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        hs.addView(colorRow);
        box.addView(hs, Ui.lpW());
        box.addView(Ui.space(this, 8));

        // 粗細：滑桿連續調整（1～100），右邊即時預覽實際粗細與顏色
        LinearLayout sizeRow = Ui.rowH(this);
        sizeRow.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        sizeText = Ui.tv(this, "", 13f, T.DIM, true);
        sizeText.setMinWidth(Ui.dp(this, 58));
        sizeRow.addView(sizeText);
        sizeBar = new SeekBar(this);
        sizeBar.setMax(AnnotateView.SIZE_MAX - AnnotateView.SIZE_MIN);
        sizeBar.setProgress(canvas.size() - AnnotateView.SIZE_MIN);
        sizeBar.setProgressTintList(android.content.res.ColorStateList.valueOf(T.PRIMARY));
        sizeBar.setThumbTintList(android.content.res.ColorStateList.valueOf(T.PRIMARY));
        sizeBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(T.SURFACE3));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int v, boolean user) {
                canvas.setSize(v + AnnotateView.SIZE_MIN);
                styleSize();
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) {
                Prefs.setEditorSize(EditorActivity.this, canvas.size());
            }
        });
        sizeRow.addView(sizeBar, Ui.lpWeight());
        sizePreview = new SizePreview();
        sizePreview.setBackground(Ui.round(T.SURFACE2, Ui.dp(this, 10), T.BORDER, Ui.dp(this, 1)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46));
        plp.leftMargin = Ui.dp(this, 6);
        sizeRow.addView(sizePreview, plp);
        box.addView(sizeRow, Ui.lpW());
        box.addView(Ui.space(this, 8));

        // 工具：固定寬度的格子＋橫向捲動，字不會被擠出框線
        HorizontalScrollView ts = new HorizontalScrollView(this);
        ts.setHorizontalScrollBarEnabled(false);
        toolRow = Ui.rowH(this);
        toolRow.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        for (int i = 0; i < TOOL_NAMES.length; i++) {
            final int t = i;
            LinearLayout cell = Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9));
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            cell.addView(iv, ilp);
            cell.addView(Ui.space(this, 5));
            TextView lab = Ui.tv(this, TOOL_NAMES[i], 12f, T.DIM, false);
            lab.setMaxLines(1);
            lab.setGravity(Gravity.CENTER);
            cell.addView(lab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    canvas.setTool(t);
                    Prefs.setEditorTool(EditorActivity.this, t);
                    styleTools();
                    styleSize();
                    if (t == AnnotateView.TEXT) Ui.toast(EditorActivity.this, "點照片上要放文字的位置；點已經有的文字可以修改，按住可以拖曳");
                    else if (t == AnnotateView.MOSAIC_RECT) Ui.toast(EditorActivity.this, "拖曳框出要打馬賽克的範圍");
                    else if (t == AnnotateView.MOSAIC_BRUSH) Ui.toast(EditorActivity.this, "用手指塗抹要打馬賽克的地方，粗細用上面的滑桿調");
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 70),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = Ui.dp(this, 4);
            toolRow.addView(cell, lp);
        }
        ts.addView(toolRow);
        box.addView(ts, Ui.lpW());

        buildColors();
        styleTools();
        styleSize();
        return box;
    }

    private void buildColors() {
        colorRow.removeAllViews();
        for (int i = 0; i < COLORS.length; i++) {
            final int c = COLORS[i];
            View dot = new View(this);
            boolean on = canvas.color() == c;
            dot.setBackground(Ui.round(c, Ui.dp(this, 15),
                    on ? 0xFFFFFFFF : 0x55FFFFFF, Ui.dp(this, on ? 3 : 1)));
            dot.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    canvas.setColor(c);
                    Prefs.setEditorColor(EditorActivity.this, c);
                    buildColors();
                    styleSize();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
            lp.rightMargin = Ui.dp(this, 10);
            colorRow.addView(dot, lp);
        }
    }

    private void styleTools() {
        for (int i = 0; i < toolRow.getChildCount(); i++) {
            LinearLayout cell = (LinearLayout) toolRow.getChildAt(i);
            boolean on = canvas.tool() == i;
            int col = on ? T.PRIMARY : 0xFFC9D1DE;
            cell.setBackground(Ui.tappable(on ? Ui.withAlpha(T.PRIMARY, 0x26) : T.SURFACE2, Ui.dp(this, 12),
                    on ? Ui.withAlpha(T.PRIMARY, 0x8C) : T.BORDER, Ui.dp(this, on ? 1.4f : 1f)));
            ((ImageView) cell.getChildAt(0)).setImageDrawable(Icons.get(TOOL_ICONS[i], col));
            ((TextView) cell.getChildAt(2)).setTextColor(col);
        }
    }

    private void styleSize() {
        sizeText.setText((canvas.tool() == AnnotateView.TEXT ? "字級 " : "粗細 ") + canvas.size());
        // 馬賽克框不用粗細（格子大小固定），顏色也用不到
        boolean noSize = canvas.tool() == AnnotateView.MOSAIC_RECT;
        sizeBar.setEnabled(!noSize);
        sizeBar.setAlpha(noSize ? 0.35f : 1f);
        sizeText.setAlpha(noSize ? 0.35f : 1f);
        sizePreview.invalidate();
    }

    /** 粗細預覽：用目前顏色畫出實際粗細的圓點（文字工具畫一個「字」），太大時截在格子內。 */
    private class SizePreview extends View {
        private final android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        SizePreview() { super(EditorActivity.this); }
        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            float max = Math.min(getWidth(), getHeight()) - Ui.dp(EditorActivity.this, 8);
            // 照片還沒載入時用畫面寬度估算
            float px = canvas.hasBitmap() ? canvas.previewPx() : canvas.size() * 0.3f;
            float d = Math.max(Ui.dp(EditorActivity.this, 2), Math.min(max, px));
            int col = canvas.color();
            if (canvas.tool() == AnnotateView.MARKER) col = Ui.withAlpha(col, 0x99);
            if (canvas.tool() == AnnotateView.MOSAIC_RECT || canvas.tool() == AnnotateView.MOSAIC_BRUSH) col = 0xFF8A94A6;
            pt.setColor(col);
            if (canvas.tool() == AnnotateView.TEXT) {
                pt.setTextSize(Math.max(Ui.dp(EditorActivity.this, 8), d));
                pt.setFakeBoldText(true);
                pt.setTextAlign(android.graphics.Paint.Align.CENTER);
                float y = getHeight() / 2f - (pt.descent() + pt.ascent()) / 2f;
                c.drawText("字", getWidth() / 2f, y, pt);
            } else {
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, d / 2f, pt);
            }
        }
    }

    private void updateButtons() {
        undoBtn.setAlpha(canvas.canUndo() ? 1f : 0.3f);
        redoBtn.setAlpha(canvas.canRedo() ? 1f : 0.3f);
        undoBtn.setEnabled(canvas.canUndo());
        redoBtn.setEnabled(canvas.canRedo());
        saveBtn.setAlpha(canvas.isDirty() && !saving ? 1f : 0.45f);
    }

    // ---------- 載入 ----------

    private void load() {
        final ContentResolver cr = getContentResolver();
        final Uri uri = item.uri();
        new Thread(new Runnable() {
            @Override public void run() {
                Bitmap bm = null;
                try {
                    // ImageDecoder 會自動套用 EXIF 方向
                    ImageDecoder.Source src = ImageDecoder.createSource(cr, uri);
                    bm = ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                        @Override public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info,
                                                              ImageDecoder.Source s) {
                            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                            float k = Math.min(1f, (float) MAX_PX / Math.max(w, h));
                            if (k < 1f) d.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                            d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        }
                    });
                } catch (Throwable t) {
                    bm = null;
                }
                final Bitmap fb = bm;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        loading.setVisibility(View.GONE);
                        if (fb == null) {
                            Ui.toast(EditorActivity.this, "這張照片無法開啟編輯");
                            finish();
                            return;
                        }
                        canvas.setBitmap(fb);
                        // 等版面算好縮放比例後，預覽才會是實際粗細
                        canvas.post(new Runnable() { @Override public void run() { sizePreview.invalidate(); } });
                    }
                });
            }
        }, "editor-load").start();
    }

    // ---------- 文字 ----------

    private void askText(final float bx, final float by, final AnnotateView.Shape existing) {
        final EditText ed = new EditText(this);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ed.setMinLines(1);
        ed.setMaxLines(5);
        ed.setTextColor(T.TEXT);
        ed.setHintTextColor(T.FAINT);
        ed.setHint("輸入文字（可換行）");
        if (existing != null) {
            ed.setText(existing.text);
            ed.setSelection(ed.getText().length());
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        wrap.addView(ed);
        AlertDialog.Builder bd = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(existing == null ? "加入文字" : "修改文字")
                .setView(wrap)
                .setPositiveButton("確定", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String s = ed.getText().toString();
                        if (existing == null) canvas.addText(bx, by, s);
                        else canvas.replaceText(existing, s);
                    }
                })
                .setNegativeButton("取消", null);
        if (existing != null) {
            bd.setNeutralButton("刪除這段", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { canvas.replaceText(existing, ""); }
            });
        }
        AlertDialog dlg = bd.create();
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        ed.requestFocus();
    }

    // ---------- 存檔 ----------

    private void save() {
        if (saving || !canvas.hasBitmap()) return;
        if (!canvas.isDirty()) { Ui.toast(this, "還沒有任何標註"); return; }
        saving = true;
        updateButtons();
        loading.setVisibility(View.VISIBLE);
        final Bitmap out;
        try {
            out = canvas.render();
        } catch (OutOfMemoryError e) {
            saving = false;
            loading.setVisibility(View.GONE);
            updateButtons();
            Ui.toast(this, "照片太大，記憶體不足，無法存檔");
            return;
        }
        final ContentResolver cr = getContentResolver();
        new Thread(new Runnable() {
            @Override public void run() {
                long id = -1;
                String err = null;
                try {
                    id = writeCopy(cr, out);
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                out.recycle();
                final long fid = id;
                final String ferr = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        saving = false;
                        loading.setVisibility(View.GONE);
                        updateButtons();
                        if (fid < 0) {
                            Ui.toast(EditorActivity.this, "存檔失敗" + (ferr == null ? "" : "：" + ferr));
                            return;
                        }
                        Ui.toast(EditorActivity.this, "已另存為新照片，原照片沒有變動");
                        Intent r = new Intent();
                        r.putExtra("newId", fid);
                        setResult(RESULT_OK, r);
                        finish();
                    }
                });
            }
        }, "editor-save").start();
    }

    /** 寫進 MediaStore（App 自己新增的照片不需要額外權限），回傳新照片的 id。 */
    private long writeCopy(ContentResolver cr, Bitmap out) throws Exception {
        boolean png = "image/png".equalsIgnoreCase(item.mime);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String baseName = item.name;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);
        String name = baseName + "_編輯_" + stamp + (png ? ".png" : ".jpg");

        String[] dirs = { item.relPath, "Pictures/照片整理箱/" };
        Exception last = null;
        for (int i = 0; i < dirs.length; i++) {
            String dir = dirs[i];
            if (dir == null || dir.length() == 0) continue;
            Uri uri = null;
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, png ? "image/png" : "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, dir);
                // 拍攝時間沿用原照片，照片牆上會排在原照片旁邊
                v.put(MediaStore.Images.Media.DATE_TAKEN, item.timeMs);
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                if (uri == null) throw new IllegalStateException("系統不允許在 " + dir + " 新增檔案");
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) throw new IllegalStateException("無法寫入");
                try {
                    if (!out.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 95, os))
                        throw new IllegalStateException("壓縮失敗");
                } finally {
                    os.close();
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                return ContentUris.parseId(uri);
            } catch (Exception e) {
                last = e;
                if (uri != null) try { cr.delete(uri, null, null); } catch (Throwable ignored) { }
            }
        }
        throw last != null ? last : new IllegalStateException("找不到可以存放的資料夾");
    }

    // ---------- 離開 ----------

    private void askLeave() {
        if (!canvas.isDirty() || saving) { finish(); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("放棄這次的編輯？")
                .setMessage("還沒有存檔，離開後標註會消失。")
                .setPositiveButton("放棄", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { finish(); }
                })
                .setNeutralButton("另存後離開", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { save(); }
                })
                .setNegativeButton("繼續編輯", null)
                .show();
    }

    @Override public void onBackPressed() { askLeave(); }
}
