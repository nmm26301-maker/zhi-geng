package cn.zhigeng.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Public AniList catalogue queries, independently adapted for Android; no tracker credentials. */
class AnimeCatalogClient {
    static final String ENDPOINT="https://graphql.anilist.co";
    static final String QUERY="query ($search: String, $page: Int, $sort: [MediaSort]) { Page(page: $page, perPage: 20) { pageInfo { hasNextPage } media(type: ANIME, isAdult: false, search: $search, sort: $sort) { id isAdult title { userPreferred native romaji english } episodes format status seasonYear description(asHtml: false) nextAiringEpisode { episode airingAt } } } }";
    private final String endpoint;
    private volatile HttpURLConnection active;
    private long retryAt;
    AnimeCatalogClient(){endpoint=ENDPOINT;}
    AnimeCatalogClient(String endpoint)throws Exception {
        URI uri=new URI(endpoint);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new Exception("动漫服务地址不可用");
        this.endpoint=endpoint;
    }
    static JSONObject body(String term,int page)throws Exception {
        term=term.trim();if(term.codePointCount(0,term.length())>100||page<1||page>500)throw new Exception("搜索词过长或页码不可用");
        return new JSONObject().put("query",QUERY).put("variables",new JSONObject().put("search",term.isEmpty()?JSONObject.NULL:term)
            .put("page",page).put("sort",new JSONArray().put(term.isEmpty()?"TRENDING_DESC":"SEARCH_MATCH")));
    }
    JSONObject search(String term,int page)throws Exception {
        if(System.currentTimeMillis()<retryAt)throw new Exception("动漫服务请求较多，请稍后重试");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();active=c;
        c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");c.setDoOutput(true);
        try {
            byte[] payload=body(term,page).toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()){out.write(payload);}
            int status=c.getResponseCode();
            if(status==429){long seconds=60;try{seconds=Long.parseLong(c.getHeaderField("Retry-After"));}catch(Exception ignored){}
                retryAt=System.currentTimeMillis()+Math.max(1,Math.min(300,seconds))*1000;throw new Exception("动漫服务请求较多，请稍后重试");}
            if(status!=200)throw new Exception("动漫目录暂不可用（HTTP "+status+"），本地追番仍可使用");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[8192];int count;
                while((count=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("已停止请求");
                    if(out.size()+count>2*1024*1024)throw new Exception("动漫目录返回的数据过大");out.write(bytes,0,count);}
                return page(new JSONObject(out.toString("UTF-8")));
            }
        }finally{c.disconnect();if(active==c)active=null;}
    }
    static JSONObject page(JSONObject response)throws Exception {
        JSONArray errors=response.optJSONArray("errors");
        if(errors!=null&&errors.length()>0)throw new Exception("动漫目录返回错误，请稍后重试；本地追番仍可使用");
        JSONObject data=response.optJSONObject("data"),page=data==null?null:data.optJSONObject("Page");
        if(page==null||page.optJSONArray("media")==null||page.optJSONObject("pageInfo")==null)throw new Exception("动漫目录响应无法识别");
        return page;
    }
    static String title(JSONObject media) {
        JSONObject title=media.optJSONObject("title");
        if(title!=null)for(String key:new String[]{"userPreferred","native","romaji","english"})
            if(!title.isNull(key)&&!title.optString(key).trim().isEmpty()){
                String value=title.optString(key).trim();return value.codePointCount(0,value.length())>200?value.substring(0,value.offsetByCodePoints(0,200)):value;
            }
        return "未命名动漫";
    }
    void cancel(){HttpURLConnection c=active;if(c!=null)c.disconnect();}
}
