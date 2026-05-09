package com.example.smartexpensetracker.ui.screens.expenses

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.model.ExpenseTransaction
import com.example.smartexpensetracker.model.ProductResult
import com.example.smartexpensetracker.ui.components.CameraPreview
import com.example.smartexpensetracker.ui.components.LocationConsentDialog
import com.example.smartexpensetracker.ui.components.ProductScanDialog
import com.example.smartexpensetracker.ui.components.VoiceInputDialog
import com.example.smartexpensetracker.ui.components.VoiceParseMode
import com.example.smartexpensetracker.ui.components.takePhoto
import com.example.smartexpensetracker.ui.theme.*
import com.example.smartexpensetracker.utils.GeminiReceiptParser
import com.example.smartexpensetracker.utils.LocationHelper
import com.example.smartexpensetracker.utils.VoiceParser
import com.example.smartexpensetracker.utils.VoiceParseResult
import com.example.smartexpensetracker.viewmodel.ExpensesViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ExpensesScreenWithFirebase(
    onMenuClick: () -> Unit,
    viewModel: ExpensesViewModel = viewModel(),
    externalVoiceTrigger: Boolean = false,
    externalCameraTrigger: Boolean = false,
    onExternalTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val expenses             by viewModel.expenses.collectAsState()
    val availableCategories  by viewModel.categories.collectAsState()
    val isLoading            by viewModel.isLoading.collectAsState()

    val geminiParser  = remember { GeminiReceiptParser("AIzaSyBB-OaS2PDQkEv4XvRoPOAsAIQ9_PiL6RM") }
    val locationHelper = remember { LocationHelper(context) }
    val voiceParser   = remember { VoiceParser(apiKey = "AIzaSyBB-OaS2PDQkEv4XvRoPOAsAIQ9_PiL6RM") }

    val cameraPermission   = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val imageCapture   = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // ── Standard UI state ─────────────────────────────────────────────────────
    var showAddDialog        by remember { mutableStateOf(false) }
    var showLocationDialog   by remember { mutableStateOf(false) }
    var showCamera           by remember { mutableStateOf(false) }
    var showScannedTextDialog by remember { mutableStateOf(false) }
    var isProcessingAI       by remember { mutableStateOf(false) }
    var showVoiceInput       by remember { mutableStateOf(false) }
    var isReceiptMode        by remember { mutableStateOf(true) }

    // ── Voice state ───────────────────────────────────────────────────────────
    var voiceParseResult by remember { mutableStateOf<VoiceParseResult?>(null) }
    var isVoiceParsing   by remember { mutableStateOf(false) }

    // ── Location / Photon state ───────────────────────────────────────────────
    val searchResults      by viewModel.searchResults.collectAsState()
    val currentSearchQuery by viewModel.searchQuery.collectAsState()
    var useCurrentLocation by remember { mutableStateOf(true) }
    var lat          by remember { mutableStateOf(0.0) }
    var lng          by remember { mutableStateOf(0.0) }
    var locationName by remember { mutableStateOf("") }

    // ── Form data ─────────────────────────────────────────────────────────────
    var name                 by remember { mutableStateOf("") }
    var amount               by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("None") }
    var isExpense            by remember { mutableStateOf(true) }
    var scannedRawText       by remember { mutableStateOf("") }
    var detectedAddress      by remember { mutableStateOf("") }
    var selectedExpense      by remember { mutableStateOf<ExpenseTransaction?>(null) }
    val isEditing = selectedExpense != null
    var showAnomalyDialog by remember { mutableStateOf(false) }
    var anomalyAverage    by remember { mutableStateOf(0.0) }

    // ── Product scan state ────────────────────────────────────────────────────
    var productScanResult     by remember { mutableStateOf<ProductResult?>(null) }
    var showProductScanDialog by remember { mutableStateOf(false) }

    // Location consent for product scan:
    //   null  = not yet asked → show the consent dialog
    //   true  = user allowed → resolve GPS → local stores
    //   false = user declined → international stores (Amazon, eBay)
    var locationConsentGranted    by remember { mutableStateOf<Boolean?>(null) }
    var showLocationConsentDialog by remember { mutableStateOf(false) }
    var pendingProductUri         by remember { mutableStateOf<Uri?>(null) }

    // ── External triggers (shake / volume keys) ───────────────────────────────
    LaunchedEffect(externalVoiceTrigger) {
        if (externalVoiceTrigger) { showVoiceInput = true; onExternalTriggerHandled() }
    }
    LaunchedEffect(externalCameraTrigger) {
        if (externalCameraTrigger) {
            if (cameraPermission.status.isGranted) showCamera = true
            else cameraPermission.launchPermissionRequest()
            onExternalTriggerHandled()
        }
    }

    // ── Voice parsing ─────────────────────────────────────────────────────────
    fun processVoiceCommand(transcript: String) {
        if (transcript.isBlank()) { voiceParseResult = null; isVoiceParsing = false; return }
        isVoiceParsing = true; voiceParseResult = null
        scope.launch {
            val result = voiceParser.parseExpense(transcript, availableCategories)
            voiceParseResult = result; isVoiceParsing = false
        }
    }

    // ── Product scan launcher ─────────────────────────────────────────────────
    // Resolves GPS → countryIso via Geocoder, then calls the updated processImage.
    // Called after the user answers the consent dialog AND from the camera shutter.
    fun launchProductScan(uri: Uri) {
        isProcessingAI = true
        scope.launch {
            val resolvedCountryIso: String? = if (locationConsentGranted == true) {
                try {
                    val coords = locationHelper.getCurrentLocation()
                    if (coords != null) {
                        @Suppress("DEPRECATION")
                        val addresses = Geocoder(context, Locale.ENGLISH)
                            .getFromLocation(coords.first, coords.second, 1)
                        addresses?.firstOrNull()?.countryCode   // e.g. "RO"
                    } else null
                } catch (e: Exception) { null }
            } else {
                null  // declined → international
            }

            processImage(
                context           = context,
                uri               = uri,
                isReceiptMode     = false,
                geminiParser      = geminiParser,
                scope             = scope,
                countryIso        = resolvedCountryIso,
                onLoading         = { /* already set above */ },
                onReceiptComplete = { _, _, _, _ -> },  // not used in product mode
                onProductComplete = { result ->
                    isProcessingAI = false
                    if (result != null) {
                        productScanResult     = result
                        showProductScanDialog = true
                    } else {
                        scannedRawText        = "Could not identify product"
                        showScannedTextDialog = true
                    }
                }
            )
        }
    }

    // ── Gallery launcher ──────────────────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult

        if (!isReceiptMode) {
            // Product scan → ask for location consent once, then scan
            pendingProductUri = uri
            if (locationConsentGranted == null) {
                showLocationConsentDialog = true
            } else {
                launchProductScan(uri)
            }
        } else {
            // Receipt scan → straight through, no consent needed
            processImage(
                context           = context,
                uri               = uri,
                isReceiptMode     = true,
                geminiParser      = geminiParser,
                scope             = scope,
                countryIso        = null,
                onLoading         = { isProcessingAI = true },
                onReceiptComplete = { merchant, address, total, raw ->
                    isProcessingAI = false
                    if (merchant != null) {
                        name = merchant; detectedAddress = address ?: ""
                        amount = total?.toString() ?: ""; showAddDialog = true
                    } else { scannedRawText = raw; showScannedTextDialog = true }
                },
                onProductComplete = {}
            )
        }
    }

    val totalExpenses = expenses.filter { it.amount < 0 }.sumOf { -it.amount }
    val totalIncome   = expenses.filter { it.amount > 0 }.sumOf { it.amount }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick  = onMenuClick,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(PrimaryGreen)
                ) { Icon(Icons.Default.Wallet, "Menu", tint = Color.White) }

                Text("Expenses", style = MaterialTheme.typography.headlineSmall)

                Row {
                    IconButton(
                        onClick  = { voiceParseResult = null; isVoiceParsing = false; showVoiceInput = true },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LightMint)
                    ) { Icon(Icons.Default.Mic, "Voice", tint = PrimaryGreen) }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (cameraPermission.status.isGranted) showCamera = true
                            else cameraPermission.launchPermissionRequest()
                        },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.LightGray.copy(alpha = 0.3f))
                    ) { Icon(Icons.Default.CameraAlt, "Scan", tint = PrimaryGreen) }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = {
                            selectedExpense = null; name = ""; amount = ""
                            selectedCategoryName = availableCategories.firstOrNull() ?: "None"
                            isExpense = true; detectedAddress = ""; showAddDialog = true
                        },
                        containerColor = PrimaryGreen,
                        modifier       = Modifier.size(48.dp)
                    ) { Icon(Icons.Default.Add, "Add", tint = Color.White) }
                }
            }

            // Summary cards
            Row(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Expenses", color = TextSecondary)
                        Text("$${String.format("%.2f", totalExpenses)}", color = ExpenseRed, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Income", color = TextSecondary)
                        Text("$${String.format("%.2f", totalIncome)}", color = IncomeGreen, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            if (isLoading) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            // Expense list
            LazyColumn(
                contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses) { transaction ->
                    Card(
                        colors   = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.clickable {
                            selectedExpense = transaction
                            name = transaction.name
                            amount = abs(transaction.amount).toString()
                            selectedCategoryName = transaction.category
                            isExpense = transaction.amount < 0
                            detectedAddress = transaction.locationName
                            showAddDialog = true
                        }
                    ) {
                        Row(
                            modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(transaction.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${transaction.category}${if (transaction.locationName.isNotEmpty()) " • ${transaction.locationName}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text  = "${if (transaction.amount >= 0) "+" else ""}$${String.format("%.2f", transaction.amount)}",
                                color = if (transaction.amount >= 0) IncomeGreen else ExpenseRed,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // ── DIALOGS ───────────────────────────────────────────────────────────

        // Voice input
        if (showVoiceInput) {
            VoiceInputDialog(
                mode          = VoiceParseMode.EXPENSE,
                parseResult   = voiceParseResult,
                isParsing     = isVoiceParsing,
                onTextReceived = { text -> processVoiceCommand(text) },
                onResultConfirmed = { result ->
                    if (result is VoiceParseResult.ExpenseResult) {
                        name                 = result.name
                        amount               = result.amount?.toString() ?: ""
                        selectedCategoryName = result.category ?: availableCategories.firstOrNull() ?: "None"
                        detectedAddress      = result.location ?: ""
                        isExpense            = true
                        showVoiceInput = false; voiceParseResult = null; isVoiceParsing = false
                        showAddDialog  = true
                    }
                },
                onDismiss = { showVoiceInput = false; voiceParseResult = null; isVoiceParsing = false }
            )
        }

        // Location consent — shown once before the first product scan
        if (showLocationConsentDialog) {
            LocationConsentDialog(
                onAllow = {
                    locationConsentGranted    = true
                    showLocationConsentDialog = false
                    pendingProductUri?.let { launchProductScan(it) }
                },
                onDecline = {
                    locationConsentGranted    = false
                    showLocationConsentDialog = false
                    pendingProductUri?.let { launchProductScan(it) }
                },
                onDismiss = {
                    // Treat dismiss as decline so the scan still proceeds
                    locationConsentGranted    = false
                    showLocationConsentDialog = false
                    pendingProductUri?.let { launchProductScan(it) }
                }
            )
        }

        // Product scan result
        if (showProductScanDialog && productScanResult != null) {
            ProductScanDialog(
                result = productScanResult!!,
                onAddAsExpense = { productName, price ->
                    name                 = productName
                    amount               = price.toString()
                    isExpense            = true
                    selectedCategoryName = availableCategories.firstOrNull() ?: "Shopping"
                    showProductScanDialog = false
                    productScanResult     = null
                    showAddDialog         = true
                },
                onDismiss = { showProductScanDialog = false; productScanResult = null }
            )
        }

        // Add / Edit expense form
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false; selectedExpense = null },
                title = { Text(if (isEditing) "Edit Transaction" else "Add Transaction") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Name / Details") },
                            modifier = Modifier.fillMaxWidth(), maxLines = 4
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it },
                            label = { Text("Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // ── Expense / Income toggle ───────────────────────────────────
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isExpense,
                                onCheckedChange = { nowExpense ->
                                    isExpense = nowExpense
                                    // Reset to "Income" when switching to income so no
                                    // budget category is accidentally pre-selected.
                                    if (!nowExpense) selectedCategoryName = "Income"
                                }
                            )
                            Text(if (isExpense) "Expense" else "Income")
                        }

                        if (isExpense) {
                            // ── EXPENSE: mandatory category picker (unchanged) ────────────
                            Spacer(modifier = Modifier.height(8.dp))
                            var expanded by remember { mutableStateOf(false) }
                            var showNewCategoryField by remember { mutableStateOf(false) }
                            var newCategoryName by remember { mutableStateOf("") }

                            if (!showNewCategoryField) {
                                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                    OutlinedTextField(
                                        value = selectedCategoryName, onValueChange = {},
                                        readOnly = true, label = { Text("Select Category") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        availableCategories.forEach { category ->
                                            DropdownMenuItem(
                                                text    = { Text(category) },
                                                onClick = { selectedCategoryName = category; expanded = false }
                                            )
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        DropdownMenuItem(
                                            text    = { Text("+ Add New Category", color = PrimaryGreen, style = MaterialTheme.typography.labelLarge) },
                                            onClick = { showNewCategoryField = true; expanded = false }
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier          = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newCategoryName, onValueChange = { newCategoryName = it },
                                        label = { Text("New Category Name") },
                                        modifier = Modifier.weight(1f), singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = {
                                        if (newCategoryName.isNotBlank()) {
                                            fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
                                                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                                                .lowercase().trim()
                                            fun levenshtein(s1: String, s2: String): Int {
                                                val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
                                                for (i in 0..s1.length) dp[i][0] = i
                                                for (j in 0..s2.length) dp[0][j] = j
                                                for (i in 1..s1.length) for (j in 1..s2.length) {
                                                    val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                                                    dp[i][j] = minOf(dp[i-1][j]+1, dp[i][j-1]+1, dp[i-1][j-1]+cost)
                                                }
                                                return dp[s1.length][s2.length]
                                            }
                                            val normalizedNew = normalize(newCategoryName)
                                            val existingSimilar = availableCategories.find { existing ->
                                                val ne = normalize(existing)
                                                val dist = levenshtein(normalizedNew, ne)
                                                val commonPrefix = normalizedNew.take(4) == ne.take(4) && normalizedNew.length >= 4
                                                normalizedNew == ne || normalizedNew.contains(ne) ||
                                                        ne.contains(normalizedNew) || (dist <= 3 && commonPrefix)
                                            }
                                            if (existingSimilar != null) {
                                                Toast.makeText(context, "⚠️ Category '$existingSimilar' already exists!", Toast.LENGTH_LONG).show()
                                                selectedCategoryName = existingSimilar
                                                showNewCategoryField = false; newCategoryName = ""
                                            } else {
                                                viewModel.addCustomCategory(newCategoryName)
                                                selectedCategoryName = newCategoryName
                                                showNewCategoryField = false; newCategoryName = ""
                                            }
                                        }
                                    }) { Icon(Icons.Default.Check, "Add", tint = PrimaryGreen) }
                                    IconButton(onClick = { showNewCategoryField = false }) {
                                        Icon(Icons.Default.Close, "Cancel", tint = Color.Gray)
                                    }
                                }
                            }

                        } else {
                            // ── INCOME: optional category budget boost ────────────────────
                            //
                            // Income ALWAYS boosts the general budget total automatically.
                            // The user can OPTIONALLY also pick a specific budget category
                            // to extend that category's spending limit by this income amount.
                            //
                            // selectedCategoryName == "Income"   → general budget only
                            // selectedCategoryName == <cat name> → general budget + that category limit
                            Spacer(modifier = Modifier.height(12.dp))

                            // Info hint card
                            Card(
                                colors   = CardDefaults.cardColors(containerColor = LightMint),
                                shape    = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier          = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint     = PrimaryGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "This income is always added to your general budget. " +
                                                "You can also choose to extend a specific category's limit.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrimaryGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Optional category boost dropdown
                            var incomeExpanded by remember { mutableStateOf(false) }
                            val noneLabel = "General budget only"
                            val displayValue = if (selectedCategoryName == "Income") noneLabel else selectedCategoryName

                            ExposedDropdownMenuBox(
                                expanded         = incomeExpanded,
                                onExpandedChange = { incomeExpanded = !incomeExpanded }
                            ) {
                                OutlinedTextField(
                                    value         = displayValue,
                                    onValueChange = {},
                                    readOnly      = true,
                                    label         = { Text("Also boost category (optional)") },
                                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = incomeExpanded) },
                                    modifier      = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded         = incomeExpanded,
                                    onDismissRequest = { incomeExpanded = false }
                                ) {
                                    // "None" — general budget only
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint     = PrimaryGreen,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(noneLabel)
                                            }
                                        },
                                        onClick = { selectedCategoryName = "Income"; incomeExpanded = false }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    // All budget categories except "Income" itself
                                    availableCategories.filter { it != "Income" }.forEach { cat ->
                                        DropdownMenuItem(
                                            text    = { Text(cat) },
                                            onClick = { selectedCategoryName = cat; incomeExpanded = false }
                                        )
                                    }
                                }
                            }

                            // Confirmation hint when a category is chosen
                            if (selectedCategoryName != "Income") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "✓ Will also extend the \"$selectedCategoryName\" budget by $${amount.ifBlank { "0" }}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryGreen
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (isEditing) {
                            TextButton(
                                onClick = {
                                    selectedExpense?.let { viewModel.deleteExpense(it.id) }
                                    showAddDialog = false; selectedExpense = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    // Reset coords — GPS is only fetched inside the location
                                    // dialog if the user explicitly picks "Use GPS".
                                    // This prevents real coordinates from leaking into
                                    // transactions where the user chose "Skip" or "Search".
                                    lat = 0.0; lng = 0.0; locationName = ""
                                    val amountVal = amount.toDoubleOrNull() ?: 0.0
                                    if (isExpense && amountVal > 0) {
                                        val anomaly = viewModel.checkAnomaly(amountVal, selectedCategoryName)
                                        if (anomaly.isAnomaly) {
                                            anomalyAverage = anomaly.average
                                            showAddDialog  = false
                                            showAnomalyDialog = true
                                            return@launch
                                        }
                                    }
                                    showAddDialog = false; showLocationDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) { Text("Next") }
                    }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false; selectedExpense = null }) { Text("Cancel") } }
            )
        }

        // Location dialog
        // Location / Photon dialog
        if (showLocationDialog) {
            // locationMode:
            //   "gps"    → fetch current GPS on Save
            //   "search" → Photon text search, optionally biased to vicinity
            //   "skip"   → save with lat=0, lng=0 (excluded from heatmap)
            var locationMode    by remember { mutableStateOf("skip") }
            var searchNearMe    by remember { mutableStateOf(false) }   // vicinity toggle
            var isFetchingGps   by remember { mutableStateOf(false) }
            var isLoadingVicinity by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                title = { Text("Set Location") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Where did you make this transaction?",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // ── Option 1: GPS ────────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { locationMode = "gps" }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = locationMode == "gps",
                                onClick  = { locationMode = "gps" },
                                colors   = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text("Use my current GPS location",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("Precise – fetched on Save",
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }

                        // ── Option 2: Search ─────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { locationMode = "search" }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = locationMode == "search",
                                onClick  = { locationMode = "search" },
                                colors   = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                            )
                            Text("Search for a place",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp))
                        }

                        if (locationMode == "search") {
                            Spacer(modifier = Modifier.height(4.dp))

                            // ── "Search near me" vicinity toggle ─────────────
                            Card(
                                colors   = CardDefaults.cardColors(containerColor = LightMint),
                                shape    = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = searchNearMe,
                                        onCheckedChange = { wantNearby ->
                                            searchNearMe = wantNearby
                                            if (wantNearby) {
                                                // Fetch GPS once to bias Photon – user opted in
                                                isLoadingVicinity = true
                                                scope.launch {
                                                    val coords = locationHelper.getCurrentLocation()
                                                    if (coords != null) {
                                                        viewModel.updateLastLocation(coords.first, coords.second)
                                                    }
                                                    isLoadingVicinity = false
                                                    // Re-trigger search with new coords
                                                    if (currentSearchQuery.length >= 3) {
                                                        viewModel.onSearchQueryChanged(currentSearchQuery + " ")
                                                        viewModel.onSearchQueryChanged(currentSearchQuery)
                                                    }
                                                }
                                            } else {
                                                // Reset to global (0,0)
                                                viewModel.updateLastLocation(0.0, 0.0)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                                    )
                                    if (isLoadingVicinity) {
                                        CircularProgressIndicator(
                                            modifier    = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color       = PrimaryGreen
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(Icons.Default.LocationOn, null,
                                            tint     = PrimaryGreen,
                                            modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        if (searchNearMe) "Searching near your location"
                                        else              "Search near me (uses GPS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PrimaryGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value         = currentSearchQuery,
                                onValueChange = { viewModel.onSearchQueryChanged(it) },
                                label         = { Text("Search shop (ex: Lidl, Mall...)") },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true
                            )

                            if (searchResults.isNotEmpty()) {
                                Card(
                                    modifier  = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .padding(top = 4.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    LazyColumn {
                                        items(searchResults) { spot ->
                                            ListItem(
                                                headlineContent   = { Text(spot.name) },
                                                supportingContent = {
                                                    Text("${spot.city ?: ""} ${spot.street ?: ""}")
                                                },
                                                modifier = Modifier.clickable {
                                                    lat          = spot.latitude
                                                    lng          = spot.longitude
                                                    locationName = spot.name
                                                    viewModel.onSearchQueryChanged(spot.name)
                                                    viewModel.clearLocationSearch()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (lat != 0.0 && locationName.isNotBlank()) {
                                Text(
                                    "📍 $locationName",
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = PrimaryGreen,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        // ── Option 3: Skip ───────────────────────────────────
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { locationMode = "skip" }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = locationMode == "skip",
                                onClick  = { locationMode = "skip" },
                                colors   = RadioButtonDefaults.colors(selectedColor = PrimaryGreen)
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text("No location",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("Won't appear on heatmap",
                                    style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                },
                confirmButton = {
                    // Disable Save while GPS/vicinity is loading, or if Search
                    // is selected but no result has been tapped yet.
                    val saveEnabled = !isFetchingGps && !isLoadingVicinity &&
                            (locationMode != "search" || (lat != 0.0 && locationName.isNotBlank()))

                    Button(
                        enabled = saveEnabled,
                        onClick = {
                            scope.launch {
                                when (locationMode) {
                                    "gps" -> {
                                        isFetchingGps = true
                                        val coords = locationHelper.getCurrentLocation()
                                        isFetchingGps = false
                                        if (coords != null) {
                                            lat          = coords.first
                                            lng          = coords.second
                                            locationName = "Current Location"
                                            viewModel.updateLastLocation(lat, lng)
                                        } else {
                                            lat = 0.0; lng = 0.0; locationName = ""
                                        }
                                    }
                                    "skip" -> { lat = 0.0; lng = 0.0; locationName = "" }
                                    // "search": lat/lng/locationName already set by the row tap
                                }
                                saveTransaction(
                                    viewModel       = viewModel,
                                    selectedExpense = selectedExpense,
                                    name            = name,
                                    amountStr       = amount,
                                    category        = selectedCategoryName,
                                    isExpense       = isExpense,
                                    locationName    = locationName,
                                    lat             = lat,
                                    lng             = lng
                                )
                                showLocationDialog = false
                                selectedExpense    = null
                                name = ""; amount = ""; selectedCategoryName = "General"
                                viewModel.clearLocationSearch()
                                // Reset vicinity bias so next transaction starts clean
                                viewModel.updateLastLocation(0.0, 0.0)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        if (isFetchingGps) {
                            CircularProgressIndicator(
                                color       = Color.White,
                                modifier    = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationDialog = false }) { Text("Back") }
                }
            )
        }

        // Scanned text fallback
        if (showScannedTextDialog) {
            AlertDialog(
                onDismissRequest = { showScannedTextDialog = false },
                title = { Text("Verify Data") },
                text = {
                    Column {
                        Text("Could not auto-detect info.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = scannedRawText, onValueChange = { scannedRawText = it },
                            label = { Text("Raw Info") }, modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { name = scannedRawText; showScannedTextDialog = false; showAddDialog = true }) {
                        Text("Use This")
                    }
                }
            )
        }

        // AI loading spinner
        if (isProcessingAI) {
            Dialog(onDismissRequest = {}) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryGreen)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("AI is analyzing...")
                    }
                }
            }
        }

        // Camera overlay
        if (showCamera) {
            Dialog(
                onDismissRequest = { showCamera = false },
                properties       = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    CameraPreview(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)

                    // Receipt / Product toggle
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { isReceiptMode = true },
                            colors  = ButtonDefaults.buttonColors(containerColor = if (isReceiptMode) PrimaryGreen else Color.Transparent),
                            shape   = RoundedCornerShape(24.dp)
                        ) { Icon(Icons.Default.Receipt, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Receipt") }

                        Button(
                            onClick = { isReceiptMode = false },
                            colors  = ButtonDefaults.buttonColors(containerColor = if (!isReceiptMode) PrimaryGreen else Color.Transparent),
                            shape   = RoundedCornerShape(24.dp)
                        ) { Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Product") }
                    }

                    // Bottom controls
                    Row(
                        modifier              = Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        // Gallery button — same consent logic as direct gallery launch
                        IconButton(
                            onClick  = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) { Icon(Icons.Default.Image, "Gallery", tint = Color.White) }

                        // Shutter
                        Box(
                            modifier         = Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).clickable {
                                takePhoto(
                                    imageCapture    = imageCapture,
                                    executor        = cameraExecutor,
                                    onImageCaptured = { uri ->
                                        showCamera = false
                                        if (!isReceiptMode) {
                                            // Product mode — go through consent flow
                                            pendingProductUri = uri
                                            if (locationConsentGranted == null) {
                                                showLocationConsentDialog = true
                                            } else {
                                                launchProductScan(uri)
                                            }
                                        } else {
                                            // Receipt mode — straight through
                                            processImage(
                                                context           = context,
                                                uri               = uri,
                                                isReceiptMode     = true,
                                                geminiParser      = geminiParser,
                                                scope             = scope,
                                                countryIso        = null,
                                                onLoading         = { isProcessingAI = true },
                                                onReceiptComplete = { merchant, address, total, raw ->
                                                    isProcessingAI = false
                                                    if (merchant != null) {
                                                        name = merchant; detectedAddress = address ?: ""
                                                        amount = total?.toString() ?: ""; showAddDialog = true
                                                    } else { scannedRawText = raw; showScannedTextDialog = true }
                                                },
                                                onProductComplete = {}
                                            )
                                        }
                                    },
                                    onError = {}
                                )
                            },
                            contentAlignment = Alignment.Center
                        ) { Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape)) }

                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    IconButton(
                        onClick  = { showCamera = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    ) { Text("X", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                }
            }
        }

        // Anomaly warning
        if (showAnomalyDialog) {
            AlertDialog(
                onDismissRequest = { showAnomalyDialog = false; showAddDialog = true },
                title = { Text("Unusual Amount 🚨", color = ExpenseRed) },
                text  = { Text("The amount entered ($amount RON) is much higher than your usual average for '$selectedCategoryName' (~${String.format("%.0f", anomalyAverage)} RON).\n\nAre you sure this is correct?") },
                confirmButton = {
                    Button(onClick = { showAnomalyDialog = false; showLocationDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)) { Text("Yes, it's correct") }
                },
                dismissButton = {
                    TextButton(onClick = { showAnomalyDialog = false; showAddDialog = true }) { Text("Let me change it") }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// saveTransaction
// ─────────────────────────────────────────────────────────────────────────────

fun saveTransaction(
    viewModel: ExpensesViewModel, selectedExpense: ExpenseTransaction?,
    name: String, amountStr: String, category: String,
    isExpense: Boolean, locationName: String, lat: Double, lng: Double
) {
    val amountVal   = amountStr.toDoubleOrNull() ?: 0.0
    val finalAmount = if (isExpense) -amountVal else amountVal
    // For expenses: use the chosen category directly.
    // For income:   category is either "Income" (general budget only) or a specific
    //               budget category name (user wants to extend that category's limit too).
    //               Either way we store it as-is; BudgetViewModel handles the logic.
    //               The positive amount ensures spentMap (amount < 0 filter) never counts it.
    if (selectedExpense != null) viewModel.editExpense(selectedExpense.id, name, finalAmount, category)
    else viewModel.addExpense(name, finalAmount, category, locationName, lat, lng)
}

// ─────────────────────────────────────────────────────────────────────────────
// processImage — updated signature
//
// isReceiptMode = true  → onReceiptComplete callback, onProductComplete ignored
// isReceiptMode = false → onProductComplete callback, onReceiptComplete ignored
// countryIso            → null for receipt mode (unused) and for international
//                         product mode; ISO string (e.g. "RO") for local mode
// ─────────────────────────────────────────────────────────────────────────────

fun processImage(
    context: Context,
    uri: Uri,
    isReceiptMode: Boolean,
    geminiParser: GeminiReceiptParser,
    scope: CoroutineScope,
    countryIso: String?,
    onLoading: () -> Unit,
    onReceiptComplete: (String?, String?, Double?, String) -> Unit,
    onProductComplete: (ProductResult?) -> Unit
) {
    onLoading()

    if (isReceiptMode) {
        try {
            val image      = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    scope.launch {
                        val result = geminiParser.parseReceiptText(visionText.text)
                        if (result != null) onReceiptComplete(result.merchant, result.address, result.total, visionText.text)
                        else onReceiptComplete(null, null, null, visionText.text)
                    }
                }
                .addOnFailureListener { onReceiptComplete(null, null, null, "OCR Failed") }
        } catch (e: IOException) { onReceiptComplete(null, null, null, "Error") }

    } else {
        scope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap      = BitmapFactory.decodeStream(inputStream)

                val currency = when (countryIso?.uppercase()) {
                    "RO"                   -> "RON"
                    "DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT" -> "EUR"
                    "GB"                   -> "GBP"
                    "US"                   -> "USD"
                    "CA"                   -> "CAD"
                    "AU"                   -> "AUD"
                    else                   -> "USD"
                }

                val result = geminiParser.identifyProduct(bitmap, countryIso, currency)
                onProductComplete(result)

            } catch (e: Exception) {
                Log.e("ExpensesScreen", "Product scan failed: ${e.message}")
                onProductComplete(null)
            }
        }
    }
}