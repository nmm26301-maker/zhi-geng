package cn.zhigeng.app;

import java.net.URI;

/** Only user-selected web addresses may be handed to an external browser. */
public final class BrowserPolicy {
    private BrowserPolicy() {}

    public static String webUrl(String raw) {
        if (raw == null || raw.length() > 8192) return "";
        String value = raw.trim();
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return "";
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) return "";
            if (uri.getHost() == null || uri.getUserInfo() != null) return "";
            if (uri.getPort() < -1 || uri.getPort() > 65535) return "";
            String ascii = uri.toASCIIString();
            return scheme.toLowerCase(java.util.Locale.ROOT) + ascii.substring(scheme.length());
        } catch (Exception error) {
            return "";
        }
    }
}
