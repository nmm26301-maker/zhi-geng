package cn.zhigeng.app;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class AnimeTrackerAuthTest {
    private JSONObject pending(String service)throws Exception{return AnimeTrackerAuth.begin(service,service.equals("anilist")?"12345":"abcdef1234567890",10000);}
    @Test public void validAniListCallbackAndOwnClient()throws Exception{JSONObject p=pending("anilist");String url=AnimeTrackerAuth.authorize(p);assertTrue(url.contains("client_id=12345"));assertTrue(url.contains("response_type=token"));JSONObject a=AnimeTrackerAuth.answer(p,"zhigeng://oauth/anilist#access_token=test-token&state="+p.getString("state"),10001);assertEquals("test-token",a.getString("access_token"));}
    @Test public void pkceBoundToAttempt()throws Exception{JSONObject a=pending("mal"),b=pending("mal");assertNotEquals(a.getString("verifier"),b.getString("verifier"));String url=AnimeTrackerAuth.authorize(a);assertTrue(url.contains("code_challenge="+a.getString("verifier")));assertTrue(url.contains("code_challenge_method=plain"));JSONObject answer=AnimeTrackerAuth.answer(a,"zhigeng://oauth/mal?code=code123&state="+a.getString("state"),11000);assertEquals(a.getString("verifier"),AnimeTrackerAuth.exchange(a,answer).getString("code_verifier"));}
    @Test public void wrongStateRejected()throws Exception{JSONObject p=pending("anilist");assertThrows(Exception.class,()->AnimeTrackerAuth.answer(p,"zhigeng://oauth/anilist#access_token=x&state=wrong",11000));}
    @Test public void expiredAndDifferentProviderRejected()throws Exception{JSONObject p=pending("anilist");String uri="zhigeng://oauth/anilist#access_token=x&state="+p.getString("state");assertThrows(Exception.class,()->AnimeTrackerAuth.answer(p,uri,9999999));assertThrows(Exception.class,()->AnimeTrackerAuth.answer(p,uri.replace("/anilist","/mal"),11000));}
    @Test public void duplicateAndLookalikeCallbackRejected()throws Exception{JSONObject p=pending("anilist");String uri="zhigeng://oauth/anilist#access_token=x&state="+p.getString("state");assertThrows(Exception.class,()->AnimeTrackerAuth.answer(p,uri+"&access_token=y",11000));assertThrows(Exception.class,()->AnimeTrackerAuth.answer(p,uri.replace("oauth/","oauth.evil/"),11000));}
    @Test public void formProgressAndTrackerStates()throws Exception{assertEquals("progress=12",AnimeNet.form(new JSONObject().put("progress",12)));assertEquals("completed",AnimeTrackerClient.remoteState("已看完",true));assertEquals("在看",AnimeTrackerClient.state("REPEATING",false));assertThrows(Exception.class,()->AnimeTrackerClient.state("unknown",true));}
}
