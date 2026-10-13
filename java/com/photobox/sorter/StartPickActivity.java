package com.photobox.sorter;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** 從整理頁直接挑「從哪一張開始整理」：點一下照片就選方向，然後回到整理頁。 */
public class StartPickActivity extends Activity {

    private MediaGrid grid;
    private LinearLayout chips;
    private boolean onlyUnsorted = false;
    private int year = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(T.BG);
        root.setFitsSystemWindows(true);

        LinearLayout top = Ui.rowH(this);
        top.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));
        ImageView back = new ImageView(this);
        back.setImageDrawable(Icons.get(Icons.BACK, T.TEXT));
        int p = Ui.dp(this, 10);
        back.setPadding(p, p, p, p);
        back.setBackground(Ui.tappable(0, Ui.dp(this, 22)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        LinearLayout titles = Ui.col(this);
        titles.addView(Ui.tv(this, "從哪一張開始整理？", 18f, T.TEXT, true));
        titles.addView(Ui.space(this, 3));
        titles.addView(Ui.tv(this, "點一下照片，再選要往舊或往新的方向", 12.5f, T.DIM, false));
        LinearLayout.LayoutParams tlp = Ui.lpWeight();
        tlp.leftMargin = Ui.dp(this, 6);
        top.addView(titles, tlp);
        root.addView(top, Ui.lpW());

        chips = Ui.rowH(this);
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        sv.setClipToPadding(false);
        sv.addView(chips);
        root.addView(sv, Ui.lpW());

        grid = new MediaGrid(this, Prefs.gridCols(this));
        grid.setEmptyText("沒有符合的照片");
        grid.setDateSelectEnabled(false);
        grid.setListener(new MediaGrid.Listener() {
            @Override public void onOpen(Item it, ArrayList<Item> ctx) {
                SortStartDialog.show(StartPickActivity.this, it, new Runnable() {
                    @Override public void run() { finish(); }
                });
            }
            @Override public void onSelectionChanged(int n) { }
            @Override public boolean onEnterSelect() { return false; }   // 這裡只挑一張，不進入多選
            @Override public void onExitSelect() { }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        glp.weight = 1f;
        root.addView(grid, glp);

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
        ArrayList<Item> src = Store.get().all;
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < src.size(); i++) {
            Item it = src.get(i);
            if (onlyUnsorted && Store.get().stateOf(it) != Db.STATE_NONE) continue;
            out.add(it);
        }
        grid.setItems(out, true, true);

        chips.removeAllViews();
        chips.addView(chip("只顯示未整理", onlyUnsorted, new View.OnClickListener() {
            @Override public void onClick(View v) { onlyUnsorted = !onlyUnsorted; rebuild(); }
        }));
        ArrayList<Integer> ys = Store.get().years(out);
        for (int i = 0; i < ys.size(); i++) {
            final int y = ys.get(i).intValue();
            chips.addView(chip(y + " 年", year == y, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    year = y;
                    rebuildChipsOnly();
                    grid.jumpTo(y * 100 + 13);
                }
            }));
        }
    }

    private void rebuildChipsOnly() {
        // 只更新膠囊的選取樣式，不重排照片牆（保留捲動位置）
        for (int i = 1; i < chips.getChildCount(); i++) {
            TextView t = (TextView) chips.getChildAt(i);
            boolean on = t.getText().toString().equals(year + " 年");
            t.setTextColor(on ? 0xFF0B0E16 : T.DIM);
            t.setBackground(Ui.tappable(on ? T.PRIMARY : T.SURFACE2, Ui.dp(this, 16),
                    on ? T.PRIMARY : T.BORDER, Ui.dp(this, 1)));
        }
    }
}
