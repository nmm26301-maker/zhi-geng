package cn.zhigeng.app;

import org.junit.Test;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class FccClientTest {
    @Test public void endpointsAndCredentialBoundaries(){
        assertEquals("https://example.test/v1",FccPolicy.base("https://example.test/"));
        assertEquals("https://example.test/fcc/v1",FccPolicy.base("https://example.test/fcc/v1/"));
        for(String s:new String[]{"http://example.test","https://token@example.test","https://example.test/?key=secret","https://example.test/#token","https://example.test/../x","https://example.test/%2e%2e/x","file:///x","https://example.test:0/"})try{FccPolicy.base(s);fail(s);}catch(IllegalArgumentException expected){}
        try{FccPolicy.token("secret\nheader");fail();}catch(IllegalArgumentException expected){}
        assertFalse(FccPolicy.status(401).contains("secret"));assertTrue(FccPolicy.status(302).contains("跳转"));
    }
    @Test public void actualCatalogDeduplicatesAndExcludesNonText()throws Exception{
        JSONArray m=FccClient.parseModels("{\"data\":[{\"id\":\"wire-id\",\"display_name\":\"Model\",\"provider_model_ref\":\"nim/test\"},{\"id\":\"wire-id\"},{\"id\":\"image\",\"inputModalities\":[\"image\"]},{\"id\":\"\"}]}");
        assertEquals(1,m.length());assertEquals("wire-id",m.getJSONObject(0).getString("id"));assertEquals("nim/test",m.getJSONObject(0).getString("ref"));
        try{FccClient.parseModels("{\"status\":\"ok\"}");fail();}catch(IOException expected){}
    }
    @Test public void requestKeepsOnlyCompletedUserAssistantContext()throws Exception{
        JSONArray h=new JSONArray();for(int i=0;i<16;i++){h.put(new JSONObject().put("role","user").put("content","q"+i));h.put(new JSONObject().put("role","assistant").put("content","a"+i));}
        JSONObject req=FccClient.request("wire-id",h,"hello");assertTrue(req.getBoolean("stream"));assertEquals(2048,req.getInt("max_tokens"));JSONArray sent=req.getJSONArray("messages");assertEquals(25,sent.length());assertEquals("q4",sent.getJSONObject(0).getString("content"));assertEquals("hello",sent.getJSONObject(24).getString("content"));assertFalse(req.has("token"));
    }
    private String event(String json){return "data: "+json+"\n\n";}
    @Test public void streamingTextIgnoresReasoningAndRequiresCompletion()throws Exception{
        String s=": heartbeat\n\n"+event("{\"type\":\"message_start\",\"message\":{\"model\":\"actual-model\"}}")+event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"private reasoning\"}}")+event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}")+event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"，世界\"}}")+event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}")+event("{\"type\":\"message_stop\"}");
        StringBuilder seen=new StringBuilder();FccClient.Reply r=new FccClient("https://example.test","").stream(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),"selected",seen::append);
        assertEquals("你好，世界",r.text);assertEquals("actual-model",r.model);assertEquals("end_turn",r.reason);assertFalse(seen.toString().contains("private reasoning"));
        try{new FccClient("https://example.test","").stream(new ByteArrayInputStream(event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}").getBytes(StandardCharsets.UTF_8)),"x",t->{});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("连接中断"));}
    }
    @Test public void cancelAndInStreamErrorAreNotSuccessfulReplies()throws Exception{
        FccClient c=new FccClient("https://example.test","");c.cancel();try{c.stream(new ByteArrayInputStream(event("{\"type\":\"message_stop\"}").getBytes(StandardCharsets.UTF_8)),"x",t->{});fail();}catch(java.util.concurrent.CancellationException expected){}
        try{new FccClient("https://example.test","").stream(new ByteArrayInputStream(event("{\"type\":\"error\",\"error\":{\"message\":\"secret-key-in-error\"}}").getBytes(StandardCharsets.UTF_8)),"x",t->{});fail();}catch(IOException expected){assertFalse(expected.getMessage().contains("secret-key"));}
    }
}
