package com.example.smartexpensetracker.ui.screens.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.smartexpensetracker.model.RedeemResult
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.viewmodel.SharedListsViewModel

/**
 * Lets a user type a 6-character invite code to join an existing shared list.
 *
 * Shows live feedback for each failure mode (expired, used, etc.).
 */
@Composable
fun JoinListDialog(
    viewModel: SharedListsViewModel,
    onDismiss: () -> Unit,
    onJoined: (listId: String, listName: String) -> Unit
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var isRedeeming by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isRedeeming) onDismiss() },
        title = { Text("Join a shared list") },
        text = {
            Column {
                Text(
                    "Enter the 6-character invite code shared with you.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { newValue ->
                        // Limit length, uppercase, strip whitespace
                        val cleaned = newValue.uppercase().filter { it.isLetterOrDigit() }.take(6)
                        code = cleaned
                        errorText = null
                    },
                    label = { Text("Code") },
                    singleLine = true,
                    enabled = !isRedeeming,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (isRedeeming) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryGreen
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isRedeeming = true
                    errorText = null
                    viewModel.redeemCode(code) { result ->
                        isRedeeming = false
                        when (result) {
                            is RedeemResult.Success -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "Joined \"${result.listName}\"",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onJoined(result.listId, result.listName)
                                onDismiss()
                            }
                            RedeemResult.NotFound      -> errorText = "Invalid code"
                            RedeemResult.Expired       -> errorText = "This code has expired"
                            RedeemResult.AlreadyUsed   -> errorText = "This code has already been used"
                            RedeemResult.AlreadyMember -> {
                                android.widget.Toast.makeText(
                                    context,
                                    "You're already a member of this list",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                            is RedeemResult.Error      -> errorText = result.message
                        }
                    }
                },
                enabled = code.length == 6 && !isRedeeming,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRedeeming) { Text("Cancel") }
        }
    )
}