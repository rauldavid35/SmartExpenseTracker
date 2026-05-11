package com.example.smartexpensetracker.utils

import android.content.Context
import android.util.Log
import com.example.smartexpensetracker.llm.LLamaAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

class LocalLlmEngine private constructor(
    private val appContext: Context
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadMutex = Mutex()
    private val llama = LLamaAndroid.instance()

    private var lastUseAtMs: Long = 0L
    private var idleUnloadJob: Job? = null

    fun isModelInstalled(): Boolean = modelFile().exists()
    fun modelFile(): File = File(appContext.filesDir, "models/$MODEL_FILENAME")

    /**
     * Generate a completion. Returns:
     *   - the generated string on success
     *   - null on failure (model missing, load failed, error)
     *   - null on timeout (also recorded in LlmStats)
     *
     * Records every call into LlmStats so the UI can show diagnostics and
     * decide when to surface warnings.
     */
    suspend fun complete(
        systemPrompt: String,
        userPrompt: String,
        timeoutMs: Long = 90_000L
    ): String? = withContext(Dispatchers.IO) {
        if (!isModelInstalled()) {
            Log.w(TAG, "complete() called but model not installed")
            return@withContext null
        }
        val startMs = System.currentTimeMillis()
        try {
            ensureLoaded()
            val formatted = formatQwenChat(systemPrompt, userPrompt)
            lastUseAtMs = System.currentTimeMillis()

            val result = withTimeoutOrNull(timeoutMs) {
                llama.send(formatted, formatChat = false).toList().joinToString("")
            }
            scheduleIdleUnload()

            val elapsed = System.currentTimeMillis() - startMs
            if (result == null) {
                Log.w(TAG, "Generation timed out after ${timeoutMs}ms")
                LlmStats.recordTimeout(elapsed)
            } else {
                Log.i(TAG, "Generation completed in ${elapsed}ms")
                LlmStats.recordSuccess(elapsed)
            }
            result?.trim()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            Log.e(TAG, "complete() failed: ${e.message}", e)
            LlmStats.recordError(elapsed)
            null
        }
    }

    suspend fun unload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = null
        try { llama.unload() } catch (e: Exception) { Log.w(TAG, "unload threw: ${e.message}") }
    }

    fun warmUp() {
        scope.launch {
            try { ensureLoaded() } catch (e: Exception) { Log.w(TAG, "warmUp failed: ${e.message}") }
        }
    }

    private suspend fun ensureLoaded() = loadMutex.withLock {
        if (llama.isLoaded()) return@withLock
        val file = modelFile()
        require(file.exists()) { "Model missing at ${file.absolutePath}" }
        Log.i(TAG, "Loading model from ${file.absolutePath} (${file.length() / 1_000_000} MB)")
        llama.load(file.absolutePath)
        Log.i(TAG, "Model loaded")
    }

    private fun scheduleIdleUnload() {
        idleUnloadJob?.cancel()
        idleUnloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            val sinceLast = System.currentTimeMillis() - lastUseAtMs
            if (sinceLast >= IDLE_UNLOAD_MS) {
                Log.i(TAG, "Idle for ${sinceLast}ms — unloading model")
                try { llama.unload() } catch (e: Exception) { Log.w(TAG, "idle unload threw: ${e.message}") }
            }
        }
    }

    private fun formatQwenChat(system: String, user: String): String = buildString {
        append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    companion object {
        private const val TAG = "LocalLlmEngine"

        const val MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"

        const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"

        const val MODEL_EXPECTED_BYTES = 397_800_000L

        private const val IDLE_UNLOAD_MS = 90_000L

        @Volatile private var INSTANCE: LocalLlmEngine? = null

        fun get(context: Context): LocalLlmEngine = INSTANCE ?: synchronized(this) {
            INSTANCE ?: LocalLlmEngine(context.applicationContext).also { INSTANCE = it }
        }
    }
}