package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

// --- Data Models ---

data class MonthlyBudget(
    val totalLimit: Double = 0.0
)

data class BudgetCategorySetting(
    val name: String = "",
    val limit: Double = 0.0,
    val colorHex: String = "#4CAF50"
)

data class CategoryBudgetView(
    val name: String,
    val spent: Double,
    val budget: Double,
    val colorHex: String
)

data class BudgetUiState(
    val totalLimit: Double = 0.0,
    val totalSpent: Double = 0.0,
    val categories: List<CategoryBudgetView> = emptyList(),
    val isLoading: Boolean = true
)

// --- ViewModel ---

class BudgetViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    private val _budgetConfig = MutableStateFlow<MonthlyBudget?>(null)
    private val _categorySettings = MutableStateFlow<List<BudgetCategorySetting>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<BudgetUiState> = combine(
        _expenses,
        _budgetConfig,
        _categorySettings,
        _isLoading
    ) { expenses, budgetConfig, settings, loading ->
        
        // 1. Calculate Income and Spent per Category
        val totalIncome = expenses.filter { it.amount > 0 }.sumOf { it.amount }
        
        val spentMap = expenses
            .filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { Math.abs(it.amount) } }

        // 2. Map Settings to View Objects
        val categoryViews = settings.map { setting ->
            CategoryBudgetView(
                name = setting.name,
                spent = spentMap[setting.name] ?: 0.0,
                budget = setting.limit,
                colorHex = setting.colorHex
            )
        }

        // 3. Calculate Totals (Dynamic Limit: Base + Income)
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
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        db.collection("users").document(userId).collection("expenses")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExpenseTransaction::class.java)
                    }
                    _expenses.value = list
                }
            }

        db.collection("users").document(userId).collection("budgets").document(currentMonth)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _budgetConfig.value = snapshot.toObject(MonthlyBudget::class.java)
                }
                _isLoading.value = false
            }

        db.collection("users").document(userId).collection("budgets").document(currentMonth)
            .collection("categorySettings")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val settings = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(BudgetCategorySetting::class.java)?.copy(name = doc.id)
                    }
                    _categorySettings.value = settings
                }
            }
    }

    fun updateMonthlyLimit(newLimit: Double) {
        val userId = auth.currentUser?.uid ?: return
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        db.collection("users").document(userId).collection("budgets").document(currentMonth)
            .set(mapOf("totalLimit" to newLimit), com.google.firebase.firestore.SetOptions.merge())
    }

    fun saveCategory(name: String, limit: Double, colorHex: String): Boolean {
        val uiStateValue = uiState.value
        val usedByOthers = uiStateValue.categories.filter { it.name != name }.sumOf { it.budget }
        val available = uiStateValue.totalLimit - usedByOthers
        
        if (limit > available) return false

        val userId = auth.currentUser?.uid ?: return false
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val setting = BudgetCategorySetting(name, limit, colorHex)
        db.collection("users").document(userId).collection("budgets").document(currentMonth)
            .collection("categorySettings").document(name).set(setting)
        return true
    }

    fun deleteCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        db.collection("users").document(userId).collection("budgets").document(currentMonth)
            .collection("categorySettings").document(name).delete()
    }

    fun getUnallocatedAmount(editingCategoryName: String? = null): Double {
        val uiStateValue = uiState.value
        val usedByOthers = uiStateValue.categories
            .filter { it.name != editingCategoryName }
            .sumOf { it.budget }
        return (uiStateValue.totalLimit - usedByOthers).coerceAtLeast(0.0)
    }
}
