package com.photobox.sorter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/** 接收 PackageInstaller 的安裝結果（App 內更新） */
public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                    : (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            try {
                c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Updater.reportFailure(c, PackageInstaller.STATUS_FAILURE, e.getMessage());
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            // 成功時系統會結束舊版程序，相簿與整理進度都保留
            Updater.reportFailure(c, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}
