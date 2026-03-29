package com.example.smartexpensetracker

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Wallet
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
import com.example.smartexpensetracker.ui.components.CameraPreview
import com.example.smartexpensetracker.ui.components.takePhoto
import com.example.smartexpensetracker.ui.theme.*
import com.example.smartexpensetracker.utils.GeminiReceiptParser
import com.example.smartexpensetracker.utils.LocationHelper
import com.example.smartexpensetracker.viewmodel.ExpenseTransaction
import com.example.smartexpensetracker.viewmodel.ExpensesViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors
import java.util.Locale
import java.util.Currency
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ExpensesScreenWithFirebase(
    onMenuClick: () -> Unit,
    viewModel: ExpensesViewModel = viewModel(),
    // Added params to allow external triggers (from Shake in MainActivity)
    externalVoiceTrigger: Boolean = false,
    externalCameraTrigger: Boolean = false,
    onExternalTriggerHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val expenses by viewModel.expenses.collectAsState()
    val availableCategories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Helpers
    val geminiParser = remember { GeminiReceiptParser("key") }
    val locationHelper = remember { LocationHelper(context) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // UI States
    var showAddDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showScannedTextDialog by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var showVoiceInput by remember { mutableStateOf(false) }

    var isReceiptMode by remember { mutableStateOf(true) }

    // Form Data
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCategoryName by remember { mutableStateOf("None") }
    var isExpense by remember { mutableStateOf(true) }
    var scannedRawText by remember { mutableStateOf("") }
    var detectedAddress by remember { mutableStateOf("") }

    var selectedExpense by remember { mutableStateOf<ExpenseTransaction?>(null) }
    val isEditing = selectedExpense != null

    // --- HANDLE EXTERNAL TRIGGERS (SHAKE) ---
    LaunchedEffect(externalVoiceTrigger) {
        if (externalVoiceTrigger) {
            showVoiceInput = true
            onExternalTriggerHandled()
        }
    }
    LaunchedEffect(externalCameraTrigger) {
        if (externalCameraTrigger) {
            if (cameraPermission.status.isGranted) showCamera = true
            else cameraPermission.launchPermissionRequest()
            onExternalTriggerHandled()
        }
    }

    // --- LOGIC: Voice Parsing ---
    fun processVoiceCommand(text: String) {
        // Logic: "Name" then "Amount"
        // Heuristic: Extract the first number found as amount
        val numberRegex = Regex("[0-9]+(\\.[0-9]+)?")
        val match = numberRegex.find(text)

        if (match != null) {
            amount = match.value
            // Remove the amount from the text to get the name
            name = text.replace(match.value, "").trim()
        } else {
            name = text
            amount = ""
        }

        isExpense = true
        showAddDialog = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImage(
                context = context,
                uri = it,
                isReceiptMode = isReceiptMode,
                geminiParser = geminiParser,
                scope = scope,
                onLoading = { isProcessingAI = true },
                onComplete = { merchant, address, total, raw ->
                    isProcessingAI = false
                    if (merchant != null) {
                        name = merchant
                        detectedAddress = address ?: ""
                        amount = total?.toString() ?: ""
                        showAddDialog = true
                    } else {
                        scannedRawText = raw
                        showScannedTextDialog = true
                    }
                }
            )
        }
    }

    val totalExpenses = expenses.filter { it.amount < 0 }.sumOf { -it.amount }
    val totalIncome = expenses.filter { it.amount > 0 }.sumOf { it.amount }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Top Bar
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(PrimaryGreen)) {
                    Icon(Icons.Default.Wallet, "Menu", tint = Color.White)
                }
                Text("Expenses", style = MaterialTheme.typography.headlineSmall)
                Row {
                    // Voice Button
                    IconButton(
                        onClick = { showVoiceInput = true },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LightMint)
                    ) {
                        Icon(Icons.Default.Mic, "Voice", tint = PrimaryGreen)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (cameraPermission.status.isGranted) showCamera = true
                            else cameraPermission.launchPermissionRequest()
                        },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.CameraAlt, "Scan", tint = PrimaryGreen)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            selectedExpense = null
                            name = ""
                            amount = ""
                            selectedCategoryName = if (availableCategories.isNotEmpty()) availableCategories[0] else "None"
                            isExpense = true
                            detectedAddress = ""
                            showAddDialog = true
                        },
                        containerColor = PrimaryGreen,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, "Add", tint = Color.White)
                    }
                }
            }

            // Summary
            Row(modifier = Modifier.padding(16.dp)) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) { Text("Expenses", color = TextSecondary); Text("$${String.format("%.2f", totalExpenses)}", color = ExpenseRed, style = MaterialTheme.typography.titleLarge) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) { Text("Income", color = TextSecondary); Text("$${String.format("%.2f", totalIncome)}", color = IncomeGreen, style = MaterialTheme.typography.titleLarge) }
                }
            }

            if (isLoading) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            // List
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenses) { transaction ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(transaction.name, style = MaterialTheme.typography.titleMedium)
                                Text("${transaction.category}${if (transaction.locationName.isNotEmpty()) " • ${transaction.locationName}" else ""}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Text(text = "${if (transaction.amount >= 0) "+" else ""}$${String.format("%.2f", transaction.amount)}", color = if (transaction.amount >= 0) IncomeGreen else ExpenseRed, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Voice Dialog
        if (showVoiceInput) {
            VoiceInputDialog(
                onTextReceived = { text ->
                    processVoiceCommand(text)
                    showVoiceInput = false
                },
                onDismiss = { showVoiceInput = false }
            )
        }

        // Add/Edit Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false; selectedExpense = null },
                title = { Text(if (isEditing) "Edit Transaction" else "Add Transaction") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name / Details") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isExpense, onCheckedChange = { isExpense = it })
                            Text("Is Expense?")
                        }

                        if (isExpense) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // CATEGORY DROPDOWN LOGIC
                            if (availableCategories.isEmpty()) {
                                OutlinedTextField(
                                    value = selectedCategoryName,
                                    onValueChange = { selectedCategoryName = it },
                                    label = { Text("Category (Free Text)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                var expanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedCategoryName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Category") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        availableCategories.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category) },
                                                onClick = {
                                                    selectedCategoryName = category
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
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
                                    showAddDialog = false
                                    selectedExpense = null
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                        }
                        Button(
                            onClick = {
                                // Force category to "Income" if it's not an expense
                                if (!isExpense) {
                                    selectedCategoryName = "Income"
                                }
                                showAddDialog = false
                                showLocationDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) { Text("Next") }
                    }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false; selectedExpense = null }) { Text("Cancel") } }
            )
        }

        // Location Dialog
        if (showLocationDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Add Location?") },
                text = { Text("Where did this transaction happen?") },
                confirmButton = {
                    Button(onClick = {
                        if (locationPermission.status.isGranted) {
                            scope.launch {
                                val coords = locationHelper.getCurrentLocation()
                                val lat = coords?.first ?: 0.0
                                val lng = coords?.second ?: 0.0
                                val realAddressName = if (lat != 0.0) locationHelper.getAddressFromCoordinates(lat, lng) else "My GPS Location"

                                saveTransaction(viewModel, selectedExpense, name, amount, selectedCategoryName, isExpense, realAddressName, lat, lng)
                                showLocationDialog = false
                                selectedExpense = null
                            }
                        } else {
                            locationPermission.launchPermissionRequest()
                        }
                    }) { Text("Use GPS") }
                },
                dismissButton = {
                    Row {
                        if (detectedAddress.isNotBlank()) {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val coords = locationHelper.getCoordinatesFromAddress(detectedAddress)
                                    saveTransaction(viewModel, selectedExpense, name, amount, selectedCategoryName, isExpense, detectedAddress, coords?.first ?: 0.0, coords?.second ?: 0.0)
                                    showLocationDialog = false
                                    selectedExpense = null
                                }
                            }) { Text("Use Receipt Addr") }
                        }
                        TextButton(onClick = {
                            saveTransaction(viewModel, selectedExpense, name, amount, selectedCategoryName, isExpense, "", 0.0, 0.0)
                            showLocationDialog = false
                            selectedExpense = null
                        }) { Text("Skip") }
                    }
                }
            )
        }

        // Fallback Dialog
        if (showScannedTextDialog) {
            AlertDialog(
                onDismissRequest = { showScannedTextDialog = false },
                title = { Text("Verify Data") },
                text = {
                    Column {
                        Text("Could not auto-detect info.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = scannedRawText, onValueChange = { scannedRawText = it }, label = { Text("Raw Info") }, modifier = Modifier.fillMaxWidth().height(100.dp))
                    }
                },
                confirmButton = { Button(onClick = { name = scannedRawText; showScannedTextDialog = false; showAddDialog = true }) { Text("Use This") } }
            )
        }

        // Loading
        if (isProcessingAI) {
            Dialog(onDismissRequest = {}) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = PrimaryGreen); Spacer(modifier = Modifier.height(16.dp)); Text("AI is analyzing...") }
                }
            }
        }

        // Camera Overlay
        if (showCamera) {
            Dialog(onDismissRequest = { showCamera = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    CameraPreview(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { isReceiptMode = true }, colors = ButtonDefaults.buttonColors(containerColor = if (isReceiptMode) PrimaryGreen else Color.Transparent), shape = RoundedCornerShape(24.dp)) {
                            Icon(Icons.Default.Receipt, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Receipt")
                        }
                        Button(onClick = { isReceiptMode = false }, colors = ButtonDefaults.buttonColors(containerColor = if (!isReceiptMode) PrimaryGreen else Color.Transparent), shape = RoundedCornerShape(24.dp)) {
                            Icon(Icons.Default.ShoppingBag, null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Product")
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape)) { Icon(Icons.Default.Image, "Gallery", tint = Color.White) }
                        Box(modifier = Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).clickable {
                            takePhoto(imageCapture = imageCapture, executor = cameraExecutor, onImageCaptured = { uri ->
                                showCamera = false
                                processImage(context, uri, isReceiptMode, geminiParser, scope, { isProcessingAI = true }, { merchant, address, total, raw ->
                                    isProcessingAI = false
                                    if (merchant != null) {
                                        name = merchant
                                        detectedAddress = address ?: ""
                                        amount = total?.toString() ?: ""
                                        showAddDialog = true
                                    } else {
                                        scannedRawText = raw
                                        showScannedTextDialog = true
                                    }
                                })
                            }, onError = {})
                        }, contentAlignment = Alignment.Center) { Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape)) }
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    IconButton(onClick = { showCamera = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Text("X", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    }
}

// --- Helper Functions ---

fun saveTransaction(
    viewModel: ExpensesViewModel,
    selectedExpense: ExpenseTransaction?,
    name: String,
    amountStr: String,
    category: String,
    isExpense: Boolean,
    locationName: String,
    lat: Double,
    lng: Double
) {
    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
    val finalAmount = if (isExpense) -amountVal else amountVal

    if (selectedExpense != null) {
        viewModel.editExpense(selectedExpense.id, name, finalAmount, category)
    } else {
        viewModel.addExpense(name, finalAmount, category, locationName, lat, lng)
    }
}

fun processImage(
    context: android.content.Context,
    uri: Uri,
    isReceiptMode: Boolean,
    geminiParser: GeminiReceiptParser,
    scope: kotlinx.coroutines.CoroutineScope,
    onLoading: () -> Unit,
    onComplete: (String?, String?, Double?, String) -> Unit
) {
    onLoading()

    if (isReceiptMode) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    scope.launch {
                        val result = geminiParser.parseReceiptText(visionText.text)
                        if (result != null) onComplete(result.merchant, result.address, result.total, visionText.text)
                        else onComplete(null, null, null, visionText.text)
                    }
                }
                .addOnFailureListener { onComplete(null, null, null, "OCR Failed") }
        } catch (e: IOException) { onComplete(null, null, null, "Error") }
    } else {
        scope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                val locale = Locale.getDefault()
                val country = locale.displayCountry.takeIf { it.isNotEmpty() } ?: "Romania"
                val currencyCode = try { Currency.getInstance(locale).currencyCode } catch(e:Exception) { "RON" }

                val result = geminiParser.identifyProduct(bitmap, country, currencyCode)

                if (result != null) onComplete(result.merchant, null, result.total, "Product Scan")
                else onComplete(null, null, null, "Could not identify product")

            } catch (e: Exception) {
                onComplete(null, null, null, "Error processing image")
            }
        }
    }
}
