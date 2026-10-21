package cn.zhigeng.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookFileNamesTest {
    @Test public void titlesStayRecognizableWithoutBecomingPaths() {
        assertEquals("春日来信.epub",BookFileNames.fileName("春日来信","epub"));
        assertEquals("春日来信.pdf",BookFileNames.fileName("春日来信.PDF","pdf"));
        assertEquals("电子书.txt",BookFileNames.fileName("... ","txt"));
        String value=BookFileNames.fileName("../../书名/章节\\卷一\n","epub");
        assertFalse(value.contains("/"));assertFalse(value.contains("\\"));assertFalse(value.contains("\n"));
        assertFalse(value.startsWith("."));
    }
    @Test public void unicodeLengthDoesNotSplitEmojiAndFormatsMatch() {
        String title="📚".repeat(100);String name=BookFileNames.fileName(title,"epub");
        assertEquals(72,name.substring(0,name.length()-5).codePointCount(0,name.length()-5));
        assertEquals("application/epub+zip",BookFileNames.mimeType("epub"));
        assertEquals("application/pdf",BookFileNames.mimeType("pdf"));
        assertEquals("text/plain",BookFileNames.mimeType("txt"));
        assertTrue(BookFileNames.fileName("书","../../bad").endsWith(".bin"));
    }
}
