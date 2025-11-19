package com.example.smartexpensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartexpensetracker.ui.theme.SmartExpenseTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartExpenseTrackerTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onNavigate = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                BottomNavigationBar(navController)
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* Help action */ },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Help, contentDescription = "Help")
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") {
                    HomeScreen(onMenuClick = {
                        scope.launch { drawerState.open() }
                    })
                }
                composable("expenses") { ExpensesScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
                composable("budget") { BudgetScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
                composable("lists") { ListsScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
                composable("heatmap") { HeatmapScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
                composable("profile") { ProfileScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
                composable("settings") { SettingsScreen(onMenuClick = {
                    scope.launch { drawerState.open() }
                }) }
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

data class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)