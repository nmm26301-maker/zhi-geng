package cn.zhigeng.app;

import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native account connection and explicit, two-direction per-title synchronization. */
public class AnimeTrackerActivity extends ReadingToolActivity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());
    private Spinner services;private TextView status,profile;private LinearLayout rows;private EditText clientId;private Button login,disconnect,remote,local,previous,next;
    private String service=AnimeTrackerAuth.ANI;private int page=1,generation;private boolean destroyed,busy,remoteMode=true,more;private AnimeTrackerClient client;
    protected AnimeTrackerClient createClient(String service)throws Exception{return new AnimeTrackerClient(this,service);}
    @Override public void onCreate(Bundle state){super.onCreate(state);screen("追番账号与同步");add(body,text("连接 AniList / MyAnimeList",23,INK),12);
        add(body,text("授权在手机浏览器完成。查看远端追番，或逐条确认导入、上传；断开连接保留本地记录，不删除远端条目。",14,INK),10);
        services=new Spinner(this);services.setContentDescription("追番账号平台");services.setMinimumHeight(dp(48));services.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"AniList","MyAnimeList"}));add(body,services,10);
        profile=text("",16,INK);add(body,profile,14);Button config=button("授权应用设置",false);add(body,config,10);config.setOnClickListener(v->configuration());
        login=button("连接账号 · 在浏览器授权",true);disconnect=button("断开当前平台账号",false);add(body,login,10);add(body,disconnect,10);login.setOnClickListener(v->connect());disconnect.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("断开账号？").setMessage("只移除这台设备保存的登录凭证，本地和远端追番保留。可在服务网站撤销授权。")
            .setNegativeButton("保留",null).setPositiveButton("断开",(d,w)->task(()->{client.disconnect();return null;},value->{rows.removeAllViews();refreshProfile();status.setText("已断开，本地与远端追番保留");})).show());
        Button cancel=button("取消待完成的授权",false);add(body,cancel,8);cancel.setOnClickListener(v->{try{new SecretStore(this).put("tracker.pending","");status.setText("已取消，旧的授权回调不再接受");}catch(Exception e){error(e);}});
        LinearLayout modes=row();remote=button("远端追番",false);local=button("本地追番 · 上传",false);modes.addView(remote,new LinearLayout.LayoutParams(0,-2,1));modes.addView(local,new LinearLayout.LayoutParams(0,-2,1));add(body,modes,18);
        remote.setOnClickListener(v->{remoteMode=true;page=1;load();});local.setOnClickListener(v->{remoteMode=false;page=1;load();});
        status=text("",14,INK);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,status,14);rows=column();add(body,rows,10);
        LinearLayout nav=row();previous=button("同步上一页",false);next=button("同步下一页",false);nav.addView(previous,new LinearLayout.LayoutParams(0,-2,1));nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));add(body,nav,12);previous.setOnClickListener(v->{if(page>1){page--;load();}});next.setOnClickListener(v->{if(more){page++;load();}});
        services.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int position,long id){String chosen=position==0?AnimeTrackerAuth.ANI:AnimeTrackerAuth.MAL;if(busy||chosen.equals(service))return;generation++;service=chosen;page=1;rows.removeAllViews();try{client=createClient(service);refreshProfile();status.setText("选择远端列表或本地条目开始同步");}catch(Exception e){error(e);}}});
        try{client=createClient(service);refreshProfile();}catch(Exception e){error(e);}handleCallback(getIntent());
    }
    private void refreshProfile()throws Exception{JSONObject account=client.account();profile.setText(account==null?"尚未连接当前平台":"已连接 · "+account.getString("name"));disconnect.setEnabled(account!=null&&!busy);}
    private void configuration(){
        LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(12));add(box,text("使用为知更注册的公开 Client ID，不填写 Client Secret。AniList 使用移动端 implicit grant；MyAnimeList 使用 public/native client 与 PKCE。",14,INK),0);
        TextView redirect=text("回调地址：cn.zhigeng.app://oauth/"+service,14,INK);redirect.setTextIsSelectable(true);add(box,redirect,12);add(box,text("公开 Client ID",15,INK),12);EditText id=input("公开 Client ID");id.setText(getSharedPreferences("anime_tracker_setup",MODE_PRIVATE).getString(service,""));add(box,id,6);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("授权应用设置").setView(box).setNegativeButton("取消",null).setNeutralButton("打开注册页面",(d,w)->BrowserLinks.open(this,service.equals(AnimeTrackerAuth.ANI)?"https://anilist.co/settings/developer":"https://myanimelist.net/apiconfig" )).setPositiveButton("保存设置",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{AnimeTrackerAuth.begin(service,id.getText().toString().trim(),System.currentTimeMillis());if(!getSharedPreferences("anime_tracker_setup",MODE_PRIVATE).edit().putString(service,id.getText().toString().trim()).commit())throw new Exception("设置未能保存");dialog.dismiss();status.setText("授权应用已配置，可连接账号");}catch(Exception e){id.setError(e.getMessage());}}));dialog.show();
    }
    private void connect(){try{String id=getSharedPreferences("anime_tracker_setup",MODE_PRIVATE).getString(service,"");if(id.isEmpty()){configuration();return;}JSONObject pending=AnimeTrackerAuth.begin(service,id,System.currentTimeMillis());new SecretStore(this).put("tracker.pending",pending.toString());BrowserLinks.open(this,AnimeTrackerAuth.authorize(pending));status.setText("请在浏览器中确认授权，完成后返回知更");}catch(Exception e){error(e);}}
    private void handleCallback(Intent intent){String raw=intent==null||intent.getData()==null?null:intent.getData().toString();setIntent(new Intent(this,AnimeTrackerActivity.class));if(raw==null)return;
        try{String saved=new SecretStore(this).get("tracker.pending");if(saved.isEmpty())throw new Exception("没有待完成的授权，请重新连接");JSONObject pending=new JSONObject(saved);JSONObject answer=AnimeTrackerAuth.answer(pending,raw,System.currentTimeMillis());String provider=pending.getString("service");service=provider;services.setSelection(provider.equals(AnimeTrackerAuth.ANI)?0:1);client=createClient(provider);
            AnimeTrackerClient target=client;task(()->{target.complete(pending,answer);return null;},value->{try{refreshProfile();status.setText("账号已连接，可读取远端追番");}catch(Exception e){error(e);}});
        }catch(Exception e){error(e);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);handleCallback(intent);}
    interface Work{Object run()throws Exception;}interface Done{void run(Object value)throws Exception;}
    private void task(Work work,Done done){if(busy)return;busy=true;services.setEnabled(false);for(Button b:new Button[]{login,disconnect,remote,local,previous,next})b.setEnabled(false);status.setText("正在连接追番服务…");int current=generation;
        io.execute(()->{Object value=null;Exception failure=null;try{value=work.run();}catch(Exception e){failure=e;}final Object result=value;final Exception error=failure;ui.post(()->{if(destroyed)return;busy=false;services.setEnabled(true);for(Button b:new Button[]{login,remote,local})b.setEnabled(true);previous.setEnabled(page>1);next.setEnabled(more);try{refreshProfile();}catch(Exception ignored){}if(current!=generation)return;try{if(error!=null)throw error;done.run(result);}catch(Exception e){error(e);}});});
    }
    private void load(){AnimeTrackerClient target=client;if(remoteMode)task(()->target.page(page),value->{JSONObject result=(JSONObject)value;more=result.getBoolean("more");show(result.getJSONArray("rows"),true,result.getLong("accountId"));});
        else try{JSONObject all=new JSONObject(getSharedPreferences("anix_watchlist_v1",MODE_PRIVATE).getString("entries","{}"));ArrayList<JSONObject> media=AnimeWatchlist.select(all,"","",false);more=page*20<media.size();JSONArray list=new JSONArray();for(int i=(page-1)*20;i<Math.min(page*20,media.size());i++)list.put(all.getJSONObject(Long.toString(media.get(i).getLong("id"))));JSONObject a=client.account();show(list,false,a==null?-1:a.getLong("userId"));}catch(Exception e){error(e);}
    }
    private void show(JSONArray list,boolean fromRemote,long accountId)throws Exception {
        rows.removeAllViews();status.setText((fromRemote?"远端":"本地")+"追番 · 第 "+page+" 页 · "+list.length()+" 部");for(int i=0;i<list.length();i++){JSONObject saved=list.getJSONObject(i),m=saved.getJSONObject("media");LinearLayout card=column();card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(shape(android.graphics.Color.WHITE,16));add(card,text(AnimeCatalogClient.title(m),18,INK),0);add(card,text(saved.getString("state")+" · 已看 "+saved.getInt("progress")+" 集",14,INK),8);Button action=button(fromRemote?"导入此条到本地":"上传此条到账号",false);add(card,action,10);add(rows,card,12);action.setOnClickListener(v->{if(fromRemote)importOne(saved,accountId);else uploadOne(saved,accountId);});}
        previous.setEnabled(page>1&&!busy);next.setEnabled(more&&!busy);
    }
    private void importOne(JSONObject saved,long accountId){AnimeTrackerClient target=client;task(()->target.resolve(saved),value->{JSONObject resolved=(JSONObject)value,media=resolved.getJSONObject("media");JSONObject before=new JSONObject(getSharedPreferences("anix_watchlist_v1",MODE_PRIVATE).getString("entries","{}"));JSONObject old=before.optJSONObject(Long.toString(media.getLong("id")));
        String detail=AnimeCatalogClient.title(media)+"\n远端："+resolved.getString("state")+" / "+resolved.getInt("progress")+" 集"+(old==null?"\n本地尚无记录":"\n本地："+old.optString("state")+" / "+old.optInt("progress")+" 集");
        new AlertDialog.Builder(this).setTitle("将这条远端记录保存到本地？").setMessage(detail+"\n只替换这条记录，其他追番保留。").setNegativeButton("取消",null).setPositiveButton("确认导入",(d,w)->{try{JSONObject a=target.account();if(a==null||a.getLong("userId")!=accountId)throw new Exception("账号已变化，请刷新列表");JSONObject latest=new JSONObject(getSharedPreferences("anix_watchlist_v1",MODE_PRIVATE).getString("entries","{}"));JSONObject updated=AnimeTrackerClient.importEntry(latest,resolved);if(!getSharedPreferences("anix_watchlist_v1",MODE_PRIVATE).edit().putString("entries",updated.toString()).commit())throw new Exception("本地记录未能保存");status.setText("已导入到本地追番");}catch(Exception e){error(e);}}).show();});}
    private void uploadOne(JSONObject saved,long accountId){if(accountId<0){status.setText("请先连接当前平台账号");return;}try{JSONObject a=client.account();if(a==null)throw new Exception("请先连接账号");AnimeTrackerClient target=client;String name=a.getString("name");
        new AlertDialog.Builder(this).setTitle("同步这条记录到 "+name+"？").setMessage(AnimeCatalogClient.title(saved.getJSONObject("media"))+"\n将该账号中这部动漫的状态、已看集数改为：\n"+saved.getString("state")+" / "+saved.getInt("progress")+" 集\n远端现有值会被这两个本地值替换，评分和其他条目保留。")
            .setNegativeButton("取消",null).setPositiveButton("确认上传",(d,w)->task(()->target.push(saved,accountId),value->status.setText("远端已确认保存这条追番"))).show();}catch(Exception e){error(e);}}
    private void error(Exception e){if(status!=null)status.setText(Objects.toString(e.getMessage(),"操作未完成，请重试"));}
    @Override protected void onDestroy(){destroyed=true;io.shutdownNow();ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
