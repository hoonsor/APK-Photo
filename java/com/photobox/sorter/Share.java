package com.photobox.sorter;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * 透過系統分享選單送出照片／影片（LINE、Dropbox、Google 雲端硬碟等都會出現在選單裡）。
 *
 * 一定要用 it.uri()（媒體項目 URI），並同時放進 ClipData 與 FLAG_GRANT_READ_URI_PERMISSION：
 * 接收的 App 不一定有相簿讀取權限，沒有這個授權會收到空白或讀取失敗。
 */
public final class Share {

    private Share() {}

    public static void items(Activity a, List<Item> items) {
        if (items == null || items.isEmpty()) { Ui.toast(a, "還沒選取任何項目"); return; }
        try {
            Intent chooser = Intent.createChooser(build(items), "分享 " + items.size() + " 個項目");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(chooser);
        } catch (Throwable t) {
            Ui.toast(a, "無法開啟分享選單：" + t.getClass().getSimpleName());
        }
    }

    /**
     * 直接送到「賈維斯資料包」。本 App 不連網，只把讀取授權交給對方；OCR 與上傳都由資料包負責。
     * 影片不支援，只送照片。LAB 版優先送 LAB 版資料包，兩版資料才不會混在一起。
     */
    public static void toDataPack(Activity a, List<Item> items) {
        ArrayList<Item> photos = new ArrayList<Item>();
        if (items != null) for (int k = 0; k < items.size(); k++) if (!items.get(k).video) photos.add(items.get(k));
        if (photos.isEmpty()) { Ui.toast(a, "資料包只收照片"); return; }
        String target = dataPackPackage(a);
        if (target == null) { Ui.toast(a, "還沒安裝賈維斯資料包"); return; }
        try {
            Intent i = build(photos);
            i.setPackage(target);
            a.startActivity(i);
        } catch (Throwable t) {
            Ui.toast(a, "無法送到資料包：" + t.getClass().getSimpleName());
        }
    }

    private static String dataPackPackage(Activity a) {
        String[] order = a.getPackageName().endsWith(".lab")
                ? new String[] { "tw.hoonsor.datapack.lab", "tw.hoonsor.datapack" }
                : new String[] { "tw.hoonsor.datapack", "tw.hoonsor.datapack.lab" };
        for (int k = 0; k < order.length; k++) {
            if (a.getPackageManager().getLaunchIntentForPackage(order[k]) != null) return order[k];
        }
        return null;
    }

    private static Intent build(List<Item> items) {
        Intent i;
        if (items.size() == 1) {
            Item it = items.get(0);
            i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeOf(it));
            i.putExtra(Intent.EXTRA_STREAM, it.uri());
            i.setClipData(ClipData.newRawUri("", it.uri()));
        } else {
            ArrayList<Uri> uris = new ArrayList<Uri>(items.size());
            boolean anyVideo = false, anyImage = false;
            for (int k = 0; k < items.size(); k++) {
                uris.add(items.get(k).uri());
                if (items.get(k).video) anyVideo = true; else anyImage = true;
            }
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            // 混合時用 */*：image/* 會讓部分 App 把影片當成圖片處理
            i.setType(anyVideo && anyImage ? "*/*" : (anyVideo ? "video/*" : "image/*"));
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            ClipData clip = ClipData.newRawUri("", uris.get(0));
            for (int k = 1; k < uris.size(); k++) clip.addItem(new ClipData.Item(uris.get(k)));
            i.setClipData(clip);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private static String mimeOf(Item it) {
        if (it.mime != null && it.mime.length() > 0) return it.mime;
        return it.video ? "video/*" : "image/*";
    }
}
