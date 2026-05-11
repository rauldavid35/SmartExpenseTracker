package com.example.smartexpensetracker.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Downloads the Qwen2.5-1.5B GGUF model into app private storage.
 * Resumable via HTTP Range header; emits progress events.
 *
 * Storage location: filesDir/models/qwen2.5-1.5b-instruct-q4_k_m.gguf
 * (~986 MB after completion). Survives app reinstalls only on Android backup;
 * the user will be re-prompted if the file disappears.
 */
class ModelDownloader(private val context: Context) {

    sealed class Progress {
        data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : Progress() {
            val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
        }
        data object Done : Progress()
        data class Error(val message: String) : Progress()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun targetFile(): File = File(context.filesDir, "models/${LocalLlmEngine.MODEL_FILENAME}")

    fun isAlreadyDownloaded(): Boolean {
        val f = targetFile()
        // Loose size check — full integrity would need a hash, which costs another download.
        // Within 1% of expected = good enough; users can manually delete + redownload.
        return f.exists() && f.length() >= (LocalLlmEngine.MODEL_EXPECTED_BYTES * 0.99).toLong()
    }

    /**
     * Download the model. Emits Progress.Downloading every ~1% and a final
     * Progress.Done or Progress.Error.
     *
     * If a partial file exists, the download resumes from that offset.
     */
    fun download(): Flow<Progress> = flow {
        val target = targetFile()
        target.parentFile?.mkdirs()

        if (isAlreadyDownloaded()) {
            emit(Progress.Done)
            return@flow
        }

        val partial = File(target.parentFile, target.name + ".part")
        val resumeFrom = if (partial.exists()) partial.length() else 0L

        try {
            val requestBuilder = Request.Builder()
                .url(LocalLlmEngine.MODEL_URL)
                .header("User-Agent", "SmartExpenseTracker/1.0")
            if (resumeFrom > 0) {
                requestBuilder.header("Range", "bytes=$resumeFrom-")
                Log.i(TAG, "Resuming from byte $resumeFrom")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(Progress.Error("HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(Progress.Error("Empty response body"))
                    return@flow
                }
                // Total size: from Content-Length (when full) or Content-Range (when resuming)
                val contentLength = body.contentLength()
                val total = when {
                    response.code == 206 -> {
                        // "Content-Range: bytes 0-999/123456" → take last number
                        val cr = response.header("Content-Range")
                        cr?.substringAfter('/')?.toLongOrNull() ?: (contentLength + resumeFrom)
                    }
                    contentLength > 0 -> contentLength
                    else -> LocalLlmEngine.MODEL_EXPECTED_BYTES
                }

                body.byteStream().use { input ->
                    FileOutputStream(partial, /* append = */ resumeFrom > 0).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = resumeFrom
                        var lastEmittedPercent = -1
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            output.write(buf, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100) / total).toInt()
                            if (pct != lastEmittedPercent) {
                                emit(Progress.Downloading(downloaded, total))
                                lastEmittedPercent = pct
                            }
                        }
                        output.fd.sync()
                    }
                }
            }

            // Atomic rename to final path so a half-finished file is never used
            if (!partial.renameTo(target)) {
                emit(Progress.Error("Could not rename .part to final"))
                return@flow
            }
            Log.i(TAG, "Model downloaded to ${target.absolutePath} (${target.length()} bytes)")
            emit(Progress.Done)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            emit(Progress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /** Delete the model from disk. */
    fun deleteModel(): Boolean {
        val deleted = targetFile().delete()
        File(context.filesDir, "models/${LocalLlmEngine.MODEL_FILENAME}.part").delete()
        return deleted
    }

    companion object {
        private const val TAG = "ModelDownloader"
    }
}