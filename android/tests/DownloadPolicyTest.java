package cn.zhigeng.app;
import java.nio.charset.StandardCharsets;
public class DownloadPolicyTest {
    interface Throws {void run()throws Exception;}
    static void reject(Throws f)throws Exception{try{f.run();}catch(Exception e){return;}throw new AssertionError("Expected rejection");}
    public static void main(String[] args)throws Exception{
        for(String url:new String[]{"http://cdn.example/file","https://u:p@cdn.example/a","https://127.0.0.1/a","https://localhost/a","https://host.local/a","https://cdn.example:8443/a","file:///tmp/a"})reject(()->DownloadPolicy.validate(url));
        if(!DownloadPolicy.sameOrigin("https://source.example/file","https://source.example/other"))throw new AssertionError();
        if(DownloadPolicy.sameOrigin("https://source.example/file","https://cdn.example/other"))throw new AssertionError("Never forward credentials across origins");
        DownloadPolicy.verifyContent(new byte[]{'P','K',3,4},"epub",100,100);
        DownloadPolicy.verifyContent("%PDF-1.7".getBytes(StandardCharsets.UTF_8),"pdf",100,100);
        reject(()->DownloadPolicy.verifyContent("<html>captcha".getBytes(StandardCharsets.UTF_8),"epub",100,100));
        reject(()->DownloadPolicy.verifyContent("\ufeff  <!DOCTYPE HTML><html>captcha".getBytes(StandardCharsets.UTF_8),"txt",100,100));
        reject(()->DownloadPolicy.verifyContent("\ufeff{ \n  \"error\" : \"expired\"}".getBytes(StandardCharsets.UTF_8),"txt",100,100));
        reject(()->DownloadPolicy.verifyContent(new byte[]{'P','K'},"epub",101,100));
        reject(()->DownloadPolicy.verifyContent(new byte[]{'x'},"pdf",1,1));
        reject(()->DownloadPolicy.verifyContent(new byte[0],"txt",0,0));
        if(!DownloadPolicy.extension("../../bad").equals("bin"))throw new AssertionError();
        System.out.println("Download policy checks passed: HTTPS, origins, response signatures, incomplete files and format bounds");
    }
}

