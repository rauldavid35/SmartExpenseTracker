package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.RedeemResult
import com.example.smartexpensetracker.model.SharedListData
import com.example.smartexpensetracker.model.SharedListEmailInvite
import com.example.smartexpensetracker.model.SharedListItem
import com.example.smartexpensetracker.repository.SharedListRepository
import com.example.smartexpensetracker.utils.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedListsViewModel : ViewModel() {

    private val auth       = FirebaseAuth.getInstance()
    private val repository = SharedListRepository()

    private val _sharedLists = MutableStateFlow<List<SharedListData>>(emptyList())
    val sharedLists: StateFlow<List<SharedListData>> = _sharedLists.asStateFlow()

    private val _currentItems = MutableStateFlow<List<SharedListItem>>(emptyList())
    val currentItems: StateFlow<List<SharedListItem>> = _currentItems.asStateFlow()

    private val _pendingInvites = MutableStateFlow<List<SharedListEmailInvite>>(emptyList())
    val pendingInvites: StateFlow<List<SharedListEmailInvite>> = _pendingInvites.asStateFlow()

    private val _lastGeneratedCode = MutableStateFlow<String?>(null)
    val lastGeneratedCode: StateFlow<String?> = _lastGeneratedCode.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = NetworkMonitor.isOnline

    private var listsJob:    Job? = null
    private var itemsJob:    Job? = null
    private var invitesJob:  Job? = null

    private var lastUid: String? = null

    init {
        lastUid = auth.currentUser?.uid
        auth.currentUser?.let { user ->
            startListeningLists(user.uid)
            user.email?.let { startListeningPendingInvites(it) }
        }

        auth.addAuthStateListener { fa ->
            val newUid = fa.currentUser?.uid
            if (newUid == lastUid) return@addAuthStateListener

            lastUid = newUid
            listsJob?.cancel();    listsJob   = null
            itemsJob?.cancel();    itemsJob   = null
            invitesJob?.cancel();  invitesJob = null

            if (newUid == null) {
                _sharedLists.value    = emptyList()
                _currentItems.value   = emptyList()
                _pendingInvites.value = emptyList()
                _lastGeneratedCode.value = null
            } else {
                startListeningLists(newUid)
                fa.currentUser?.email?.let { startListeningPendingInvites(it) }
            }
        }
    }

    private fun startListeningLists(uid: String) {
        listsJob = viewModelScope.launch {
            try {
                repository.getSharedLists(uid).collect { _sharedLists.value = it }
            } catch (_: Exception) { }
        }
    }

    private fun startListeningPendingInvites(email: String) {
        invitesJob = viewModelScope.launch {
            try {
                repository.getPendingInvitesForEmail(email).collect { _pendingInvites.value = it }
            } catch (_: Exception) { }
        }
    }
    private fun requireOnline(action: String): Boolean {
        if (!NetworkMonitor.isOnline.value) {
            _userMessage.value = "You're offline — $action requires internet"
            return false
        }
        return true
    }

    fun createList(name: String) {
        val user = auth.currentUser ?: return
        if (name.isBlank()) return
        if (!requireOnline("creating a list")) return
        viewModelScope.launch {
            try {
                repository.createList(user.uid, user.email ?: "", name.trim())
            } catch (e: Exception) {
                _userMessage.value = "Failed to create list: ${e.message}"
            }
        }
    }

    fun renameList(listId: String, newName: String) {
        if (newName.isBlank()) return
        if (!requireOnline("renaming a list")) return
        viewModelScope.launch {
            try { repository.renameList(listId, newName.trim()) }
            catch (e: Exception) { _userMessage.value = "Rename failed: ${e.message}" }
        }
    }

    fun deleteOrLeaveList(list: SharedListData) {
        val uid = auth.currentUser?.uid ?: return
        if (!requireOnline("deleting or leaving a list")) return
        viewModelScope.launch {
            try {
                if (list.ownerId == uid) repository.deleteList(list.id)
                else                     repository.leaveList(list.id, uid)
            } catch (e: Exception) {
                _userMessage.value = "Operation failed: ${e.message}"
            }
        }
    }

    fun startListeningItems(listId: String) {
        val uid = auth.currentUser?.uid ?: return
        itemsJob?.cancel()
        _currentItems.value = emptyList()
        android.util.Log.d("SharedList", "startListeningItems called for listId=$listId, uid=$uid")
        itemsJob = viewModelScope.launch {
            try {
                repository.getItems(listId).collect { items ->
                    android.util.Log.d("SharedList", "Items received: ${items.size} items for listId=$listId")
                    _currentItems.value = items
                }
            } catch (e: Exception) {
                android.util.Log.e("SharedList", "Error listening to items", e)
            }
        }
    }

    fun stopListeningItems() {
        itemsJob?.cancel()
        itemsJob = null
        _currentItems.value = emptyList()
    }

    fun addItem(listId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        if (text.isBlank()) return
        if (!requireOnline("adding items")) return
        viewModelScope.launch {
            try { repository.addItem(listId, text.trim(), uid) }
            catch (e: Exception) { _userMessage.value = "Add failed: ${e.message}" }
        }
    }

    fun toggleItem(listId: String, itemId: String, checked: Boolean) {
        viewModelScope.launch {
            try { repository.toggleItem(listId, itemId, checked) }
            catch (e: Exception) { _userMessage.value = "Update failed: ${e.message}" }
        }
    }

    fun deleteItem(listId: String, itemId: String) {
        if (!requireOnline("deleting items")) return
        viewModelScope.launch {
            try { repository.deleteItem(listId, itemId) }
            catch (e: Exception) { _userMessage.value = "Delete failed: ${e.message}" }
        }
    }

    fun generateInviteCode(list: SharedListData) {
        val user = auth.currentUser ?: return
        if (list.ownerId != user.uid) {
            _userMessage.value = "Only the owner can generate invite codes"
            return
        }
        if (!requireOnline("generating an invite code")) return
        viewModelScope.launch {
            try {
                val code = repository.generateInviteCode(
                    listId     = list.id,
                    ownerId    = user.uid,
                    ownerEmail = user.email ?: "",
                    listName   = list.name
                )
                _lastGeneratedCode.value = code
            } catch (e: Exception) {
                _userMessage.value = "Could not generate code: ${e.message}"
            }
        }
    }

    fun clearGeneratedCode() { _lastGeneratedCode.value = null }

    fun redeemCode(code: String, onResult: (RedeemResult) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(RedeemResult.Error("Not signed in"))
            return
        }
        if (!NetworkMonitor.isOnline.value) {
            onResult(RedeemResult.Error("You're offline — joining requires internet"))
            return
        }
        viewModelScope.launch {
            val result = repository.redeemInviteCode(code, uid)
            onResult(result)
        }
    }

    fun sendEmailInvite(list: SharedListData, inviteeEmail: String) {
        val user = auth.currentUser ?: return
        if (list.ownerId != user.uid) {
            _userMessage.value = "Only the owner can invite users"
            return
        }
        if (!requireOnline("sending email invites")) return
        val trimmed = inviteeEmail.trim().lowercase()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            _userMessage.value = "Please enter a valid email"
            return
        }
        if (trimmed == user.email?.lowercase()) {
            _userMessage.value = "You cannot invite yourself"
            return
        }
        viewModelScope.launch {
            try {
                repository.createEmailInvite(
                    listId       = list.id,
                    ownerId      = user.uid,
                    ownerEmail   = user.email ?: "",
                    listName     = list.name,
                    inviteeEmail = trimmed
                )
                _userMessage.value = "Invite sent to $trimmed"
            } catch (e: Exception) {
                _userMessage.value = "Failed to send invite: ${e.message}"
            }
        }
    }

    fun acceptEmailInvite(invite: SharedListEmailInvite, onResult: (RedeemResult) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) { onResult(RedeemResult.Error("Not signed in")); return }
        if (!NetworkMonitor.isOnline.value) {
            onResult(RedeemResult.Error("You're offline — accepting requires internet"))
            return
        }
        viewModelScope.launch {
            val result = repository.acceptEmailInvite(invite.id, uid)
            onResult(result)
        }
    }

    fun declineEmailInvite(invite: SharedListEmailInvite) {
        if (!requireOnline("declining invites")) return
        viewModelScope.launch {
            try { repository.declineEmailInvite(invite.id) }
            catch (_: Exception) { /* silent */ }
        }
    }

    fun clearUserMessage() { _userMessage.value = null }
}