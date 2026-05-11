package com.example.smartexpensetracker.llm

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LLamaAndroid private constructor() {

    private val tag = "LLamaAndroid"

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") { it.run() }
    }.asCoroutineDispatcher()

    /** Hard cap on generated tokens. Voice JSON: ~50-80 tokens typical. */
    private val nlen: Int = 96

    private sealed interface State {
        data object Idle : State
        data class Loaded(val model: Long, val context: Long, val batch: Long, val sampler: Long) : State
    }

    private var threadLocalState: State = State.Idle

    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            when (threadLocalState) {
                is State.Idle -> {
                    Log.i(tag, "load(): calling load_model()")
                    val startLoad = System.nanoTime()
                    val model = load_model(pathToModel)
                    if (model == 0L) throw IllegalStateException("load_model() failed")
                    val loadMs = (System.nanoTime() - startLoad) / 1_000_000
                    Log.i(tag, "load(): load_model() returned in ${loadMs}ms")

                    val context = new_context(model)
                    if (context == 0L) throw IllegalStateException("new_context() failed")

                    // batch must hold the full prompt; n_ctx=512 so 512 is the cap
                    val batch = new_batch(512, 0, 1)
                    if (batch == 0L) throw IllegalStateException("new_batch() failed")

                    val sampler = new_sampler()
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    Log.i(tag, "load(): all initialized for $pathToModel")
                    threadLocalState = State.Loaded(model, context, batch, sampler)
                }
                is State.Loaded -> Log.w(tag, "load() called but model already loaded")
            }
        }
    }

    fun send(prompt: String, formatChat: Boolean = false): Flow<String> = flow {
        when (val state = threadLocalState) {
            is State.Loaded -> {
                val startNs = System.nanoTime()
                var tokenCount = 0
                Log.i(tag, "send() starting prompt of ${prompt.length} chars")
                val ncur = IntVar(completion_init(state.context, state.batch, prompt, formatChat, nlen))
                Log.i(tag, "completion_init returned, starting generation loop")
                while (ncur.value <= nlen) {
                    val str = completion_loop(state.context, state.batch, state.sampler, nlen, ncur)
                        ?: break
                    tokenCount++
                    if (tokenCount % 5 == 0) {
                        val elapsed = (System.nanoTime() - startNs) / 1_000_000
                        Log.i(tag, "Progress: $tokenCount tokens in ${elapsed}ms")
                    }
                    emit(str)
                }
                kv_cache_clear(state.context)
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                val tps = if (elapsedMs > 0) (tokenCount * 1000.0) / elapsedMs else 0.0
                Log.i(tag, "Generated $tokenCount tokens in ${elapsedMs}ms (${"%.1f".format(tps)} tok/s)")
            }
            else -> Log.w(tag, "send() called but model not loaded")
        }
    }.flowOn(runLoop)

    suspend fun unload() {
        withContext(runLoop) {
            when (val state = threadLocalState) {
                is State.Loaded -> {
                    free_context(state.context)
                    free_model(state.model)
                    free_batch(state.batch)
                    free_sampler(state.sampler)
                    threadLocalState = State.Idle
                    Log.i(tag, "Unloaded")
                }
                is State.Idle -> {}
            }
        }
    }

    fun isLoaded(): Boolean = threadLocalState is State.Loaded

    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun backend_free()
    private external fun new_batch(nTokens: Int, embd: Int, nSeqMax: Int): Long
    private external fun free_batch(batch: Long)
    private external fun new_sampler(): Long
    private external fun free_sampler(sampler: Long)
    private external fun bench_model(context: Long, model: Long, batch: Long,
                                     pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun system_info(): String
    private external fun completion_init(context: Long, batch: Long, text: String,
                                         formatChat: Boolean, nLen: Int): Int
    private external fun completion_loop(context: Long, batch: Long, sampler: Long,
                                         nLen: Int, ncur: IntVar): String?
    private external fun kv_cache_clear(context: Long)

    companion object {
        init { System.loadLibrary("llama-android") }

        @Volatile private var instance: LLamaAndroid? = null

        fun instance(): LLamaAndroid = instance ?: synchronized(this) {
            instance ?: LLamaAndroid().also {
                it.log_to_android()
                it.backend_init(false)
                Log.d("LLamaAndroid", "system info: ${it.system_info()}")
                instance = it
            }
        }
    }
}

class IntVar(value: Int) {
    @Volatile var value: Int = value; private set
    fun inc() { value += 1 }
    fun set(v: Int) { value = v }
}