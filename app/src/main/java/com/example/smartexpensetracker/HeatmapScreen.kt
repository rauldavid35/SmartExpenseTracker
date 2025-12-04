package com.example.smartexpensetracker

import android.graphics.Paint
import android.preference.PreferenceManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.ExpensesViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

data class LocationStat(
    val name: String,
    val totalSpent: Double,
    val visitCount: Int,
    val position: GeoPoint
)

@Composable
fun HeatmapScreen(
    onMenuClick: () -> Unit,
    viewModel: ExpensesViewModel = viewModel()
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()

    // 1. Initialize OSMDroid Configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // 2. Logic: Group expenses by location
    val locationStats = remember(expenses) {
        expenses
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .groupBy { it.locationName.ifBlank { "Unknown Location" } }
            .map { (name, list) ->
                LocationStat(
                    name = name,
                    totalSpent = list.sumOf { abs(it.amount) },
                    visitCount = list.size,
                    position = GeoPoint(list[0].latitude, list[0].longitude)
                )
            }
            .sortedByDescending { it.totalSpent }
    }

    val top3Locations = locationStats.take(3)

    // Calculate max spent for scaling intensity
    val maxSpent = locationStats.maxOfOrNull { it.totalSpent } ?: 1.0

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

        // The OpenStreetMap View
        Box(modifier = Modifier.fillMaxWidth().height(350.dp).padding(16.dp).clip(RoundedCornerShape(16.dp))) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(13.0) // Zoom out slightly to see clusters

                        val startPoint = if (locationStats.isNotEmpty()) locationStats[0].position else GeoPoint(46.0, 25.0)
                        controller.setCenter(startPoint)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    locationStats.forEach { stat ->
                        // 1. Create a "Heat" Circle (Polygon)
                        // Size based on spending amount (Log scale to prevent massive circles)
                        val radius = max(100.0, (stat.totalSpent / maxSpent) * 1000.0)

                        // Color based on intensity: Green (Low) -> Yellow -> Red (High)
                        val intensity = (stat.totalSpent / maxSpent).toFloat()
                        val color = getHeatColor(intensity)

                        val circle = Polygon().apply {
                            points = Polygon.pointsAsCircle(stat.position, radius)
                            fillPaint.color = color
                            fillPaint.style = Paint.Style.FILL
                            outlinePaint.color = android.graphics.Color.TRANSPARENT // Corrected from strokePaint
                            title = "${stat.name} ($${String.format("%.0f", stat.totalSpent)})"
                        }

                        // 2. Add a standard marker on top for precise clicking
                        val marker = Marker(mapView)
                        marker.position = stat.position
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "${stat.name}\nTotal: $${String.format("%.2f", stat.totalSpent)}"

                        marker.setOnMarkerClickListener { m, _ ->
                            m.showInfoWindow()
                            true
                        }

                        // Add Heat Circle FIRST (background), then Marker (foreground)
                        mapView.overlays.add(circle)
                        mapView.overlays.add(marker)
                    }

                    mapView.invalidate()
                }
            )
        }

        Text("Highest Spending Zones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

        // Top 3 List
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(top3Locations) { loc ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Dynamic color icon based on spend
                        val intensity = (loc.totalSpent / maxSpent).toFloat()
                        val colorInt = getHeatColor(intensity)

                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(colorInt) // Convert Android Color int to Compose Color
                        )
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

// Helper to calculate Color based on intensity (0.0 to 1.0)
// Returns an Android Graphics Color Int
fun getHeatColor(intensity: Float): Int {
    // 0.0 = Green (Low spend)
    // 0.5 = Yellow
    // 1.0 = Red (High spend)

    // Simple interpolation
    val r = (intensity * 255).toInt().coerceIn(0, 255)
    val g = ((1 - intensity) * 255).toInt().coerceIn(0, 255)

    // Add transparency (Alpha = 100 out of 255) to allow map to show through
    return android.graphics.Color.argb(100, r, g, 0)
}