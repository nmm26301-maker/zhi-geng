package cn.zhigeng.app;
import java.net.URI;
import java.util.Locale;

final class DownloadPolicy {
    static String validate(String raw)throws Exception{
        URI u=new URI(raw);String host=u.getHost();
        if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)throw new Exception("下载链接必须使用 HTTPS");
        host=host.toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".local")||host.endsWith(".localhost")||host.matches("[0-9.]+")||host.contains(":"))throw new Exception("下载地址不可用");
        return u.toString();
    }
    static boolean sameOrigin(String a,String b)throws Exception{
        URI x=new URI(validate(a)),y=new URI(validate(b));return x.getHost().equalsIgnoreCase(y.getHost());
    }
    static void verifyContent(byte[] prefix,String extension,long total,long bytes)throws Exception{
        if(bytes==0||total>=0&&bytes!=total)throw new Exception("文件下载不完整，请重试");
        String head=new String(prefix,java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if(head.startsWith("<!doctype html")||head.startsWith("<html")||head.startsWith("{\"error\""))throw new Exception("来源返回错误页，未加入书架");
        if(extension.equals("pdf")&&!(prefix.length>=4&&prefix[0]=='%'&&prefix[1]=='P'&&prefix[2]=='D'&&prefix[3]=='F'))throw new Exception("来源返回的文件不是 PDF");
        if(extension.equals("epub")&&!(prefix.length>=2&&prefix[0]=='P'&&prefix[1]=='K'))throw new Exception("来源返回的文件不是 EPUB");
    }
    static String extension(String raw){String ext=raw.toLowerCase(Locale.ROOT);return ext.matches("epub|pdf|txt|mobi|azw3|djvu|fb2")?ext:"bin";}
}
