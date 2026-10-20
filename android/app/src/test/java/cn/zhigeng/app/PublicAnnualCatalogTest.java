package cn.zhigeng.app;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PublicAnnualCatalogTest {
    @Test public void spanningDatesDoNotBecomeTitlesOrLoseFollowingTitles()throws Exception {
        String html="<table class='wikitable'><tr><th>月份</th><th>日期</th><th>片名</th></tr><tr><td rowspan='2'>五月</td><td>1日</td><td><a href='/wiki/作品甲' title='作品甲'>作品甲</a></td></tr><tr><td>2日</td><td><a href='/wiki/作品乙' title='作品乙'>作品乙</a></td></tr><tr><td>六月</td><td>3日</td><td>作品丙</td></tr><tr><td>六月</td><td>4日</td><td>待定</td></tr></table>";
        List<JSONObject> rows=PublicAnnualCatalog.parse(html,"movie","2020","https://zh.wikipedia.org/wiki/2020年中国大陆电影作品列表");assertEquals(3,rows.size());assertEquals("作品乙",rows.get(1).getString("title"));assertEquals("作品丙",rows.get(2).getString("title"));for(JSONObject row:rows){assertTrue(row.getBoolean("directory"));assertFalse(row.has("rank"));assertEquals(0,row.getInt("score"));}
    }
    @Test public void unrelatedTablesAreNotTreatedAsYearCatalogs()throws Exception {assertTrue(PublicAnnualCatalog.parse("<table class='wikitable'><tr><th>演员</th></tr><tr><td>张三</td></tr></table>","tv","2020","https://zh.wikipedia.org/").isEmpty());}
}
