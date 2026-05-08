package com.example.smartexpensetracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen

/**
 * Navigation drawer.
 * Pass onExport lambda – the composable that calls it should open ExportBottomSheet.
 */
@Composable
fun DrawerContentWithAuth(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onExport: () -> Unit = {}          // ← NEW
) {
    ModalDrawerSheet(drawerContainerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Wallet, "App Icon", tint = PrimaryGreen, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Smart Expense Tracker", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(24.dp))

            // ── Main navigation items ──────────────────────────────────────
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Home, null) },
                label = { Text("Home") },
                selected = false,
                onClick = { onNavigate("home") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Receipt, null) },
                label = { Text("Expenses") },
                selected = false,
                onClick = { onNavigate("expenses") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                label = { Text("Budget & Dashboards") },
                selected = false,
                onClick = { onNavigate("budget") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.LocationOn, null) },
                label = { Text("Heatmap") },
                selected = false,
                onClick = { onNavigate("heatmap") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.ShoppingCart, null) },
                label = { Text("Shopping Lists") },
                selected = false,
                onClick = { onNavigate("lists") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            // ── Export item ────────────────────────────────────────────────
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Share, null, tint = PrimaryGreen) },
                label = { Text("Export Data", color = PrimaryGreen) },
                selected = false,
                onClick = onExport,
                modifier = Modifier.padding(horizontal = 12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = LightMint
                )
            )

            Spacer(Modifier.weight(1f))
            HorizontalDivider()

            // ── Logout ─────────────────────────────────────────────────────
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                label = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }
}