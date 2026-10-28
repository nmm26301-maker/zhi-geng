package cn.zhigeng.app;
import org.json.*;

/** Wire shape used by Seanime/Anix onlinestream providers, independent of any one website. */
final class AnimeProviderModel {
    static JSONObject search(JSONObject anime,String query,boolean dub)throws Exception {
        if(query.trim().isEmpty()||query.length()>200)throw new Exception("请填写1至200字的动漫名称");JSONObject title=anime.optJSONObject("title");if(title==null)title=new JSONObject();
        JSONObject media=new JSONObject().put("id",anime.optLong("id")).put("idMal",anime.optLong("idMal")).put("englishTitle",title.optString("english",query)).put("romajiTitle",title.optString("romaji",query)).put("episodeCount",anime.optInt("episodes")).put("absoluteSeasonOffset",0).put("synonyms",new JSONArray().put(title.optString("native",query))).put("isAdult",false).put("format",anime.optString("format")).put("status",JSONObject.NULL);
        if(anime.optInt("seasonYear")>0)media.put("startDate",new JSONObject().put("year",anime.getInt("seasonYear")));
        return new JSONObject().put("media",media).put("query",query.trim()).put("dub",dub).put("year",anime.optInt("seasonYear"));
    }
    static JSONArray series(String raw)throws Exception{JSONArray values=new JSONArray(raw),out=new JSONArray();if(values.length()>2000)throw new Exception("扩展搜索结果过多");for(int i=0;i<values.length();i++){JSONObject row=values.optJSONObject(i);if(row!=null&&row.has("id")&&!row.optBoolean("isAdult")&&row.optString("title").length()>0)out.put(row);}return out;}
    static JSONArray episodes(String raw)throws Exception {
        JSONArray values=new JSONArray(raw);if(values.length()>5000)throw new Exception("扩展返回剧集过多");java.util.ArrayList<JSONObject> sorted=new java.util.ArrayList<>();java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(int i=0;i<values.length();i++){JSONObject row=values.optJSONObject(i);if(row==null||row.isNull("id")||!Double.isFinite(row.optDouble("number"))||row.optDouble("number")<0)continue;String id=String.valueOf(row.get("id"));if(!ids.add(id))continue;sorted.add(new JSONObject(row.toString()).put("id",id));}
        sorted.sort(java.util.Comparator.comparingDouble(o->o.optDouble("number")));JSONArray out=new JSONArray();for(JSONObject row:sorted)out.put(row);return out;
    }
    static JSONArray streams(String raw)throws Exception {
        JSONObject result=new JSONObject(raw);JSONArray values=result.getJSONArray("videoSources"),out=new JSONArray();if(values.length()>100)throw new Exception("扩展返回视频源过多");
        for(int i=0;i<values.length();i++){JSONObject source=values.getJSONObject(i);if(source.optBoolean("drm"))continue;String url=source.getString("url");if(url.startsWith("//"))url="https:"+url;url=AnimeVideoModel.source(url);if(!url.startsWith("https://"))continue;
            String type=source.optString("type"),format=type.equals("hls")||type.equals("m3u8")?"hls":type.equals("dash")||type.equals("mpd")?"dash":"auto";
            JSONObject options=new JSONObject().put("headers",result.optJSONObject("headers")==null?new JSONObject():result.getJSONObject("headers")).put("subtitles",source.optJSONArray("subtitles")==null?new JSONArray():source.getJSONArray("subtitles"));
            out.put(new JSONObject().put("url",url).put("format",format).put("quality",source.optString("quality","自动")+" "+source.optString("label","")).put("options",options));
        }return out;
    }
}
