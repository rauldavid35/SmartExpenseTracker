package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.BudgetUiState
import com.example.smartexpensetracker.model.Dashboard
import com.example.smartexpensetracker.model.ExpenseTransaction
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates export strings (CSV / XML / JSON) fully offline.
 * Each function returns a plain String – callers write it to a temp file
 * and fire Intent.ACTION_SEND.
 */
object ExportRepository {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // ─── Expense exports ──────────────────────────────────────────────────────

    fun expensesToCsv(expenses: List<ExpenseTransaction>): String {
        val sb = StringBuilder()
        sb.appendLine("id,name,amount,category,date,location")
        expenses.forEach { e ->
            val date = dateFormatter.format(Date(e.date))
            sb.appendLine("\"${e.id}\",\"${e.name}\",${e.amount},\"${e.category}\",\"$date\",\"${e.locationName}\"")
        }
        return sb.toString()
    }

    fun expensesToJson(expenses: List<ExpenseTransaction>): String {
        val sb = StringBuilder("[\n")
        expenses.forEachIndexed { idx, e ->
            val date = dateFormatter.format(Date(e.date))
            sb.append(
                """  {
    "id": "${e.id}",
    "name": "${e.name.escapeJson()}",
    "amount": ${e.amount},
    "category": "${e.category.escapeJson()}",
    "date": "$date",
    "location": "${e.locationName.escapeJson()}"
  }"""
            )
            if (idx < expenses.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun expensesToXml(expenses: List<ExpenseTransaction>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<expenses>\n")
        expenses.forEach { e ->
            val date = dateFormatter.format(Date(e.date))
            sb.append(
                """  <expense>
    <id>${e.id}</id>
    <name>${e.name.escapeXml()}</name>
    <amount>${e.amount}</amount>
    <category>${e.category.escapeXml()}</category>
    <date>$date</date>
    <location>${e.locationName.escapeXml()}</location>
  </expense>
"""
            )
        }
        sb.append("</expenses>")
        return sb.toString()
    }

    // ─── Full financial report ────────────────────────────────────────────────

    fun fullReportToCsv(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val sb = StringBuilder()
        // Summary section
        sb.appendLine("=== BUDGET SUMMARY ===")
        sb.appendLine("Total Budget,Total Spent,Remaining")
        sb.appendLine("${budget.totalLimit},${budget.totalSpent},${budget.totalLimit - budget.totalSpent}")
        sb.appendLine()
        // Category breakdown
        sb.appendLine("=== CATEGORY BUDGETS ===")
        sb.appendLine("category,budget,spent,remaining,usage_%")
        budget.categories.forEach { c ->
            val pct = if (c.budget > 0) (c.spent / c.budget * 100).toInt() else 0
            sb.appendLine("\"${c.name}\",${c.budget},${c.spent},${c.budget - c.spent},$pct%")
        }
        sb.appendLine()
        // Transactions
        sb.appendLine("=== TRANSACTIONS ===")
        sb.append(expensesToCsv(expenses))
        return sb.toString()
    }

    fun fullReportToJson(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val categoriesJson = budget.categories.joinToString(",\n    ") { c ->
            val pct = if (c.budget > 0) (c.spent / c.budget * 100).toInt() else 0
            """{"name":"${c.name.escapeJson()}","budget":${c.budget},"spent":${c.spent},"remaining":${c.budget - c.spent},"usage_pct":$pct}"""
        }
        val expensesJson = expensesToJson(expenses)
        return """{
  "report_date": "${dateFormatter.format(Date())}",
  "budget_summary": {
    "total_budget": ${budget.totalLimit},
    "total_spent": ${budget.totalSpent},
    "remaining": ${budget.totalLimit - budget.totalSpent}
  },
  "category_budgets": [
    $categoriesJson
  ],
  "transactions": $expensesJson
}"""
    }

    fun fullReportToXml(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<financial_report>\n")
        sb.append("  <report_date>${dateFormatter.format(Date())}</report_date>\n")
        sb.append("  <budget_summary>\n")
        sb.append("    <total_budget>${budget.totalLimit}</total_budget>\n")
        sb.append("    <total_spent>${budget.totalSpent}</total_spent>\n")
        sb.append("    <remaining>${budget.totalLimit - budget.totalSpent}</remaining>\n")
        sb.append("  </budget_summary>\n")
        sb.append("  <category_budgets>\n")
        budget.categories.forEach { c ->
            val pct = if (c.budget > 0) (c.spent / c.budget * 100).toInt() else 0
            sb.append("    <category>\n")
            sb.append("      <name>${c.name.escapeXml()}</name>\n")
            sb.append("      <budget>${c.budget}</budget>\n")
            sb.append("      <spent>${c.spent}</spent>\n")
            sb.append("      <remaining>${c.budget - c.spent}</remaining>\n")
            sb.append("      <usage_pct>$pct</usage_pct>\n")
            sb.append("    </category>\n")
        }
        sb.append("  </category_budgets>\n")
        // Embed transactions XML (strip the XML declaration from inner fragment)
        sb.append("  <transactions>\n")
        expenses.forEach { e ->
            val date = dateFormatter.format(Date(e.date))
            sb.append("    <expense>\n")
            sb.append("      <id>${e.id}</id>\n")
            sb.append("      <name>${e.name.escapeXml()}</name>\n")
            sb.append("      <amount>${e.amount}</amount>\n")
            sb.append("      <category>${e.category.escapeXml()}</category>\n")
            sb.append("      <date>$date</date>\n")
            sb.append("      <location>${e.locationName.escapeXml()}</location>\n")
            sb.append("    </expense>\n")
        }
        sb.append("  </transactions>\n")
        sb.append("</financial_report>")
        return sb.toString()
    }

    // ─── Dashboard export ─────────────────────────────────────────────────────

    fun dashboardsToJson(dashboards: List<Dashboard>): String {
        val items = dashboards.joinToString(",\n  ") { d ->
            val widgets = d.widgets.joinToString("\", \"") { it.name }
            """{"id":"${d.id}","name":"${d.name.escapeJson()}","widgets":["$widgets"]}"""
        }
        return "[\n  $items\n]"
    }

    // ─── String utilities ─────────────────────────────────────────────────────

    private fun String.escapeXml() = this
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun String.escapeJson() = this
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}