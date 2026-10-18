package com.photobox.sorter;

import java.util.ArrayList;

/**
 * 比較 GitHub Release tag（例如 v1.2.0）與 App 的 versionName。
 * 純 Java、不依賴 Android，tests/LogicTest 直接測（移植自鬧鐘 APP 的 VersionCompare.kt）。
 */
public final class UpdateVersion {
    private UpdateVersion() {}

    /** 解析 "v1.2.3"、"1.2"、"1.2.3-lab"；格式不對回傳 null。最後一格是 1＝預先發行版、0＝正式版 */
    static int[] parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        if (s.isEmpty()) return null;
        boolean pre = s.indexOf('-') >= 0;
        String core = s;
        int cut = core.indexOf('-');
        if (cut >= 0) core = core.substring(0, cut);
        cut = core.indexOf('+');
        if (cut >= 0) core = core.substring(0, cut);
        String[] parts = core.split("\\.", -1);
        ArrayList<Integer> nums = new ArrayList<Integer>();
        for (String p : parts) {
            if (p.isEmpty()) return null;
            for (int i = 0; i < p.length(); i++) if (!Character.isDigit(p.charAt(i))) return null;
            try { nums.add(Integer.parseInt(p)); } catch (NumberFormatException e) { return null; }
        }
        int[] out = new int[nums.size() + 1];
        for (int i = 0; i < nums.size(); i++) out[i] = nums.get(i);
        out[nums.size()] = pre ? 1 : 0;
        return out;
    }

    /** 正數代表 a 較新，0 代表相同 */
    static int compare(int[] a, int[] b) {
        int na = a.length - 1, nb = b.length - 1;
        int n = Math.max(na, nb);
        for (int i = 0; i < n; i++) {
            int x = i < na ? a[i] : 0, y = i < nb ? b[i] : 0;
            if (x != y) return x - y;
        }
        // 數字相同時，正式版比預先發行版新（1.0.0 > 1.0.0-beta）
        return b[nb] - a[na];
    }

    /** remote 是否比 local 新；任一方解析失敗一律視為「不需要更新」 */
    public static boolean isNewer(String remote, String local) {
        int[] r = parse(remote), l = parse(local);
        if (r == null || l == null) return false;
        return compare(r, l) > 0;
    }

    /**
     * 從 Release 附件檔名中挑要下載的 APK：優先選本 App 命名的（prefix 開頭），
     * 否則第一個 .apk；都沒有回傳 -1。
     */
    public static int pickApk(String[] names, String prefix) {
        int first = -1;
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n == null || !n.toLowerCase().endsWith(".apk")) continue;
            if (n.startsWith(prefix)) return i;
            if (first < 0) first = i;
        }
        return first;
    }
}
