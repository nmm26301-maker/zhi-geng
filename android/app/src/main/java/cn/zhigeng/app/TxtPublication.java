package cn.zhigeng.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Offline TXT adapter: turns escaped text into a small, chaptered EPUB for Readium. */
public final class TxtPublication {
    private TxtPublication() {}
    private static final Pattern HEADING=Pattern.compile("^(第[零〇一二三四五六七八九十百千万两0-9]{1,12}[章节卷部篇回].{0,45}|(?:序言|前言|后记|尾声|引子|楔子|目录)|Chapter\\s+[0-9IVXLC]+.{0,45})$",Pattern.CASE_INSENSITIVE);
    public static String decode(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF","");}catch(Exception e){return new String(bytes,Charset.forName("GB18030")).replace("\uFEFF","");}}
    public static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>16_000_000)throw new IOException("TXT 超过 16 MB，请拆分后导入");out.write(b,0,n);}return out.toByteArray();}
    public static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    public static File convert(byte[] bytes,String title,File directory)throws Exception{
        String text=decode(bytes).replace("\r\n","\n").replace('\r','\n');if(text.trim().isEmpty())throw new IOException("TXT 文件没有可阅读的正文");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(bytes);digest.update(title.getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder();for(byte b:digest.digest())id.append(String.format(Locale.ROOT,"%02x",b));
        directory.mkdirs();File target=new File(directory,"txt-"+id+".epub");if(target.isFile())return target;
        ArrayList<String> names=new ArrayList<>(),pages=new ArrayList<>();String current=title;StringBuilder body=new StringBuilder();
        for(String line:text.split("\n",-1)){String trimmed=line.trim();if(trimmed.isEmpty())continue;boolean heading=HEADING.matcher(trimmed).matches();if((heading&&body.length()>0)||body.length()>32000){names.add(current);pages.add(body.toString());if(names.size()>2000)throw new IOException("TXT 章节过多");body.setLength(0);if(!heading)current="正文 · "+(names.size()+1);}
            if(heading){current=trimmed;}else body.append("<p>").append(escape(trimmed)).append("</p>\n");
        }
        if(body.length()>0||pages.isEmpty()){names.add(current);pages.add(body.toString());}
        File temporary=File.createTempFile("txt-adapter-",".tmp",directory);
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temporary))){byte[] mime="application/epub+zip".getBytes(StandardCharsets.US_ASCII);CRC32 crc=new CRC32();crc.update(mime);ZipEntry me=new ZipEntry("mimetype");me.setMethod(ZipEntry.STORED);me.setSize(mime.length);me.setCrc(crc.getValue());zip.putNextEntry(me);zip.write(mime);zip.closeEntry();
            put(zip,"META-INF/container.xml","<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/package.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            StringBuilder manifest=new StringBuilder("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"),spine=new StringBuilder(),nav=new StringBuilder();
            for(int i=0;i<pages.size();i++){String file="chapter-"+i+".xhtml";manifest.append("<item id=\"c").append(i).append("\" href=\"").append(file).append("\" media-type=\"application/xhtml+xml\"/>");spine.append("<itemref idref=\"c").append(i).append("\"/>");nav.append("<li><a href=\"").append(file).append("\">").append(escape(names.get(i))).append("</a></li>");put(zip,"OEBPS/"+file,page(names.get(i),"<h1>"+escape(names.get(i))+"</h1>"+pages.get(i)));}
            put(zip,"OEBPS/nav.xhtml",page(title,"<nav xmlns:epub=\"http://www.idpf.org/2007/ops\" epub:type=\"toc\"><h1>目录</h1><ol>"+nav+"</ol></nav>"));
            put(zip,"OEBPS/package.opf","<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"book-id\">urn:sha256:"+id+"</dc:identifier><dc:title>"+escape(title)+"</dc:title><dc:language>zh-CN</dc:language><meta property=\"dcterms:modified\">2026-10-17T00:00:00Z</meta></metadata><manifest>"+manifest+"</manifest><spine>"+spine+"</spine></package>");
        }catch(Exception e){temporary.delete();throw e;}if(!temporary.renameTo(target)){temporary.delete();throw new IOException("本地排版文件保存失败");}return target;
    }
    private static String page(String title,String body){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh-CN\" xml:lang=\"zh-CN\"><head><title>"+escape(title)+"</title><meta charset=\"utf-8\"/><style>body{line-height:1.8}h1{font-size:1.35em;line-height:1.6;margin:2em 0 1.5em;font-weight:normal}p{text-indent:2em;margin:0 0 .65em}</style></head><body>"+body+"</body></html>";}
    private static void put(ZipOutputStream zip,String name,String value)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
