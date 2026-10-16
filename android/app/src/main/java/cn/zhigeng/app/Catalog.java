package cn.zhigeng.app;

import android.content.Context;
import android.net.Uri;
import android.text.Html;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Native HTTP calls; Z-Library EAPI mapping adapted from heartleo/zlib, MIT. */
final class Catalog {
    static final String[] DOMAINS={"https://z-lib.gd","https://z-lib.gl","https://z-library.ec","https://article.sk","https://articles.sk","https://zlib.bz","https://1lib.sk","https://z-lib.fm","https://z-lib.sk","https://zliba.ru"};
    static class Query {
        String type="all",text="",year="all",tag="",provider="public",language="",extension="",from="",to="",sort="popular",region="CN",rankSource="editor";
        int page=1;boolean exact=false;boolean forceLive=false;boolean ranking=false;
        Query copy(){Query q=new Query();q.type=type;q.text=text;q.year=year;q.tag=tag;q.provider=provider;q.language=language;q.extension=extension;q.from=from;q.to=to;q.sort=sort;q.page=page;q.exact=exact;q.forceLive=forceLive;q.region=region;q.rankSource=rankSource;q.ranking=ranking;return q;}
    }
    static class Result {List<JSONObject> items=new ArrayList<>();String note="";boolean more=false;}
    private final Context ctx;private final SecretStore secrets;
    Catalog(Context c,SecretStore s){ctx=c.getApplicationContext();secrets=s;}
    private static String readString(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    static String clean(String s){return Html.fromHtml(s==null?"":s,Html.FROM_HTML_MODE_LEGACY).toString().trim();}
    static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return "";}}
    static String secureUrl(String raw){try{URI u=URI.create(raw);return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null?u.toString():"";}catch(Exception e){return "";}}
    static String domain(String raw)throws Exception{for(String d:DOMAINS)if(d.equals(raw))return d;throw new Exception("请选择支持的源站地址");}
    private static class Reply {JSONObject data;Map<String,List<String>> headers;int status;}
    private Reply request(String url,String form,Map<String,String> headers)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(9500);c.setReadTimeout(11000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","ZhigengAndroid/0.6 (heartleo-zlib adapter)");
        if(headers!=null)for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
        try{
            if(form!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(form.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();if(code>=300&&code<400)throw new Exception("来源发生跳转，请在设置中选择最终源站");
            InputStream stream=code>=400?c.getErrorStream():c.getInputStream();if(stream==null)throw new Exception("来源返回 HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            try(InputStream in=stream){while((n=in.read(buffer))!=-1){if(out.size()+n>5_000_000)throw new Exception("返回数据过大");out.write(buffer,0,n);}}
            String body=new String(out.toByteArray(),StandardCharsets.UTF_8);Reply reply=new Reply();reply.status=code;reply.headers=c.getHeaderFields();
            try{String trimmed=body.trim();reply.data=trimmed.startsWith("[")?new JSONObject().put("items",new JSONArray(trimmed)):new JSONObject(trimmed);}catch(JSONException e){throw new Exception("来源返回验证页或非 JSON 数据，请稍后再试");}
            if(code>=400&&!(reply.data.has("success")))throw new Exception(code==401?"凭证无效或尚未激活（HTTP 401）":code==429?"来源请求限流，请稍后再试":"来源返回 HTTP "+code);
            return reply;
        }catch(SocketTimeoutException e){throw new Exception("连接超时，请检查网络或稍后重试");}finally{c.disconnect();}
    }
    private JSONObject get(String url)throws Exception{return request(url,null,null).data;}
    private static JSONObject item(String id,String type,String title,String author,String year,String source,String url,String cover,String summary)throws JSONException{
        return new JSONObject().put("id",id).put("type",type).put("title",clean(title)).put("author",clean(author)).put("year",year).put("source",source).put("url",secureUrl(url)).put("image",secureUrl(cover)).put("summary",clean(summary)).put("score",0);
    }
    List<JSONObject> bundled(Query q)throws Exception{
        String asset=q.type.equals("book")?"books.json":"weekly.json";JSONObject data;
        try(InputStream in=ctx.getAssets().open(asset)){data=new JSONObject(readString(in));}
        List<JSONObject> list=new ArrayList<>();JSONArray entries=data.optJSONArray("items");if(entries==null)return list;
        for(int i=0;i<entries.length();i++){
            JSONObject v=entries.getJSONObject(i);String type=v.optString("type","book");if(!q.type.equals("all")&&!type.equals(q.type))continue;
            String year=v.optString("year","");if(!q.year.equals("all")&&!q.year.equals(year))continue;
            if(!q.from.isEmpty()&&year.compareTo(q.from)<0||!q.to.isEmpty()&&year.compareTo(q.to)>0)continue;
            String text=(v.optString("title")+" "+v.optString("author")).toLowerCase(Locale.ROOT);
            if(!q.text.isEmpty()&&!text.contains(q.text.toLowerCase(Locale.ROOT)))continue;
            if(!q.language.isEmpty()&&(v.optJSONArray("language")==null||!v.optJSONArray("language").toString().contains(q.language)))continue;
            JSONObject normalized=item(v.optString("id"),type,v.optString("title"),v.optString("author"),year,v.optString("source")+" · 内置目录",v.optString("url"),v.optString("image"),v.optString("summary",v.optString("description")));
            normalized.put("score",v.optDouble("score",0));list.add(normalized);
        }
        int start=(q.page-1)*24;return start>=list.size()?new ArrayList<>():new ArrayList<>(list.subList(start,Math.min(start+24,list.size())));
    }
    Result search(Query q)throws Exception{
        if(q.type.equals("book")&&q.provider.equals("zlib"))return zsearch(q);
        if(q.ranking&&q.rankSource.equals("tmdb")){
            if(secrets.get("tmdb").isEmpty())throw new Exception("TMDB 评分榜需要有效凭证，可先浏览知更编辑推荐榜。");
            if(q.type.equals("book"))throw new Exception("中文图书提供编辑推荐榜，请选择影视分类查看 TMDB 评分榜。");
            Query live=q.copy();live.sort="rating";live.text="";Result rank=new Result();rank.items=tmdb(live,q.type);rank.more=rank.items.size()>=20;rank.note="TMDB 国内用户评分榜 · 至少50票 · "+LocalDate.now();return rank;
        }
        if(q.ranking||q.region.equals("CN")&&!q.forceLive){Result domestic=domestic(q);if(!domestic.items.isEmpty()||q.text.isEmpty()){if(q.ranking||q.text.isEmpty()||secrets.get("tmdb").isEmpty())return domestic;}}
        if(q.region.equals("CN")&&!q.type.equals("book")&&!q.type.equals("all")){
            if(secrets.get("tmdb").isEmpty()){Result local=domestic(q);local.note="当前为国内编辑书目；更多实时影视结果需配置 TMDB。";return local;}
            Result cn=new Result();cn.items=tmdb(q,q.type);cn.more=cn.items.size()>=20;return cn;
        }

        Result result=new Result();if(q.type.equals("all")&&q.text.isEmpty()&&q.tag.isEmpty()&&!q.forceLive){result.items=bundled(q);if(!result.items.isEmpty()){result.note="内置真实目录快照，搜索可在线找作品。";result.more=result.items.size()>=24;return result;}}List<Callable<List<JSONObject>>> calls=new ArrayList<>();
        if(q.type.equals("all")){calls.add(()->anime(q));calls.add(()->books(q));if(!secrets.get("tmdb").isEmpty())calls.add(()->tmdb(q,"movie"));}
        else if(q.type.equals("book"))return publicBooks(q);
        else if(q.type.equals("anime"))calls.add(()->anime(q));
        else if(!secrets.get("tmdb").isEmpty())calls.add(()->tmdb(q,q.type));
        else if(q.type.equals("tv")||q.type.equals("variety"))calls.add(()->tv(q));
        else {result.note="电影搜索需要 TMDB 凭证，点下方「设置」填写。书籍和动漫无需 TMDB。";return result;}
        ExecutorService pool=Executors.newFixedThreadPool(Math.max(1,calls.size()));
        try{List<Future<List<JSONObject>>> futures=pool.invokeAll(calls,23,TimeUnit.SECONDS);for(Future<List<JSONObject>> f:futures){try{result.items.addAll(f.get());}catch(Exception e){result.note="部分来源暂时不可用，可点击重试。";}}}finally{pool.shutdownNow();}
        if(result.items.isEmpty()&&q.text.isEmpty()){result.items=bundled(q);if(!result.items.isEmpty())result.note="当前显示随 App 保存的真实目录快照。";}
        result.more=result.items.size()>=20;return result;
    }
    private Result publicBooks(Query q)throws Exception{
        Result r=new Result();if(q.text.isEmpty()&&q.language.isEmpty()&&q.tag.isEmpty()){r.items=bundled(q);if(!r.items.isEmpty()){r.note="内置真实书目，输入书名可在线搜索。";r.more=r.items.size()>=24;return r;}}
        List<Callable<List<JSONObject>>> calls=new ArrayList<>();calls.add(()->books(q));calls.add(()->gutenberg(q));
        if(q.page==1&&q.year.equals("all")&&q.from.isEmpty()&&q.to.isEmpty()&&q.language.isEmpty()&&q.extension.isEmpty())calls.add(()->wiki(q));
        ExecutorService pool=Executors.newFixedThreadPool(calls.size());int failures=0;
        try{for(Future<List<JSONObject>> f:pool.invokeAll(calls,23,TimeUnit.SECONDS)){try{r.items.addAll(f.get());}catch(Exception e){failures++;}}}finally{pool.shutdownNow();}
        LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();for(JSONObject v:r.items)unique.putIfAbsent(v.optString("title").toLowerCase(Locale.ROOT)+v.optString("author"),v);r.items=new ArrayList<>(unique.values());
        if(r.items.isEmpty()&&failures>0)r.note="部分图书源连接失败。请重试，或连接自己的 Z-Library 账号搜索。";
        r.more=r.items.size()>=20;return r;
    }
    private List<JSONObject> books(Query q)throws Exception{
        String search=q.text.isEmpty()?"subject:"+(q.tag.isEmpty()?"fiction":q.tag):q.exact?"\""+q.text.replace("\"","")+"\"":q.text;
        if(!q.year.equals("all"))search+=" first_publish_year:"+q.year;
        if(!q.from.isEmpty()||!q.to.isEmpty())search+=" first_publish_year:["+(q.from.isEmpty()?"0":q.from)+" TO "+(q.to.isEmpty()?"2100":q.to)+"]";
        if(!q.language.isEmpty())search+=" language:"+q.language;
        try{
            JSONObject d=get("https://openlibrary.org/search.json?q="+enc(search)+"&limit=24&page="+q.page+"&fields=key,title,author_name,first_publish_year,cover_i,ratings_average");
            JSONArray entries=d.optJSONArray("docs");List<JSONObject> out=new ArrayList<>();if(entries!=null)for(int i=0;i<entries.length();i++){
                JSONObject v=entries.getJSONObject(i);JSONArray authors=v.optJSONArray("author_name");String cover=v.has("cover_i")?"https://covers.openlibrary.org/b/id/"+v.optString("cover_i")+"-M.jpg":"";
                JSONObject b=item("book:ol:"+v.optString("key"),"book",v.optString("title"),authors==null?"":authors.optString(0),v.optString("first_publish_year"),"Open Library","https://openlibrary.org"+v.optString("key"),cover,"");b.put("score",v.optDouble("ratings_average",0));out.add(b);
            }return out;
        }catch(Exception primary){
            JSONObject d=get("https://www.googleapis.com/books/v1/volumes?q="+enc(q.text.isEmpty()?"subject:fiction":q.text)+"&maxResults=24&startIndex="+((q.page-1)*24));
            List<JSONObject> out=new ArrayList<>();JSONArray entries=d.optJSONArray("items");if(entries!=null)for(int i=0;i<entries.length();i++){
                JSONObject v=entries.getJSONObject(i),info=v.optJSONObject("volumeInfo");if(info==null)continue;String year=info.optString("publishedDate");if(year.length()>=4)year=year.substring(0,4);if(!yearMatches(year,q))continue;
                if(!q.language.isEmpty()&&!info.optString("language").equals(language2(q.language)))continue;
                JSONArray authors=info.optJSONArray("authors");JSONObject images=info.optJSONObject("imageLinks");
                out.add(item("book:google:"+v.optString("id"),"book",info.optString("title"),authors==null?"":authors.optString(0),year,"Google Books",info.optString("infoLink"),images==null?"":images.optString("thumbnail").replace("http:","https:"),info.optString("description")));
            }return out;
        }
    }
    private boolean yearMatches(String year,Query q){return (q.year.equals("all")||q.year.equals(year))&&(q.from.isEmpty()||year.compareTo(q.from)>=0)&&(q.to.isEmpty()||!year.isEmpty()&&year.compareTo(q.to)<=0);}
    private static String language2(String code){return switch(code){case "chi"->"zh";case "eng"->"en";case "jpn"->"ja";default->code;};}
    private List<JSONObject> wiki(Query q)throws Exception{
        List<JSONObject> out=new ArrayList<>();if(q.text.isEmpty())return out;
        JSONObject d=get("https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=zh&uselang=zh&type=item&limit=30&search="+enc(q.text));JSONArray a=d.optJSONArray("search");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);String desc=v.optString("description");if(!desc.matches(".*(小说|书籍|图书|著作|诗集|散文|传记|漫画|novel|book|poem|written work).*"))continue;if(desc.matches(".*(电影|电视剧|电子游戏|film|television|video game).*"))continue;
            out.add(item("book:wiki:"+v.optString("id"),"book",v.optString("label"),"","","Wikidata","https://www.wikidata.org/wiki/"+v.optString("id"),"",desc));}return out;
    }
    private List<JSONObject> gutenberg(Query q)throws Exception{
        List<JSONObject> out=new ArrayList<>();if(q.text.isEmpty()||!q.year.equals("all")||!q.from.isEmpty()||!q.to.isEmpty())return out;
        JSONObject d=get("https://gutendex.com/books/?search="+enc(q.text)+"&page="+q.page+(q.language.isEmpty()?"":"&languages="+language2(q.language)));JSONArray a=d.optJSONArray("results");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);JSONArray authors=v.optJSONArray("authors");JSONObject formats=v.optJSONObject("formats");out.add(item("book:gutenberg:"+v.optInt("id"),"book",v.optString("title"),authors==null||authors.length()==0?"":authors.getJSONObject(0).optString("name"),"","Project Gutenberg","https://www.gutenberg.org/ebooks/"+v.optInt("id"),formats==null?"":formats.optString("image/jpeg"),"公版图书资料与阅读入口"));}return out;
    }
    private List<JSONObject> anime(Query q)throws Exception{
        String url="https://api.jikan.moe/v4/anime?sfw=true&limit=24&page="+q.page+(q.text.isEmpty()?"&order_by=members&sort=desc":"&q="+enc(q.text));if(!q.year.equals("all"))url+="&start_date="+q.year+"-01-01&end_date="+q.year+"-12-31";
        JSONObject d=get(url);JSONArray a=d.optJSONArray("data");List<JSONObject> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject v=a.getJSONObject(i);JSONObject images=v.optJSONObject("images"),jpg=images==null?null:images.optJSONObject("jpg");String year=v.optString("year","");if(year.equals("null"))year="";
            JSONObject b=item("anime:"+v.optInt("mal_id"),"anime",v.optString("title_english").equals("null")?v.optString("title"):v.optString("title_english",v.optString("title")),"",year,"MyAnimeList / Jikan",v.optString("url"),jpg==null?"":jpg.optString("image_url"),v.optString("synopsis"));b.put("score",v.optDouble("score",0));out.add(b);
        }return out;
    }
    private List<JSONObject> tmdb(Query q,String type)throws Exception{
        boolean movie=type.equals("movie");String route=movie?"movie":"tv",key=secrets.get("tmdb");
        Map<String,String> headers=new HashMap<>();String url="https://api.themoviedb.org/3/"+(q.text.isEmpty()?"discover/":"search/")+route+"?language=zh-CN&include_adult=false&page="+q.page;
        if(key.matches("[a-fA-F0-9]{32}"))url+="&api_key="+key;else headers.put("Authorization","Bearer "+key);
        if(!q.text.isEmpty())url+="&query="+enc(q.text);
        if(!q.year.equals("all"))url+="&"+(q.text.isEmpty()?(movie?"primary_release_year":"first_air_date_year"):(movie?"year":"first_air_date_year"))+"="+q.year;
        if(q.text.isEmpty()&&q.region.equals("CN"))url+="&with_origin_country=CN";
        if(q.text.isEmpty()&&type.equals("anime"))url+="&with_genres=16";
        if(q.ranking)url+="&"+(movie?"primary_release_date.lte=":"first_air_date.lte=")+LocalDate.now();
        if(q.text.isEmpty()&&type.equals("variety"))url+="&with_genres=10764%7C10767";
        if(q.text.isEmpty()&&q.sort.equals("rating"))url+="&sort_by=vote_average.desc&vote_count.gte=50";
        if(q.text.isEmpty()&&!q.tag.isEmpty())url+="&with_genres="+genre(q.tag,movie);
        JSONArray a=request(url,null,headers).data.optJSONArray("results");List<JSONObject> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject v=a.getJSONObject(i);
            if(q.region.equals("CN")&&!q.text.isEmpty()){
                JSONArray origin=v.optJSONArray("origin_country");String language=v.optString("original_language");
                if(movie&&!language.equals("zh")&&!language.equals("cn"))continue;
                if(!movie&&(origin==null||!origin.toString().contains("CN")))continue;
                if(type.equals("anime")&&(v.optJSONArray("genre_ids")==null||!v.optJSONArray("genre_ids").toString().contains("16")))continue;
            }
            if(type.equals("variety")&&!q.text.isEmpty()){JSONArray genres=v.optJSONArray("genre_ids");if(genres==null||!genres.toString().contains("10764")&&!genres.toString().contains("10767"))continue;}
            String date=v.optString(movie?"release_date":"first_air_date");String year=date.length()>=4?date.substring(0,4):"";String path=v.optString("poster_path");
            JSONObject b=item(type+":"+v.optInt("id"),type,v.optString(movie?"title":"name"),"",year,"TMDB","https://www.themoviedb.org/"+route+"/"+v.optInt("id"),path.isEmpty()||path.equals("null")?"":"https://image.tmdb.org/t/p/w342"+path,v.optString("overview"));b.put("score",v.optDouble("vote_average",0)).put("votes",v.optInt("vote_count",0));out.add(b);
        }return out;
    }
    private static String genre(String tag,boolean movie){return switch(tag){case "mystery"->"9648";case "science_fiction"->movie?"878":"10765";case "romance"->"10749";case "history"->"36";case "humor"->"35";default->"18";};}
    void verifyTmdb(String value)throws Exception{
        String key=value.trim().replaceFirst("(?i)^Bearer\\s+","");Map<String,String> h=new HashMap<>();String url="https://api.themoviedb.org/3/configuration";
        if(key.matches("[a-fA-F0-9]{32}"))url+="?api_key="+key;else if(key.startsWith("eyJ")&&key.split("\\.").length==3)h.put("Authorization","Bearer "+key);else throw new Exception("请填写 32 位 API Key 或完整 Read Access Token");
        if(!request(url,null,h).data.has("images"))throw new Exception("来源未返回有效配置");secrets.put("tmdb",key);
    }
    private List<JSONObject> tv(Query q)throws Exception{
        List<JSONObject> out=new ArrayList<>();String url=q.text.isEmpty()?"https://api.tvmaze.com/schedule/web?date="+LocalDate.now():"https://api.tvmaze.com/search/shows?q="+enc(q.text);
        JSONArray a=get(url).optJSONArray("items");if(a!=null)for(int i=0;i<Math.min(40,a.length());i++){
            JSONObject show=a.getJSONObject(i).optJSONObject("show");if(show==null)continue;String type=show.optString("type"),genres=show.optJSONArray("genres")==null?"":show.optJSONArray("genres").toString();boolean variety=(type+genres).matches("(?i).*(reality|talk show|game show).*" );if(variety!=q.type.equals("variety"))continue;
            String date=show.optString("premiered"),year=date.length()>=4?date.substring(0,4):"";if(!yearMatches(year,q))continue;JSONObject image=show.optJSONObject("image");out.add(item(q.type+":maze:"+show.optInt("id"),q.type,show.optString("name"),"",year,"TVmaze",show.optString("url"),image==null?"":image.optString("medium"),show.optString("summary")));
        }return out;
    }
    Result domestic(Query q)throws Exception{
        JSONObject data;try(InputStream in=ctx.getAssets().open("domestic.json")){data=new JSONObject(readString(in));}
        ArrayList<JSONObject> found=new ArrayList<>();JSONArray a=data.getJSONArray("items");
        for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);if(!q.type.equals("all")&&!v.optString("type").equals(q.type))continue;
            if(!yearMatches(v.optString("year"),q))continue;
            String hay=(v.optString("title")+" "+v.optString("author")+" "+v.optString("tag")).toLowerCase(Locale.ROOT);
            if(!q.text.isEmpty()&&!hay.contains(q.text.toLowerCase(Locale.ROOT)))continue;found.add(new JSONObject(v.toString()));}
        Result r=new Result();int start=(q.page-1)*24,end=Math.min(start+24,found.size());if(start<found.size())r.items.addAll(found.subList(start,end));r.more=end<found.size();r.note="知更编辑推荐 · 顺序不代表平台热度";return r;
    }
    String resolveDownload(JSONObject item)throws Exception{
        String id=item.optString("zlibId"),hash=item.optString("zlibHash");
        if(!id.matches("[0-9]{1,30}")||!hash.matches("[A-Za-z0-9_-]{1,100}"))throw new Exception("请先从 Z-Library 搜索结果中选择具体版本");
        String origin=domain(secrets.get("zdomain"));JSONObject data=request(origin+"/eapi/book/"+id+"/"+hash+"/file",null,session()).data;
        if(data.optInt("success")!=1)throw new Exception("来源未提供下载链接，请检查账号额度、权限或源站状态");
        JSONObject file=data.optJSONObject("file");String url=file==null?"":file.optString("downloadLink");
        if(url.isEmpty())url=data.optString("downloadLink");if(url.isEmpty())url=data.optString("url");if(url.isEmpty())url=data.optString("link");
        if(url.startsWith("/"))url=origin+url;url=secureUrl(url);if(url.isEmpty())throw new Exception("来源返回的下载链接无效");return url;
    }
    Map<String,String> downloadHeaders(String url)throws Exception{
        String origin=domain(secrets.get("zdomain"));return new URI(url).getHost().equals(new URI(origin).getHost())?session():Collections.emptyMap();
    }
    private Map<String,String> session()throws Exception{
        String id=secrets.get("zid"),key=secrets.get("zkey");if(!id.matches("[0-9]{1,30}")||!key.matches("[-a-zA-Z0-9_]{6,256}"))throw new Exception("请先在设置中连接自己的 Z-Library 账号");
        Map<String,String> h=new HashMap<>();h.put("remix-userid",id);h.put("remix-userkey",key);h.put("Cookie","remix_userid="+id+"; remix_userkey="+key);return h;
    }
    void login(String origin,String email,String password)throws Exception{
        domain(origin);String form="email="+enc(email)+"&password="+enc(password);Reply reply=request(origin+"/eapi/user/login",form,null);JSONObject user=reply.data.optJSONObject("user");String id="",key="";
        if(reply.data.optInt("success")==1&&user!=null){id=user.optString("id");key=user.optString("remix_userkey",user.optString("remix_user_key"));}
        else{
            reply=request(origin+"/rpc.php",form+"&isModal=true&site_mode=books&action=login&isSingleLogin=1&redirectUrl=&gg_json_mode=1",null);JSONObject response=reply.data.optJSONObject("response");if(response!=null&&!response.isNull("validationError"))throw new Exception("登录未通过，请核对账号或在来源站完成验证");
            for(Map.Entry<String,List<String>> entry:reply.headers.entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie"))for(String cookie:entry.getValue()){for(String part:cookie.split("[,;]")){String p=part.trim();if(p.startsWith("remix_userid="))id=p.substring(13);if(p.startsWith("remix_userkey="))key=p.substring(14);}}
        }
        if(!id.matches("[0-9]{1,30}")||!key.matches("[-a-zA-Z0-9_]{6,256}"))throw new Exception("来源未返回有效会话，请稍后重试");
        secrets.put("zid",id);secrets.put("zkey",key);secrets.put("zdomain",origin);
    }
    private Result zsearch(Query q)throws Exception{
        Map<String,String> h=session();String origin=domain(secrets.get("zdomain"));if(q.text.trim().isEmpty())throw new Exception("请输入书名或作者，再点搜索");
        String form="message="+enc(q.text)+"&page="+q.page+"&limit=24";
        if(q.exact)form+="&e=1";String from=q.year.equals("all")?q.from:q.year,to=q.year.equals("all")?q.to:q.year;
        if(!from.isEmpty())form+="&yearFrom="+enc(from);if(!to.isEmpty())form+="&yearTo="+enc(to);
        String lang=switch(q.language){case "chi"->"chinese";case "eng"->"english";case "jpn"->"japanese";default->"";};
        if(!lang.isEmpty())form+="&languages%5B%5D="+lang;if(!q.extension.isEmpty())form+="&extensions%5B%5D="+enc(q.extension);
        JSONObject d=request(origin+"/eapi/book/search",form,h).data;if(d.optInt("success")!=1)throw new Exception("账号搜索被来源拒绝，请重新连接账号或确认来源站状态");
        Result r=new Result();JSONArray a=d.optJSONArray("books");if(a!=null)for(int i=0;i<a.length();i++){
            JSONObject v=a.getJSONObject(i);String path=v.optString("href");String url=path.startsWith("/")?origin+path:path;if(!url.startsWith(origin+"/"))url=origin+"/book/"+v.optString("id")+"/"+v.optString("hash");
            JSONObject b=item("book:zlib:"+v.optString("id")+":"+v.optString("hash"),"book",v.optString("title"),v.optString("author"),v.optString("year"),"Z-Library · "+v.optString("extension").toUpperCase(Locale.ROOT),url,v.optString("cover"),v.optString("description"));b.put("zlibId",v.optString("id")).put("zlibHash",v.optString("hash")).put("format",v.optString("extension").toLowerCase(Locale.ROOT));r.items.add(b);
        }
        JSONObject pagination=d.optJSONObject("pagination");int total=pagination==null?0:pagination.optInt("total_pages");r.more=total>0?q.page<total:r.items.size()>=24;return r;
    }
}
