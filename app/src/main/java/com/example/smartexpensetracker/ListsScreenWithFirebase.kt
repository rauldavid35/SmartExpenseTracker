package com.example.smartexpensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.ExpenseRed
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.viewmodel.ListsViewModel
import com.example.smartexpensetracker.viewmodel.ShoppingListData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreenWithFirebase(
    onMenuClick: () -> Unit,
    onListClick: (String, String) -> Unit,
    viewModel: ListsViewModel = viewModel(),
    externalVoiceTrigger: Boolean = false,
    onExternalTriggerHandled: () -> Unit = {}
) {
    val shoppingLists by viewModel.shoppingLists.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var listToEdit by remember { mutableStateOf<ShoppingListData?>(null) }
    var showVoiceInput by remember { mutableStateOf(false) }

    // --- HANDLE SHAKE TRIGGER ---
    LaunchedEffect(externalVoiceTrigger) {
        if (externalVoiceTrigger) {
            showVoiceInput = true
            onExternalTriggerHandled()
        }
    }

    // --- LOGIC: Parse Voice Command ---
    // Format: "List [My List Name] item [Item 1] item [Item 2]"
    // Example: "Grocery Store item Eggs item Milk"
    // Result: List Name = "Grocery Store", Items = ["Eggs", "Milk"]
    fun processListVoiceCommand(text: String) {
        // Normalize text
        val lowerText = text.lowercase()

        // 1. Clean up "list" keyword if present at start
        // Be robust: Check if it starts with "list " (with space) to avoid partial matches
        val content = if (lowerText.startsWith("list ")) text.substring(5).trim() else text

        // 2. Split by "item" keyword
        // Regex splits on " item " surrounded by spaces to act as a separator
        // We use (?i) for case insensitivity
        val parts = content.split(Regex("(?i)\\s+item\\s+"))

        if (parts.isNotEmpty()) {
            // First part is always the List Name
            val listName = parts[0].trim().replaceFirstChar { it.uppercase() }

            if (listName.isNotBlank()) {
                // 3. Create List and get ID via callback
                viewModel.addList(listName) { newListId ->
                    // 4. Add remaining parts as items
                    // Loop from index 1 to end (skip index 0 which is the name)
                    for (i in 1 until parts.size) {
                        val itemText = parts[i].trim().replaceFirstChar { it.uppercase() }
                        if (itemText.isNotBlank()) {
                            viewModel.addListItem(newListId, itemText)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryGreen)
            ) {
                Icon(Icons.Default.Wallet, contentDescription = "Menu", tint = Color.White)
            }
            Text("Shopping Lists", style = MaterialTheme.typography.headlineSmall)

            Row {
                // Voice Button
                IconButton(
                    onClick = { showVoiceInput = true },
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
                        listToEdit = null
                        newListName = ""
                        showAddDialog = true
                    },
                    containerColor = PrimaryGreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        }

        // --- List of Shopping Lists ---
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shoppingLists) { list ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onListClick(list.id, list.name) }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, tint = PrimaryGreen)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                            Text("Items: ${list.itemCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }

                        // Edit Button
                        IconButton(onClick = {
                            listToEdit = list
                            newListName = list.name
                            showAddDialog = true
                        }) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary)
                        }

                        // Delete Button
                        IconButton(onClick = { viewModel.deleteList(list.id) }) {
                            Icon(Icons.Default.Delete, null, tint = ExpenseRed)
                        }
                    }
                }
            }
        }
    }

    // --- Voice Input Dialog ---
    if (showVoiceInput) {
        VoiceInputDialog(
            onTextReceived = { text ->
                processListVoiceCommand(text)
                showVoiceInput = false
            },
            onDismiss = { showVoiceInput = false }
        )
    }

    // --- Add / Rename Dialog ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                listToEdit = null
                newListName = ""
            },
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
                            if (listToEdit != null) {
                                viewModel.renameList(listToEdit!!.id, newListName)
                            } else {
                                viewModel.addList(newListName)
                            }
                            newListName = ""
                            listToEdit = null
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(if (listToEdit != null) "Update" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    listToEdit = null
                    newListName = ""
                }) { Text("Cancel") }
            }
        )
    }
}