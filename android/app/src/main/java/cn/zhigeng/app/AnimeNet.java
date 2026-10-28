package cn.zhigeng.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

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
            if(privateAddress(ip))throw new IOException("扩展不能访问本机或内网地址");
        return u;
    }
    private static boolean privateAddress(InetAddress ip){return ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress()||((ip.getAddress()[0]&0xfe)==0xfc);}
    Reply request(String raw,String method,JSONObject headers,byte[] body,boolean privateAllowed)throws Exception {
        return request(raw,method,headers,body,privateAllowed,"follow");
    }
    Reply request(String raw,String method,JSONObject headers,byte[] body,boolean privateAllowed,String redirects)throws Exception {
        String url=raw;JSONObject current=new JSONObject(headers.toString());
        for(int redirect=0;redirect<6;redirect++) {
            URI u=publicUrl(url,privateAllowed);
            OkHttpClient client=new OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
                .connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
                .dns(host->{List<InetAddress> addresses=Arrays.asList(InetAddress.getAllByName(host));if(!privateAllowed)for(InetAddress ip:addresses)if(privateAddress(ip))throw new UnknownHostException("扩展不能访问本机或内网地址");return addresses;}).build();
            Request.Builder request=new Request.Builder().url(url).header("User-Agent","Zhigeng/0.6.19");
            Iterator<String> keys=current.keys();while(keys.hasNext()){String k=keys.next(),v=current.getString(k);if(!k.matches("[A-Za-z0-9-]{1,80}")||v.indexOf('\r')>=0||v.indexOf('\n')>=0||v.length()>8192||k.equalsIgnoreCase("Host")||k.equalsIgnoreCase("Content-Length"))throw new IOException("请求头不可用");if(!k.equalsIgnoreCase("Accept-Encoding"))request.header(k,v);}
            if(body!=null&&body.length>1024*1024)throw new IOException("请求内容过大");
            RequestBody payload=body==null?null:RequestBody.create(body,MediaType.parse(current.optString("Content-Type","application/octet-stream")));
            if(payload==null&&Arrays.asList("POST","PUT","PATCH").contains(method))payload=RequestBody.create(new byte[0],null);
            request.method(method,payload);
            try(Response response=client.newCall(request.build()).execute()){
                int status=response.code();
                if(status>=300&&status<400&&response.header("Location")!=null&&!redirects.equals("manual")){
                    if(redirects.equals("error"))throw new IOException("扩展不允许此请求跳转");
                    String next=u.resolve(response.header("Location")).toASCIIString();URI to=publicUrl(next,privateAllowed);
                    if(!u.getHost().equalsIgnoreCase(to.getHost())||u.getPort()!=to.getPort()) {
                        if(current.has("Authorization")||current.has("authorization"))throw new IOException("已阻止账号请求跨站跳转");
                        JSONObject safe=new JSONObject();for(String k:new String[]{"User-Agent","Accept","Accept-Language","Referer","Origin"})if(current.has(k))safe.put(k,current.get(k));current=safe;
                    }
                    if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body=null;}url=next;continue;
                }
                JSONObject responseHeaders=new JSONObject();for(Map.Entry<String,List<String>> e:response.headers().toMultimap().entrySet())if(e.getKey()!=null)responseHeaders.put(e.getKey().toLowerCase(Locale.ROOT),String.join(", ",e.getValue()));
                InputStream stream=response.body()==null?null:response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(stream!=null)try(InputStream in=stream){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("请求已取消");if(out.size()+count>LIMIT)throw new IOException("服务返回内容过大");out.write(buffer,0,count);}}
                return new Reply(status,url,out.toByteArray(),responseHeaders);
            }finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
        }throw new IOException("服务跳转次数过多");
    }
    static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static String form(JSONObject values)throws Exception {
        ArrayList<String> pairs=new ArrayList<>();Iterator<String> keys=values.keys();while(keys.hasNext()){String k=keys.next();pairs.add(URLEncoder.encode(k,"UTF-8")+"="+URLEncoder.encode(String.valueOf(values.get(k)),"UTF-8"));}return String.join("&",pairs);
    }
}
