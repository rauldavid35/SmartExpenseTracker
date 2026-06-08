package com.example.smartexpensetracker.ui.navigation

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.example.smartexpensetracker.ui.screens.auth.AuthScreen
import com.example.smartexpensetracker.ui.theme.SmartExpenseTrackerTheme
import com.example.smartexpensetracker.utils.BiometricPromptManager
import com.example.smartexpensetracker.utils.UserPreferences
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

// ── Composition local so any screen can read privacyMode without prop-drilling ─
val LocalPrivacyMode = staticCompositionLocalOf { false }

// ── Privacy helpers ───────────────────────────────────────────────────────────
//
// MoneyText is the canonical way to render any monetary value in the UI.
// When privacy mode is on, it replaces digits with bullets so the layout
// stays roughly the same width but the actual figure is hidden.
//
// Pattern: replace every Text("$${...amount...}") with MoneyText("$${...amount...}").
// The currency symbol / sign / formatting stays — only digits get masked.

/**
 * Mask digits in a money string with • characters when [masked] is true.
 * Keeps non-digit characters (currency symbols, signs, spaces, decimal separators)
 * so the result reads as e.g. "$••••.••" instead of leaking the magnitude.
 */
fun maskMoney(value: String, masked: Boolean): String =
    if (!masked) value else buildString(value.length) {
        for (c in value) append(if (c.isDigit()) '•' else c)
    }

/**
 * Drop-in replacement for `Text(...)` for any monetary value.
 * Reads [LocalPrivacyMode] and masks digits automatically.
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    val masked = LocalPrivacyMode.current
    Text(
        text     = maskMoney(text, masked),
        modifier = modifier,
        color    = color,
        style    = style
    )
}

@Composable
fun MainApp(
    authViewModel: AuthViewModel,
    biometricManager: BiometricPromptManager,
    triggerAction: MutableIntState
) {
    val authState by authViewModel.authState.collectAsState()
    val context   = LocalContext.current

    // ── Per-user preferences ──────────────────────────────────────────────────
    val uid   = authState.user?.uid ?: "local"
    val prefs = remember(uid) { UserPreferences(context, uid) }

    val darkMode    by prefs.darkMode.collectAsState()
    val privacyMode by prefs.privacyMode.collectAsState()

    // ── Biometric state ───────────────────────────────────────────────────────
    val sharedPrefs = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    var isBiometricEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("biometric_enabled", false))
    }
    var isAppUnlocked by remember { mutableStateOf(!isBiometricEnabled) }
    var showEnrollmentDialog by remember {
        mutableStateOf(
            authState.user != null &&
                    !isBiometricEnabled &&
                    !sharedPrefs.getBoolean("biometric_enrollment_asked", false)
        )
    }
    val biometricResult    by biometricManager.promptResults.collectAsState(initial = null)

    LaunchedEffect(biometricResult) {
        when (val result = biometricResult) {
            is BiometricPromptManager.BiometricResult.AuthenticationSuccess -> {
                isAppUnlocked = true
            }
            is BiometricPromptManager.BiometricResult.AuthenticationNotSet -> {
                android.widget.Toast.makeText(
                    context,
                    "No biometric enrolled on this device. Set one up in your phone's settings.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                sharedPrefs.edit().putBoolean("biometric_enabled", false).apply()
                isBiometricEnabled = false
                isAppUnlocked = true
            }
            is BiometricPromptManager.BiometricResult.HardwareUnavailable,
            is BiometricPromptManager.BiometricResult.FeatureUnavailable -> {
                android.widget.Toast.makeText(
                    context,
                    "Biometric not available on this device.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                sharedPrefs.edit().putBoolean("biometric_enabled", false).apply()
                isBiometricEnabled = false
                isAppUnlocked = true
            }
            is BiometricPromptManager.BiometricResult.AuthenticationError -> {
                android.widget.Toast.makeText(
                    context,
                    "Auth error: ${result.error}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            else -> { /* failed attempt or null — let user retry */ }
        }
    }

    LaunchedEffect(authState.user, isAppUnlocked) {
        if (authState.user == null) {
            isAppUnlocked = !isBiometricEnabled
        } else if (!isAppUnlocked && isBiometricEnabled) {
            biometricManager.showBiometricPrompt("Welcome Back", "Confirm fingerprint")
        }
    }

    // ── Wrap everything in theme + privacy local ──────────────────────────────
    SmartExpenseTrackerTheme(darkTheme = darkMode) {
        CompositionLocalProvider(LocalPrivacyMode provides privacyMode) {

            if (authState.user == null) {
                AuthScreen(
                    viewModel    = authViewModel,
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
                    text  = { Text("Enable fingerprint for faster login?") },
                    confirmButton = {
                        Button(onClick = {
                            sharedPrefs.edit()
                                .putBoolean("biometric_enabled", true)
                                .putBoolean("biometric_enrollment_asked", true)
                                .apply()
                            isBiometricEnabled = true
                            biometricManager.showBiometricPrompt("Verify", "Scan now")
                            showEnrollmentDialog = false
                        }) { Text("Yes") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            sharedPrefs.edit()
                                .putBoolean("biometric_enrollment_asked", true)
                                .apply()
                            showEnrollmentDialog = false
                            isAppUnlocked = true
                        }) { Text("No") }
                    }
                )
            }
        }
    }
}