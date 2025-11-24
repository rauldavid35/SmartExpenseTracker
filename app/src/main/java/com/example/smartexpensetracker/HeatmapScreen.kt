package com.example.smartexpensetracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.ExpensesViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlin.math.abs

data class LocationStat(
    val name: String,
    val totalSpent: Double,
    val visitCount: Int,
    val position: LatLng
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapScreen(
    onMenuClick: () -> Unit,
    viewModel: ExpensesViewModel = viewModel()
) {
    val expenses by viewModel.expenses.collectAsState()

    // Logic: Group expenses by location
    val locationStats = remember(expenses) {
        expenses
            .filter { it.latitude != 0.0 && it.longitude != 0.0 } // Only ones with location
            .groupBy { it.locationName.ifBlank { "Unknown Location" } } // Group by Name
            .map { (name, list) ->
                LocationStat(
                    name = name,
                    totalSpent = list.sumOf { abs(it.amount) },
                    visitCount = list.size,
                    // Just take the first coordinate found for this location name
                    position = LatLng(list[0].latitude, list[0].longitude)
                )
            }
            .sortedByDescending { it.totalSpent } // Sort for Top 3
    }

    val top3Locations = locationStats.take(3)

    // Initial Camera (Center on first location or Default to Romania)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            if (locationStats.isNotEmpty()) locationStats[0].position else LatLng(46.0, 25.0),
            12f
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(PrimaryGreen)) {
                Icon(Icons.Default.Wallet, "Menu", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Spending Heatmap", style = MaterialTheme.typography.headlineSmall)
        }

        // The Map
        Box(modifier = Modifier.fillMaxWidth().height(350.dp).padding(16.dp).clip(RoundedCornerShape(16.dp))) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Add Markers for all locations
                locationStats.forEach { stat ->
                    Marker(
                        state = MarkerState(position = stat.position),
                        title = stat.name,
                        snippet = "Spent: $${String.format("%.2f", stat.totalSpent)}"
                    )
                }
            }
        }

        Text("Top 3 Most Visited", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

        // Top 3 List
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(top3Locations) { loc ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = PrimaryGreen)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(loc.name, style = MaterialTheme.typography.titleMedium)
                            Text("${loc.visitCount} visits", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("$${String.format("%.2f", loc.totalSpent)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}