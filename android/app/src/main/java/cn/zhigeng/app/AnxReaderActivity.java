package cn.zhigeng.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Locale;

/**
 * Anx Reader compatibility surface for 知更.
 *
 * The Anx foliate-js reader is embedded as a local, offline WebView module.
 * EPUB/TXT/PDF continue using the existing Readium/PdfRenderer paths; MOBI,
 * AZW3 and FB2 are routed here so the same signed app can read all Anx formats.
 */
public final class AnxReaderActivity extends Activity {
    private static final int PAPER = 0xfff7f3e9;
    private static final int INK = 0xff303136;
    private static final int NIGHT = 0xff1c1d20;
    private static final int NIGHT_INK = 0xffd7d8dc;

    private WebView webView;
    private FrameLayout root;
    private LinearLayout chrome;
    private TextView progress;
    private TextView titleView;
    private Uri bookUri;
    private String format;
    private String prefKey;
    private boolean night;
    private boolean chromeVisible = true;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        bookUri = Uri.parse(getIntent().getStringExtra("uri"));
        format = AnxReaderFormats.normalize(getIntent().getStringExtra("format"));
        String title = getIntent().getStringExtra("title");
        if (TextUtils.isEmpty(title)) title = "本地图书";
        prefKey = "anx:" + Integer.toHexString(bookUri.toString().hashCode());
        android.content.SharedPreferences prefs = getSharedPreferences("reader", MODE_PRIVATE);
        night = prefs.getBoolean(prefKey + ":night", false);

