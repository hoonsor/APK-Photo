import com.photobox.sorter.*;
import java.util.*;

public class LogicTest {
    static int fails = 0, checks = 0;
    static void ok(boolean c, String m) { checks++; if (!c) { fails++; System.out.println("FAIL: " + m); } }

    static long t(int y, int mo, int d, int h) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, mo - 1, d, h, 0, 0); return c.getTimeInMillis();
    }

    public static void main(String[] a) {
        // ---------- Flow ----------
        // 3 天：10/9 x2, 10/7 x3, 9/28 x5（新到舊）
        long[] times = { t(2026,10,9,12), t(2026,10,9,11), t(2026,10,7,10), t(2026,10,7,9), t(2026,10,7,8),
                         t(2026,9,28,20), t(2026,9,28,19), t(2026,9,28,18), t(2026,9,28,17), t(2026,9,28,16) };
        // 無月份標題（相簿內頁）：日期格連續排
        ArrayList<Flow.Row> rows = Flow.build(times, 4, false, true);
        ArrayList<Integer> flat = new ArrayList<Integer>();
        ArrayList<Integer> days = new ArrayList<Integer>();
        for (Flow.Row r : rows) { ok(r.type == Flow.ROW_CELLS, "only cells"); for (int i = 0; i < 4; i++) { flat.add(r.cells[i]); days.add(r.dayKeys[i]); } }
        // 期望序列：D(1009) 0 1 D(1007) | 2 3 4 D(0928) | 5 6 7 8 | 9 E E E
        int[] exp = { -2,0,1,-2, 2,3,4,-2, 5,6,7,8, 9,-1,-1,-1 };
        ok(flat.size() == exp.length, "flat size " + flat.size());
        for (int i = 0; i < exp.length && i < flat.size(); i++) ok(flat.get(i) == exp[i], "cell " + i + " = " + flat.get(i) + " expected " + exp[i]);
        ok(days.get(0) == 20261009 && days.get(3) == 20261007 && days.get(7) == 20260928, "dayKeys " + days);
        // 每個項目剛好出現一次
        int[] seen = new int[times.length];
        for (int v : flat) if (v >= 0) seen[v]++;
        for (int i = 0; i < seen.length; i++) ok(seen[i] == 1, "item " + i + " seen " + seen[i]);

        // 有月份標題（照片頁）
        rows = Flow.build(times, 4, true, true);
        ok(rows.get(0).type == Flow.ROW_HEADER && rows.get(0).ym == 202610 && rows.get(0).count == 5, "header oct count " + rows.get(0).count);
        int hdrSep = -1;
        for (int i = 1; i < rows.size(); i++) if (rows.get(i).type == Flow.ROW_HEADER) hdrSep = i;
        ok(hdrSep > 0 && rows.get(hdrSep).ym == 202609 && rows.get(hdrSep).count == 5, "header sep");
        ok(rows.get(hdrSep + 1).cells[0] == Flow.DATE, "new month starts with date cell on fresh row");
        // 標題後第一列：D 0 1 D
        ok(rows.get(1).cells[0] == -2 && rows.get(1).cells[1] == 0 && rows.get(1).cells[3] == -2, "oct first row");

        // 剛好填滿時，日期格不可留在上一列最後
        long[] t2 = { t(2026,1,2,1), t(2026,1,2,0), t(2026,1,2,0), t(2026,1,1,5) };
        rows = Flow.build(t2, 4, false, true);
        // D 0 1 2 | D 3 E E
        ok(rows.size() == 2 && rows.get(0).cells[3] == 2 && rows.get(1).cells[0] == -2 && rows.get(1).cells[1] == 3, "boundary wrap");

        // 大量隨機資料：每個項目恰好一次、日期格數 = 天數、每列長度固定
        Random rnd = new Random(42);
        int N = 5000;
        long[] big = new long[N];
        long cur = t(2026,10,9,23);
        for (int i = 0; i < N; i++) { cur -= (long) (rnd.nextDouble() * 9 * 3600 * 1000); big[i] = cur; }
        for (int cols = 1; cols <= 6; cols++) for (int mh = 0; mh < 2; mh++) {
            rows = Flow.build(big, cols, mh == 1, true);
            int[] s = new int[N]; int dates = 0;
            HashSet<Integer> distinctDays = new HashSet<Integer>();
            Calendar c = Calendar.getInstance();
            for (long x : big) { c.setTimeInMillis(x); distinctDays.add(c.get(Calendar.YEAR)*10000+(c.get(Calendar.MONTH)+1)*100+c.get(Calendar.DAY_OF_MONTH)); }
            int last = -1; boolean order = true;
            for (Flow.Row r : rows) {
                if (r.type != Flow.ROW_CELLS) continue;
                ok(r.cells.length == cols, "row len");
                for (int v : r.cells) { if (v >= 0) { s[v]++; if (v != last + 1) order = false; last = v; } else if (v == Flow.DATE) dates++; }
            }
            boolean once = true; for (int v : s) if (v != 1) once = false;
            ok(once, "big once cols=" + cols + " mh=" + mh);
            ok(order, "big order cols=" + cols);
            ok(dates == distinctDays.size(), "date cells " + dates + " vs days " + distinctDays.size());
        }
        rows = Flow.plain(10, 4);
        ok(rows.size() == 3 && rows.get(2).cells[1] == 9 && rows.get(2).cells[2] == -1, "plain");
        ok(Flow.build(new long[0], 4, true, true).isEmpty(), "empty");
        ok(Flow.weekday(20261009).equals("週五"), "weekday " + Flow.weekday(20261009));
        ok(Flow.weekday(20261007).equals("週三"), "weekday wed");

        // ---------- BackupMatch ----------
        // 同手機：id 一致
        long[] m1 = BackupMatch.match(new long[]{5, 6}, new String[]{"a.jpg","b.jpg"}, new long[]{100,200}, new long[]{1000,2000},
                                      new long[]{6, 5}, new String[]{"b.jpg","a.jpg"}, new long[]{200,100}, new long[]{2000,1000});
        ok(m1[0] == 5 && m1[1] == 6, "same ids");
        // 換手機：id 全變，但檔名＋大小相同；id 碰巧撞到別張的不能誤配
        long[] m2 = BackupMatch.match(new long[]{5, 6, 7}, new String[]{"a.jpg","b.jpg","c.mp4"}, new long[]{100,200,300}, new long[]{1000,2000,3000},
                                      new long[]{5, 900, 901}, new String[]{"zzz.jpg","a.jpg","c.mp4"}, new long[]{999,100,300}, new long[]{0,1000,3000});
        ok(m2[0] == 900, "rehome a -> 900 got " + m2[0]);
        ok(m2[1] == -1, "b missing got " + m2[1]);
        ok(m2[2] == 901, "c -> 901");
        // 大小變了（重新壓縮），但檔名＋時間在 2 秒內
        long[] m3 = BackupMatch.match(new long[]{1}, new String[]{"IMG_1.jpg"}, new long[]{5000}, new long[]{100000},
                                      new long[]{77, 78}, new String[]{"IMG_1.jpg","IMG_1.jpg"}, new long[]{4000, 4100}, new long[]{150000, 101500});
        ok(m3[0] == 78, "name+time picks closest within tolerance got " + m3[0]);
        // 檔名＋大小重複（兩張一樣大小同名在不同資料夾）→ 不靠第 2 步亂配，退到時間比對
        long[] m4 = BackupMatch.match(new long[]{1, 2}, new String[]{"x.jpg","x.jpg"}, new long[]{10,10}, new long[]{1000, 50000},
                                      new long[]{31, 32}, new String[]{"x.jpg","x.jpg"}, new long[]{10,10}, new long[]{50000, 1000});
        ok(m4[0] == 32 && m4[1] == 31, "dup name+size resolved by time: " + m4[0] + "," + m4[1]);
        // 同一張不能被兩筆搶
        long[] m5 = BackupMatch.match(new long[]{1, 2}, new String[]{"y.jpg","y.jpg"}, new long[]{10,10}, new long[]{1000, 1000},
                                      new long[]{40}, new String[]{"y.jpg"}, new long[]{10}, new long[]{1000});
        ok((m5[0] == 40) != (m5[1] == 40), "no double assignment");

        // ---------- Geo ----------
        double[] g = Geo.parseIso6709("+25.0330+121.5654/");
        ok(g != null && Math.abs(g[0]-25.033) < 1e-9 && Math.abs(g[1]-121.5654) < 1e-9 && Double.isNaN(g[2]), "iso basic");
        g = Geo.parseIso6709("+24.9936+121.3010+105.250/");
        ok(g != null && Math.abs(g[2]-105.25) < 1e-9, "iso alt");
        g = Geo.parseIso6709("-33.8688+151.2093/");
        ok(g != null && g[0] < 0, "iso south");
        ok(Geo.parseIso6709("+00.0000+000.0000/") == null, "zero rejected");
        ok(Geo.parseIso6709(null) == null && Geo.parseIso6709("garbage") == null, "garbage");

        // ---------- UpdateVersion（App 內更新） ----------
        ok(UpdateVersion.isNewer("v1.2.1", "1.2.0"), "patch newer");
        ok(UpdateVersion.isNewer("v1.10.0", "1.9.9"), "numeric not string compare");
        ok(UpdateVersion.isNewer("v2.0.0", "1.99.99"), "major newer");
        ok(!UpdateVersion.isNewer("v1.2.0", "1.2.0"), "same version");
        ok(!UpdateVersion.isNewer("v1.1.8", "1.2.0"), "older");
        ok(!UpdateVersion.isNewer("v1.2", "1.2.0") && UpdateVersion.isNewer("v1.2.1", "1.2") && UpdateVersion.isNewer("v1.3", "1.2.9"), "pad zeros");
        ok(UpdateVersion.isNewer(" V1.3.0 ", "1.2.0"), "upper V and spaces");
        ok(UpdateVersion.isNewer("v1.2.0", "1.2.0-lab") && !UpdateVersion.isNewer("v1.2.0-beta", "1.2.0")
                && !UpdateVersion.isNewer("v1.2.0-beta", "1.2.0-lab"), "release beats prerelease");
        ok(UpdateVersion.isNewer("v1.3.0-beta", "1.2.0"), "numbers first");
        ok(!UpdateVersion.isNewer("latest", "1.2.0") && !UpdateVersion.isNewer("", "1.2.0") && !UpdateVersion.isNewer(null, "1.2.0")
                && !UpdateVersion.isNewer("v1.0.0", null) && !UpdateVersion.isNewer("v1..0", "0.1.0") && !UpdateVersion.isNewer("v1.a.0", "0.1.0"), "unparsable never updates");
        ok(UpdateVersion.pickApk(new String[]{"notes.txt", "Other-v1.apk", "PhotoSortBox-v1.2.0.apk"}, "PhotoSortBox-") == 2, "pick own apk");
        ok(UpdateVersion.pickApk(new String[]{"notes.txt", "x.APK"}, "PhotoSortBox-") == 1, "fallback first apk");
        ok(UpdateVersion.pickApk(new String[]{"notes.txt", null}, "PhotoSortBox-") == -1, "no apk");

        System.out.println(checks + " checks, " + fails + " failures");
        if (fails > 0) System.exit(1);
    }
}
