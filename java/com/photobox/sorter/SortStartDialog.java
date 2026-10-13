package com.photobox.sorter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/** 「從這張開始整理」：選方向（往舊／往新）與範圍（只整理未整理的／全部）。檢視器與挑選起點畫面共用。 */
public final class SortStartDialog {

    private SortStartDialog() {}

    /** 一次選完方向與範圍；選定後送出請求並執行 then（通常是 finish()，回到主畫面由整理頁接手）。 */
    public static void show(final Activity a, final Item it, final Runnable then) {
        boolean sorted = Store.get().stateOf(it) != Db.STATE_NONE;
        String[] ops = {
                "往較舊的照片整理（只整理還沒整理過的）",
                "往較舊的照片整理（全部重新看一遍）",
                "往較新的照片整理（只整理還沒整理過的）",
                "往較新的照片整理（全部重新看一遍）"
        };
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("從 " + it.dayText() + " 這張開始"
                        + (sorted ? "\n（這張已經整理過，仍會從它開始）" : ""))
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        boolean newer = w >= 2;
                        int include = (w % 2 == 1) ? 1 : 0;
                        Store.get().requestSortStart(it.id, newer, include);
                        if (then != null) then.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
