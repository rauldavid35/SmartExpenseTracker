package com.example.smartexpensetracker.ui.screens.heatmap

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

data class LocationStat(
    val name: String,
    val totalSpent: Double,
    val visitCount: Int,
    val position: GeoPoint
)

// Sursa de hartă minimalistă și gratuită (CartoDB Positron)
val CARTO_POSITRON = object : XYTileSource(
    "CartoPositron",
    1, 20, 256, ".png",
    arrayOf("https://a.basemaps.cartocdn.com/light_all/")
) {
    override fun getCopyrightNotice(): String = "© OpenStreetMap, © CARTO"
}

// Overlay personalizat pentru un Heatmap cu efect de "Gradient" fin
class ModernHeatmapOverlay(
    private val locations: List<LocationStat>,
    private val maxSpent: Double
) : Overlay() {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || locations.isEmpty()) return
        val projection = osmv.projection

        locations.forEach { stat ->
            val point = Point()
            projection.toPixels(stat.position, point)

            // Raza zonei de căldură pe ecran (în pixeli)
            val radius = 100f + (stat.totalSpent / maxSpent).toFloat() * 150f

            // Calculăm culoarea (Verde pentru ieftin, Roșu pentru scump)
            val intensity = (stat.totalSpent / maxSpent).toFloat()
            val r = (intensity * 255).toInt().coerceIn(0, 255)
            val g = ((1 - intensity) * 255).toInt().coerceIn(0, 255)

            // Centrul e opac (alpha 180), marginile sunt complet transparente (alpha 0)
            val centerColor = android.graphics.Color.argb(180, r, g, 0)
            val edgeColor = android.graphics.Color.argb(0, r, g, 0)

            paint.shader = RadialGradient(
                point.x.toFloat(), point.y.toFloat(), radius,
                centerColor, edgeColor,
                Shader.TileMode.CLAMP
            )

            c.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, paint)
        }
    }
}

@Composable
fun HeatmapScreen(
    onMenuClick: () -> Unit,
    viewModel: ExpensesViewModel = viewModel()
) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val locationStats = remember(expenses) {
        expenses
            .filter { tx ->
                tx.amount < 0 &&              // expenses only — income excluded
                        tx.latitude  != 0.0 &&        // must have real coordinates
                        tx.longitude != 0.0           // lat=0/lng=0 means user skipped location
            }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp) // Am mărit puțin harta să arate mai impunător
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp)) // Colțuri mai rotunjite
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(CARTO_POSITRON) // AICI FOLOSIM HARTA PREMIUM
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)

                        val startPoint = if (locationStats.isNotEmpty()) locationStats[0].position else GeoPoint(45.75, 21.23) // Timișoara default
                        controller.setCenter(startPoint)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // 1. Adăugăm stratul de Heatmap (pete de culoare fine)
                    if (locationStats.isNotEmpty()) {
                        mapView.overlays.add(ModernHeatmapOverlay(locationStats, maxSpent))
                    }

                    // 2. Adăugăm markere curate peste heatmap
                    locationStats.forEach { stat ->
                        val marker = Marker(mapView)
                        marker.position = stat.position
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "${stat.name}\nTotal: $${String.format("%.2f", stat.totalSpent)}"

                        // Iconiță implicită mică ca să nu acopere culorile
                        marker.setOnMarkerClickListener { m, _ ->
                            m.showInfoWindow()
                            true
                        }
                        mapView.overlays.add(marker)
                    }

                    mapView.invalidate()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Highest Spending Zones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

        // Top 3 List
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(top3Locations) { loc ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val intensity = (loc.totalSpent / maxSpent).toFloat()
                        val r = (intensity * 255).toInt().coerceIn(0, 255)
                        val g = ((1 - intensity) * 255).toInt().coerceIn(0, 255)

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(android.graphics.Color.argb(50, r, g, 0))), // Fundal pal
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(android.graphics.Color.argb(255, r, g, 0)) // Iconiță intensă
                            )
                        }

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