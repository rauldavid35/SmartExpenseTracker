package com.example.smartexpensetracker.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CategoryRepository {
    private val db = FirebaseFirestore.getInstance()

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

    private val defaultCategories = listOf(
        "Food",
        "Transport",
        "Bills",
        "Health",
        "Entertainment",
        "Shopping"
    )

    // Romanian → English mapping used for migration
    private val romanianToEnglish = mapOf(
        "Mâncare"      to "Food",
        "Mancare"      to "Food",
        "Transport"    to "Transport",   // same
        "Facturi"      to "Bills",
        "Sănătate"     to "Health",
        "Sanatate"     to "Health",
        "Divertisment" to "Entertainment",
        "Cumpărături"  to "Shopping",
        "Cumparaturi"  to "Shopping"
    )

    suspend fun seedDefaultCategories(userId: String) {
        val current = getCollection(userId).get().await()
        if (current.isEmpty) {
            defaultCategories.forEach { addCategory(userId, it) }
        }
    }

    /**
     * Call this ONCE for existing users who have Romanian categories.
     * It deletes each Romanian category and creates its English equivalent.
     * Safe to call multiple times — already-English categories are skipped.
     */
    suspend fun migrateToEnglishCategories(userId: String) {
        val collection = getCollection(userId)
        val current    = collection.get().await()

        current.documents.forEach { doc ->
            val romanian = doc.id
            val english  = romanianToEnglish[romanian]
            if (english != null && english != romanian) {
                // Delete the Romanian doc
                collection.document(romanian).delete().await()
                // Create the English doc (skip if already exists)
                collection.document(english).set(mapOf("active" to true)).await()
            }
        }

        // Ensure all defaults exist (handles partial migrations)
        defaultCategories.forEach { cat ->
            val exists = collection.document(cat).get().await().exists()
            if (!exists) collection.document(cat).set(mapOf("active" to true)).await()
        }
    }
}