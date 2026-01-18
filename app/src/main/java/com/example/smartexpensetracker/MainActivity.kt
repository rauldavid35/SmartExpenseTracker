package com.example.smartexpensetracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.smartexpensetracker.ui.theme.SmartExpenseTrackerTheme
import com.example.smartexpensetracker.utils.BiometricPromptManager
import com.example.smartexpensetracker.utils.ShakeDetector
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    private val biometricManager by lazy { BiometricPromptManager(this) }

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    // 0 = None, 1 = Voice Expenses, 2 = Voice Lists, 3 = Camera Expenses
    private var shakeTriggerAction = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector(
            onShakeHorizontal = { shakeTriggerAction.intValue = 1 },
            onShakeVertical = { shakeTriggerAction.intValue = 2 },
            onShakeTriple = { shakeTriggerAction.intValue = 3 }
        )

        setContent {
            SmartExpenseTrackerTheme {
                MainApp(authViewModel, biometricManager, shakeTriggerAction)
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
    shakeTrigger: MutableIntState
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
        MainScreen(authViewModel, shakeTrigger)
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
    shakeTrigger: MutableIntState
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // --- GLOBAL SHAKE LISTENER ---
    LaunchedEffect(shakeTrigger.intValue) {
        when (shakeTrigger.intValue) {
            1 -> navController.navigate("expenses") { launchSingleTop = true }
            2 -> navController.navigate("lists") { launchSingleTop = true }
            3 -> navController.navigate("expenses") { launchSingleTop = true }
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
                    val isVoice = shakeTrigger.intValue == 1
                    val isCamera = shakeTrigger.intValue == 3

                    ExpensesScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        externalVoiceTrigger = isVoice,
                        externalCameraTrigger = isCamera,
                        onExternalTriggerHandled = { shakeTrigger.intValue = 0 }
                    )
                }

                composable("budget") { BudgetScreen(onMenuClick = { scope.launch { drawerState.open() } }) }

                composable("lists") {
                    val isVoice = shakeTrigger.intValue == 2

                    ListsScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onListClick = { listId, listName -> navController.navigate("list_detail/$listId/$listName") },
                        externalVoiceTrigger = isVoice,
                        onExternalTriggerHandled = { shakeTrigger.intValue = 0 }
                    )
                }

                composable(
                    "list_detail/{listId}/{listName}",
                    arguments = listOf(navArgument("listId") { type = NavType.StringType }, navArgument("listName") { type = NavType.StringType })
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

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)