package cn.zhigeng.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Publishes complete book copies to shared Downloads without broad storage permissions. */
final class LocalBookStore {
    static final String DIRECTORY=Environment.DIRECTORY_DOWNLOADS+"/知更/";
    static final class SavedFile {
        final Uri uri;final String name;
        SavedFile(Uri uri,String name){this.uri=uri;this.name=name;}
    }
    static SavedFile publish(Context context,Uri source,String title,String format,AtomicBoolean cancelled)throws Exception {
        if(Build.VERSION.SDK_INT<29)throw new IOException("请选择手机上的保存位置");
        String name=BookFileNames.fileName(title,format);
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);
        values.put(MediaStore.Downloads.MIME_TYPE,BookFileNames.mimeType(format));
        values.put(MediaStore.Downloads.RELATIVE_PATH,DIRECTORY);values.put(MediaStore.Downloads.IS_PENDING,1);
        Uri uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("无法创建手机本地文件");
        boolean complete=false;
        try {
            copy(context,source,uri,cancelled);
            if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("已取消保存");
            values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);
            if(context.getContentResolver().update(uri,values,null,null)!=1)throw new IOException("本地文件尚未保存完成");
            complete=true;
            try(Cursor cursor=context.getContentResolver().query(uri,new String[]{MediaStore.Downloads.DISPLAY_NAME},null,null,null)) {
                if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
            }catch(Exception ignored){}
            return new SavedFile(uri,name);
        }finally {
            if(!complete)try{context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
        }
    }
    private static FileOutputStream output(Context context,Uri target)throws IOException {
        ParcelFileDescriptor descriptor=context.getContentResolver().openFileDescriptor(target,"w");
        if(descriptor==null)throw new IOException("无法创建本地文件");
        return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
    }
    static long copy(Context context,Uri source,Uri target,AtomicBoolean cancelled)throws Exception {
        if(source.equals(target))throw new IOException("请选择其他保存位置");
        try(InputStream in=context.getContentResolver().openInputStream(source);
            FileOutputStream out=output(context,target)) {
            if(in==null||out==null)throw new IOException("无法读写文件，请重新选择保存位置");
            byte[] buffer=new byte[32768];long bytes=0;int count;
            while((count=in.read(buffer))!=-1) {
                if(cancelled.get()||Thread.currentThread().isInterrupted())throw new IOException("已取消保存");
                out.write(buffer,0,count);bytes+=count;
            }
            if(bytes==0)throw new IOException("图书文件为空");
            out.flush();out.getFD().sync();return bytes;
        }
    }
}
