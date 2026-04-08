package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.repository.CategoryRepository
import com.example.smartexpensetracker.repository.LocationRepository
import com.example.smartexpensetracker.repository.PhotonResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.debounce
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class ExpenseTransaction(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: Long = 0L,
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

class ExpensesViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val categoryRepo = CategoryRepository()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val locationRepo = LocationRepository()

    // State-uri pentru UI
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PhotonResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var lastLat = 0.0
    private var lastLon = 0.0


    init {
        fetchExpenses()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModelScope.launch {
                categoryRepo.seedDefaultCategories(userId)
                categoryRepo.getCategories(userId).collect { list ->
                    _categories.value = list
                }
            }
        }

        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(1000L) // Așteaptă 1 secundă după ce userul s-a oprit din scris
                .filter { it.length >= 3 }
                .distinctUntilChanged()
                .collect { query ->
                    // Căutăm locația (folosim 0.0 momentan pentru ancora GPS)
                    val results = locationRepo.searchLocations(query, lastLat, lastLon)
                    _searchResults.value = results
                }
        }
    }

    private fun fetchExpenses() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        db.collection("users").document(userId).collection("expenses")
            .addSnapshotListener { snapshot, _ ->
                _isLoading.value = false
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ExpenseTransaction::class.java)?.copy(id = doc.id)
                    }
                    _expenses.value = list
                }
            }
    }

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

    fun addCustomCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            categoryRepo.addCategory(userId, name)
        }
    }

    // --- LOGICA DE DETECTARE ANOMALII ---
    data class AnomalyResult(val isAnomaly: Boolean, val average: Double)

    fun checkAnomaly(amountToCheck: Double, category: String): AnomalyResult {
        val categoryExpenses = _expenses.value.filter {
            it.category == category && it.amount < 0
        }

        if (categoryExpenses.size < 3) return AnomalyResult(false, 0.0)

        val amounts = categoryExpenses.map { abs(it.amount) }
        val mean = amounts.average()

        val variance = amounts.map { Math.pow(it - mean, 2.0) }.average()
        val standardDeviation = Math.sqrt(variance)

        val safeSd = if (standardDeviation < 1.0) 1.0 else standardDeviation
        val zScore = Math.abs(amountToCheck - mean) / safeSd

        val isAnomaly = zScore > 2.5 && amountToCheck > (mean * 2)

        return AnomalyResult(isAnomaly, mean)
    }

    private fun normalizeString(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun clearLocationSearch() {
        _searchResults.value = emptyList()
    }

    fun updateLastLocation(lat: Double, lon: Double) {
        lastLat = lat
        lastLon = lon
    }
}