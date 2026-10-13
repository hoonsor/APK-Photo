package com.photobox.sorter;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * 照片牆的排版運算：把一串依時間排好的項目，切成「月份標題列」與「格子列」，
 * 並在每一天的第一張前面插入一個「日期格」（年/月、日、星期）。
 *
 * 刻意不引用任何 Android 類別，可以在桌機 JVM 上獨立測試
 * （這個專案的 bug 幾乎都出在索引運算，所以排版規則抽出來單獨驗證）。
 */
public final class Flow {

    private Flow() {}

    public static final int ROW_HEADER = 0;
    public static final int ROW_CELLS = 1;

    /** 格子內容：>= 0 為項目索引；EMPTY 為空格；DATE 為日期格（日期見 dayKeys）。 */
    public static final int EMPTY = -1;
    public static final int DATE = -2;

    public static final class Row {
        public int type;
        /** 月份 key（yyyymm），依大小排序時為 0。 */
        public int ym;
        /** 標題列：這個月份的項目數。 */
        public int count;
        public int[] cells;
        /** 與 cells 對應：日期格的 yyyymmdd，其他格為 0。 */
        public int[] dayKeys;
    }

    /**
     * @param times        每個項目的時間（毫秒），順序即顯示順序
     * @param cols         每列格數
     * @param monthHeaders 是否在月份改變時插入整列標題
     * @param dateCells    是否在每一天的第一張前插入日期格
     */
    public static ArrayList<Row> build(long[] times, int cols, boolean monthHeaders, boolean dateCells) {
        ArrayList<Row> rows = new ArrayList<Row>();
        if (cols < 1) cols = 1;
        int n = times == null ? 0 : times.length;
        if (n == 0) return rows;

        int[] ym = new int[n];
        int[] day = new int[n];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < n; i++) {
            cal.setTimeInMillis(times[i]);
            int y = cal.get(Calendar.YEAR), m = cal.get(Calendar.MONTH) + 1, d = cal.get(Calendar.DAY_OF_MONTH);
            ym[i] = y * 100 + m;
            day[i] = ym[i] * 100 + d;
        }

        Row cur = null;
        int filled = 0;
        int lastYm = Integer.MIN_VALUE, lastDay = Integer.MIN_VALUE;
        Row header = null;

        for (int i = 0; i < n; i++) {
            if (monthHeaders && ym[i] != lastYm) {
                header = new Row();
                header.type = ROW_HEADER;
                header.ym = ym[i];
                rows.add(header);
                cur = null;              // 新月份一律換新列
                filled = 0;
            }
            if (header != null && monthHeaders) header.count++;

            if (dateCells && day[i] != lastDay) {
                if (cur == null || filled >= cols) { cur = newCells(cols, ym[i]); rows.add(cur); filled = 0; }
                cur.cells[filled] = DATE;
                cur.dayKeys[filled] = day[i];
                filled++;
            }
            if (cur == null || filled >= cols) { cur = newCells(cols, ym[i]); rows.add(cur); filled = 0; }
            cur.cells[filled] = i;
            filled++;

            lastYm = ym[i];
            lastDay = day[i];
        }
        return rows;
    }

    private static Row newCells(int cols, int ym) {
        Row r = new Row();
        r.type = ROW_CELLS;
        r.ym = ym;
        r.cells = new int[cols];
        r.dayKeys = new int[cols];
        for (int c = 0; c < cols; c++) r.cells[c] = EMPTY;
        return r;
    }

    /** 不分月份、不插日期格（依大小排序時使用）。 */
    public static ArrayList<Row> plain(int count, int cols) {
        ArrayList<Row> rows = new ArrayList<Row>();
        if (cols < 1) cols = 1;
        for (int k = 0; k < count; k += cols) {
            Row r = newCells(cols, 0);
            for (int c = 0; c < cols && k + c < count; c++) r.cells[c] = k + c;
            rows.add(r);
        }
        return rows;
    }

    /** 星期的中文簡寫（Calendar.DAY_OF_WEEK：1=週日）。 */
    public static String weekday(int yyyymmdd) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(yyyymmdd / 10000, (yyyymmdd / 100) % 100 - 1, yyyymmdd % 100);
        String[] w = { "週日", "週一", "週二", "週三", "週四", "週五", "週六" };
        return w[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }
}
