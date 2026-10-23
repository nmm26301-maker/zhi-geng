package cn.zhigeng.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared native typography and spacing for the two independent reading tools. */
abstract class ReadingToolActivity extends Activity {
    final int BG=0xfff7f5ef, INK=0xff203b32, MUTED=0xff78837a, GREEN=0xff244e40, PALE=0xffe9eee7;
    LinearLayout body;
    int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(1); return v; }
    LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    TextView text(String value,int size,int color) { TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1.12f);return v; }
    void add(LinearLayout parent,View child,int top) { LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp); }
    Button button(String label,boolean primary) {
        Button v=new Button(this);v.setText(label);v.setTextSize(14);v.setAllCaps(false);v.setStateListAnimator(null);
        v.setTextColor(primary?Color.WHITE:GREEN);v.setBackground(shape(primary?GREEN:PALE,18));
        v.setPadding(dp(16),dp(10),dp(16),dp(10));v.setMinHeight(dp(48));return v;
    }
    EditText input(String hint) {
        EditText v=new EditText(this);v.setSingleLine();v.setTextSize(15);v.setTextColor(INK);v.setHintTextColor(MUTED);
        v.setHint(hint);v.setContentDescription(hint);v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setBackground(shape(Color.WHITE,14));return v;
    }
    void screen(String title) {
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column();root.setBackgroundColor(BG);LinearLayout bar=row();bar.setPadding(dp(18),dp(12),dp(18),dp(10));
        Button back=button("‹ 返回",false);back.setOnClickListener(v->finish());bar.addView(back);
        TextView name=text(title,20,INK);name.setPadding(dp(16),0,0,0);name.setTypeface(null,Typeface.BOLD);bar.addView(name);root.addView(bar);
        ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(22),dp(8),dp(22),dp(32));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    void hero(String overline,String title,String description) {
        LinearLayout card=column();card.setPadding(dp(22),dp(24),dp(22),dp(24));card.setBackground(shape(GREEN,26));
        add(card,text(overline,10,PALE),0);TextView name=text(title,28,Color.WHITE);name.setTypeface(null,Typeface.BOLD);
        add(card,name,10);add(card,text(description,13,PALE),12);add(body,card,8);
    }
}
