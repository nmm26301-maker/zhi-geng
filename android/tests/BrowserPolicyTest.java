package cn.zhigeng.app;

public final class BrowserPolicyTest {
    public static void main(String[] args) {
        String[] accepted = {
            "https://kanju.ai/session/new?prompt=%E7%9F%A5%E6%9B%B4",
            "https://xn--4gqx1hgtfdmt.com/#/dashboard",
            "http://example.com:8080/a?q=1&next=%2Fbook#section",
            "https://duck.ai/", "https://github.com/nmm26301-maker/zhi-geng"
        };
        for (String url : accepted) {
            if (!url.equals(BrowserPolicy.webUrl(url))) throw new AssertionError("URL changed: " + url);
        }
        if (!"https://EXAMPLE.com/a".equals(BrowserPolicy.webUrl("HTTPS://EXAMPLE.com/a"))) {
            throw new AssertionError("Browser schemes must be lowercase");
        }
        String[] rejected = {null, "", "javascript:alert(1)", "intent://example.com", "file:///etc/passwd",
            "content://books/1", "https://user:secret@example.com", "https://", "https://example.com\n/path",
            "https://example.com:65536/", "https://example.com\\@evil.com"};
        for (String url : rejected) {
            if (!BrowserPolicy.webUrl(url).isEmpty()) throw new AssertionError("Unexpected URL: " + url);
        }
        System.out.println("Browser URL policy passed: preserved searches/fragments and rejected non-web schemes");
    }
}
