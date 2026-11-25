package cn.zhigeng.app;

import java.util.Locale;

/** Formats handled by the embedded Anx Reader compatibility reader. */
final class AnxReaderFormats {
    private AnxReaderFormats() {}

    static String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.toLowerCase(Locale.ROOT).trim();
        if (value.startsWith(".")) value = value.substring(1);
        return value;
    }

    static boolean supports(String raw) {
        return normalize(raw).matches("epub|mobi|azw3|fb2|txt|pdf");
    }

    static boolean usesEmbeddedAnxReader(String raw) {
        return normalize(raw).matches("mobi|azw3|fb2");
    }

    static String mimeType(String raw) {
        switch (normalize(raw)) {
            case "mobi": return "application/x-mobipocket-ebook";
            case "azw3": return "application/vnd.amazon.ebook";
            case "fb2": return "application/x-fictionbook+xml";
            case "epub": return "application/epub+zip";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain";
            default: return "application/octet-stream";
        }
    }
}
