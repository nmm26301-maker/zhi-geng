package cn.zhigeng.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class AnxReaderFormatsTest {
    @Test public void normalizesExtensionsAndRoutesAnxFormats() {
        assertTrue(AnxReaderFormats.supports(".mobi"));
        assertTrue(AnxReaderFormats.supports("AZW3"));
        assertTrue(AnxReaderFormats.supports("fb2"));
        assertTrue(AnxReaderFormats.usesEmbeddedAnxReader("MOBI"));
        assertTrue(AnxReaderFormats.usesEmbeddedAnxReader(".azw3"));
        assertFalse(AnxReaderFormats.usesEmbeddedAnxReader("epub"));
        assertFalse(AnxReaderFormats.supports("djvu"));
    }

    @Test public void exposesStableMimeTypesForDocumentProviderAndWebView() {
        assertEquals("application/x-mobipocket-ebook", AnxReaderFormats.mimeType("mobi"));
        assertEquals("application/vnd.amazon.ebook", AnxReaderFormats.mimeType("azw3"));
        assertEquals("application/x-fictionbook+xml", AnxReaderFormats.mimeType("fb2"));
        assertEquals("application/octet-stream", AnxReaderFormats.mimeType("unknown"));
    }
}
