package com.example.smartexpensetracker.repository

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.smartexpensetracker.model.BudgetUiState
import com.example.smartexpensetracker.model.CategoryBudgetView
import com.example.smartexpensetracker.model.Dashboard
import com.example.smartexpensetracker.model.ExpenseTransaction
import com.example.smartexpensetracker.model.WidgetType
import com.example.smartexpensetracker.viewmodel.ExportScope
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.PresetColor
import org.apache.poi.xddf.usermodel.XDDFColor
import org.apache.poi.xddf.usermodel.XDDFShapeProperties
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties
import org.apache.poi.xddf.usermodel.chart.AxisPosition
import org.apache.poi.xddf.usermodel.chart.BarDirection
import org.apache.poi.xddf.usermodel.chart.ChartTypes
import org.apache.poi.xddf.usermodel.chart.LegendPosition
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData
import org.apache.poi.xddf.usermodel.chart.XDDFChartData
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFChart
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
object ExportRepository {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dayFmt  = SimpleDateFormat("yyyy-MM-dd",       Locale.getDefault())
    private val monthFmt = SimpleDateFormat("MMM dd",          Locale.getDefault())

    private val palette = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336",
        "#00BCD4", "#FFC107", "#795548", "#607D8B", "#E91E63"
    )


    fun expensesToCsv(expenses: List<ExpenseTransaction>): String {
        val sb = StringBuilder("name,amount,type,category,date,location\n")
        expenses.sortedByDescending { it.date }.forEach { e ->
            sb.appendLine("\"${e.name.csv()}\",${e.amount},${if (e.amount < 0) "Expense" else "Income"}," +
                    "\"${e.category.csv()}\",\"${dateFmt.format(Date(e.date))}\",\"${e.locationName.csv()}\"")
        }
        return sb.toString()
    }

    fun fullReportToCsv(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val sb = StringBuilder()
        sb.appendLine("=== BUDGET SUMMARY ===")
        sb.appendLine("Total Budget,Total Spent,Remaining")
        sb.appendLine("${budget.totalLimit},${budget.totalSpent},${budget.totalLimit - budget.totalSpent}")
        sb.appendLine()
        val catNames = budget.categories.map { it.name }.toSet()
        budget.categories.forEach { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
            sb.appendLine("=== CATEGORY: ${cat.name} | Limit: \$${fmt2(cat.budget)} | Spent: \$${fmt2(cat.spent)} | $pct% ===")
            sb.appendLine("name,amount,date,location")
            expenses.filter { it.category == cat.name && it.amount < 0 }
                .sortedByDescending { it.date }
                .forEach { e -> sb.appendLine("\"${e.name.csv()}\",${-e.amount},\"${dateFmt.format(Date(e.date))}\",\"${e.locationName.csv()}\"") }
            sb.appendLine()
        }
        val other = expenses.filter { it.category !in catNames }
        if (other.isNotEmpty()) {
            sb.appendLine("=== OTHER / INCOME ===")
            sb.appendLine("name,amount,type,category,date")
            other.sortedByDescending { it.date }.forEach { e ->
                sb.appendLine("\"${e.name.csv()}\",${e.amount},${if (e.amount < 0) "Expense" else "Income"},\"${e.category.csv()}\",\"${dateFmt.format(Date(e.date))}\"")
            }
        }
        return sb.toString()
    }

    fun dashboardsToCsv(dashboards: List<Dashboard>, budget: BudgetUiState): String {
        val sb = StringBuilder()
        dashboards.forEach { d ->
            sb.appendLine("=== DASHBOARD: ${d.name} ===")
            sb.appendLine("Widgets: ${d.widgets.joinToString(", ") { it.displayName }}")
            sb.appendLine()
            sb.appendLine("Category,Limit,Spent,Remaining,Usage%")
            budget.categories.forEach { cat ->
                val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
                sb.appendLine("\"${cat.name.csv()}\",${cat.budget},${cat.spent},${cat.budget - cat.spent},$pct%")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun expensesToJson(expenses: List<ExpenseTransaction>): String {
        val sorted = expenses.sortedByDescending { it.date }
        val sb = StringBuilder("[\n")
        sorted.forEachIndexed { i, e ->
            sb.append("  {\"name\":\"${e.name.json()}\",\"amount\":${e.amount}," +
                    "\"type\":\"${if (e.amount < 0) "Expense" else "Income"}\"," +
                    "\"category\":\"${e.category.json()}\",\"date\":\"${dateFmt.format(Date(e.date))}\"," +
                    "\"location\":\"${e.locationName.json()}\"}")
            if (i < sorted.lastIndex) sb.append(",")
            sb.append("\n")
        }
        return sb.append("]").toString()
    }

    fun fullReportToJson(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val catNames = budget.categories.map { it.name }.toSet()
        val cats = budget.categories.joinToString(",\n    ") { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
            val txs = expenses.filter { it.category == cat.name && it.amount < 0 }
                .sortedByDescending { it.date }
                .joinToString(",\n        ") { e ->
                    "{\"name\":\"${e.name.json()}\",\"amount\":${-e.amount}," +
                            "\"date\":\"${dateFmt.format(Date(e.date))}\",\"location\":\"${e.locationName.json()}\"}"
                }
            "{\"name\":\"${cat.name.json()}\",\"limit\":${cat.budget},\"spent\":${cat.spent}," +
                    "\"remaining\":${cat.budget - cat.spent},\"usage_pct\":$pct," +
                    "\"transactions\":[\n        $txs\n      ]}"
        }
        val other = expenses.filter { it.category !in catNames }
            .sortedByDescending { it.date }
            .joinToString(",\n    ") { e ->
                "{\"name\":\"${e.name.json()}\",\"amount\":${e.amount}," +
                        "\"type\":\"${if (e.amount < 0) "Expense" else "Income"}\"," +
                        "\"category\":\"${e.category.json()}\",\"date\":\"${dateFmt.format(Date(e.date))}\"}"
            }
        return """{
  "report_date":"${dateFmt.format(Date())}",
  "budget_summary":{"total_budget":${budget.totalLimit},"total_spent":${budget.totalSpent},"remaining":${budget.totalLimit - budget.totalSpent}},
  "categories":[$cats],
  "other_transactions":[$other]
}"""
    }

    fun dashboardsToJson(
        dashboards: List<Dashboard>,
        budget: BudgetUiState = BudgetUiState(),
        expenses: List<ExpenseTransaction> = emptyList()
    ): String {
        val items = dashboards.joinToString(",\n  ") { d ->
            val cats = budget.categories.joinToString(",") { cat ->
                val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
                "{\"name\":\"${cat.name.json()}\",\"limit\":${cat.budget},\"spent\":${cat.spent},\"usage_pct\":$pct}"
            }
            "{\"name\":\"${d.name.json()}\",\"widgets\":[${d.widgets.joinToString(",") { "\"${it.displayName}\"" }}],\"categories\":[$cats]}"
        }
        return "[\n  $items\n]"
    }


    fun dashboardsToXml(dashboards: List<Dashboard>, budget: BudgetUiState): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dashboards>\n")
        sb.append("  <report_date>${dateFmt.format(Date())}</report_date>\n")
        dashboards.forEach { d ->
            sb.append("  <dashboard><name>${d.name.xml()}</name>\n")
            sb.append("    <widgets>")
            d.widgets.forEach { w -> sb.append("<widget>${w.displayName.xml()}</widget>") }
            sb.append("</widgets>\n")
            sb.append("    <categories>\n")
            budget.categories.forEach { cat ->
                val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
                sb.append("      <category><name>${cat.name.xml()}</name>")
                sb.append("<limit>${cat.budget}</limit>")
                sb.append("<spent>${cat.spent}</spent>")
                sb.append("<remaining>${cat.budget - cat.spent}</remaining>")
                sb.append("<usage_pct>$pct</usage_pct></category>\n")
            }
            sb.append("    </categories>\n")
            sb.append("  </dashboard>\n")
        }
        sb.append("</dashboards>")
        return sb.toString()
    }

    fun expensesToXml(expenses: List<ExpenseTransaction>): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<expenses>\n")
        expenses.sortedByDescending { it.date }.forEach { e ->
            sb.append("  <transaction><name>${e.name.xml()}</name><amount>${e.amount}</amount>")
            sb.append("<type>${if (e.amount < 0) "Expense" else "Income"}</type>")
            sb.append("<category>${e.category.xml()}</category><date>${dateFmt.format(Date(e.date))}</date>")
            sb.append("<location>${e.locationName.xml()}</location></transaction>\n")
        }
        return sb.append("</expenses>").toString()
    }

    fun fullReportToXml(expenses: List<ExpenseTransaction>, budget: BudgetUiState): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<financial_report>\n")
        sb.append("  <report_date>${dateFmt.format(Date())}</report_date>\n")
        sb.append("  <budget_summary><total_budget>${budget.totalLimit}</total_budget>")
        sb.append("<total_spent>${budget.totalSpent}</total_spent>")
        sb.append("<remaining>${budget.totalLimit - budget.totalSpent}</remaining></budget_summary>\n")
        sb.append("  <categories>\n")
        budget.categories.forEach { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
            sb.append("    <category><name>${cat.name.xml()}</name><limit>${cat.budget}</limit>")
            sb.append("<spent>${cat.spent}</spent><usage_pct>$pct</usage_pct><transactions>\n")
            expenses.filter { it.category == cat.name && it.amount < 0 }.sortedByDescending { it.date }
                .forEach { e ->
                    sb.append("      <transaction><name>${e.name.xml()}</name><amount>${-e.amount}</amount>")
                    sb.append("<date>${dateFmt.format(Date(e.date))}</date></transaction>\n")
                }
            sb.append("    </transactions></category>\n")
        }
        sb.append("  </categories>\n")

        val catNames = budget.categories.map { it.name }.toSet()
        val other = expenses.filter { it.category !in catNames }.sortedByDescending { it.date }
        if (other.isNotEmpty()) {
            sb.append("  <other_transactions>\n")
            other.forEach { e ->
                sb.append("    <transaction><name>${e.name.xml()}</name>")
                sb.append("<amount>${e.amount}</amount>")
                sb.append("<type>${if (e.amount < 0) "Expense" else "Income"}</type>")
                sb.append("<category>${e.category.xml()}</category>")
                sb.append("<date>${dateFmt.format(Date(e.date))}</date></transaction>\n")
            }
            sb.append("  </other_transactions>\n")
        }
        sb.append("</financial_report>")
        return sb.toString()
    }


    fun buildXlsx(
        expenses: List<ExpenseTransaction>,
        budget: BudgetUiState,
        dashboards: List<Dashboard>,
        scope: ExportScope
    ): ByteArray {
        val wb = XSSFWorkbook()
        val styles = XlsxStyles(wb)

        when (scope) {
            ExportScope.EXPENSES_ONLY -> writeExpensesSheet(wb, styles, expenses)

            ExportScope.FULL_REPORT -> {
                writeBudgetSummarySheet(wb, styles, budget)
                writeByCategorySheet(wb, styles, expenses, budget)
            }

            ExportScope.DASHBOARDS -> {
                if (dashboards.isEmpty()) {
                    val sh = wb.createSheet("Dashboards")
                    sh.createRow(0).createCell(0).setCellValue("No dashboards saved yet.")
                } else {
                    dashboards.forEachIndexed { i, d ->
                        writeDashboardSheet(wb, styles, d, budget, expenses, "${i + 1}. ${d.name}".take(28).sanitizeSheetName())
                    }
                }
            }

            ExportScope.EVERYTHING -> {
                writeBudgetSummarySheet(wb, styles, budget)
                writeByCategorySheet(wb, styles, expenses, budget)
                dashboards.forEachIndexed { i, d ->
                    writeDashboardSheet(wb, styles, d, budget, expenses, "${i + 1}. ${d.name}".take(28).sanitizeSheetName())
                }
            }
        }

        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    private class XlsxStyles(wb: XSSFWorkbook) {
        val title: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            setFont(wb.createFont().also {
                it.bold = true
                it.color = IndexedColors.WHITE.index
                it.fontHeightInPoints = 14
            })
        }
        val greenHeader: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.SEA_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            setFont(wb.createFont().also {
                it.bold = true
                it.color = IndexedColors.WHITE.index
                it.fontHeightInPoints = 11
            })
            borderBottom = BorderStyle.THIN; borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN;   borderRight = BorderStyle.THIN
        }
        val catHeader: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().also { it.bold = true; it.fontHeightInPoints = 11 })
        }
        val sectionHeader: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_TURQUOISE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().also { it.bold = true; it.fontHeightInPoints = 12 })
        }
        val num: CellStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0.00")
        }
        val redNum: CellStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0.00")
            setFont(wb.createFont().also { it.color = IndexedColors.RED.index })
        }
        val greenNum: CellStyle = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0.00")
            setFont(wb.createFont().also { it.color = IndexedColors.GREEN.index; it.bold = true })
        }
        val pctStyle: CellStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.RIGHT
            setFont(wb.createFont().also { it.bold = true })
        }
    }

    private fun Sheet.writeHeader(rowIdx: Int, style: CellStyle, vararg titles: String) {
        val row = createRow(rowIdx)
        row.height = 380
        titles.forEachIndexed { i, t ->
            row.createCell(i).also { it.setCellValue(t); it.cellStyle = style }
        }
    }


    private fun writeExpensesSheet(wb: XSSFWorkbook, s: XlsxStyles, expenses: List<ExpenseTransaction>) {
        val sh = wb.createSheet("Expenses")
        listOf(8000, 4000, 4000, 4500, 5500, 5500).forEachIndexed { i, w -> sh.setColumnWidth(i, w) }
        sh.writeHeader(0, s.greenHeader, "Name", "Amount", "Type", "Category", "Date", "Location")
        expenses.sortedByDescending { it.date }.forEachIndexed { idx, e ->
            val row = sh.createRow(idx + 1)
            row.createCell(0).setCellValue(e.name)
            row.createCell(1).also { it.setCellValue(e.amount); it.cellStyle =
                (if (e.amount < 0) s.redNum else s.greenNum) as XSSFCellStyle?
            }
            row.createCell(2).setCellValue(if (e.amount < 0) "Expense" else "Income")
            row.createCell(3).setCellValue(e.category)
            row.createCell(4).setCellValue(dateFmt.format(Date(e.date)))
            row.createCell(5).setCellValue(e.locationName)
        }
        sh.createFreezePane(0, 1)
    }


    private fun writeBudgetSummarySheet(wb: XSSFWorkbook, s: XlsxStyles, budget: BudgetUiState) {
        val sh = wb.createSheet("Budget Summary")
        listOf(7000, 5000, 5000).forEachIndexed { i, w -> sh.setColumnWidth(i, w) }

        val titleRow = sh.createRow(0); titleRow.height = 500
        titleRow.createCell(0).also { it.setCellValue("OVERALL BUDGET SUMMARY"); it.cellStyle =
            s.title as XSSFCellStyle?
        }
        sh.addMergedRegion(CellRangeAddress(0, 0, 0, 2))

        sh.writeHeader(2, s.greenHeader, "Metric", "Value ($)", "")
        var r = 3
        listOf(
            "Total Budget" to budget.totalLimit,
            "Total Spent" to budget.totalSpent,
            "Remaining"   to (budget.totalLimit - budget.totalSpent)
        ).forEach { (k, v) ->
            val row = sh.createRow(r++)
            row.createCell(0).setCellValue(k)
            row.createCell(1).also {
                it.setCellValue(v)
                it.cellStyle = (if (k == "Remaining" && v < 0) s.redNum else s.num) as XSSFCellStyle?
            }
        }
        r++

        sh.writeHeader(r++, s.greenHeader, "Category", "Limit ($)", "Spent ($)")
        budget.categories.forEach { cat ->
            val row = sh.createRow(r++)
            row.createCell(0).setCellValue(cat.name)
            row.createCell(1).also { it.setCellValue(cat.budget); it.cellStyle =
                s.num as XSSFCellStyle?
            }
            row.createCell(2).also {
                it.setCellValue(cat.spent)
                it.cellStyle = (if (cat.spent > cat.budget) s.redNum else s.num) as XSSFCellStyle?
            }
        }

        if (budget.categories.any { it.spent > 0 }) {
            val pieFirstRow = r + 2
            val pieDataStartRow = pieFirstRow
            sh.writeHeader(pieDataStartRow, s.catHeader, "Category", "Spent")
            val pieDataRow0 = pieDataStartRow + 1
            budget.categories.filter { it.spent > 0 }.forEachIndexed { i, cat ->
                val row = sh.createRow(pieDataRow0 + i)
                row.createCell(0).setCellValue(cat.name)
                row.createCell(1).also { it.setCellValue(cat.spent); it.cellStyle =
                    s.num as XSSFCellStyle?
                }
            }
            val pieDataRowN = pieDataRow0 + budget.categories.count { it.spent > 0 } - 1
            embedPieChart(
                sh = sh,
                title = "Spending by Category",
                catCol = 0, valCol = 1,
                rowFrom = pieDataRow0, rowTo = pieDataRowN,
                anchorRow0 = 2, anchorCol0 = 4,
                anchorRow1 = 22, anchorCol1 = 14
            )
        }
        sh.createFreezePane(0, 3)
    }


    private fun writeByCategorySheet(
        wb: XSSFWorkbook, s: XlsxStyles,
        expenses: List<ExpenseTransaction>, budget: BudgetUiState
    ) {
        val catSh = wb.createSheet("By Category")
        listOf(8000, 4500, 4500, 5500, 5500).forEachIndexed { i, w -> catSh.setColumnWidth(i, w) }
        var row = 0
        budget.categories.forEach { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0

            val hRow = catSh.createRow(row); hRow.height = 500
            hRow.createCell(0).also {
                it.setCellValue("${cat.name}   |   Limit: \$${fmt2(cat.budget)}   Spent: \$${fmt2(cat.spent)}   ($pct%)")
                it.cellStyle = s.catHeader as XSSFCellStyle?
            }
            catSh.addMergedRegion(CellRangeAddress(row, row, 0, 4)); row++

            drawCellBar(catSh, wb, row, pct)
            row++

            catSh.writeHeader(row++, s.greenHeader, "Transaction Name", "Amount ($)", "Date", "Location", "")

            val txs = expenses.filter { it.category == cat.name && it.amount < 0 }.sortedByDescending { it.date }
            if (txs.isEmpty()) {
                catSh.createRow(row++).createCell(0).setCellValue("  (no transactions in this category)")
            } else {
                txs.forEach { e ->
                    val r2 = catSh.createRow(row++)
                    r2.createCell(0).setCellValue(e.name)
                    r2.createCell(1).also { it.setCellValue(-e.amount); it.cellStyle =
                        s.redNum as XSSFCellStyle?
                    }
                    r2.createCell(2).setCellValue(dateFmt.format(Date(e.date)))
                    r2.createCell(3).setCellValue(e.locationName)
                }
            }
            row += 2
        }

        val catNames = budget.categories.map { it.name }.toSet()
        val other = expenses.filter { it.category !in catNames }.sortedByDescending { it.date }
        if (other.isNotEmpty()) {
            val hRow = catSh.createRow(row); hRow.height = 500
            hRow.createCell(0).also {
                it.setCellValue("OTHER / INCOME"); it.cellStyle = s.sectionHeader as XSSFCellStyle?
            }
            catSh.addMergedRegion(CellRangeAddress(row, row, 0, 4)); row++
            catSh.writeHeader(row++, s.greenHeader, "Name", "Amount ($)", "Type", "Category", "Date")
            other.forEach { e ->
                val r2 = catSh.createRow(row++)
                r2.createCell(0).setCellValue(e.name)
                r2.createCell(1).also { it.setCellValue(e.amount); it.cellStyle =
                    (if (e.amount < 0) s.redNum else s.greenNum) as XSSFCellStyle?
                }
                r2.createCell(2).setCellValue(if (e.amount < 0) "Expense" else "Income")
                r2.createCell(3).setCellValue(e.category)
                r2.createCell(4).setCellValue(dateFmt.format(Date(e.date)))
            }
        }
    }

    private fun drawCellBar(sh: Sheet, wb: XSSFWorkbook, row: Int, pct: Int) {
        val barRow = sh.createRow(row); barRow.height = 280
        val totalCols = 30
        val filled = (pct * totalCols / 100).coerceIn(0, totalCols)
        val barColor = when {
            pct >= 100 -> IndexedColors.RED.index
            pct >= 80  -> IndexedColors.ORANGE.index
            else       -> IndexedColors.BRIGHT_GREEN.index
        }
        val filledStyle = wb.createCellStyle().apply {
            fillForegroundColor = barColor
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val emptyStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        repeat(totalCols) { col ->
            barRow.createCell(col).cellStyle = if (col < filled) filledStyle else emptyStyle
        }
    }


    private fun writeDashboardSheet(
        wb: XSSFWorkbook, s: XlsxStyles, dashboard: Dashboard,
        budget: BudgetUiState, expenses: List<ExpenseTransaction>, sheetName: String
    ) {
        val safeName = sheetName.replace(Regex("[\\\\/?*\\[\\]]"), "_").take(31)
        val sh = wb.createSheet(safeName)
        listOf(7000, 4500, 4500, 4500, 4500, 4500).forEachIndexed { i, w -> sh.setColumnWidth(i, w) }


        val titleRow = sh.createRow(0); titleRow.height = 600
        titleRow.createCell(0).also { it.setCellValue("DASHBOARD: ${dashboard.name}"); it.cellStyle =
            s.title as XSSFCellStyle?
        }
        sh.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

        sh.createRow(1).createCell(0).setCellValue(
            "Widgets: ${dashboard.widgets.joinToString("  ·  ") { it.displayName }}"
        )

        var row = 3

        sh.writeHeader(row++, s.greenHeader, "Category", "Limit ($)", "Spent ($)", "Remaining ($)", "Usage %")
        val catDataStart = row
        budget.categories.forEach { cat ->
            val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
            val r = sh.createRow(row++)
            r.createCell(0).setCellValue(cat.name)
            r.createCell(1).also { it.setCellValue(cat.budget); it.cellStyle =
                s.num as XSSFCellStyle?
            }
            r.createCell(2).also {
                it.setCellValue(cat.spent)
                it.cellStyle = (if (pct >= 100) s.redNum else s.num) as XSSFCellStyle?
            }
            r.createCell(3).also { it.setCellValue(cat.budget - cat.spent); it.cellStyle =
                s.num as XSSFCellStyle?
            }
            r.createCell(4).also { it.setCellValue("$pct%"); it.cellStyle =
                s.pctStyle as XSSFCellStyle?
            }
        }
        val catDataEnd = row - 1
        row += 2

        if (budget.categories.isEmpty()) {
            sh.createRow(row).createCell(0).setCellValue("(No categories configured — set up your budget first.)")
            return
        }

        var anchorRowTop = 2
        val chartHeight = 18

        dashboard.widgets.forEach { widget ->
            when (widget) {
                WidgetType.PIE_CHART -> {
                    embedPieChart(
                        sh, "Spending by Category",
                        catCol = 0, valCol = 2,
                        rowFrom = catDataStart, rowTo = catDataEnd,
                        anchorRow0 = anchorRowTop, anchorCol0 = 7,
                        anchorRow1 = anchorRowTop + chartHeight, anchorCol1 = 16
                    )
                    anchorRowTop += chartHeight + 1
                }
                WidgetType.BUDGET_VS_ACTUAL -> {
                    embedBudgetVsActualChart(
                        sh, catCol = 0, limitCol = 1, spentCol = 2,
                        rowFrom = catDataStart, rowTo = catDataEnd,
                        anchorRow0 = anchorRowTop, anchorCol0 = 7,
                        anchorRow1 = anchorRowTop + chartHeight, anchorCol1 = 16
                    )
                    anchorRowTop += chartHeight + 1
                }
                WidgetType.BAR_CHART -> {
                    val (labels, values) = buildDailySeries(expenses, days = 14)
                    if (values.any { it > 0 }) {
                        val (rowFrom, rowTo) = writeSeries(sh, row, "Day", "Spent", labels, values, s)
                        embedBarChart(
                            sh, title = "Daily Spending (last 14 days)",
                            catCol = 0, valCol = 1,
                            rowFrom = rowFrom, rowTo = rowTo,
                            anchorRow0 = anchorRowTop, anchorCol0 = 7,
                            anchorRow1 = anchorRowTop + chartHeight, anchorCol1 = 16
                        )
                        row = rowTo + 2
                        anchorRowTop += chartHeight + 1
                    }
                }
                WidgetType.MONTHLY_TREND -> {
                    val (labels, values) = buildDailySeries(expenses, days = 30)
                    if (values.any { it > 0 }) {
                        val (rowFrom, rowTo) = writeSeries(sh, row, "Day", "Spent", labels, values, s)
                        embedBarChart(
                            sh, title = "Monthly Trend (last 30 days)",
                            catCol = 0, valCol = 1,
                            rowFrom = rowFrom, rowTo = rowTo,
                            anchorRow0 = anchorRowTop, anchorCol0 = 7,
                            anchorRow1 = anchorRowTop + chartHeight, anchorCol1 = 16
                        )
                        row = rowTo + 2
                        anchorRowTop += chartHeight + 1
                    }
                }
                WidgetType.TOP_EXPENSES -> {
                    val top = expenses.filter { it.amount < 0 }
                        .sortedBy { it.amount }
                        .take(10)
                    if (top.isNotEmpty()) {
                        val labels = top.map { it.name.take(18) }
                        val values = top.map { -it.amount }
                        val (rowFrom, rowTo) = writeSeries(sh, row, "Expense", "Amount", labels, values, s)
                        embedBarChart(
                            sh, title = "Top Expenses",
                            catCol = 0, valCol = 1,
                            rowFrom = rowFrom, rowTo = rowTo,
                            anchorRow0 = anchorRowTop, anchorCol0 = 7,
                            anchorRow1 = anchorRowTop + chartHeight, anchorCol1 = 16
                        )
                        row = rowTo + 2
                        anchorRowTop += chartHeight + 1
                    }
                }
                WidgetType.SUMMARY_STATS -> {
                    val r = sh.createRow(row++)
                    r.createCell(0).setCellValue("Summary:")
                    r.createCell(1).setCellValue("Spent \$${fmt2(budget.totalSpent)} of \$${fmt2(budget.totalLimit)}")
                    r.createCell(2).setCellValue(
                        "Remaining \$${fmt2(budget.totalLimit - budget.totalSpent)}"
                    )
                    row++
                }
            }
        }
    }

    private fun writeSeries(
        sh: Sheet, startRow: Int,
        labelHdr: String, valueHdr: String,
        labels: List<String>, values: List<Double>,
        s: XlsxStyles
    ): Pair<Int, Int> {
        sh.writeHeader(startRow, s.catHeader, labelHdr, valueHdr)
        val first = startRow + 1
        labels.forEachIndexed { i, lbl ->
            val r = sh.createRow(first + i)
            r.createCell(0).setCellValue(lbl)
            r.createCell(1).also { it.setCellValue(values[i]); it.cellStyle = s.num }
        }
        return first to (first + labels.size - 1)
    }


    private fun embedPieChart(
        sh: Sheet, title: String,
        catCol: Int, valCol: Int,
        rowFrom: Int, rowTo: Int,
        anchorRow0: Int, anchorCol0: Int,
        anchorRow1: Int, anchorCol1: Int
    ) {
        val xsh = sh as? XSSFSheet ?: return
        if (rowTo < rowFrom) return
        val drawing = xsh.createDrawingPatriarch()
        val anchor: XSSFClientAnchor = drawing.createAnchor(
            0, 0, 0, 0, anchorCol0, anchorRow0, anchorCol1, anchorRow1
        )
        val chart: XSSFChart = drawing.createChart(anchor)
        chart.setTitleText(title)
        chart.titleOverlay = false

        val legend = chart.orAddLegend
        legend.position = LegendPosition.RIGHT

        val categories = XDDFDataSourcesFactory.fromStringCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, catCol, catCol)
        )
        val values = XDDFDataSourcesFactory.fromNumericCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, valCol, valCol)
        )
        val data: XDDFChartData = chart.createData(ChartTypes.PIE, null, null)
        (data as XDDFPieChartData).setVaryColors(true)
        data.addSeries(categories, values)
        chart.plot(data)
    }

    private fun embedBarChart(
        sh: Sheet, title: String,
        catCol: Int, valCol: Int,
        rowFrom: Int, rowTo: Int,
        anchorRow0: Int, anchorCol0: Int,
        anchorRow1: Int, anchorCol1: Int
    ) {
        val xsh = sh as? XSSFSheet ?: return
        if (rowTo < rowFrom) return
        val drawing = xsh.createDrawingPatriarch()
        val anchor: XSSFClientAnchor = drawing.createAnchor(
            0, 0, 0, 0, anchorCol0, anchorRow0, anchorCol1, anchorRow1
        )
        val chart: XSSFChart = drawing.createChart(anchor)
        chart.setTitleText(title)
        chart.titleOverlay = false

        val legend = chart.orAddLegend
        legend.position = LegendPosition.BOTTOM

        val bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM)
        val leftAxis = chart.createValueAxis(AxisPosition.LEFT)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, catCol, catCol)
        )
        val values = XDDFDataSourcesFactory.fromNumericCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, valCol, valCol)
        )
        val data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis) as XDDFBarChartData
        data.barDirection = BarDirection.COL
        data.setVaryColors(true)
        val series = data.addSeries(categories, values)
        series.setTitle(title, null)

        val fill = XDDFSolidFillProperties(XDDFColor.from(PresetColor.SEA_GREEN))
        val sp = XDDFShapeProperties().apply { fillProperties = fill }
        series.shapeProperties = sp

        chart.plot(data)
    }

    private fun embedBudgetVsActualChart(
        sh: Sheet,
        catCol: Int, limitCol: Int, spentCol: Int,
        rowFrom: Int, rowTo: Int,
        anchorRow0: Int, anchorCol0: Int,
        anchorRow1: Int, anchorCol1: Int
    ) {
        val xsh = sh as? XSSFSheet ?: return
        if (rowTo < rowFrom) return
        val drawing = xsh.createDrawingPatriarch()
        val anchor: XSSFClientAnchor = drawing.createAnchor(
            0, 0, 0, 0, anchorCol0, anchorRow0, anchorCol1, anchorRow1
        )
        val chart: XSSFChart = drawing.createChart(anchor)
        chart.setTitleText("Budget vs Actual")
        chart.titleOverlay = false

        val legend = chart.orAddLegend
        legend.position = LegendPosition.BOTTOM

        val bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM)
        val leftAxis = chart.createValueAxis(AxisPosition.LEFT)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, catCol, catCol)
        )
        val limits = XDDFDataSourcesFactory.fromNumericCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, limitCol, limitCol)
        )
        val spent = XDDFDataSourcesFactory.fromNumericCellRange(
            xsh, CellRangeAddress(rowFrom, rowTo, spentCol, spentCol)
        )
        val data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis) as XDDFBarChartData
        data.barDirection = BarDirection.COL
        data.setVaryColors(false)

        val sLimit = data.addSeries(categories, limits)
        sLimit.setTitle("Limit", null)
        sLimit.shapeProperties = XDDFShapeProperties().apply {
            fillProperties = XDDFSolidFillProperties(XDDFColor.from(PresetColor.LIGHT_GREEN))
        }
        val sSpent = data.addSeries(categories, spent)
        sSpent.setTitle("Spent", null)
        sSpent.shapeProperties = XDDFShapeProperties().apply {
            fillProperties = XDDFSolidFillProperties(XDDFColor.from(PresetColor.DARK_GREEN))
        }
        chart.plot(data)
    }


    fun buildPdf(
        expenses: List<ExpenseTransaction>,
        budget: BudgetUiState,
        dashboards: List<Dashboard>,
        scope: ExportScope
    ): ByteArray = PdfBuilder(expenses, budget, dashboards).build(scope)

    private class PdfBuilder(
        val expenses: List<ExpenseTransaction>,
        val budget: BudgetUiState,
        val dashboards: List<Dashboard>
    ) {
        private val pageW = 595
        private val pageH = 842
        private val mg    = 40f
        private val lh    = 16f

        private val titleP = mkPaint(20f, true,  "#1B5E20")
        private val secP   = mkPaint(14f, true,  "#2E7D32")
        private val catP   = mkPaint(11f, true,  "#1565C0")
        private val bodyP  = mkPaint(10f, false, "#333333")
        private val smallP = mkPaint(9f,  false, "#888888")
        private val redP   = mkPaint(10f, false, "#C62828")
        private val greenP = mkPaint(10f, false, "#2E7D32")
        private val lineP  = Paint().apply {
            color = android.graphics.Color.parseColor("#C8E6C9")
            strokeWidth = 1f; isAntiAlias = true
        }

        private val pdf = PdfDocument()
        private var pageNum = 1
        private var page: PdfDocument.Page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
        )
        private var cv = page.canvas
        private var y = mg + 20f

        fun build(scope: ExportScope): ByteArray {
            cv.drawText("Smart Expense Tracker", mg, y, titleP); y += lh + 6
            cv.drawText("${scope.label}   |   ${dayFmt.format(Date())}", mg, y, smallP); y += lh
            divider(); gap(4f)

            when (scope) {
                ExportScope.EXPENSES_ONLY -> renderExpensesOnly()
                ExportScope.FULL_REPORT   -> renderFullReport()
                ExportScope.DASHBOARDS    -> renderDashboards()
                ExportScope.EVERYTHING    -> { renderFullReport(); renderDashboards() }
            }

            pdf.finishPage(page)
            val out = ByteArrayOutputStream()
            pdf.writeTo(out); pdf.close()
            return out.toByteArray()
        }


        private fun newPage() {
            pdf.finishPage(page); pageNum++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
            cv = page.canvas; y = mg + 20f
        }
        private fun ensureSpace(need: Float = lh + 4f) { if (y + need > pageH - mg) newPage() }
        private fun text(t: String, p: Paint, x: Float = mg) { ensureSpace(); cv.drawText(t, x, y, p); y += lh }
        private fun divider() { ensureSpace(10f); cv.drawLine(mg, y, pageW - mg, y, lineP); y += 9f }
        private fun gap(n: Float = 6f) { y += n }

        private fun renderExpensesOnly() {
            text("Transactions  (${expenses.size})", secP); gap(4f)
            val sorted = expenses.sortedByDescending { it.date }
            val cols = floatArrayOf(mg + 8f, mg + 188f, mg + 268f, mg + 358f)
            cv.drawText("Name", cols[0], y, catP); cv.drawText("Amount", cols[1], y, catP)
            cv.drawText("Category", cols[2], y, catP); cv.drawText("Date", cols[3], y, catP); y += lh
            cv.drawLine(mg, y, pageW - mg, y, lineP); y += 7f
            sorted.forEach { e ->
                ensureSpace()
                cv.drawText(e.name.take(24), cols[0], y, bodyP)
                val ap = if (e.amount < 0) redP else greenP
                cv.drawText("${if (e.amount >= 0) "+" else ""}\$${fmt2(e.amount)}", cols[1], y, ap)
                cv.drawText(e.category.take(13), cols[2], y, smallP)
                cv.drawText(dayFmt.format(Date(e.date)), cols[3], y, smallP)
                y += lh
            }
        }

        private fun renderFullReport() {
            text("Budget Overview", secP); gap(2f)
            text("Total Budget : \$${fmt2(budget.totalLimit)}", bodyP, mg + 8f)
            text("Total Spent  : \$${fmt2(budget.totalSpent)}", bodyP, mg + 8f)
            text("Remaining    : \$${fmt2(budget.totalLimit - budget.totalSpent)}", bodyP, mg + 8f)
            gap(4f); divider()

            if (budget.categories.isEmpty()) {
                text("(No categories configured.)", smallP, mg + 8f)
                divider(); return
            }

            budget.categories.forEach { cat ->
                val pct = if (cat.budget > 0) (cat.spent / cat.budget * 100).toInt() else 0
                ensureSpace(lh * 4)
                text("${cat.name}   Limit: \$${fmt2(cat.budget)}   Spent: \$${fmt2(cat.spent)}   $pct%", catP)
                drawProgressBar(cat.colorHex, pct)

                val txs = expenses.filter { it.category == cat.name && it.amount < 0 }
                    .sortedByDescending { it.date }
                if (txs.isEmpty()) {
                    text("  (no transactions)", smallP)
                } else {
                    txs.take(60).forEach { e ->
                        ensureSpace()
                        cv.drawText(e.name.take(28), mg + 12f, y, bodyP)
                        cv.drawText("-\$${fmt2(-e.amount)}", mg + 258f, y, redP)
                        cv.drawText(dayFmt.format(Date(e.date)), mg + 340f, y, smallP)
                        cv.drawText(e.locationName.take(16), mg + 418f, y, smallP)
                        y += lh
                    }
                    if (txs.size > 60) text("  … and ${txs.size - 60} more", smallP)
                }
                gap(6f); divider()
            }

            val catNames = budget.categories.map { it.name }.toSet()
            val other = expenses.filter { it.category !in catNames }.sortedByDescending { it.date }
            if (other.isNotEmpty()) {
                ensureSpace(lh * 3)
                text("Other / Income", secP)
                other.take(40).forEach { e ->
                    ensureSpace()
                    cv.drawText(e.name.take(28), mg + 12f, y, bodyP)
                    val ap = if (e.amount < 0) redP else greenP
                    cv.drawText("${if (e.amount >= 0) "+" else ""}\$${fmt2(e.amount)}", mg + 258f, y, ap)
                    cv.drawText(e.category.take(14), mg + 340f, y, smallP)
                    cv.drawText(dayFmt.format(Date(e.date)), mg + 430f, y, smallP)
                    y += lh
                }
                if (other.size > 40) text("  … and ${other.size - 40} more", smallP)
                divider()
            }
        }

        private fun renderDashboards() {
            ensureSpace(lh * 3)
            text("Dashboards", secP); gap(4f); divider()

            if (dashboards.isEmpty()) {
                text("No dashboards saved yet.", bodyP); return
            }

            dashboards.forEach { d ->
                ensureSpace(lh * 4)
                text("Dashboard: ${d.name}", secP, mg)
                text("Widgets: ${d.widgets.joinToString("  ·  ") { it.displayName }}", smallP, mg + 8f)
                gap(6f)

                if (budget.categories.isEmpty()) {
                    text("(No categories — set up your budget first.)", smallP, mg + 8f)
                    divider(); return@forEach
                }

                d.widgets.forEach { w ->
                    when (w) {
                        WidgetType.PIE_CHART -> {
                            text("Spending by Category", catP, mg + 8f)
                            drawPieChart(budget.categories)
                            gap(6f)
                        }
                        WidgetType.BUDGET_VS_ACTUAL -> {
                            text("Budget vs Actual", catP, mg + 8f)
                            drawGroupedBarChart(budget.categories)
                            gap(6f)
                        }
                        WidgetType.BAR_CHART -> {
                            text("Daily Spending (last 14 days)", catP, mg + 8f)
                            val (labels, values) = buildDailySeries(expenses, 14)
                            drawValueBarChart(labels, values)
                            gap(6f)
                        }
                        WidgetType.MONTHLY_TREND -> {
                            text("Monthly Trend (last 30 days)", catP, mg + 8f)
                            val (labels, values) = buildDailySeries(expenses, 30)
                            drawLineChart(labels, values)
                            gap(6f)
                        }
                        WidgetType.TOP_EXPENSES -> {
                            text("Top Expenses", catP, mg + 8f)
                            val top = expenses.filter { it.amount < 0 }
                                .sortedBy { it.amount }
                                .take(8)
                            if (top.isNotEmpty()) {
                                val labels = top.map { it.name.take(14) }
                                val values = top.map { -it.amount }
                                drawValueBarChart(labels, values)
                            } else {
                                text("(No expenses recorded yet)", smallP, mg + 12f)
                            }
                            gap(6f)
                        }
                        WidgetType.SUMMARY_STATS -> {
                            ensureSpace(lh * 3)
                            text("Summary", catP, mg + 8f)
                            text(
                                "Total spent: \$${fmt2(budget.totalSpent)}   |   " +
                                        "Budget: \$${fmt2(budget.totalLimit)}   |   " +
                                        "Remaining: \$${fmt2(budget.totalLimit - budget.totalSpent)}",
                                bodyP, mg + 12f
                            )
                            gap(6f)
                        }
                    }
                }
                divider()
            }
        }


        private fun drawProgressBar(catColor: String, pct: Int) {
            ensureSpace(20f)
            y += 4f
            val barW = pageW - mg * 2 - 16f
            val bgP = Paint().apply {
                color = android.graphics.Color.parseColor("#E8F5E9")
                style = Paint.Style.FILL; isAntiAlias = true
            }
            val fillColor = when {
                pct >= 100 -> "#C62828"
                pct >= 80  -> "#E65100"
                else       -> catColor
            }
            val fillP = Paint().apply {
                color = parseColorSafe(fillColor)
                style = Paint.Style.FILL; isAntiAlias = true
            }
            val bgR = RectF(mg + 8f, y, mg + 8f + barW, y + 9f)
            cv.drawRoundRect(bgR, 4f, 4f, bgP)
            val fillW = barW * (pct.coerceIn(0, 100) / 100f)
            if (fillW > 0) cv.drawRoundRect(RectF(mg + 8f, y, mg + 8f + fillW, y + 9f), 4f, 4f, fillP)
            y += 18f
        }

        private fun drawPieChart(categories: List<CategoryBudgetView>) {
            val cats = categories.filter { it.spent > 0 }
            if (cats.isEmpty()) { text("(no spending yet)", smallP, mg + 12f); return }

            val chartH = 170f
            ensureSpace(chartH + 10f)

            val cx = mg + 95f
            val cy = y + chartH / 2 - 5f
            val r  = 70f
            val total = cats.sumOf { it.spent }.toFloat().coerceAtLeast(0.001f)

            var startAngle = -90f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            cats.forEachIndexed { i, cat ->
                val sweep = (cat.spent.toFloat() / total) * 360f
                val color = parseColorSafe(cat.colorHex.ifEmpty { palette[i % palette.size] })
                val arcP = Paint().apply {
                    this.color = color
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                cv.drawArc(rect, startAngle, sweep, true, arcP)
                val borderP = Paint().apply {
                    this.color = android.graphics.Color.WHITE
                    style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
                }
                cv.drawArc(rect, startAngle, sweep, true, borderP)
                startAngle += sweep
            }

            val lx = cx + r + 30f
            var ly = y + 8f
            cats.take(8).forEachIndexed { i, cat ->
                val pct = (cat.spent.toFloat() / total * 100).toInt()
                val swatch = Paint().apply {
                    color = parseColorSafe(cat.colorHex.ifEmpty { palette[i % palette.size] })
                    style = Paint.Style.FILL; isAntiAlias = true
                }
                cv.drawRect(lx, ly - 8f, lx + 10f, ly + 2f, swatch)
                cv.drawText("${cat.name.take(16)}  $pct%  (\$${fmt2(cat.spent)})", lx + 16f, ly, smallP)
                ly += lh
            }
            y = max(cy + r + 10f, ly + 4f)
        }

        private fun drawGroupedBarChart(categories: List<CategoryBudgetView>) {
            val cats = categories.take(8)
            if (cats.isEmpty()) { text("(no categories)", smallP, mg + 12f); return }

            val chartH = 160f
            ensureSpace(chartH + 30f)

            val left = mg + 50f
            val right = pageW - mg - 10f
            val top = y
            val bottom = y + chartH
            val width = right - left

            val maxV = cats.maxOf { max(it.budget, it.spent) }.toFloat().coerceAtLeast(1f)
            val groupW = width / cats.size
            val barW = (groupW - 6f) / 2f

            val axisP = Paint().apply {
                color = android.graphics.Color.parseColor("#888888")
                strokeWidth = 1f; isAntiAlias = true
            }
            cv.drawLine(left, bottom, right, bottom, axisP)
            cv.drawLine(left, top, left, bottom, axisP)

            for (i in 0..4) {
                val v = maxV * i / 4f
                val ty = bottom - (chartH * i / 4f)
                cv.drawText("\$${fmt0(v.toDouble())}", mg + 4f, ty + 3f, smallP)
                if (i > 0) cv.drawLine(left, ty, right, ty, Paint(axisP).apply {
                    color = android.graphics.Color.parseColor("#EEEEEE")
                })
            }

            cats.forEachIndexed { i, cat ->
                val cx = left + groupW * i + 3f
                val limitH = (cat.budget.toFloat() / maxV) * chartH
                val spentH = (cat.spent.toFloat() / maxV) * chartH

                val limitP = Paint().apply {
                    color = android.graphics.Color.parseColor("#C8E6C9")
                    style = Paint.Style.FILL; isAntiAlias = true
                }
                val spentP = Paint().apply {
                    color = parseColorSafe(cat.colorHex.ifEmpty { "#2E7D32" })
                    style = Paint.Style.FILL; isAntiAlias = true
                }
                cv.drawRect(cx, bottom - limitH, cx + barW, bottom, limitP)
                cv.drawRect(cx + barW + 2f, bottom - spentH, cx + 2 * barW + 2f, bottom, spentP)

                cv.drawText(cat.name.take(8), cx, bottom + 12f, smallP)
            }

            val legX = right - 100f
            val legY = top + 8f
            val swatchA = Paint().apply { color = android.graphics.Color.parseColor("#C8E6C9"); style = Paint.Style.FILL }
            val swatchB = Paint().apply { color = android.graphics.Color.parseColor("#2E7D32"); style = Paint.Style.FILL }
            cv.drawRect(legX, legY - 8f, legX + 10f, legY + 2f, swatchA)
            cv.drawText("Limit", legX + 14f, legY, smallP)
            cv.drawRect(legX, legY + 8f, legX + 10f, legY + 18f, swatchB)
            cv.drawText("Spent", legX + 14f, legY + 16f, smallP)

            y = bottom + 22f
        }

        private fun drawValueBarChart(labels: List<String>, values: List<Double>) {
            if (labels.isEmpty()) return
            val chartH = 150f
            ensureSpace(chartH + 30f)

            val left = mg + 50f
            val right = pageW - mg - 10f
            val top = y
            val bottom = y + chartH
            val width = right - left

            val maxV = values.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val barSlot = width / values.size
            val barW = barSlot * 0.7f

            val axisP = Paint().apply {
                color = android.graphics.Color.parseColor("#888888")
                strokeWidth = 1f; isAntiAlias = true
            }
            cv.drawLine(left, bottom, right, bottom, axisP)
            cv.drawLine(left, top, left, bottom, axisP)
            for (i in 0..4) {
                val v = maxV * i / 4f
                val ty = bottom - (chartH * i / 4f)
                cv.drawText("\$${fmt0(v.toDouble())}", mg + 4f, ty + 3f, smallP)
                if (i > 0) cv.drawLine(left, ty, right, ty, Paint(axisP).apply {
                    color = android.graphics.Color.parseColor("#EEEEEE")
                })
            }

            val barP = Paint().apply {
                color = android.graphics.Color.parseColor("#2E7D32")
                style = Paint.Style.FILL; isAntiAlias = true
            }
            values.forEachIndexed { i, v ->
                val h = (v.toFloat() / maxV) * chartH
                val cx = left + barSlot * i + (barSlot - barW) / 2f
                cv.drawRect(cx, bottom - h, cx + barW, bottom, barP)
            }

            val step = max(1, values.size / 8)
            for (i in values.indices step step) {
                val cx = left + barSlot * i + (barSlot - barW) / 2f
                cv.drawText(labels[i].take(7), cx, bottom + 12f, smallP)
            }

            y = bottom + 22f
        }

        private fun drawLineChart(labels: List<String>, values: List<Double>) {
            if (labels.isEmpty()) return
            val chartH = 150f
            ensureSpace(chartH + 30f)

            val left = mg + 50f
            val right = pageW - mg - 10f
            val top = y
            val bottom = y + chartH
            val width = right - left
            val maxV = values.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

            val axisP = Paint().apply {
                color = android.graphics.Color.parseColor("#888888")
                strokeWidth = 1f; isAntiAlias = true
            }
            cv.drawLine(left, bottom, right, bottom, axisP)
            cv.drawLine(left, top, left, bottom, axisP)
            for (i in 0..4) {
                val v = maxV * i / 4f
                val ty = bottom - (chartH * i / 4f)
                cv.drawText("\$${fmt0(v.toDouble())}", mg + 4f, ty + 3f, smallP)
                if (i > 0) cv.drawLine(left, ty, right, ty, Paint(axisP).apply {
                    color = android.graphics.Color.parseColor("#EEEEEE")
                })
            }

            val n = values.size
            val dx = if (n > 1) width / (n - 1) else 0f

            val path = Path()
            path.moveTo(left, bottom)
            values.forEachIndexed { i, v ->
                val px = left + dx * i
                val py = bottom - (v.toFloat() / maxV) * chartH
                path.lineTo(px, py)
            }
            path.lineTo(left + dx * (n - 1), bottom)
            path.close()
            val areaP = Paint().apply {
                color = android.graphics.Color.parseColor("#A5D6A7")
                style = Paint.Style.FILL; isAntiAlias = true; alpha = 120
            }
            cv.drawPath(path, areaP)

            val lineDraw = Paint().apply {
                color = android.graphics.Color.parseColor("#2E7D32")
                style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true
            }
            val dotP = Paint().apply {
                color = android.graphics.Color.parseColor("#1B5E20")
                style = Paint.Style.FILL; isAntiAlias = true
            }
            for (i in 0 until n - 1) {
                val x1 = left + dx * i
                val y1 = bottom - (values[i].toFloat() / maxV) * chartH
                val x2 = left + dx * (i + 1)
                val y2 = bottom - (values[i + 1].toFloat() / maxV) * chartH
                cv.drawLine(x1, y1, x2, y2, lineDraw)
            }
            for (i in 0 until n) {
                val px = left + dx * i
                val py = bottom - (values[i].toFloat() / maxV) * chartH
                cv.drawCircle(px, py, 2.5f, dotP)
            }

            val step = max(1, values.size / 8)
            for (i in values.indices step step) {
                val cx = left + dx * i
                cv.drawText(labels[i].take(7), cx - 12f, bottom + 12f, smallP)
            }

            y = bottom + 22f
        }
    }

    private fun buildDailySeries(expenses: List<ExpenseTransaction>, days: Int): Pair<List<String>, List<Double>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);     cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000

        val labels = mutableListOf<String>()
        val values = MutableList(days) { 0.0 }
        for (i in 0 until days) {
            val dayStart = today - (days - 1 - i) * dayMs
            labels += monthFmt.format(Date(dayStart))
        }
        expenses.filter { it.amount < 0 }.forEach { e ->
            val diff = ((today - e.date) / dayMs).toInt()
            val idx = days - 1 - diff
            if (idx in 0 until days) values[idx] = values[idx] + abs(e.amount)
        }
        return labels to values
    }

    private fun mkPaint(size: Float, bold: Boolean = false, hex: String = "#333333") =
        Paint().apply {
            textSize = size; isFakeBoldText = bold
            color = parseColorSafe(hex); isAntiAlias = true
        }

    private fun parseColorSafe(hex: String): Int =
        runCatching { android.graphics.Color.parseColor(hex) }
            .getOrDefault(android.graphics.Color.parseColor("#4CAF50"))

    private fun fmt2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun fmt0(v: Double) = String.format(Locale.US, "%.0f", v)

    private fun String.xml()  = replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    private fun String.json() = replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
    private fun String.csv()  = replace("\"","\"\"")

    private fun String.sanitizeSheetName(): String =
        replace(Regex("[:\\\\/?*\\[\\]]"), " ").trim().ifEmpty { "Sheet" }
}