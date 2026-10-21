package cn.zhigeng.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.net.*;

/** 从应用资源或 HTTPS 来源加载封面，限制文件及解码尺寸。 */
final class CoverLoader {
    static Bitmap local(Context context,String asset)throws Exception {
        if(context==null||asset==null||!asset.matches("posters/[a-f0-9]{24}\\.(jpg|png|webp|gif)"))
            throw new IOException("离线封面路径无效");
        try(InputStream in=context.getAssets().open(asset)){return decode(read(in));}
    }
    static Bitmap download(String original)throws Exception {
        String current=original;
        for(int redirects=0;redirects<=4;redirects++){
            if(!ServiceDirectory.https(current))throw new IOException("封面地址无效");
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setConnectTimeout(6500);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","ZhigengAndroid/0.6.7");
            c.setRequestProperty("Accept","image/webp,image/png,image/jpeg,image/*;q=0.8");
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400){
                    String location=c.getHeaderField("Location");
                    if(location==null)throw new IOException("封面跳转无效");
                    current=new URL(new URL(current),location).toString();continue;
                }
                if(status!=200)throw new IOException("封面 HTTP "+status);
                try(InputStream in=c.getInputStream()){return decode(read(in));}
            }finally{c.disconnect();}
        }
        throw new IOException("封面跳转次数过多");
    }
    private static byte[] read(InputStream in)throws IOException {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){
                if(out.size()+count>6_000_000)throw new IOException("封面过大");
                out.write(buffer,0,count);
            }
            return out.toByteArray();
        }
    }
    private static Bitmap decode(byte[] bytes)throws IOException {
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
        if(opts.outWidth<=0||opts.outHeight<=0||opts.outWidth>15000||opts.outHeight>15000)
            throw new IOException("封面格式无法解码");
        opts.inJustDecodeBounds=false;opts.inSampleSize=1;
        while(opts.outWidth/opts.inSampleSize>900||opts.outHeight/opts.inSampleSize>1400)opts.inSampleSize*=2;
        Bitmap result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
        if(result==null)throw new IOException("封面无法解码");
        return result;
    }
}
