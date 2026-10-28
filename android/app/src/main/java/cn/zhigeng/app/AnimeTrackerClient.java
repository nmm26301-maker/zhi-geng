package cn.zhigeng.app;

import android.content.Context;
import java.util.*;
import org.json.*;

/** Account-bound, authenticated tracker requests. A write only happens after a visible per-item confirmation. */
class AnimeTrackerClient {
    static final String ANI_API="https://graphql.anilist.co",MAL_API="https://api.myanimelist.net/v2",MAL_TOKEN="https://myanimelist.net/v1/oauth2/token";
    private final SecretStore secrets;private final AnimeNet net;private final String service;
    private static final Object ACCOUNT_LOCK=new Object();private static final Map<String,Long> BACKOFF=new HashMap<>();
    AnimeTrackerClient(Context context,String service)throws Exception{this(context,service,new AnimeNet());}
    AnimeTrackerClient(Context context,String service,AnimeNet net)throws Exception{this.service=AnimeTrackerAuth.service(service);this.secrets=new SecretStore(context);this.net=net;}
    JSONObject account()throws Exception{String raw=secrets.get("tracker.account."+service);return raw.isEmpty()?null:new JSONObject(raw);}
    void disconnect()throws Exception{synchronized(ACCOUNT_LOCK){secrets.put("tracker.account."+service,"");secrets.put("tracker.pending","");}}
    private JSONObject formRequest(JSONObject values)throws Exception {
        AnimeNet.Reply r=net.request(MAL_TOKEN,"POST",new JSONObject().put("Content-Type","application/x-www-form-urlencoded"),AnimeNet.utf8(AnimeNet.form(values)),false);
        if(r.status!=200)throw new Exception("账号授权未完成（HTTP "+r.status+"），请重新连接");return r.json();
    }
    void complete(JSONObject pending,JSONObject answer)throws Exception {
        JSONObject token=service.equals(AnimeTrackerAuth.ANI)?new JSONObject().put("access_token",answer.getString("access_token")).put("expires_in",answer.optLong("expires_in",31536000)):formRequest(AnimeTrackerAuth.exchange(pending,answer));
        String access=token.getString("access_token");if(access.isEmpty()||access.length()>12000)throw new Exception("授权凭证不可用");
        JSONObject info=service.equals(AnimeTrackerAuth.ANI)?graphql("query { Viewer { id name } }",new JSONObject(),access).getJSONObject("Viewer"):api("/users/@me","GET",null,access);
        if(info.optLong("id")<=0||info.optString("name").isEmpty())throw new Exception("无法核对登录账号");
        JSONObject saved=new JSONObject().put("token",access).put("refresh",token.optString("refresh_token")).put("clientId",pending.getString("clientId"))
            .put("expiresAt",System.currentTimeMillis()+Math.min(366L*86400,Math.max(1,token.optLong("expires_in",31536000)))*1000).put("userId",info.getLong("id")).put("name",info.getString("name"));
        synchronized(ACCOUNT_LOCK){secrets.put("tracker.account."+service,saved.toString());secrets.put("tracker.pending","");}
    }
    private JSONObject current()throws Exception {
        synchronized(ACCOUNT_LOCK){JSONObject a=account();if(a==null)throw new Exception("请先连接账号");
            if(a.optLong("expiresAt")<System.currentTimeMillis()+60000){
                if(service.equals(AnimeTrackerAuth.ANI)||a.optString("refresh").isEmpty())throw new Exception("账号授权已过期，请重新连接");
                JSONObject token=formRequest(new JSONObject().put("client_id",a.getString("clientId")).put("grant_type","refresh_token").put("refresh_token",a.getString("refresh")));
                a.put("token",token.getString("access_token")).put("refresh",token.optString("refresh_token",a.getString("refresh"))).put("expiresAt",System.currentTimeMillis()+Math.max(1,Math.min(366L*86400,token.getLong("expires_in")))*1000);
                secrets.put("tracker.account."+service,a.toString());
            }return a;
        }
    }
    private JSONObject request(String url,String method,JSONObject payload,String token,boolean json)throws Exception {
        synchronized(BACKOFF){if(BACKOFF.getOrDefault(service,0L)>System.currentTimeMillis())throw new Exception("账号服务请求较多，请稍后重试");}
        JSONObject headers=new JSONObject().put("Accept","application/json");if(token!=null)headers.put("Authorization","Bearer "+token);
        byte[] bytes=null;if(payload!=null){headers.put("Content-Type",json?"application/json":"application/x-www-form-urlencoded");bytes=AnimeNet.utf8(json?payload.toString():AnimeNet.form(payload));}
        AnimeNet.Reply r=net.request(url,method,headers,bytes,false);
        if(r.status==429){long seconds=60;try{seconds=Long.parseLong(r.headers.optString("retry-after"));}catch(Exception ignored){}synchronized(BACKOFF){BACKOFF.put(service,System.currentTimeMillis()+Math.max(1,Math.min(seconds,300))*1000);}throw new Exception("账号服务请求较多，请稍后重试");}
        if(r.status==401||r.status==403)throw new Exception("账号授权不可用，请重新连接");if(r.status<200||r.status>=300)throw new Exception("账号服务暂不可用（HTTP "+r.status+"），本地追番保留");return r.json();
    }
    private JSONObject graphql(String query,JSONObject variables,String token)throws Exception {
        JSONObject r=request(ANI_API,"POST",new JSONObject().put("query",query).put("variables",variables),token,true);
        if(r.optJSONArray("errors")!=null&&r.getJSONArray("errors").length()>0)throw new Exception("追番服务未接受请求，本地记录保留");return r.getJSONObject("data");
    }
    private JSONObject api(String path,String method,JSONObject payload,String token)throws Exception{return request(MAL_API+path,method,payload,token,false);}
    static String state(String remote,boolean mal)throws Exception {
        String[] values=mal?new String[]{"plan_to_watch","watching","completed","on_hold","dropped"}:new String[]{"PLANNING","CURRENT","COMPLETED","PAUSED","DROPPED"};
        if(!mal&&remote.equals("REPEATING"))return "在看";for(int i=0;i<values.length;i++)if(values[i].equals(remote))return AnimeWatchlist.STATES[i];throw new Exception("远端追番状态暂不支持");
    }
    static String remoteState(String local,boolean mal)throws Exception {
        String[] values=mal?new String[]{"plan_to_watch","watching","completed","on_hold","dropped"}:new String[]{"PLANNING","CURRENT","COMPLETED","PAUSED","DROPPED"};for(int i=0;i<AnimeWatchlist.STATES.length;i++)if(AnimeWatchlist.STATES[i].equals(local))return values[i];throw new Exception("本地状态不可用");
    }
    static JSONObject importEntry(JSONObject current,JSONObject remote)throws Exception {
        JSONObject media=remote.getJSONObject("media");int count=remote.getInt("progress");
        JSONObject updated=AnimeWatchlist.update(current,media,remote.getString("state"),count);
        updated.getJSONObject(Long.toString(media.getLong("id"))).put("progress",count);return updated;
    }
    JSONObject page(int page)throws Exception {
        if(page<1||page>1000)throw new Exception("页码不可用");JSONObject a=current();JSONArray rows=new JSONArray();boolean more;
        if(service.equals(AnimeTrackerAuth.ANI)){
            JSONObject result=graphql("query ($uid:Int!,$page:Int!){ Page(page:$page,perPage:20){ pageInfo{hasNextPage} mediaList(userId:$uid,type:ANIME){status progress media{id idMal isAdult episodes title{userPreferred native romaji english} description(asHtml:false)}}}}",new JSONObject().put("uid",a.getLong("userId")).put("page",page),a.getString("token")).getJSONObject("Page");
            JSONArray entries=result.getJSONArray("mediaList");for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i),m=e.getJSONObject("media");if(!m.optBoolean("isAdult"))rows.put(new JSONObject().put("media",m).put("state",state(e.getString("status"),false)).put("progress",e.getInt("progress")));}more=result.getJSONObject("pageInfo").getBoolean("hasNextPage");
        }else{
            JSONObject result=api("/users/@me/animelist?limit=20&offset="+((page-1)*20)+"&nsfw=false&fields=list_status,num_episodes,alternative_titles","GET",null,a.getString("token"));JSONArray entries=result.getJSONArray("data");
            for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i),node=e.getJSONObject("node"),s=e.getJSONObject("list_status");rows.put(new JSONObject().put("malId",node.getLong("id")).put("media",new JSONObject().put("id",0).put("idMal",node.getLong("id")).put("episodes",node.optInt("num_episodes")).put("title",new JSONObject().put("userPreferred",node.getString("title")))).put("state",state(s.getString("status"),true)).put("progress",s.getInt("num_episodes_watched")));}more=!result.getJSONObject("paging").optString("next").isEmpty();
        }return new JSONObject().put("rows",rows).put("more",more).put("accountId",a.getLong("userId"));
    }
    JSONObject resolve(JSONObject remote)throws Exception {
        if(remote.getJSONObject("media").optLong("id")>0)return remote;
        JSONObject m=graphql("query ($id:Int!){Media(idMal:$id,type:ANIME){id idMal isAdult episodes title{userPreferred native romaji english} description(asHtml:false)}}",new JSONObject().put("id",remote.getLong("malId")),null).getJSONObject("Media");
        if(m.optBoolean("isAdult")||m.optLong("id")<=0)throw new Exception("此条目不能关联到本地目录");return new JSONObject(remote.toString()).put("media",m);
    }
    JSONObject push(JSONObject local,long expectedAccount)throws Exception {
        JSONObject a=current();if(a.getLong("userId")!=expectedAccount)throw new Exception("连接的账号已变化，请重新确认同步");
        JSONObject media=local.getJSONObject("media");int count=local.getInt("progress");String state=remoteState(local.getString("state"),service.equals(AnimeTrackerAuth.MAL));
        if(count<0||(media.optInt("episodes")>0&&count>media.optInt("episodes")))throw new Exception("请先检查本地追番集数");
        if(service.equals(AnimeTrackerAuth.ANI)){
            JSONObject result=graphql("mutation ($id:Int!,$state:MediaListStatus!,$progress:Int!){SaveMediaListEntry(mediaId:$id,status:$state,progress:$progress){mediaId status progress}}",new JSONObject().put("id",media.getLong("id")).put("state",state).put("progress",count),a.getString("token")).getJSONObject("SaveMediaListEntry");
            if(result.getLong("mediaId")!=media.getLong("id")||result.getInt("progress")!=count||!result.getString("status").equals(state))throw new Exception("远端返回与同步请求不一致，请刷新核对");
        }else{
            long id=media.optLong("idMal");if(id<=0)id=graphql("query($id:Int!){Media(id:$id,type:ANIME){idMal}}",new JSONObject().put("id",media.getLong("id")),null).getJSONObject("Media").optLong("idMal");if(id<=0)throw new Exception("此条目没有 MyAnimeList 编号");
            JSONObject r=api("/anime/"+id+"/my_list_status","PUT",new JSONObject().put("status",state).put("num_watched_episodes",count),a.getString("token"));if(r.getInt("num_episodes_watched")!=count||!r.getString("status").equals(state))throw new Exception("远端返回与同步请求不一致，请刷新核对");
        }return local;
    }
}
