package com.example.smartexpensetracker.model

data class ShoppingListData(
    val id: String = "",
    val name: String = "",
    val itemCount: Int = 0,
    val date: Long = 0L
)

data class ShoppingItem(
    val id: String = "",
    val text: String = "",
    val checked: Boolean = false
)