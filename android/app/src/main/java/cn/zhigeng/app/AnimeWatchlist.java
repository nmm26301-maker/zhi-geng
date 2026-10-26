package cn.zhigeng.app;

import org.json.*;

/** Copy-on-write local progress. Never modifies the user's existing book/movie shelf. */
final class AnimeWatchlist {
    static final String[] STATES={"想看","在看","已看完","搁置","弃番"};
    static JSONObject update(JSONObject current,JSONObject media,String state,int progress)throws Exception {
        long id=media.optLong("id");boolean known=false;for(String value:STATES)if(value.equals(state))known=true;
        if(id<=0||!known||progress<0||progress>100000)throw new Exception("追番状态或集数无效");
        int total=media.optInt("episodes",0);if(total>0&&progress>total)throw new Exception("已看集数不能超过总集数");
        if(!current.has(Long.toString(id))&&current.length()>=500)throw new Exception("本地追番最多保存500部");
        JSONObject copy=new JSONObject(current.toString()),saved=new JSONObject();
        for(String key:new String[]{"id","episodes","format","status","seasonYear"})
            if(media.has(key))saved.put(key,media.get(key));
        saved.put("title",new JSONObject().put("userPreferred",AnimeCatalogClient.title(media)));
        JSONObject airing=media.optJSONObject("nextAiringEpisode");
        if(airing!=null)saved.put("nextAiringEpisode",new JSONObject().put("episode",airing.optInt("episode")).put("airingAt",airing.optLong("airingAt")));
        saved=new JSONObject(saved.toString());
        copy.put(Long.toString(id),new JSONObject().put("media",saved).put("state",state).put("progress",progress));return copy;
    }
    static JSONObject remove(JSONObject current,long id)throws Exception {
        JSONObject copy=new JSONObject(current.toString());copy.remove(Long.toString(id));return copy;
    }
}
