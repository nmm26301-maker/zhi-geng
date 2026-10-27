package cn.zhigeng.app;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native video playback, persistent checkpoints and explicit local-watchlist completion. */
public final class AnimeVideoPlayerActivity extends Activity {
    private JSONObject entry;
    private AnimeVideoStore store;
    private ExoPlayer player;
    private PlayerView surface;
    private TextView title,status,time;
    private Button previous,next,retry;
    private CheckBox track,continuous;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService writes=Executors.newSingleThreadExecutor();
    private long position,lastWrite;
    private boolean play=true,failed,completed,started,usingOffline;
    private volatile boolean saveWarning;
    private final Runnable clock=new Runnable(){public void run(){if(player!=null){
        if(!failed){int state=player.getPlaybackState();String value=state==Player.STATE_ENDED?"播放结束":state==Player.STATE_BUFFERING?"正在缓冲…":state==Player.STATE_READY?(player.isPlaying()?"播放中":"已暂停"):"正在载入视频…";if(usingOffline)value+=" · 离线";if(!value.contentEquals(status.getText()))status.setText(value);}
        time.setText("进度 "+format(player.getCurrentPosition())+" / "+(player.getDuration()>0?format(player.getDuration()):"--:--"));
        if(SystemClock.elapsedRealtime()-lastWrite>5000){checkpoint();lastWrite=SystemClock.elapsedRealtime();}
    }ui.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);try{entry=new JSONObject(getIntent().getStringExtra("episode"));AnimeVideoModel.source(entry.getString("uri"));store=new AnimeVideoStore(this,entry.getJSONObject("media").getLong("id"));JSONObject latest=store.episode(entry.optInt("episode"));if(latest!=null&&latest.optString("id").equals(entry.optString("id")))entry=latest;}
        catch(Exception e){Toast.makeText(this,"剧集信息不可用，请重新选择",Toast.LENGTH_LONG).show();finish();return;}
        position=state==null?entry.optLong("position"):state.getLong("position");play=state==null||state.getBoolean("play",true);completed=state!=null&&state.getBoolean("completed");
        getWindow().getDecorView().setSystemUiVisibility(0);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root=column();root.setBackgroundColor(Color.BLACK);LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹ 返回剧集");bar.addView(back);back.setOnClickListener(v->finish());title=label("",17);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button rotate=button("横屏 / 竖屏");rotate.setOnClickListener(v->setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));root.addView(bar);
        surface=new PlayerView(this);surface.setContentDescription("动漫视频播放器");surface.setUseController(true);surface.setKeepScreenOn(true);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView controls=new ScrollView(this);LinearLayout panel=column();panel.setPadding(dp(14),dp(4),dp(14),dp(10));controls.addView(panel);
        status=label("正在载入视频…",15);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);panel.addView(status);time=label("",13);panel.addView(time);
        LinearLayout seek=new LinearLayout(this);Button rewind=button("后退10秒"),forward=button("前进10秒"),speed=button("倍速");
        for(Button b:new Button[]{rewind,forward,speed})seek.addView(b,new LinearLayout.LayoutParams(0,-2,1));panel.addView(seek);
        rewind.setOnClickListener(v->{if(player!=null)player.seekTo(Math.max(0,player.getCurrentPosition()-10000));});forward.setOnClickListener(v->{if(player!=null)player.seekTo(player.getDuration()>0?Math.min(player.getDuration(),player.getCurrentPosition()+10000):player.getCurrentPosition()+10000);});
        speed.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("播放速度").setItems(new String[]{"0.75×","1.0×","1.25×","1.5×","2.0×"},(d,w)->{if(player!=null)player.setPlaybackSpeed(new float[]{.75f,1f,1.25f,1.5f,2f}[w]);}).show());
        LinearLayout navigation=new LinearLayout(this);previous=button("上一集视频");next=button("下一集视频");navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next,new LinearLayout.LayoutParams(0,-2,1));panel.addView(navigation);panel.addView(rotate);
        previous.setOnClickListener(v->move(-1));next.setOnClickListener(v->move(1));
        SharedPreferences p=getSharedPreferences("anime_player_settings",MODE_PRIVATE);
        track=new CheckBox(this);track.setTextColor(Color.WHITE);track.setText("播放结束更新本地追番");track.setChecked(p.getBoolean("track",true));track.setVisibility(entry.optJSONObject("media").optLong("id")>0?View.VISIBLE:View.GONE);panel.addView(track);
        continuous=new CheckBox(this);continuous.setTextColor(Color.WHITE);continuous.setText("自动播放下一集");continuous.setChecked(p.getBoolean("continuous",false));panel.addView(continuous);
        track.setOnCheckedChangeListener((v,checked)->p.edit().putBoolean("track",checked).apply());continuous.setOnCheckedChangeListener((v,checked)->p.edit().putBoolean("continuous",checked).apply());
        retry=button("重试播放");retry.setVisibility(View.GONE);panel.addView(retry);retry.setOnClickListener(v->{release();build();});
        int maxPanel=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?110:220;root.addView(controls,new LinearLayout.LayoutParams(-1,dp(maxPanel)));setContentView(root);caption();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(size);return v;}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    private static String format(long ms){long seconds=Math.max(0,ms/1000);return String.format(Locale.ROOT,seconds>=3600?"%d:%02d:%02d":"%02d:%02d",seconds>=3600?new Object[]{seconds/3600,seconds/60%60,seconds%60}:new Object[]{seconds/60,seconds%60});}
    private void build(){
        if(isFinishing())return;failed=false;retry.setVisibility(View.GONE);
        boolean local=entry.optString("uri").startsWith("content://");androidx.media3.exoplayer.offline.Download d=AnimeVideoDownloads.find(this,entry.optString("id"));
        boolean offline=d!=null&&d.state==androidx.media3.exoplayer.offline.Download.STATE_COMPLETED;usingOffline=offline;surface.setContentDescription("动漫视频播放器");
        DefaultMediaSourceFactory sources=new DefaultMediaSourceFactory(local?new DefaultDataSource.Factory(this):AnimeVideoDownloads.playback(this,offline));
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(sources).build();surface.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.addListener(new Player.Listener(){
            @Override public void onRenderedFirstFrame(){surface.setContentDescription("视频画面已显示");}
            @Override public void onPlayerError(PlaybackException error){failed=true;status.setText(local?"无法读取或解码本地视频，请重试或重新选择文件":"视频暂时无法播放，请检查网络、缓存或视频地址");retry.setVisibility(View.VISIBLE);}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&!completed){
                completed=true;checkpoint();if(track.isChecked()&&entry.optJSONObject("media").optLong("id")>0)try{AnimeVideoStore.finish(AnimeVideoPlayerActivity.this,entry);Toast.makeText(AnimeVideoPlayerActivity.this,"本地追番已更新",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(AnimeVideoPlayerActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}
                if(continuous.isChecked())ui.postDelayed(()->{if(started&&player!=null)move(1);},500);
            }}
        });player.setMediaItem(AnimeVideoDownloads.item(entry));player.seekTo(Math.max(0,position));player.prepare();player.setPlayWhenReady(play);
    }
    private void caption(){title.setText("第 "+entry.optInt("episode")+" 集 · "+entry.optString("title"));previous.setEnabled(neighbor(-1)!=null);next.setEnabled(neighbor(1)!=null);}
    private JSONObject neighbor(int direction){try{ArrayList<JSONObject> all=AnimeVideoModel.list(store.entries());JSONObject result=null;int current=entry.optInt("episode");for(JSONObject e:all){int n=e.optInt("episode");if(direction>0&&n>current)return e;if(direction<0&&n<current)result=e;}return direction<0?result:null;}catch(Exception ignored){return null;}}
    private void move(int direction){JSONObject target=neighbor(direction);if(target==null)return;release();entry=target;position=entry.optLong("position");play=true;completed=false;caption();build();}
    private void checkpoint(){if(player==null)return;position=player.getCurrentPosition();play=player.getPlayWhenReady();long duration=Math.max(0,player.getDuration());JSONObject captured=entry;long savedPosition=position;writes.execute(()->{try{store.progress(captured,savedPosition,duration);}catch(Exception error){if(!saveWarning){saveWarning=true;runOnUiThread(()->Toast.makeText(getApplicationContext(),"续播位置未能保存，请检查存储空间",Toast.LENGTH_LONG).show());}}});}
    private void release(){if(player!=null){checkpoint();surface.setPlayer(null);player.release();player=null;}}
    @Override protected void onStart(){super.onStart();started=true;if(entry!=null){build();ui.post(clock);}}
    @Override protected void onStop(){started=false;ui.removeCallbacks(clock);release();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){if(player!=null){position=player.getCurrentPosition();play=player.getPlayWhenReady();}out.putLong("position",position);out.putBoolean("play",play);out.putBoolean("completed",completed);if(entry!=null)getIntent().putExtra("episode",entry.toString());super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);writes.shutdown();super.onDestroy();}
}
