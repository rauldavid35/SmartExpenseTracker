package com.example.smartexpensetracker.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.BudgetViewModel
import com.example.smartexpensetracker.viewmodel.ExportFormat
import com.example.smartexpensetracker.viewmodel.ExportScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var selectedScope by remember { mutableStateOf(ExportScope.FULL_REPORT) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportIntent.collect { intent ->
            context.startActivity(intent)
            isExporting = false
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Export Data",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Choose what to export and in which format",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            // ── What to export ──────────────────────────────────────────────

            Text("What to export", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportScope.entries.forEach { scope ->
                    val (icon, desc) = scopeMeta(scope)
                    ScopeRow(
                        icon = icon,
                        title = scope.label,
                        description = desc,
                        selected = selectedScope == scope,
                        onClick = { selectedScope = scope }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Format ──────────────────────────────────────────────────────

            Text("File format", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(10.dp))

            // Row 1: recommended (XLSX opens in Excel/Sheets, PDF is printable)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportFormat.XLSX, ExportFormat.PDF, ExportFormat.CSV).forEach { fmt ->
                    FormatChip(label = fmt.name, subtitle = formatSubtitle(fmt),
                        selected = selectedFormat == fmt, modifier = Modifier.weight(1f),
                        onClick = { selectedFormat = fmt })
                }
            }
            Spacer(Modifier.height(8.dp))
            // Row 2: developer/data formats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportFormat.JSON, ExportFormat.XML).forEach { fmt ->
                    FormatChip(label = fmt.name, subtitle = formatSubtitle(fmt),
                        selected = selectedFormat == fmt, modifier = Modifier.weight(1f),
                        onClick = { selectedFormat = fmt })
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))

            // ── Export button ────────────────────────────────────────────────

            Button(
                onClick = {
                    isExporting = true
                    viewModel.buildExportIntent(context, selectedFormat, selectedScope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(16.dp),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export & Share", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// ── Row for scope selection ────────────────────────────────────────────────────

@Composable
private fun ScopeRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) PrimaryGreen else Color(0xFFE5E7EB)
    val bgColor = if (selected) LightMint else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) PrimaryGreen else Color.Gray, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(description, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Format chip ────────────────────────────────────────────────────────────────

@Composable
private fun FormatChip(
    label: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) PrimaryGreen else Color(0xFFE5E7EB)
    val bgColor = if (selected) LightMint else Color.White

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (selected) PrimaryGreen else MaterialTheme.colorScheme.onSurface
            )
        )
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

// ── Metadata helpers ───────────────────────────────────────────────────────────

private fun scopeMeta(scope: ExportScope): Pair<ImageVector, String> = when (scope) {
    ExportScope.EXPENSES_ONLY -> Icons.Default.Receipt to "Raw list of all transactions"
    ExportScope.FULL_REPORT -> Icons.Default.Assessment to "Budget limits + all transactions"
    ExportScope.DASHBOARDS -> Icons.Default.Dashboard to "Dashboard widget configurations"
    ExportScope.EVERYTHING -> Icons.Default.FolderZip to "Full report + dashboard configs"
}

private fun formatSubtitle(fmt: ExportFormat) = when (fmt) {
    ExportFormat.XLSX -> "Excel ★"
    ExportFormat.PDF  -> "Printable ★"
    ExportFormat.CSV  -> "Spreadsheet"
    ExportFormat.JSON -> "Structured"
    ExportFormat.XML  -> "Universal"
}