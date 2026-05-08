package com.example.smartexpensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.model.WidgetType
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen

@Composable
fun CreateDashboardDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, widgets: List<WidgetType>) -> Unit
) {
    var dashboardName by remember { mutableStateOf("") }
    val selectedWidgets = remember { mutableStateListOf<WidgetType>() }
    var nameError by remember { mutableStateOf(false) }
    var widgetError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dashboard, null, tint = PrimaryGreen)
                Spacer(Modifier.width(8.dp))
                Text("New Dashboard", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Name field
                OutlinedTextField(
                    value = dashboardName,
                    onValueChange = { dashboardName = it; nameError = false },
                    label = { Text("Dashboard name") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Enter a name", color = MaterialTheme.colorScheme.error) }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Select Widgets",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (widgetError) {
                    Text("Pick at least one widget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                WidgetType.entries.forEach { widget ->
                    val checked = widget in selectedWidgets
                    WidgetToggleRow(
                        widget = widget,
                        checked = checked,
                        onToggle = {
                            widgetError = false
                            if (checked) selectedWidgets.remove(widget)
                            else selectedWidgets.add(widget)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError = dashboardName.isBlank()
                    widgetError = selectedWidgets.isEmpty()
                    if (!nameError && !widgetError) {
                        onCreate(dashboardName.trim(), selectedWidgets.toList())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun WidgetToggleRow(widget: WidgetType, checked: Boolean, onToggle: () -> Unit) {
    val borderColor = if (checked) PrimaryGreen else Color(0xFFE5E7EB)
    val bgColor = if (checked) LightMint else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryGreen,
                uncheckedColor = Color.Gray
            )
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(widget.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(widget.description, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}