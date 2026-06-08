package com.example.smartexpensetracker.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var recoveryEmail   by remember { mutableStateOf("") }
    var isLogin         by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgot      by remember { mutableStateOf(false) }
    var forgotEmail     by remember { mutableStateOf("") }
    var forgotSent      by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState.user) {
        if (authState.user != null && authState.error == null) onAuthSuccess()
    }

    if (showForgot) {
        ForgotPasswordDialog(
            email       = forgotEmail,
            onEmailChange = { forgotEmail = it },
            sent        = forgotSent,
            onSend      = {
                viewModel.sendPasswordReset(forgotEmail)
                forgotSent = true
            },
            onDismiss   = { showForgot = false; forgotSent = false; forgotEmail = "" }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text  = if (isLogin) "Welcome Back" else "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryGreen
        )

        Spacer(Modifier.height(32.dp))

        // ── Email ─────────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            leadingIcon   = { Icon(Icons.Default.Email, null) },
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // ── Password ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, null) },
            trailingIcon  = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle password visibility"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp)
        )

        // ── Recovery email (sign-up only) ─────────────────────────────────────
        if (!isLogin) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value         = recoveryEmail,
                onValueChange = { recoveryEmail = it },
                label         = { Text("Recovery Email / Phone (optional)") },
                leadingIcon   = { Icon(Icons.Default.ContactMail, null) },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp)
            )
        }

        // ── Forgot password (login only) ──────────────────────────────────────
        if (isLogin) {
            Spacer(Modifier.height(6.dp))
            Text(
                text     = "Forgot password?",
                color    = PrimaryGreen,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { showForgot = true }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (authState.error != null) {
            Text(
                text  = authState.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (isLogin) viewModel.login(email, password)
                else         viewModel.signup(email, password, recoveryEmail.trim().ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
            enabled  = !authState.isLoading
        ) {
            if (authState.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isLogin) "Sign In" else "Sign Up")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text     = if (isLogin) "Don't have an account? Sign Up"
            else         "Already have an account? Sign In",
            color    = PrimaryGreen,
            modifier = Modifier.clickable { isLogin = !isLogin; authState.let {} }
        )
    }
}

// ── Forgot Password Dialog ────────────────────────────────────────────────────

@Composable
private fun ForgotPasswordDialog(
    email: String,
    onEmailChange: (String) -> Unit,
    sent: Boolean,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Reset Password") },
        text    = {
            if (sent) {
                Text("A password reset link has been sent to $email. Check your inbox.")
            } else {
                Column {
                    Text("Enter your account email and we'll send a reset link.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value         = email,
                        onValueChange = onEmailChange,
                        label         = { Text("Email") },
                        leadingIcon   = { Icon(Icons.Default.Email, null) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("Done", color = PrimaryGreen) }
            } else {
                TextButton(onClick = onSend, enabled = email.isNotBlank()) {
                    Text("Send Link", color = PrimaryGreen)
                }
            }
        },
        dismissButton = {
            if (!sent) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}