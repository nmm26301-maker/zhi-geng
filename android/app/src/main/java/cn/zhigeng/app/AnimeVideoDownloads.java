package cn.zhigeng.app;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Requirements;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** One persistent Media3 cache shared by the player, MP4/HLS/DASH download tasks and exporters. */
final class AnimeVideoDownloads {
    private static SimpleCache cache;
    private static DownloadManager manager;
    static synchronized DownloadManager manager(Context context) {
        if(manager==null){
            Context app=context.getApplicationContext();StandaloneDatabaseProvider database=new StandaloneDatabaseProvider(app);
            cache=new SimpleCache(new File(app.getFilesDir(),"anime-video-cache"),new NoOpCacheEvictor(),database);
            manager=new DownloadManager(app,new DefaultDownloadIndex(database),request->{
                try{JSONObject entry=new JSONObject(new String(request.data,StandardCharsets.UTF_8));return new DefaultDownloaderFactory(factory(false,entry),Runnable::run).createDownloader(request);}
                catch(Exception e){throw new IllegalArgumentException("缓存任务信息不可用",e);}
            });
            manager.setMaxParallelDownloads(2);manager.setMinRetryCount(1);manager.setRequirements(new Requirements(Requirements.NETWORK));manager.resumeDownloads();
        }return manager;
    }
    private static DefaultHttpDataSource.Factory http(JSONObject entry) {
        HashMap<String,String> headers=new HashMap<>();JSONObject values=entry.optJSONObject("headers");if(values!=null)for(String k:new String[]{"User-Agent","Referer","Origin"})if(values.has(k))headers.put(k,values.optString(k));
        return new DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers).setUserAgent(headers.containsKey("User-Agent")?headers.get("User-Agent"):"Zhigeng/0.6.19").setConnectTimeoutMs(12000).setReadTimeoutMs(18000)
            .setAllowCrossProtocolRedirects(false).setContentTypePredicate(type->{String value=type==null?"":type.toLowerCase(Locale.ROOT);return !value.contains("text/html")&&!value.contains("application/json")&&!value.contains("+json");});
    }
    static DataSource.Factory playback(Context context,boolean offline) {
        return playback(context,offline,new JSONObject());
    }
    private static CacheDataSource.Factory factory(boolean offline,JSONObject entry){String scope=entry.optString("cacheScope");return new CacheDataSource.Factory().setCache(cache).setCacheKeyFactory(spec->scope.isEmpty()?spec.uri.toString():scope+":"+spec.uri).setUpstreamDataSourceFactory(offline?null:http(entry));}
    static DataSource.Factory playback(Context context,boolean offline,JSONObject entry){manager(context);return factory(offline,entry).setCacheWriteDataSinkFactory(null);}
    static MediaItem item(JSONObject entry) {
        return item(entry,true);
    }
    static MediaItem item(JSONObject entry,boolean subtitles) {
        MediaItem.Builder b=new MediaItem.Builder().setUri(entry.optString("uri")).setMediaId(entry.optString("id"));
        if(subtitles){ArrayList<MediaItem.SubtitleConfiguration> tracks=new ArrayList<>();JSONArray rows=entry.optJSONArray("subtitles");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject s=rows.optJSONObject(i);if(s==null)continue;String f=s.optString("format");tracks.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.optString("url"))).setMimeType(f.equals("srt")?"application/x-subrip":f.equals("vtt")?"text/vtt":"text/x-ssa").setLanguage(s.optString("language","und")).setLabel(s.optString("label","字幕")).build());}b.setSubtitleConfigurations(tracks);}
        if(entry.optString("format").equals("hls"))b.setMimeType("application/x-mpegURL");
        if(entry.optString("format").equals("dash"))b.setMimeType("application/dash+xml");return b.build();
    }
    static void add(Context context,JSONObject entry)throws Exception {
        if(!entry.getString("uri").startsWith("https://"))throw new Exception("本地文件无需重复缓存");
        DownloadRequest.Builder b=new DownloadRequest.Builder(entry.getString("id"),Uri.parse(entry.getString("uri")))
            .setData(entry.toString().getBytes(StandardCharsets.UTF_8));
        if(entry.optString("format").equals("hls"))b.setMimeType("application/x-mpegURL");
        if(entry.optString("format").equals("dash"))b.setMimeType("application/dash+xml");
        DownloadService.sendAddDownload(context,AnimeDownloadService.class,b.build(),false);
    }
    static Download find(Context context,String id) {
        DownloadManager m=manager(context);for(Download d:m.getCurrentDownloads())if(d.request.id.equals(id))return d;
        try{return m.getDownloadIndex().getDownload(id);}catch(Exception ignored){return null;}
    }
    static ArrayList<Download> all(Context context)throws Exception {
        ArrayList<Download> items=new ArrayList<>();try(DownloadCursor cursor=manager(context).getDownloadIndex().getDownloads()){
            while(cursor.moveToNext()){items.add(cursor.getDownload());if(items.size()>=2000)break;}
        }return items;
    }
    static JSONObject entry(Download download)throws Exception{return new JSONObject(new String(download.request.data,StandardCharsets.UTF_8));}
    static String label(Download d) {
        if(d==null)return "尚未缓存";
        switch(d.state){
            case Download.STATE_COMPLETED:return "已缓存 · 可离线播放";
            case Download.STATE_FAILED:return "缓存失败 · 可重试";
            case Download.STATE_STOPPED:return "缓存已暂停";
            case Download.STATE_REMOVING:case Download.STATE_RESTARTING:return "正在清理缓存…";
            case Download.STATE_DOWNLOADING:float percent=d.getPercentDownloaded();return "缓存中 · "+(percent<0?d.getBytesDownloaded()/1024+" KB":Math.round(percent)+"%");
            default:return "等待网络或下载队列…";
        }
    }
}
