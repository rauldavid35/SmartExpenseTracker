package com.example.smartexpensetracker

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope // Import needed for the timer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.SmartExpenseTrackerTheme
import com.example.smartexpensetracker.utils.BiometricPromptManager
import com.example.smartexpensetracker.utils.ShakeDetector
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import kotlinx.coroutines.Job // Import needed
import kotlinx.coroutines.delay // Import needed
import kotlinx.coroutines.launch // Import needed

class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val biometricManager by lazy { BiometricPromptManager(this) }

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    // 0 = None
    // 1 = Voice Expense (Single Vol Up)
    // 2 = Voice List (Double Vol Up)
    // 3 = Camera Expense (Vol Down)
    // 99 = Shake Detected
    private var triggerAction = mutableIntStateOf(0)

    // Timer Job for the Double Tap logic
    private var volumeUpJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup Universal Shake
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector {
            triggerAction.intValue = 99
        }

        // 2. Check for App Shortcuts
        handleIntent(intent)

        setContent {
            SmartExpenseTrackerTheme {
                MainApp(authViewModel, biometricManager, triggerAction)
            }
        }
    }

    // --- NEW "SMART" VOLUME BUTTON LOGIC ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.repeatCount == 0) { // Only catch the first 'down' event
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (volumeUpJob?.isActive == true) {
                        // CASE: DOUBLE TAP DETECTED
                        // User pressed again while we were waiting.
                        volumeUpJob?.cancel() // Cancel the Single Tap action
                        volumeUpJob = null
                        triggerAction.intValue = 2 // Go to Lists immediately
                    } else {
                        // CASE: FIRST TAP
                        // Start a timer. If it finishes, it's a Single Tap.
                        volumeUpJob = lifecycleScope.launch {
                            delay(400) // Wait 400ms for a second press
                            triggerAction.intValue = 1 // No second press? Go to Expenses
                            volumeUpJob = null
                        }
                    }
                    return true // Block system volume change
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    // Volume Down -> Camera (Instant)
                    triggerAction.intValue = 3
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    // ---------------------------------------

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("SHORTCUT_ACTION")?.let { action ->
            when (action) {
                "voice_expense" -> triggerAction.intValue = 1
                "voice_list" -> triggerAction.intValue = 2
                "camera_expense" -> triggerAction.intValue = 3
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shakeDetector?.let { sensorManager.registerListener(it, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }
}

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    biometricManager: BiometricPromptManager,
    triggerAction: MutableIntState
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isBiometricEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", false)) }
    var isAppUnlocked by remember { mutableStateOf(false) }
    var showEnrollmentDialog by remember { mutableStateOf(false) }
    val biometricResult by biometricManager.promptResults.collectAsState(initial = null)

    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess) isAppUnlocked = true
    }

    LaunchedEffect(authState.user, isAppUnlocked) {
        if (authState.user != null && !isAppUnlocked) {
            if (isBiometricEnabled) biometricManager.showBiometricPrompt("Welcome Back", "Confirm fingerprint")
            else showEnrollmentDialog = true
        }
    }

    if (authState.user == null) {
        AuthScreen(viewModel = authViewModel, onAuthSuccess = { isAppUnlocked = true; if (!isBiometricEnabled) showEnrollmentDialog = true })
    } else if (!isAppUnlocked) {
        LockedScreen(onUnlockClick = { if (isBiometricEnabled) biometricManager.showBiometricPrompt("Unlock App", "Confirm fingerprint") })
    } else {
        MainScreen(authViewModel, triggerAction)
    }

    if (showEnrollmentDialog) {
        AlertDialog(
            onDismissRequest = { showEnrollmentDialog = false; isAppUnlocked = true },
            title = { Text("Enable Fingerprint?") },
            text = { Text("Enable fingerprint for faster login?") },
            confirmButton = {
                Button(onClick = {
                    sharedPrefs.edit().putBoolean("biometric_enabled", true).apply()
                    isBiometricEnabled = true
                    biometricManager.showBiometricPrompt("Verify", "Scan now")
                    showEnrollmentDialog = false
                }) { Text("Yes") }
            },
            dismissButton = { TextButton(onClick = { showEnrollmentDialog = false; isAppUnlocked = true }) { Text("No") } }
        )
    }
}

