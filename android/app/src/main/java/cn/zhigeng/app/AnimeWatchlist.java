package cn.zhigeng.app;

import org.json.*;
import java.util.*;

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
        JSONObject titles=new JSONObject(),sourceTitles=media.optJSONObject("title");
        if(sourceTitles!=null)for(String key:new String[]{"userPreferred","native","romaji","english"})
            if(!sourceTitles.isNull(key))titles.put(key,bounded(sourceTitles.optString(key),200));
        if(titles.length()==0)titles.put("userPreferred",AnimeCatalogClient.title(media));
        saved.put("title",titles);
        if(!media.isNull("description"))saved.put("description",bounded(media.optString("description"),4000));
        JSONObject airing=media.optJSONObject("nextAiringEpisode");
        if(airing!=null)saved.put("nextAiringEpisode",new JSONObject().put("episode",airing.optInt("episode")).put("airingAt",airing.optLong("airingAt")));
        saved=new JSONObject(saved.toString());
        if("已看完".equals(state)&&total>0)progress=total;
        copy.put(Long.toString(id),new JSONObject().put("media",saved).put("state",state).put("progress",progress)
            .put("updatedAt",System.currentTimeMillis()));return copy;
    }
    private static String bounded(String value,int maximum) {
        return value.codePointCount(0,value.length())>maximum?value.substring(0,value.offsetByCodePoints(0,maximum)):value;
    }
    static boolean canAdvance(JSONObject saved) {
        if(saved==null)return false;
        String state=saved.optString("state");JSONObject media=saved.optJSONObject("media");
        int progress=saved.optInt("progress",-1),total=media==null?0:media.optInt("episodes",0);
        return media!=null&&(state.equals("想看")||state.equals("在看"))&&progress>=0&&progress<100000&&(total<=0||progress<total);
    }
    static JSONObject advance(JSONObject current,long id)throws Exception {
        JSONObject saved=current.optJSONObject(Long.toString(id));
        if(!canAdvance(saved))throw new Exception("当前追番不能增加集数，请在更新追番中检查状态");
        JSONObject media=saved.getJSONObject("media");int count=saved.getInt("progress")+1;
        String state=media.optInt("episodes",0)==count?"已看完":"在看";
        return update(current,media,state,count);
    }
    static ArrayList<JSONObject> select(JSONObject current,String query,String state,boolean recent) {
        String needle=query.trim().toLowerCase(Locale.ROOT);ArrayList<JSONObject> selected=new ArrayList<>();
        Iterator<String> keys=current.keys();
        while(keys.hasNext()) {
            JSONObject saved=current.optJSONObject(keys.next());JSONObject media=saved==null?null:saved.optJSONObject("media");
            if(media==null||media.optLong("id")<=0||(!state.isEmpty()&&!state.equals(saved.optString("state"))))continue;
            JSONObject titles=media.optJSONObject("title");boolean matches=needle.isEmpty()||AnimeCatalogClient.title(media).toLowerCase(Locale.ROOT).contains(needle);
            if(titles!=null)for(String key:new String[]{"userPreferred","native","romaji","english"})
                if(!titles.isNull(key)&&titles.optString(key).toLowerCase(Locale.ROOT).contains(needle))matches=true;
            if(matches)selected.add(media);
        }
        selected.sort((a,b)->{
            if(recent) {
                JSONObject left=current.optJSONObject(Long.toString(a.optLong("id"))),right=current.optJSONObject(Long.toString(b.optLong("id")));
                int byTime=Long.compare(right==null?0:right.optLong("updatedAt"),left==null?0:left.optLong("updatedAt"));
                if(byTime!=0)return byTime;
            }
            int byTitle=AnimeCatalogClient.title(a).compareToIgnoreCase(AnimeCatalogClient.title(b));
            return byTitle==0?Long.compare(a.optLong("id"),b.optLong("id")):byTitle;
        });return selected;
    }
    static JSONObject remove(JSONObject current,long id)throws Exception {
        JSONObject copy=new JSONObject(current.toString());copy.remove(Long.toString(id));return copy;
    }
}
