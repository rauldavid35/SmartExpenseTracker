package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import com.example.smartexpensetracker.repository.NoteData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _notes = MutableStateFlow<List<NoteData>>(emptyList())
    val notes: StateFlow<List<NoteData>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchNotes()
    }

    private fun fetchNotes() {
        val userId = auth.currentUser?.uid ?: return

        _isLoading.value = true
        db.collection("users").document(userId).collection("notes")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                _isLoading.value = false
                if (e != null || snapshot == null) return@addSnapshotListener

                val notesList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(NoteData::class.java)?.copy(id = doc.id)
                }
                _notes.value = notesList
            }
    }

    fun addNote(text: String) {
        val userId = auth.currentUser?.uid ?: return
        val note = NoteData(text = text, date = System.currentTimeMillis())

        db.collection("users").document(userId).collection("notes").add(note)
    }

    fun deleteNote(noteId: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("notes").document(noteId).delete()
    }
}