@Composable
fun LockedScreen(onUnlockClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, "Locked", modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onUnlockClick) { Text("Unlock") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    triggerAction: MutableIntState
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showShakeMenu by remember { mutableStateOf(false) }

    // --- TRIGGER HANDLER ---
    LaunchedEffect(triggerAction.intValue) {
        when (triggerAction.intValue) {
            1 -> { // Voice Expense
                navController.navigate("expenses") { launchSingleTop = true }
            }
            2 -> { // Voice List
                navController.navigate("lists") { launchSingleTop = true }
            }
            3 -> { // Camera Expense
                navController.navigate("expenses") { launchSingleTop = true }
            }
            99 -> { // Shake Detected
                showShakeMenu = true
                triggerAction.intValue = 0 // Reset to avoid loop
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContentWithAuth(
                onNavigate = { route -> scope.launch { drawerState.close(); navController.navigate(route) } },
                onLogout = { scope.launch { drawerState.close(); authViewModel.logout() } }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                if (currentRoute in listOf("home", "expenses", "budget", "lists", "heatmap", "profile", "settings")) {
                    BottomNavigationBar(navController)
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") { HomeScreenWithFirebase(onMenuClick = { scope.launch { drawerState.open() } }) }

                composable("expenses") {
                    val isVoice = triggerAction.intValue == 1
                    val isCamera = triggerAction.intValue == 3

                    ExpensesScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        externalVoiceTrigger = isVoice,
                        externalCameraTrigger = isCamera,
                        onExternalTriggerHandled = { triggerAction.intValue = 0 }
                    )
                }

                composable("budget") { BudgetScreen(onMenuClick = { scope.launch { drawerState.open() } }) }

                composable("lists") {
                    val isVoice = triggerAction.intValue == 2
                    ListsScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onListClick = { listId, listName -> navController.navigate("list_detail/$listId/$listName") },
                        externalVoiceTrigger = isVoice,
                        onExternalTriggerHandled = { triggerAction.intValue = 0 }
                    )
                }

                composable(
                    "list_detail/{listId}/{listName}",
                    arguments = listOf(
                        navArgument("listId") { type = NavType.StringType },
                        navArgument("listName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val listId = backStackEntry.arguments?.getString("listId") ?: ""
                    val listName = backStackEntry.arguments?.getString("listName") ?: "List"
                    ListDetailScreen(listId, listName, onBackClick = { navController.popBackStack() })
                }

                composable("heatmap") { HeatmapScreen(onMenuClick = { scope.launch { drawerState.open() } }) }
                composable("profile") { ProfileScreen(onMenuClick = { scope.launch { drawerState.open() } }) }
                composable("settings") { SettingsScreen(onMenuClick = { scope.launch { drawerState.open() } }) }
            }
        }
    }

    // --- POPUP MENU FOR SHAKE ---
    if (showShakeMenu) {
        Dialog(onDismissRequest = { showShakeMenu = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Quick Actions", style = MaterialTheme.typography.headlineSmall, color = PrimaryGreen)
                    Spacer(modifier = Modifier.height(24.dp))

                    QuickActionBtn("Add Expense", Icons.Default.Mic) {
                        showShakeMenu = false
                        triggerAction.intValue = 1
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    QuickActionBtn("Scan Receipt", Icons.Default.CameraAlt) {
                        showShakeMenu = false
                        triggerAction.intValue = 3
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    QuickActionBtn("Create List", Icons.Default.List) {
                        showShakeMenu = false
                        triggerAction.intValue = 2
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionBtn(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("expenses", "Expenses", Icons.Default.Receipt),
        BottomNavItem("budget", "Budget", Icons.Default.Wallet),
        BottomNavItem("lists", "Lists", Icons.Default.List)
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = { navController.navigate(item.route) { popUpTo(navController.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } }
            )
        }
    }
}

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)