package com.photobox.sorter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

public class SortPage extends Page {

    private ArrayList<Item> deck = new ArrayList<Item>();
    private int index = 0;
    private String srcAlbum = null;
    private int srcYm = 0;
    private String startLabel = null;

    private FrameLayout stack;
    private Card[] cards = new Card[2];
    private int f = 0;
    private TextView srcChip, progressText, undoBtn;
    private ProgressBar bar;
    private LinearLayout actionRow;
    private LinearLayout emptyBox;
    private TextView emptyTitle, emptyStats;

    private float x0, y0;
    private long downTime;
    private boolean dragging = false;
    private boolean busy = false;

    public SortPage(MainActivity a) { super(a); }

    private Card front() { return cards[f]; }
    private Card back() { return cards[1 - f]; }

    @Override
    public View build() {
        LinearLayout root = Ui.col(a);
        root.setBackgroundColor(T.BG);

        // ---- 標題列 ----
        LinearLayout head = Ui.rowH(a);
        head.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 8));

        srcChip = Ui.tv(a, "全部未整理", 14.5f, T.TEXT, true);
        srcChip.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 18), T.BORDER, Ui.dp(a, 1)));
        srcChip.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        srcChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickSource(); }
        });
        srcChip.setMaxLines(1);
        srcChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(srcChip, sclp);

        // 直接挑「從某一張開始」，不必再進來源選單最下面
        TextView pickStart = Ui.tv(a, "從某張開始", 14f, T.PRIMARY, true);
        pickStart.setBackground(Ui.tappable(Ui.withAlpha(T.PRIMARY, 0x22), Ui.dp(a, 18),
                Ui.withAlpha(T.PRIMARY, 0x88), Ui.dp(a, 1)));
        pickStart.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        pickStart.setMaxLines(1);
        pickStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.startActivity(new android.content.Intent(a, StartPickActivity.class)); }
        });
        LinearLayout.LayoutParams pslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pslp.leftMargin = Ui.dp(a, 8);
        pslp.rightMargin = Ui.dp(a, 8);
        head.addView(pickStart, pslp);

        undoBtn = Ui.tv(a, "還原", 14f, T.DIM, true);
        undoBtn.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 18), T.BORDER, Ui.dp(a, 1)));
        undoBtn.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 8));
        undoBtn.setCompoundDrawablePadding(Ui.dp(a, 6));
        undoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doUndo(); }
        });
        head.addView(undoBtn);
        root.addView(head, Ui.lpW());

        // ---- 進度 ----
        LinearLayout pr = Ui.col(a);
        pr.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 8));
        progressText = Ui.tv(a, "", 12.5f, T.DIM, false);
        pr.addView(progressText, Ui.lpW());
        pr.addView(Ui.space(a, 6));
        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(T.PRIMARY));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(T.SURFACE3));
        pr.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 4)));
        root.addView(pr, Ui.lpW());

        // ---- 卡片區 ----
        stack = new FrameLayout(a);
        stack.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        stack.setClipChildren(false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        slp.weight = 1f;
        root.addView(stack, slp);

        cards[0] = new Card(a);
        cards[1] = new Card(a);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        stack.addView(cards[1], clp);
        stack.addView(cards[0], new FrameLayout.LayoutParams(clp));
        setDepth();

        emptyBox = buildEmpty();
        stack.addView(emptyBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        emptyBox.setVisibility(View.GONE);

        stack.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) { return onStackTouch(e); }
        });

        // ---- 動作列 ----
        actionRow = Ui.rowH(a);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 12));
        root.addView(actionRow, Ui.lpW());
        rebuildActionRow();

        return root;
    }

    private void setDepth() {
        front().setElevation(Ui.dp(a, 12));
        front().setScaleX(1f); front().setScaleY(1f);
        front().setTranslationX(0); front().setTranslationY(0); front().setRotation(0);
        front().setAlpha(1f);
        back().setElevation(Ui.dp(a, 3));
        back().setScaleX(0.94f); back().setScaleY(0.94f);
        back().setTranslationX(0); back().setTranslationY(Ui.dp(a, 12)); back().setRotation(0);
        back().setAlpha(0.9f);
        back().clearHint();
    }

    private LinearLayout buildEmpty() {
        LinearLayout box = Ui.col(a);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.round(T.SURFACE, Ui.dp(a, 22)));
        box.setPadding(Ui.dp(a, 26), Ui.dp(a, 26), Ui.dp(a, 26), Ui.dp(a, 26));

        ImageView ic = new ImageView(a);
        ic.setImageDrawable(Icons.get(Icons.CHECK, T.KEEP));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(a, 54), Ui.dp(a, 54));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(ic, ilp);
        box.addView(Ui.space(a, 16));

        emptyTitle = Ui.tv(a, "這個來源已經整理完了", 19f, T.TEXT, true);
        emptyTitle.setGravity(Gravity.CENTER);
        box.addView(emptyTitle, Ui.lpW());
        box.addView(Ui.space(a, 8));
        emptyStats = Ui.tv(a, "", 14f, T.DIM, false);
        emptyStats.setGravity(Gravity.CENTER);
        emptyStats.setLineSpacing(Ui.dp(a, 3), 1f);
        box.addView(emptyStats, Ui.lpW());
        box.addView(Ui.space(a, 22));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(a, 230),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;

        TextView b1 = Ui.btn(a, "前往待刪清單", T.PRIMARY, 0xFF0B0E16);
        b1.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.select(MainActivity.TAB_TRASH); }
        });
        box.addView(b1, blp);
        box.addView(Ui.space(a, 10));

        TextView b2 = Ui.btnGhost(a, "換一個來源", T.TEXT);
        b2.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickSource(); }
        });
        box.addView(b2, blp);
        return box;
    }

    private void rebuildActionRow() {
        if (actionRow == null) return;
        actionRow.removeAllViews();
        int[] dirs = { Prefs.LEFT, Prefs.DOWN, Prefs.UP, Prefs.RIGHT };
        for (int i = 0; i < dirs.length; i++) {
            final int dir = dirs[i];
            int act = Prefs.action(a, dir);
            if (act == Prefs.ACT_NONE) continue;
            LinearLayout cell = Ui.col(a);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 10), 0);

            ImageView iv = new ImageView(a);
            iv.setImageDrawable(Icons.get(Prefs.icon(act), Prefs.color(act)));
            int pad = Ui.dp(a, 15);
            iv.setPadding(pad, pad, pad, pad);
            iv.setBackground(Ui.tappable(T.SURFACE2, Ui.dp(a, 28),
                    Ui.withAlpha(Prefs.color(act), 0x55), Ui.dp(a, 1.4f)));
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { flingOut(dir); }
            });
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
            cell.addView(Ui.space(a, 6));
            cell.addView(Ui.tv(a, shortName(act), 11.5f, T.DIM, false));
            actionRow.addView(cell);
        }
    }

    private String shortName(int act) {
        switch (act) {
            case Prefs.ACT_TRASH: return "待刪";
            case Prefs.ACT_KEEP:  return "保留";
            case Prefs.ACT_MOVE:  return "移動";
            case Prefs.ACT_SKIP:  return "跳過";
            default: return "";
        }
    }

    // ---------- 來源 ----------

    public void setSource(String album, int ym) {
        srcAlbum = album;
        srcYm = ym;
        startLabel = null;
        rebuildDeck();
    }

    /** 舊入口：往舊的整理，自動決定是否包含已整理過的。 */
    public void startAt(long id) { startAt(id, false, -1); }

    /**
     * 從指定的那一張開始整理。
     * @param newer   true＝往較新的照片（時間往後）；false＝往較舊的照片（時間往前）
     * @param include 0＝只整理還沒整理過的（起點那張一定包含）；1＝全部；-1＝那張未整理就只看未整理，否則全部
     */
    public void startAt(long id, boolean newer, int include) {
        ArrayList<Item> all = Store.get().all;   // 新到舊
        int pos = indexOf(all, id);
        if (pos < 0) {
            Ui.toast(a, "在目前的清單中找不到那張照片");
            return;
        }
        Item start = all.get(pos);
        boolean includeAll = include == 1
                || (include == -1 && Store.get().stateOf(start) != Db.STATE_NONE);

        ArrayList<Item> list = new ArrayList<Item>();
        if (newer) {
            for (int i = pos; i >= 0; i--) add(list, all.get(i), includeAll, start);
        } else {
            for (int i = pos; i < all.size(); i++) add(list, all.get(i), includeAll, start);
        }
        srcAlbum = null;
        srcYm = 0;
        deck = list;
        index = 0;
        startLabel = "從 " + start.dayText() + (newer ? " 往新" : " 往舊")
                + (includeAll ? "（全部）" : "（未整理）");
        showCurrent();
        Ui.toast(a, "從 " + start.dayText() + " 這張開始" + (newer ? "往較新的照片" : "往較舊的照片")
                + "整理，共 " + list.size() + " 張");
    }

    private static void add(ArrayList<Item> out, Item it, boolean includeAll, Item start) {
        if (it == start || includeAll || Store.get().stateOf(it) == Db.STATE_NONE) out.add(it);
    }

    private int indexOf(ArrayList<Item> list, long id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id == id) return i;
        return -1;
    }

    private void pickSource() {
        final ArrayList<String> labels = new ArrayList<String>();
        final ArrayList<String> albums = new ArrayList<String>();
        final ArrayList<Integer> yms = new ArrayList<Integer>();

        labels.add("全部未整理（" + Store.get().count(Db.STATE_NONE) + "）");
        albums.add(null); yms.add(Integer.valueOf(0));

        ArrayList<String[]> al = Store.get().albums();
        for (int i = 0; i < al.size(); i++) {
            labels.add("資料夾：" + al.get(i)[0] + "（" + al.get(i)[1] + "）");
            albums.add(al.get(i)[0]); yms.add(Integer.valueOf(0));
        }
        ArrayList<Integer> seen = new ArrayList<Integer>();
        ArrayList<Item> allItems = Store.get().all;
        for (int i = 0; i < allItems.size(); i++) {
            Integer ym = Integer.valueOf(allItems.get(i).ym);
            if (!seen.contains(ym)) {
                seen.add(ym);
                labels.add("時間：" + (ym.intValue() / 100) + " 年 " + (ym.intValue() % 100) + " 月");
                albums.add(null); yms.add(ym);
            }
            if (seen.size() >= 36) break;
        }

        labels.add("＊ 從某一張開始…");
        albums.add(null); yms.add(Integer.valueOf(-1));

        final String[] arr = labels.toArray(new String[0]);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("選擇要整理的來源")
                .setItems(arr, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (yms.get(w).intValue() == -1) {
                            a.startActivity(new android.content.Intent(a, StartPickActivity.class));
                            return;
                        }
                        setSource(albums.get(w), yms.get(w).intValue());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String sourceLabel() {
        if (startLabel != null) return startLabel;
        if (srcYm > 0) return (srcYm / 100) + " 年 " + (srcYm % 100) + " 月";
        if (srcAlbum != null) return srcAlbum;
        return "全部未整理";
    }

    // ---------- 牌堆 ----------

    public void rebuildDeck() {
        startLabel = null;
        deck = Store.get().deck(srcAlbum, srcYm);
        index = 0;
        showCurrent();
    }

    private void skipDead() {
        while (index < deck.size() && !Store.get().byId.containsKey(deck.get(index).id)) index++;
    }

    private void showCurrent() {
        if (srcChip != null) srcChip.setText(sourceLabel());
        skipDead();
        boolean empty = index >= deck.size();
        if (emptyBox != null) emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        front().setVisibility(empty ? View.GONE : View.VISIBLE);
        back().setVisibility(empty ? View.GONE : View.VISIBLE);
        if (actionRow != null) actionRow.setAlpha(empty ? 0.3f : 1f);

        if (empty) {
            if (emptyStats != null) {
                emptyStats.setText("已保留 " + Store.get().count(Db.STATE_KEPT) + " 張　·　"
                        + "待刪 " + Store.get().count(Db.STATE_PENDING) + " 張\n"
                        + "可釋放約 " + Item.formatSize(Store.get().pendingBytes()));
            }
            if (emptyTitle != null) {
                emptyTitle.setText(Store.get().all.isEmpty()
                        ? "找不到照片" : "「" + sourceLabel() + "」整理完成");
            }
            updateProgress();
            return;
        }

        int px = maxPx();
        front().bind(deck.get(index), px);
        back().bind(index + 1 < deck.size() ? deck.get(index + 1) : null, px);
        if (index + 2 < deck.size()) Thumbs.preload(a, deck.get(index + 2), px);
        setDepth();
        updateProgress();
    }

    private int maxPx() {
        int w = stack != null && stack.getWidth() > 0 ? stack.getWidth() : 1080;
        return Math.min(1440, Math.max(720, w * 3 / 2));
    }

    private void updateProgress() {
        if (progressText == null) return;
        int total = deck.size();
        int done = Math.min(index, total);
        progressText.setText(total == 0
                ? "沒有待整理的項目"
                : ("已處理 " + done + " / " + total + "　·　剩下 " + (total - done) + " 張"));
        bar.setProgress(total == 0 ? 0 : (int) (1000L * done / total));
        undoBtn.setTextColor(Store.get().canUndo() ? T.PRIMARY : T.FAINT);
    }

    // ---------- 手勢 ----------

    private boolean onStackTouch(MotionEvent e) {
        if (busy || index >= deck.size()) return false;
        Card c = front();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getRawX(); y0 = e.getRawY();
                downTime = SystemClock.uptimeMillis();
                dragging = true;
                c.animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) return false;
                float dx = e.getRawX() - x0;
                float dy = e.getRawY() - y0;
                c.setTranslationX(dx);
                c.setTranslationY(dy);
                c.setRotation(Math.max(-14f, Math.min(14f, dx / 26f)));
                int dir = dirOf(dx, dy);
                float amt = amount(dx, dy, dir);
                int act = Prefs.action(a, dir);
                if (amt > 0.06f && act != Prefs.ACT_NONE) c.showHint(act, amt);
                else c.clearHint();
                float t = Math.min(1f, Math.max(Math.abs(dx) / thX(), Math.abs(dy) / thY()));
                back().setScaleX(0.94f + 0.06f * t);
                back().setScaleY(0.94f + 0.06f * t);
                back().setAlpha(0.9f + 0.1f * t);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (!dragging) return false;
                dragging = false;
                float dx = e.getRawX() - x0;
                float dy = e.getRawY() - y0;
                long dt = SystemClock.uptimeMillis() - downTime;
                if (Math.abs(dx) < Ui.dp(a, 8) && Math.abs(dy) < Ui.dp(a, 8) && dt < 300) {
                    springBack();
                    openViewer();
                    return true;
                }
                int dir = dirOf(dx, dy);
                boolean pass = (dir == Prefs.LEFT || dir == Prefs.RIGHT)
                        ? Math.abs(dx) > thX() : Math.abs(dy) > thY();
                if (pass && Prefs.action(a, dir) != Prefs.ACT_NONE) commit(dir);
                else springBack();
                return true;
            }
        }
        return false;
    }

    private float thX() { return Math.max(Ui.dp(a, 70), stack.getWidth() * 0.26f); }
    private float thY() { return Math.max(Ui.dp(a, 90), stack.getHeight() * 0.20f); }

    private int dirOf(float dx, float dy) {
        if (Math.abs(dx) >= Math.abs(dy)) return dx < 0 ? Prefs.LEFT : Prefs.RIGHT;
        return dy < 0 ? Prefs.UP : Prefs.DOWN;
    }

    private float amount(float dx, float dy, int dir) {
        if (dir == Prefs.LEFT || dir == Prefs.RIGHT) return Math.abs(dx) / thX();
        return Math.abs(dy) / thY();
    }

    private void springBack() {
        Card c = front();
        c.animate().translationX(0).translationY(0).rotation(0)
                .setDuration(220).start();
        c.clearHint();
        back().animate().scaleX(0.94f).scaleY(0.94f).alpha(0.9f).setDuration(220).start();
    }

    private void openViewer() {
        if (index >= deck.size()) return;
        Intent i = new Intent(a, ViewerActivity.class);
        i.putExtra("id", deck.get(index).id);
        a.startActivity(i);
    }

    private void flingOut(int dir) {
        if (busy || index >= deck.size()) return;
        commit(dir);
    }

    private void commit(final int dir) {
        final int act = Prefs.action(a, dir);
        if (act == Prefs.ACT_NONE) { springBack(); return; }
        if (act == Prefs.ACT_MOVE) { springBack(); askAlbumThenMove(); return; }

        busy = true;
        haptic();
        final Card c = front();
        c.showHint(act, 1f);
        float w = stack.getWidth(), h = stack.getHeight();
        float tx = 0, ty = 0, rot = 0;
        if (dir == Prefs.LEFT)  { tx = -w * 1.4f; rot = -18f; }
        if (dir == Prefs.RIGHT) { tx =  w * 1.4f; rot =  18f; }
        if (dir == Prefs.UP)    { ty = -h * 1.3f; }
        if (dir == Prefs.DOWN)  { ty =  h * 1.3f; }

        c.animate().translationX(tx).translationY(ty).rotation(rot).alpha(0.35f)
                .setDuration(240)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        c.animate().setListener(null);
                        applyAction(act);
                        advance();
                        busy = false;
                    }
                }).start();
        back().animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240).start();
    }

    private void applyAction(int act) {
        if (index >= deck.size()) return;
        Item it = deck.get(index);
        if (act == Prefs.ACT_TRASH) {
            Store.get().setState(a, it, Db.STATE_PENDING, true);
        } else if (act == Prefs.ACT_KEEP) {
            Store.get().setState(a, it, Db.STATE_KEPT, true);
        } else {
            Store.get().setState(a, it, Db.STATE_NONE, true);
        }
        Store.get().notifyChanged();
    }

    private void advance() {
        index++;
        f = 1 - f;
        showCurrent();
    }

    private void doUndo() {
        if (busy) return;
        Item it = Store.get().undo(a);
        if (it == null) { Ui.toast(a, "沒有可以還原的步驟"); return; }
        if (index > 0) index--;
        f = 1 - f;
        // 確保還原後顯示的就是那一張
        if (index < deck.size() && deck.get(index).id != it.id) {
            for (int i = 0; i < deck.size(); i++) {
                if (deck.get(i).id == it.id) { index = i; break; }
            }
        }
        showCurrent();
        Store.get().notifyChanged();
        Ui.toast(a, "已還原上一步");
    }

    private void askAlbumThenMove() {
        if (index >= deck.size()) return;
        final Item it = deck.get(index);
        ArrayList<String[]> al = Store.get().albums();
        final ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < al.size(); i++) {
            if (!al.get(i)[0].equals(it.bucket)) names.add(al.get(i)[0]);
        }
        names.add("＋ 建立新相簿…");
        final String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("移動到哪個相簿？")
                .setItems(arr, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (w == arr.length - 1) askNewAlbum(it);
                        else doMove(it, arr[w]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askNewAlbum(final Item it) {
        final android.widget.EditText ed = new android.widget.EditText(a);
        ed.setHint("新相簿名稱");
        ed.setTextColor(T.TEXT);
        ed.setPadding(Ui.dp(a, 24), Ui.dp(a, 16), Ui.dp(a, 24), Ui.dp(a, 8));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("建立新相簿")
                .setView(ed)
                .setPositiveButton("建立並移動", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String s = ed.getText().toString().trim();
                        if (s.length() == 0) { Ui.toast(a, "名稱不能空白"); return; }
                        doMove(it, s);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doMove(Item it, String album) {
        ArrayList<Item> one = new ArrayList<Item>();
        one.add(it);
        Store.get().setState(a, it, Db.STATE_KEPT, true);
        advance();
        a.moveToAlbum(one, album, null);
    }

    private void haptic() {
        if (!Prefs.hapticOn(a)) return;
        try {
            Vibrator v = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(12, 60));
            }
        } catch (Throwable ignored) { }
    }

    // ---------- 生命週期 ----------

    @Override public void onShow() {
        rebuildActionRow();
        if (deck.isEmpty() || index >= deck.size()) rebuildDeck();
        else showCurrent();
    }

    @Override public void onStoreChanged() {
        if (root == null) return;
        if (deck.isEmpty()) rebuildDeck();
        else { skipDead(); updateProgress(); }
    }
}
