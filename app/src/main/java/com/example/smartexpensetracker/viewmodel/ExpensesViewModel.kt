package com.example.smartexpensetracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartexpensetracker.model.ExpenseTransaction
import com.example.smartexpensetracker.model.PhotonResult
import com.example.smartexpensetracker.repository.CategoryRepository
import com.example.smartexpensetracker.repository.ExpensesRepository
import com.example.smartexpensetracker.repository.LocationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

class ExpensesViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val expensesRepository = ExpensesRepository()
    private val categoryRepo = CategoryRepository()
    private val locationRepo = LocationRepository()

    private val _expenses = MutableStateFlow<List<ExpenseTransaction>>(emptyList())
    val expenses: StateFlow<List<ExpenseTransaction>> = _expenses.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PhotonResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private var lastLat = 0.0
    private var lastLon = 0.0

    init {
        fetchExpenses()
        fetchCategories()
        observeSearchQuery()
    }

    private fun fetchExpenses() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            expensesRepository.getExpenses(userId).collect { list ->
                _expenses.value = list
                _isLoading.value = false
            }
        }
    }

    private fun fetchCategories() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            categoryRepo.seedDefaultCategories(userId)
            categoryRepo.getCategories(userId).collect { list ->
                _categories.value = list
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(1000L)
                .filter { it.length >= 3 }
                .distinctUntilChanged()
                .collect { query ->
                    val results = locationRepo.searchLocations(query, lastLat, lastLon)
                    _searchResults.value = results
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
        viewModelScope.launch {
            expensesRepository.addExpense(userId, expense)
        }
    }

    fun editExpense(id: String, name: String, amount: Double, category: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            expensesRepository.editExpense(userId, id, name, amount, category)
        }
    }

    fun deleteExpense(id: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            expensesRepository.deleteExpense(userId, id)
        }
    }

    fun addCustomCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            categoryRepo.addCategory(userId, name)
        }
    }

    private fun normalizeString(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
    }

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