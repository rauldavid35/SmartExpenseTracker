package com.example.smartexpensetracker.model

data class MonthlyBudget(
    val totalLimit: Double = 0.0
)

data class BudgetCategorySetting(
    val name: String = "",
    val limit: Double = 0.0,
    val colorHex: String = "#4CAF50"
)