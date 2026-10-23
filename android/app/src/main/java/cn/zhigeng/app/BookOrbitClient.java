package cn.zhigeng.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Per-server session. Credentials never enter URLs, global cookies, logs, or other origins. */
final class BookOrbitClient {
    final String base;
    private String access = "", refresh = "";
    private boolean webRefresh;
    BookOrbitClient(String origin) throws Exception { base = BookOrbitProtocol.base(origin); }

    static BookOrbitClient restore(String json) throws Exception {
        JSONObject saved = new JSONObject(json);
        BookOrbitClient client = new BookOrbitClient(saved.getString("base"));
        client.access = saved.optString("access"); client.refresh = saved.optString("refresh");
        client.webRefresh = saved.optBoolean("webRefresh");
        return client;
    }

    synchronized String session() throws Exception {
        return new JSONObject().put("base", base).put("access", access).put("refresh", refresh).put("webRefresh",webRefresh).toString();
    }

    synchronized void login(String username, String password) throws Exception {
        webRefresh=false;access="";refresh="";
        accept((JSONObject) request("POST", "/auth/login", new JSONObject().put("username", username)
                .put("password", password).put("clientKind", "native").put("deviceLabel", "知更 Android"), false));
    }

    synchronized void magicLogin(String link) throws Exception {
        URI uri = new URI(link.trim());
        if (!BookOrbitProtocol.sameOrigin(base, link)) throw new Exception("登录链接必须属于填写的 BookOrbit 服务器");
        String token = "";
        for (String part : Objects.toString(uri.getRawQuery(), "").split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals("token")) token = URLDecoder.decode(pair[1], "UTF-8");
        }
        if (!token.matches("[a-fA-F0-9]{64}")) throw new Exception("登录链接缺少有效 token");
        // The upstream magic-link DTO is web-only; keep its refresh cookie scoped to this client.
        webRefresh=true;access="";refresh="";
        accept((JSONObject) request("POST", "/auth/magic-links/login", new JSONObject().put("token", token), false));
    }

    private void accept(JSONObject result) throws Exception {
        String token = result.optString("accessToken");
        if (token.isEmpty() || token.contains("\r") || token.contains("\n")) throw new Exception("服务器未返回有效会话");
        access = token;
        refresh = result.optString("refreshToken", refresh);
    }

    synchronized JSONArray libraries() throws Exception { return (JSONArray) request("GET", "/libraries", null, true); }
    synchronized JSONObject books(long library, String term, int page) throws Exception {
        if (library <= 0) throw new Exception("请选择书库");
        return (JSONObject) request("POST", "/libraries/" + library + "/books", BookOrbitProtocol.query(term, page), true);
    }
    synchronized JSONObject detail(long id) throws Exception {
        if (id <= 0) throw new Exception("图书编号无效");
        return (JSONObject) request("GET", "/books/" + id, null, true);
    }
    synchronized Map<String, String> downloadHeaders() throws Exception {
        request("GET", "/auth/me", null, true);
        return Collections.singletonMap("Authorization", "Bearer " + access);
    }
    synchronized void logout() throws Exception {
        try { request("POST", "/auth/logout", refreshBody(), false); }
        finally { access = ""; refresh = ""; }
    }

    private JSONObject refreshBody() throws Exception {
        return webRefresh ? new JSONObject() : new JSONObject().put("refreshToken",refresh);
    }

    private Object request(String method, String path, JSONObject body, boolean retry) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(base + "/api/v1" + path).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(18000); connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method); connection.setRequestProperty("Accept", "application/json");
        if (!access.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + access);
        if(webRefresh&&!refresh.isEmpty()&&(path.equals("/auth/refresh")||path.equals("/auth/logout")))
            connection.setRequestProperty("Cookie","refresh_token="+refresh);
        try {
            if (body != null) {
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = connection.getResponseCode();
            if(webRefresh&&status>=200&&status<300&&path.startsWith("/auth/")) {
                for(Map.Entry<String,List<String>> entry:connection.getHeaderFields().entrySet()) {
                    if(!"set-cookie".equalsIgnoreCase(entry.getKey()))continue;
                    for(String value:entry.getValue())for(HttpCookie cookie:HttpCookie.parse(value))
                        if(cookie.getName().equals("refresh_token")&&cookie.getValue().matches("[a-f0-9]{64}"))refresh=cookie.getValue();
                }
            }
            if (status == 401 && retry && !refresh.isEmpty()) {
                connection.disconnect();
                accept((JSONObject) request("POST", "/auth/refresh", refreshBody(), false));
                return request(method, path, body, false);
            }
            if (status == 401) throw new Exception("BookOrbit 会话已失效，请重新登录");
            if (status == 403) throw new Exception("当前账号没有访问这项内容的权限");
            if (status == 429) throw new Exception("请求较多，请稍后重试");
            if (status < 200 || status >= 300) throw new Exception("BookOrbit 服务暂不可用（HTTP " + status + "）");
            if (status == 204) return new JSONObject();
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = in.read(bytes)) != -1) {
                    if (out.size() + count > 4 * 1024 * 1024) throw new Exception("书库返回的数据过大，请缩小搜索范围");
                    out.write(bytes, 0, count);
                }
                Object value = new JSONTokener(out.toString("UTF-8")).nextValue();
                if (!(value instanceof JSONObject) && !(value instanceof JSONArray)) throw new Exception("此地址未返回 BookOrbit 数据");
                return value;
            }
        } finally { connection.disconnect(); }
    }
}
