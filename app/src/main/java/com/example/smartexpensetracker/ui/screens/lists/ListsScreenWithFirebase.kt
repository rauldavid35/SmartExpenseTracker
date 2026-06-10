package com.example.smartexpensetracker.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.smartexpensetracker.ui.components.VoiceParseMode
import com.example.smartexpensetracker.ui.theme.ExpenseRed
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.VoiceParser
import com.example.smartexpensetracker.utils.VoiceParseResult
import com.example.smartexpensetracker.viewmodel.ListsViewModel
import com.example.smartexpensetracker.model.ShoppingListData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreenWithFirebase(
    onMenuClick: () -> Unit,
    onListClick: (String, String) -> Unit,
    onSharedListsClick: () -> Unit = {},
    viewModel: ListsViewModel = viewModel(),
    externalVoiceTrigger: Boolean = false,
    onExternalTriggerHandled: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val shoppingLists by viewModel.shoppingLists.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var listToEdit by remember { mutableStateOf<ShoppingListData?>(null) }
    var showVoiceInput by remember { mutableStateOf(false) }

    // ── Voice state ───────────────────────────────────────────────────────────
    val voiceParser = remember { VoiceParser(apiKey = com.example.smartexpensetracker.BuildConfig.GEMINI_API_KEY) }
    var voiceParseResult by remember { mutableStateOf<VoiceParseResult?>(null) }
    var isVoiceParsing   by remember { mutableStateOf(false) }

    // External trigger (shake / volume)
    LaunchedEffect(externalVoiceTrigger) {
        if (externalVoiceTrigger) {
            voiceParseResult = null
            isVoiceParsing   = false
            showVoiceInput   = true
            onExternalTriggerHandled()
        }
    }

    // ── Parse voice transcript for a shopping list ────────────────────────────
    fun processListVoiceCommand(transcript: String) {
        if (transcript.isBlank()) {
            voiceParseResult = null
            isVoiceParsing   = false
            return
        }
        isVoiceParsing   = true
        voiceParseResult = null
        scope.launch {
            val result = voiceParser.parseShoppingList(transcript)
            voiceParseResult = result
            isVoiceParsing   = false
        }
    }

    // ── Confirm handler: create list + add all items atomically ──────────────
    fun confirmShoppingListResult(result: VoiceParseResult.ShoppingListResult) {
        viewModel.addList(result.listName) { newListId ->
            result.items.forEach { item ->
                viewModel.addListItem(newListId, item)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Top Bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(PrimaryGreen)
            ) {
                Icon(Icons.Default.Wallet, "Menu", tint = Color.White)
            }

            Text("Shopping Lists", style = MaterialTheme.typography.headlineSmall)

            Row {
                // Voice button
                IconButton(
                    onClick = {
                        voiceParseResult = null
                        isVoiceParsing = false
                        showVoiceInput = true
                    },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LightMint)
                ) {
                    Icon(Icons.Default.Mic, "Voice Input", tint = PrimaryGreen)
                }
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { listToEdit = null; newListName = ""; showAddDialog = true },
                    containerColor = PrimaryGreen, modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, "Add", tint = Color.White)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = LightMint),
            shape  = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onSharedListsClick() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Group, null, tint = PrimaryGreen)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shared lists", style = MaterialTheme.typography.titleMedium)
                    Text("Lists shared with you or by you", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(Icons.Default.ChevronRight, null, tint = PrimaryGreen)
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── List of Shopping Lists ────────────────────────────────────────────
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shoppingLists) { list ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onListClick(list.id, list.name) }
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LightMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = PrimaryGreen)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                            Text("Items: ${list.itemCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        IconButton(onClick = { listToEdit = list; newListName = list.name; showAddDialog = true }) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.deleteList(list.id) }) {
                            Icon(Icons.Default.Delete, null, tint = ExpenseRed)
                        }
                    }
                }
            }
        }
    }

    // ── Voice Input Dialog ────────────────────────────────────────────────────
    if (showVoiceInput) {
        VoiceInputDialog(
            mode         = VoiceParseMode.SHOPPING_LIST,
            parseResult  = voiceParseResult,
            isParsing    = isVoiceParsing,
            onTextReceived = { text ->
                processListVoiceCommand(text)
            },
            onResultConfirmed = { result ->
                if (result is VoiceParseResult.ShoppingListResult) {
                    confirmShoppingListResult(result)
                    showVoiceInput   = false
                    voiceParseResult = null
                    isVoiceParsing   = false
                }
            },
            onDismiss = {
                showVoiceInput   = false
                voiceParseResult = null
                isVoiceParsing   = false
            }
        )
    }

    // ── Manual Add / Rename Dialog ────────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; listToEdit = null; newListName = "" },
            title = { Text(if (listToEdit != null) "Rename List" else "New Shopping List") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text("List Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            if (listToEdit != null) viewModel.renameList(listToEdit!!.id, newListName)
                            else viewModel.addList(newListName)
                            newListName = ""; listToEdit = null; showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(if (listToEdit != null) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; listToEdit = null; newListName = "" }) { Text("Cancel") }
            }
        )
    }
}