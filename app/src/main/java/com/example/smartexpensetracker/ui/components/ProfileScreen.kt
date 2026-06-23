package com.example.smartexpensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.utils.UserPreferences
import com.example.smartexpensetracker.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ProfileScreen(onMenuClick: () -> Unit) {
    val context      = LocalContext.current
    val authViewModel: AuthViewModel = viewModel()
    val user         = FirebaseAuth.getInstance().currentUser
    val uid          = user?.uid ?: "local"
    val prefs        = remember(uid) { UserPreferences(context, uid) }
    val currency     by prefs.currency.collectAsState()

    var recoveryContact    by remember { mutableStateOf("") }
    var newPassword        by remember { mutableStateOf("") }
    var passwordVisible    by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var snackMessage       by remember { mutableStateOf<String?>(null) }
    val snackState         = remember { SnackbarHostState() }

    LaunchedEffect(uid) {
        authViewModel.fetchRecoveryContact { recoveryContact = it ?: "" }
    }
    LaunchedEffect(snackMessage) {
        snackMessage?.let { snackState.showSnackbar(it); snackMessage = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, null, tint = PrimaryGreen)
                }
                Spacer(Modifier.width(8.dp))
                Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(LightMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = PrimaryGreen, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(user?.email ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Signed in with Firebase", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            Spacer(Modifier.height(32.dp))

            SectionLabel("Preferred Currency")
            ProfileRow(
                icon   = Icons.Default.AttachMoney,
                label  = "Currency",
                value  = currency,
                onClick = { showCurrencyPicker = true }
            )

            Spacer(Modifier.height(24.dp))

            SectionLabel("Security")

            OutlinedTextField(
                value         = newPassword,
                onValueChange = { newPassword = it },
                label         = { Text("New Password") },
                leadingIcon   = { Icon(Icons.Default.Lock, null) },
                trailingIcon  = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (newPassword.length >= 6) {
                        authViewModel.updatePassword(newPassword) { ok, err ->
                            snackMessage = if (ok) "Password updated" else (err ?: "Failed")
                            if (ok) newPassword = ""
                        }
                    } else {
                        snackMessage = "Password must be at least 6 characters"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) { Text("Update Password") }

            Spacer(Modifier.height(24.dp))

            SectionLabel("Recovery Contact")
            OutlinedTextField(
                value         = recoveryContact,
                onValueChange = { recoveryContact = it },
                label         = { Text("Recovery Email / Phone") },
                leadingIcon   = { Icon(Icons.Default.ContactMail, null) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    authViewModel.updateRecoveryContact(recoveryContact) { ok ->
                        snackMessage = if (ok) "Recovery info saved" else "Save failed"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) { Text("Save Recovery Info") }
        }
    }

    if (showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text("Select Currency") },
            text  = {
                Column {
                    UserPreferences.SUPPORTED_CURRENCIES.forEach { (code, symbol) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (currency == code) LightMint else Color.Transparent)
                                .clickable {
                                    prefs.setCurrency(code)
                                    showCurrencyPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$symbol  $code", style = MaterialTheme.typography.bodyMedium)
                            if (currency == code) {
                                Icon(Icons.Default.Check, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCurrencyPicker = false }) { Text("Close") } }
        )
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium,
        color = PrimaryGreen,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}