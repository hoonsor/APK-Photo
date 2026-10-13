package com.photobox.sorter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 位置字串解析（不引用 Android 類別，可在 JVM 測試）。 */
public final class Geo {
    private Geo() {}

    private static final Pattern ISO6709 = Pattern.compile(
            "([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)?");

    /**
     * 影片 metadata 的位置（ISO 6709），例如 "+25.0330+121.5654/" 或 "+25.0330+121.5654+012.000/"。
     * @return {緯度, 經度, 高度(沒有為 NaN)}；無法解析或為 (0,0) 時回傳 null。
     */
    public static double[] parseIso6709(String s) {
        if (s == null) return null;
        Matcher m = ISO6709.matcher(s.trim());
        if (!m.find()) return null;
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            double alt = m.group(3) != null ? Double.parseDouble(m.group(3)) : Double.NaN;
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return null;
            if (lat == 0 && lon == 0) return null;
            return new double[]{ lat, lon, alt };
        } catch (Throwable t) { return null; }
    }
}
