package cn.zhigeng.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingToolsTest {
    @Test public void selfHostedHttpsKeepsPortAndPrefix() throws Exception {
        assertEquals("https://library.example:9443/books",BookOrbitProtocol.base(" https://library.example:9443/books/api/v1/ "));
        assertEquals("https://192.168.1.4:9443/api/v1/books/files/7/download",BookOrbitProtocol.fileUrl("https://192.168.1.4:9443",7));
        for(String value:new String[]{"http://library.example","https://u:p@library.example","https://library.example/?token=x","https://library.example/#login"}) {
            try{BookOrbitProtocol.base(value);fail(value);}catch(Exception expected){}
        }
    }
    @Test public void downloadCredentialsCannotFollowOtherOrigins() throws Exception {
        String base="https://library.example:9443/prefix";
        assertEquals(base+"/file",BookOrbitProtocol.downloadUrl(base,base+"/file"));
        for(String target:new String[]{"https://other.example/file","https://library.example/file","http://library.example:9443/file","https://u@library.example:9443/file"}) {
            try{BookOrbitProtocol.downloadUrl(base,target);fail(target);}catch(Exception expected){}
        }
        // The new self-hosted client must not weaken the existing Z-Library policy.
        try{DownloadPolicy.validate("https://192.168.1.4:9443/file");fail();}catch(Exception expected){}
    }
    @Test public void bookQueryAndSavedItemContainNoSession() throws Exception {
        JSONObject query=BookOrbitProtocol.query("中文 + author",2);
        assertEquals(2,query.getJSONObject("pagination").getInt("page"));assertEquals(20,query.getJSONObject("pagination").getInt("size"));
        JSONObject book=new JSONObject("{\"id\":1,\"title\":\"春日来信\",\"authors\":[{\"name\":\"知更\"}]}");
        JSONObject file=new JSONObject("{\"id\":7,\"format\":\"EPUB\"}");
        JSONObject saved=BookOrbitProtocol.shelfItem("https://library.example",book,file,"file:///books/local.epub");
        assertEquals("知更",saved.getString("author"));assertEquals("epub",saved.getString("format"));
        assertEquals("file:///books/local.epub",saved.getString("localUri"));assertFalse(saved.has("accessToken"));assertFalse(saved.has("refreshToken"));
        assertTrue(BookOrbitProtocol.supported("PDF"));assertFalse(BookOrbitProtocol.supported("cbz"));
    }
    @Test public void resourceSearchEncodesKeywordsWithoutChangingOrigin() throws Exception {
        String url=ResourceSearchLinks.search(" 漫画 & name/#中文 ");
        assertTrue(url.startsWith(ResourceSearchLinks.HOME+"?q="));assertTrue(url.contains("%26"));assertTrue(url.contains("%23"));
        assertEquals("漫画 搜索",ResourceSearchLinks.clean("漫画\n搜索"));
        for(String query:new String[]{" \n ","字".repeat(101)}){try{ResourceSearchLinks.search(query);fail();}catch(Exception expected){}}
        for(String[] category:ResourceSearchLinks.CATEGORIES)assertFalse(BrowserPolicy.webUrl("https://jys66.top/"+category[1]).isEmpty());
    }
}
