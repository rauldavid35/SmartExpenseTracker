package com.example.smartexpensetracker.ui.screens.lists

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.smartexpensetracker.model.SharedListData
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.viewmodel.SharedListsViewModel

@Composable
fun ShareListDialog(
    list: SharedListData,
    viewModel: SharedListsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val code by viewModel.lastGeneratedCode.collectAsState()
    var emailInput by remember { mutableStateOf("") }

    LaunchedEffect(list.id) {
        viewModel.clearGeneratedCode()
        viewModel.generateInviteCode(list)
    }

    Dialog(onDismissRequest = {
        viewModel.clearGeneratedCode()
        onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "Share \"${list.name}\"",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Single-use code, expires in 24 hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    color = LightMint,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (code == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = PrimaryGreen,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                code!!,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 36.sp,
                                    letterSpacing = 8.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = PrimaryGreen
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CodeActionButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        enabled = code != null
                    ) {
                        copyToClipboard(context, code!!)
                    }
                    CodeActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        enabled = code != null
                    ) {
                        shareCode(context, code!!, list.name)
                    }
                    CodeActionButton(
                        icon = Icons.Default.Refresh,
                        label = "New",
                        enabled = code != null
                    ) {
                        viewModel.clearGeneratedCode()
                        viewModel.generateInviteCode(list)
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, null, tint = PrimaryGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Or invite by email",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Recipient email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.sendEmailInvite(list, emailInput)
                        emailInput = ""
                    },
                    enabled = emailInput.isNotBlank() && emailInput.contains("@"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send invite") }

                Spacer(Modifier.height(20.dp))

                TextButton(
                    onClick = {
                        viewModel.clearGeneratedCode()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Done") }
            }
        }
    }
}

@Composable
private fun CodeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (enabled) LightMint else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Icon(icon, label, tint = PrimaryGreen)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Shared list invite code", code))
    android.widget.Toast.makeText(context, "Code copied", android.widget.Toast.LENGTH_SHORT).show()
}

private fun shareCode(context: Context, code: String, listName: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Join my shopping list \"$listName\" in Smart Expense Tracker!\n\nInvite code: $code\n(Expires in 24 hours, single use)"
        )
    }
    context.startActivity(Intent.createChooser(intent, "Share invite via…"))
}