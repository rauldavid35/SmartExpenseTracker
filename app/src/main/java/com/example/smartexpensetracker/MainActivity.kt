package com.example.smartexpensetracker

import android.content.Context
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
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

// CHANGED: Inherit from FragmentActivity for Biometric support
class MainActivity : FragmentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    // Initialize the Biometric Manager
    private val biometricManager by lazy { BiometricPromptManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartExpenseTrackerTheme {
                // Pass the manager to the main app logic
                MainApp(authViewModel, biometricManager)
            }
        }
    }
}

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    biometricManager: BiometricPromptManager
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current

    // --- Biometric State ---

    // 1. Check if the user has enabled fingerprint in the past
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var isBiometricEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", false))
    }

    // 2. Is the app "Unlocked" for this session?
    // Starts false. Becomes true after fingerprint OR normal login.
    var isAppUnlocked by remember { mutableStateOf(false) }

    // 3. Dialog control
    var showEnrollmentDialog by remember { mutableStateOf(false) }

    // 4. Listen for Fingerprint Results
    val biometricResult by biometricManager.promptResults.collectAsState(initial = null)

    // Effect: Unlock app when fingerprint succeeds
    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess) {
            isAppUnlocked = true
        }
    }

    // Effect: Logic for Auto-Login or App Start
    LaunchedEffect(authState.user, isAppUnlocked) {
        if (authState.user != null && !isAppUnlocked) {
            if (isBiometricEnabled) {
                // Case A: Enabled -> Show prompt immediately
                biometricManager.showBiometricPrompt(
                    title = "Welcome Back",
                    description = "Confirm your fingerprint to access your expenses."
                )
            } else {
                // Case B: Not Enabled -> Ask to enable (Requirement)
                showEnrollmentDialog = true
            }
        }
    }

    // --- UI Switching ---

    if (authState.user == null) {
        // SCENARIO 1: Not Logged In -> Show Login Screen
        AuthScreen(
            viewModel = authViewModel,
            onAuthSuccess = {
                // Successful login unlocks the app
                isAppUnlocked = true
                // If not enabled yet, ask them now
                if (!isBiometricEnabled) showEnrollmentDialog = true
            }
        )
    } else if (!isAppUnlocked) {
        // SCENARIO 2: Logged In but Locked (Waiting for Fingerprint)
        LockedScreen(
            onUnlockClick = {
                if (isBiometricEnabled) {
                    biometricManager.showBiometricPrompt(
                        title = "Unlock App",
                        description = "Confirm fingerprint"
                    )
                }
            }
        )
    } else {
        // SCENARIO 3: Unlocked -> Show the Main Content
        MainScreen(authViewModel)
    }

    // --- Dialogs ---
    if (showEnrollmentDialog) {
        AlertDialog(
            onDismissRequest = {
                // If dismissed, we assume "No" but let them in
                showEnrollmentDialog = false
                isAppUnlocked = true
            },
            title = { Text("Enable Fingerprint?") },
            text = { Text("Would you like to use your fingerprint for faster login next time?") },
            confirmButton = {
                Button(onClick = {
                    sharedPrefs.edit().putBoolean("biometric_enabled", true).apply()
                    isBiometricEnabled = true
                    // Test it immediately
                    biometricManager.showBiometricPrompt(
                        title = "Verify Fingerprint",
                        description = "Scan now to enable."
                    )
                    showEnrollmentDialog = false
                }) {
                    Text("Yes, Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEnrollmentDialog = false
                    isAppUnlocked = true // Let them in without it
                }) {
                    Text("Not Now")
                }
            }
        )
    }
}

// A simple screen shown while waiting for fingerprint
@Composable
fun LockedScreen(onUnlockClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("App Locked", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Waiting for biometric verification...")

                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = onUnlockClick) {
                    Text("Tap to Unlock")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContentWithAuth(
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        authViewModel.logout()
                    }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Only show bottom bar on main screens
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
                composable("home") {
                    HomeScreenWithFirebase(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("expenses") {
                    ExpensesScreenWithFirebase(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("budget") {
                    BudgetScreen(onMenuClick = { scope.launch { drawerState.open() } })
                }

                // Updated: Pass navigation lambda to open details
                composable("lists") {
                    ListsScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onListClick = { listId, listName ->
                            navController.navigate("list_detail/$listId/$listName")
                        }
                    )
                }

                // New Route: List Detail
                composable(
                    "list_detail/{listId}/{listName}",
                    arguments = listOf(
                        navArgument("listId") { type = NavType.StringType },
                        navArgument("listName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val listId = backStackEntry.arguments?.getString("listId") ?: ""
                    val listName = backStackEntry.arguments?.getString("listName") ?: "List"
                    ListDetailScreen(
                        listId = listId,
                        listName = listName,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("heatmap") {
                    HeatmapScreen(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("profile") {
                    ProfileScreen(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("settings") {
                    SettingsScreen(onMenuClick = { scope.launch { drawerState.open() } })
                }
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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
