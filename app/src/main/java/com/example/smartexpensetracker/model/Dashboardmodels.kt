package com.example.smartexpensetracker.model

import androidx.compose.ui.graphics.Color

// ─── Dashboard Widget Types ────────────────────────────────────────────────────

enum class WidgetType(val displayName: String, val description: String) {
    PIE_CHART("Spending by Category", "Pie chart breakdown of categories"),
    BAR_CHART("Daily / Weekly Spending", "Bar chart of spending over time"),
    BUDGET_VS_ACTUAL("Budget vs Actual", "Comparison bars per category"),
    SUMMARY_STATS("Summary Statistics", "Total spent, saved, avg per day"),
    TOP_EXPENSES("Top Expenses", "Highest individual transactions"),
    MONTHLY_TREND("Monthly Trend", "Line of spending day by day")
}

// ─── Persisted Dashboard Definition ───────────────────────────────────────────

data class Dashboard(
    val id: String,
    val name: String,
    val widgets: List<WidgetType>   // ordered list of widgets on this dashboard
)

// ─── Runtime chart data helpers ────────────────────────────────────────────────

data class PieSlice(val label: String, val value: Float, val color: Color)

data class BarEntry(val label: String, val value: Float)

data class SummaryStats(
    val totalSpent: Double,
    val totalBudget: Double,
    val avgPerDay: Double,
    val biggestExpense: Double,
    val biggestCategory: String,
    val transactionCount: Int
)