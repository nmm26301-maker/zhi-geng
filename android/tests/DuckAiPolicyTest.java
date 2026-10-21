package cn.zhigeng.app;

public class DuckAiPolicyTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        for(String u:new String[]{"https://duck.ai/","https://www.duck.ai/chat","https://duckduckgo.com/?ia=chat","https://accounts.duckduckgo.com/","https://DUCK.AI:443/"})check(DuckAiPolicy.official(u),u);
        for(String u:new String[]{"https://duck.ai.attacker.test/","https://duckduckgo.com.attacker.test/","https://notduckduckgo.com/","https://duck.ai@attacker.test/","https://attacker@duck.ai/","https://duck.ai:444/","http://duck.ai/","javascript:alert(1)","intent://duck.ai","file:///sdcard/book.txt","data:text/html,hi","//duck.ai/","","invalid"})check(!DuckAiPolicy.official(u),u);
        check(DuckAiPolicy.https("https://example.org/source"),"external source");
        check(!DuckAiPolicy.https("content://downloads/private"),"content blocked");
        System.out.println("Duck.ai navigation policy passed");
    }
}
