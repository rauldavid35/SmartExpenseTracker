package com.example.smartexpensetracker.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.ListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: String,
    listName: String,
    onBackClick: () -> Unit,
    viewModel: ListsViewModel = viewModel()
) {
    // Load items for this specific list
    LaunchedEffect(listId) {
        viewModel.fetchListItems(listId)
    }

    val items by viewModel.currentListItems.collectAsState()
    var newItemText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = listName,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Add Item Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemText,
                onValueChange = { newItemText = it },
                placeholder = { Text("Add item (e.g., Milk)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    if (newItemText.isNotBlank()) {
                        viewModel.addListItem(listId, newItemText)
                        newItemText = ""
                    }
                },
                containerColor = PrimaryGreen,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Items List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            // UPDATED: Use .checked instead of .isChecked
                            checked = item.checked,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleListItem(listId, item.id, isChecked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                        )

                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                // UPDATED: Use .checked
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                color = if (item.checked) Color.Gray else Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = { viewModel.deleteListItem(listId, item.id) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}