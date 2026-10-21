package cn.zhigeng.app;

import java.net.URI;
import java.util.Locale;

/** Official chat stays here; other HTTPS sources open in the internal source viewer. */
public final class DuckAiPolicy {
    public static final String HOME = "https://duck.ai/";
    private DuckAiPolicy() {}
    public static boolean https(String raw) {
        try {
            URI u = new URI(raw);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null
                    && u.getRawUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) { return false; }
    }
    public static boolean official(String raw) {
        if (!https(raw)) return false;
        String host = URI.create(raw).getHost().toLowerCase(Locale.ROOT);
        return host.equals("duck.ai") || host.equals("www.duck.ai")
                || host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com");
    }
}
