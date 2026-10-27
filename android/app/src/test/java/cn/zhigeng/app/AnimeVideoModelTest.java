package cn.zhigeng.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnimeVideoModelTest {
    private JSONObject anime()throws Exception{return new JSONObject("{\"id\":7,\"episodes\":12,\"title\":{\"native\":\"测试动漫\"}}");}
    @Test public void sourceAcceptsOnlyHttpsOrPersistedDocumentAddresses()throws Exception {
        assertEquals("https://example.com/a.mp4?token=a%2Bb#part",AnimeVideoModel.source(" HTTPS://example.com/a.mp4?token=a%2Bb#part "));
        assertEquals("content://docs/document/1",AnimeVideoModel.source("content://docs/document/1"));
        for(String raw:new String[]{"http://example.com/a","file:///data/data/secret","javascript:alert(1)","https://u:p@example.com/a","https://example.com:99999/a","https://example.com/a\nfile"})try{AnimeVideoModel.source(raw);fail(raw);}catch(Exception expected){}
    }
    @Test public void updatesAreIndependentAndChangingSourceResetsOnlyItsCheckpoint()throws Exception {
        JSONObject original=new JSONObject(),first=AnimeVideoModel.put(original,anime(),1,"A","https://example.com/a.mp4","auto");
        assertEquals(0,original.length());String id=first.getJSONObject("1").getString("id");
        JSONObject played=AnimeVideoModel.progress(first,1,id,5000,10000);assertEquals(0,first.getJSONObject("1").getLong("position"));
        JSONObject same=AnimeVideoModel.put(played,anime(),1,"Renamed","https://example.com/a.mp4","file");assertEquals(5000,same.getJSONObject("1").getLong("position"));assertNotEquals(id,same.getJSONObject("1").getString("id"));
        JSONObject changed=AnimeVideoModel.put(played,anime(),1,"A","https://example.com/b.mp4","auto");assertEquals(0,changed.getJSONObject("1").getLong("position"));assertNotEquals(id,changed.getJSONObject("1").getString("id"));
        assertEquals(0,AnimeVideoModel.progress(changed,1,id,9000,10000).getJSONObject("1").getLong("position"));
    }
    @Test public void episodeAndProgressBoundsAreEnforced()throws Exception {
        for(int n:new int[]{-1,0,13,100001})try{AnimeVideoModel.put(new JSONObject(),anime(),n,"","https://example.com/a.mp4","auto");fail();}catch(Exception expected){}
        JSONObject p=AnimeVideoModel.put(new JSONObject(),anime(),1,"","https://example.com/a.mp4","auto");String id=p.getJSONObject("1").getString("id");
        assertEquals(10000,AnimeVideoModel.progress(p,1,id,20000,10000).getJSONObject("1").getLong("position"));
        assertEquals(0,AnimeVideoModel.progress(p,1,id,-1,10000).getJSONObject("1").getLong("position"));
        assertEquals(5000,AnimeVideoModel.progress(p,1,id,5000,-1).getJSONObject("1").getLong("position"));
    }
    @Test public void playlistImportIsAtomicAndRejectsDuplicateNumbersAndLocalReferences()throws Exception {
        JSONObject initial=AnimeVideoModel.put(new JSONObject(),anime(),3,"Original","https://example.com/3.mp4","auto");String before=initial.toString();
        for(String json:new String[]{"{\"episodes\":[]}","{\"episodes\":[{\"episode\":1,\"url\":\"https://example.com/1.mp4\"},{\"episode\":1,\"url\":\"https://example.com/2.mp4\"}]}","{\"episodes\":[{\"episode\":1.5,\"url\":\"https://example.com/1.mp4\"}]}","{\"episodes\":[{\"episode\":1,\"url\":\"https://example.com/1.mp4\"},{\"episode\":2,\"url\":\"content://private/document/1\"}] }"})try{AnimeVideoModel.playlist(initial,anime(),json);fail(json);}catch(Exception expected){}
        assertEquals(before,initial.toString());
        JSONObject imported=AnimeVideoModel.playlist(initial,anime(),"{\"episodes\":[{\"episode\":2,\"url\":\"https://example.com/2.m3u8\",\"format\":\"hls\"},{\"episode\":1,\"url\":\"https://example.com/1.mp4\"}]}");
        assertEquals(3,imported.length());assertEquals(1,AnimeVideoModel.list(imported).get(0).getInt("episode"));
        assertTrue(AnimeVideoModel.adaptive(imported.getJSONObject("2")));assertFalse(AnimeVideoModel.adaptive(imported.getJSONObject("1")));
    }
    @Test public void filenamesSortByEpisodeNumbersWithoutNumericOverflow()throws Exception {
        assertTrue(AnimeVideoModel.natural("episode2.mp4","episode10.mp4")<0);
        assertTrue(AnimeVideoModel.natural("ep0003.mp4","ep20.mp4")<0);
        assertTrue(AnimeVideoModel.natural("ep99999999999999999999999.mp4","ep100000000000000000000000.mp4")<0);
    }
    @Test public void finishingPreservesExistingMetadataAndNeverLowersWatchProgress()throws Exception {
        JSONObject media=anime();media.put("description","Saved offline synopsis");media.getJSONObject("title").put("english","Alternate title");
        JSONObject watch=AnimeWatchlist.update(new JSONObject(),media,"在看",5);
        JSONObject episode=AnimeVideoModel.put(new JSONObject(),anime(),1,"1","https://example.com/1.mp4","auto").getJSONObject("1");
        JSONObject finished=AnimeVideoModel.finished(watch,episode);assertEquals(5,finished.getJSONObject("7").getInt("progress"));
        assertEquals("Saved offline synopsis",finished.getJSONObject("7").getJSONObject("media").getString("description"));
        assertEquals("Alternate title",finished.getJSONObject("7").getJSONObject("media").getJSONObject("title").getString("english"));
        episode.put("episode",12);assertEquals("已看完",AnimeVideoModel.finished(watch,episode).getJSONObject("7").getString("state"));assertEquals(5,watch.getJSONObject("7").getInt("progress"));
    }
    @Test public void genericLocalVideosNeverCreateAnAnimeWatchlistEntry()throws Exception {
        JSONObject generic=new JSONObject("{\"id\":0,\"title\":{\"userPreferred\":\"本地视频\"}}");
        JSONObject episode=AnimeVideoModel.put(new JSONObject(),generic,1,"My file","content://docs/document/1","file").getJSONObject("1");
        assertEquals(0,AnimeVideoModel.finished(new JSONObject(),episode).length());
    }
}
