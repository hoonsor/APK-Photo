import tw.hoonsor.backup.AutoBackup;
import java.util.*;

/**
 * 每日自動備份（hoonsor-kit android-auto-backup）的純邏輯測試：檔名格式、只認自己的檔、保留最近 7 份。
 * AutoBackup 引用 Android 類別，所以 tests/run.sh 用 android.jar 當 classpath 編譯；
 * 這裡只呼叫純 Java 的靜態方法，不會碰到 android.jar 的 stub。
 */
public class AutoBackupTest {
    static int fails = 0, checks = 0;
    static void ok(boolean c, String m) { checks++; if (!c) { fails++; System.out.println("FAIL: " + m); } }
    static void eq(Object exp, Object act, String m) { ok(exp.equals(act), m + "：期望 " + exp + "，實際 " + act); }

    public static void main(String[] a) {
        // ---------- fileName ----------
        Calendar c = Calendar.getInstance(); c.clear(); c.set(2026, 10, 10, 9, 5);   // 2026-11-10 09:05
        eq("PhotoBox-20261110-0905.json", AutoBackup.fileName("PhotoBox", "json", c.getTime()), "fileName");
        eq("PhotoBox-LAB-20261110-0905.json", AutoBackup.fileName("PhotoBox-LAB", "json", c.getTime()), "fileName LAB");
        c.set(2026, 0, 2, 23, 59);
        eq("PhotoBox-20260102-2359.json", AutoBackup.fileName("PhotoBox", "json", c.getTime()), "fileName 補零");

        // ---------- isBackupName：只認自己寫的檔 ----------
        ok(AutoBackup.isBackupName("PhotoBox-20261110-0905.json", "PhotoBox", "json"), "自己的檔");
        ok(AutoBackup.isBackupName("PhotoBox-20261110-0905 (1).json", "PhotoBox", "json"), "MediaStore 重名加 (1)");
        ok(AutoBackup.isBackupName("PhotoBox-20261110-0905(2).json", "PhotoBox", "json"), "重名加 (2) 無空白");
        ok(!AutoBackup.isBackupName("PhotoBox-LAB-20261110-0905.json", "PhotoBox", "json"), "正式版不認 LAB 的檔");
        ok(!AutoBackup.isBackupName("PhotoBox-20261110-0905.json", "PhotoBox-LAB", "json"), "LAB 不認正式版的檔");
        ok(!AutoBackup.isBackupName("PhotoBox-20261110-0905.zip", "PhotoBox", "json"), "副檔名不同");
        ok(!AutoBackup.isBackupName("PhotoBox-20261110-0905.json.bak", "PhotoBox", "json"), "多一段副檔名");
        ok(!AutoBackup.isBackupName("照片整理箱備份_20261110_0905.json", "PhotoBox", "json"), "手動匯出的檔名不碰");
        ok(!AutoBackup.isBackupName("PhotoBox-2026111-0905.json", "PhotoBox", "json"), "日期少一碼");
        ok(!AutoBackup.isBackupName("PhotoBox-20261110-0905-手動.json", "PhotoBox", "json"), "倬手動改名的檔");
        ok(!AutoBackup.isBackupName("photobox-20261110-0905.json", "PhotoBox", "json"), "大小寫不同");
        ok(!AutoBackup.isBackupName(null, "PhotoBox", "json"), "null");

        // ---------- toDelete：保留最近 KEEP 份 ----------
        eq(Integer.valueOf(7), Integer.valueOf(AutoBackup.KEEP), "KEEP");
        ArrayList<String> names = new ArrayList<String>();
        for (int d = 1; d <= 9; d++) names.add("PhotoBox-2026110" + d + "-1200.json");
        names.add("notes.txt");
        names.add("照片整理箱備份_20261001_0800.json");
        names.add("PhotoBox-LAB-20261001-0800.json");
        Collections.shuffle(names, new Random(42));   // MediaStore 回傳順序不保證
        List<String> del = AutoBackup.toDelete(names, "PhotoBox", "json", AutoBackup.KEEP);
        eq(Arrays.asList("PhotoBox-20261102-1200.json", "PhotoBox-20261101-1200.json"), del, "刪最舊的 2 份");
        ok(AutoBackup.toDelete(names.subList(0, 0), "PhotoBox", "json", 7).isEmpty(), "空清單");
        ArrayList<String> seven = new ArrayList<String>();
        for (int d = 1; d <= 7; d++) seven.add("PhotoBox-2026110" + d + "-1200.json");
        seven.add("notes.txt");
        ok(AutoBackup.toDelete(seven, "PhotoBox", "json", 7).isEmpty(), "剛好 7 份不刪");
        // 跨月、跨年：檔名排序＝時間排序
        List<String> y = Arrays.asList("PhotoBox-20261231-2359.json", "PhotoBox-20270101-0000.json", "PhotoBox-20261130-1200.json");
        eq(Arrays.asList("PhotoBox-20261130-1200.json"), AutoBackup.toDelete(y, "PhotoBox", "json", 2), "跨年排序");

        System.out.println("AutoBackupTest: " + checks + " 項檢查，" + fails + " 項失敗");
        if (fails > 0) System.exit(1);
    }
}
