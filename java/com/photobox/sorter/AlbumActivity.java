package com.photobox.sorter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;

/** 單一相簿：上方封面＋名稱＋數量，照片牆，底部「新增照片／分享相簿」。 */
public class AlbumActivity extends Activity implements Store.Listener {

    private static final int REQ_PICK = 4001;

    public static void open(Context c, long albumId) {
        Intent i = new Intent(c, AlbumActivity.class);
        i.putExtra("album", albumId);
        c.startActivity(i);
    }

    private long albumId;
    private Albums.Album album;
    private MediaGrid grid;
    private LinearLayout topBar, selTop, bottomBar, selBar;
    private TextView title, selCount, heroName, heroCount, heroHint;
    private ImageView heroImg;
    private ArrayList<Item> items = new ArrayList<Item>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        albumId = getIntent().getLongExtra("album", -1);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(T.BG);
        root.setFitsSystemWindows(true);

        topBar = buildTop();
        root.addView(topBar, Ui.lpW());
        selTop = buildSelTop();
        selTop.setVisibility(View.GONE);
        root.addView(selTop, Ui.lpW());

        grid = new MediaGrid(this, Prefs.gridCols(this));
        grid.addHeader(buildHero());
        grid.setListener(new MediaGrid.Listener() {
            @Override public void onOpen(Item it, ArrayList<Item> ctx) {
                ViewerActivity.open(AlbumActivity.this, it, ctx);
            }
            @Override public void onSelectionChanged(int n) { updateSel(); }
            @Override public boolean onEnterSelect() { showSel(true); return true; }
            @Override public void onExitSelect() { showSel(false); }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        glp.weight = 1f;
        root.addView(grid, glp);

        bottomBar = buildBottom();
        root.addView(bottomBar, Ui.lpW());
        selBar = buildSelBar();
        selBar.setVisibility(View.GONE);
        root.addView(selBar, Ui.lpW());

        setContentView(root);
        Store.get().addListener(this);
        refresh();
    }

    @Override protected void onDestroy() {
        Store.get().removeListener(this);
        super.onDestroy();
    }

    @Override public void onStoreChanged() { refresh(); }

    private void refresh() {
        album = Albums.get().byId(albumId);
        if (album == null) {
            if (Store.get().loaded) { finish(); return; }
            return;
        }
        items = Albums.get().items(album);
        title.setText(album.name);
        heroName.setText(album.name);
        int[] pv = Albums.get().counts(album);
        heroCount.setText(Albums.countText(pv));
        Item cover = Albums.get().cover(album);
        if (cover != null) {
            heroImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Thumbs.full(heroImg, cover, 1080, null);
        } else {
            heroImg.setTag(null);
            heroImg.setScaleType(ImageView.ScaleType.CENTER);
            heroImg.setImageDrawable(Ui.icon(this, album.isFav() ? Icons.STAR_O : Icons.ALBUM, T.FAINT, 48));
        }
        heroHint.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        heroHint.setText(album.isFav()
                ? "在檢視照片時按下方的 ☆，就會收進這裡。"
                : "這本相簿還是空的。按下方的「新增照片」挑選要放進來的照片和影片。");
        grid.setItems(items, false, true);
        if (grid.inSelect()) updateSel();
    }

    // ---------- 版面 ----------

    private ImageView iconBtn(int icon, int color, View.OnClickListener cl) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(Icons.get(icon, color));
        int p = Ui.dp(this, 10);
        v.setPadding(p, p, p, p);
        v.setBackground(Ui.tappable(0x00000000, Ui.dp(this, 22)));
        if (cl != null) v.setOnClickListener(cl);
        return v;
    }

