package cn.zhigeng.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bounded, independent HTTPS requests. No app cookies, account store or automatic auth redirects. */
class AnimeNet {
    static final int LIMIT=4*1024*1024;
    static final class Reply {
        final int status;final String url;final byte[] bytes;final JSONObject headers;
        Reply(int status,String url,byte[] bytes,JSONObject headers){this.status=status;this.url=url;this.bytes=bytes;this.headers=headers;}
        String text(){return new String(bytes,StandardCharsets.UTF_8);}
        JSONObject json()throws Exception{return new JSONObject(text());}
    }
    static URI publicUrl(String raw,boolean privateAllowed)throws Exception {
        URI u=new URI(raw);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||raw.length()>8192)throw new IOException("需要有效的 HTTPS 地址");
        if(!privateAllowed)for(InetAddress ip:InetAddress.getAllByName(u.getHost()))
            if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress()||((ip.getAddress()[0]&0xfe)==0xfc))throw new IOException("扩展不能访问本机或内网地址");
        return u;
    }
    Reply request(String raw,String method,JSONObject headers,byte[] body,boolean privateAllowed)throws Exception {
        return request(raw,method,headers,body,privateAllowed,"follow");
    }
    Reply request(String raw,String method,JSONObject headers,byte[] body,boolean privateAllowed,String redirects)throws Exception {
        String url=raw;JSONObject current=new JSONObject(headers.toString());
        for(int redirect=0;redirect<6;redirect++) {
            URI u=publicUrl(url,privateAllowed);HttpURLConnection c=(HttpURLConnection)u.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestMethod(method);
            c.setRequestProperty("User-Agent","Zhigeng/0.6.19");c.setRequestProperty("Accept-Encoding","identity");
            Iterator<String> keys=current.keys();while(keys.hasNext()){String k=keys.next(),v=current.getString(k);if(!k.matches("[A-Za-z0-9-]{1,80}")||v.indexOf('\r')>=0||v.indexOf('\n')>=0||v.length()>8192||k.equalsIgnoreCase("Host")||k.equalsIgnoreCase("Content-Length"))throw new IOException("请求头不可用");c.setRequestProperty(k,v);}
            try {
                if(body!=null){if(body.length>1024*1024)throw new IOException("请求内容过大");c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body);}}
                int status=c.getResponseCode();
                if(status>=300&&status<400&&c.getHeaderField("Location")!=null&&!redirects.equals("manual")){
                    if(redirects.equals("error"))throw new IOException("扩展不允许此请求跳转");
                    String next=u.resolve(c.getHeaderField("Location")).toASCIIString();URI to=publicUrl(next,privateAllowed);
                    if(!u.getHost().equalsIgnoreCase(to.getHost())||u.getPort()!=to.getPort()) {
                        if(current.has("Authorization")||current.has("authorization"))throw new IOException("已阻止账号请求跨站跳转");
                        JSONObject safe=new JSONObject();for(String k:new String[]{"User-Agent","Accept","Accept-Language","Referer","Origin"})if(current.has(k))safe.put(k,current.get(k));current=safe;
                    }
                    if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body=null;}url=next;continue;
                }
                JSONObject responseHeaders=new JSONObject();for(Map.Entry<String,List<String>> e:c.getHeaderFields().entrySet())if(e.getKey()!=null)responseHeaders.put(e.getKey().toLowerCase(Locale.ROOT),String.join(", ",e.getValue()));
                InputStream stream=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("请求已取消");if(out.size()+count>LIMIT)throw new IOException("服务返回内容过大");out.write(buffer,0,count);}}
                return new Reply(status,url,out.toByteArray(),responseHeaders);
            }finally{c.disconnect();}
        }throw new IOException("服务跳转次数过多");
    }
    static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static String form(JSONObject values)throws Exception {
        ArrayList<String> pairs=new ArrayList<>();Iterator<String> keys=values.keys();while(keys.hasNext()){String k=keys.next();pairs.add(URLEncoder.encode(k,"UTF-8")+"="+URLEncoder.encode(String.valueOf(values.get(k)),"UTF-8"));}return String.join("&",pairs);
    }
}
