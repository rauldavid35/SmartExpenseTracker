package com.example.smartexpensetracker.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.BudgetCategorySetting
import com.example.smartexpensetracker.model.BudgetUiState
import com.example.smartexpensetracker.model.CategoryBudgetView
import com.example.smartexpensetracker.model.Dashboard
import com.example.smartexpensetracker.model.ExpenseTransaction
import com.example.smartexpensetracker.model.MonthlyBudget
import com.example.smartexpensetracker.model.WidgetType
import com.example.smartexpensetracker.repository.BudgetRepository
import com.example.smartexpensetracker.repository.DashboardRepository
import com.example.smartexpensetracker.repository.ExportRepository
import com.example.smartexpensetracker.repository.ExpensesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ─── Export options ────────────────────────────────────────────────────────────

enum class ExportFormat { CSV, JSON, XML, XLSX, PDF }

enum class ExportScope(val label: String) {
    EXPENSES_ONLY("Expenses only"),
    FULL_REPORT("Full financial report"),
    DASHBOARDS("Dashboard layouts"),
    EVERYTHING("Everything (full + dashboards)")
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class BudgetViewModel(
    private val context: Context
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val budgetRepository = BudgetRepository()
    private val expensesRepository = ExpensesRepository()

    // Recreated whenever the signed-in user changes
    private var dashboardRepository = DashboardRepository(context, auth.currentUser?.uid ?: "local")

    // ── Budget state ──────────────────────────────────────────────────────────

    private val _expenses        = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    private val _budgetConfig    = MutableStateFlow<MonthlyBudget?>(null)

    private val _exportIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val exportIntent = _exportIntent.asSharedFlow()
    private val _categorySettings = MutableStateFlow<List<BudgetCategorySetting>>(emptyList())
    private val _isLoading       = MutableStateFlow(true)

    val uiState: StateFlow<BudgetUiState> = combine(
        _expenses, _budgetConfig, _categorySettings, _isLoading
    ) { expenses, budgetConfig, settings, loading ->

        val incomeTransactions = expenses.filter { it.amount > 0 }
        val totalIncome        = incomeTransactions.sumOf { it.amount }

        val categoryNames = settings.map { it.name }.toSet()
        val categoryIncomeBoostMap = incomeTransactions
            .filter { it.category in categoryNames }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val spentMap = expenses.filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { abs(it.amount) } }

        val categoryViews = settings.map { setting ->
            CategoryBudgetView(
                name     = setting.name,
                spent    = spentMap[setting.name] ?: 0.0,
                budget   = setting.limit + (categoryIncomeBoostMap[setting.name] ?: 0.0),
                colorHex = setting.colorHex
            )
        }

        BudgetUiState(
            totalLimit = (budgetConfig?.totalLimit ?: 0.0) + totalIncome,
            totalSpent = spentMap.values.sum(),
            categories = categoryViews,
            isLoading  = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    // Expose raw expenses for export / charts
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    // ── Dashboard state ───────────────────────────────────────────────────────

    // Backed by a MutableStateFlow so we can swap the source when the user changes
    private val _dashboards = MutableStateFlow<List<Dashboard>>(emptyList())
    val dashboards: StateFlow<List<Dashboard>> = _dashboards.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        val _authUid = MutableStateFlow(auth.currentUser?.uid)

        auth.addAuthStateListener { firebaseAuth ->
            _authUid.value = firebaseAuth.currentUser?.uid
        }

        viewModelScope.launch {
            _authUid.collect { uid ->
                // Clear stale state from the previous account
                _expenses.value          = emptyList()
                _budgetConfig.value      = null
                _categorySettings.value  = emptyList()
                _isLoading.value         = true
                _dashboards.value        = emptyList()

                if (uid != null) startListening(uid)
            }
        }
    }

    private fun startListening(userId: String) {
        // Recreate dashboard repository for this user so it reads the correct prefs file
        dashboardRepository = DashboardRepository(context, userId)

        viewModelScope.launch {
            try {
                dashboardRepository.dashboards.collect { _dashboards.value = it }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                expensesRepository.getExpenses(userId).collect { _expenses.value = it }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try {
                budgetRepository.getMonthlyBudget(userId).collect {
                    _budgetConfig.value = it
                    _isLoading.value    = false
                }
            } catch (_: Exception) { _isLoading.value = false }
        }
        viewModelScope.launch {
            try {
                budgetRepository.getCategorySettingsFlow(userId).collect { _categorySettings.value = it }
            } catch (_: Exception) {}
        }
    }

    // ── Budget operations ─────────────────────────────────────────────────────

    fun updateMonthlyLimit(newLimit: Double) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch { budgetRepository.updateMonthlyLimit(userId, newLimit) }
    }

    fun saveCategory(name: String, limit: Double, colorHex: String): Boolean {
        val state = uiState.value
        val usedByOthers = state.categories.filter { it.name != name }.sumOf { it.budget }
        if (limit > state.totalLimit - usedByOthers) return false
        val userId = auth.currentUser?.uid ?: return false
        viewModelScope.launch { budgetRepository.saveCategory(userId, name, limit, colorHex) }
        return true
    }

    fun deleteCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch { budgetRepository.deleteCategory(userId, name) }
    }

