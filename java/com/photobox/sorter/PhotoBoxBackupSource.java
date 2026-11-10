package com.photobox.sorter;

import android.app.Application;
import android.content.Context;

import java.io.OutputStream;

import tw.hoonsor.backup.AutoBackup;

/**
 * 每日自動備份的內容來源（hoonsor-kit android-auto-backup）：AndroidManifest 的
 * meta-data tw.hoonsor.backup.SOURCE 指到這個類別，備份檔寫到 Documents/HoonsorBackup/PhotoBox/。
 *
 * 內容和「匯出備份檔」相同（Backup.writeTo：相簿與整理標記清單，不含照片本身），還原用既有的「從備份檔還原」。
 * 名稱用 PhotoBox：和套件 com.photobox.sorter、備份格式 photobox-backup、資料庫 photobox.db 一致。
 */
public class PhotoBoxBackupSource implements AutoBackup.Source {

    public PhotoBoxBackupSource() {}

    /**
     * LAB 版另放一個資料夾，不跟正式版混在一起（兩邊互相刪不到對方的檔）。
     * 本專案沒有 BuildConfig，LAB 版是 aapt2 --rename-manifest-package 改套件名，
     * 所以看行程名稱（預設＝套件名）：com.photobox.sorter.lab。
     */
    @Override public String appName() {
        return isLab(Application.getProcessName()) ? "PhotoBox-LAB" : "PhotoBox";
    }

    static boolean isLab(String process) {
        return process != null && (process.endsWith(".lab") || process.contains(".lab:"));
    }

    @Override public String extension() { return "json"; }

    @Override public String mimeType() { return "application/json"; }

    @Override public void write(Context context, OutputStream out) throws Exception {
        Backup.writeTo(context, out);
    }
}