    private LinearLayout buildTop() {
        LinearLayout l = Ui.rowH(this);
        l.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        l.addView(iconBtn(Icons.BACK, T.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        title = Ui.tv(this, "", 18f, T.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = Ui.lpWeight();
        tlp.leftMargin = Ui.dp(this, 6);
        l.addView(title, tlp);
        final ImageView more = iconBtn(Icons.MORE, T.TEXT, null);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        l.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return l;
    }

    private LinearLayout buildSelTop() {
        LinearLayout l = Ui.rowH(this);
        l.setBackgroundColor(T.SURFACE);
        l.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        l.addView(iconBtn(Icons.CLOSE, T.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { grid.exitSelect(); }
        }), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        selCount = Ui.tv(this, "", 14f, T.TEXT, true);
        LinearLayout.LayoutParams clp = Ui.lpWeight();
        clp.leftMargin = Ui.dp(this, 4);
        l.addView(selCount, clp);
        TextView all = Ui.tv(this, "全選", 13f, T.PRIMARY, true);
        all.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(this, 13), T.BORDER, Ui.dp(this, 1)));
        all.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { grid.selectAll(); }
        });
        l.addView(all);
        TextView share = Ui.sharePill(this);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Share.items(AlbumActivity.this, grid.selectedItems()); }
        });
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = Ui.dp(this, 8);
        l.addView(share, ilp);
        return l;
    }

    private View buildHero() {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 10));
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.round(T.SURFACE2, Ui.dp(this, 18)));
        card.setClipToOutline(true);
        int h = Ui.dp(this, 230);
        heroImg = new ImageView(this);
        card.addView(heroImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ 0x00000000, 0x33000000, 0xCC000000 }));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

        LinearLayout texts = Ui.col(this);
        texts.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 16));
        heroName = Ui.tv(this, "", 24f, 0xFFFFFFFF, true);
        heroName.setMaxLines(2);
        heroName.setShadowLayer(Ui.dp(this, 4), 0, 0, 0x99000000);
        heroCount = Ui.tv(this, "", 14f, 0xE6FFFFFF, false);
        heroCount.setShadowLayer(Ui.dp(this, 3), 0, 0, 0x99000000);
        texts.addView(heroName, Ui.lpW());
        texts.addView(Ui.space(this, 6));
        texts.addView(heroCount, Ui.lpW());
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.BOTTOM;
        card.addView(texts, tlp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Item c = Albums.get().cover(album);
                if (c != null && !grid.inSelect()) ViewerActivity.open(AlbumActivity.this, c, items);
            }
        });

        LinearLayout col = Ui.col(this);
        col.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        heroHint = Ui.tv(this, "", 14f, T.DIM, false);
        heroHint.setGravity(Gravity.CENTER);
        heroHint.setLineSpacing(Ui.dp(this, 3), 1f);
        heroHint.setPadding(Ui.dp(this, 24), Ui.dp(this, 40), Ui.dp(this, 24), Ui.dp(this, 20));
        col.addView(heroHint, Ui.lpW());
        f.addView(col);
        return f;
    }

    private LinearLayout buildBottom() {
        LinearLayout l = Ui.rowH(this);
        l.setBackgroundColor(T.SURFACE);
        l.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        TextView add = Ui.btn(this, "＋ 新增照片", T.PRIMARY, 0xFF0B0E16);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick(); }
        });
        LinearLayout.LayoutParams lp1 = Ui.lpWeight();
        lp1.rightMargin = Ui.dp(this, 10);
        l.addView(add, lp1);
        TextView share = Ui.btnGhost(this, "分享相簿", T.PRIMARY);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (items.isEmpty()) { Ui.toast(AlbumActivity.this, "相簿是空的"); return; }
                Share.items(AlbumActivity.this, items);
            }
        });
        l.addView(share, Ui.lpWeight());
        return l;
    }

    private LinearLayout buildSelBar() {
        LinearLayout row = Ui.rowH(this);
        row.setBackgroundColor(T.SURFACE);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 9), Ui.dp(this, 10), Ui.dp(this, 11));
        row.addView(small("移出相簿", T.DIM, false, new View.OnClickListener() {
            @Override public void onClick(View v) { removeSelected(); }
        }), wlp(true));
        row.addView(small("設為封面", T.PRIMARY, false, new View.OnClickListener() {
            @Override public void onClick(View v) { setCover(); }
        }), wlp(true));
        row.addView(small("加入其他相簿", T.MOVE, false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                AlbumDialogs.addTo(AlbumActivity.this, grid.selectedItems(), new Runnable() {
                    @Override public void run() { grid.exitSelect(); }
                });
            }
        }), wlp(true));
        row.addView(small("加入待刪", T.TRASH, true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                ArrayList<Item> sel = grid.selectedItems();
                if (sel.isEmpty()) return;
                Store.get().setStates(AlbumActivity.this, sel, Db.STATE_PENDING);
                Ui.toast(AlbumActivity.this, "已加入待刪清單 " + sel.size() + " 個項目（到「待刪」分頁確認後才會刪除）");
                grid.exitSelect();
            }
        }), wlp(false));
        return row;
    }

    private TextView small(String label, int color, boolean solid, View.OnClickListener cl) {
        TextView t = solid ? Ui.btn(this, label, color, 0xFF1A0C0C) : Ui.btnGhost(this, label, color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 11), Ui.dp(this, 2), Ui.dp(this, 11));
        t.setMaxLines(1);
        t.setOnClickListener(cl);
        return t;
    }

    private LinearLayout.LayoutParams wlp(boolean gap) {
        LinearLayout.LayoutParams lp = Ui.lpWeight();
        if (gap) lp.rightMargin = Ui.dp(this, 6);
        return lp;
    }

    // ---------- 動作 ----------

    private void showSel(boolean on) {
        topBar.setVisibility(on ? View.GONE : View.VISIBLE);
        selTop.setVisibility(on ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        selBar.setVisibility(on ? View.VISIBLE : View.GONE);
        updateSel();
    }

    private void updateSel() {
        selCount.setText("已選 " + grid.selectedCount() + " 個　·　" + Item.formatSize(grid.selectedBytes()));
    }

    private void removeSelected() {
        ArrayList<Item> sel = grid.selectedItems();
        if (sel.isEmpty() || album == null) return;
        int n = Albums.get().remove(this, album, sel);
        Ui.toast(this, "已從「" + album.name + "」移出 " + n + " 個項目（檔案本身不受影響）");
        grid.exitSelect();
    }

    private void setCover() {
        ArrayList<Item> sel = grid.selectedItems();
        if (album == null) return;
        if (sel.size() != 1) { Ui.toast(this, "請只選一張當作封面"); return; }
        Albums.get().setCover(this, album, sel.get(0).id);
        Ui.toast(this, "已設為封面");
        grid.exitSelect();
    }

    private void pick() {
        if (album == null) return;
        Intent i = new Intent(this, PickerActivity.class);
        i.putExtra("album", album.id);
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && album != null) {
            long[] ids = data.getLongArrayExtra("ids");
            if (ids == null || ids.length == 0) return;
            ArrayList<Item> chosen = new ArrayList<Item>();
            for (int i = 0; i < ids.length; i++) {
                Item it = Store.get().byId.get(ids[i]);
                if (it != null) chosen.add(it);
            }
            AlbumDialogs.addAndReport(this, album, chosen);
        }
    }

    private void showMenu(View anchor) {
        if (album == null) return;
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "選取項目");
        if (!album.isFav()) pm.getMenu().add(0, 2, 1, "重新命名相簿");
        pm.getMenu().add(0, 3, 2, "複製相簿");
        if (!album.isFav()) pm.getMenu().add(0, 4, 4, "刪除相簿");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem m) {
                switch (m.getItemId()) {
                    case 1:
                        if (items.isEmpty()) Ui.toast(AlbumActivity.this, "相簿是空的");
                        else grid.enterSelect(null);
                        break;
                    case 2: AlbumDialogs.rename(AlbumActivity.this, album, null); break;
                    case 3:
                        AlbumDialogs.duplicate(AlbumActivity.this, album, new AlbumDialogs.AlbumCallback() {
                            @Override public void onAlbum(Albums.Album copy) { open(AlbumActivity.this, copy.id); }
                        });
                        break;
                    case 4:
                        AlbumDialogs.delete(AlbumActivity.this, album, new Runnable() {
                            @Override public void run() { finish(); }
                        });
                        break;
                }
                return true;
            }
        });
        pm.show();
    }

    @Override public void onBackPressed() {
        if (grid.inSelect()) { grid.exitSelect(); return; }
        super.onBackPressed();
    }
}
