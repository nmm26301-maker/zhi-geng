package cn.zhigeng.app;

import java.net.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Public-client OAuth; no shared upstream client ids, client secret or password collection. */
final class AnimeTrackerAuth {
    static final String ANI="anilist",MAL="mal";
    static String service(String value)throws Exception{if(!ANI.equals(value)&&!MAL.equals(value))throw new Exception("账号类型不可用");return value;}
    static String callback(String service)throws Exception{return "zhigeng://oauth/"+service(service);}
    static String random(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static JSONObject begin(String service,String clientId,long now)throws Exception {
        service(service);if(!clientId.matches(service.equals(ANI)?"[0-9]{1,20}":"[A-Za-z0-9_-]{8,128}"))throw new Exception("请填写为知更注册的公开 Client ID");
        return new JSONObject().put("service",service).put("clientId",clientId).put("state",random()).put("verifier",random()).put("createdAt",now);
    }
    static String authorize(JSONObject pending)throws Exception {
        String s=service(pending.getString("service"));JSONObject q=new JSONObject().put("client_id",pending.getString("clientId")).put("state",pending.getString("state"));
        q.put("response_type",s.equals(ANI)?"token":"code");
        if(s.equals(MAL)){q.put("code_challenge",pending.getString("verifier")).put("code_challenge_method","plain").put("redirect_uri",callback(s));}
        return (s.equals(ANI)?"https://anilist.co/api/v2/oauth/authorize?":"https://myanimelist.net/v1/oauth2/authorize?")+AnimeNet.form(q);
    }
    static JSONObject answer(JSONObject pending,String raw,long now)throws Exception {
        if(now-pending.getLong("createdAt")>10*60*1000||now<pending.getLong("createdAt"))throw new Exception("授权已过期，请重新连接");
        URI uri=new URI(raw);String s=service(pending.getString("service"));
        if(!callback(s).equals(new URI(uri.getScheme(),uri.getAuthority(),uri.getPath(),null,null).toASCIIString())||uri.getUserInfo()!=null||raw.length()>16384)throw new Exception("授权返回地址不匹配");
        String encoded=s.equals(ANI)?uri.getRawFragment():uri.getRawQuery();JSONObject args=new JSONObject();
        if(encoded!=null)for(String pair:encoded.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8"),v=URLDecoder.decode(kv.length==2?kv[1]:"","UTF-8");if(args.has(k))throw new Exception("授权返回包含重复参数");args.put(k,v);}
        if(!MessageDigest.isEqual(pending.getString("state").getBytes(java.nio.charset.StandardCharsets.UTF_8),args.optString("state").getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new Exception("授权校验未通过，请重新连接");
        if(args.has("error"))throw new Exception("账号授权未获批准，请重新连接");
        String value=args.optString(s.equals(ANI)?"access_token":"code");if(value.isEmpty()||value.length()>12000||value.matches("(?s).*\\p{Cntrl}.*"))throw new Exception("授权没有返回有效凭证");return args;
    }
    static JSONObject exchange(JSONObject pending,JSONObject answer)throws Exception {
        return new JSONObject().put("client_id",pending.getString("clientId")).put("grant_type","authorization_code").put("code",answer.getString("code")).put("code_verifier",pending.getString("verifier")).put("redirect_uri",callback(MAL));
    }
}
