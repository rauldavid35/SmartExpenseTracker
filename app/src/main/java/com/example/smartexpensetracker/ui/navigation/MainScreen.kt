package com.example.smartexpensetracker.ui.navigation

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.smartexpensetracker.ui.components.ExportBottomSheet
import com.example.smartexpensetracker.ui.screens.auth.AuthScreen
import com.example.smartexpensetracker.ui.screens.budget.BudgetScreen
import com.example.smartexpensetracker.ui.screens.expenses.ExpensesScreenWithFirebase
import com.example.smartexpensetracker.ui.screens.heatmap.HeatmapScreen
import com.example.smartexpensetracker.ui.screens.home.HomeScreenWithFirebase
import com.example.smartexpensetracker.ui.screens.lists.ListDetailScreen
import com.example.smartexpensetracker.ui.screens.lists.ListsScreenWithFirebase
import com.example.smartexpensetracker.ui.components.PlaceholderScreen
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import com.example.smartexpensetracker.viewmodel.BudgetViewModel
import com.example.smartexpensetracker.utils.BiometricPromptManager
import kotlinx.coroutines.launch
import com.example.smartexpensetracker.ui.components.ProfileScreen
import com.example.smartexpensetracker.ui.components.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    triggerAction: MutableIntState
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showShakeMenu by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    // Shared BudgetViewModel so the drawer export sheet uses the same data
    val budgetViewModel: BudgetViewModel = viewModel(
        factory = BudgetViewModel.Factory(context)
    )

    LaunchedEffect(triggerAction.intValue) {
        when (triggerAction.intValue) {
            1 -> navController.navigate("expenses") { launchSingleTop = true }
            2 -> navController.navigate("lists") { launchSingleTop = true }
            3 -> navController.navigate("expenses") { launchSingleTop = true }
            99 -> { showShakeMenu = true; triggerAction.intValue = 0 }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContentWithAuth(
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        authViewModel.logout()
                    }
                },
                onExport = {
                    scope.launch {
                        drawerState.close()
                        showExportSheet = true
                    }
                }
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
                composable("home") {
                    HomeScreenWithFirebase(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable("expenses") {
                    ExpensesScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        externalVoiceTrigger = triggerAction.intValue == 1,
                        externalCameraTrigger = triggerAction.intValue == 3,
                        onExternalTriggerHandled = { triggerAction.intValue = 0 }
                    )
                }
                composable("budget") {
                    // Pass the shared viewModel so dashboards/export share state
                    BudgetScreen(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        viewModel = budgetViewModel
                    )
                }
                composable("lists") {
                    ListsScreenWithFirebase(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onListClick = { listId, listName -> navController.navigate("list_detail/$listId/$listName") },
                        externalVoiceTrigger = triggerAction.intValue == 2,
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
                    ListDetailScreen(
                        listId = backStackEntry.arguments?.getString("listId") ?: "",
                        listName = backStackEntry.arguments?.getString("listName") ?: "List",
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

    // ── Export bottom sheet ──────────────────────────────────────────────────
    if (showExportSheet) {
        ExportBottomSheet(
            viewModel = budgetViewModel,
            onDismiss = { showExportSheet = false }
        )
    }

    // ── Shake quick-actions dialog ───────────────────────────────────────────
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
                    Spacer(Modifier.height(24.dp))
                    QuickActionBtn("Add Expense", Icons.Default.Mic) {
                        showShakeMenu = false; triggerAction.intValue = 1
                    }
                    Spacer(Modifier.height(16.dp))
                    QuickActionBtn("Scan Receipt", Icons.Default.CameraAlt) {
                        showShakeMenu = false; triggerAction.intValue = 3
                    }
                    Spacer(Modifier.height(16.dp))
                    QuickActionBtn("Create List", Icons.Default.List) {
                        showShakeMenu = false; triggerAction.intValue = 2
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
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}