package cn.zhigeng.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnimeCatalogTest {
    @Test public void searchUsesVariablesAndHasNoCredentialOrQueryInjection()throws Exception {
        String term="中文 \" }) { Viewer { id } }";JSONObject body=AnimeCatalogClient.body(term,2);
        assertEquals(term,body.getJSONObject("variables").getString("search"));
        assertFalse(body.getString("query").contains("Viewer"));assertEquals(2,body.getJSONObject("variables").getInt("page"));
        assertEquals("SEARCH_MATCH",body.getJSONObject("variables").getJSONArray("sort").getString(0));
        assertTrue(AnimeCatalogClient.body("",1).getJSONObject("variables").isNull("search"));
        try{AnimeCatalogClient.body("字".repeat(101),1);fail();}catch(Exception expected){}
    }
    @Test public void nullTitlesFallBackAndPreserveUnicodeBoundaries()throws Exception {
        JSONObject media=new JSONObject("{\"title\":{\"userPreferred\":null,\"native\":\"测试动漫\"}}");
        assertEquals("测试动漫",AnimeCatalogClient.title(media));
        media.getJSONObject("title").put("native","\ud83c\udf38".repeat(201));
        assertEquals(200,AnimeCatalogClient.title(media).codePointCount(0,AnimeCatalogClient.title(media).length()));
    }
    @Test public void graphQlErrorsDoNotMasqueradeAsAnEmptySuccessfulPage()throws Exception {
        try{AnimeCatalogClient.page(new JSONObject("{\"data\":null,\"errors\":[{\"message\":\"offline\"}]}"));fail();}catch(Exception expected){}
        JSONObject page=AnimeCatalogClient.page(new JSONObject("{\"data\":{\"Page\":{\"media\":[],\"pageInfo\":{\"hasNextPage\":true,\"total\":0}}}}"));
        assertTrue(page.getJSONObject("pageInfo").getBoolean("hasNextPage"));
    }
    @Test public void progressUpdatesAreIndependentAndRejectInvalidCounts()throws Exception {
        JSONObject original=new JSONObject(),media=new JSONObject("{\"id\":1,\"title\":{\"native\":\"测试动漫\"},\"episodes\":12}");
        JSONObject updated=AnimeWatchlist.update(original,media,"在看",3);
        assertEquals(0,original.length());assertEquals(3,updated.getJSONObject("1").getInt("progress"));
        media.getJSONObject("title").put("native","Changed");
        assertEquals("测试动漫",AnimeCatalogClient.title(updated.getJSONObject("1").getJSONObject("media")));
        for(int count:new int[]{-1,13,100001})try{AnimeWatchlist.update(updated,media,"在看",count);fail();}catch(Exception expected){}
        try{AnimeWatchlist.update(updated,media,"invalid",0);fail();}catch(Exception expected){}
        JSONObject removed=AnimeWatchlist.remove(updated,1);assertEquals(0,removed.length());assertEquals(1,updated.length());
    }
    @Test public void unknownEpisodeTotalsStillAllowLocalProgress()throws Exception {
        JSONObject media=new JSONObject("{\"id\":2,\"title\":{\"romaji\":\"Ongoing\"},\"episodes\":null}");
        JSONObject saved=AnimeWatchlist.update(new JSONObject(),media,"在看",55);
        assertEquals(55,saved.getJSONObject("2").getInt("progress"));
    }
}
