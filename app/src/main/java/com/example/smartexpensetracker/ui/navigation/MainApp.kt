package com.example.smartexpensetracker.ui.navigation

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.smartexpensetracker.ui.screens.auth.AuthScreen
import com.example.smartexpensetracker.utils.BiometricPromptManager
import com.example.smartexpensetracker.viewmodel.AuthViewModel

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    biometricManager: BiometricPromptManager,
    triggerAction: MutableIntState
) {
    val authState by authViewModel.authState.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    var isBiometricEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", false))
    }
    var isAppUnlocked by remember { mutableStateOf(false) }
    var showEnrollmentDialog by remember { mutableStateOf(false) }
    val biometricResult by biometricManager.promptResults.collectAsState(initial = null)

    LaunchedEffect(biometricResult) {
        if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess) {
            isAppUnlocked = true
        }
    }

    LaunchedEffect(authState.user, isAppUnlocked) {
        if (authState.user != null && !isAppUnlocked) {
            if (isBiometricEnabled) {
                biometricManager.showBiometricPrompt("Welcome Back", "Confirm fingerprint")
            } else {
                showEnrollmentDialog = true
            }
        }
    }

    if (authState.user == null) {
        AuthScreen(
            viewModel = authViewModel,
            onAuthSuccess = {
                isAppUnlocked = true
                if (!isBiometricEnabled) showEnrollmentDialog = true
            }
        )
    } else if (!isAppUnlocked) {
        LockedScreen(
            onUnlockClick = {
                if (isBiometricEnabled) {
                    biometricManager.showBiometricPrompt("Unlock App", "Confirm fingerprint")
                }
            }
        )
    } else {
        MainScreen(authViewModel, triggerAction)
    }

    if (showEnrollmentDialog) {
        AlertDialog(
            onDismissRequest = {
                showEnrollmentDialog = false
                isAppUnlocked = true
            },
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
            dismissButton = {
                TextButton(onClick = {
                    showEnrollmentDialog = false
                    isAppUnlocked = true
                }) { Text("No") }
            }
        )
    }
}