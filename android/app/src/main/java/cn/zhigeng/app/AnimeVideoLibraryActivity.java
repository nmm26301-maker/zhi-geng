package cn.zhigeng.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.offline.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Actual episode sources, local-file grants, native playback, downloads and completed-file export. */
public class AnimeVideoLibraryActivity extends ReadingToolActivity {
    private static final int PICK=71,JSON_FILE=72,EXPORT=73,NOTICE=74;
    private JSONObject anime,exporting,notificationPending;
    private AnimeVideoStore store;
    private AnimeCatalogClient catalog;
    protected AnimeCatalogClient createCatalogClient()throws Exception{return new AnimeCatalogClient();}
    private LinearLayout entriesView;
    private TextView status,pageText;
    private boolean downloadsOnly,destroyed;
    private int page=1,startEpisode=1;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final HashMap<String,TextView> labels=new HashMap<>();
    private final HashMap<String,Integer> observed=new HashMap<>();
    private final Runnable tick=new Runnable(){public void run(){if(destroyed)return;boolean changed=false;for(String id:new ArrayList<>(labels.keySet())){
        Download d=AnimeVideoDownloads.find(AnimeVideoLibraryActivity.this,id);TextView label=labels.get(id);if(label!=null)label.setText(AnimeVideoDownloads.label(d));int state=d==null?-1:d.state;
        if(!Objects.equals(observed.put(id,state),state))changed=true;
    }if(changed)render();ui.postDelayed(this,1000);}};
    public static void open(android.content.Context context,JSONObject media){
        try{context.startActivity(new Intent(context,AnimeVideoLibraryActivity.class).putExtra("anime",AnimeVideoModel.media(media).toString()));}
        catch(Exception e){Toast.makeText(context,"动漫条目暂不可用",Toast.LENGTH_SHORT).show();}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);try{String raw=getIntent().getStringExtra("anime");anime=raw==null?new JSONObject("{\"id\":0,\"title\":{\"userPreferred\":\"本地视频\"}}"):AnimeVideoModel.media(new JSONObject(raw));store=new AnimeVideoStore(this,anime.optLong("id"));}
        catch(Exception e){finish();return;}
        downloadsOnly=getIntent().getBooleanExtra("downloads",false);if(state!=null){page=Math.max(1,state.getInt("page",1));startEpisode=state.getInt("startEpisode",1);try{if(state.containsKey("exporting"))exporting=new JSONObject(state.getString("exporting"));if(state.containsKey("notificationPending"))notificationPending=new JSONObject(state.getString("notificationPending"));}catch(Exception ignored){}}
        try{catalog=createCatalogClient();}catch(Exception e){Toast.makeText(this,"播出服务暂不可用",Toast.LENGTH_LONG).show();finish();return;}
        screen(downloadsOnly?"离线缓存":"视频与剧集");add(body,text(downloadsOnly?"管理我的离线缓存":AnimeCatalogClient.title(anime),23,INK),12);
        if(!downloadsOnly){
            add(body,text("导入视频后可直接播放、续播。网络点播支持 MP4、HLS（m3u8）与 DASH（mpd）。",14,INK),10);
            Button local=button("导入本地视频",true),link=button("添加视频地址",false),playlist=button("导入剧集清单",false),downloads=button("离线缓存管理",false);
            for(Button b:new Button[]{local,link,playlist,downloads})add(body,b,10);
            if(anime.optLong("id")>0){Button platforms=button("查询播出平台",false);add(body,platforms,10);platforms.setOnClickListener(v->platforms(platforms));}
            local.setOnClickListener(v->pickStart());link.setOnClickListener(v->network(null));playlist.setOnClickListener(v->importMenu());downloads.setOnClickListener(v->startActivity(new Intent(this,AnimeVideoLibraryActivity.class).putExtra("downloads",true)));
        }else add(body,text("缓存可暂停、继续或清除。普通视频完成后可导出独立文件；HLS/DASH 使用应用内离线播放。",14,INK),10);
        status=text("",14,INK);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,status,14);entriesView=column();add(body,entriesView,8);
        pageText=text("",13,INK);add(body,pageText,12);LinearLayout nav=row();Button previous=button("剧集上一页",false),next=button("剧集下一页",false);nav.addView(previous,new LinearLayout.LayoutParams(0,-2,1));nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));add(body,nav,8);
        previous.setOnClickListener(v->{if(page>1){page--;render();}});next.setOnClickListener(v->{page++;render();});
        add(body,text("离线缓存保存在知更中，卸载应用会清除缓存；已导出到手机的独立文件自行保留。本地原文件不会被移除。",13,INK),18);
        Button license=button("播放器开源许可",false);add(body,license,12);license.setOnClickListener(v->{try(InputStream in=getAssets().open("media3-Apache-2.0.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);new AlertDialog.Builder(this).setTitle("AndroidX Media3 · Apache 2.0").setMessage(out.toString("UTF-8")).setPositiveButton("关闭",null).show();}catch(Exception e){message(new Exception("许可文件暂不可读"));}});
        AnimeVideoDownloads.manager(this);DownloadService.start(this,AnimeDownloadService.class);render();
    }
    private void platforms(Button button){
        button.setEnabled(false);status.setText("正在查询目录收录的播出平台…");io.execute(()->{try{
            JSONArray links=catalog.platforms(anime.optLong("id"));String[] names=new String[links.length()];
            for(int i=0;i<names.length;i++){JSONObject row=links.getJSONObject(i);names[i]=row.optString("site")+" · "+new java.net.URI(row.getString("url")).getHost();}
            ui.post(()->{if(destroyed)return;button.setEnabled(true);status.setText(names.length==0?"该条目暂无收录的播出平台，可导入你的视频或剧集清单":"找到 "+names.length+" 个播出链接");
                if(names.length>0&&hasWindowFocus())new AlertDialog.Builder(this).setTitle("播出平台 · 在浏览器中打开").setItems(names,(dialog,which)->BrowserLinks.open(this,links.optJSONObject(which).optString("url"))).setNegativeButton("关闭",null).show();
            });
        }catch(Exception e){ui.post(()->{if(!destroyed){button.setEnabled(true);message(e);}});}});
    }
    private ArrayList<JSONObject> rows()throws Exception {
        if(!downloadsOnly)return AnimeVideoModel.list(store.entries());
        ArrayList<JSONObject> out=new ArrayList<>();for(Download d:AnimeVideoDownloads.all(this))try{out.add(AnimeVideoDownloads.entry(d));}catch(Exception ignored){}return out;
    }
    private void render(){
        if(destroyed)return;try{
            ArrayList<JSONObject> rows=rows();page=Math.max(1,Math.min(page,Math.max(1,(rows.size()+9)/10)));entriesView.removeAllViews();labels.clear();observed.clear();
            status.setText((downloadsOnly?"离线任务":"已关联剧集")+" · "+rows.size()+" 集");
            for(int i=(page-1)*10;i<Math.min(page*10,rows.size());i++)card(rows.get(i));
            if(rows.isEmpty())add(entriesView,text(downloadsOnly?"还没有离线缓存任务。请先添加视频地址，再点击缓存。":"还没有关联剧集。可以导入本地视频、添加视频地址或导入清单。",15,INK),16);
            pageText.setText("第 "+page+" 页 · 每页10集");
        }catch(Exception e){status.setText("剧集数据暂不可读，已保留原数据，请重试");}
    }
    private void card(JSONObject entry)throws Exception {
        int number=entry.getInt("episode");String id=entry.getString("id");Download d=AnimeVideoDownloads.find(this,id);boolean local=entry.getString("uri").startsWith("content://");
        LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(android.graphics.Color.WHITE,18));
        if(downloadsOnly)add(card,text(AnimeCatalogClient.title(entry.getJSONObject("media")),14,INK),0);
        add(card,text("第 "+number+" 集 · "+entry.getString("title"),19,INK),6);
        if(entry.optLong("position")>0)add(card,text("上次观看至 "+entry.optLong("position")/1000+" 秒",13,INK),6);
        TextView label=text(local?"本地文件 · 可直接播放":AnimeVideoDownloads.label(d),14,INK);label.setContentDescription("第"+number+"集缓存状态");add(card,label,8);
        if(!local){labels.put(id,label);observed.put(id,d==null?-1:d.state);}
        Button play=button("播放第"+number+"集",true);add(card,play,10);play.setOnClickListener(v->startActivity(new Intent(this,AnimeVideoPlayerActivity.class).putExtra("episode",entry.toString())));
        if(!local){
            if(d==null||d.state==Download.STATE_FAILED){Button cache=button((d==null?"缓存第":"重试缓存第")+number+"集",false);add(card,cache,8);cache.setOnClickListener(v->askDownload(entry));}
            else if(d.state!=Download.STATE_COMPLETED&&d.state!=Download.STATE_REMOVING){boolean paused=d.state==Download.STATE_STOPPED;Button pause=button((paused?"继续缓存第":"暂停缓存第")+number+"集",false);add(card,pause,8);pause.setOnClickListener(v->{DownloadService.sendSetStopReason(this,AnimeDownloadService.class,id,paused?Download.STOP_REASON_NONE:1,false);ui.postDelayed(this::render,300);});}
            if(d!=null){Button remove=button("清除缓存第"+number+"集",false);add(card,remove,8);remove.setOnClickListener(v->confirmCache(entry));}
            if(d!=null&&d.state==Download.STATE_COMPLETED&&!AnimeVideoModel.adaptive(entry)){Button export=button("导出第"+number+"集视频",false);add(card,export,8);export.setOnClickListener(v->export(entry));}
        }
        if(!downloadsOnly){Button change=button(local?"重新选择第"+number+"集文件":"更新第"+number+"集来源",false),remove=button("移出第"+number+"集",false);add(card,change,8);add(card,remove,8);change.setOnClickListener(v->{if(local)pickFiles(number,false);else network(entry);});remove.setOnClickListener(v->removeEpisode(entry));}
        add(entriesView,card,12);
    }
    private void message(Exception e){status.setText(Objects.toString(e.getMessage(),"操作未完成，请重试"));Toast.makeText(this,status.getText(),Toast.LENGTH_LONG).show();}
    private void askDownload(JSONObject entry){
        new AlertDialog.Builder(this).setTitle("缓存这集视频？").setMessage("将按视频源提供的画质下载到知更，可能使用较多流量和存储空间。")
            .setNegativeButton("取消",null).setPositiveButton("开始缓存",(d,w)->{
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){notificationPending=entry;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTICE);}
                else download(entry);
            }).show();
    }
    private void download(JSONObject entry){try{AnimeVideoDownloads.add(this,entry);ui.postDelayed(this::render,300);}catch(Exception e){message(e);}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==NOTICE&&notificationPending!=null){JSONObject entry=notificationPending;notificationPending=null;download(entry);}}
    private void confirmCache(JSONObject entry){new AlertDialog.Builder(this).setTitle("清除此集缓存？").setMessage("只移除知更中的离线缓存，已导出的文件保留。")
        .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{DownloadService.sendRemoveDownload(this,AnimeDownloadService.class,entry.optString("id"),false);ui.postDelayed(this::render,300);}).show();}
    private void removeEpisode(JSONObject entry){new AlertDialog.Builder(this).setTitle("移出这集视频？").setMessage("同时清除此集的离线缓存，本地原文件及已导出文件保留。")
        .setNegativeButton("保留",null).setPositiveButton("移出",(d,w)->{try{JSONObject updated=store.entries();updated.remove(Integer.toString(entry.optInt("episode")));store.save(updated);DownloadService.sendRemoveDownload(this,AnimeDownloadService.class,entry.optString("id"),false);render();}catch(Exception e){message(e);}}).show();}
    private void numberDialog(String title,java.util.function.IntConsumer confirmed){
        EditText input=input("起始集数");input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setText("1");LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(12));add(box,text("起始集数",15,INK),0);add(box,input,8);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("取消",null).setPositiveButton("继续",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int n=Integer.parseInt(input.getText().toString());if(n<1||n>100000)throw new Exception("请填写有效起始集数");dialog.dismiss();confirmed.accept(n);}catch(Exception e){input.setError("请填写有效起始集数");}}));dialog.show();
    }
    private void pickStart(){numberDialog("选择视频的起始集数",n->pickFiles(n,true));}
    private void pickFiles(int number,boolean multiple){startEpisode=number;try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple),PICK);}catch(Exception e){message(new Exception("未找到系统文件选择器"));}}
    private void network(JSONObject old){
        LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),dp(12));EditText episode=input("视频集数"),title=input("剧集名称（可选）"),url=input("HTTPS 视频地址");episode.setInputType(InputType.TYPE_CLASS_NUMBER);episode.setText(Integer.toString(old==null?1:old.optInt("episode")));if(old!=null){title.setText(old.optString("title"));if(old.optString("uri").startsWith("https://"))url.setText(old.optString("uri"));episode.setEnabled(false);}
        for(String label:new String[]{"视频集数","剧集名称（可选）","HTTPS 视频地址"}){add(box,text(label,14,INK),8);add(box,label.equals("视频集数")?episode:label.equals("HTTPS 视频地址")?url:title,4);}
        Spinner format=new Spinner(this);format.setContentDescription("视频格式");format.setMinimumHeight(dp(48));String[] kinds={"auto","hls","dash","file"};format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"自动识别","HLS · m3u8","DASH · mpd","普通视频文件"}));if(old!=null)format.setSelection(Math.max(0,Arrays.asList(kinds).indexOf(old.optString("format"))));add(box,text("视频格式",14,INK),8);add(box,format,4);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(old==null?"添加视频地址":"更新剧集来源").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存视频",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{int n=Integer.parseInt(episode.getText().toString());JSONObject updated=AnimeVideoModel.put(store.entries(),anime,n,title.getText().toString(),url.getText().toString(),kinds[format.getSelectedItemPosition()]);if(!updated.getJSONObject(Integer.toString(n)).getString("uri").startsWith("https://"))throw new Exception("请在这里填写 HTTPS 视频地址");dialog.dismiss();apply(updated);}catch(Exception e){url.setError(Objects.toString(e.getMessage(),"请检查视频信息"));}}));dialog.show();
    }
    private void apply(JSONObject updated)throws Exception {
        JSONObject old=store.entries();boolean replacing=false;Iterator<String> keys=updated.keys();while(keys.hasNext()){String k=keys.next();JSONObject a=old.optJSONObject(k),b=updated.optJSONObject(k);if(a!=null&&b!=null&&!a.optString("id").equals(b.optString("id")))replacing=true;}
        if(replacing)new AlertDialog.Builder(this).setTitle("替换已有剧集来源？").setMessage("相同集数将使用新来源，并清理旧来源的离线缓存；原视频文件保留。")
            .setNegativeButton("取消",null).setPositiveButton("替换",(d,w)->commit(updated,old)).show();else commit(updated,old);
    }
    private void commit(JSONObject updated,JSONObject old){try{store.save(updated);Iterator<String> keys=old.keys();while(keys.hasNext()){String k=keys.next();JSONObject a=old.optJSONObject(k),b=updated.optJSONObject(k);if(a!=null&&(b==null||!a.optString("id").equals(b.optString("id"))))DownloadService.sendRemoveDownload(this,AnimeDownloadService.class,a.optString("id"),false);}render();Toast.makeText(this,"剧集已保存",Toast.LENGTH_SHORT).show();}catch(Exception e){message(e);}}
    private void importMenu(){new AlertDialog.Builder(this).setTitle("导入剧集清单").setItems(new String[]{"选择 JSON 文件","粘贴 JSON 清单","查看清单格式"},(d,w)->{if(w==0)try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),JSON_FILE);}catch(Exception e){message(new Exception("未找到系统文件选择器"));}else if(w==1)paste();else new AlertDialog.Builder(this).setTitle("清单格式").setMessage("{\"episodes\":[{\"episode\":1,\"title\":\"第一集\",\"url\":\"https://你的服务器/video.mp4\",\"format\":\"auto\"}]}\n\nformat 可填 auto、hls、dash、file；每次最多200集。清单中的地址需要可直接播放。").setPositiveButton("知道了",null).show();}).show();}
    private void paste(){EditText input=input("粘贴剧集 JSON");input.setSingleLine(false);input.setMinLines(5);ScrollView scroll=new ScrollView(this);scroll.addView(input);AlertDialog d=new AlertDialog.Builder(this).setTitle("粘贴剧集清单").setView(scroll).setNegativeButton("取消",null).setPositiveButton("导入清单",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{JSONObject updated=AnimeVideoModel.playlist(store.entries(),anime,input.getText().toString());d.dismiss();apply(updated);}catch(Exception e){input.setError(e.getMessage());}}));d.show();}
    private String fileName(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "本地视频";}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null){if(request==EXPORT)exporting=null;return;}
        try{if(request==PICK){ArrayList<Uri> picked=new ArrayList<>();if(data.getClipData()!=null){ClipData clip=data.getClipData();if(clip.getItemCount()>200)throw new Exception("每次最多导入200个视频");for(int i=0;i<clip.getItemCount();i++)picked.add(clip.getItemAt(i).getUri());}else if(data.getData()!=null)picked.add(data.getData());
            HashMap<Uri,String> names=new HashMap<>();for(Uri uri:picked){getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);names.put(uri,fileName(uri));}picked.sort((a,b)->AnimeVideoModel.natural(names.get(a),names.get(b)));JSONObject updated=store.entries();for(int i=0;i<picked.size();i++)updated=AnimeVideoModel.put(updated,anime,startEpisode+i,names.get(picked.get(i)),picked.get(i).toString(),"file");apply(updated);
        }else if(request==JSON_FILE&&data.getData()!=null){Uri uri=data.getData();status.setText("正在读取剧集清单…");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int count;while((count=in.read(bytes))!=-1){if(out.size()+count>1024*1024)throw new Exception("剧集清单不能超过1MB");out.write(bytes,0,count);}String raw=out.toString("UTF-8");ui.post(()->{if(!destroyed)try{apply(AnimeVideoModel.playlist(store.entries(),anime,raw));}catch(Exception e){message(e);}});
            }catch(Exception e){ui.post(()->{if(!destroyed)message(e);});}});
        }else if(request==EXPORT&&data.getData()!=null&&exporting!=null){JSONObject entry=exporting;exporting=null;Download d=AnimeVideoDownloads.find(this,entry.optString("id"));if(d==null||d.state!=Download.STATE_COMPLETED)throw new Exception("请完成缓存后再导出");writeExport(entry,data.getData());}
        }catch(Exception e){message(e);}
    }
    private void export(JSONObject entry){try{exporting=entry;String path=Uri.parse(entry.optString("uri")).getLastPathSegment(),extension="bin";if(path!=null&&path.matches("(?i).*\\.(mp4|webm|mkv|mov|m4v|ts)"))extension=path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String name=(AnimeCatalogClient.title(entry.getJSONObject("media"))+"-第"+entry.optInt("episode")+"集").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(name.length()>100)name=name.substring(0,100);
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(extension.equals("bin")?"application/octet-stream":"video/"+(extension.equals("mkv")?"x-matroska":extension)).putExtra(Intent.EXTRA_TITLE,name+"."+extension),EXPORT);
    }catch(Exception e){message(new Exception("未找到系统保存位置选择器"));}}
    private void writeExport(JSONObject entry,Uri target){status.setText("正在导出视频…");io.execute(()->{DataSource source=AnimeVideoDownloads.playback(this,true).createDataSource();try{
        source.open(new DataSpec(Uri.parse(entry.getString("uri"))));try(OutputStream out=getContentResolver().openOutputStream(target,"wt")){if(out==null)throw new IOException("无法打开保存位置");byte[] buffer=new byte[65536];int count;while((count=source.read(buffer,0,buffer.length))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("导出已中止");out.write(buffer,0,count);}}
        ui.post(()->{if(!destroyed){status.setText("视频已导出到手机");Toast.makeText(this,"视频已导出到手机",Toast.LENGTH_LONG).show();}});
    }catch(Exception e){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),target);}catch(Exception ignored){}ui.post(()->{if(!destroyed)message(e);});}finally{try{source.close();}catch(Exception ignored){}}});}
    @Override protected void onResume(){super.onResume();if(store!=null){render();ui.removeCallbacks(tick);ui.postDelayed(tick,1000);}}
    @Override protected void onPause(){ui.removeCallbacks(tick);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("page",page);state.putInt("startEpisode",startEpisode);if(exporting!=null)state.putString("exporting",exporting.toString());if(notificationPending!=null)state.putString("notificationPending",notificationPending.toString());}
    @Override protected void onDestroy(){destroyed=true;if(catalog!=null)catalog.cancel();ui.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy();}
}
