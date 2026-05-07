package com.example.smartexpensetracker.repository

import com.example.smartexpensetracker.model.BudgetCategorySetting
import com.example.smartexpensetracker.model.MonthlyBudget
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BudgetRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun getCurrentMonth() =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    private fun getBudgetDoc(userId: String) =
        db.collection("users").document(userId)
            .collection("budgets").document(getCurrentMonth())

    private fun getCategorySettings(userId: String) =
        getBudgetDoc(userId).collection("categorySettings")

    fun getMonthlyBudget(userId: String): Flow<MonthlyBudget?> = callbackFlow {
        val subscription = getBudgetDoc(userId)
            .addSnapshotListener { snapshot, _ ->
                val budget = snapshot?.toObject(MonthlyBudget::class.java)
                trySend(budget)
            }
        awaitClose { subscription.remove() }
    }

    fun getCategorySettingsFlow(userId: String): Flow<List<BudgetCategorySetting>> = callbackFlow {
        val subscription = getCategorySettings(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val settings = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(BudgetCategorySetting::class.java)?.copy(name = doc.id)
                    }
                    trySend(settings)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun updateMonthlyLimit(userId: String, newLimit: Double) {
        getBudgetDoc(userId)
            .set(mapOf("totalLimit" to newLimit), SetOptions.merge()).await()
    }

    suspend fun saveCategory(
        userId: String,
        name: String,
        limit: Double,
        colorHex: String
    ) {
        val setting = BudgetCategorySetting(name, limit, colorHex)
        getCategorySettings(userId).document(name).set(setting).await()
    }

    suspend fun deleteCategory(userId: String, name: String) {
        getCategorySettings(userId).document(name).delete().await()
    }
}