    fun getUnallocatedAmount(editingCategoryName: String? = null): Double {
        val state = uiState.value
        return (state.totalLimit - state.categories
            .filter { it.name != editingCategoryName }
            .sumOf { it.budget })
            .coerceAtLeast(0.0)
    }

    // ── Dashboard CRUD ────────────────────────────────────────────────────────

    fun createDashboard(name: String, widgets: List<WidgetType>) {
        dashboardRepository.createDashboard(name, widgets)
    }

    fun deleteDashboard(id: String) {
        dashboardRepository.deleteDashboard(id)
    }

    fun reorderWidgets(dashboardId: String, newOrder: List<WidgetType>) {
        val existing = dashboards.value.find { it.id == dashboardId } ?: return
        dashboardRepository.updateDashboard(existing.copy(widgets = newOrder))
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun buildExportIntent(
        context: Context,
        format: ExportFormat,
        scope: ExportScope
    ) {
        val state = uiState.value
        val exp   = expenses.value
        val dash  = dashboards.value

        viewModelScope.launch(Dispatchers.IO) {
            val fileName: String; val mime: String; val file: File

            when (format) {
                ExportFormat.XLSX -> {
                    fileName = "smart_expense_${scope.name.lowercase()}_${System.currentTimeMillis()}.xlsx"
                    mime     = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    file     = File(context.cacheDir, fileName)
                        .also { it.writeBytes(ExportRepository.buildXlsx(exp, state, dash, scope)) }
                }
                ExportFormat.PDF -> {
                    fileName = "smart_expense_${scope.name.lowercase()}_${System.currentTimeMillis()}.pdf"
                    mime     = "application/pdf"
                    file     = File(context.cacheDir, fileName)
                        .also { it.writeBytes(ExportRepository.buildPdf(exp, state, dash, scope)) }
                }
                else -> {
                    val (content, ext, m) = when (format) {
                        ExportFormat.CSV  -> Triple(buildCsv(scope, exp, state, dash),  "csv",  "text/csv")
                        ExportFormat.JSON -> Triple(buildJson(scope, exp, state, dash), "json", "application/json")
                        ExportFormat.XML  -> Triple(buildXml(scope, exp, state),        "xml",  "application/xml")
                        else -> throw IllegalStateException()
                    }
                    fileName = "smart_expense_${scope.name.lowercase()}_${System.currentTimeMillis()}.$ext"
                    mime = m
                    file = File(context.cacheDir, fileName).also { it.writeText(content) }
                }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Tracker – Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Share export via…") }

            _exportIntent.emit(intent)
        }
    }

    private fun buildCsv(
        scope: ExportScope, exp: List<ExpenseTransaction>,
        state: BudgetUiState, dash: List<Dashboard>
    ) = when (scope) {
        ExportScope.EXPENSES_ONLY -> ExportRepository.expensesToCsv(exp)
        ExportScope.FULL_REPORT   -> ExportRepository.fullReportToCsv(exp, state)
        ExportScope.DASHBOARDS    -> ExportRepository.dashboardsToCsv(dash, state)
        ExportScope.EVERYTHING    -> ExportRepository.fullReportToCsv(exp, state) + "\n\n" +
                ExportRepository.dashboardsToCsv(dash, state)
    }

    private fun buildJson(
        scope: ExportScope, exp: List<ExpenseTransaction>,
        state: BudgetUiState, dash: List<Dashboard>
    ) = when (scope) {
        ExportScope.EXPENSES_ONLY -> ExportRepository.expensesToJson(exp)
        ExportScope.FULL_REPORT   -> ExportRepository.fullReportToJson(exp, state)
        ExportScope.DASHBOARDS    -> ExportRepository.dashboardsToJson(dash, state, exp)
        ExportScope.EVERYTHING    ->
            "{\n  \"financial_report\": ${ExportRepository.fullReportToJson(exp, state)},\n" +
                    "  \"dashboards\": ${ExportRepository.dashboardsToJson(dash, state, exp)}\n}"
    }

    private fun buildXml(scope: ExportScope, exp: List<ExpenseTransaction>, state: BudgetUiState) =
        when (scope) {
            ExportScope.EXPENSES_ONLY -> ExportRepository.expensesToXml(exp)
            ExportScope.FULL_REPORT,
            ExportScope.EVERYTHING    -> ExportRepository.fullReportToXml(exp, state)
            ExportScope.DASHBOARDS    -> "<note>Dashboard XML — use JSON or PDF for richer output.</note>"
        }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BudgetViewModel(context) as T
    }
}