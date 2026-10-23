package cn.zhigeng.app;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ResourceSearchLinks {
    static final String HOME="https://jys66.top/index.html";
    static final String[][] CATEGORIES={
        {"漫画导航","manhua.html","按原站分类找漫画网站"},
        {"动漫导航","dongman.html","番剧、动画与国创入口"},
        {"电子书导航","dianzishu.html","图书与电子书资源入口"},
        {"影视导航","index.html","电影、剧集与网盘搜索"},
        {"字幕导航","zimu.html","字幕下载与检索网站"},
        {"应用工具","app.html","原站应用与工具分类"}
    };
    static String clean(String input) {
        return input.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
    }
    static String search(String input) throws Exception {
        String term=clean(input);
        if(term.isEmpty())throw new Exception("请先填写漫画、书名或影视名称");
        if(term.codePointCount(0,term.length())>100)throw new Exception("搜索词请控制在100字以内");
        return HOME+"?q="+URLEncoder.encode(term,StandardCharsets.UTF_8.name());
    }
}
