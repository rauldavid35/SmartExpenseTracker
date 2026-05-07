package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.repository.NotesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = NotesRepository()

    private val _notes = MutableStateFlow<List<com.example.smartexpensetracker.model.NoteData>>(emptyList())
    val notes: StateFlow<List<com.example.smartexpensetracker.model.NoteData>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchNotes()
    }

    private fun fetchNotes() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            repository.getNotes(userId).collect { notes ->
                _notes.value = notes
                _isLoading.value = false
            }
        }
    }

    fun addNote(text: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.addNote(userId, text)
        }
    }

    fun deleteNote(noteId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.deleteNote(userId, noteId)
        }
    }
}