package cn.zhigeng.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Bind private chat state to both a gateway and its credential, without raw secrets in preference keys. */
public final class FccSessionKeys {
    private FccSessionKeys() {}
    public static String key(String kind,String base,String token){return "fcc_"+kind+"_"+hash(base+"\n"+token);}
    public static String legacy(String kind,String base){return "fcc_"+kind+"_"+hash(base);}
    private static String hash(String input){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b));return result.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
