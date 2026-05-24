package com.gouxiong.sleep;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gouxiong.sleep.util.Theme;

public class DebugLive2DEntryActivity extends Activity {
    private boolean autoLoad;
    private boolean debugCommands;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isDebuggableBuild()) {
            finish();
            return;
        }
        Intent source = getIntent();
        if (source != null) {
            autoLoad = source.getBooleanExtra("auto_load", false);
            debugCommands = source.getBooleanExtra("debug_commands", false);
        }
        drawFirstFrame();
        getWindow().getDecorView().postDelayed(this::openPreview, 500);
    }

    private void drawFirstFrame() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(Theme.dp(this, 24), Theme.dp(this, 24), Theme.dp(this, 24), Theme.dp(this, 24));
        root.setBackgroundColor(Theme.WARM_WHITE);
        TextView title = Theme.text(this, "Live2D debug gate", 24, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView status = Theme.text(this, "Opening gated preview...", 16, Theme.MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void openPreview() {
        Intent preview = new Intent(this, Live2DPreviewActivity.class);
        preview.putExtra("auto_load", autoLoad);
        preview.putExtra("debug_commands", debugCommands);
        startActivity(preview);
        finish();
    }

    private boolean isDebuggableBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
