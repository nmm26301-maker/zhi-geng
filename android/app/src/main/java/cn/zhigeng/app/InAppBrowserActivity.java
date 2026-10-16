package cn.zhigeng.app;
import android.app.Activity;import android.os.Bundle;import android.webkit.*;import android.widget.*;import android.graphics.Color;

/** No Javascript bridge, file access or external-browser redirects. */
public class InAppBrowserActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfff7f5ef);Button back=new Button(this);back.setText("← 返回知更");back.setOnClickListener(v->finish());root.addView(back);web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSafeBrowsingEnabled(true);web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return Catalog.secureUrl(req.getUrl().toString()).isEmpty();}});String url=Catalog.secureUrl(getIntent().getStringExtra("url"));if(!url.isEmpty())web.loadUrl(url);}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}
