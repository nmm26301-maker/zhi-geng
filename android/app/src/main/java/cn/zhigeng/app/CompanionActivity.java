package cn.zhigeng.app;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native entry points, without copying messages, passwords or library data to third parties. */
public class CompanionActivity extends Activity {
    private final int BG=0xfff7f5ef,INK=0xff203b32,MUTED=0xff78837a,GREEN=0xff244e40,PALE=0xffe9eee7;
    private LinearLayout body,cards,filters;private EditText search;private TextView count;
    private String mode="kanju",filter="all",query="";private SharedPreferences prefs;
    private static final String[][] SITES={
        {"FreePhoneNum","https://freephonenum.com/","美国、加拿大等地区的公开临时号码；部分历史号码已归档。"},
        {"SMSToMe","https://smstome.com/","按国家地区浏览公开号码与历史短信。"},
        {"Receive-SMSS","https://receive-smss.com/","按地区查看公开号码和历史收件箱。"},
        {"Temporary Phone Number","https://temporary-phone-number.com/","临时号码与公开收件箱原站入口。"},
        {"Temp Number","https://temp-number.com/","公开临时号码和历史短信。"},
        {"Receive SMS Free","https://receive-sms-free.cc/","部分验证码可能被服务方过滤。"}
    };
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout col(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1.12f);return t;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private Button button(String title,boolean strong){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setStateListAnimator(null);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(13),dp(9),dp(13),dp(9));b.setTextColor(strong?0xffffffff:GREEN);b.setBackground(shape(strong?GREEN:PALE,20));return b;}
    private EditText input(String hint){EditText v=new EditText(this);v.setSingleLine();v.setTextSize(15);v.setTextColor(INK);v.setHintTextColor(MUTED);v.setHint(hint);v.setPadding(dp(16),dp(12),dp(16),dp(12));v.setBackground(shape(0xffffffff,15));return v;}
    @Override public void onCreate(Bundle state){super.onCreate(state);mode=getIntent().getStringExtra("mode");if(mode==null)mode="kanju";prefs=getSharedPreferences("companion",MODE_PRIVATE);filter=state==null?"all":state.getString("filter","all");query=state==null?getIntent().getStringExtra("query"):state.getString("query","");if(query==null)query="";
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=col();root.setBackgroundColor(BG);LinearLayout bar=row();bar.setPadding(dp(16),dp(10),dp(20),dp(10));Button back=button("‹ 返回",false);back.setOnClickListener(v->finish());bar.addView(back);TextView title=text(mode.equals("sms")?"接码导航":"看剧 AI",19,INK);title.setPadding(dp(18),0,0,0);bar.addView(title);root.addView(bar);ScrollView scroll=new ScrollView(this);body=col();body.setPadding(dp(22),dp(8),dp(22),dp(32));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);if(mode.equals("sms"))sms();else kanju();
    }
    private void hero(String overline,String title,String detail){LinearLayout box=col();box.setPadding(dp(22),dp(24),dp(22),dp(24));box.setBackground(shape(GREEN,26));add(box,text(overline,10,PALE),0);TextView h=text(title,28,BG);h.setTypeface(null,Typeface.BOLD);add(box,h,12);add(box,text(detail,12,PALE),12);add(body,box,10);}
    private void kanju(){hero("KANJU.AI  /  官方网页","好故事，\n接着看。","从片名出发，在知更内打开看剧 AI。");add(body,text("电影 · 剧集 · 动漫 · 综艺",13,MUTED),24);search=input("输入想看的片名…");search.setText(query);add(body,search,10);Button find=button("在知更内找片",true);add(body,find,14);find.setOnClickListener(v->{String name=search.getText().toString().replaceAll("[\\p{Cntrl}]"," ").trim();if(name.isEmpty()){search.setError("请填写片名");return;}if(name.length()>100)name=name.substring(0,100);open("https://kanju.ai/session/new?prompt="+Catalog.enc(name),"看剧 AI");});Button home=button("打开看剧 AI 官网",false);add(body,home,10);home.setOnClickListener(v->open("https://kanju.ai/","看剧 AI"));Button annual=button("回到年度榜单",false);add(body,annual,10);annual.setOnClickListener(v->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("open_annual",true));finish();});add(body,text("只有点击后才连接 kanju.ai，搜索时会发送填写的片名。账号、访问线路与播放权限由对方提供；不会附带知更书架、TMDB 凭证或聊天记录。",12,MUTED),22);}
    private Set<String> values(String key){return new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));}
    private void toggle(String key,String url){Set<String> data=values(key);if(!data.add(url))data.remove(url);if(prefs.edit().putStringSet(key,data).commit())renderSites();else Toast.makeText(this,"保存失败，请检查设备空间",Toast.LENGTH_SHORT).show();}
    private void sms(){hero("6 SITES  /  你的接码站","常用入口，\n收在一处。","原站导航 · 搜索 · 收藏 · 隐藏");search=input("搜索网站名称或网址…");search.setContentDescription("搜索接码网站");search.setText(query);add(body,search,22);HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);filters=row();h.addView(filters);add(body,h,12);count=text("",11,MUTED);add(body,count,14);cards=col();add(body,cards,4);add(body,text("公开号码的短信可能被任何人查看，请勿用于重要账号或敏感验证。收藏和隐藏仅保存在本机；知更不读取、不缓存短信与验证码。",12,MUTED),22);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();renderSites();}public void afterTextChanged(Editable e){}});renderSites();}
    private void renderSites(){filters.removeAllViews();String[] labels={"全部网站","我的收藏","已隐藏"},keys={"all","fav","hidden"};for(int i=0;i<keys.length;i++){final String key=keys[i];Button b=button(labels[i],filter.equals(key));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.rightMargin=dp(8);filters.addView(b,lp);b.setOnClickListener(v->{filter=key;renderSites();});}
        cards.removeAllViews();Set<String> favorites=values("favorites"),hidden=values("hidden");String term=query.trim().toLowerCase(Locale.ROOT);int total=0;
        for(String[] site:SITES){boolean hide=hidden.contains(site[1]);if(filter.equals("hidden")?!hide:hide)continue;if(filter.equals("fav")&&!favorites.contains(site[1]))continue;if(!(site[0]+site[1]+site[2]).toLowerCase(Locale.ROOT).contains(term))continue;total++;LinearLayout card=col();card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(shape(0xffffffff,22));LinearLayout top=row();TextView name=text(site[0],18,INK);name.setTypeface(null,Typeface.BOLD);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button star=button(favorites.contains(site[1])?"★":"☆",false);star.setContentDescription((favorites.contains(site[1])?"取消收藏":"收藏")+site[0]);star.setOnClickListener(v->toggle("favorites",site[1]));top.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));add(card,top,0);add(card,text(site[1].replace("https://",""),11,MUTED),6);add(card,text(site[2],13,MUTED),12);LinearLayout actions=row();Button visit=button("访问原站",true),remove=button(hide?"恢复显示":"隐藏此网站",false);visit.setContentDescription("访问 "+site[0]);visit.setOnClickListener(v->open(site[1],site[0]));remove.setContentDescription((hide?"恢复":"隐藏")+site[0]);remove.setOnClickListener(v->toggle("hidden",site[1]));actions.addView(visit,new LinearLayout.LayoutParams(0,dp(46),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.leftMargin=dp(10);actions.addView(remove,lp);add(card,actions,16);add(cards,card,12);}
        count.setText("显示 "+total+" / 6 个网站 · 导航名单：2026-10-19");if(total==0)add(cards,text("暂无匹配网站，试试更换搜索词或筛选。",15,MUTED),22);
    }
    private void open(String url,String title){startActivity(new Intent(this,InAppBrowserActivity.class).putExtra("url",url).putExtra("title",title));}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("filter",filter);state.putString("query",search==null?query:search.getText().toString());}
}
