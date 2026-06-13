package com.example.smartexpensetracker.model

data class ReceiptItem(
    val name: String = "",
    val price: Double = 0.0
)
data class ExpenseTransaction(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val date: Long = 0L,
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val items: List<ReceiptItem> = emptyList()
)