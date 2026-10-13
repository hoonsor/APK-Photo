package com.photobox.sorter;

import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayList;

/** 相簿頁：相簿卡片牆、右下「＋」、右上選單（建立／重新命名／複製／刪除）。 */
public class AlbumsPage extends Page {

    private static final int COLS = 3;
    private GridView gridView;
    private TextView empty, subtitle;
    private ArrayList<Albums.Album> albums = new ArrayList<Albums.Album>();
    private final CardAdapter adapter = new CardAdapter();

    public AlbumsPage(MainActivity a) { super(a); }

    @Override
    public View build() {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(T.BG);

        LinearLayout col = Ui.col(a);
        LinearLayout head = Ui.rowH(a);
        head.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 6));
        head.addView(iconBtn(Icons.MENU, T.TEXT, new View.OnClickListener() {
            @Override public void onClick(View v) { a.openDrawer(); }
        }), new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));

        LinearLayout titles = Ui.col(a);
        titles.addView(Ui.tv(a, "相簿", 21f, T.TEXT, true));
        subtitle = Ui.tv(a, "", 12f, T.FAINT, false);
        titles.addView(Ui.space(a, 3));
        titles.addView(subtitle);
        LinearLayout.LayoutParams tlp = Ui.lpWeight();
        tlp.leftMargin = Ui.dp(a, 6);
        head.addView(titles, tlp);

        final ImageView more = iconBtn(Icons.MORE, T.TEXT, null);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        head.addView(more, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        col.addView(head, Ui.lpW());

        FrameLayout body = new FrameLayout(a);
        gridView = new GridView(a);
        gridView.setNumColumns(COLS);
        gridView.setHorizontalSpacing(Ui.dp(a, 10));
        gridView.setVerticalSpacing(Ui.dp(a, 12));
        gridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        gridView.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 96));
        gridView.setClipToPadding(false);
        gridView.setSelector(new android.graphics.drawable.ColorDrawable(0));
        gridView.setAdapter(adapter);
        gridView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            int lastW = -1;
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                if (r - l != lastW) {
                    lastW = r - l;
                    v.post(new Runnable() { @Override public void run() { adapter.notifyDataSetChanged(); } });
                }
            }
        });
        body.addView(gridView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = Ui.tv(a, "還沒有自己建立的相簿。\n\n在照片頁長按任一張照片，左側就會出現「建立相簿」；"
                + "也可以按右下角的 ＋。\n\n相簿只是整理用的清單，不會搬動或複製檔案，"
                + "同一張照片可以同時放在好幾本相簿裡。", 14f, T.DIM, false);
        empty.setGravity(Gravity.CENTER);
        empty.setLineSpacing(Ui.dp(a, 3), 1f);
        empty.setPadding(Ui.dp(a, 32), 0, Ui.dp(a, 32), Ui.dp(a, 60));
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.gravity = Gravity.BOTTOM;
        elp.bottomMargin = Ui.dp(a, 40);
        body.addView(empty, elp);

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        blp.weight = 1f;
        col.addView(body, blp);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 右下角＋ ----
        ImageView fab = new ImageView(a);
        fab.setImageDrawable(Icons.get(Icons.PLUS, 0xFF0B0E16));
        int p = Ui.dp(a, 16);
        fab.setPadding(p, p, p, p);
        fab.setBackground(Ui.tappable(T.PRIMARY, Ui.dp(a, 30)));
        fab.setElevation(Ui.dp(a, 6));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createAlbum(); }
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(a, 60), Ui.dp(a, 60));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = Ui.dp(a, 20);
        flp.bottomMargin = Ui.dp(a, 20);
        root.addView(fab, flp);
        return root;
    }

    private ImageView iconBtn(int icon, int color, View.OnClickListener cl) {
        ImageView v = new ImageView(a);
        v.setImageDrawable(Icons.get(icon, color));
        int p = Ui.dp(a, 10);
        v.setPadding(p, p, p, p);
        v.setBackground(Ui.tappable(0x00000000, Ui.dp(a, 22)));
        if (cl != null) v.setOnClickListener(cl);
        return v;
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(a, anchor);
        pm.getMenu().add(0, 1, 0, "建立相簿");
        pm.getMenu().add(0, 2, 1, "重新命名相簿");
        pm.getMenu().add(0, 3, 2, "複製相簿");
        pm.getMenu().add(0, 4, 3, "刪除相簿");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem m) {
                switch (m.getItemId()) {
                    case 1: createAlbum(); break;
                    case 2:
                        AlbumDialogs.pickAlbum(a, "要重新命名哪一本？", false, new AlbumDialogs.AlbumCallback() {
                            @Override public void onAlbum(Albums.Album al) { AlbumDialogs.rename(a, al, null); }
                        });
                        break;
                    case 3:
                        AlbumDialogs.pickAlbum(a, "要複製哪一本？", true, new AlbumDialogs.AlbumCallback() {
                            @Override public void onAlbum(Albums.Album al) { AlbumDialogs.duplicate(a, al, null); }
                        });
                        break;
                    case 4:
                        AlbumDialogs.pickAlbum(a, "要刪除哪一本？", false, new AlbumDialogs.AlbumCallback() {
                            @Override public void onAlbum(Albums.Album al) { AlbumDialogs.delete(a, al, null); }
                        });
                        break;
                }
                return true;
            }
        });
        pm.show();
    }

    private void createAlbum() {
        AlbumDialogs.create(a, null, new AlbumDialogs.AlbumCallback() {
            @Override public void onAlbum(Albums.Album al) {
                // 建好直接進去，方便馬上按「新增照片」
                AlbumActivity.open(a, al.id);
            }
        });
    }

    public void refresh() {
        if (root == null) return;
        if (Store.get().loaded && Albums.get().favorites() == null) Albums.get().ensureFavorites(a);
        albums = Albums.get().ordered();
        adapter.notifyDataSetChanged();
        int normal = 0;
        for (int i = 0; i < albums.size(); i++) if (!albums.get(i).isFav()) normal++;
        subtitle.setText(normal + " 本相簿　·　長按相簿可改名、複製或刪除");
        empty.setVisibility(normal == 0 ? View.VISIBLE : View.GONE);
    }

    private class CardAdapter extends BaseAdapter {
        @Override public int getCount() { return albums.size(); }
        @Override public Object getItem(int p) { return albums.get(p); }
        @Override public long getItemId(int p) { return albums.get(p).id; }

        @Override public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout card = (LinearLayout) cv;
            int w = (gridView.getWidth() - gridView.getPaddingLeft() - gridView.getPaddingRight()
                    - Ui.dp(a, 10) * (COLS - 1)) / COLS;
            if (w <= 0) w = Ui.dp(a, 110);
            if (card == null) {
                card = Ui.col(a);
                card.setBackground(Ui.tappable(T.SURFACE, Ui.dp(a, 14), T.BORDER, Ui.dp(a, 1)));
                FrameLayout thumb = new FrameLayout(a);
                thumb.setId(1);
                thumb.setBackground(Ui.round(T.SURFACE3, Ui.dp(a, 13)));
                thumb.setClipToOutline(true);
                ImageView iv = new ImageView(a);
                iv.setId(2);
                thumb.addView(iv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ImageView badge = new ImageView(a);
                badge.setId(5);
                int bp = Ui.dp(a, 4);
                badge.setPadding(bp, bp, bp, bp);
                badge.setBackground(Ui.round(0x99000000, Ui.dp(a, 12)));
                FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24));
                blp.gravity = Gravity.TOP | Gravity.START;
                blp.leftMargin = blp.topMargin = Ui.dp(a, 6);
                thumb.addView(badge, blp);
                card.addView(thumb);
                TextView name = Ui.tv(a, "", 13.5f, T.TEXT, true);
                name.setId(3);
                name.setMaxLines(2);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setPadding(Ui.dp(a, 9), Ui.dp(a, 8), Ui.dp(a, 9), 0);
                card.addView(name, Ui.lpW());
                TextView cnt = Ui.tv(a, "", 11.5f, T.FAINT, false);
                cnt.setId(4);
                cnt.setPadding(Ui.dp(a, 9), Ui.dp(a, 4), Ui.dp(a, 9), Ui.dp(a, 10));
                card.addView(cnt, Ui.lpW());
            }
            final Albums.Album al = albums.get(pos);
            View thumb = card.findViewById(1);
            thumb.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, w));
            ImageView iv = (ImageView) card.findViewById(2);
            Item cover = Albums.get().cover(al);
            if (cover != null) {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Thumbs.grid(iv, cover, 384);
            } else {
                iv.setTag(null);
                iv.setScaleType(ImageView.ScaleType.CENTER);
                iv.setImageDrawable(Ui.icon(a, al.isFav() ? Icons.STAR_O : Icons.ALBUM, T.FAINT, 34));
            }
            ImageView badge = (ImageView) card.findViewById(5);
            badge.setVisibility(al.isFav() ? View.VISIBLE : View.GONE);
            badge.setImageDrawable(Icons.get(Icons.STAR, 0xFFF2C14E));
            ((TextView) card.findViewById(3)).setText(al.name);
            int[] pv = Albums.get().counts(al);
            ((TextView) card.findViewById(4)).setText(pv[1] > 0
                    ? (pv[0] + " 張・" + pv[1] + " 部影片") : (pv[0] + " 張照片"));
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { AlbumActivity.open(a, al.id); }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    AlbumDialogs.manage(a, al, null, null);
                    return true;
                }
            });
            return card;
        }
    }

    @Override public void onShow() { refresh(); }
    @Override public void onStoreChanged() { refresh(); }
}
