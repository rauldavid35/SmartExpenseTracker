package com.example.smartexpensetracker.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CategoryRepository {
    private val db = FirebaseFirestore.getInstance()

    // Referință dinamică către colecția utilizatorului
    private fun getCollection(userId: String) =
        db.collection("users").document(userId).collection("categories")

    fun getCategories(userId: String): Flow<List<String>> = callbackFlow {
        val subscription = getCollection(userId).addSnapshotListener { snapshot, _ ->
            val categories = snapshot?.documents?.map { it.id } ?: emptyList()
            trySend(categories)
        }
        awaitClose { subscription.remove() }
    }

    suspend fun addCategory(userId: String, categoryName: String) {
        if (categoryName.isNotBlank()) {
            getCollection(userId).document(categoryName).set(mapOf("active" to true)).await()
        }
    }

    suspend fun seedDefaultCategories(userId: String) {
        val defaults = listOf("Mâncare", "Transport", "Facturi", "Sănătate", "Divertisment", "Cumpărături")
        val current = getCollection(userId).get().await()
        if (current.isEmpty) {
            defaults.forEach { addCategory(userId, it) }
        }
    }
}