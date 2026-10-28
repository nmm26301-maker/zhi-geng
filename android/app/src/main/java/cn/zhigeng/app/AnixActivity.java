package cn.zhigeng.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.jsoup.Jsoup;

/** Native catalogue and local watchlist inspired by Anix 1.3.0; desktop player is not bundled. */
public class AnixActivity extends ReadingToolActivity {
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private AnimeCatalogClient client;
    private SharedPreferences preferences;
    private JSONObject watchlist=new JSONObject();
    private boolean local,storageBroken,hasNext;
    private volatile int generation;
    private int page=1,localPage=1;
    private String term="",localQuery="";
    private int filterIndex,sortIndex;
    private LinearLayout localControls;
    private Spinner filter,sort;
    private EditText search;
    private TextView status,pageLabel;
    private LinearLayout results;
    private Button previous,next,retry;
    private JSONArray shown=new JSONArray();
    protected AnimeCatalogClient createClient()throws Exception{return new AnimeCatalogClient();}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=getSharedPreferences("anix_watchlist_v1",MODE_PRIVATE);
        try{watchlist=new JSONObject(preferences.getString("entries","{}"));}catch(Exception e){storageBroken=true;}
        local=state==null?preferences.getBoolean("local",false):state.getBoolean("local");
        localQuery=state==null?preferences.getString("localQuery",""):state.getString("localQuery","");
        filterIndex=Math.max(0,Math.min(AnimeWatchlist.STATES.length,preferences.getInt("filter",0)));
        sortIndex=Math.max(0,Math.min(1,preferences.getInt("sort",0)));
        if(state!=null){term=state.getString("term","");page=Math.max(1,state.getInt("page",1));localPage=Math.max(1,state.getInt("localPage",1));}
        try{client=createClient();}catch(Exception e){finish();return;}
        screen("Anix");hero("ANIX  /  知更动漫","搜动漫，\n接着追。","参考 Anix v1.3.0 的目录与追番设计 · 数据来自 AniList");
        LinearLayout tabs=row();Button discover=action("热门动漫",false),mine=action("我的追番",false);
        tabs.addView(discover,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(0,-2,1);gap.leftMargin=dp(8);tabs.addView(mine,gap);add(body,tabs,16);
        discover.setOnClickListener(v->{if(local)localQuery=search.getText().toString().trim();local=false;rememberMode();search.setText(term);load(1);});
        mine.setOnClickListener(v->{local=true;rememberMode();++generation;client.cancel();search.setText(localQuery);showLocal(localQuery);});
        add(body,text("搜索动漫名称",14,INK),16);search=input("输入动漫名称");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(local?localQuery:term);add(body,search,8);
        Button find=action("搜索动漫",true);add(body,find,10);find.setOnClickListener(v->submit());
        search.setOnEditorActionListener((v,what,event)->{if(what==EditorInfo.IME_ACTION_SEARCH){submit();return true;}return false;});
        localControls=column();add(localControls,text("追番状态筛选",14,INK),0);
        String[] states=new String[AnimeWatchlist.STATES.length+1];states[0]="全部状态";System.arraycopy(AnimeWatchlist.STATES,0,states,1,AnimeWatchlist.STATES.length);
        filter=selection("追番状态筛选",states,filterIndex);add(localControls,filter,4);
        add(localControls,text("追番排序",14,INK),12);sort=selection("追番排序",new String[]{"名称排序","最近更新"},sortIndex);add(localControls,sort,4);
        status=text("",15,INK);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,status,16);
        retry=action("重试动漫目录",false);retry.setVisibility(View.GONE);retry.setOnClickListener(v->load(page));add(body,retry,8);
        add(body,localControls,12);results=column();add(body,results,12);pageLabel=text("",13,INK);add(body,pageLabel,12);
        LinearLayout pages=row();previous=action("动漫上一页",false);next=action("动漫下一页",false);
        pages.addView(previous,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams ng=new LinearLayout.LayoutParams(0,-2,1);ng.leftMargin=dp(8);pages.addView(next,ng);add(body,pages,8);
        previous.setOnClickListener(v->{if(local)showLocal(search.getText().toString().trim(),localPage-1);else if(page>1)load(page-1);});
        next.setOnClickListener(v->{if(local)showLocal(search.getText().toString().trim(),localPage+1);else if(hasNext)load(page+1);});
        Button extensions=action("社区视频源扩展",false),accounts=action("追番账号与同步",false);add(body,extensions,12);add(body,accounts,10);
        extensions.setOnClickListener(v->startActivity(new android.content.Intent(this,AnimeExtensionsActivity.class)));accounts.setOnClickListener(v->startActivity(new android.content.Intent(this,AnimeTrackerActivity.class)));
        Button videos=action("本地视频与离线缓存",false);add(body,videos,16);videos.setOnClickListener(v->startActivity(new android.content.Intent(this,AnimeVideoLibraryActivity.class)));
        Button official=action("Anix 官方项目与使用说明",false);add(body,official,24);official.setOnClickListener(v->official());
        add(body,text("追番状态与已看集数保存在本机，可离线查看。可安装社区视频源扩展搜索剧集，或连接 AniList / MyAnimeList，逐条确认导入与上传追番。",14,INK),16);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int index,long id){if(index!=filterIndex){filterIndex=index;rememberMode();if(local)showLocal(search.getText().toString().trim());}}
        });
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int index,long id){if(index!=sortIndex){sortIndex=index;rememberMode();if(local)showLocal(search.getText().toString().trim());}}
        });
        if(local)showLocal(localQuery,localPage);else load(page);
    }
    private Spinner selection(String label,String[] options,int selected) {
        Spinner spinner=new Spinner(this);spinner.setContentDescription(label);spinner.setMinimumHeight(dp(48));
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));spinner.setSelection(selected);return spinner;
    }
    private Button action(String title,boolean primary) {
        Button button=button(title,primary);int color=((primary?Color.WHITE:GREEN)&0xffffff)|0x33000000;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(color),shape(primary?GREEN:PALE,18),null));return button;
    }
    private void enabled(Button button,boolean enabled){button.setEnabled(enabled);button.setAlpha(enabled?1f:.45f);}
    private void rememberMode(){preferences.edit().putBoolean("local",local).putString("localQuery",localQuery).putInt("filter",filterIndex).putInt("sort",sortIndex).apply();}
    private void submit() {
        String query=search.getText().toString().trim();
        if(query.codePointCount(0,query.length())>100){search.setError("搜索词请控制在100字以内");return;}
        if(local)showLocal(query);else{term=query;load(1);}
    }
    private boolean current(int request){return request==generation&&!isFinishing()&&!isDestroyed()&&!local;}
    @Override protected void onResume(){super.onResume();if(preferences!=null)try{watchlist=new JSONObject(preferences.getString("entries","{}"));if(local&&results!=null)showLocal(localQuery,localPage);}catch(Exception e){storageBroken=true;if(status!=null)status.setText("本地追番暂不可读，已保留原记录");}}
    private void load(int requestedPage) {
        localControls.setVisibility(View.GONE);local=false;page=requestedPage;int request=++generation;String query=term;client.cancel();
        retry.setVisibility(View.GONE);enabled(previous,false);enabled(next,false);results.removeAllViews();pageLabel.setText("");status.setText("正在读取动漫目录…");
        jobs.execute(()->{if(!current(request))return;try{
            JSONObject data=client.search(query,requestedPage);
            runOnUiThread(()->{if(current(request))showPage(data.optJSONArray("media"),requestedPage,data.optJSONObject("pageInfo").optBoolean("hasNextPage"));});
        }catch(Exception e){runOnUiThread(()->{if(current(request)){
            status.setText(e instanceof java.io.IOException?"动漫目录连接失败，请检查网络；本地追番仍可使用":Objects.toString(e.getMessage(),"动漫目录暂不可用"));retry.setVisibility(View.VISIBLE);}});}});
    }
    private void showPage(JSONArray items,int requestedPage,boolean more) {
        shown=items;hasNext=more;page=requestedPage;results.removeAllViews();
        status.setText(items.length()==0?"没有匹配的动漫，试试其他名称":"动漫目录 · 第 "+page+" 页 · "+items.length()+" 部");
        for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optLong("id")>0&&!item.optBoolean("isAdult"))card(item);}
        pageLabel.setText("数据来源 AniList · 每页最多20部");enabled(previous,page>1);enabled(next,more);
    }
    private void showLocal(String query){showLocal(query,1);}
    private void showLocal(String query,int requestedPage) {
        results.removeAllViews();retry.setVisibility(View.GONE);pageLabel.setText("");enabled(previous,false);enabled(next,false);
        localControls.setVisibility(View.VISIBLE);localQuery=query;rememberMode();
        ArrayList<JSONObject> items=AnimeWatchlist.select(watchlist,query,filterIndex==0?"":AnimeWatchlist.STATES[filterIndex-1],sortIndex==1);
        status.setText(storageBroken?"本地追番数据暂不可读，已暂停写入以保留原数据":"本地追番 · "+items.size()+" 部 · 可离线查看");
        localPage=Math.max(1,Math.min(requestedPage,Math.max(1,(items.size()+19)/20)));
        for(int i=(localPage-1)*20;i<Math.min(localPage*20,items.size());i++)card(items.get(i));
        pageLabel.setText(items.isEmpty()?"":"本地追番 · 第 "+localPage+" 页");enabled(previous,localPage>1);enabled(next,localPage*20<items.size());
        if(items.isEmpty())add(results,text("没有匹配的追番记录。可切换「全部状态」、清空关键词，或到热门动漫加入追番。",16,INK),16);
    }
    private String summary(JSONObject media) {
        String year=media.optInt("seasonYear",0)>0?Integer.toString(media.optInt("seasonYear")):"年份待定";
        int episodes=media.optInt("episodes",0);return year+" · "+(episodes>0?episodes+" 集":"总集数待定")+" · "+media.optString("format","动漫");
    }
    private void card(JSONObject media) {
        LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(shape(Color.WHITE,20));
        add(card,text(AnimeCatalogClient.title(media),20,INK),0);add(card,text(summary(media),14,INK),8);
        JSONObject saved=watchlist.optJSONObject(Long.toString(media.optLong("id")));
        if(saved!=null)add(card,text(saved.optString("state")+" · 已看 "+saved.optInt("progress")+" 集",15,GREEN),8);
        JSONObject airing=media.optJSONObject("nextAiringEpisode");
        if(airing!=null&&airing.optLong("airingAt")>0)add(card,text((local?"上次保存：":"")+"第 "+airing.optInt("episode")+" 集 · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(airing.optLong("airingAt")*1000)),13,INK),8);
        Button episode=action("播放与下载剧集",false);add(card,episode,10);episode.setOnClickListener(v->AnimeVideoLibraryActivity.open(this,media));
        Button source=action("从视频源搜索此动漫",false);add(card,source,10);source.setOnClickListener(v->startActivity(new android.content.Intent(this,AnimeExtensionsActivity.class).putExtra("anime",media.toString())));

        Button detail=action("动漫详情",false),track=action(saved==null?"加入追番":"更新追番",true);add(card,detail,12);add(card,track,8);
        detail.setOnClickListener(v->details(media));track.setOnClickListener(v->edit(media));
        if(AnimeWatchlist.canAdvance(saved)) {
            Button advance=action("看完一集 +1",false);advance.setContentDescription(AnimeCatalogClient.title(media)+"，看完一集");add(card,advance,8);
            advance.setOnClickListener(v->{try{if(save(AnimeWatchlist.advance(watchlist,media.optLong("id")))){
                redraw();Toast.makeText(this,"已记录看完一集",Toast.LENGTH_SHORT).show();
            }}catch(Exception e){status.setText(Objects.toString(e.getMessage(),"追番未能保存，请重试"));}});
        }
        add(results,card,12);
    }
    private void details(JSONObject media) {
        String description=Jsoup.parse(media.optString("description","")).text();if(description.isEmpty())description=local?"暂无离线简介，可在联网目录中重新保存追番后查看":"暂无简介";
        if(description.codePointCount(0,description.length())>4000)description=description.substring(0,description.offsetByCodePoints(0,4000))+"…";
        new AlertDialog.Builder(this).setTitle(AnimeCatalogClient.title(media)).setMessage(summary(media)+"\n\n"+description)
            .setNegativeButton("关闭",null).setPositiveButton("查看 AniList 条目 ↗",(d,w)->BrowserLinks.open(this,"https://anilist.co/anime/"+media.optLong("id"))).show();
    }
    private void edit(JSONObject media) {
        if(storageBroken){status.setText("本地追番数据暂不可读，已暂停写入以保留原数据");return;}
        JSONObject saved=watchlist.optJSONObject(Long.toString(media.optLong("id")));
        LinearLayout content=column();content.setPadding(dp(22),dp(12),dp(22),dp(12));add(content,text("追番状态",15,INK),0);
        Spinner state=new Spinner(this);state.setContentDescription("选择追番状态");state.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,AnimeWatchlist.STATES));add(content,state,8);
        if(saved!=null)for(int i=0;i<AnimeWatchlist.STATES.length;i++)if(AnimeWatchlist.STATES[i].equals(saved.optString("state")))state.setSelection(i);
        add(content,text("已看集数",15,INK),12);EditText progress=input("填写已看集数");progress.setInputType(InputType.TYPE_CLASS_NUMBER);progress.setText(Integer.toString(saved==null?0:saved.optInt("progress")));add(content,progress,8);
        TextView validation=text("",14,INK);validation.setVisibility(View.GONE);validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(content,validation,8);
        ScrollView form=new ScrollView(this);form.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(AnimeCatalogClient.title(media)).setView(form).setNegativeButton("取消",null)
            .setPositiveButton("保存追番",null).setNeutralButton(saved==null?null:"移出追番",(d,w)->remove(media)).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            int count=Integer.parseInt(progress.getText().toString().trim());String value=state.getSelectedItem().toString();
            if(value.equals("已看完")&&media.optInt("episodes",0)>0)count=media.optInt("episodes");
            if(save(AnimeWatchlist.update(watchlist,media,value,count))){dialog.dismiss();redraw();Toast.makeText(this,"追番已保存到本机",Toast.LENGTH_SHORT).show();}
        }catch(Exception e){String message=e instanceof NumberFormatException?"请填写有效集数":e.getMessage();progress.setError(message);validation.setText(message);validation.setVisibility(View.VISIBLE);form.post(()->form.fullScroll(View.FOCUS_DOWN));}}));dialog.show();
    }
    private boolean save(JSONObject updated) {
        if(!preferences.edit().putString("entries",updated.toString()).commit()){status.setText("追番未能保存，请重试");return false;}
        watchlist=updated;return true;
    }
    private void remove(JSONObject media) {
        new AlertDialog.Builder(this).setTitle("移出追番？").setMessage("将移除这部动漫在本机的追番状态与已看集数。")
            .setNegativeButton("保留",null).setPositiveButton("移出",(d,w)->{try{if(save(AnimeWatchlist.remove(watchlist,media.optLong("id"))))redraw();}catch(Exception e){status.setText("暂时无法移出追番，请重试");}}).show();
    }
    private void redraw(){if(local)showLocal(search.getText().toString().trim(),localPage);else showPage(shown,page,hasNext);}
    private void official() {
        new AlertDialog.Builder(this).setTitle("Anix v1.3.0 · Linux 桌面版").setMessage("官方没有 Android 安装包。本页的动漫目录与追番由知更原生实现，本地与已关联的视频使用知更原生播放器；视频扩展由你导入到 Android QuickJS 运行时，未打包桌面播放器。")
            .setPositiveButton("进入 Anix 官网 ↗",(d,w)->BrowserLinks.open(this,"https://anix-linux.vercel.app"))
            .setNeutralButton("查看 v1.3.0 官方发布 ↗",(d,w)->BrowserLinks.open(this,"https://github.com/MaidLover69/Anix/releases/tag/1.3"))
            .setNegativeButton("关闭",null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("local",local);state.putString("term",term);state.putInt("page",page);state.putInt("localPage",localPage);state.putString("localQuery",local?search.getText().toString().trim():localQuery);}
    @Override protected void onDestroy(){++generation;if(client!=null)client.cancel();jobs.shutdownNow();super.onDestroy();}
}
