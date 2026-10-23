package cn.zhigeng.app;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;

/** A separate native search/category screen. All website visits use the external browser. */
public class ResourceSearchActivity extends ReadingToolActivity {
    private EditText search;
    private LinearLayout recent;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);screen("极映空间");
        hero("JYS66.TOP  /  搜索与导航","漫画，也有新发现。","漫画 · 动漫 · 电子书 · 影视 · 字幕");
        search=input("输入漫画、书名或影视名称");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        if(state!=null)search.setText(state.getString("query",""));add(body,search,22);
        Button go=button("去极映空间搜索",true);add(body,go,12);go.setOnClickListener(v->submit());
        search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){submit();return true;}return false;});
        add(body,text("关键词交给极映空间搜索，下方分类可直接进入原站。",12,MUTED),12);
        recent=column();add(body,recent,12);renderRecent();
        add(body,text("按内容分类",19,INK),22);
        for(String[] category:ResourceSearchLinks.CATEGORIES) {
            LinearLayout card=column();card.setPadding(dp(18),dp(14),dp(18),dp(16));card.setBackground(shape(0xffffffff,20));
            Button open=button(category[0],category[1].equals("manhua.html"));
            open.setOnClickListener(v->BrowserLinks.open(this,"https://jys66.top/"+category[1]));
            add(card,open,0);add(card,text(category[2],12,MUTED),8);add(body,card,12);
        }
        add(body,text("访问网站与搜索结果由手机浏览器处理；最近搜索仅保存在本机。",12,MUTED),20);
    }
    private JSONArray history() {
        try{return new JSONArray(getPreferences(MODE_PRIVATE).getString("recent","[]"));}catch(Exception ignored){return new JSONArray();}
    }
    private void submit() {
        try {
            String term=ResourceSearchLinks.clean(search.getText().toString()),url=ResourceSearchLinks.search(term);
            JSONArray updated=new JSONArray().put(term),old=history();
            for(int i=0;i<old.length()&&updated.length()<6;i++)if(!term.equals(old.optString(i)))updated.put(old.optString(i));
            getPreferences(MODE_PRIVATE).edit().putString("recent",updated.toString()).apply();renderRecent();BrowserLinks.open(this,url);
        }catch(Exception e){search.setError(e.getMessage());}
    }
    private void renderRecent() {
        recent.removeAllViews();JSONArray list=history();if(list.length()==0)return;
        add(recent,text("最近搜索",12,MUTED),0);
        for(int i=0;i<list.length();i++){String term=list.optString(i);Button item=button(term,false);item.setOnClickListener(v->search.setText(term));add(recent,item,6);}
        Button clear=button("清空最近搜索",false);clear.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().remove("recent").apply();renderRecent();});add(recent,clear,6);
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("query",search.getText().toString());}
}
