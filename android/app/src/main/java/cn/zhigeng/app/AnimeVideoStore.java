package cn.zhigeng.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

/** Separate episode storage; corrupt source data is never replaced by an empty library. */
final class AnimeVideoStore {
    private static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    AnimeVideoStore(Context context,long id){preferences=context.getSharedPreferences("anime_video_"+id+"_v1",Context.MODE_PRIVATE);}
    JSONObject entries()throws Exception{synchronized(LOCK){return new JSONObject(preferences.getString("episodes","{}"));}}
    void save(JSONObject entries)throws Exception {
        synchronized(LOCK){if(!preferences.edit().putString("episodes",entries.toString()).commit())throw new Exception("剧集未能保存，请重试");}
    }
    JSONObject episode(int number)throws Exception{return entries().optJSONObject(Integer.toString(number));}
    void progress(JSONObject entry,long position,long duration)throws Exception {
        synchronized(LOCK){save(AnimeVideoModel.progress(entries(),entry.optInt("episode"),entry.optString("id"),position,duration));}
    }
    static void finish(Context context,JSONObject episode)throws Exception {
        SharedPreferences p=context.getSharedPreferences("anix_watchlist_v1",Context.MODE_PRIVATE);
        JSONObject updated=AnimeVideoModel.finished(new JSONObject(p.getString("entries","{}")),episode);
        if(!p.edit().putString("entries",updated.toString()).commit())throw new Exception("播放已结束，但追番未能保存，请重试");
    }
}
