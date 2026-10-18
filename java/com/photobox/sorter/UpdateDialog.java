package com.photobox.sorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * 新版對話框：確認 → 檢查安裝權限 → 下載 → 交給系統安裝。
 * 所在 Activity 要在 onResume 呼叫 {@link #onResume()}，從「安裝不明應用程式」設定頁回來才會自動開始下載。
 */
public class UpdateDialog {
    private static final int CONFIRM = 0, NEED_PERMISSION = 1, DOWNLOADING = 2, INSTALLING = 3, ERROR = 4;

    /** 目前顯示中的對話框（同時只開一個） */
    static UpdateDialog current;

    private final Activity a;
    private final Updater.Release release;
    private final boolean skippable;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView message;
    private final ProgressBar progress;
    private final AlertDialog dialog;
    private int phase = CONFIRM;
    private volatile boolean cancelled;
    private int lastPercent = -2;

    /** 顯示新版對話框；已有一個在顯示時不重複開 */
    public static void show(Activity a, Updater.Release r, boolean skippable) {
        if (current != null || a.isFinishing() || a.isDestroyed()) return;
        current = new UpdateDialog(a, r, skippable);
        current.open();
    }

    /** Activity.onDestroy 呼叫，不讓關掉的畫面留在記憶體裡 */
    public static void activityDestroyed(Activity a) {
        if (current != null && current.a == a) current.close();
    }

    /** Activity.onResume 呼叫 */
    public static void resume() {
        if (current != null) current.onResume();
    }

    private UpdateDialog(Activity a, Updater.Release r, boolean skippable) {
        this.a = a;
        this.release = r;
        this.skippable = skippable;
        message = Ui.tv(a, "", 14.5f, T.TEXT, false);
        message.setLineSpacing(Ui.dp(a, 4), 1f);
        progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);

        LinearLayout body = Ui.col(a);
        int pad = Ui.dp(a, 22);
        body.setPadding(pad, Ui.dp(a, 8), pad, 0);
        ScrollView sv = new ScrollView(a);
        sv.addView(message);
        body.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(progress, Ui.lpW());

        // 按鈕先放佔位文字讓 AlertDialog 建立按鈕，show() 之後再依階段改文字與行為（避免按了自動關閉）
        dialog = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("有新版本 v" + r.version())
                .setView(body)
                .setPositiveButton(" ", null)
                .setNeutralButton(" ", null)
                .setNegativeButton(" ", null)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface d) { close(); }
        });
    }

    private void open() {
        Updater.installErrorListener = new Updater.ErrorListener() {
            @Override public void onInstallError(String text) {
                if (phase == INSTALLING) showError(text);
            }
        };
        dialog.show();
        render();
    }

    private void onResume() {
        if (phase == NEED_PERMISSION && Updater.canInstall(a)) start();
    }

    private void start() {
        if (!Updater.canInstall(a)) {
            phase = NEED_PERMISSION;
            render();
            return;
        }
        phase = DOWNLOADING;
        lastPercent = -2;
        cancelled = false;
        render();
        final android.content.Context app = a.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File apk = Updater.download(app, release, new Updater.Progress() {
                        @Override public void onProgress(float p) {
                            final int percent = p < 0 ? -1 : (int) (p * 100);
                            if (percent == lastPercent) return;
                            lastPercent = percent;
                            main.post(new Runnable() {
                                @Override public void run() { if (!cancelled) showProgress(percent); }
                            });
                        }
                        @Override public boolean cancelled() { return cancelled; }
                    });
                    if (cancelled) return;
                    main.post(new Runnable() {
                        @Override public void run() { if (!cancelled) { phase = INSTALLING; render(); } }
                    });
                    Updater.install(app, apk);
                } catch (Updater.Cancelled e) {
                    // 使用者取消，對話框已關閉
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (!cancelled) showError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                        }
                    });
                }
            }
        }, "app-update");
        t.setDaemon(true);
        t.start();
    }

    private void showProgress(int percent) {
        if (phase != DOWNLOADING) return;
        progress.setIndeterminate(percent < 0);
        if (percent >= 0) progress.setProgress(percent);
        message.setText(percent >= 0 ? "下載中… " + percent + "%" : "下載中…");
    }

    private void showError(String text) {
        phase = ERROR;
        render();
        message.setText(text + "\n\n也可以改用瀏覽器到 GitHub 下載 APK 手動安裝。");
    }

    private void close() {
        cancelled = true;
        Updater.installErrorListener = null;
        if (dialog.isShowing()) dialog.dismiss();
        if (current == this) current = null;
    }

    private void button(int which, String label, final Runnable onClick) {
        Button b = dialog.getButton(which);
        if (b == null) return;
        if (label == null) {
            b.setVisibility(View.GONE);
            return;
        }
        b.setVisibility(View.VISIBLE);
        b.setText(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
    }

    private final Runnable doClose = new Runnable() { @Override public void run() { close(); } };

    private void render() {
        progress.setVisibility(phase == DOWNLOADING ? View.VISIBLE : View.GONE);
        switch (phase) {
            case CONFIRM:
                message.setText("目前版本 v" + Updater.currentVersion(a) + "\n\n"
                        + (release.notes.isEmpty() ? "（這個版本沒有附更新說明）" : release.notes));
                button(DialogInterface.BUTTON_POSITIVE, "下載並更新", new Runnable() { @Override public void run() { start(); } });
                button(DialogInterface.BUTTON_NEUTRAL, skippable ? "略過此版" : null, new Runnable() {
                    @Override public void run() { Updater.skip(a, release.tag); close(); }
                });
                button(DialogInterface.BUTTON_NEGATIVE, "稍後", doClose);
                break;
            case NEED_PERMISSION:
                message.setText("第一次更新需要允許本 App「安裝不明應用程式」。\n\n"
                        + "按「前往設定」後打開開關，再按返回鍵回到這裡，就會自動開始下載。");
                button(DialogInterface.BUTTON_POSITIVE, "前往設定", new Runnable() {
                    @Override public void run() { Updater.openInstallPermissionSettings(a); }
                });
                button(DialogInterface.BUTTON_NEUTRAL, null, null);
                button(DialogInterface.BUTTON_NEGATIVE, "關閉", doClose);
                break;
            case DOWNLOADING:
                progress.setIndeterminate(true);
                message.setText("下載中…");
                button(DialogInterface.BUTTON_POSITIVE, null, null);
                button(DialogInterface.BUTTON_NEUTRAL, null, null);
                button(DialogInterface.BUTTON_NEGATIVE, "取消", doClose);
                break;
            case INSTALLING:
                message.setText("已交給系統安裝，請在系統畫面按「更新」或「安裝」。\n\n"
                        + "安裝完成後相簿與整理進度都會保留。");
                button(DialogInterface.BUTTON_POSITIVE, null, null);
                button(DialogInterface.BUTTON_NEUTRAL, null, null);
                button(DialogInterface.BUTTON_NEGATIVE, "關閉", doClose);
                break;
            default: // ERROR
                button(DialogInterface.BUTTON_POSITIVE, "用瀏覽器下載", new Runnable() {
                    @Override public void run() {
                        String url = release.pageUrl == null || release.pageUrl.isEmpty() ? Updater.RELEASES_PAGE : release.pageUrl;
                        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
                    }
                });
                button(DialogInterface.BUTTON_NEUTRAL, null, null);
                button(DialogInterface.BUTTON_NEGATIVE, "關閉", doClose);
        }
    }
}
