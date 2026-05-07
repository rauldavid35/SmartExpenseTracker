package com.example.smartexpensetracker.ui.components

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.VoiceParseResult
import com.example.smartexpensetracker.utils.VoiceParser
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

enum class VoiceParseMode { NOTE, EXPENSE, SHOPPING_LIST }

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceInputDialog(
    mode: VoiceParseMode = VoiceParseMode.NOTE,
    parseResult: VoiceParseResult? = null,
    isParsing: Boolean = false,
    onTextReceived: (String) -> Unit,
    onResultConfirmed: (VoiceParseResult) -> Unit,
    onDismiss: () -> Unit
) {
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    var isListening   by remember { mutableStateOf(false) }
    var rawTranscript by remember { mutableStateOf("") }

    // ── Single launcher — registered once at the top level, never recreated ───
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            rawTranscript = text
            onTextReceived(text)
        }
        // null/blank = user dismissed the STT dialog → stay on Listening phase
    }

    LaunchedEffect(Unit) {
        if (!micPermission.status.isGranted) micPermission.launchPermissionRequest()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier  = Modifier.fillMaxWidth().padding(16.dp),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (mode) {
                        VoiceParseMode.NOTE          -> "Voice Note"
                        VoiceParseMode.EXPENSE       -> "Voice Expense"
                        VoiceParseMode.SHOPPING_LIST -> "Voice Shopping List"
                    },
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Plain when — no AnimatedContent, no key() — avoids all launcher
                // lifecycle conflicts that caused the Retry crash.
                when {
                    isParsing -> ParsingPhase(rawTranscript)

                    parseResult != null -> PreviewPhase(
                        result    = parseResult,
                        onConfirm = { onResultConfirmed(parseResult) },
                        onRetry   = {
                            isListening   = false
                            rawTranscript = ""
                            onTextReceived("")
                        },
                        onDismiss = onDismiss
                    )

                    else -> ListeningPhase(
                        isListening       = isListening,
                        hasPermission     = micPermission.status.isGranted,
                        mode              = mode,
                        onMicClick        = {
                            isListening   = true
                            rawTranscript = ""
                            // Fix #3 — English STT locale applied here
                            launcher.launch(buildSpeechIntent(mode))
                        },
                        onGrantPermission = { micPermission.launchPermissionRequest() },
                        onDismiss         = onDismiss
                    )
                }
            }
        }
    }
}

// ── ListeningPhase ────────────────────────────────────────────────────────────

@Composable
private fun ListeningPhase(
    isListening: Boolean,
    hasPermission: Boolean,
    mode: VoiceParseMode,
    onMicClick: () -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasPermission) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) PrimaryGreen.copy(alpha = 0.15f)
                        else Color(0xFFF5F5F5)
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick  = { if (!isListening) onMicClick() },
                    modifier = Modifier.size(80.dp),
                    enabled  = !isListening
                ) {
                    Icon(
                        imageVector        = Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint               = if (isListening) PrimaryGreen else Color.Gray,
                        modifier           = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text  = if (isListening) "Listening…" else "Tap to speak",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isListening) PrimaryGreen else TextSecondary
            )

            if (!isListening) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = when (mode) {
                        VoiceParseMode.EXPENSE       -> "e.g. \"Kaufland 45 groceries\""
                        VoiceParseMode.SHOPPING_LIST -> "e.g. \"Weekend list: milk, bread, eggs\""
                        VoiceParseMode.NOTE          -> "e.g. \"Call the bank tomorrow\""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            Icon(Icons.Default.MicOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Microphone permission required", color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantPermission,
                colors  = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) { Text("Grant Permission") }
        }

        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGreen) }
    }
}

// ── ParsingPhase ──────────────────────────────────────────────────────────────

@Composable
private fun ParsingPhase(rawTranscript: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = PrimaryGreen, strokeWidth = 3.dp, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Analyzing…", style = MaterialTheme.typography.bodyLarge, color = PrimaryGreen)
        if (rawTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text     = "\"$rawTranscript\"",
                style    = MaterialTheme.typography.bodySmall,
                color    = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── PreviewPhase ──────────────────────────────────────────────────────────────

@Composable
private fun PreviewPhase(
    result: VoiceParseResult,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLowConfidence = when (result) {
        is VoiceParseResult.ExpenseResult ->
            result.confidence == VoiceParseResult.ExpenseResult.Confidence.LOW
        is VoiceParseResult.ShoppingListResult ->
            result.confidence == VoiceParseResult.ShoppingListResult.Confidence.LOW
        else -> false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isLowConfidence) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF3E0))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Couldn't detect all fields — please review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF57C00)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when (result) {
            is VoiceParseResult.NoteResult ->
                PreviewField("Note", result.text, Icons.Default.Notes)

            is VoiceParseResult.ExpenseResult -> {
                PreviewField("Name",     result.name,                                     Icons.Default.ShoppingCart)
                PreviewField("Amount",   result.amount?.let { "%.2f".format(it) } ?: "—", Icons.Default.Payments)
                PreviewField("Category", result.category ?: "—",                          Icons.Default.Category)
                PreviewField("Location", result.location ?: "—",                          Icons.Default.LocationOn)
            }

            is VoiceParseResult.ShoppingListResult -> {
                PreviewField("List Name", result.listName, Icons.Default.List)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Items (${result.items.size})",
                    style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                if (result.items.isEmpty()) {
                    Text("No items detected",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                } else {
                    result.items.forEachIndexed { idx, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}.",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = PrimaryGreen,
                                modifier = Modifier.width(20.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = onRetry,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry")
            }
            OutlinedButton(
                onClick  = onDismiss,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp)
            ) { Text("Cancel") }
            Button(
                onClick  = onConfirm,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Use")
            }
        }
    }
}

// ── PreviewField ──────────────────────────────────────────────────────────────

@Composable
private fun PreviewField(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8F9FA))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

// ── buildSpeechIntent — Fix #3: English STT locale locked here ───────────────

private fun buildSpeechIntent(mode: VoiceParseMode): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Lock STT to English — uses the same constant defined in VoiceParser
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,            VoiceParser.STT_LOCALE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, VoiceParser.STT_LOCALE)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, when (mode) {
            VoiceParseMode.EXPENSE       -> "Say expense name, amount, category…"
            VoiceParseMode.SHOPPING_LIST -> "Say list name then items…"
            VoiceParseMode.NOTE          -> "Speak your note…"
        })
    }