package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.BudgetCategorySetting
import com.example.smartexpensetracker.model.CategoryBudgetView
import com.example.smartexpensetracker.model.MonthlyBudget
import com.example.smartexpensetracker.model.BudgetUiState
import com.example.smartexpensetracker.repository.BudgetRepository
import com.example.smartexpensetracker.repository.ExpensesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs

class BudgetViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val budgetRepository = BudgetRepository()
    private val expensesRepository = ExpensesRepository()

    private val _expenses = MutableStateFlow<List<com.example.smartexpensetracker.model.ExpenseTransaction>>(emptyList())
    private val _budgetConfig = MutableStateFlow<MonthlyBudget?>(null)
    private val _categorySettings = MutableStateFlow<List<BudgetCategorySetting>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<BudgetUiState> = combine(
        _expenses,
        _budgetConfig,
        _categorySettings,
        _isLoading
    ) { expenses, budgetConfig, settings, loading ->
        val totalIncome = expenses.filter { it.amount > 0 }.sumOf { it.amount }
        val spentMap = expenses
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { abs(it.amount) } }

        val categoryViews = settings.map { setting ->
            CategoryBudgetView(
                name = setting.name,
                spent = spentMap[setting.name] ?: 0.0,
                budget = setting.limit,
                colorHex = setting.colorHex
            )
        }

        val totalSpent = spentMap.values.sum()
        val baseLimit = budgetConfig?.totalLimit ?: 0.0

        BudgetUiState(
            totalLimit = baseLimit + totalIncome,
            totalSpent = totalSpent,
            categories = categoryViews,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    init {
        startListening()
    }

    private fun startListening() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            expensesRepository.getExpenses(userId).collect {
                _expenses.value = it
            }
        }
        viewModelScope.launch {
            budgetRepository.getMonthlyBudget(userId).collect {
                _budgetConfig.value = it
                _isLoading.value = false
            }
        }
        viewModelScope.launch {
            budgetRepository.getCategorySettingsFlow(userId).collect {
                _categorySettings.value = it
            }
        }
    }

    fun updateMonthlyLimit(newLimit: Double) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            budgetRepository.updateMonthlyLimit(userId, newLimit)
        }
    }

    fun saveCategory(name: String, limit: Double, colorHex: String): Boolean {
        val uiStateValue = uiState.value
        val usedByOthers = uiStateValue.categories.filter { it.name != name }.sumOf { it.budget }
        val available = uiStateValue.totalLimit - usedByOthers
        if (limit > available) return false

        val userId = auth.currentUser?.uid ?: return false
        viewModelScope.launch {
            budgetRepository.saveCategory(userId, name, limit, colorHex)
        }
        return true
    }

    fun deleteCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            budgetRepository.deleteCategory(userId, name)
        }
    }

    fun getUnallocatedAmount(editingCategoryName: String? = null): Double {
        val uiStateValue = uiState.value
        val usedByOthers = uiStateValue.categories
            .filter { it.name != editingCategoryName }
            .sumOf { it.budget }
        return (uiStateValue.totalLimit - usedByOthers).coerceAtLeast(0.0)
    }
}