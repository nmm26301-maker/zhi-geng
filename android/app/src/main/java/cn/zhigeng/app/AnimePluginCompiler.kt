package cn.zhigeng.app

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.*
import org.json.JSONObject

/** Official TypeScript transpiler runs in its own bounded isolate, separate from providers. */
object AnimePluginCompiler {
    @JvmStatic fun compile(context: Context, source: String): String = compileSource(context.assets.open("typescript.js").bufferedReader().use { it.readText() },source)
    internal fun compileSource(compiler: String, source: String): String = runBlocking(Dispatchers.Default) {
        require(source.length <= 2 * 1024 * 1024) { "扩展脚本超过 2 MB" }
        withTimeout(20000) {
            val q = QuickJs.create(Dispatchers.Default)
            try {
                q.memoryLimit = 256L * 1024 * 1024
                q.maxStackSize = 2L * 1024 * 1024
                q.evaluationTimeoutMillis = 10000
                q.evaluate<Any?>(compiler, filename = "typescript.js")
                q.evaluate<String>("""
                    (function(){
                      const out=ts.transpileModule(${JSONObject.quote(source)}, {fileName:'provider.ts',reportDiagnostics:true,compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.CommonJS}});
                      const error=(out.diagnostics||[]).find(d=>d.category===ts.DiagnosticCategory.Error);
                      if(error)throw new Error(ts.flattenDiagnosticMessageText(error.messageText,' '));
                      return out.outputText;
                    })()
                """.trimIndent())
            } finally { q.close() }
        }
    }
}
