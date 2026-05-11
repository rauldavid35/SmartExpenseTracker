package com.example.smartexpensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.utils.DeviceCapabilityChecker
import com.example.smartexpensetracker.utils.LocalLlmEngine
import com.example.smartexpensetracker.utils.ModelDownloader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Settings card to manage the on-device LLM. Drop into SettingsScreen.kt.
 *
 *   OfflineAiSettingsCard()
 *
 * Behavior is gated by DeviceCapabilityChecker:
 *   - SUPPORTED → "Download" button works directly
 *   - WARNING   → "Download" button shows confirmation with reasons first
 *   - BLOCKED   → button hidden, explanation shown instead
 */
@Composable
fun OfflineAiSettingsCard() {
    val context = LocalContext.current
    val downloader = remember { ModelDownloader(context) }
    val engine = remember { LocalLlmEngine.get(context) }
    val report = remember { DeviceCapabilityChecker.check(context) }
    val scope = rememberCoroutineScope()

    var isInstalled by remember { mutableStateOf(downloader.isAlreadyDownloaded()) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("") }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showWarningConfirm by remember { mutableStateOf(false) }

    val startDownload: () -> Unit = {
        isDownloading = true
        statusText = "Starting…"
        scope.launch {
            downloader.download().collectLatest { p ->
                when (p) {
                    is ModelDownloader.Progress.Downloading -> {
                        progress = p.percent / 100f
                        statusText = "${p.percent}% (${p.bytesDownloaded / 1_000_000} MB)"
                    }
                    is ModelDownloader.Progress.Done -> {
                        isDownloading = false
                        isInstalled = true
                        statusText = "Done"
                    }
                    is ModelDownloader.Progress.Error -> {
                        isDownloading = false
                        statusText = "Error: ${p.message}"
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Offline AI", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Run voice and receipt parsing on-device. Works without internet. Uses ~986 MB of storage.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Device: ${report.tierLabel}  •  ${DeviceCapabilityChecker.formatBytes(report.totalRamBytes)} RAM  •  ${DeviceCapabilityChecker.formatBytes(report.availableStorageBytes)} free",
                style = MaterialTheme.typography.labelSmall,
                color = when (report.tier) {
                    DeviceCapabilityChecker.Tier.SUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
                    DeviceCapabilityChecker.Tier.WARNING   -> MaterialTheme.colorScheme.tertiary
                    DeviceCapabilityChecker.Tier.BLOCKED   -> MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.height(12.dp))

            when {
                isInstalled -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✓ Installed", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showConfirmDelete = true }) { Text("Delete") }
                    }
                }
                isDownloading -> {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodySmall)
                }
                report.tier == DeviceCapabilityChecker.Tier.BLOCKED -> {
                    Text(
                        "This device doesn't meet the requirements:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    report.reasons.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Voice and receipt parsing will continue to work online via Gemini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    if (report.tier == DeviceCapabilityChecker.Tier.WARNING) {
                        report.reasons.forEach {
                            Text(
                                "⚠ $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            if (report.tier == DeviceCapabilityChecker.Tier.WARNING) {
                                showWarningConfirm = true
                            } else {
                                startDownload()
                            }
                        }
                    ) {
                        Text("Download offline AI (≈986 MB)")
                    }
                }
            }
        }
    }

    if (showWarningConfirm) {
        AlertDialog(
            onDismissRequest = { showWarningConfirm = false },
            title = { Text("Continue anyway?") },
            text = {
                Column {
                    Text("This device may have trouble running the offline AI:")
                    Spacer(Modifier.height(8.dp))
                    report.reasons.forEach { Text("• $it") }
                    Spacer(Modifier.height(8.dp))
                    Text("You can always delete the model later if it doesn't work well.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarningConfirm = false
                    startDownload()
                }) { Text("Download anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showWarningConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete offline AI model?") },
            text = { Text("Voice and receipt parsing will require internet again until you redownload.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        engine.unload()
                        downloader.deleteModel()
                        isInstalled = false
                    }
                    showConfirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}