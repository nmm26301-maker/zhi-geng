package cn.zhigeng.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Bounded EPUB container preflight; Readium remains responsible for publication parsing. */
final class DownloadedBookVerifier {
    private static final String INVALID="下载文件损坏或不是 EPUB，请重新获取";
    static void checkCancelled(AtomicBoolean cancelled)throws Exception {
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new Exception("已取消下载");
    }
    static void verify(File file,String extension,AtomicBoolean cancelled)throws Exception {
        checkCancelled(cancelled);
        if(!"epub".equals(extension))return;
        try(ZipFile zip=new ZipFile(file)) {
            byte[] mime=read(zip,"mimetype",20,cancelled);
            if(!Arrays.equals(mime,"application/epub+zip".getBytes(StandardCharsets.US_ASCII)))throw new IOException(INVALID);
            read(zip,"META-INF/container.xml",512*1024,cancelled);
        }catch(IOException e){throw new Exception(INVALID,e);}
        checkCancelled(cancelled);
    }
    private static byte[] read(ZipFile zip,String path,int limit,AtomicBoolean cancelled)throws Exception {
        ZipEntry entry=zip.getEntry(path);
        if(entry==null||entry.isDirectory()||entry.getSize()<=0||entry.getSize()>limit)throw new IOException(INVALID);
        CRC32 crc=new CRC32();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=zip.getInputStream(entry)) {
            byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1) {
                checkCancelled(cancelled);
                if(bytes.size()+count>limit)throw new IOException(INVALID);
                bytes.write(buffer,0,count);crc.update(buffer,0,count);
            }
        }
        if(bytes.size()!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new IOException(INVALID);
        return bytes.toByteArray();
    }
}
