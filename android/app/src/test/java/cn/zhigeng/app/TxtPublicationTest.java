package cn.zhigeng.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
public class TxtPublicationTest {
 @Test public void chineseChaptersAndMarkupAreLocalAndEscaped()throws Exception{
  File dir=Files.createTempDirectory("txt-publication").toFile();String source="第一章 春日\n<script>alert('x')</script> & 正文\n\n第二章 夜色\n安静地读书。";File epub=TxtPublication.convert(source.getBytes(StandardCharsets.UTF_8),"书名 & 测试",dir);
  try(ZipFile z=new ZipFile(epub)){assertEquals(ZipEntry.STORED,z.getEntry("mimetype").getMethod());String c=new String(z.getInputStream(z.getEntry("OEBPS/chapter-0.xhtml")).readAllBytes(),StandardCharsets.UTF_8);assertTrue(c.contains("&lt;script&gt;"));assertFalse(c.contains("<script>"));assertTrue(c.contains("第一章 春日"));String opf=new String(z.getInputStream(z.getEntry("OEBPS/package.opf")).readAllBytes(),StandardCharsets.UTF_8);assertTrue(opf.contains("c0"));assertTrue(opf.contains("c1"));for(String p:new String[]{"OEBPS/package.opf","OEBPS/nav.xhtml","OEBPS/chapter-0.xhtml","OEBPS/chapter-1.xhtml"})DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(z.getInputStream(z.getEntry(p)));}
  assertEquals(epub,TxtPublication.convert(source.getBytes(StandardCharsets.UTF_8),"书名 & 测试",dir));assertEquals("第一章 春日",TxtPublication.decode("第一章 春日".getBytes(Charset.forName("GB18030"))));
 }
}
