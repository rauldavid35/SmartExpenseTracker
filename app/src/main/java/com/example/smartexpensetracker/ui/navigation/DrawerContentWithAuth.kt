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

@Composable
fun DrawerContentWithAuth(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            // Header
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
                    Icon(
                        imageVector = Icons.Default.Wallet,
                        contentDescription = "App Icon",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Smart Expense Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Items
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("Home") },
                selected = false,
                onClick = { onNavigate("home") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                label = { Text("Expenses") },
                selected = false,
                onClick = { onNavigate("expenses") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                label = { Text("Heatmap") },
                selected = false,
                onClick = { onNavigate("heatmap") },
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider()

            // Logout Button
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                label = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }
}