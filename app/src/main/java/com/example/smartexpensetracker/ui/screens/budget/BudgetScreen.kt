package com.example.smartexpensetracker.ui.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.model.*
import com.example.smartexpensetracker.ui.components.CreateDashboardDialog
import com.example.smartexpensetracker.ui.components.charts.*
import com.example.smartexpensetracker.ui.theme.*
import com.example.smartexpensetracker.viewmodel.BudgetViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─── Tab definition ────────────────────────────────────────────────────────────

private enum class BudgetTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BUDGET("Budget", Icons.Default.AccountBalanceWallet),
    DASHBOARDS("Dashboards", Icons.Default.Dashboard)
}

// ─── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onMenuClick: () -> Unit,
    viewModel: BudgetViewModel = viewModel(
        factory = BudgetViewModel.Factory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val dashboards by viewModel.dashboards.collectAsState()

    var selectedTab by remember { mutableStateOf(BudgetTab.BUDGET) }
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<CategoryBudgetView?>(null) }
    var showCreateDashboard by remember { mutableStateOf(false) }

    // Which dashboard is expanded (id)
    var expandedDashboardId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
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
                    Icon(Icons.Default.Wallet, "Menu", tint = Color.White)
                }

                Text(
                    text = if (selectedTab == BudgetTab.BUDGET) "Budget" else "Dashboards",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // FAB changes per tab
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == BudgetTab.BUDGET) {
                            selectedCategory = null; showCategoryDialog = true
                        } else {
                            showCreateDashboard = true
                        }
                    },
                    containerColor = PrimaryGreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, "Add", tint = Color.White)
                }
            }

            // ── Tabs ─────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.White,
                contentColor = PrimaryGreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                BudgetTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, style = MaterialTheme.typography.titleSmall) },
                        icon = { Icon(tab.icon, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            when (selectedTab) {
                BudgetTab.BUDGET -> BudgetTabContent(
                    uiState = uiState,
                    onEditBudget = { showEditBudgetDialog = true },
                    onAddCategory = { selectedCategory = null; showCategoryDialog = true },
                    onCategoryClick = { cat -> selectedCategory = cat; showCategoryDialog = true }
                )
                BudgetTab.DASHBOARDS -> DashboardTabContent(
                    dashboards = dashboards,
                    uiState = uiState,
                    expenses = expenses,
                    expandedId = expandedDashboardId,
                    onExpand = { id -> expandedDashboardId = if (expandedDashboardId == id) null else id },
                    onDelete = { id -> viewModel.deleteDashboard(id) },
                    onAdd = { showCreateDashboard = true }
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showEditBudgetDialog) {
        EditBudgetDialog(
            currentLimit = uiState.totalLimit,
            onDismiss = { showEditBudgetDialog = false },
            onSave = { viewModel.updateMonthlyLimit(it); showEditBudgetDialog = false }
        )
    }

    if (showCategoryDialog) {
        CategoryDialog(
            category = selectedCategory,
            unallocatedAmount = viewModel.getUnallocatedAmount(selectedCategory?.name),
            onDismiss = { showCategoryDialog = false },
            onSave = { name, limit, color ->
                val ok = viewModel.saveCategory(name, limit, color)
                if (ok) showCategoryDialog = false
                ok
            },
            onDelete = { name -> viewModel.deleteCategory(name); showCategoryDialog = false }
        )
    }

    if (showCreateDashboard) {
        CreateDashboardDialog(
            onDismiss = { showCreateDashboard = false },
            onCreate = { name, widgets ->
                viewModel.createDashboard(name, widgets)
                showCreateDashboard = false
            }
        )
    }
}

// ─── Budget tab ────────────────────────────────────────────────────────────────

@Composable
private fun BudgetTabContent(
    uiState: BudgetUiState,
    onEditBudget: () -> Unit,
    onAddCategory: () -> Unit,
    onCategoryClick: (CategoryBudgetView) -> Unit
) {
    val totalBudget = uiState.totalLimit
    val totalSpent = uiState.totalSpent
    val remainingPercent = if (totalBudget > 0)
        (((totalBudget - totalSpent) / totalBudget) * 100).toInt().coerceIn(0, 100) else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Overview card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Monthly Overview", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        IconButton(onClick = onEditBudget) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$${String.format("%.0f", totalSpent)}",
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White
                        )
                        Text(
                            " / $${String.format("%.0f", totalBudget)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("$remainingPercent% remaining", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }

        // ── Categories header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = onAddCategory) {
                    Icon(Icons.Default.Add, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", color = PrimaryGreen)
                }
            }
        }

        // ── Categories list
        if (uiState.categories.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No categories yet", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("Tap + to add a spending category", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            items(uiState.categories) { cat ->
                CategoryBudgetItem(category = cat, onClick = { onCategoryClick(cat) })
            }
        }
    }
}

// ─── Dashboards tab ────────────────────────────────────────────────────────────

