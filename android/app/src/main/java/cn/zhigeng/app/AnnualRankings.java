package cn.zhigeng.app;

import org.json.*;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;

/** Annual lists keep each provider's year, region, score scale and pagination separate. */
final class AnnualRankings {
    interface Fetch { JSONObject request(String url,JSONObject body,Map<String,String> headers)throws Exception; }
    static Catalog.Result load(Catalog.Query q,String credential,Fetch fetch)throws Exception {
        int year;try{year=Integer.parseInt(q.year);}catch(Exception e){throw new Exception("请选择年度");}
        if(year<1900||year>LocalDate.now().getYear()||q.page<1||q.page>25)throw new Exception("年份或页数无效");
        if(!Arrays.asList("movie","tv","variety","anime","book").contains(q.type)||!Arrays.asList("CN","global").contains(q.region)||!Arrays.asList("rating","popular").contains(q.sort))throw new Exception("榜单筛选无效");
        if(q.type.equals("anime"))return anime(q,year,fetch);
        if(q.type.equals("book"))return books(q,year,fetch);
        return films(q,year,credential,fetch);
    }
    private static String enc(String text)throws Exception{return URLEncoder.encode(text,"UTF-8");}
    private static Catalog.Result result(Catalog.Query q,List<JSONObject> rows,String note,boolean more)throws Exception {
        Catalog.Result r=new Catalog.Result();r.items=rows;r.note=note;r.more=more&&q.page<25;
        for(int i=0;i<rows.size();i++)rows.get(i).put("rank",(q.page-1)*20+i+1).put("annual",true);
        return r;
    }
    private static JSONObject item(String id,String type,String title,String year,String source,String url,String image,String summary)throws Exception {
        return new JSONObject().put("id",id).put("type",type).put("title",title).put("year",year).put("author",source).put("source",source).put("url",url).put("image",image).put("summary",summary).put("score",0);
    }
    private static Catalog.Result films(Catalog.Query q,int year,String credential,Fetch fetch)throws Exception {
        String token=credential.trim();boolean key=token.matches("[a-fA-F0-9]{32}");
        if(!key&&!token.matches("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))throw new Exception("影视年度榜需要 TMDB 凭证，请到「我的」验证保存；也可切换编辑推荐。");
        String route=q.type.equals("movie")?"movie":"tv",date=route.equals("movie")?"primary_release_date":"first_air_date";
        String upper=year==LocalDate.now().getYear()?LocalDate.now().toString():year+"-12-31";
        int minVotes=q.sort.equals("popular")?0:q.region.equals("CN")?10:50;
        String url="https://api.themoviedb.org/3/discover/"+route+"?language=zh-CN&include_adult=false&page="+q.page+"&sort_by="+(q.sort.equals("rating")?"vote_average.desc":"popularity.desc")+"&"+date+".gte="+year+"-01-01&"+date+".lte="+upper;
        if(minVotes>0)url+="&vote_count.gte="+minVotes;
        if(q.region.equals("CN"))url+="&with_origin_country=CN%7CHK%7CTW";
        if(q.type.equals("variety"))url+="&with_genres=10764%7C10767";
        Map<String,String> headers=new HashMap<>();if(key)url+="&api_key="+token;else headers.put("Authorization","Bearer "+token);
        JSONObject data=fetch.request(url,null,headers);JSONArray values=data.optJSONArray("results");List<JSONObject> rows=new ArrayList<>();
        if(values!=null)for(int i=0;i<values.length();i++){
            JSONObject v=values.getJSONObject(i);String released=v.optString(route.equals("movie")?"release_date":"first_air_date");
            if(!released.startsWith(year+"-")||released.compareTo(upper)>0||v.optInt("vote_count")<minVotes)continue;
            String path=v.optString("poster_path","");
            rows.add(item("tmdb:"+route+":"+v.optInt("id"),q.type,v.optString(route.equals("movie")?"title":"name"),q.year,"TMDB","https://www.themoviedb.org/"+route+"/"+v.optInt("id"),path.isEmpty()||path.equals("null")?"":"https://image.tmdb.org/t/p/w342"+path,v.optString("overview"))
                .put("score",v.optDouble("vote_average",0)).put("votes",v.optInt("vote_count")).put("interest",v.optDouble("popularity",0)).put("scoreLabel","TMDB /10"));
        }
        return result(q,rows,"TMDB · "+year+" 年 · "+(q.region.equals("CN")?"内地／香港／台湾来源":"全球")+" · "+(q.sort.equals("rating")?"评分":"热度")+"排序 · "+(minVotes==0?"不限评分人数":minVotes+"票起，小样本需谨慎")+" · 匹配 "+data.optInt("total_results")+" 条，非完整作品全集",q.page<data.optInt("total_pages",1));
    }
    private static Catalog.Result anime(Catalog.Query q,int year,Fetch fetch)throws Exception {
        String gql="query YearAnime($page:Int!,$year:Int!,$origin:CountryCode,$sort:MediaSort!){Page(page:$page,perPage:20){pageInfo{hasNextPage}media(type:ANIME,seasonYear:$year,countryOfOrigin:$origin,sort:[$sort],isAdult:false){id title{native romaji english}siteUrl coverImage{large}description(asHtml:false)averageScore popularity seasonYear}}}";
        JSONObject vars=new JSONObject().put("page",q.page).put("year",year).put("origin",q.region.equals("CN")?"CN":JSONObject.NULL).put("sort",q.sort.equals("rating")?"SCORE_DESC":"POPULARITY_DESC");
        JSONObject data=fetch.request("https://graphql.anilist.co",new JSONObject().put("query",gql).put("variables",vars),Collections.emptyMap());
        if(data.has("errors"))throw new Exception("AniList 年度榜暂不可用，请稍后重试");
        JSONObject page=data.getJSONObject("data").getJSONObject("Page");JSONArray values=page.getJSONArray("media");List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<values.length();i++){
            JSONObject v=values.getJSONObject(i);if(v.optInt("seasonYear")!=year)continue;JSONObject names=v.getJSONObject("title");String title=names.optString("native","");if(title.isEmpty()||title.equals("null"))title=names.optString("romaji","未命名动漫");
            JSONObject cover=v.optJSONObject("coverImage");rows.add(item("anilist:"+v.optInt("id"),"anime",title,q.year,"AniList",v.optString("siteUrl"),cover==null?"":cover.optString("large"),v.optString("description").replaceAll("<[^>]*>","")).put("score",v.optDouble("averageScore",0)/10).put("interest",v.optInt("popularity")).put("scoreLabel","AniList /10"));
        }
        return result(q,rows,"AniList · "+year+" 年 · "+(q.region.equals("CN")?"中国来源动漫":"全球动漫")+" · "+(q.sort.equals("rating")?"均分":"关注人数")+"排序，关注人数不等于评分人数",page.optJSONObject("pageInfo")!=null&&page.getJSONObject("pageInfo").optBoolean("hasNextPage"));
    }
    private static Catalog.Result books(Catalog.Query q,int year,Fetch fetch)throws Exception {
        String search="first_publish_year:"+year+(q.region.equals("CN")?" AND language:chi":"")+(q.sort.equals("rating")?" AND ratings_count:[10 TO *]":"");
        String url="https://openlibrary.org/search.json?q="+enc(search)+"&fields=key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count,readinglog_count&sort="+(q.sort.equals("rating")?"rating":"readinglog")+"&limit=20&page="+q.page;
        JSONObject data=fetch.request(url,null,Collections.emptyMap());JSONArray values=data.getJSONArray("docs");List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<values.length();i++){
            JSONObject v=values.getJSONObject(i);if(v.optInt("first_publish_year")!=year||q.sort.equals("rating")&&v.optInt("ratings_count")<10)continue;
            String work=v.optString("key");JSONArray authors=v.optJSONArray("author_name");
            rows.add(item("book:ol:"+work,"book",v.optString("title"),q.year,"Open Library","https://openlibrary.org"+work,v.has("cover_i")?"https://covers.openlibrary.org/b/id/"+v.optString("cover_i")+"-M.jpg":"","首次出版年份为 "+year+"；包含不同语言版本。").put("author",authors==null?"":authors.optString(0)).put("score",v.optDouble("ratings_average",0)).put("votes",v.optInt("ratings_count")).put("interest",v.optInt("readinglog_count")).put("scoreLabel","Open Library /5"));
        }
        return result(q,rows,"Open Library · "+year+" 年首次出版 · "+(q.region.equals("CN")?"存在中文版本，不代表中国原创":"全部语言")+" · "+(q.sort.equals("rating")?"至少10人评分":"书架关注度，不代表销量"),q.page*20<data.optInt("numFound",data.optInt("num_found")));
    }
}
