package com.photobox.sorter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 整理模式的一張卡片。 */
public class Card extends FrameLayout {

    public final ImageView img;
    public final View veil;
    public final LinearLayout badge;
    public final ImageView badgeIcon;
    public final TextView badgeText;
    public final TextView info;
    public final TextView sub;
    public final ImageView playIcon;
    public Item item;

    public Card(Context c) {
        super(c);
        final int r = Ui.dp(c, 22);
        setBackground(Ui.round(0xFF0A0C11, r));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        setClipToOutline(true);
        setElevation(Ui.dp(c, 10));

        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(img, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        playIcon = new ImageView(c);
        playIcon.setImageDrawable(Icons.get(Icons.PLAY, 0xCCFFFFFF));
        playIcon.setBackground(Ui.round(0x66000000, Ui.dp(c, 30)));
        playIcon.setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14));
        LayoutParams plp = new LayoutParams(Ui.dp(c, 62), Ui.dp(c, 62));
        plp.gravity = Gravity.CENTER;
        playIcon.setVisibility(GONE);
        addView(playIcon, plp);

        // 底部漸層資訊列
        View scrim = new View(c);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{ 0xE6000000, 0x66000000, 0x00000000 });
        scrim.setBackground(g);
        LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 110));
        slp.gravity = Gravity.BOTTOM;
        addView(scrim, slp);

        LinearLayout infoBox = Ui.col(c);
        infoBox.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), Ui.dp(c, 16));
        info = Ui.tv(c, "", 15.5f, 0xFFFFFFFF, true);
        info.setMaxLines(1);
        info.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        sub = Ui.tv(c, "", 12.5f, 0xCCE3E8F0, false);
        sub.setMaxLines(1);
        infoBox.addView(info, Ui.lpW());
        infoBox.addView(Ui.space(c, 3));
        infoBox.addView(sub, Ui.lpW());
        LayoutParams ilp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.BOTTOM;
        addView(infoBox, ilp);

        veil = new View(c);
        veil.setAlpha(0f);
        addView(veil, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        badge = Ui.rowH(c);
        badge.setAlpha(0f);
        badge.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 22), Ui.dp(c, 12));
        badgeIcon = new ImageView(c);
        badge.addView(badgeIcon, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        badgeText = Ui.tv(c, "", 18f, Color.WHITE, true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Ui.dp(c, 10);
        badge.addView(badgeText, blp);
        LayoutParams badgeLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.CENTER;
        addView(badge, badgeLp);
    }

    public void bind(Item it, int maxPx) {
        this.item = it;
        if (it == null) {
            img.setImageBitmap(null);
            info.setText("");
            sub.setText("");
            playIcon.setVisibility(GONE);
            return;
        }
        Thumbs.full(img, it, maxPx, null);
        info.setText(it.name);
        sub.setText(it.timeText() + "　·　" + it.bucket + "　·　" + it.sizeText());
        playIcon.setVisibility(it.video ? VISIBLE : GONE);
        veil.setAlpha(0f);
        badge.setAlpha(0f);
    }

    public void showHint(int action, float amount) {
        int color = Prefs.color(action);
        veil.setBackgroundColor(Ui.withAlpha(color, 0x4D));
        veil.setAlpha(Math.min(1f, amount));
        badgeIcon.setImageDrawable(Icons.get(Prefs.icon(action), 0xFFFFFFFF));
        badgeText.setText(Prefs.ACT_NAMES[action]);
        badge.setBackground(Ui.round(Ui.withAlpha(color, 0xF2), Ui.dp(getContext(), 26)));
        badge.setAlpha(Math.min(1f, amount * 1.4f));
        badge.setScaleX(0.85f + 0.15f * Math.min(1f, amount));
        badge.setScaleY(0.85f + 0.15f * Math.min(1f, amount));
    }

    public void clearHint() {
        veil.setAlpha(0f);
        badge.setAlpha(0f);
    }
}
