package com.example.smartexpensetracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.smartexpensetracker.model.ProductResult
import com.example.smartexpensetracker.model.StoreLink
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary

@Composable
fun ProductScanDialog(
    result: ProductResult,
    onAddAsExpense: (name: String, price: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier  = Modifier.fillMaxWidth().padding(16.dp),
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier         = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ShoppingBag, null, tint = PrimaryGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Product Identified",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel("Product")
                Text(
                    text  = result.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                SectionLabel("Estimated Price")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "${result.currency} ${"%.2f".format(result.estimatedPrice)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ConfidenceBadge(result.priceConfidence)
                }

                if (result.priceConfidence == "low") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFF57C00), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Price is an estimate. Check store links for the current market price.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF57C00)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel("Where to Buy")
                Spacer(modifier = Modifier.height(6.dp))

                result.storeLinks.forEach { link ->
                    StoreLinkRow(link = link, context = context)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick  = { onAddAsExpense(result.productName, result.estimatedPrice) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add as Expense")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreLinkRow(link: StoreLink, context: Context) {
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8F9FA))
            .padding(12.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Store, null,
                tint     = PrimaryGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text     = link.storeName,
                style    = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick  = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.searchUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.OpenInNew, "Open in browser",
                    tint     = PrimaryGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick  = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip      = ClipData.newPlainText("Product Search URL", link.searchUrl)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector        = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy URL",
                    tint               = if (copied) Color(0xFF4CAF50) else TextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text     = link.searchUrl,
            style    = MaterialTheme.typography.bodySmall,
            color    = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (copied) {
            LaunchedEffect(copied) {
                kotlinx.coroutines.delay(2000)
                copied = false
            }
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    val isHigh = confidence == "high"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isHigh) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text  = if (isHigh) "✓ Accurate" else "~ Estimate",
            style = MaterialTheme.typography.labelSmall,
            color = if (isHigh) Color(0xFF2E7D32) else Color(0xFFF57C00)
        )
    }
}