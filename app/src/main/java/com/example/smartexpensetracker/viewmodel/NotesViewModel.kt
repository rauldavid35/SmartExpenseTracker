package com.example.smartexpensetracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.NoteData
import com.example.smartexpensetracker.repository.NotesRepository
import com.example.smartexpensetracker.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(private val context: Context) : ViewModel() {

    private val auth       = FirebaseAuth.getInstance()
    private val repository = NotesRepository()

    private val _notes     = MutableStateFlow<List<NoteData>>(emptyList())
    val notes:     StateFlow<List<NoteData>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                _notes.value = emptyList()
            } else {
                fetchNotes()
            }
        }
    }

    private fun fetchNotes() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repository.getNotes(userId).collect { notes ->
                    _notes.value    = notes
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                _isLoading.value = false
            }
        }
    }
    fun addNote(text: String, remindAt: Long? = null) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.addNote(userId, text, remindAt)
            if (remindAt != null) {
                // Use a stable positive Int as alarm/notification ID
                val alarmId = (text + remindAt).hashCode().and(0x7FFFFFFF)
                NotificationHelper.scheduleNoteReminder(context, alarmId, text, remindAt)
            }
        }
    }

    fun deleteNote(noteId: String) {
        val userId = auth.currentUser?.uid ?: return
        val note = _notes.value.find { it.id == noteId }
        if (note?.remindAt != null) {
            val alarmId = (note.text + note.remindAt).hashCode().and(0x7FFFFFFF)
            NotificationHelper.cancelNoteReminder(context, alarmId)
        }
        viewModelScope.launch { repository.deleteNote(userId, noteId) }
    }

    // ── Factory ───────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotesViewModel(context.applicationContext) as T
    }
}