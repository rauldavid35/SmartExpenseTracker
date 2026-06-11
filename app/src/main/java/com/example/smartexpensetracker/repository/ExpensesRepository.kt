package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.ExpenseTransaction
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ExpensesRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun getCollection(userId: String) =
        db.collection("users").document(userId).collection("expenses")

    fun getExpenses(userId: String): Flow<List<ExpenseTransaction>> = callbackFlow {
        val subscription = getCollection(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExpenseTransaction::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.date }
                    trySend(list)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addExpense(userId: String, expense: ExpenseTransaction) {
        getCollection(userId).add(expense).await()
    }

    suspend fun editExpense(
        userId: String,
        id: String,
        name: String,
        amount: Double,
        category: String
    ) {
        val updates = mapOf(
            "name" to name,
            "amount" to amount,
            "category" to category
        )
        getCollection(userId).document(id).update(updates).await()
    }

    suspend fun deleteExpense(userId: String, id: String) {
        getCollection(userId).document(id).delete().await()
    }
}