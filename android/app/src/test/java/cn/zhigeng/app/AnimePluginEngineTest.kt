package cn.zhigeng.app
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

class AnimePluginEngineTest {
    private val assets get() = File(System.getProperty("zhigeng.assets"))
    private fun engine(code: String) = AnimePluginEngine(assets.resolve("anix-prelude.js").readText(),assets.resolve("anime-plugin-bootstrap.js").readText(),code,JSONObject(),AnimePluginHost(AnimeNet(),false))
    @Test fun promiseDomCryptoAndSettings() {
        engine("""class Provider {
          getSettings(){return {episodeServers:['main'],supportsDub:false};}
          async search(o){await new Promise(r=>setTimeout(r,10));const d=LoadDoc('<a href="/one">Original</a>');return [{id:d('a').attr('href'),title:d('a').text(),hash:CryptoJS.SHA256('hello').toString(CryptoJS.enc.Hex)}]}
        }""").use { e ->
            assertTrue(e.call("getSettings","[]").contains("main"))
            val r=e.call("search","[{}]");assertTrue(r.contains("Original"));assertTrue(r.contains("2cf24dba5fb0a30e"))
        }
    }
    @Test fun officialTypescriptCompiler() {
        val js=AnimePluginCompiler.compileSource(assets.resolve("typescript.js").readText(),"class Provider { getSettings(): object {const answer: number = 42; return {answer};} }")
        engine(js).use { e -> assertTrue(e.call("getSettings","[]").contains("42")) }
    }
    @Test fun endlessScriptIsInterrupted() {
        val start=System.currentTimeMillis();val e=engine("class Provider{getSettings(){while(true){}}}")
        try { assertThrows(IllegalStateException::class.java) {e.call("getSettings","[]")} }
        finally {e.close()}
        assertTrue("busy JS must be interrupted",System.currentTimeMillis()-start<12000)
    }
    @Test fun noJvmOrProcessBindings() {
        engine("class Provider{getSettings(){return {java:typeof Java,process:typeof process,require:typeof require};}}").use { e ->
            assertEquals("{\"java\":\"undefined\",\"process\":\"undefined\",\"require\":\"undefined\"}",e.call("getSettings","[]"))
        }
    }
}
