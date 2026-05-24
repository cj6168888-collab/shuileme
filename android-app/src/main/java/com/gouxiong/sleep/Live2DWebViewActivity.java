package com.gouxiong.sleep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gouxiong.sleep.util.Theme;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Live2DWebViewActivity extends Activity {
    private static final String TAG = "Live2DPreview";
    private static final String ASSET_HOST = "sleep.local";
    private static final String LIVE2D_BASE_URL = "https://" + ASSET_HOST + "/live2d/hiyori/";

    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout previewHost;
    private TextView status;
    private Button loadButton;
    private Button speakButton;
    private WebView webView;
    private boolean bridgeAnswered;
    private boolean loading;
    private boolean autoLoadRequested;
    private boolean autoLoadScheduled;
    private long loadStartedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (getIntent() != null && getIntent().getBooleanExtra("auto_load", false)) {
            autoLoadRequested = true;
            status.setText("Waiting for the preview window to settle before loading.");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && autoLoadRequested && !autoLoadScheduled) {
            autoLoadScheduled = true;
            status.setText("Window ready. Loading Live2D shortly...");
            main.postDelayed(this::loadLive2D, 12000);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Theme.dp(this, 14), Theme.dp(this, 22), Theme.dp(this, 14), Theme.dp(this, 14));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.WARM_WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(Theme.dp(this, 12), Theme.dp(this, 8), Theme.dp(this, 12), Theme.dp(this, 8));
        TextView title = Theme.text(this, "L01 Hiyori Live2D", 28, Theme.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        header.addView(title, matchWrap());
        TextView sub = Theme.text(this, "Independent preview. Main chat stays stable until this passes.", 15, Theme.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        header.addView(sub, matchWrap());
        root.addView(header, matchWrap());

        previewHost = new FrameLayout(this);
        previewHost.setBackground(Theme.tintedCard(this, Theme.LILAC));
        TextView placeholder = Theme.text(this, "Tap Load to test L01.", 22, Theme.LILAC, Typeface.BOLD);
        placeholder.setGravity(Gravity.CENTER);
        previewHost.addView(placeholder, frameMatch());
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, 0, 1);
        previewLp.setMargins(0, Theme.dp(this, 8), 0, Theme.dp(this, 10));
        root.addView(previewHost, previewLp);

        status = Theme.text(this, "Not loaded. This is a gated technical preview.", 16, Theme.MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(Theme.dp(this, 36));
        root.addView(status, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        loadButton = Theme.button(this, "Load", Theme.LILAC);
        loadButton.setTextSize(20);
        loadButton.setMinHeight(Theme.dp(this, 58));
        loadButton.setOnClickListener(v -> loadLive2D());
        actions.addView(loadButton, weightedButtonLp());

        speakButton = Theme.softButton(this, "Speak", Theme.GREEN);
        speakButton.setTextSize(20);
        speakButton.setMinHeight(Theme.dp(this, 58));
        speakButton.setEnabled(false);
        speakButton.setOnClickListener(v -> sendToLive2D("window.setMood && window.setMood('speaking'); window.setMouth && window.setMouth(0.9);"));
        actions.addView(speakButton, weightedButtonLp());

        Button close = Theme.softButton(this, "Close", Theme.ORANGE);
        close.setTextSize(20);
        close.setMinHeight(Theme.dp(this, 58));
        close.setOnClickListener(v -> finish());
        actions.addView(close, weightedButtonLp());

        root.addView(actions, matchWrap());
        setContentView(root);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void loadLive2D() {
        if (loading) {
            status.setText("Live2D is still loading. Please wait for a pass or fail result.");
            return;
        }
        destroyWebView();
        loading = true;
        bridgeAnswered = false;
        loadStartedAt = SystemClock.elapsedRealtime();
        setLoadButtons(true, false);
        status.setText("Loading local Live2D assets...");
        previewHost.removeAllViews();

        webView = new WebView(this);
        status.setText("Starting Android WebView...");
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new AssetClient());
        webView.setWebChromeClient(new ConsoleClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidLive2D");
        previewHost.addView(webView, frameMatch());
        String html = readAssetText("live2d/hiyori/index.html");
        if (html.length() == 0) {
            loading = false;
            setLoadButtons(false, false);
            status.setText("Live2D failed: preview html missing");
            return;
        }
        status.setText("Injecting Live2D preview HTML...");
        webView.loadDataWithBaseURL(LIVE2D_BASE_URL, html, "text/html", "UTF-8", null);

        main.postDelayed(() -> {
            if (!bridgeAnswered && webView != null) {
                status.setText("Still waiting after " + elapsedSeconds() + "s. WebView is alive, model not ready yet.");
            }
        }, 12000);
        main.postDelayed(() -> {
            if (!bridgeAnswered && webView != null) {
                status.setText("Validation timeout after " + elapsedSeconds() + "s. Do not promote this to the main avatar on this device.");
                loading = false;
                setLoadButtons(false, false);
            }
        }, 120000);
    }

    private void sendToLive2D(String script) {
        if (webView == null) {
            status.setText("Load the preview first.");
            return;
        }
        webView.evaluateJavascript(script, null);
    }

    private void setLoadButtons(boolean busy, boolean canSpeak) {
        if (loadButton != null) {
            loadButton.setEnabled(!busy);
            loadButton.setText(busy ? "Loading" : "Load");
        }
        if (speakButton != null) {
            speakButton.setEnabled(canSpeak);
        }
    }

    private long elapsedSeconds() {
        if (loadStartedAt <= 0) return 0;
        return Math.max(0, (SystemClock.elapsedRealtime() - loadStartedAt) / 1000);
    }

    private void destroyWebView() {
        if (webView == null) return;
        try {
            previewHost.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "destroy webview failed", e);
        }
        webView = null;
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Theme.dp(this, 3), 0, Theme.dp(this, 3), 0);
        return lp;
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady() {
            bridgeAnswered = true;
            main.post(() -> {
                loading = false;
                setLoadButtons(false, true);
                Log.i(TAG, "bridge ready after " + elapsedSeconds() + "s");
                status.setText("Loaded in " + elapsedSeconds() + "s. L01 is rendering in WebView preview.");
            });
        }

        @JavascriptInterface
        public void onError(String message) {
            bridgeAnswered = true;
            final String clean = message == null ? "unknown error" : message;
            main.post(() -> {
                loading = false;
                setLoadButtons(false, false);
                Log.w(TAG, "bridge error after " + elapsedSeconds() + "s: " + clean);
                status.setText("Live2D failed after " + elapsedSeconds() + "s: " + clean);
            });
        }

        @JavascriptInterface
        public void onStatus(String message) {
            final String clean = message == null ? "" : message;
            if (clean.length() == 0) return;
            main.post(() -> {
                if (!bridgeAnswered && webView != null) {
                    Log.i(TAG, "bridge status after " + elapsedSeconds() + "s: " + clean);
                    status.setText(clean + " (" + elapsedSeconds() + "s)");
                }
            });
        }
    }

    private final class ConsoleClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (consoleMessage != null) {
                Log.d(TAG, "console " + consoleMessage.messageLevel() + ": " + consoleMessage.message());
            }
            return true;
        }
    }

    private final class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) return null;
            if (!ASSET_HOST.equals(request.getUrl().getHost())) return null;
            String path = request.getUrl().getPath();
            if (path == null || !path.startsWith("/live2d/")) return null;
            String assetPath = path.substring(1);
            try {
                InputStream in = getAssets().open(assetPath);
                return new WebResourceResponse(mimeType(assetPath), encoding(assetPath), in);
            } catch (Exception e) {
                Log.w(TAG, "asset not found: " + assetPath, e);
                return null;
            }
        }
    }

    private String mimeType(String path) {
        String clean = path == null ? "" : path.toLowerCase();
        if (clean.endsWith(".html")) return "text/html";
        if (clean.endsWith(".js")) return "application/javascript";
        if (clean.endsWith(".json")) return "application/json";
        if (clean.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private String encoding(String path) {
        String clean = path == null ? "" : path.toLowerCase();
        if (clean.endsWith(".png") || clean.endsWith(".moc3")) return null;
        return "UTF-8";
    }

    private String readAssetText(String path) {
        try (InputStream in = getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "read asset failed: " + path, e);
            return "";
        }
    }
}