@Composable
private fun DashboardTabContent(
    dashboards: List<Dashboard>,
    uiState: BudgetUiState,
    expenses: List<ExpenseTransaction>,
    expandedId: String?,
    onExpand: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit
) {
    if (dashboards.isEmpty()) {
        // Empty state
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Dashboard, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("No dashboards yet", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Text("Tap + to build your first dashboard", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Dashboard")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(dashboards, key = { it.id }) { dashboard ->
            DashboardCard(
                dashboard = dashboard,
                expanded = expandedId == dashboard.id,
                uiState = uiState,
                expenses = expenses,
                onToggle = { onExpand(dashboard.id) },
                onDelete = { onDelete(dashboard.id) }
            )
        }
    }
}

// ─── Single dashboard card ─────────────────────────────────────────────────────

@Composable
private fun DashboardCard(
    dashboard: Dashboard,
    expanded: Boolean,
    uiState: BudgetUiState,
    expenses: List<ExpenseTransaction>,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Dashboard, null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(dashboard.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "${dashboard.widgets.size} widget${if (dashboard.widgets.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                // Widget preview chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(dashboard.widgets.take(3)) { w ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(w.displayName.split(" ").first(), style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = LightMint
                            )
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = Color.Gray
                )
            }

            // Expanded widget area
            if (expanded) {
                HorizontalDivider(color = Color(0xFFF3F4F6))
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    dashboard.widgets.forEach { widgetType ->
                        DashboardWidget(
                            type = widgetType,
                            uiState = uiState,
                            expenses = expenses
                        )
                    }
                    // Delete dashboard
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Dashboard")
                    }
                }
            }
        }
    }
}

// ─── Individual widget renderer ────────────────────────────────────────────────

@Composable
private fun DashboardWidget(
    type: WidgetType,
    uiState: BudgetUiState,
    expenses: List<ExpenseTransaction>
) {
    when (type) {
        WidgetType.PIE_CHART -> {
            val slices = uiState.categories.map { cat ->
                PieSlice(
                    label = cat.name,
                    value = cat.spent.toFloat(),
                    color = runCatching {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    }.getOrDefault(PrimaryGreen)
                )
            }
            PieChartCard(slices = slices)
        }

        WidgetType.BAR_CHART -> {
            val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
            val grouped = expenses
                .filter { it.amount < 0 }
                .groupBy { sdf.format(Date(it.date)) }
                .map { (k, v) -> BarEntry(k, v.sumOf { abs(it.amount) }.toFloat()) }
                .takeLast(14)
            BarChartCard(entries = grouped, title = "Daily Spending (last 14 days)")
        }

        WidgetType.BUDGET_VS_ACTUAL -> {
            BudgetVsActualCard(categories = uiState.categories)
        }

        WidgetType.SUMMARY_STATS -> {
            val negativeExpenses = expenses.filter { it.amount < 0 }
            val daysActive = expenses
                .map { SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(it.date)) }
                .distinct().size.coerceAtLeast(1)
            val biggestCat = uiState.categories.maxByOrNull { it.spent }?.name ?: "—"

            SummaryStatsCard(
                stats = SummaryStats(
                    totalSpent = uiState.totalSpent,
                    totalBudget = uiState.totalLimit,
                    avgPerDay = uiState.totalSpent / daysActive,
                    biggestExpense = negativeExpenses.minOfOrNull { it.amount }?.let { abs(it) } ?: 0.0,
                    biggestCategory = biggestCat,
                    transactionCount = negativeExpenses.size
                )
            )
        }

        WidgetType.TOP_EXPENSES -> {
            TopExpensesCard(expenses = expenses)
        }

        WidgetType.MONTHLY_TREND -> {
            val sdf = SimpleDateFormat("dd", Locale.getDefault())
            val grouped = expenses
                .filter { it.amount < 0 }
                .groupBy { sdf.format(Date(it.date)) }
                .map { (k, v) -> BarEntry(k, v.sumOf { abs(it.amount) }.toFloat()) }
                .sortedBy { it.label.toIntOrNull() ?: 0 }
            MonthlyTrendCard(entries = grouped)
        }
    }
}

// ─── Reused dialogs (unchanged from original) ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBudgetDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var limitText by remember { mutableStateOf(currentLimit.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Monthly Budget") },
        text = {
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                label = { Text("Total Budget ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                limitText.toDoubleOrNull()?.let { onSave(it) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CategoryDialog(
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
                        "No budget left to allocate. Increase your total budget first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(), enabled = category == null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it; showError = false },
                    label = { Text("Limit (Available: $${String.format("%.2f", unallocatedAmount)})") },
                    isError = isOverBudget || showError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isOverBudget) {
                    Text(
                        "Exceeds unallocated amount ($${String.format("%.2f", unallocatedAmount)})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                if (showError) {
                    Text("Failed to save. Please check the amount.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(16.dp))
                Text("Select Color", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("#FF9800", "#2196F3", "#E91E63", "#9C27B0", "#4CAF50", "#795548").forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(android.graphics.Color.parseColor(color)))
                                .clickable { colorHex = color }
                                .let { if (colorHex == color) it.background(Color.Black.copy(alpha = 0.2f)) else it }
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
                    TextButton(
                        onClick = { onDelete(category.name) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun CategoryBudgetItem(category: CategoryBudgetView, onClick: () -> Unit) {
    val categoryColor = remember(category.colorHex) {
        try { Color(android.graphics.Color.parseColor(category.colorHex)) } catch (_: Exception) { Color.Gray }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(category.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$${String.format("%.2f", category.spent)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (category.spent > category.budget) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Text("of $${String.format("%.2f", category.budget)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Monthly", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            val progress = if (category.budget > 0) (category.spent / category.budget).toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (progress > 1f) Color.Red else categoryColor,
                trackColor = Color(0xFFF3F4F6)
            )
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt()}% used", style = MaterialTheme.typography.bodySmall, color = if (progress > 1f) Color.Red else TextSecondary)
        }
    }
}