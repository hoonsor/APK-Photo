package com.photobox.sorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** 相簿相關的對話框：命名、加入相簿、重新命名／複製／刪除。 */
public final class AlbumDialogs {

    private AlbumDialogs() {}

    public interface NameCallback { void onName(String name); }
    public interface AlbumCallback { void onAlbum(Albums.Album album); }

    private static AlertDialog.Builder b(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
    }

    /** 輸入相簿名稱；空白或與既有相簿同名時不關閉，直接提示。 */
    public static void askName(final Activity a, String title, String initial,
                               final Albums.Album except, final NameCallback cb) {
        final EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(initial == null ? "" : initial);
        et.setSelectAllOnFocus(true);
        et.setTextColor(T.TEXT);
        et.setHintTextColor(T.FAINT);
        et.setHint("相簿名稱");
        FrameLayout box = new FrameLayout(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        box.addView(et);

        final AlertDialog d = b(a).setTitle(title).setView(box)
                .setPositiveButton("確定", null)
                .setNegativeButton("取消", null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface di) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String n = et.getText().toString().trim();
                        if (n.length() == 0) { et.setError("請輸入名稱"); return; }
                        if (n.length() > 60) { et.setError("名稱太長了（最多 60 字）"); return; }
                        if (Albums.get().nameTaken(n, except)) { et.setError("已經有同名的相簿"); return; }
                        d.dismiss();
                        cb.onName(n);
                    }
                });
                et.requestFocus();
            }
        });
        try {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } catch (Throwable ignored) { }
        d.show();
    }

    /** 建立新相簿，可選擇同時放入 items。 */
    public static void create(final Activity a, final List<Item> items, final AlbumCallback cb) {
        askName(a, "建立相簿", Albums.get().uniqueName("新相簿"), null, new NameCallback() {
            @Override public void onName(String name) {
                Albums.Album al = Albums.get().create(a, name);
                if (al == null) { Ui.toast(a, "建立失敗"); return; }
                if (items != null && !items.isEmpty()) {
                    int n = Albums.get().add(a, al, items);
                    Ui.toast(a, "已建立「" + name + "」並加入 " + n + " 個項目");
                } else {
                    Ui.toast(a, "已建立「" + name + "」");
                }
                if (cb != null) cb.onAlbum(al);
            }
        });
    }

    /**
     * 加入相簿。
     * 單一項目：勾選清單，可同時加入／移出多本（含喜好項目）。
     * 多個項目：選一本加入，或建立新相簿。
     */
    public static void addTo(final Activity a, final List<Item> items, final Runnable done) {
        if (items == null || items.isEmpty()) { Ui.toast(a, "還沒選取任何項目"); return; }
        final ArrayList<Albums.Album> albums = Albums.get().ordered();
        if (items.size() == 1) {
            final Item it = items.get(0);
            if (Albums.get().favorites() == null) {
                Albums.get().ensureFavorites(a);
                albums.clear();
                albums.addAll(Albums.get().ordered());
            }
            String[] names = new String[albums.size()];
            final boolean[] checked = new boolean[albums.size()];
            final boolean[] before = new boolean[albums.size()];
            for (int i = 0; i < albums.size(); i++) {
                names[i] = albums.get(i).name;
                checked[i] = before[i] = albums.get(i).items.containsKey(it.id);
            }
            b(a).setTitle("放在哪些相簿")
                    .setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
                        @Override public void onClick(DialogInterface d, int w, boolean on) { checked[w] = on; }
                    })
                    .setPositiveButton("完成", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            int add = 0, rem = 0;
                            for (int i = 0; i < albums.size(); i++) {
                                if (checked[i] == before[i]) continue;
                                if (checked[i]) { Albums.get().add(a, albums.get(i), items); add++; }
                                else { Albums.get().remove(a, albums.get(i), items); rem++; }
                            }
                            if (add + rem > 0) {
                                Ui.toast(a, (add > 0 ? "加入 " + add + " 本" : "")
                                        + (add > 0 && rem > 0 ? "、" : "")
                                        + (rem > 0 ? "移出 " + rem + " 本" : ""));
                            }
                            if (done != null) done.run();
                        }
                    })
                    .setNeutralButton("＋ 建立新相簿", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            create(a, items, new AlbumCallback() {
                                @Override public void onAlbum(Albums.Album al) { if (done != null) done.run(); }
                            });
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        final ArrayList<String> names = new ArrayList<String>();
        names.add("＋ 建立新相簿");
        for (int i = 0; i < albums.size(); i++) {
            int[] pv = Albums.get().counts(albums.get(i));
            names.add(albums.get(i).name + "（" + (pv[0] + pv[1]) + "）");
        }
        b(a).setTitle("把 " + items.size() + " 個項目加入")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            create(a, items, new AlbumCallback() {
                                @Override public void onAlbum(Albums.Album al) { if (done != null) done.run(); }
                            });
                            return;
                        }
                        addAndReport(a, albums.get(w - 1), items);
                        if (done != null) done.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public static void addAndReport(Activity a, Albums.Album al, List<Item> items) {
        int n = Albums.get().add(a, al, items);
        int dup = items.size() - n;
        Ui.toast(a, "已加入「" + al.name + "」" + n + " 個項目"
                + (dup > 0 ? "（" + dup + " 個原本就在裡面）" : ""));
    }

    public static void rename(final Activity a, final Albums.Album al, final Runnable done) {
        if (al.isFav()) { Ui.toast(a, "「喜好項目」是內建相簿，不能改名"); return; }
        askName(a, "重新命名相簿", al.name, al, new NameCallback() {
            @Override public void onName(String name) {
                Albums.get().rename(a, al, name);
                Ui.toast(a, "已改名為「" + name + "」");
                if (done != null) done.run();
            }
        });
    }

    public static void duplicate(final Activity a, final Albums.Album al, final AlbumCallback cb) {
        askName(a, "複製相簿", Albums.get().uniqueName(al.name + " 的副本"), null, new NameCallback() {
            @Override public void onName(String name) {
                Albums.Album copy = Albums.get().duplicate(a, al, name);
                if (copy == null) { Ui.toast(a, "複製失敗"); return; }
                Ui.toast(a, "已複製為「" + name + "」");
                if (cb != null) cb.onAlbum(copy);
            }
        });
    }

    public static void delete(final Activity a, final Albums.Album al, final Runnable done) {
        if (al.isFav()) { Ui.toast(a, "「喜好項目」是內建相簿，不能刪除"); return; }
        int[] pv = Albums.get().counts(al);
        b(a).setTitle("刪除相簿「" + al.name + "」？")
                .setMessage("只會刪除這本相簿（" + Albums.countText(pv) + "的清單）。\n\n"
                        + "裡面的照片和影片本身完全不會被刪除，仍然留在手機上。")
                .setPositiveButton("刪除相簿", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Albums.get().delete(a, al);
                        Ui.toast(a, "已刪除相簿「" + al.name + "」");
                        if (done != null) done.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 先挑一本相簿，再執行動作（相簿頁右上選單的重新命名／複製／刪除用）。 */
    public static void pickAlbum(final Activity a, String title, boolean includeFav, final AlbumCallback cb) {
        final ArrayList<Albums.Album> albums = includeFav ? Albums.get().ordered() : Albums.get().normal();
        if (albums.isEmpty()) { Ui.toast(a, "目前還沒有相簿"); return; }
        String[] names = new String[albums.size()];
        for (int i = 0; i < albums.size(); i++) names[i] = albums.get(i).name;
        b(a).setTitle(title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { cb.onAlbum(albums.get(w)); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 單一相簿的管理選單。 */
    public static void manage(final Activity a, final Albums.Album al, final Runnable changed,
                              final Runnable deleted) {
        String[] ops = al.isFav()
                ? new String[]{ "複製相簿" }
                : new String[]{ "重新命名相簿", "複製相簿", "刪除相簿" };
        b(a).setTitle(al.name)
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (al.isFav()) w += 1;
                        if (w == 0) rename(a, al, changed);
                        else if (w == 1) duplicate(a, al, new AlbumCallback() {
                            @Override public void onAlbum(Albums.Album x) { if (changed != null) changed.run(); }
                        });
                        else delete(a, al, deleted);
                    }
                })
                .show();
    }
}
