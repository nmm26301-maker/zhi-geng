package cn.zhigeng.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import androidx.media3.exoplayer.offline.*;
import androidx.media3.exoplayer.scheduler.Scheduler;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import java.util.List;

/** Foreground download work; pending tasks resume when the user reopens the video library. */
public final class AnimeDownloadService extends DownloadService {
    public AnimeDownloadService(){super(618,1000,"anime-video-downloads",R.string.anime_download_channel,0);}
    @Override protected DownloadManager getDownloadManager(){return AnimeVideoDownloads.manager(this);}
    @Override protected Scheduler getScheduler(){return null;}
    @Override protected Notification getForegroundNotification(List<Download> downloads,int requirements) {
        Intent intent=new Intent(this,AnimeVideoLibraryActivity.class).putExtra("downloads",true);
        PendingIntent open=PendingIntent.getActivity(this,618,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new DownloadNotificationHelper(this,"anime-video-downloads").buildProgressNotification(this,android.R.drawable.stat_sys_download,open,"知更动漫离线缓存",downloads,requirements);
    }
}
