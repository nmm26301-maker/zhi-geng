package cn.zhigeng.app;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Episode associations and playback progress. No provider scripts or catalogue URLs are executed. */
final class AnimeVideoModel {
    static final int MAX_EPISODES=2000;
    static String source(String raw)throws Exception {
        if(raw==null||raw.length()>4096)throw new Exception("视频地址为空或过长");
        String value=raw.trim();for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))throw new Exception("视频地址包含无效字符");
        URI uri=new URI(value);String scheme=uri.getScheme();
        boolean valid="https".equalsIgnoreCase(scheme)?uri.getHost()!=null:"content".equalsIgnoreCase(scheme)&&uri.getRawAuthority()!=null&&uri.getRawAuthority().matches("[A-Za-z0-9._-]+");
        if(!valid||uri.getUserInfo()!=null||uri.getPort()>65535)
            throw new Exception("请选择本地视频，或填写 HTTPS 视频地址");
        return scheme.toLowerCase(Locale.ROOT)+uri.toASCIIString().substring(scheme.length());
    }
    static JSONObject media(JSONObject input)throws Exception {
        long id=input.optLong("id",-1);if(id<0)throw new Exception("动漫条目无效");
        JSONObject media=new JSONObject().put("id",id).put("episodes",Math.max(0,input.optInt("episodes")))
            .put("title",new JSONObject().put("userPreferred",AnimeCatalogClient.title(input)));
        for(String k:new String[]{"idMal","format","seasonYear"})if(input.has(k))media.put(k,input.get(k));return media;
    }
    static String key(long anime,int episode,String uri)throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash=new StringBuilder();for(int i=0;i<12;i++)hash.append(String.format(Locale.ROOT,"%02x",digest[i]&255));
        return anime+"-"+episode+"-"+hash;
    }
    static JSONObject put(JSONObject current,JSONObject anime,int number,String title,String uri,String format)throws Exception {
        JSONObject compact=media(anime);int total=compact.optInt("episodes");
        if(number<1||number>100000||(total>0&&number>total))throw new Exception("集数必须大于0，且不能超过已知总集数");
        uri=source(uri);if(!Arrays.asList("auto","hls","dash","file").contains(format))throw new Exception("视频格式无效");
        if(!current.has(Integer.toString(number))&&current.length()>=MAX_EPISODES)throw new Exception("每部动漫最多关联2000集");
        String clean=title==null?"":title.trim();if(clean.isEmpty())clean="第 "+number+" 集";
        if(clean.codePointCount(0,clean.length())>200)clean=clean.substring(0,clean.offsetByCodePoints(0,200));
        JSONObject previous=current.optJSONObject(Integer.toString(number));String id=key(compact.optLong("id"),number,uri+"\n"+format);
        JSONObject entry=new JSONObject().put("id",id).put("media",compact).put("episode",number).put("title",clean).put("uri",uri).put("format",format)
            .put("position",previous!=null&&uri.equals(previous.optString("uri"))?previous.optLong("position"):0)
            .put("duration",previous!=null&&uri.equals(previous.optString("uri"))?previous.optLong("duration"):0);
        JSONObject copy=new JSONObject(current.toString());copy.put(Integer.toString(number),entry);return copy;
    }
    static JSONObject progress(JSONObject current,int number,String expectedId,long position,long duration)throws Exception {
        JSONObject copy=new JSONObject(current.toString()),entry=copy.optJSONObject(Integer.toString(number));
        if(entry==null||!expectedId.equals(entry.optString("id")))return copy;
        long length=Math.max(0,duration);entry.put("duration",length).put("position",Math.max(0,length>0?Math.min(position,length):position));return copy;
    }
    static JSONObject transport(JSONObject entries,int number,JSONObject options)throws Exception {
        JSONObject copy=new JSONObject(entries.toString()),entry=copy.getJSONObject(Integer.toString(number)),headers=new JSONObject(),given=options.optJSONObject("headers");
        if(given!=null){Iterator<String> keys=given.keys();while(keys.hasNext()){String k=keys.next(),value=given.getString(k),canonical="";
            for(String allowed:new String[]{"User-Agent","Referer","Origin"})if(allowed.equalsIgnoreCase(k))canonical=allowed;
            if(canonical.isEmpty())throw new Exception("视频源要求尚不支持的请求头："+k);
            if(value.length()>4096||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new Exception("视频请求头无效");headers.put(canonical,value);
        }}
        JSONArray subtitles=new JSONArray(),givenSubs=options.optJSONArray("subtitles");if(givenSubs!=null){if(givenSubs.length()>30)throw new Exception("外置字幕过多");for(int i=0;i<givenSubs.length();i++){JSONObject row=givenSubs.getJSONObject(i);String url=source(row.getString("url"));if(!url.startsWith("https://"))throw new Exception("字幕需要 HTTPS 地址");String format=row.optString("format").toLowerCase(Locale.ROOT);if(format.isEmpty()){String path=new URI(url).getPath().toLowerCase(Locale.ROOT);format=path.endsWith(".srt")?"srt":path.endsWith(".ass")||path.endsWith(".ssa")?"ass":"vtt";}
            if(!Arrays.asList("srt","vtt","ass","ssa").contains(format))throw new Exception("字幕格式暂不支持");String language=row.optString("language",row.optString("lang","und")),label=row.optString("label",row.optString("language","字幕"));if(language.length()>40||label.length()>100)throw new Exception("字幕名称过长");subtitles.put(new JSONObject().put("url",url).put("format",format).put("language",language).put("label",label));}}
        StringBuilder material=new StringBuilder(entry.getString("uri")).append('\n').append(entry.getString("format"));for(String k:new String[]{"User-Agent","Referer","Origin"})material.append('\n').append(headers.optString(k));material.append(subtitles.toString());
        String id=key(entry.getJSONObject("media").getLong("id"),number,material.toString());entry.put("id",id).put("cacheScope",id).put("headers",headers).put("subtitles",subtitles);return copy;
    }
    static JSONObject finished(JSONObject watchlist,JSONObject episode)throws Exception {
        JSONObject anime=episode.getJSONObject("media");if(anime.optLong("id")<=0)return new JSONObject(watchlist.toString());
        JSONObject saved=watchlist.optJSONObject(Long.toString(anime.optLong("id")));
        if(saved!=null&&saved.optJSONObject("media")!=null)anime=saved.getJSONObject("media");
        int number=episode.getInt("episode"),count=Math.max(number,saved==null?0:saved.optInt("progress")),total=anime.optInt("episodes");
        if(total>0&&count>total)throw new Exception("原追番进度超过新总集数，请手动检查");
        String state=total>0&&count==total?"已看完":"在看";
        return AnimeWatchlist.update(watchlist,anime,state,count);
    }
    static ArrayList<JSONObject> list(JSONObject entries) {
        ArrayList<JSONObject> out=new ArrayList<>();Iterator<String> keys=entries.keys();while(keys.hasNext()){JSONObject e=entries.optJSONObject(keys.next());if(e!=null)out.add(e);}
        out.sort(Comparator.comparingInt(e->e.optInt("episode")));return out;
    }
    static JSONObject playlist(JSONObject current,JSONObject anime,String raw)throws Exception {
        if(raw.length()>1024*1024)throw new Exception("剧集清单不能超过1MB");
        JSONArray rows=new JSONObject(raw).getJSONArray("episodes");if(rows.length()==0||rows.length()>200)throw new Exception("每次请导入1至200集");
        JSONObject copy=new JSONObject(current.toString());HashSet<Integer> seen=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);int number=row.getInt("episode");
            if(number!=row.getDouble("episode")||!seen.add(number))throw new Exception("清单包含重复或无效集数");
            String uri=source(row.getString("url"));if(!uri.startsWith("https://"))throw new Exception("网络清单只能包含 HTTPS 视频地址");
            copy=put(copy,anime,number,row.optString("title"),uri,row.optString("format","auto"));
        }return copy;
    }
    static boolean adaptive(JSONObject entry) {
        String format=entry.optString("format"),path="";try{path=new URI(entry.optString("uri")).getPath().toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        return format.equals("hls")||format.equals("dash")||path.endsWith(".m3u8")||path.endsWith(".mpd");
    }
    static int natural(String left,String right) {
        Matcher a=Pattern.compile("\\d+|\\D+").matcher(left),b=Pattern.compile("\\d+|\\D+").matcher(right);
        while(a.find()&&b.find()){
            String x=a.group(),y=b.group();int order;
            if(Character.isDigit(x.charAt(0))&&Character.isDigit(y.charAt(0))){x=x.replaceFirst("^0+(?!$)","");y=y.replaceFirst("^0+(?!$)","");order=Integer.compare(x.length(),y.length());if(order==0)order=x.compareTo(y);}
            else order=x.compareToIgnoreCase(y);if(order!=0)return order;
        }return left.compareToIgnoreCase(right);
    }
}
