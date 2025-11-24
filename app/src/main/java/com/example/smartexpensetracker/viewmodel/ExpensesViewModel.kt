package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExpenseTransaction(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: Long = 0L,
    // NEW: Location Fields for Heatmap
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

class ExpensesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchExpenses()
    }

    private fun fetchExpenses() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("expenses")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExpenseTransaction::class.java)?.copy(id = doc.id)
                    }
                    _expenses.value = list
                }
            }
    }

    // Updated to accept Location Data
    fun addExpense(
        name: String,
        amount: Double,
        category: String,
        locationName: String = "",
        lat: Double = 0.0,
        lng: Double = 0.0
    ) {
        val userId = auth.currentUser?.uid ?: return
        val expense = ExpenseTransaction(
            name = name,
            amount = amount,
            category = category,
            date = System.currentTimeMillis(),
            locationName = locationName,
            latitude = lat,
            longitude = lng
        )
        db.collection("users").document(userId).collection("expenses").add(expense)
    }

    fun editExpense(id: String, name: String, amount: Double, category: String) {
        val userId = auth.currentUser?.uid ?: return
        val updates = mapOf(
            "name" to name,
            "amount" to amount,
            "category" to category
        )
        db.collection("users").document(userId).collection("expenses").document(id).update(updates)
    }

    fun deleteExpense(id: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("expenses").document(id).delete()
    }
}