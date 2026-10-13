package com.photobox.sorter;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** 挑選要放進相簿的照片／影片（點一下就是選，可連續選）。 */
public class PickerActivity extends Activity {

    private Albums.Album album;
    private MediaGrid grid;
    private TextView count, confirm;
    private LinearLayout chips;
    private int type = 0;            // 0 全部 1 照片 2 影片
    private boolean hideExisting = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        album = Albums.get().byId(getIntent().getLongExtra("album", -1));
        if (album == null) { finish(); return; }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(T.BG);
        root.setFitsSystemWindows(true);

        LinearLayout top = Ui.rowH(this);
        top.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));
        ImageView back = new ImageView(this);
        back.setImageDrawable(Icons.get(Icons.CLOSE, T.TEXT));
        int p = Ui.dp(this, 11);
        back.setPadding(p, p, p, p);
        back.setBackground(Ui.tappable(0, Ui.dp(this, 22)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout titles = Ui.col(this);
        TextView t = Ui.tv(this, "新增到「" + album.name + "」", 17f, T.TEXT, true);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(t, Ui.lpW());
        count = Ui.tv(this, "", 12.5f, T.DIM, false);
        titles.addView(Ui.space(this, 3));
        titles.addView(count);
        LinearLayout.LayoutParams tlp = Ui.lpWeight();
        tlp.leftMargin = Ui.dp(this, 6);
        top.addView(titles, tlp);
        TextView all = Ui.tv(this, "全選", 13f, T.PRIMARY, true);
        all.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(this, 13), T.BORDER, Ui.dp(this, 1)));
        all.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (grid.allSelected()) grid.exitSelect(); else grid.selectAll();
            }
        });
        top.addView(all);
        root.addView(top, Ui.lpW());

        chips = Ui.rowH(this);
        chips.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        root.addView(chips, Ui.lpW());

        grid = new MediaGrid(this, Prefs.gridCols(this));
        grid.setStateDots(false);
        grid.setEmptyText("沒有可以加入的項目");
        grid.setPickMode(true);
        grid.setListener(new MediaGrid.Listener() {
            @Override public void onOpen(Item it, ArrayList<Item> ctx) { }
            @Override public void onSelectionChanged(int n) { updateCount(); }
            @Override public boolean onEnterSelect() { return true; }
            @Override public void onExitSelect() { updateCount(); }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        glp.weight = 1f;
        root.addView(grid, glp);

        LinearLayout bottom = Ui.rowH(this);
        bottom.setBackgroundColor(T.SURFACE);
        bottom.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        confirm = Ui.btn(this, "", T.PRIMARY, 0xFF0B0E16);
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { done(); }
        });
        bottom.addView(confirm, Ui.lpWeight());
        root.addView(bottom, Ui.lpW());

        setContentView(root);
        rebuild();
    }

    private TextView chip(String label, boolean on, View.OnClickListener cl) {
        TextView t = Ui.tv(this, label, 13f, on ? 0xFF0B0E16 : T.DIM, true);
        t.setBackground(Ui.tappable(on ? T.PRIMARY : T.SURFACE2, Ui.dp(this, 16),
                on ? T.PRIMARY : T.BORDER, Ui.dp(this, 1)));
        t.setPadding(Ui.dp(this, 13), Ui.dp(this, 7), Ui.dp(this, 13), Ui.dp(this, 7));
        t.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 7);
        t.setLayoutParams(lp);
        return t;
    }

    private void rebuild() {
        chips.removeAllViews();
        String[] names = { "全部", "照片", "影片" };
        for (int i = 0; i < 3; i++) {
            final int k = i;
            chips.addView(chip(names[i], type == i, new View.OnClickListener() {
                @Override public void onClick(View v) { type = k; rebuild(); }
            }));
        }
        chips.addView(chip("隱藏已在相簿的", hideExisting, new View.OnClickListener() {
            @Override public void onClick(View v) { hideExisting = !hideExisting; rebuild(); }
        }));

        ArrayList<Item> src = Store.get().all;
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < src.size(); i++) {
            Item it = src.get(i);
            if (type == 1 && it.video) continue;
            if (type == 2 && !it.video) continue;
            if (hideExisting && album.items.containsKey(it.id)) continue;
            out.add(it);
        }
        grid.setItems(out, true, true);
        updateCount();
    }

    private void updateCount() {
        int n = grid.selectedCount();
        count.setText(n == 0 ? "點選要加入的項目，長按可連續選取一段"
                : ("已選 " + n + " 個　·　" + Item.formatSize(grid.selectedBytes())));
        confirm.setText(n == 0 ? "請先點選照片" : ("加入 " + n + " 個項目"));
        confirm.setAlpha(n == 0 ? 0.5f : 1f);
    }

    private void done() {
        ArrayList<Item> sel = grid.selectedItems();
        if (sel.isEmpty()) { Ui.toast(this, "還沒選取任何項目"); return; }
        long[] ids = new long[sel.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = sel.get(i).id;
        Intent r = new Intent();
        r.putExtra("ids", ids);
        setResult(RESULT_OK, r);
        finish();
    }
}
