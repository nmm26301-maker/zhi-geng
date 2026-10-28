package cn.zhigeng.app;
import org.json.*;
/** Original local protocol endpoint; never included in release. */
final class Anime619FixtureNet extends AnimeNet {
    @Override Reply request(String url,String method,JSONObject headers,byte[] body,boolean privateAllowed,String redirect)throws Exception {
        String target=url;
        if(url.equals(AnimeTrackerClient.ANI_API))target="https://10.0.2.2:8447/anilist";
        else if(url.startsWith(AnimeTrackerClient.MAL_API))target="https://10.0.2.2:8447/mal"+url.substring(AnimeTrackerClient.MAL_API.length());
        else if(url.equals(AnimeTrackerClient.MAL_TOKEN))target="https://10.0.2.2:8447/token";
        return super.request(target,method,headers,body,true,redirect);
    }
}
