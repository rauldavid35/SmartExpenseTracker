package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.NoteData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotesRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun getCollection(userId: String) =
        db.collection("users").document(userId).collection("notes")

    fun getNotes(userId: String): Flow<List<NoteData>> = callbackFlow {
        val subscription = getCollection(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                // Original checked both error AND null snapshot
                if (error != null || snapshot == null) return@addSnapshotListener
                val notes = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(NoteData::class.java)?.copy(id = doc.id)
                }
                trySend(notes)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addNote(userId: String, text: String) {
        val note = NoteData(
            text = text,
            date = System.currentTimeMillis()
        )
        getCollection(userId).add(note).await()
    }

    suspend fun deleteNote(userId: String, noteId: String) {
        getCollection(userId).document(noteId).delete().await()
    }
}