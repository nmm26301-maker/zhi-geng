package cn.zhigeng.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class FccSessionKeysTest {
    @Test public void accountAndGatewayChangesSeparatePrivateState(){
        String first=FccSessionKeys.key("history","https://one.test/v1","token-A");
        assertNotEquals(first,FccSessionKeys.key("history","https://one.test/v1","token-B"));
        assertNotEquals(first,FccSessionKeys.key("history","https://two.test/v1","token-A"));
        assertNotEquals(first,FccSessionKeys.key("draft","https://one.test/v1","token-A"));
        assertEquals(first,FccSessionKeys.key("history","https://one.test/v1","token-A"));
        assertFalse(first.contains("token-A"));assertFalse(first.contains("one.test"));
        assertNotEquals(first,FccSessionKeys.legacy("history","https://one.test/v1"));
    }
}
