package com.example.smartexpensetracker.utils

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


data class ReceiptResult(
    val merchant: String,
    val address: String?, // Added this field for the heatmap
    val total: Double,
    val items: List<Pair<String, Double>>
)
// --------------------------------------------

class GeminiReceiptParser(private val apiKey: String) {

    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        safetySettings = safetySettings
    )

    // --- RECEIPT TEXT ANALYSIS ---
    suspend fun parseReceiptText(rawText: String): ReceiptResult? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    You are a receipt scanner. Extract the Merchant Name, Total Amount, Items, and ADDRESS.
                    Rules:
                    1. Fix obvious OCR typos.
                    2. Look for the street address or city name (e.g., "Calea Aurel Vlaicu, Arad").
                    3. If "TOTAL" is missing, SUM the items.
                    4. Return ONLY raw JSON.
                    
                    Format:
                    { 
                      "merchant": "Store Name", 
                      "address": "City or Street Name",
                      "total": 123.45,
                      "items": []
                    }
                    
                    Receipt Text:
                    $rawText
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                parseAiResponse(response.text ?: "")
            } catch (e: Exception) {
                Log.e("GeminiAI", "Parsing Failed: ${e.message}")
                null
            }
        }
    }

    // --- PRODUCT IMAGE ANALYSIS ---
    suspend fun identifyProduct(
        image: Bitmap,
        country: String = "Romania",
        currency: String = "RON"
    ): ReceiptResult? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("GeminiAI", "Analyzing Product for $country in $currency...")

                val prompt = """
                    Identify this product from the image.
                    Your goal is to find a valid purchase link and price for a user in $country.
                    
                    Priority 1: Find a retailer in $country with price in $currency (e.g., eMAG, Altex, or local sites).
                    Priority 2: If not found locally, find a European/Global retailer with price in EUR.
                    
                    Return ONLY raw JSON:
                    { 
                      "product_name": "Exact Product Name", 
                      "price": 123.45 (Number only),
                      "currency": "$currency",
                      "link": "https://..." 
                    }
                """.trimIndent()

                val inputContent = content {
                    image(image)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val textResponse = response.text ?: ""

                // Custom parser for products (re-uses ReceiptResult container)
                parseProductResponse(textResponse)

            } catch (e: Exception) {
                Log.e("GeminiAI", "Product Analysis Failed: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseProductResponse(aiOutput: String): ReceiptResult? {
        try {
            var cleanOutput = aiOutput.replace("```json", "").replace("```", "").trim()
            val startIndex = cleanOutput.indexOf("{")
            val endIndex = cleanOutput.lastIndexOf("}")
            if (startIndex != -1 && endIndex != -1) {
                cleanOutput = cleanOutput.substring(startIndex, endIndex + 1)
            }

            val json = JSONObject(cleanOutput)

            val name = json.optString("product_name", "Unknown Item")
            val link = json.optString("link", "")
            val detectedCurrency = json.optString("currency", "")
            val price = json.optDouble("price", 0.0)

            // Format data into the ReceiptResult structure
            // We put the Link info into the "merchant" field so it displays in the Name box
            val displayInfo = "$name\n$detectedCurrency $price\nLink: $link"

            return ReceiptResult(displayInfo, null, price, emptyList())
        } catch (e: Exception) {
            return null
        }
    }

    // Shared helper to clean and parse JSON (Updated for Address)
    private fun parseAiResponse(aiOutput: String): ReceiptResult? {
        try {
            var cleanOutput = aiOutput.replace("```json", "").replace("```", "").trim()
            val startIndex = cleanOutput.indexOf("{")
            val endIndex = cleanOutput.lastIndexOf("}")
            if (startIndex != -1 && endIndex != -1) {
                cleanOutput = cleanOutput.substring(startIndex, endIndex + 1)
            }

            val json = JSONObject(cleanOutput)
            val merchant = json.optString("merchant", "Unknown Store")
            val address = json.optString("address", "") // <--- Extracts Address now
            var total = json.optDouble("total", 0.0)

            val itemsList = mutableListOf<Pair<String, Double>>()
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    itemsList.add(Pair(itemObj.optString("name"), itemObj.optDouble("price")))
                }
            }

            if (total == 0.0 && itemsList.isNotEmpty()) {
                total = itemsList.sumOf { it.second }
            }

            return ReceiptResult(merchant, address, total, itemsList)
        } catch (e: Exception) {
            return null
        }
    }
}