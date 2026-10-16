package cn.zhigeng.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.*;
import android.widget.*;

/** Native assistant foyer, with the official Duck.ai experience embedded on demand. */
public class AiActivity extends Activity {
    private static final int PAPER=0xfff7f5ef, INK=0xff203b32, MUTED=0xff78837a,
            FOREST=0xff244e40, SAGE=0xffe9eee7, LINE=0xffe4e6de;
    private LinearLayout root, toolbar;
    private FrameLayout stage;
    private ProgressBar progress;
    private TextView subtitle;
    private WebView web;
    private View error;
    private boolean chatting, failed, destroyed;
    private int loadSequence;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=column();root.setBackgroundColor(PAPER);
        toolbar=row();toolbar.setPadding(dp(12),dp(8),dp(12),dp(8));
        Button back=button("‹ 返回",false);back.setContentDescription("返回知更");
        back.setOnClickListener(v->finish());toolbar.addView(back,new LinearLayout.LayoutParams(dp(76),dp(44)));
        LinearLayout title=column();title.setPadding(dp(12),0,0,0);
        add(title,text("AI 助手",17,INK,true),0);subtitle=text("知更 × Duck.ai",10,MUTED,false);add(title,subtitle,4);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("⋯",false);menu.setContentDescription("助手菜单");menu.setOnClickListener(v->menu());
        toolbar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));root.addView(toolbar);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(FOREST));
        progress.setVisibility(View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(state!=null&&state.getBoolean("chatting"))openChat();else foyer();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(color==Color.WHITE)d.setStroke(dp(1),LINE);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1.12f);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:FOREST);b.setBackground(bg(primary?FOREST:SAGE,22));b.setStateListAnimator(null);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinimumHeight(dp(44));b.setMinHeight(dp(44));return b;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}

    private void foyer(){
        chatting=false;loadSequence++;subtitle.setText("知更 × Duck.ai");progress.setVisibility(View.INVISIBLE);
        stage.removeAllViews();error=null;if(web!=null)web.setVisibility(View.GONE);
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(content);stage.addView(scroll);
        TextView eyebrow=text("A LITTLE INSPIRATION  /  聊点新想法",10,FOREST,false);eyebrow.setLetterSpacing(.08f);add(content,eyebrow,0);
        LinearLayout hero=column();hero.setBackground(bg(FOREST,28));hero.setPadding(dp(24),dp(24),dp(24),dp(24));
        add(hero,text("让好奇，\n多走一步。",31,PAPER,true),0);
        add(hero,text("聊一本书，找一部电影，\n也可以从一个小问题开始。",14,SAGE,false),16);
        add(content,hero,18);
        LinearLayout service=column();service.setPadding(dp(20),dp(20),dp(20),dp(20));service.setBackground(bg(Color.WHITE,20));
        LinearLayout serviceTitle=row();serviceTitle.addView(text("Duck.ai",24,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("应用内聊天",10,FOREST,false);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(bg(SAGE,12));serviceTitle.addView(badge);add(service,serviceTitle,0);
        add(service,text("在知更内使用 Duck.ai 官方聊天页。\n模型、历史会话与服务选项由 Duck.ai 提供。",12,MUTED,false),12);
        Button start=button("开始聊天  →",true);add(service,start,18);start.setOnClickListener(v->openChat());add(content,service,18);
        add(content,text("从这里开始",19,INK,true),24);add(content,text("点选后复制问题，再粘贴到聊天框发送。",11,MUTED,false),8);
        prompt(content,"01  /  找一本书","推荐三本适合睡前阅读的中文小说，说明各自的风格，避免剧透。",16);
        prompt(content,"02  /  挑一部电影","推荐三部适合周末看的华语电影，比较它们的主题和观影氛围，避免剧透。",10);
        prompt(content,"03  /  读懂一个概念","用简单的语言解释一个我想了解的概念，先问我想了解什么，再举一个生活中的例子。",10);
        add(content,text("聊天内容由你在 Duck.ai 中输入并发送。\n知更不会自动读取或发送书架、账号和阅读记录。",11,MUTED,false),20);
    }
    private void prompt(LinearLayout parent,String title,String value,int top){
        LinearLayout card=column();card.setPadding(dp(17),dp(16),dp(17),dp(16));card.setBackground(bg(Color.WHITE,20));
        add(card,text(title+"   ↗",14,FOREST,true),0);add(card,text(value,12,MUTED,false),8);card.setContentDescription(title+"，复制问题并打开聊天");
        card.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("聊天问题",value));Toast.makeText(this,"问题已复制，请粘贴到聊天框后发送",Toast.LENGTH_LONG).show();openChat();});add(parent,card,top);
    }
    private void openChat(){
        chatting=true;stage.removeAllViews();error=null;
        if(web==null){
            web=new WebView(this);web.setBackgroundColor(Color.WHITE);
            WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);
            s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setWebChromeClient(new WebChromeClient(){
                @Override public void onProgressChanged(WebView view,int value){if(!chatting||destroyed||failed)return;progress.setProgress(value);progress.setVisibility(value<100?View.VISIBLE:View.INVISIBLE);}
            });
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return route(req.getUrl().toString());}
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return route(url);}
                @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){if(!chatting)return;failed=false;hideError();subtitle.setText("正在加载 · Duck.ai");progress.setVisibility(View.VISIBLE);final int seq=++loadSequence;stage.postDelayed(()->{if(!destroyed&&chatting&&seq==loadSequence&&progress.getVisibility()==View.VISIBLE)showError("连接时间较长，请检查网络后重试。");},30000);}
                @Override public void onPageFinished(WebView view,String url){if(!chatting||failed)return;loadSequence++;progress.setVisibility(View.INVISIBLE);subtitle.setText(DuckAiPolicy.official(url)?"Duck.ai · 官方网页":"应用内浏览");CookieManager.getInstance().flush();}
                @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())showError("暂时无法连接 Duck.ai，请检查网络后重试。");}
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest req,WebResourceResponse res){if(req.isForMainFrame())showError("Duck.ai 暂时无法打开（HTTP "+res.getStatusCode()+"），请稍后重试。");}
                @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();showError("安全连接未能建立，请检查设备时间与网络后重试。");}
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);view.destroy();web=null;showError("聊天页面已关闭，点击重试重新打开。");return true;}
            });
            web.loadUrl(DuckAiPolicy.HOME);
        }
        web.setVisibility(View.VISIBLE);stage.addView(web,new FrameLayout.LayoutParams(-1,-1));
        if(failed)showError("暂时无法连接 Duck.ai，请检查网络后重试。");
    }
    private boolean route(String url){
        if(DuckAiPolicy.official(url))return false;
        if(DuckAiPolicy.https(url))startActivity(new Intent(this,InAppBrowserActivity.class).putExtra("url",url));
        else Toast.makeText(this,"此链接无法在应用内打开",Toast.LENGTH_SHORT).show();
        return true;
    }
    private void hideError(){if(error!=null){stage.removeView(error);error=null;}}
    private void showError(String message){
        if(destroyed||!chatting)return;failed=true;loadSequence++;progress.setVisibility(View.INVISIBLE);subtitle.setText("连接未完成 · Duck.ai");hideError();
        LinearLayout box=column();box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(28),dp(30),dp(28),dp(30));box.setBackgroundColor(PAPER);
        add(box,text("稍等，再试一次。",26,INK,true),0);add(box,text(message,14,MUTED,false),16);
        Button retry=button("重新连接",true);add(box,retry,24);retry.setOnClickListener(v->retry());
        Button home=button("返回助手首页",false);add(box,home,12);home.setOnClickListener(v->foyer());error=box;stage.addView(error,new FrameLayout.LayoutParams(-1,-1));
    }
    private void retry(){if(web!=null){web.stopLoading();failed=false;hideError();web.loadUrl(DuckAiPolicy.HOME);}else{failed=false;openChat();}}
    private void menu(){String[] items=chatting?new String[]{"刷新聊天页","助手首页","关于 Duck.ai"}:new String[]{"打开 Duck.ai","关于 Duck.ai"};new AlertDialog.Builder(this).setTitle("AI 助手").setItems(items,(d,i)->{String item=items[i];if(item.equals("刷新聊天页"))retry();else if(item.equals("助手首页"))foyer();else if(item.equals("打开 Duck.ai"))openChat();else new AlertDialog.Builder(this).setTitle("Duck.ai · 应用内访问").setMessage("本页内嵌 Duck.ai 官方网站，并非知更自建 AI 模型。模型与用量以 Duck.ai 页面为准。聊天记录和删除选项请在 Duck.ai 页面中管理。\n\n本次集成支持文字聊天，未申请麦克风或相机权限。").setPositiveButton("知道了",null).show();}).show();}
    @Override public void onBackPressed(){if(chatting&&web!=null&&web.canGoBack()&&!failed){web.goBack();}else if(chatting)foyer();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("chatting",chatting);super.onSaveInstanceState(state);}
    @Override protected void onPause(){if(web!=null){web.onPause();CookieManager.getInstance().flush();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){destroyed=true;loadSequence++;if(web!=null){stage.removeView(web);web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
