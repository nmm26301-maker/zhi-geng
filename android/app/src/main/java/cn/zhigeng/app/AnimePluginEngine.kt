package cn.zhigeng.app

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Interruptible QuickJS isolate: no JVM reflection, filesystem, module loader or app credential bindings. */
class AnimePluginEngine internal constructor(
    private val prelude: String,
    private val bootstrap: String,
    private val payload: String,
    private val preferences: JSONObject,
    private val host: AnimePluginHost,
) : AutoCloseable {
    private var runtime: QuickJs? = null
    private var sequence = 0L
    private val timers = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    @Volatile private var active: Job? = null
    @Volatile private var closed = false

    constructor(context: Context, payload: String, preferences: JSONObject) : this(
        context.assets.open("anix-prelude.js").bufferedReader().use { it.readText() },
        context.assets.open("anime-plugin-bootstrap.js").bufferedReader().use { it.readText() },
        payload, preferences, AnimePluginHost(AnimeNet(), false)
    )

    private suspend fun initialize(): QuickJs {
        check(!closed) { "扩展已停止" }
        runtime?.let { return it }
        val q = QuickJs.create(Dispatchers.IO)
        runtime = q
        q.memoryLimit = 96L * 1024 * 1024
        q.maxStackSize = 1024L * 1024
        q.evaluationTimeoutMillis = 4000
        q.function("__host") { args ->
            val result = host.dispatch(args[0] as String, args[1] as String)
            if (host.done) timers.values.forEach { it.complete(false) }
            result
        }
        q.function("__cancelTimer") { args -> timers[(args[0] as Number).toInt()]?.complete(false); Unit }
        q.asyncFunction("__timer") { args ->
            check(timers.size < 64) { "扩展定时器过多" }
            val id = (args[0] as Number).toInt()
            val wait = (args[1] as Number).toLong().coerceIn(1, 60000)
            val signal = CompletableDeferred<Boolean>()
            timers[id] = signal
            try { withTimeoutOrNull(wait) { signal.await() } ?: true }
            finally { timers.remove(id, signal) }
        }
        q.asyncFunction("__fetch") { args ->
            withContext(Dispatchers.IO) {
                host.fetch(args[0] as String, Base64.getDecoder().decode(args[1] as String))
            }
        }
        q.evaluate<Any?>("globalThis.__preferences=" + preferences.toString() + ";globalThis.__makePrelude=" + prelude + ";")
        q.evaluate<Any?>(bootstrap)
        q.evaluate<Any?>(payload, filename = "provider.js")
        q.evaluate<Any?>("if(typeof Provider !== 'function') throw new Error('扩展没有 Provider 类');")
        return q
    }

    /** Call from a worker thread; one engine belongs to one serialized UI search session. */
    @Synchronized fun call(method: String, arguments: String): String = runBlocking(Dispatchers.Default) {
        require(method in listOf("getSettings", "search", "findEpisodes", "findEpisodeServer")) { "扩展方法不可用" }
        require(arguments.length <= 1024 * 1024) { "扩展参数过大" }
        JSONArray(arguments)
        active = currentCoroutineContext()[Job]
        try {
            withTimeout(35000) {
                host.done = false; host.result = null; host.error = null; host.callId = ++sequence
                val q = initialize()
                q.evaluate<Any?>("__engine.invoke(" + sequence + "," + JSONObject.quote(method) + "," + JSONObject.quote(arguments) + ");")
                check(host.done) { "扩展未返回结果" }
                host.error?.let { throw IllegalStateException(it) }
                val result = host.result ?: "null"
                check(result.length <= 2 * 1024 * 1024) { "扩展返回内容过大" }
                result
            }
        } catch (error: Throwable) {
            runtime?.close(); runtime = null; closed = true
            throw IllegalStateException(if (error is CancellationException) "扩展已取消或超时" else error.message ?: "扩展执行失败")
        } finally { timers.values.forEach { it.complete(false) }; timers.clear(); active = null }
    }
    fun cancel() { active?.cancel(); runtime?.interruptEvaluation() }
    @Synchronized override fun close() { closed = true; cancel(); runtime?.close(); runtime = null }
}
