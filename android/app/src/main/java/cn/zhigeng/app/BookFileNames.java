package cn.zhigeng.app;

import java.util.Locale;

/** Names for independent copies in phone storage, never paths supplied by a source. */
final class BookFileNames {
    static String fileName(String title,String format) {
        String ext=DownloadPolicy.extension(format);
        String clean=(title==null?"":title).replaceAll("[\\p{Cc}\\p{Cf}/\\\\:*?\"<>|]","_").trim();
        if(clean.toLowerCase(Locale.ROOT).endsWith("."+ext))clean=clean.substring(0,clean.length()-ext.length()-1);
        clean=clean.replaceAll("^[. ]+|[. ]+$","");
        if(clean.isEmpty())clean="电子书";
        if(clean.codePointCount(0,clean.length())>72)clean=clean.substring(0,clean.offsetByCodePoints(0,72));
        return clean+"."+ext;
    }
    static String mimeType(String format) {
        switch(DownloadPolicy.extension(format)) {
            case "epub":return "application/epub+zip";
            case "pdf":return "application/pdf";
            case "txt":return "text/plain";
            case "mobi":return "application/x-mobipocket-ebook";
            case "azw3":return "application/vnd.amazon.ebook";
            case "fb2":return "application/x-fictionbook+xml";
            case "djvu":return "image/vnd.djvu";
            default:return "application/octet-stream";
        }
    }
}
