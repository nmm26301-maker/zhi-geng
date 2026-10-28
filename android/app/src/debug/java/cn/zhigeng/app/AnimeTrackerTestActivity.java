package cn.zhigeng.app;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
/** Original fake-account callback, exercising actual state validation, encryption and sync client. */
public final class AnimeTrackerTestActivity extends AnimeTrackerActivity {
    @Override protected AnimeTrackerClient createClient(String service)throws Exception{return new AnimeTrackerClient(this,service,new Anime619FixtureNet());}
    @Override public void onCreate(Bundle saved){
        if(saved==null)try{String provider=getIntent().getStringExtra("provider");if(provider==null)provider="anilist";JSONObject p=AnimeTrackerAuth.begin(provider,provider.equals("anilist")?"12345":"abcdef1234567890",System.currentTimeMillis());new SecretStore(this).put("tracker.pending",p.toString());
            String uri=AnimeTrackerAuth.callback(provider)+(provider.equals("anilist")?"#access_token=fixture-anime-619-ani&state=":"?code=fixture-anime-619-code&state=")+p.getString("state");setIntent(new Intent(getIntent()).setData(Uri.parse(uri)));
        }catch(Exception e){throw new IllegalStateException(e);}super.onCreate(saved);
    }
}
