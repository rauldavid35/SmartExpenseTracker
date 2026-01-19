package com.example.smartexpensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.*
import com.example.smartexpensetracker.viewmodel.BudgetViewModel
import com.example.smartexpensetracker.viewmodel.CategoryBudgetView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onMenuClick: () -> Unit,
    viewModel: BudgetViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<CategoryBudgetView?>(null) }

    val totalBudget = uiState.totalLimit
    val totalSpent = uiState.totalSpent
    val remainingPercent = if (totalBudget > 0) {
        (((totalBudget - totalSpent) / totalBudget) * 100).toInt().coerceIn(0, 100)
    } else 0

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wallet,
                            contentDescription = "Menu",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Budget",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    FloatingActionButton(
                        onClick = {
                            selectedCategory = null
                            showCategoryDialog = true
                        },
                        containerColor = PrimaryGreen,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Category",
                            tint = Color.White
                        )
                    }
                }
            }

            item {
                // Monthly Overview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Monthly Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = { showEditBudgetDialog = true }) {
                                Icon(Icons.Default.Edit, "Edit Budget", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "$${String.format("%.0f", totalSpent)}",
                                style = MaterialTheme.typography.displayMedium,
                                color = Color.White
                            )
                            Text(
                                text = " / $${String.format("%.0f", totalBudget)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "$remainingPercent% remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            if (uiState.categories.isEmpty() && !uiState.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Add the budget amount and split it into categories",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(24.dp))

                    // Categories Header
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (uiState.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryGreen)
                        }
                    }
                }

                items(uiState.categories) { category ->
                    CategoryBudgetItem(
                        category = category,
                        onClick = {
                            selectedCategory = category
                            showCategoryDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Dialogs
        if (showEditBudgetDialog) {
            var budgetInput by remember { mutableStateOf(if(totalBudget > 0) totalBudget.toString() else "") }
            AlertDialog(
                onDismissRequest = { showEditBudgetDialog = false },
                title = { Text("Edit Monthly Budget") },
                text = {
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("Monthly Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        budgetInput.toDoubleOrNull()?.let { viewModel.updateMonthlyLimit(it) }
                        showEditBudgetDialog = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showEditBudgetDialog = false }) { Text("Cancel") } }
            )
        }

        if (showCategoryDialog) {
            val unallocated = viewModel.getUnallocatedAmount(selectedCategory?.name)
            CategoryEditDialog(
                category = selectedCategory,
                unallocatedAmount = unallocated,
                onDismiss = { showCategoryDialog = false },
                onSave = { name, limit, color ->
                    val success = viewModel.saveCategory(name, limit, color)
                    if (success) showCategoryDialog = false
                    success
                },
                onDelete = { name ->
                    viewModel.deleteCategory(name)
                    showCategoryDialog = false
                }
            )
        }
    }
}

@Composable
fun CategoryEditDialog(
    category: CategoryBudgetView?,
    unallocatedAmount: Double,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Boolean,
    onDelete: (String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var limit by remember { mutableStateOf(category?.budget?.toString() ?: "") }
    var colorHex by remember { mutableStateOf(category?.colorHex ?: "#4CAF50") }
    var showError by remember { mutableStateOf(false) }

    val currentLimit = limit.toDoubleOrNull() ?: 0.0
    val isOverBudget = currentLimit > unallocatedAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Add Category" else "Edit Category") },
        text = {
            Column {
                if (unallocatedAmount <= 0 && category == null) {
                    Text(
                        text = "No budget left to allocate. Increase your total budget first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = category == null
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = limit,
                    onValueChange = { 
                        limit = it
                        showError = false
                    },
                    label = { Text("Limit (Available: $${String.format("%.2f", unallocatedAmount)})") },
                    isError = isOverBudget || showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isOverBudget) {
                    Text(
                        text = "Exceeds unallocated amount ($${String.format("%.2f", unallocatedAmount)})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                if (showError) {
                    Text(
                        text = "Failed to save. Please check the amount.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Color", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("#FF9800", "#2196F3", "#E91E63", "#9C27B0", "#4CAF50", "#795548").forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(android.graphics.Color.parseColor(color)))
                                .clickable { colorHex = color }
                                .let { 
                                    if (colorHex == color) it.background(Color.Black.copy(alpha = 0.2f)) else it
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limitVal = limit.toDoubleOrNull()
                    if (limitVal != null && !isOverBudget) {
                        val success = onSave(name, limitVal, colorHex)
                        if (!success) showError = true
                    }
                },
                enabled = name.isNotBlank() && limit.isNotBlank() && !isOverBudget
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (category != null) {
                    TextButton(onClick = { onDelete(category.name) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun CategoryBudgetItem(
    category: CategoryBudgetView,
    onClick: () -> Unit
) {
    val categoryColor = remember(category.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (e: Exception) {
            Color.Gray
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${String.format("%.2f", category.spent)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (category.spent > category.budget) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of $${String.format("%.2f", category.budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Monthly",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val progress = if (category.budget > 0) (category.spent / category.budget).toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (progress > 1f) Color.Red else categoryColor,
                trackColor = Color(0xFFF3F4F6)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val usagePercent = (progress * 100).toInt()
            Text(
                text = "$usagePercent% used",
                style = MaterialTheme.typography.bodySmall,
                color = if (progress > 1f) Color.Red else TextSecondary
            )
        }
    }
}
