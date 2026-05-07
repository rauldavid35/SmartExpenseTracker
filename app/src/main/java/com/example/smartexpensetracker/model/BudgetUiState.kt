package com.example.smartexpensetracker.model

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