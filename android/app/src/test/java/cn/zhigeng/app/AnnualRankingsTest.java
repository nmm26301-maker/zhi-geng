package cn.zhigeng.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnnualRankingsTest {
    private Catalog.Query query(String type){Catalog.Query q=new Catalog.Query();q.type=type;q.year="2020";q.region="global";q.sort="rating";q.page=2;return q;}
    @Test public void filmsRejectWrongYearAndLowVotesAndKeepPageRanks()throws Exception {
        Catalog.Query q=query("movie");q.region="CN";
        Catalog.Result r=AnnualRankings.load(q,"0123456789abcdef0123456789abcdef",(url,body,headers)->{
            assertTrue(url.contains("primary_release_date.gte=2020-01-01"));assertTrue(url.contains("with_origin_country=CN%7CHK%7CTW"));assertTrue(url.contains("vote_count.gte=10"));assertTrue(url.contains("page=2"));
            return new JSONObject("{\"total_pages\":4,\"results\":[{\"id\":1,\"title\":\"Right\",\"release_date\":\"2020-02-03\",\"vote_count\":60,\"vote_average\":8.2},{\"id\":2,\"release_date\":\"2019-02-03\",\"vote_count\":80},{\"id\":3,\"release_date\":\"2020-02-03\",\"vote_count\":2}]}");
        });assertEquals(1,r.items.size());assertEquals(21,r.items.get(0).getInt("rank"));assertEquals("TMDB /10",r.items.get(0).getString("scoreLabel"));assertTrue(r.more);
    }
    @Test public void tokenStaysInHeaderAndVarietyUsesTelevision()throws Exception {
        Catalog.Query q=query("variety");q.sort="popular";
        AnnualRankings.load(q,"eyJabc.def.ghi",(url,body,headers)->{assertTrue(url.contains("/discover/tv?"));assertTrue(url.contains("first_air_date.gte=2020"));assertTrue(url.contains("10764%7C10767"));assertTrue(url.contains("popularity.desc"));assertFalse(url.contains("vote_count.gte"));assertFalse(url.contains("eyJ"));assertEquals("Bearer eyJabc.def.ghi",headers.get("Authorization"));return new JSONObject("{\"results\":[]}");});
    }
    @Test public void booksUseOwnScaleAndExcludeSparseRatings()throws Exception {
        Catalog.Query q=query("book");q.region="CN";
        Catalog.Result r=AnnualRankings.load(q,"",(url,body,headers)->{assertTrue(url.contains("language%3Achi"));assertTrue(url.contains("ratings_count%3A"));return new JSONObject("{\"numFound\":80,\"docs\":[{\"key\":\"/works/OL1W\",\"title\":\"Book\",\"first_publish_year\":2020,\"ratings_count\":12,\"ratings_average\":4.5},{\"key\":\"/works/OL2W\",\"first_publish_year\":2020,\"ratings_count\":1},{\"first_publish_year\":2021,\"ratings_count\":30}]}");});
        assertEquals(1,r.items.size());assertEquals("Open Library /5",r.items.get(0).getString("scoreLabel"));assertTrue(r.note.contains("不代表中国原创"));assertTrue(r.more);
    }
    @Test public void animeKeepsOriginSortYearAndGraphqlErrors()throws Exception {
        Catalog.Query q=query("anime");q.region="CN";q.sort="popular";
        Catalog.Result r=AnnualRankings.load(q,"never-send",(url,body,headers)->{assertEquals("https://graphql.anilist.co",url);assertEquals("CN",body.getJSONObject("variables").getString("origin"));assertEquals("POPULARITY_DESC",body.getJSONObject("variables").getString("sort"));assertFalse(body.toString().contains("never-send"));return new JSONObject("{\"data\":{\"Page\":{\"pageInfo\":{\"hasNextPage\":true},\"media\":[{\"id\":1,\"title\":{\"native\":null,\"romaji\":\"Anime\"},\"seasonYear\":2020,\"averageScore\":85},{\"title\":{},\"seasonYear\":2021}]}}}");});assertEquals(1,r.items.size());assertEquals(8.5,r.items.get(0).getDouble("score"),.001);assertEquals("Anime",r.items.get(0).getString("title"));
        try{AnnualRankings.load(q,"",(u,b,h)->new JSONObject("{\"errors\":[{}]}"));fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("AniList"));}
    }
    @Test public void invalidFiltersFailBeforeNetworkAndLastPageStops()throws Exception {
        Catalog.Query q=query("book");q.year="1899";try{AnnualRankings.load(q,"",(u,b,h)->{fail("Network should not be used");return null;});fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("无效"));}
        q.year="2020";q.page=25;Catalog.Result r=AnnualRankings.load(q,"",(u,b,h)->new JSONObject("{\"numFound\":1000,\"docs\":[]}"));assertFalse(r.more);
    }
    @Test public void absentCredentialDoesNotInventMovieResults()throws Exception {
        try{AnnualRankings.load(query("movie"),"",(u,b,h)->{fail();return null;});fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("TMDB"));}
    }
}
