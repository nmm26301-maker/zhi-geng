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
            manager=new DownloadManager(app,database,cache,http(),Runnable::run);
            manager.setMaxParallelDownloads(2);manager.setMinRetryCount(1);manager.setRequirements(new Requirements(Requirements.NETWORK));manager.resumeDownloads();
        }return manager;
    }
    private static DefaultHttpDataSource.Factory http() {
        return new DefaultHttpDataSource.Factory().setUserAgent("Zhigeng/0.6.18").setConnectTimeoutMs(12000).setReadTimeoutMs(18000)
            .setAllowCrossProtocolRedirects(false).setContentTypePredicate(type->{String value=type==null?"":type.toLowerCase(Locale.ROOT);return !value.contains("text/html")&&!value.contains("application/json")&&!value.contains("+json");});
    }
    static DataSource.Factory playback(Context context,boolean offline) {
        manager(context);return new CacheDataSource.Factory().setCache(cache).setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(offline?null:http());
    }
    static MediaItem item(JSONObject entry) {
        MediaItem.Builder b=new MediaItem.Builder().setUri(entry.optString("uri")).setMediaId(entry.optString("id"));
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
