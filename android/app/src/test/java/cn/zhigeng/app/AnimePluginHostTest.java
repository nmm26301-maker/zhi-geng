package cn.zhigeng.app;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class AnimePluginHostTest {
    @Test public void realDomTraversalAndText()throws Exception{AnimePluginHost h=new AnimePluginHost(new AnimeNet(),false);int d=Integer.parseInt(h.dispatch("docLoad",new JSONArray().put("<ul><li class='ep'><a href='/one'>One</a></li><li class='ep'>Two</li></ul>").toString()));JSONArray ids=new JSONArray(h.dispatch("docSelect",new JSONArray().put(d).put("root").put(new JSONArray()).put("li.ep").toString()));assertEquals(2,ids.length());String text=h.dispatch("docText",new JSONArray().put(d).put("text").put(ids).put("").toString());assertEquals("OneTwo",new JSONTokener(text).nextValue());}
    @Test public void digestBase64AndUnsafeNetwork()throws Exception{AnimePluginHost h=new AnimePluginHost(new AnimeNet(),false);JSONArray bytes=new JSONArray(h.dispatch("utf8Encode","[\"hello\"]"));String b64=h.dispatch("b64Encode",new JSONArray().put(bytes).toString());assertEquals("aGVsbG8=",new JSONTokener(b64).nextValue());assertEquals(32,new JSONArray(h.dispatch("digest",new JSONArray().put("sha256").put(bytes).toString())).length());assertThrows(Exception.class,()->AnimeNet.publicUrl("https://127.0.0.1/x",false));assertThrows(Exception.class,()->AnimeNet.publicUrl("file:///etc/passwd",false));}
}
