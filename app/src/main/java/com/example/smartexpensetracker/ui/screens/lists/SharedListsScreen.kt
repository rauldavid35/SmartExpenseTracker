package com.example.smartexpensetracker.ui.screens.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartexpensetracker.model.RedeemResult
import com.example.smartexpensetracker.model.SharedListData
import com.example.smartexpensetracker.ui.theme.ExpenseRed
import com.example.smartexpensetracker.ui.theme.LightMint
import com.example.smartexpensetracker.ui.theme.PrimaryGreen
import com.example.smartexpensetracker.ui.theme.TextSecondary
import com.example.smartexpensetracker.viewmodel.SharedListsViewModel
import com.google.firebase.auth.FirebaseAuth

@Composable
fun SharedListsScreen(
    onBackClick: () -> Unit,
    onListClick: (listId: String, listName: String) -> Unit,
    viewModel: SharedListsViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val myUid = currentUser?.uid ?: ""

    val sharedLists    by viewModel.sharedLists.collectAsState()
    val pendingInvites by viewModel.pendingInvites.collectAsState()
    val userMessage    by viewModel.userMessage.collectAsState()
    val isOnline       by viewModel.isOnline.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog   by remember { mutableStateOf(false) }
    var shareTarget      by remember { mutableStateOf<SharedListData?>(null) }
    var renameTarget     by remember { mutableStateOf<SharedListData?>(null) }
    var deleteTarget     by remember { mutableStateOf<SharedListData?>(null) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearUserMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGreen)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Shared Lists",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showJoinDialog = true },
                enabled = isOnline,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isOnline) LightMint else Color.LightGray.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.Default.GroupAdd, "Join with code",
                    tint = if (isOnline) PrimaryGreen else TextSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { if (isOnline) showCreateDialog = true },
                containerColor = if (isOnline) PrimaryGreen else Color.LightGray,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Add, "New", tint = Color.White)
            }
        }

        if (!isOnline) {
            Surface(
                color = Color(0xFFFFF3CD),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = Color(0xFF856404))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "You're offline — only checking items is available",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF856404)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pendingInvites.isNotEmpty()) {
                item {
                    Text(
                        "Pending invitations",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(pendingInvites) { invite ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LightMint),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "${invite.ownerEmail} invited you to:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                invite.listName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(12.dp))
                            Row {
                                Button(
                                    onClick = {
                                        viewModel.acceptEmailInvite(invite) { result ->
                                            when (result) {
                                                is RedeemResult.Success ->
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Joined \"${result.listName}\"",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                RedeemResult.AlreadyMember ->
                                                    android.widget.Toast.makeText(
                                                        context, "Already a member",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                is RedeemResult.Error ->
                                                    android.widget.Toast.makeText(
                                                        context, result.message,
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                else ->
                                                    android.widget.Toast.makeText(
                                                        context, "Could not accept invite",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                            }
                                        }
                                    },
                                    enabled = isOnline,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                                ) { Text("Accept") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.declineEmailInvite(invite) },
                                    enabled = isOnline
                                ) { Text("Decline") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Text(
                        "My shared lists",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (sharedLists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Group, null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No shared lists yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a new one with + or join with a code",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            items(sharedLists, key = { it.id }) { list ->
                val isOwner = list.ownerId == myUid
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onListClick(list.id, list.name) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(LightMint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Group, null, tint = PrimaryGreen)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(list.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("${list.itemCount} items · ${list.members.size} member")
                                    if (list.members.size != 1) append("s")
                                    if (!isOwner) append(" · owned by ${list.ownerEmail}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (isOwner) {
                            IconButton(
                                onClick = { shareTarget = list },
                                enabled = isOnline
                            ) {
                                Icon(
                                    Icons.Default.Share, "Share",
                                    tint = if (isOnline) PrimaryGreen else Color.LightGray
                                )
                            }
                            IconButton(
                                onClick = { renameTarget = list },
                                enabled = isOnline
                            ) {
                                Icon(
                                    Icons.Default.Edit, null,
                                    tint = if (isOnline) TextSecondary else Color.LightGray
                                )
                            }
                        }
                        IconButton(
                            onClick = { deleteTarget = list },
                            enabled = isOnline
                        ) {
                            Icon(
                                if (isOwner) Icons.Default.Delete else Icons.AutoMirrored.Filled.ArrowBack,
                                if (isOwner) "Delete" else "Leave",
                                tint = if (isOnline) ExpenseRed else Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }


    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New shared list") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createList(name)
                        showCreateDialog = false
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinDialog) {
        JoinListDialog(
            viewModel = viewModel,
            onDismiss = { showJoinDialog = false },
            onJoined  = { listId, listName -> onListClick(listId, listName) }
        )
    }

    shareTarget?.let { list ->
        ShareListDialog(
            list = list,
            viewModel = viewModel,
            onDismiss = { shareTarget = null }
        )
    }

    renameTarget?.let { list ->
        var name by remember(list.id) { mutableStateOf(list.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename list") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameList(list.id, name)
                        renameTarget = null
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { list ->
        val isOwner = list.ownerId == myUid
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (isOwner) "Delete list?" else "Leave list?") },
            text = {
                Text(
                    if (isOwner) "\"${list.name}\" will be permanently deleted for all members."
                    else         "You will no longer see \"${list.name}\"."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOrLeaveList(list)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) { Text(if (isOwner) "Delete" else "Leave") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}