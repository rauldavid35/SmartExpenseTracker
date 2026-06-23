package com.example.smartexpensetracker.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.model.NoteData
import com.example.smartexpensetracker.ui.components.VoiceInputDialog
import com.example.smartexpensetracker.ui.components.VoiceParseMode
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.VoiceParseResult
import com.example.smartexpensetracker.viewmodel.NotesViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWithFirebase(
    onMenuClick: () -> Unit,
    viewModel: NotesViewModel = viewModel(
        factory = NotesViewModel.Factory(LocalContext.current)
    )
) {
    val context   = LocalContext.current
    val notes     by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var noteText        by remember { mutableStateOf("") }
    var reminderMillis  by remember { mutableStateOf<Long?>(null) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()
    val snackbarHost    = remember { SnackbarHostState() }
    var voiceResult     by remember { mutableStateOf<VoiceParseResult?>(null) }

    fun openReminderPicker() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val cal = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        reminderMillis = cal.timeInMillis
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    val reminderFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val dateFmt     = remember { SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = onMenuClick,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryGreen)
                    ) {
                        Icon(Icons.Default.Wallet, "Menu", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(LightMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Welcome", tint = PrimaryGreen, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Welcome Back!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Let's keep track of your finances today",
                        style = MaterialTheme.typography.bodyLarge, color = TextSecondary
                    )
                }

                Spacer(Modifier.height(32.dp))

                Card(
                    modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape     = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Notes", style = MaterialTheme.typography.titleLarge)
                            if (isLoading) CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value         = noteText,
                                onValueChange = { noteText = it },
                                placeholder   = { Text("Add a note…", color = TextSecondary) },
                                modifier      = Modifier.weight(1f),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = PrimaryGreen,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            IconButton(
                                onClick  = { voiceResult = null; showVoiceDialog = true },
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LightMint)
                            ) {
                                Icon(Icons.Default.Mic, "Voice Input", tint = PrimaryGreen)
                            }

                            Spacer(Modifier.width(8.dp))

                            FloatingActionButton(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        scope.launch {
                                            viewModel.addNote(noteText, reminderMillis)
                                            noteText       = ""
                                            reminderMillis = null
                                            snackbarHost.showSnackbar(
                                                if (reminderMillis != null) "Note added with reminder" else "Note added"
                                            )
                                        }
                                    }
                                },
                                containerColor = PrimaryGreen,
                                modifier       = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add Note", tint = Color.White)
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (reminderMillis != null) {
                                Icon(
                                    Icons.Default.Alarm, null,
                                    tint     = PrimaryGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Reminder: ${reminderFmt.format(Date(reminderMillis!!))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick  = { reminderMillis = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                TextButton(
                                    onClick = ::openReminderPicker,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.AddAlarm, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Set Reminder", style = MaterialTheme.typography.bodySmall, color = PrimaryGreen)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            items(notes) { note ->
                NoteItemFirebase(
                    note     = note,
                    dateFmt  = dateFmt,
                    reminderFmt = reminderFmt,
                    onDelete = {
                        scope.launch {
                            viewModel.deleteNote(note.id)
                            snackbarHost.showSnackbar("Note deleted")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                if (notes.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No notes yet. Add your first note!",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showVoiceDialog) {
        VoiceInputDialog(
            mode        = VoiceParseMode.NOTE,
            parseResult = voiceResult,
            isParsing   = false,
            onTextReceived = { text ->
                voiceResult = if (text.isNotBlank()) VoiceParseResult.NoteResult(text) else null
            },
            onResultConfirmed = { result ->
                if (result is VoiceParseResult.NoteResult) {
                    noteText        = result.text
                    showVoiceDialog = false
                    voiceResult     = null
                }
            },
            onDismiss = { showVoiceDialog = false; voiceResult = null }
        )
    }
}


@Composable
fun NoteItemFirebase(
    note: NoteData,
    dateFmt: SimpleDateFormat,
    reminderFmt: SimpleDateFormat,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    dateFmt.format(Date(note.date)),
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                )
                if (note.remindAt != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm, null,
                            tint     = PrimaryGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            reminderFmt.format(Date(note.remindAt)),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = PrimaryGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = TextSecondary)
            }
        }
    }
}