package com.example.smartexpensetracker.model

data class ProductResult(
    val productName: String,
    val estimatedPrice: Double,
    val currency: String,
    val priceConfidence: String,   // "high" or "low"
    val countryIso: String,
    val storeLinks: List<StoreLink>
)
data class StoreLink(val storeName: String, val searchUrl: String)