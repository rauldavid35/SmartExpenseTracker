package com.example.smartexpensetracker.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks local LLM call outcomes so the UI can show diagnostics and decide
 * when to surface warnings (toasts, banners) about slow performance.
 *
 * Stub for Pile 2 — fleshed out in Pile 1 with first-timeout-per-session toast,
 * 3-consecutive-timeouts banner, and full diagnostic display.
 */
object LlmStats {

    /** Snapshot of recent generation stats. */
    data class Snapshot(
        val totalCalls: Int = 0,
        val timeoutCount: Int = 0,
        val errorCount: Int = 0,
        val successCount: Int = 0,
        val consecutiveTimeouts: Int = 0,
        val lastDurationMs: Long = 0L,
        /** Rolling average of last N successful calls, in ms. */
        val avgSuccessMs: Long = 0L,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    /** Last N success durations for rolling average. */
    private val recentSuccessDurations = ArrayDeque<Long>(WINDOW_SIZE)

    fun recordSuccess(durationMs: Long) {
        synchronized(this) {
            recentSuccessDurations.addLast(durationMs)
            while (recentSuccessDurations.size > WINDOW_SIZE) {
                recentSuccessDurations.removeFirst()
            }
            val avg = if (recentSuccessDurations.isNotEmpty())
                recentSuccessDurations.sum() / recentSuccessDurations.size
            else 0L
            val cur = _state.value
            _state.value = cur.copy(
                totalCalls = cur.totalCalls + 1,
                successCount = cur.successCount + 1,
                consecutiveTimeouts = 0,
                lastDurationMs = durationMs,
                avgSuccessMs = avg
            )
        }
    }

    fun recordTimeout(durationMs: Long) {
        synchronized(this) {
            val cur = _state.value
            _state.value = cur.copy(
                totalCalls = cur.totalCalls + 1,
                timeoutCount = cur.timeoutCount + 1,
                consecutiveTimeouts = cur.consecutiveTimeouts + 1,
                lastDurationMs = durationMs
            )
        }
    }

    fun recordError(durationMs: Long) {
        synchronized(this) {
            val cur = _state.value
            _state.value = cur.copy(
                totalCalls = cur.totalCalls + 1,
                errorCount = cur.errorCount + 1,
                lastDurationMs = durationMs
            )
        }
    }

    /** Reset for testing or after the user disables/deletes the model. */
    fun reset() {
        synchronized(this) {
            recentSuccessDurations.clear()
            _state.value = Snapshot()
        }
    }

    private const val WINDOW_SIZE = 10
}