package com.gouxiong.sleep.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Theme {
    public static final int BLUE = Color.rgb(43, 104, 226);
    public static final int GREEN = Color.rgb(71, 168, 88);
    public static final int ORANGE = Color.rgb(255, 147, 30);
    public static final int RED = Color.rgb(235, 70, 52);
    public static final int WARM_WHITE = Color.rgb(255, 249, 235);
    public static final int CREAM = Color.rgb(255, 244, 220);
    public static final int PINK = Color.rgb(255, 139, 139);
    public static final int LILAC = Color.rgb(139, 118, 218);
    public static final int TEXT = Color.rgb(28, 45, 73);
    public static final int MUTED = Color.rgb(107, 118, 140);

    private Theme() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, int sp, int color, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setLineSpacing(dp(context, 3), 1.08f);
        return view;
    }

    public static Button button(Context context, String value, int color) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(24);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 76));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lighten(color, 0.18f), color, darken(color, 0.10f)});
        bg.setCornerRadius(dp(context, 28));
        bg.setStroke(dp(context, 1), darken(color, 0.18f));
        button.setBackground(bg);
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            button.setElevation(dp(context, 4));
            button.setStateListAnimator(null);
        }
        return button;
    }

    public static Button softButton(Context context, String value, int color) {
        Button button = button(context, value, color);
        button.setTextColor(darken(color, 0.38f));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.WHITE, mix(color, Color.WHITE, 0.80f)});
        bg.setCornerRadius(dp(context, 26));
        bg.setStroke(dp(context, 1), mix(color, Color.WHITE, 0.45f));
        button.setBackground(bg);
        return button;
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(context, (int) radiusDp));
        return bg;
    }

    public static GradientDrawable card(Context context) {
        GradientDrawable bg = rounded(Color.WHITE, 28, context);
        bg.setStroke(dp(context, 1), Color.rgb(239, 229, 206));
        return bg;
    }

    public static GradientDrawable tintedCard(Context context, int color) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.WHITE, mix(color, Color.WHITE, 0.86f)});
        bg.setCornerRadius(dp(context, 30));
        bg.setStroke(dp(context, 1), mix(color, Color.WHITE, 0.58f));
        return bg;
    }

    public static GradientDrawable navBar(Context context) {
        GradientDrawable bg = rounded(Color.rgb(255, 247, 229), 28, context);
        bg.setStroke(dp(context, 1), Color.rgb(241, 225, 190));
        return bg;
    }

    public static int mix(int a, int b, float amountOfB) {
        float t = Math.max(0f, Math.min(1f, amountOfB));
        int r = Math.round(Color.red(a) * (1f - t) + Color.red(b) * t);
        int g = Math.round(Color.green(a) * (1f - t) + Color.green(b) * t);
        int bl = Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    public static int lighten(int color, float amount) {
        return mix(color, Color.WHITE, amount);
    }

    public static int darken(int color, float amount) {
        return mix(color, Color.BLACK, amount);
    }

    public static void margin(View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) view.getLayoutParams();
            Context c = view.getContext();
            lp.setMargins(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
            view.setLayoutParams(lp);
        }
    }
}
