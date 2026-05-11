package com.example.smartexpensetracker.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * DeviceCapabilityChecker — decides whether this phone can run Qwen 1.5B Q4
 * locally without crashing or being painfully slow.
 *
 * Gates the model download UI:
 *   - SUPPORTED  → show "Download" button
 *   - WARNING    → show "Download anyway?" with the reason ("Slow on this device")
 *   - BLOCKED    → hide the option entirely with an explanation
 *
 * Thresholds were chosen conservatively. Tune after real-device testing.
 */
object DeviceCapabilityChecker {

    /** Outcome of the capability check. Use [reasons] to explain to the user. */
    data class CapabilityReport(
        val tier: Tier,
        val totalRamBytes: Long,
        val availableStorageBytes: Long,
        val abis: List<String>,
        val sdkVersion: Int,
        val reasons: List<String>
    ) {
        val tierLabel: String get() = when (tier) {
            Tier.SUPPORTED -> "Supported"
            Tier.WARNING   -> "Limited"
            Tier.BLOCKED   -> "Not supported"
        }
    }

    enum class Tier { SUPPORTED, WARNING, BLOCKED }

    // ── Thresholds ───────────────────────────────────────────────────────────

    /** Hard floor — below this, blocking the user is the right move. */
    private const val MIN_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024            // 3 GB
    /** Comfort threshold — above this we're confident the model will run well. */
    private const val GOOD_TOTAL_RAM_BYTES = 5L * 1024 * 1024 * 1024           // 5 GB

    /** Need ~986 MB for the model + some headroom. */
    private const val MIN_FREE_STORAGE_BYTES = 1_400_000_000L                  // 1.4 GB
    private const val GOOD_FREE_STORAGE_BYTES = 2_500_000_000L                 // 2.5 GB

    /** Min Android version. API 26 = Android 8.0 (Oreo). Below that, our app
     *  doesn't run anyway based on your minSdk, but we re-check defensively. */
    private const val MIN_SDK = Build.VERSION_CODES.O

    // ── Public API ───────────────────────────────────────────────────────────

    fun check(context: Context): CapabilityReport {
        val totalRam = totalRamBytes(context)
        val freeStorage = freeAppStorageBytes(context)
        val abis = Build.SUPPORTED_ABIS.toList()
        val sdk = Build.VERSION.SDK_INT

        val reasons = mutableListOf<String>()
        var tier = Tier.SUPPORTED

        // ── Hard blocks (BLOCKED) ────────────────────────────────────────────
        if (sdk < MIN_SDK) {
            reasons += "Requires Android 8.0 or newer (yours: API $sdk)"
            tier = Tier.BLOCKED
        }
        if ("arm64-v8a" !in abis) {
            reasons += "Requires a 64-bit ARM device (yours: ${abis.joinToString()})"
            tier = Tier.BLOCKED
        }
        if (totalRam < MIN_TOTAL_RAM_BYTES) {
            reasons += "Needs at least 3 GB RAM (yours: ${formatBytes(totalRam)})"
            tier = Tier.BLOCKED
        }
        if (freeStorage < MIN_FREE_STORAGE_BYTES) {
            reasons += "Needs at least 1.4 GB free storage (yours: ${formatBytes(freeStorage)})"
            if (tier == Tier.SUPPORTED) tier = Tier.WARNING
        }

        // ── Soft warnings (WARNING) ──────────────────────────────────────────
        if (tier == Tier.SUPPORTED && totalRam < GOOD_TOTAL_RAM_BYTES) {
            reasons += "Receipt parsing may be slow on this device (${formatBytes(totalRam)} RAM)"
            tier = Tier.WARNING
        }
        if (tier == Tier.SUPPORTED && freeStorage < GOOD_FREE_STORAGE_BYTES) {
            reasons += "Storage will be tight after install (${formatBytes(freeStorage)} free)"
            tier = Tier.WARNING
        }
        // Older chipset detection: phones that shipped with Android 12 (API 31) or older
        // overwhelmingly have pre-2022 SoCs that run Qwen 1.5B at 1-3 tok/s. The
        // Android version of original-shipping is hard to detect, but FIRST_INSTALL
        // version of the app is a reasonable proxy for "currently old OS".
        if (tier == Tier.SUPPORTED && sdk <= Build.VERSION_CODES.S) {  // S = Android 12 = API 31
            reasons += "Older device — voice may take 30-90 seconds, receipts up to 3 minutes"
            tier = Tier.WARNING
        }

        return CapabilityReport(
            tier = tier,
            totalRamBytes = totalRam,
            availableStorageBytes = freeStorage,
            abis = abis,
            sdkVersion = sdk,
            reasons = reasons
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Total physical RAM, not just what's currently free. */
    private fun totalRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /**
     * Free space on the partition where filesDir lives. The model goes there.
     * Don't use Environment.getDataDirectory() — on devices with adoptable
     * external storage it may report a different volume.
     */
    private fun freeAppStorageBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            // Fall back to data partition if filesDir stat fails
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            stat.availableBytes
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.0f MB".format(bytes / 1_000_000.0)
        else                   -> "$bytes B"
    }
}