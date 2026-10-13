package com.photobox.sorter;

import java.util.HashMap;
import java.util.HashSet;

/**
 * 備份還原時，把「備份裡記錄的項目」對應到「這支手機目前的項目」。
 *
 * MediaStore 的 _ID 只在同一支手機、同一份媒體庫裡穩定；換手機或媒體庫重建後會全部改變，
 * 所以 id 只當作第一順位，再依序用「檔名＋大小」「檔名＋拍攝時間（±2 秒）」比對。
 * 每個目前項目最多只會被對應一次，避免兩筆備份搶同一張。
 *
 * 刻意不引用 Android 類別，可在桌機 JVM 單獨測試。
 */
public final class BackupMatch {

    private BackupMatch() {}

    public static final long TIME_TOLERANCE_MS = 2000;

    /** @return 與備份陣列等長，每格是對應到的目前 id，對不上為 -1。 */
    public static long[] match(long[] bId, String[] bName, long[] bSize, long[] bTime,
                               long[] cId, String[] cName, long[] cSize, long[] cTime) {
        int nb = bId.length, nc = cId.length;
        long[] out = new long[nb];
        for (int i = 0; i < nb; i++) out[i] = -1;

        HashMap<Long, Integer> byId = new HashMap<Long, Integer>();
        HashMap<String, Integer> byNameSize = new HashMap<String, Integer>();
        HashSet<String> dupNameSize = new HashSet<String>();
        HashMap<String, java.util.ArrayList<Integer>> byName = new HashMap<String, java.util.ArrayList<Integer>>();
        for (int j = 0; j < nc; j++) {
            byId.put(cId[j], j);
            String k = key(cName[j], cSize[j]);
            if (byNameSize.containsKey(k)) dupNameSize.add(k); else byNameSize.put(k, j);
            String n = norm(cName[j]);
            java.util.ArrayList<Integer> l = byName.get(n);
            if (l == null) { l = new java.util.ArrayList<Integer>(); byName.put(n, l); }
            l.add(j);
        }
        boolean[] used = new boolean[nc];

        // 1) 同 id，且檔名與大小也一致（同一支手機、媒體庫沒重建）
        for (int i = 0; i < nb; i++) {
            Integer j = byId.get(bId[i]);
            if (j == null || used[j]) continue;
            if (norm(cName[j]).equals(norm(bName[i])) && cSize[j] == bSize[i]) {
                out[i] = cId[j];
                used[j] = true;
            }
        }
        // 2) 檔名＋大小唯一
        for (int i = 0; i < nb; i++) {
            if (out[i] >= 0) continue;
            String k = key(bName[i], bSize[i]);
            if (dupNameSize.contains(k)) continue;
            Integer j = byNameSize.get(k);
            if (j == null || used[j]) continue;
            out[i] = cId[j];
            used[j] = true;
        }
        // 3) 檔名＋拍攝時間（容許 2 秒誤差；檔案被重新壓縮過大小會變）
        for (int i = 0; i < nb; i++) {
            if (out[i] >= 0) continue;
            java.util.ArrayList<Integer> l = byName.get(norm(bName[i]));
            if (l == null) continue;
            int best = -1;
            long bestD = Long.MAX_VALUE;
            for (int k = 0; k < l.size(); k++) {
                int j = l.get(k);
                if (used[j]) continue;
                long d = Math.abs(cTime[j] - bTime[i]);
                if (d <= TIME_TOLERANCE_MS && d < bestD) { bestD = d; best = j; }
            }
            if (best >= 0) { out[i] = cId[best]; used[best] = true; }
        }
        return out;
    }

    private static String norm(String s) { return s == null ? "" : s.trim(); }
    private static String key(String name, long size) { return norm(name) + "\u0000" + size; }
}
