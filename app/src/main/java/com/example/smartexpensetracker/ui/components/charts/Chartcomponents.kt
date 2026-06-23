package com.example.smartexpensetracker.ui.components.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartexpensetracker.model.BarEntry
import com.example.smartexpensetracker.model.PieSlice
import com.example.smartexpensetracker.model.SummaryStats
import com.example.smartexpensetracker.model.CategoryBudgetView
import com.example.smartexpensetracker.model.ExpenseTransaction
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import kotlin.math.*

@Composable
fun PieChartCard(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)

    var animated by remember { mutableStateOf(false) }
    val sweep by animateFloatAsState(
        targetValue = if (animated) 360f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "pie_sweep"
    )
    LaunchedEffect(slices) { animated = true }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(16.dp))

            if (slices.isEmpty()) {
                EmptyChartPlaceholder("No category data yet")
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut
                Canvas(modifier = Modifier.size(150.dp)) {
                    val stroke = 36.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    var startAngle = -90f
                    slices.forEach { slice ->
                        val sliceSweep = (slice.value / total) * sweep
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sliceSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        startAngle += sliceSweep
                    }
                    drawCircle(
                        color = Color.White,
                        radius = (diameter / 2f) - stroke / 2f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slices.forEach { slice ->
                        val pct = ((slice.value / total) * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(slice.color, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${slice.label} ($pct%)",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BarChartCard(entries: List<BarEntry>, title: String = "Daily Spending", modifier: Modifier = Modifier) {
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(0.01f) ?: 1f

    var animated by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (animated) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "bar_anim"
    )
    LaunchedEffect(entries) { animated = true }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))

            if (entries.isEmpty()) {
                EmptyChartPlaceholder("No spending data yet")
                return@Column
            }

            val barColor = PrimaryGreen
            val barColorOver = Color(0xFFF44336)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val barCount = entries.size
                val spacing = size.width / (barCount * 2 + 1)
                val barWidth = spacing
                val chartHeight = size.height - 24.dp.toPx()  // leave space for labels

                entries.forEachIndexed { i, entry ->
                    val x = spacing + i * (barWidth + spacing)
                    val barH = (entry.value / maxValue) * chartHeight * progress
                    val color = if (entry.value >= maxValue * 0.9f) barColorOver else barColor

                    drawRoundRect(
                        color = color.copy(alpha = 0.85f),
                        topLeft = Offset(x, chartHeight - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                entries.forEach { entry ->
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetVsActualCard(categories: List<CategoryBudgetView>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Budget vs Actual",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Row {
                LegendDot(Color(0xFF90CAF9), "Budget")
                Spacer(Modifier.width(12.dp))
                LegendDot(PrimaryGreen, "Spent")
            }
            Spacer(Modifier.height(16.dp))

            if (categories.isEmpty()) {
                EmptyChartPlaceholder("Add budget categories first")
                return@Column
            }

            categories.forEach { cat ->
                val catColor = runCatching {
                    Color(android.graphics.Color.parseColor(cat.colorHex))
                }.getOrDefault(PrimaryGreen)

                val spentRatio = if (cat.budget > 0) (cat.spent / cat.budget).toFloat().coerceIn(0f, 1f) else 0f
                var animated by remember { mutableStateOf(false) }
                val animRatio by animateFloatAsState(
                    if (animated) spentRatio else 0f,
                    tween(700), label = "bva_${cat.name}"
                )
                LaunchedEffect(Unit) { animated = true }

                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$${String.format("%.0f", cat.spent)} / $${String.format("%.0f", cat.budget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cat.spent > cat.budget) Color.Red else Color.Gray
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animRatio)
                                .fillMaxHeight()
                                .background(
                                    color = if (spentRatio >= 1f) Color.Red else catColor,
                                    shape = RoundedCornerShape(5.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryStatsCard(stats: SummaryStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Total Spent", "$${String.format("%.0f", stats.totalSpent)}", PrimaryGreen)
                StatTile("Avg / Day", "$${String.format("%.1f", stats.avgPerDay)}", Color(0xFF2196F3))
                StatTile("Transactions", "${stats.transactionCount}", Color(0xFF9C27B0))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Biggest", "$${String.format("%.0f", stats.biggestExpense)}", Color(0xFFF44336))
                StatTile("Saved", "$${String.format("%.0f", (stats.totalBudget - stats.totalSpent).coerceAtLeast(0.0))}", Color(0xFF4CAF50))
                StatTile("Top Cat.", stats.biggestCategory.take(8), Color(0xFFFF9800))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun TopExpensesCard(expenses: List<ExpenseTransaction>, modifier: Modifier = Modifier) {
    val top = expenses
        .filter { it.amount < 0 }
        .sortedBy { it.amount }
        .take(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Top Expenses", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            if (top.isEmpty()) {
                EmptyChartPlaceholder("No expenses recorded yet")
                return@Column
            }
            top.forEachIndexed { idx, tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tx.category, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Text(
                        text = "$${String.format("%.2f", -tx.amount)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF44336)
                    )
                }
                if (idx < top.lastIndex) HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun MonthlyTrendCard(entries: List<BarEntry>, modifier: Modifier = Modifier) {
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(0.01f) ?: 1f

    var animated by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (animated) 1f else 0f,
        animationSpec = tween(900), label = "trend_anim"
    )
    LaunchedEffect(entries) { animated = true }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Monthly Trend", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))

            if (entries.size < 2) {
                EmptyChartPlaceholder("Need at least 2 days of data")
                return@Column
            }

            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)) {
                val w = size.width
                val h = size.height - 20.dp.toPx()
                val step = w / (entries.size - 1).coerceAtLeast(1)

                val points = entries.mapIndexed { i, e ->
                    Offset(i * step, h - (e.value / maxValue) * h)
                }

                val totalPoints = (points.size * progress).toInt().coerceAtLeast(1)
                val visiblePoints = points.take(totalPoints)

                if (visiblePoints.size > 1) {
                    val path = Path().apply {
                        moveTo(visiblePoints.first().x, h)
                        visiblePoints.forEach { lineTo(it.x, it.y) }
                        lineTo(visiblePoints.last().x, h)
                        close()
                    }
                    drawPath(
                        path,
                        brush = Brush.verticalGradient(
                            colors = listOf(PrimaryGreen.copy(alpha = 0.3f), Color.Transparent),
                            startY = 0f, endY = h
                        )
                    )
                    for (i in 0 until visiblePoints.lastIndex) {
                        drawLine(
                            color = PrimaryGreen,
                            start = visiblePoints[i],
                            end = visiblePoints[i + 1],
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    visiblePoints.forEach { pt ->
                        drawCircle(Color.White, radius = 5.dp.toPx(), center = pt)
                        drawCircle(PrimaryGreen, radius = 3.5.dp.toPx(), center = pt)
                    }
                }
            }

            val labelStep = (entries.size / 7).coerceAtLeast(1)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                entries.filterIndexed { i, _ -> i % labelStep == 0 }.forEach { e ->
                    Text(e.label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun EmptyChartPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}