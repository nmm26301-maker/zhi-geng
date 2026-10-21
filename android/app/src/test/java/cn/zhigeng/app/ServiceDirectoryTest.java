package cn.zhigeng.app;
import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class ServiceDirectoryTest {
    private String asset(String name)throws Exception{return new String(Files.readAllBytes(Paths.get(System.getProperty("zhigeng.assets"),name)),java.nio.charset.StandardCharsets.UTF_8);}
    @Test public void bundledChosenListsAndExtraConsoleAreComplete()throws Exception {
        JSONObject nodes=ServiceDirectory.validate("nodes",asset("node-sites.json")),members=ServiceDirectory.validate("memberships",asset("ai-memberships.json"));
        assertEquals(5,nodes.getJSONArray("groups").getJSONObject(0).getJSONArray("entries").length());assertEquals(5,nodes.getJSONArray("groups").getJSONObject(1).getJSONArray("entries").length());assertTrue(nodes.toString().contains("前一轮候选"));assertEquals(2,members.getJSONArray("groups").getJSONObject(1).getJSONArray("entries").length());
        JSONObject g=members.getJSONArray("groups").getJSONObject(0).getJSONArray("entries").getJSONObject(0);assertTrue(ServiceDirectory.matches(g,"GamsGo","ChatGPT","shared"));assertFalse(ServiceDirectory.matches(g,"GamsGo","ChatGPT","compare"));
    }
    @Test public void staleDirectoryAndUnsafeLinksCannotReplaceSelection()throws Exception {
        JSONObject nodes=new JSONObject(asset("node-sites.json"));nodes.put("schemaVersion",1);try{ServiceDirectory.validate("nodes",nodes.toString());fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("版本"));}
        assertFalse(ServiceDirectory.https("http://example.com/"));assertFalse(ServiceDirectory.https("https://user:pass@example.com/"));assertFalse(ServiceDirectory.https("javascript:alert(1)"));
        nodes=new JSONObject(asset("node-sites.json"));nodes.getJSONArray("groups").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put("id","old-site");try{ServiceDirectory.validate("nodes",nodes.toString());fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("条目"));}
    }
    @Test public void everyCuratedWorkHasItsOwnCoverData()throws Exception {
        JSONObject data=new JSONObject(asset("domestic.json"));assertEquals("2026-10-20",data.getString("coverUpdatedAt"));JSONArray entries=data.getJSONArray("items");assertEquals(28,entries.length());String film="",book="";for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);assertTrue(e.getString("title"),ServiceDirectory.https(e.getString("image")));if(e.getString("title").equals("活着")){if(e.getString("type").equals("movie"))film=e.getString("image");else if(e.getString("type").equals("book"))book=e.getString("image");}}assertFalse(film.isEmpty());assertFalse(book.isEmpty());assertNotEquals(film,book);
    }
}
