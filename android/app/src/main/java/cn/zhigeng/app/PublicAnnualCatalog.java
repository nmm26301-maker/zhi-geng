package cn.zhigeng.app;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Explicitly unranked public year indexes, useful when the user has no TMDB credential. */
final class PublicAnnualCatalog {
    private static final Map<String,List<JSONObject>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    static Catalog.Result load(Catalog.Query q,AnnualRankings.Fetch fetch)throws Exception {
        int year=Integer.parseInt(q.year);if(year<1900||year>LocalDate.now().getYear()||q.page<1||q.page>25||!q.region.equals("CN")||!Arrays.asList("movie","tv").contains(q.type))throw new Exception("公开年度目录仅支持国内电影和电视剧");
        String title=q.type.equals("movie")?year+"年中国大陆电影作品列表":"中国大陆电视剧列表 ("+year+"年)",article="https://zh.wikipedia.org/wiki/"+URLEncoder.encode(title.replace(' ','_'),"UTF-8");String id=q.type+":"+year;
        List<JSONObject> entries=cache.get(id);if(entries==null){String url="https://zh.wikipedia.org/w/api.php?action=parse&format=json&formatversion=2&prop=text&page="+URLEncoder.encode(title,"UTF-8");JSONObject response=fetch.request(url,null,Collections.emptyMap());JSONObject parsed=response.optJSONObject("parse");if(parsed==null)throw new Exception("该年公开作品目录不可用；可配置 TMDB 查看真实评分榜");Object raw=parsed.opt("text");String html=raw instanceof JSONObject?((JSONObject)raw).optString("*"):parsed.optString("text");entries=parse(html,q.type,q.year,article);if(entries.isEmpty())throw new Exception("该年页面暂无可解析作品，或网络限制；可配置 TMDB");if(cache.size()>12)cache.clear();cache.put(id,entries);}
        Catalog.Result r=new Catalog.Result();int start=(q.page-1)*20,end=Math.min(start+20,entries.size());if(start<end)r.items=new ArrayList<>(entries.subList(start,end));enrich(r.items,fetch);r.more=end<entries.size()&&q.page<25;r.note="维基百科 · "+year+" 年内地"+(q.type.equals("movie")?"电影":"电视剧")+"目录 · "+entries.size()+" 条 · 无评分／无排名；并非全国完整清单。评分榜需 TMDB。";return r;
    }
    static List<JSONObject> parse(String html,String type,String year,String article)throws Exception {
        Document document=Jsoup.parse(html,article);List<JSONObject> result=new ArrayList<>();Set<String> seen=new HashSet<>();Pattern header=Pattern.compile(type.equals("movie")?"^(片名|影片名稱|电影名称|電影名稱)$":"^(劇名|剧名)$");
        for(Element table:document.select("table.wikitable")){
            int titleColumn=-1;Map<Integer,Integer> spans=new HashMap<>();
            for(Element row:table.select("tr")){
                if(row.closest("table")!=table)continue;Map<Integer,Element> cells=new HashMap<>();Map<Integer,Integer> next=new HashMap<>();for(Map.Entry<Integer,Integer> span:spans.entrySet())if(span.getValue()>1)next.put(span.getKey(),span.getValue()-1);
                int col=0;for(Element cell:row.children()){if(!Arrays.asList("td","th").contains(cell.tagName()))continue;while(spans.containsKey(col)||cells.containsKey(col))col++;int width=number(cell.attr("colspan")),height=number(cell.attr("rowspan"));for(int w=0;w<width;w++){cells.put(col+w,cell);if(height>1)next.put(col+w,height-1);}col+=width;}spans=next;
                boolean heading=false;for(Map.Entry<Integer,Element> cell:cells.entrySet())if(header.matcher(cell.getValue().text().trim()).matches()){titleColumn=cell.getKey();heading=true;break;}if(heading||titleColumn<0)continue;
                Element cell=cells.get(titleColumn);if(cell==null||!cell.tagName().equals("td"))continue;String name=cell.text().replaceAll("\\[\\d+\\]","").replaceAll("[＊*†‡]$","").trim();if(name.isEmpty()||name.length()>70||name.matches("(?i)待定|待播|未知|TBA")||!seen.add(name))continue;Element link=cell.selectFirst("a[href^=/wiki/]");String url=link==null?article:link.absUrl("href");
                result.add(new JSONObject().put("id","wikipedia:"+type+":"+year+":"+name).put("type",type).put("title",name).put("year",year).put("author","维基百科年度目录").put("source","维基百科公开作品目录").put("url",url).put("image","").put("score",0).put("wikiTitle",link==null?name:link.attr("title")).put("directory",true).put("summary","收录于 "+year+" 年度作品资料列表；不是评分或热度排名。"));
            }
        }
        return result;
    }
    private static void enrich(List<JSONObject> entries,AnnualRankings.Fetch fetch){
        try{List<String> titles=new ArrayList<>();for(JSONObject e:entries)titles.add(e.optString("wikiTitle",e.optString("title")));if(titles.isEmpty())return;JSONObject response=fetch.request("https://zh.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=pageimages&piprop=thumbnail&pithumbsize=360&titles="+URLEncoder.encode(String.join("|",titles),"UTF-8"),null,Collections.emptyMap());JSONObject query=response.optJSONObject("query");JSONArray pages=query==null?null:query.optJSONArray("pages");if(pages==null)return;for(int i=0;i<pages.length();i++){JSONObject page=pages.optJSONObject(i),thumb=page.optJSONObject("thumbnail");if(thumb==null)continue;String url=thumb.optString("source");if(!url.startsWith("https://upload.wikimedia.org/"))continue;for(JSONObject entry:entries)if(entry.optString("wikiTitle").equals(page.optString("title")))entry.put("image",url).put("imageSource","维基百科条目配图");}}catch(Exception ignored){}
    }
    private static int number(String value){try{return Math.max(1,Math.min(50,Integer.parseInt(value)));}catch(Exception e){return 1;}}
}
