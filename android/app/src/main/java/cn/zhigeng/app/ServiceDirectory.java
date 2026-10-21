package cn.zhigeng.app;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Validates the exact selected directories, so stale lists cannot silently replace them. */
final class ServiceDirectory {
    static JSONObject validate(String mode,String raw)throws Exception {
        JSONObject data=new JSONObject(raw);boolean nodes=mode.equals("nodes");
        if(data.optInt("schemaVersion")!=(nodes?3:2))throw new Exception("目录版本不匹配，请稍后刷新");
        JSONArray groups=data.getJSONArray("groups");if(groups.length()!=2)throw new Exception("目录分组不完整");
        String[][] expected=nodes?new String[][]{{"decodo","nextproxy","ipfast","lycheeip","iproyal"},{"xingdaomeng","freecat","jiuyun","baoyun","jinyun"}}:new String[][]{{"gamsgo","airadar","tehuio","familypro","yinhe"},{"huanqiu","relayradar"}};
        for(int g=0;g<2;g++){
            JSONArray entries=groups.getJSONObject(g).getJSONArray("entries");if(entries.length()!=expected[g].length)throw new Exception("网站名单不完整");
            for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(!e.optString("id").equals(expected[g][i])||e.optString("name").isBlank()||!https(e.optString("url")))throw new Exception("目录条目不匹配，保留当前名单");}
        }
        if(nodes){JSONArray extra=data.getJSONArray("shortcuts");if(extra.length()!=1||!extra.getJSONObject(0).optString("url").equals("https://xn--4gqx1hgtfdmt.com/#/dashboard"))throw new Exception("额外控制台入口不匹配");}
        return data;
    }
    static boolean https(String value){try{URI u=URI.create(value);return u.getScheme().equals("https")&&u.getHost()!=null&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}}
    static boolean matches(JSONObject e,String query,String brand,String delivery){
        if(!e.toString().toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT)))return false;
        if(!brand.equals("all")&&!contains(e.optJSONArray("brands"),brand))return false;
        return delivery.equals("all")||contains(e.optJSONArray("types"),delivery);
    }
    private static boolean contains(JSONArray array,String value){if(array==null)return false;for(int i=0;i<array.length();i++)if(array.optString(i).equals(value))return true;return false;}
}
