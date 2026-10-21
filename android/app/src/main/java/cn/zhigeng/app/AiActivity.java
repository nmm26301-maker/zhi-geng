package cn.zhigeng.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Native assistant foyer; website entries use the external browser, FCC chat remains native. */
public class AiActivity extends Activity {
    private static final int PAPER=0xfff7f5ef, INK=0xff203b32, MUTED=0xff78837a,
            FOREST=0xff244e40, SAGE=0xffe9eee7, LINE=0xffe4e6de;
    private LinearLayout root, toolbar;
    private FrameLayout stage;
    private ProgressBar progress;
    private TextView subtitle;

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
        foyer();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(color==Color.WHITE)d.setStroke(dp(1),LINE);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(3),1.12f);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:FOREST);b.setBackground(bg(primary?FOREST:SAGE,22));b.setStateListAnimator(null);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinimumHeight(dp(44));b.setMinHeight(dp(44));return b;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}

    private void foyer(){
        subtitle.setText("Duck.ai · 浏览器 / FCC · 原生聊天");progress.setVisibility(View.INVISIBLE);
        stage.removeAllViews();
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(20),dp(16),dp(20),dp(28));scroll.addView(content);stage.addView(scroll);
        TextView eyebrow=text("A LITTLE INSPIRATION  /  聊点新想法",10,FOREST,false);eyebrow.setLetterSpacing(.08f);add(content,eyebrow,0);
        LinearLayout routes=row();Button duckRoute=button("Duck.ai",false),fccRoute=button("FCC 多模型",true);routes.addView(duckRoute,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams fr=new LinearLayout.LayoutParams(0,dp(44),1);fr.leftMargin=dp(10);routes.addView(fccRoute,fr);duckRoute.setOnClickListener(v->openChat());fccRoute.setOnClickListener(v->startActivity(new Intent(this,FccActivity.class)));add(content,routes,16);
        LinearLayout hero=column();hero.setBackground(bg(FOREST,28));hero.setPadding(dp(24),dp(24),dp(24),dp(24));
        add(hero,text("让好奇，\n多走一步。",31,PAPER,true),0);
        add(hero,text("聊一本书，找一部电影，\n也可以从一个小问题开始。",14,SAGE,false),16);
        add(content,hero,18);
        LinearLayout service=column();service.setPadding(dp(20),dp(20),dp(20),dp(20));service.setBackground(bg(Color.WHITE,20));
        LinearLayout serviceTitle=row();serviceTitle.addView(text("Duck.ai",24,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text("外部浏览器",10,FOREST,false);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(bg(SAGE,12));serviceTitle.addView(badge);add(service,serviceTitle,0);
        add(service,text("使用手机浏览器打开 Duck.ai 官方聊天页。\n模型、历史会话与服务选项由 Duck.ai 提供。",12,MUTED,false),12);
        Button start=button("浏览器打开 Duck.ai  →",true);add(service,start,18);start.setOnClickListener(v->openChat());add(content,service,18);
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
    private void openChat(){BrowserLinks.open(this,DuckAiPolicy.HOME);}
    private void menu(){
        String[] items={"打开 Duck.ai","FCC 多模型","关于 Duck.ai"};
        new AlertDialog.Builder(this).setTitle("AI 助手").setItems(items,(d,i)->{
            if(i==0)openChat();
            else if(i==1)startActivity(new Intent(this,FccActivity.class));
            else new AlertDialog.Builder(this).setTitle("Duck.ai · 外部浏览器")
                .setMessage("Duck.ai 官方网页将由手机浏览器打开。模型、登录与聊天记录由 Duck.ai 和浏览器管理。知更不会自动发送书架、账号或阅读记录。FCC 网关聊天仍在知更内使用。")
                .setPositiveButton("知道了",null).show();
        }).show();
    }
}
