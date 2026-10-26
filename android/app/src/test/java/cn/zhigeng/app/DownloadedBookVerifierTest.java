package cn.zhigeng.app;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import static org.junit.Assert.*;

public class DownloadedBookVerifierTest {
    private byte[] epub(String mime,byte[] container)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            if(mime!=null)put(zip,"mimetype",mime.getBytes(StandardCharsets.US_ASCII));
            if(container!=null)put(zip,"META-INF/container.xml",container);
            put(zip,"OEBPS/package.opf","<package/>".getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
    private void put(ZipOutputStream zip,String path,byte[] data)throws Exception {
        zip.putNextEntry(new ZipEntry(path));zip.write(data);zip.closeEntry();
    }
    private byte[] valid()throws Exception {
        return epub("application/epub+zip","<container><rootfiles><rootfile full-path=\"OEBPS/package.opf\"/></rootfiles></container>".getBytes(StandardCharsets.UTF_8));
    }
    private void verify(byte[] data,AtomicBoolean stop)throws Exception {
        Path path=Files.createTempFile("zhigeng-download-", ".part");
        try{Files.write(path,data);DownloadedBookVerifier.verify(path.toFile(),"epub",stop);}
        finally{Files.delete(path);}
    }
    private void rejects(byte[] data)throws Exception {
        try{verify(data,new AtomicBoolean());fail("Invalid EPUB accepted");}
        catch(Exception e){assertEquals("下载文件损坏或不是 EPUB，请重新获取",e.getMessage());}
    }
    @Test public void acceptsAnIndexedContainerWithRequiredMetadata()throws Exception {
        verify(valid(),new AtomicBoolean());
    }
    @Test public void rejectsOrdinaryZipMissingAndWrongMetadata()throws Exception {
        rejects(epub(null,null));rejects(epub("application/zip",new byte[]{1}));
        rejects(epub("application/epub+zip",null));rejects(epub("application/epub+zip",new byte[0]));
    }
    @Test public void rejectsTruncatedAndHeaderOnlyFiles()throws Exception {
        byte[] bytes=valid();rejects(Arrays.copyOf(bytes,bytes.length-22));rejects(new byte[]{'P','K',3,4});
    }
    @Test public void boundsMetadataEvenWhenItCompressesToAlmostNothing()throws Exception {
        rejects(epub("application/epub+zip",new byte[512*1024+1]));
    }
    @Test public void checksCrcRatherThanTrustingTheCentralDirectory()throws Exception {
        byte[] data=valid();boolean changed=false;
        for(int i=0;i+46<data.length;i++)if(data[i]==0x50&&data[i+1]==0x4b&&data[i+2]==1&&data[i+3]==2){
            data[i+16]^=1;changed=true;break;
        }
        assertTrue(changed);rejects(data);
    }
    @Test public void cancellationAndThreadInterruptionTakePrecedence()throws Exception {
        try{verify(valid(),new AtomicBoolean(true));fail();}catch(Exception e){assertEquals("已取消下载",e.getMessage());}
        Path path=Files.createTempFile("zhigeng-interrupt-", ".part");Files.write(path,valid());
        Thread.currentThread().interrupt();
        try{DownloadedBookVerifier.verify(path.toFile(),"epub",new AtomicBoolean());fail();}catch(Exception e){assertEquals("已取消下载",e.getMessage());}
        finally{Thread.interrupted();Files.delete(path);}
    }
}
