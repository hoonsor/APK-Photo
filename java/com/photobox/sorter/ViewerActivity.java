package com.photobox.sorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.VideoView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 單張檢視：可縮放的照片、可直接播放的影片。
 * 下方：資訊／喜好／加入相簿／分享／刪除；右上 ⋮：保留、待刪、清除標記、從此整理。
 */
public class ViewerActivity extends Activity implements Store.Listener {

    private static final int REQ_TRASH = 5001;
    private static final int REQ_DELETE = 5002;

    /** 由呼叫端指定左右滑動的範圍（例如某本相簿），避免把大量 id 塞進 Intent。 */
    private static ArrayList<Long> pendingContext;

    public static void open(Context c, Item start, List<Item> context) {
        ArrayList<Long> ids = new ArrayList<Long>(context == null ? 0 : context.size());
        if (context != null) for (int i = 0; i < context.size(); i++) ids.add(context.get(i).id);
        pendingContext = ids;
        Intent in = new Intent(c, ViewerActivity.class);
        in.putExtra("id", start.id);
        c.startActivity(in);
    }

    private ArrayList<Item> list;
    private int pos = 0;
    private ZoomImageView img;
    private VideoView video;
    private MediaController controller;
    private ImageView playBtn, starIcon, closeVideo;
    private TextView starLabel;
    private LinearLayout topBar, bottomBar;
    private TextView title, meta, stateTag;
    private boolean chromeVisible = true;
    private boolean playing = false;
    private List<Item> awaiting;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long id = getIntent().getLongExtra("id", -1);
        list = new ArrayList<Item>();
        ArrayList<Long> ctx = pendingContext;
        pendingContext = null;
        if (ctx != null && !ctx.isEmpty()) {
            for (int i = 0; i < ctx.size(); i++) {
                Item it = Store.get().byId.get(ctx.get(i));
                if (it != null) list.add(it);
            }
        }
        if (list.isEmpty()) list.addAll(Store.get().all);
        pos = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) { pos = i; break; }
        }
        if (list.isEmpty()) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        img = new ZoomImageView(this);
        root.addView(img, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        img.setSwipeListener(new ZoomImageView.SwipeListener() {
            @Override public void onSwipe(int dir) { step(dir); }
        });
        img.setTapListener(new ZoomImageView.TapListener() {
            @Override public void onSingleTap() { toggleChrome(); }
        });

        video = new VideoView(this);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.gravity = Gravity.CENTER;
        video.setVisibility(View.GONE);
        root.addView(video, vlp);

        playBtn = new ImageView(this);
        playBtn.setImageDrawable(Icons.get(Icons.PLAY, 0xFFFFFFFF));
        int pp = Ui.dp(this, 20);
        playBtn.setPadding(pp + Ui.dp(this, 3), pp, pp - Ui.dp(this, 3), pp);
        playBtn.setBackground(Ui.tappable(0x99000000, Ui.dp(this, 36), 0xCCFFFFFF, Ui.dp(this, 2)));
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playVideo(); }
        });
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72));
        plp.gravity = Gravity.CENTER;
        root.addView(playBtn, plp);

        topBar = buildTop();
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        root.addView(topBar, tlp);

        bottomBar = buildBottom();
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, blp);

        root.setFitsSystemWindows(true);
        setContentView(root);
        Store.get().addListener(this);
        show();
    }

    @Override protected void onDestroy() {
        Store.get().removeListener(this);
        stopVideo();
        super.onDestroy();
    }

    @Override protected void onPause() {
        super.onPause();
        if (playing && video.isPlaying()) video.pause();
    }

    @Override public void onStoreChanged() {
        if (list == null || list.isEmpty()) return;
        updateChrome();
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
        l.setBackgroundColor(0xB3000000);
        l.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));

        l.addView(barIcon(Icons.BACK, new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        }), new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        LinearLayout texts = Ui.col(this);
        title = Ui.tv(this, "", 17f, 0xFFFFFFFF, true);
        title.setMaxLines(1);
        meta = Ui.tv(this, "", 12f, 0xCCD8DEE9, false);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        texts.addView(title, Ui.lpW());
        texts.addView(Ui.space(this, 3));
        texts.addView(meta, Ui.lpW());
        LinearLayout.LayoutParams tl = Ui.lpWeight();
        tl.leftMargin = Ui.dp(this, 6);
        l.addView(texts, tl);

        stateTag = Ui.tv(this, "", 12f, 0xFFFFFFFF, true);
        stateTag.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        l.addView(stateTag);

        closeVideo = barIcon(Icons.CLOSE, new View.OnClickListener() {
            @Override public void onClick(View v) { stopVideo(); showPoster(); }
        });
        closeVideo.setVisibility(View.GONE);
        l.addView(closeVideo, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));

        final ImageView more = barIcon(Icons.MORE, null);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(more); }
        });
        l.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return l;
    }

    private LinearLayout buildBottom() {
        LinearLayout l = Ui.rowH(this);
        l.setBackgroundColor(0xB3000000);
        l.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 12));
        l.addView(action(Icons.INFO, "資訊", 0xFFFFFFFF, new View.OnClickListener() {
            @Override public void onClick(View v) { InfoActivity.open(ViewerActivity.this, cur().id); }
        }));
        LinearLayout fav = action(Icons.STAR_O, "喜好", 0xFFFFFFFF, new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFav(); }
        });
        starIcon = (ImageView) fav.getChildAt(0);
        starLabel = (TextView) fav.getChildAt(2);
        l.addView(fav);
        l.addView(action(Icons.ALBUM_ADD, "加入相簿", 0xFFFFFFFF, new View.OnClickListener() {
            @Override public void onClick(View v) {
                ArrayList<Item> one = new ArrayList<Item>();
                one.add(cur());
                AlbumDialogs.addTo(ViewerActivity.this, one, null);
            }
        }));
        l.addView(action(Icons.SHARE, "分享", 0xFFFFFFFF, new View.OnClickListener() {
            @Override public void onClick(View v) {
                ArrayList<Item> one = new ArrayList<Item>();
                one.add(cur());
                Share.items(ViewerActivity.this, one);
            }
        }));
        l.addView(action(Icons.TRASH, "刪除", T.TRASH, new View.OnClickListener() {
            @Override public void onClick(View v) { askDelete(); }
        }));
        return l;
    }

    private LinearLayout action(int icon, String label, int color, View.OnClickListener cl) {
        LinearLayout cell = Ui.col(this);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        cell.setBackground(Ui.ripple(new android.graphics.drawable.ColorDrawable(0), 0x33FFFFFF));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Icons.get(icon, color));
        cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        cell.addView(Ui.space(this, 5));
        TextView lab = Ui.tv(this, label, 10.5f, 0xCCE3E8F0, false);
        lab.setMaxLines(1);
        cell.addView(lab);
        cell.setOnClickListener(cl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        cell.setLayoutParams(lp);
        return cell;
    }

    // ---------- 顯示 ----------

    private Item cur() { return list.get(pos); }

    private void step(int d) {
        int n = pos + d;
        if (n < 0) { Ui.toast(this, "已經是第一張"); return; }
        if (n >= list.size()) { Ui.toast(this, "已經是最後一張"); return; }
        pos = n;
        show();
    }

    private void show() {
        stopVideo();
        Item it = cur();
        Thumbs.full(img, it, 2048, null);
        showPoster();
        updateChrome();
    }

    private void updateChrome() {
        if (pos >= list.size()) return;
        Item it = cur();
        NumberFormat nf = NumberFormat.getIntegerInstance();
        title.setText(nf.format(pos + 1) + " / " + nf.format(list.size()));
        meta.setText(it.timeText() + "　·　" + it.name);
        int st = Store.get().stateOf(it);
        if (st == Db.STATE_PENDING) {
            stateTag.setText("待刪");
            stateTag.setBackground(Ui.round(T.TRASH, Ui.dp(this, 12)));
            stateTag.setVisibility(View.VISIBLE);
        } else if (st == Db.STATE_KEPT) {
            stateTag.setText("已保留");
            stateTag.setBackground(Ui.round(T.KEEP, Ui.dp(this, 12)));
            stateTag.setVisibility(View.VISIBLE);
        } else {
            stateTag.setVisibility(View.GONE);
        }
        boolean fav = Albums.get().isFav(it.id);
        starIcon.setImageDrawable(Icons.get(fav ? Icons.STAR : Icons.STAR_O, fav ? 0xFFF2C14E : 0xFFFFFFFF));
        starLabel.setText(fav ? "已加入喜好" : "喜好");
    }

    private void showPoster() {
        playing = false;
        video.setVisibility(View.GONE);
        img.setVisibility(View.VISIBLE);
        closeVideo.setVisibility(View.GONE);
        playBtn.setVisibility(cur().video ? View.VISIBLE : View.GONE);
    }

    // ---------- 影片 ----------

    private void playVideo() {
        final Item it = cur();
        if (!it.video) return;
        try {
            playing = true;
            playBtn.setVisibility(View.GONE);
            closeVideo.setVisibility(View.VISIBLE);
            video.setVisibility(View.VISIBLE);
            if (controller == null) {
                controller = new MediaController(this);
                controller.setPrevNextListeners(new View.OnClickListener() {
                    @Override public void onClick(View v) { step(1); }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) { step(-1); }
                });
            }
            controller.setAnchorView(video);
            video.setMediaController(controller);
            video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    img.setVisibility(View.INVISIBLE);
                    video.start();
                    controller.show(2500);
                }
            });
            video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { controller.show(0); }
            });
            video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    Ui.toast(ViewerActivity.this, "這部影片無法在 App 內播放，改用其他播放器開啟");
                    stopVideo();
                    showPoster();
                    openExternally(it);
                    return true;
                }
            });
            video.setVideoURI(it.uri());
        } catch (Throwable t) {
            stopVideo();
            showPoster();
            openExternally(it);
        }
    }

    private void stopVideo() {
        try {
            if (controller != null) controller.hide();
            if (video != null) {
                video.stopPlayback();
                video.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) { }
        playing = false;
        if (img != null) img.setVisibility(View.VISIBLE);
    }

    private void openExternally(Item it) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(it.uri(), it.mime.length() > 0 ? it.mime : "video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "用其他 App 播放"));
        } catch (Throwable ignored) { }
    }

    // ---------- 動作 ----------

    private void toggleFav() {
        boolean on = Albums.get().toggleFav(this, cur());
        Ui.toast(this, on ? "已加入喜好項目" : "已從喜好項目移除");
        updateChrome();
    }

    private void mark(int state) {
        Item it = cur();
        Store.get().setState(this, it, state, true);
        Store.get().notifyChanged();
        updateChrome();
        Ui.toast(this, state == Db.STATE_PENDING ? "已加入待刪清單"
                : state == Db.STATE_KEPT ? "已標記為保留" : "已清除標記");
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "標記為保留");
        pm.getMenu().add(0, 2, 1, "加入待刪清單");
        pm.getMenu().add(0, 3, 2, "清除標記");
        pm.getMenu().add(0, 4, 3, "從這張開始整理（往舊／往新）…");
        if (cur().video) pm.getMenu().add(0, 5, 4, "用其他 App 播放");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem m) {
                switch (m.getItemId()) {
                    case 1: mark(Db.STATE_KEPT); break;
                    case 2: mark(Db.STATE_PENDING); break;
                    case 3: mark(Db.STATE_NONE); break;
                    case 4: sortFromHere(); break;
                    case 5: openExternally(cur()); break;
                }
                return true;
            }
        });
        pm.show();
    }

    /** 回到整理頁，從目前這一張開始往舊（或往新）的照片整理。 */
    private void sortFromHere() {
        SortStartDialog.show(this, cur(), new Runnable() {
            @Override public void run() { finish(); }
        });
    }

    private void askDelete() {
        final boolean perm = Prefs.permanentDelete(this);
        String[] ops = {
                "加入待刪清單（稍後在「待刪」分頁一起處理）",
                perm ? "立即永久刪除" : "立即移到系統垃圾桶（30 天內可救回）"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("刪除這個" + (cur().video ? "影片" : "照片"))
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) { mark(Db.STATE_PENDING); return; }
                        stopVideo();
                        showPoster();
                        awaiting = new ArrayList<Item>();
                        awaiting.add(cur());
                        ArrayList<Uri> uris = new ArrayList<Uri>();
                        uris.add(cur().uri());
                        if (perm) Media.requestDelete(ViewerActivity.this, uris, REQ_DELETE);
                        else Media.requestTrash(ViewerActivity.this, uris, true, REQ_TRASH);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TRASH && req != REQ_DELETE) return;
        if (res == RESULT_OK && awaiting != null) {
            if (req == REQ_TRASH) Db.get(this).addTrashed(awaiting);
            Store.get().forget(this, awaiting);
            Store.get().clearUndo();
            Ui.toast(this, req == REQ_TRASH ? "已移到系統垃圾桶，30 天內都救得回來" : "已永久刪除");
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).id == awaiting.get(0).id) list.remove(i);
            }
            awaiting = null;
            if (list.isEmpty()) { finish(); return; }
            if (pos >= list.size()) pos = list.size() - 1;
            show();
        } else {
            awaiting = null;
            Ui.toast(this, "已取消，沒有任何檔案被刪除");
        }
    }

    private void toggleChrome() {
        chromeVisible = !chromeVisible;
        topBar.animate().alpha(chromeVisible ? 1f : 0f).setDuration(160).start();
        bottomBar.animate().alpha(chromeVisible ? 1f : 0f).setDuration(160).start();
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        setTreeEnabled(topBar, chromeVisible);
        setTreeEnabled(bottomBar, chromeVisible);
    }

    private static void setTreeEnabled(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setTreeEnabled(g.getChildAt(i), on);
        }
    }

    @Override public void onBackPressed() {
        if (playing) { stopVideo(); showPoster(); return; }
        super.onBackPressed();
    }
}
