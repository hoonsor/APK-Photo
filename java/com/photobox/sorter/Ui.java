package com.photobox.sorter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static TextView tv(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD)
                           : Typeface.create("sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static GradientDrawable round(int fill, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable round(int fill, int radiusPx, int strokeColor, int strokePx) {
        GradientDrawable g = round(fill, radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    public static Drawable ripple(Drawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static Drawable tappable(int fill, int radiusPx) {
        return ripple(round(fill, radiusPx), 0x33FFFFFF);
    }

    public static Drawable tappable(int fill, int radiusPx, int strokeColor, int strokePx) {
        return ripple(round(fill, radiusPx, strokeColor, strokePx), 0x33FFFFFF);
    }

    public static View space(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static View grow(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1);
        lp.weight = 1f;
        v.setLayoutParams(lp);
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(T.BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout rowH(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lpW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams lpWeight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1f;
        return lp;
    }

    /** 主要動作按鈕。 */
    public static TextView btn(Context c, String label, int fill, int textColor) {
        TextView t = tv(c, label, 15.5f, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(tappable(fill, dp(c, 14)));
        t.setPadding(dp(c, 18), dp(c, 13), dp(c, 18), dp(c, 13));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    /** 次要（外框）按鈕。 */
    public static TextView btnGhost(Context c, String label, int color) {
        TextView t = tv(c, label, 15f, color, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(tappable(0x00000000, dp(c, 14), withAlpha(color, 0x66), dp(c, 1)));
        t.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int blend(int a, int b, float t) {
        float inv = 1f - t;
        return Color.argb(
                (int) (Color.alpha(a) * inv + Color.alpha(b) * t),
                (int) (Color.red(a) * inv + Color.red(b) * t),
                (int) (Color.green(a) * inv + Color.green(b) * t),
                (int) (Color.blue(a) * inv + Color.blue(b) * t));
    }

    public static void toast(Context c, String s) {
        try { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
    }

    public static ColorDrawable cd(int color) { return new ColorDrawable(color); }

    /** 多選工具列上的「分享」按鈕：主色實心＋圖示＋文字，一眼就找得到。 */
    public static TextView sharePill(Context c) {
        TextView t = tv(c, "分享", 14f, 0xFF0B0E16, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(tappable(T.PRIMARY, dp(c, 18)));
        t.setPadding(dp(c, 12), dp(c, 8), dp(c, 14), dp(c, 8));
        Drawable ic = icon(c, Icons.SHARE, 0xFF0B0E16, 17);
        t.setCompoundDrawablesRelative(ic, null, null, null);
        t.setCompoundDrawablePadding(dp(c, 6));
        return t;
    }

    /** 有固定大小的向量圖示（給 ScaleType.CENTER 或 compound drawable 用）。 */
    public static Drawable icon(Context c, int kind, int color, float dpSize) {
        final int px = dp(c, dpSize);
        Drawable d = new android.graphics.drawable.LayerDrawable(new Drawable[]{ Icons.get(kind, color) }) {
            @Override public int getIntrinsicWidth() { return px; }
            @Override public int getIntrinsicHeight() { return px; }
        };
        d.setBounds(0, 0, px, px);
        return d;
    }
}
