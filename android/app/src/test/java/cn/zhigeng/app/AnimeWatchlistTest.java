package cn.zhigeng.app;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AnimeWatchlistTest {
    private JSONObject media(long id,String title,int total)throws Exception {
        return new JSONObject().put("id",id).put("title",new JSONObject().put("userPreferred",title)
            .put("native","中文番名").put("english","Alternate title")).put("episodes",total).put("description","Offline synopsis");
    }
    @Test public void advanceStartsWatchingThenCompletesWithoutMutatingOldEntries()throws Exception {
        JSONObject initial=AnimeWatchlist.update(new JSONObject(),media(1,"A",2),"想看",0);
        JSONObject first=AnimeWatchlist.advance(initial,1),second=AnimeWatchlist.advance(first,1);
        assertEquals(0,initial.getJSONObject("1").getInt("progress"));
        assertEquals("在看",first.getJSONObject("1").getString("state"));
        assertEquals(1,first.getJSONObject("1").getInt("progress"));
        assertEquals("已看完",second.getJSONObject("1").getString("state"));
        assertEquals(2,second.getJSONObject("1").getInt("progress"));
        assertFalse(AnimeWatchlist.canAdvance(second.getJSONObject("1")));
        try{AnimeWatchlist.advance(second,1);fail("Must not exceed the known total");}catch(Exception expected){}
        assertEquals("Offline synopsis",second.getJSONObject("1").getJSONObject("media").getString("description"));
    }
    @Test public void advanceRejectsPausedDroppedMissingAndUnknownTotalLimit()throws Exception {
        for(String state:new String[]{"搁置","弃番","已看完"}) {
            JSONObject saved=AnimeWatchlist.update(new JSONObject(),media(1,"A",0),state,5);
            assertFalse(AnimeWatchlist.canAdvance(saved.getJSONObject("1")));
            try{AnimeWatchlist.advance(saved,1);fail(state);}catch(Exception expected){}
        }
        JSONObject unknown=AnimeWatchlist.update(new JSONObject(),media(1,"A",0),"在看",99999);
        JSONObject end=AnimeWatchlist.advance(unknown,1);assertEquals(100000,end.getJSONObject("1").getInt("progress"));
        assertFalse(AnimeWatchlist.canAdvance(end.getJSONObject("1")));
        try{AnimeWatchlist.advance(end,1);fail();}catch(Exception expected){}
        try{AnimeWatchlist.advance(new JSONObject(),42);fail();}catch(Exception expected){}
    }
    @Test public void filtersCombineStatusAndAllTitleAliasesWithoutChangingSavedData()throws Exception {
        JSONObject saved=AnimeWatchlist.update(new JSONObject(),media(1,"Z",12),"在看",3);
        saved=AnimeWatchlist.update(saved,media(2,"A",12),"想看",0);String before=saved.toString();
        assertEquals(2,AnimeWatchlist.select(saved,"", "",false).size());
        assertEquals(1,AnimeWatchlist.select(saved," 中文番名 ","在看",false).size());
        assertEquals(1,AnimeWatchlist.select(saved,"ALTERNATE", "想看",false).size());
        assertTrue(AnimeWatchlist.select(saved,"missing", "",false).isEmpty());
        assertTrue(AnimeWatchlist.select(saved,"", "弃番",false).isEmpty());
        assertEquals(before,saved.toString());
    }
    @Test public void recentSortHandlesLegacyEntriesAndUsesStableTitleAndIdTies()throws Exception {
        JSONObject saved=AnimeWatchlist.update(new JSONObject(),media(1,"Z",12),"在看",3);
        saved=AnimeWatchlist.update(saved,media(2,"A",12),"在看",4);
        saved=AnimeWatchlist.update(saved,media(3,"A",12),"在看",1);
        saved.getJSONObject("1").put("updatedAt",20);saved.getJSONObject("2").remove("updatedAt");saved.getJSONObject("3").remove("updatedAt");
        ArrayList<JSONObject> recent=AnimeWatchlist.select(saved,"","",true),alpha=AnimeWatchlist.select(saved,"","",false);
        assertEquals(1,recent.get(0).getLong("id"));assertEquals(2,recent.get(1).getLong("id"));assertEquals(3,recent.get(2).getLong("id"));
        assertEquals(2,alpha.get(0).getLong("id"));assertEquals(1,alpha.get(2).getLong("id"));
        saved.put("broken",JSONObject.NULL);assertEquals(3,AnimeWatchlist.select(saved,"","",true).size());
    }
    @Test public void offlineMetadataIsBoundedAndSurvivesRoundTripAndSourceMutation()throws Exception {
        JSONObject source=media(1,"A",12);source.put("description","🌸".repeat(4001));
        source.getJSONObject("title").put("english","🌸".repeat(201));
        JSONObject saved=new JSONObject(AnimeWatchlist.update(new JSONObject(),source,"在看",3).toString());
        source.put("description","Changed");source.getJSONObject("title").put("native","Changed");
        JSONObject copy=saved.getJSONObject("1").getJSONObject("media");String description=copy.getString("description");
        assertEquals(4000,description.codePointCount(0,description.length()));
        String english=copy.getJSONObject("title").getString("english");assertEquals(200,english.codePointCount(0,english.length()));
        assertEquals("中文番名",copy.getJSONObject("title").getString("native"));
        assertTrue(saved.getJSONObject("1").getLong("updatedAt")>0);
    }
    @Test public void completedStateFillsKnownTotalButLeavesUnknownTotalsAlone()throws Exception {
        JSONObject known=AnimeWatchlist.update(new JSONObject(),media(1,"A",12),"已看完",3);
        JSONObject unknown=AnimeWatchlist.update(new JSONObject(),media(1,"A",0),"已看完",3);
        assertEquals(12,known.getJSONObject("1").getInt("progress"));assertEquals(3,unknown.getJSONObject("1").getInt("progress"));
    }
}
