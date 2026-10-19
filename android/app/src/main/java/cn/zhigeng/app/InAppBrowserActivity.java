package cn.zhigeng.app;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** HTTPS browsing stays inside the app, with no JavaScript bridge or local file access. */
public class InAppBrowserActivity extends Activity {
    private WebView web;private FrameLayout host;private LinearLayout bar;private TextView address,error;
    private ProgressBar progress;private View video;private WebChromeClient.CustomViewCallback videoCallback;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(0xfff7f5ef);getWindow().setNavigationBarColor(0xfff7f5ef);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfff7f5ef);bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("‹ 返回");back.setOnClickListener(v->finish());bar.addView(back);address=new TextView(this);address.setTextColor(0xff244e40);address.setTextSize(12);address.setMaxLines(2);bar.addView(address,new LinearLayout.LayoutParams(0,-2,1));Button reload=new Button(this);reload.setText("刷新");reload.setContentDescription("刷新网页");reload.setOnClickListener(v->web.reload());bar.addView(reload);root.addView(bar);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,6));host=new FrameLayout(this);root.addView(host,new LinearLayout.LayoutParams(-1,0,1));web=new WebView(this);host.addView(web,new FrameLayout.LayoutParams(-1,-1));error=new TextView(this);error.setTextColor(0xff244e40);error.setTextSize(16);error.setPadding(32,40,32,40);error.setBackgroundColor(0xfff7f5ef);error.setVisibility(View.GONE);error.setOnClickListener(v->web.reload());host.addView(error,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));setContentView(root);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return Catalog.secureUrl(req.getUrl().toString()).isEmpty();}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){error.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);String safe=Catalog.secureUrl(url);address.setText(safe.isEmpty()?"知更 · 网页":Uri.parse(safe).getHost());}
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError e){if(req.isForMainFrame()){error.setText("网页连接未完成\n请检查网络，点击这里重试。");error.setVisibility(View.VISIBLE);progress.setVisibility(View.GONE);}}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,WebResourceResponse res){if(req.isForMainFrame()&&res.getStatusCode()>=400){error.setText("网站返回 HTTP "+res.getStatusCode()+"\n点击这里重试，或返回稍后再访问。");error.setVisibility(View.VISIBLE);}}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){progress.setProgress(value);if(value==100)progress.setVisibility(View.GONE);}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(video!=null){callback.onCustomViewHidden();return;}video=view;videoCallback=callback;host.addView(video,new FrameLayout.LayoutParams(-1,-1));web.setVisibility(View.GONE);bar.setVisibility(View.GONE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
            @Override public void onHideCustomView(){hideVideo();}
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,android.os.Message message){if(!userGesture)return false;WebView child=new WebView(InAppBrowserActivity.this);child.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){String url=Catalog.secureUrl(req.getUrl().toString());if(!url.isEmpty())web.loadUrl(url);v.post(v::destroy);return true;}});((WebView.WebViewTransport)message.obj).setWebView(child);message.sendToTarget();return true;}
        });
        String url=Catalog.secureUrl(getIntent().getStringExtra("url"));if(!url.isEmpty()){if(state==null||web.restoreState(state)==null)web.loadUrl(url);}else{error.setText("链接暂不可用，请返回知更。");error.setVisibility(View.VISIBLE);}
    }
    private void hideVideo(){if(video==null)return;host.removeView(video);video=null;web.setVisibility(View.VISIBLE);bar.setVisibility(View.VISIBLE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);if(videoCallback!=null){videoCallback.onCustomViewHidden();videoCallback=null;}}
    @Override public void onBackPressed(){if(video!=null)hideVideo();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);web.saveState(state);}
    @Override protected void onPause(){web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){hideVideo();if(web!=null){web.stopLoading();web.destroy();}super.onDestroy();}
}
