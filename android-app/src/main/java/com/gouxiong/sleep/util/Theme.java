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
    public static final int BLUE = Color.rgb(47, 128, 237);
    public static final int GREEN = Color.rgb(39, 174, 96);
    public static final int ORANGE = Color.rgb(242, 153, 74);
    public static final int RED = Color.rgb(235, 87, 87);
    public static final int WARM_WHITE = Color.rgb(255, 253, 248);
    public static final int TEXT = Color.rgb(36, 48, 64);
    public static final int MUTED = Color.rgb(102, 112, 133);

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
        view.setLineSpacing(dp(context, 2), 1.05f);
        return view;
    }

    public static Button button(Context context, String value, int color) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(22);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 72));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(context, 18));
        button.setBackground(bg);
        button.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        return button;
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(context, (int) radiusDp));
        return bg;
    }

    public static GradientDrawable card(Context context) {
        GradientDrawable bg = rounded(Color.WHITE, 18, context);
        bg.setStroke(dp(context, 1), Color.rgb(232, 237, 245));
        return bg;
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
