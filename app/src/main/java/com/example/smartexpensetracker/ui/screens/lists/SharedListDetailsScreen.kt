package com.example.smartexpensetracker.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.viewmodel.SharedListsViewModel

@Composable
fun SharedListDetailScreen(
    listId: String,
    listName: String,
    onBackClick: () -> Unit,
    viewModel: SharedListsViewModel = viewModel()
) {
    val context = LocalContext.current
    val items by viewModel.currentItems.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    var newItemText by remember { mutableStateOf("") }

    DisposableEffect(listId) {
        viewModel.startListeningItems(listId)
        onDispose { viewModel.stopListeningItems() }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGreen)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(listName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (isOnline) "Shared list · live sync"
                    else          "Shared list · offline (read-only except checks)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (!isOnline) {
            Surface(
                color = Color(0xFFFFF3CD),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = Color(0xFF856404))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "You're offline — you can only check items",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF856404)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = {
                    Text(if (isOnline) "Add item (e.g., Milk)" else "Connect to internet to add items")
                },
                enabled = isOnline,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (newItemText.isNotBlank() && isOnline) {
                        viewModel.addItem(listId, newItemText)
                        newItemText = ""
                    }
                },
                containerColor = if (isOnline) PrimaryGreen else Color.LightGray,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No items yet — start adding above",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleItem(listId, item.id, isChecked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) TextSecondary
                                else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.deleteItem(listId, item.id) },
                            enabled = isOnline
                        ) {
                            Icon(
                                Icons.Default.Delete, "Delete",
                                tint = if (isOnline) TextSecondary else Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}