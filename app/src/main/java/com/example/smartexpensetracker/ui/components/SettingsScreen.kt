package com.example.smartexpensetracker.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.NotificationHelper
import com.example.smartexpensetracker.utils.UserPreferences
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SettingsScreen(onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val uid     = FirebaseAuth.getInstance().currentUser?.uid ?: "local"
    val prefs   = remember(uid) { UserPreferences(context, uid) }

    val darkMode       by prefs.darkMode.collectAsState()
    val privacyMode    by prefs.privacyMode.collectAsState()
    val budgetResetDay by prefs.budgetResetDay.collectAsState()
    val notifyOnReset  by prefs.notifyOnReset.collectAsState()

    var showDayPicker by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, null, tint = PrimaryGreen)
                }
                Spacer(Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            // ── Budget cycle ──────────────────────────────────────────────────
            SectionHeader("Budget Cycle")

            SettingsRow(
                icon    = Icons.Default.CalendarMonth,
                title   = "Budget Reset Day",
                subtitle = "Day $budgetResetDay of every month",
                trailing = {
                    TextButton(onClick = { showDayPicker = true }) {
                        Text("Change", color = PrimaryGreen)
                    }
                }
            )

            SettingsToggleRow(
                icon     = Icons.Default.Notifications,
                title    = "Notify on Budget Reset",
                subtitle = "Get a notification when the new cycle starts",
                checked  = notifyOnReset,
                onToggle = { enabled ->
                    prefs.setNotifyOnReset(enabled)
                    if (enabled) {
                        NotificationHelper.scheduleBudgetResetAlarm(context, budgetResetDay)
                    } else {
                        NotificationHelper.cancelBudgetResetAlarm(context)
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── Privacy & display ─────────────────────────────────────────────
            SectionHeader("Privacy & Display")

            SettingsToggleRow(
                icon     = Icons.Default.VisibilityOff,
                title    = "Privacy Mode",
                subtitle = "Mask balances with ••••",
                checked  = privacyMode,
                onToggle = { prefs.setPrivacyMode(it) }
            )

            SettingsToggleRow(
                icon     = Icons.Default.DarkMode,
                title    = "Dark Mode",
                subtitle = "Switch to dark theme",
                checked  = darkMode,
                onToggle = { prefs.setDarkMode(it) }
            )
        }
    }

    // ── Day picker dialog (1–28) ──────────────────────────────────────────────
    if (showDayPicker) {
        ResetDayPickerDialog(
            current  = budgetResetDay,
            onSelect = { day ->
                prefs.setBudgetResetDay(day)
                if (notifyOnReset) NotificationHelper.scheduleBudgetResetAlarm(context, day)
                showDayPicker = false
            },
            onDismiss = { showDayPicker = false }
        )
    }
}

// ── Reset Day Picker ──────────────────────────────────────────────────────────

@Composable
private fun ResetDayPickerDialog(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableIntStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget Reset Day") },
        text  = {
            Column {
                Text("Pick the day of the month your budget resets (1–28).",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Text("Day: $selected", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Slider(
                    value         = selected.toFloat(),
                    onValueChange = { selected = it.toInt() },
                    valueRange    = 1f..28f,
                    steps         = 26,
                    colors        = SliderDefaults.colors(thumbColor = PrimaryGreen, activeTrackColor = PrimaryGreen)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("28", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selected) }) { Text("Save", color = PrimaryGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Row composables ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        color      = PrimaryGreen,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            trailing()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle, trailing = {
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryGreen)
        )
    })
}