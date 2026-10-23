package cn.zhigeng.app;
import android.content.Context;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;

final class BookDownloader {
    interface Progress{void update(long bytes,long total);}
    static File download(Context ctx,String raw,String extension,Map<String,String> credentials,AtomicBoolean cancelled,Progress progress)throws Exception{
        return transfer(ctx,raw,extension,credentials,cancelled,progress,null);
    }
    static File downloadFromBookOrbit(Context ctx,String server,String raw,String extension,Map<String,String> credentials,AtomicBoolean cancelled,Progress progress)throws Exception{
        return transfer(ctx,raw,extension,credentials,cancelled,progress,BookOrbitProtocol.base(server));
    }
    private static File transfer(Context ctx,String raw,String extension,Map<String,String> credentials,AtomicBoolean cancelled,Progress progress,String bookOrbit)throws Exception{
        String original=bookOrbit==null?DownloadPolicy.validate(raw):BookOrbitProtocol.downloadUrl(bookOrbit,raw),current=original;File dir=new File(ctx.getFilesDir(),"ebooks");if(!dir.exists()&&!dir.mkdirs())throw new Exception("无法创建图书目录");
        File part=new File(dir,UUID.randomUUID()+".part"),result=new File(dir,UUID.randomUUID()+"."+DownloadPolicy.extension(extension));
        try{
            for(int redirects=0;redirects<=5;redirects++){
                if(cancelled.get())throw new Exception("已取消下载");HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","ZhigengAndroid/0.6");c.setRequestProperty("Accept-Encoding","identity");
                if(bookOrbit==null?DownloadPolicy.sameOrigin(original,current):BookOrbitProtocol.sameOrigin(original,current))for(Map.Entry<String,String> h:credentials.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
                try{
                    int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new Exception("下载跳转没有目标地址");String target=new URL(new URL(current),location).toString();current=bookOrbit==null?DownloadPolicy.validate(target):BookOrbitProtocol.downloadUrl(bookOrbit,target);continue;}
                    if(code!=200)throw new Exception("下载失败（HTTP "+code+"），请重新获取链接");String type=Objects.toString(c.getContentType(),"").toLowerCase(Locale.ROOT);if(type.contains("text/html")||type.contains("application/json"))throw new Exception("来源返回验证页或错误信息，尚未取得电子书");
                    long total=c.getContentLengthLong();if(total>100L*1024*1024)throw new Exception("文件超过本次下载上限 100 MB");long bytes=0,last=0;ByteArrayOutputStream head=new ByteArrayOutputStream();
                    try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){if(cancelled.get())throw new Exception("已取消下载");if(bytes+n>100L*1024*1024)throw new Exception("文件超过 100 MB");if(head.size()<512)head.write(buffer,0,Math.min(n,512-head.size()));out.write(buffer,0,n);bytes+=n;if(System.currentTimeMillis()-last>180){progress.update(bytes,total);last=System.currentTimeMillis();}}out.getFD().sync();}
                    DownloadPolicy.verifyContent(head.toByteArray(),extension,total,bytes);
                    if(!part.renameTo(result))throw new Exception("无法保存已下载文件");progress.update(bytes,bytes);return result;
                }finally{c.disconnect();}
            }throw new Exception("下载跳转次数过多，请重新获取链接");
        }finally{if(part.exists())part.delete();}
    }
}
