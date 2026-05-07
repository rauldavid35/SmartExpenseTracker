package com.example.smartexpensetracker.ui.screens.home

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.components.VoiceInputDialog
import com.example.smartexpensetracker.model.NoteData
import com.example.smartexpensetracker.ui.components.VoiceParseMode
import com.example.smartexpensetracker.viewmodel.NotesViewModel
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.VoiceParseResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWithFirebase(
    onMenuClick: () -> Unit,
    viewModel: NotesViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var noteText by remember { mutableStateOf("") }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var voiceParseResult by remember { mutableStateOf<VoiceParseResult?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            item {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryGreen)
                    ) {
                        Icon(Icons.Default.Wallet, "Menu", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                // Welcome Section
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Welcome Back!", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Let's keep track of your finances today", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Notes Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Notes", style = MaterialTheme.typography.titleLarge)
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                placeholder = { Text("Add a note…", color = TextSecondary) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryGreen,
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // ── Voice button ──────────────────────────────────
                            IconButton(
                                onClick = {
                                    voiceParseResult = null
                                    showVoiceDialog = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightMint)
                            ) {
                                Icon(Icons.Default.Mic, "Voice Input", tint = PrimaryGreen)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FloatingActionButton(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        scope.launch {
                                            viewModel.addNote(noteText)
                                            noteText = ""
                                            snackbarHostState.showSnackbar("Note added")
                                        }
                                    }
                                },
                                containerColor = PrimaryGreen,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add Note", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            items(notes) { note ->
                NoteItemFirebase(
                    note = note,
                    onDelete = {
                        scope.launch {
                            viewModel.deleteNote(note.id)
                            snackbarHostState.showSnackbar("Note deleted")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                if (notes.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No notes yet. Add your first note!", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }

    // ── Voice dialog ──────────────────────────────────────────────────────────
    if (showVoiceDialog) {
        VoiceInputDialog(
            mode        = VoiceParseMode.NOTE,
            parseResult = voiceParseResult,
            isParsing   = false, // Notes never need AI parsing
            onTextReceived = { text ->
                if (text.isNotBlank()) {
                    voiceParseResult = VoiceParseResult.NoteResult(text)
                } else {
                    // text is blank → Retry was pressed → clear so Listening shows
                    voiceParseResult = null
                }
            },
            onResultConfirmed = { result ->
                if (result is VoiceParseResult.NoteResult) {
                    noteText = result.text
                    showVoiceDialog = false
                    voiceParseResult = null
                }
            },
            onDismiss = {
                showVoiceDialog = false
                voiceParseResult = null
            }
        )
    }
}

@Composable
fun NoteItemFirebase(
    note: NoteData,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(dateFormat.format(Date(note.date)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = TextSecondary)
            }
        }
    }
}