        getWindow().setStatusBarColor(night ? NIGHT : PAPER);
        getWindow().setNavigationBarColor(night ? NIGHT : PAPER);
        root = new FrameLayout(this);
        root.setBackgroundColor(night ? NIGHT : PAPER);
        setContentView(root);
        buildChrome(title);
        buildWebView();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(13f);
        view.setAllCaps(false);
        view.setTextColor(night ? NIGHT_INK : INK);
        view.setStateListAnimator(null);
        view.setMinHeight(dp(42));
        return view;
    }

    private void buildChrome(String title) {
        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setPadding(dp(12), dp(16), dp(12), dp(10));
        chrome.setBackgroundColor(night ? NIGHT : PAPER);
        chrome.setElevation(dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹ 返回");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(70), dp(44)));
        titleView = text(title, 16f, night ? NIGHT_INK : INK);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxLines(1);
        top.addView(titleView, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button style = button("Aa");
        style.setContentDescription("安读阅读设置");
        style.setOnClickListener(v -> showStyleDialog());
        top.addView(style, new LinearLayout.LayoutParams(dp(58), dp(44)));
        chrome.addView(top);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        progress = text("安读 Anx Reader · 正在打开…", 11f, night ? 0xff9da0aa : 0xff8a8c91);
        bottom.addView(progress, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button previous = button("‹");
        previous.setContentDescription("上一页");
        previous.setOnClickListener(v -> evaluate("typeof prevPage==='function' && prevPage()"));
        bottom.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(42)));
        Button next = button("›");
        next.setContentDescription("下一页");
        next.setOnClickListener(v -> evaluate("typeof nextPage==='function' && nextPage()"));
        bottom.addView(next, new LinearLayout.LayoutParams(dp(52), dp(42)));
        chrome.addView(bottom);
        root.addView(chrome, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void buildWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(night ? NIGHT : PAPER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new Bridge(this), "Android");
        webView.setWebViewClient(new LocalReaderClient());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.topMargin = dp(2);
        root.addView(webView, 0, params);
        webView.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                view.postDelayed(() -> {}, 20);
            }
            return false;
        });
        webView.loadUrl(readerUrl());
    }

    private String readerUrl() {
        String base = "https://appassets.androidplatform.net/books/current." + format;
        String cfi = getSharedPreferences("reader", MODE_PRIVATE).getString(prefKey + ":cfi", "");
        String style;
        try {
            JSONObject value = new JSONObject();
            value.put("fontSize", 1.15);
            value.put("fontWeight", 400);
            value.put("spacing", 1.65);
            value.put("paragraphSpacing", 0.65);
            value.put("textIndent", 2.0);
            value.put("topMargin", 4);
            value.put("bottomMargin", 4);
            value.put("sideMargin", 5);
            value.put("letterSpacing", 0);
            value.put("backgroundColor", night ? "#1c1d20" : "#f7f3e9");
            value.put("fontColor", night ? "#d7d8dc" : "#303136");
            value.put("pageTurnStyle", "slide");
            value.put("maxColumnCount", 1);
            value.put("columnThreshold", 800);
            value.put("writingMode", "horizontal-tb");
            value.put("textAlign", "justify");
            value.put("backgroundImage", "");
            value.put("bgimgBlur", 0);
            value.put("bgimgOpacity", 1);
            value.put("bgimgFit", "cover");
            value.put("allowScript", false);
            value.put("customCSS", "");
            value.put("customCSSEnabled", false);
            value.put("useBookStyles", false);
            value.put("headingFontSize", 1.25);
            value.put("codeHighlightTheme", "off");
            style = value.toString();
        } catch (Exception error) {
            style = "{}";
        }
        return "https://appassets.androidplatform.net/anx/foliate-js/index.html"
                + "?importing=false"
                + "&url=" + Uri.encode(JSONObject.quote(base))
                + "&initialCfi=" + Uri.encode(TextUtils.isEmpty(cfi) ? "null" : JSONObject.quote(cfi))
                + "&style=" + Uri.encode(style)
                + "&readingRules=" + Uri.encode("{}");
    }

    private void evaluate(String script) {
        if (webView != null) webView.evaluateJavascript("try{" + script + "}catch(e){}", null);
    }

    private void updateChrome(boolean visible) {
        chromeVisible = visible;
        chrome.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showStyleDialog() {
        final String[] options = {"纸张日间", "夜间阅读"};
        new AlertDialog.Builder(this).setTitle("安读阅读设置").setSingleChoiceItems(options, night ? 1 : 0, (dialog, which) -> {
            night = which == 1;
            getSharedPreferences("reader", MODE_PRIVATE).edit().putBoolean(prefKey + ":night", night).apply();
            String background = night ? "#1c1d20" : "#f7f3e9";
            String foreground = night ? "#d7d8dc" : "#303136";
            evaluate("typeof changeStyle==='function' && changeStyle({backgroundColor:'" + background + "',fontColor:'" + foreground + "'})");
            root.setBackgroundColor(night ? NIGHT : PAPER);
            chrome.setBackgroundColor(night ? NIGHT : PAPER);
            dialog.dismiss();
        }).setNegativeButton("取消", null).show();
    }

    private final class LocalReaderClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return openLocal(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return openLocal(Uri.parse(url));
        }

        private WebResourceResponse openLocal(Uri url) {
            if (!"appassets.androidplatform.net".equalsIgnoreCase(url.getHost())) return null;
            String path = url.getPath();
            try {
                if (path != null && path.startsWith("/anx/")) {
                    String asset = Uri.decode(path.substring(5));
                    InputStream stream = getAssets().open("anx/" + asset);
                    return new WebResourceResponse(mime(asset), "UTF-8", stream);
                }
                if (path != null && path.startsWith("/books/")) {
                    InputStream stream = bookStream();
                    return new WebResourceResponse(AnxReaderFormats.mimeType(format), null, stream);
                }
            } catch (Exception ignored) {
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }
            return null;
        }

        private InputStream bookStream() throws Exception {
            if ("file".equalsIgnoreCase(bookUri.getScheme())) return new FileInputStream(new File(bookUri.getPath()));
            InputStream stream = getContentResolver().openInputStream(bookUri);
            if (stream == null) throw new IllegalStateException("图书文件不可读");
            return stream;
        }

        private String mime(String name) {
            String value = URLConnection.guessContentTypeFromName(name);
            if (value != null) return value;
            if (name.endsWith(".js")) return "application/javascript";
            if (name.endsWith(".css")) return "text/css";
            if (name.endsWith(".html")) return "text/html";
            if (name.endsWith(".json")) return "application/json";
            if (name.endsWith(".woff2")) return "font/woff2";
            return "application/octet-stream";
        }
    }

    public final class Bridge {
        private final Context context;
        Bridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void onEvent(String name, String payload) {
            runOnUiThread(() -> {
                try {
                    JSONObject data = payload == null || "null".equals(payload) ? new JSONObject() : new JSONObject(payload);
                    if ("onRelocated".equals(name)) {
                        String cfi = data.optString("cfi", "");
                        if (!TextUtils.isEmpty(cfi)) getSharedPreferences("reader", MODE_PRIVATE).edit().putString(prefKey + ":cfi", cfi).apply();
                        double fraction = data.optDouble("percentage", 0d) * 100d;
                        progress.setText(String.format(Locale.CHINA, "安读 Anx Reader  ·  %.1f%%", fraction));
                    } else if ("onClick".equals(name)) {
                        updateChrome(!chromeVisible);
                    } else if ("onLoadEnd".equals(name)) {
                        progress.setText("安读 Anx Reader  ·  已打开");
                    } else if ("onExternalLink".equals(name)) {
                        String link = data.optString("href", data.optString("url", ""));
                        if (!TextUtils.isEmpty(link)) BrowserLinks.open(AnxReaderActivity.this, link);
                    }
                } catch (Exception ignored) { }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("Android");
            webView.destroy();
        }
        super.onDestroy();
    }
}
