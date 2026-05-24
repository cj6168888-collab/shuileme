package com.gouxiong.sleep;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gouxiong.sleep.util.PreferenceStore;
import com.gouxiong.sleep.util.ServerApiClient;
import com.gouxiong.sleep.util.Theme;

public class LinlyAvatarPreviewActivity extends Activity {
    private PreferenceStore prefs;
    private TextView status;
    private FrameLayout webHost;
    private WebView webView;
    private String linlyUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new PreferenceStore(this);
        buildUi();
        refreshStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(this, 14), Theme.dp(this, 22), Theme.dp(this, 14), Theme.dp(this, 14));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.WARM_WHITE);

        TextView title = Theme.text(this, "Linly Avatar Preview", 28, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView sub = Theme.text(this, "Validates Linly Stream and the Gouxiong WebRTC relay. Main companion still uses local 2D fallback.", 15, Theme.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, matchWrap());

        webHost = new FrameLayout(this);
        webHost.setBackground(Theme.tintedCard(this, Theme.BLUE));
        TextView placeholder = Theme.text(this, "Waiting for Linly Stream config...", 22, Theme.BLUE, Typeface.BOLD);
        placeholder.setGravity(Gravity.CENTER);
        webHost.addView(placeholder, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, 0, 1);
        previewLp.setMargins(0, Theme.dp(this, 8), 0, Theme.dp(this, 10));
        root.addView(webHost, previewLp);

        status = Theme.text(this, "Reading server /health ...", 16, Theme.MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(Theme.dp(this, 44));
        root.addView(status, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button refresh = Theme.softButton(this, "Refresh", Theme.BLUE);
        refresh.setTextSize(18);
        refresh.setMinHeight(Theme.dp(this, 56));
        refresh.setOnClickListener(v -> refreshStatus());
        actions.addView(refresh, weightedButtonLp());

        Button open = Theme.button(this, "Linly", Theme.GREEN);
        open.setTextSize(18);
        open.setMinHeight(Theme.dp(this, 56));
        open.setOnClickListener(v -> openLinlyWeb());
        actions.addView(open, weightedButtonLp());

        Button relay = Theme.button(this, "Relay", Theme.LILAC);
        relay.setTextSize(18);
        relay.setMinHeight(Theme.dp(this, 56));
        relay.setOnClickListener(v -> openRelayPreview());
        actions.addView(relay, weightedButtonLp());

        Button close = Theme.softButton(this, "Close", Theme.ORANGE);
        close.setTextSize(18);
        close.setMinHeight(Theme.dp(this, 56));
        close.setOnClickListener(v -> finish());
        actions.addView(close, weightedButtonLp());

        root.addView(actions, matchWrap());
        setContentView(root);
    }

    private void refreshStatus() {
        status.setText("Checking " + prefs.serverBaseUrl() + " ...");
        new Thread(() -> {
            try {
                ServerApiClient.ServerHealth health = ServerApiClient.health(prefs.serverBaseUrl());
                linlyUrl = health.linlyDigitalHumanWebUrl;
                String line = health.digitalHumanLine();
                if (prefs.serverAuthToken().length() > 0) {
                    try {
                        ServerApiClient.AvatarStatus avatar = ServerApiClient.avatarStatus(prefs.serverBaseUrl(), prefs.serverAuthToken());
                        if (avatar.linlyConfigured) {
                            linlyUrl = avatar.webUrl;
                            line = avatar.line();
                            if (!avatar.live) {
                                line += "\nLinly sidecar health 未通过，预览页面可能打不开或无法出流。";
                            }
                        }
                    } catch (Exception ignored) {
                        line += "\nAuthenticated avatar status failed; using public /health.";
                    }
                }
                final String finalLine = line;
                runOnUiThread(() -> status.setText(finalLine));
            } catch (Exception error) {
                runOnUiThread(() -> status.setText("Check failed: " + cleanError(error)));
            }
        }, "GouXiongLinlyPreviewStatus").start();
    }

    private void openLinlyWeb() {
        if (linlyUrl == null || linlyUrl.trim().length() == 0) {
            status.setText("Linly Stream is not configured. Set LINLY_TALKER_STREAM_ENABLED=1 and LINLY_TALKER_STREAM_URL on the server.");
            refreshStatus();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 2201);
        }
        ensureWebView();
        status.setText("Opening " + linlyUrl + " ...");
        webView.loadUrl(linlyUrl);
    }

    private void openRelayPreview() {
        if (prefs.serverAuthToken().length() == 0) {
            status.setText("Login first. The relay preview uses authenticated /api/avatar/session/offer.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 2201);
        }
        ensureWebView();
        status.setText("Opening Gouxiong relay preview...");
        webView.loadUrl("file:///android_asset/linly/relay-preview.html");
    }

    private void ensureWebView() {
        if (webView != null) return;
        webHost.removeAllViews();
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 16) {
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= 21) {
                    request.grant(request.getResources());
                }
            }
        });
        webView.addJavascriptInterface(new RelayBridge(), "AndroidLinlyRelay");
        webHost.addView(webView, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private String cleanError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.length() == 0 ? String.valueOf(error) : message;
    }

    private final class RelayBridge {
        @JavascriptInterface
        public String serverBaseUrl() {
            return prefs.serverBaseUrl();
        }

        @JavascriptInterface
        public String authToken() {
            return prefs.serverAuthToken();
        }

        @JavascriptInterface
        public void onRelayLog(String message) {
            final String clean = message == null ? "" : message;
            runOnUiThread(() -> status.setText("Relay: " + clean));
        }
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
