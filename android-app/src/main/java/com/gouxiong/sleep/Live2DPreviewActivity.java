package com.gouxiong.sleep;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gouxiong.sleep.util.Theme;

public class Live2DPreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (getIntent() != null && getIntent().getBooleanExtra("auto_load", false)) {
            findViewById(1001).postDelayed(this::openWebViewPreview, 8000);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(this, 14), Theme.dp(this, 22), Theme.dp(this, 14), Theme.dp(this, 14));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.WARM_WHITE);

        TextView title = Theme.text(this, "L01 Hiyori Live2D", 28, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView sub = Theme.text(this, "Independent preview. Main chat stays stable until this passes.", 15, Theme.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, matchWrap());

        FrameLayout previewHost = new FrameLayout(this);
        previewHost.setBackground(Theme.tintedCard(this, Theme.LILAC));
        TextView placeholder = Theme.text(this, "Tap Load to test L01.", 22, Theme.LILAC, Typeface.BOLD);
        placeholder.setGravity(Gravity.CENTER);
        previewHost.addView(placeholder, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, 0, 1);
        previewLp.setMargins(0, Theme.dp(this, 8), 0, Theme.dp(this, 10));
        root.addView(previewHost, previewLp);

        TextView status = Theme.text(this, "Not loaded. This is a gated technical preview.", 16, Theme.MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(Theme.dp(this, 36));
        root.addView(status, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button load = Theme.button(this, "Load", Theme.LILAC);
        load.setId(1001);
        load.setTextSize(20);
        load.setMinHeight(Theme.dp(this, 58));
        load.setOnClickListener(v -> openWebViewPreview());
        actions.addView(load, weightedButtonLp());

        Button close = Theme.softButton(this, "Close", Theme.ORANGE);
        close.setTextSize(20);
        close.setMinHeight(Theme.dp(this, 58));
        close.setOnClickListener(v -> finish());
        actions.addView(close, weightedButtonLp());

        root.addView(actions, matchWrap());
        setContentView(root);
    }

    private void openWebViewPreview() {
        Intent intent = new Intent(this, Live2DWebViewActivity.class);
        intent.putExtra("auto_load", true);
        startActivity(intent);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
        return lp;
    }
}
