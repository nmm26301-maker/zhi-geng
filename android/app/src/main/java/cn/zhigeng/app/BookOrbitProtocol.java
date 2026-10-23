package cn.zhigeng.app;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.*;

/** Original client-side adapter for BookOrbit's public v1 API; no server code is bundled. */
final class BookOrbitProtocol {
    static String base(String raw) throws Exception {
        URI uri = new URI(raw.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535)
            throw new Exception("请填写 BookOrbit 的 HTTPS 服务地址，不含账号、查询参数或登录链接");
        String value = uri.normalize().toASCIIString().replaceAll("/+$", "");
        if (value.endsWith("/api/v1")) value = value.substring(0, value.length() - 7);
        return value;
    }

    static boolean sameOrigin(String a, String b) throws Exception {
        URI x = new URI(a), y = new URI(b);
        return "https".equalsIgnoreCase(y.getScheme()) && y.getUserInfo() == null && y.getHost() != null
                && x.getHost().equalsIgnoreCase(y.getHost()) && port(x) == port(y);
    }

    private static int port(URI uri) { return uri.getPort() < 0 ? 443 : uri.getPort(); }

    static String downloadUrl(String base, String raw) throws Exception {
        URI target = new URI(raw);
        if (!sameOrigin(base, raw) || target.getRawFragment() != null)
            throw new Exception("BookOrbit 下载不能跳转到其他服务器，请在原站确认文件地址");
        return target.toASCIIString();
    }

    static String fileUrl(String base, long id) throws Exception {
        if (id <= 0) throw new Exception("图书文件编号无效");
        return base(base) + "/api/v1/books/files/" + id + "/download";
    }

    static JSONObject query(String term, int page) throws Exception {
        if (term.length() > 200 || page < 0) throw new Exception("搜索词过长或页码无效");
        return new JSONObject().put("q", term).put("pagination", new JSONObject().put("page", page).put("size", 20));
    }

    static String author(JSONObject book) {
        JSONArray authors = book.optJSONArray("authors");
        if (authors == null) return book.optString("author", "");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < authors.length(); i++) {
            Object value = authors.opt(i);
            String name = value instanceof JSONObject ? ((JSONObject) value).optString("name") : String.valueOf(value);
            if (!name.isEmpty()) { if (names.length() > 0) names.append("、"); names.append(name); }
        }
        return names.toString();
    }

    static boolean supported(String format) {
        return format != null && format.toLowerCase(Locale.ROOT).matches("epub|pdf|txt|mobi|azw3|djvu|fb2");
    }

    static JSONObject shelfItem(String base, JSONObject book, JSONObject file, String localUri) throws Exception {
        long bookId = book.getLong("id"), fileId = file.getLong("id");
        return new JSONObject().put("id", "bookorbit:" + URLEncoder.encode(base, StandardCharsets.UTF_8.name()) + ":" + fileId)
                .put("title", book.isNull("title") ? "未命名图书" : book.optString("title", "未命名图书"))
                .put("author", author(book)).put("type", "book").put("source", "BookOrbit")
                .put("url", base + "/books/" + bookId).put("localUri", localUri)
                .put("format", file.optString("format").toLowerCase(Locale.ROOT));
    }
}
