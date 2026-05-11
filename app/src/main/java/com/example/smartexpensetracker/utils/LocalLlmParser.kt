package com.example.smartexpensetracker.utils

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class LocalLlmParser(context: Context) {

    private val engine = LocalLlmEngine.get(context)

    fun isReady(): Boolean = engine.isModelInstalled()

    // ── Voice: expense ────────────────────────────────────────────────────────
    suspend fun parseExpense(
        transcript: String,
        knownCategories: List<String> = emptyList()
    ): VoiceParseResult.ExpenseResult? {
        // Super-short prompt with one-shot example — small models follow examples
        // far better than abstract instructions.
        val sys = "Extract expense. Output JSON only."
        val user = """
            Example:
            Input: "spent 45 on groceries at Lidl"
            Output: {"name":"Groceries","amount":45,"category":"Food","location":"Lidl"}

            Input: "$transcript"
            Output:
        """.trimIndent()

        val raw = engine.complete(sys, user, timeoutMs = 60_000L) ?: return null
        return parseExpenseJson(raw)
    }

    // ── Voice: shopping list ──────────────────────────────────────────────────
    suspend fun parseShoppingList(transcript: String): VoiceParseResult.ShoppingListResult? {
        val sys = "Extract shopping list. Output JSON only."
        val user = """
            Example:
            Input: "add milk bread and eggs to weekend list"
            Output: {"listName":"Weekend","items":["Milk","Bread","Eggs"]}

            Input: "$transcript"
            Output:
        """.trimIndent()

        val raw = engine.complete(sys, user, timeoutMs = 60_000L) ?: return null
        return parseShoppingListJson(raw)
    }

    // ── Receipt: OCR text → structured ────────────────────────────────────────
    suspend fun parseReceiptText(rawText: String): ReceiptResult? {
        // Truncate very long receipts — small models lose focus past ~1000 chars
        val trimmed = if (rawText.length > 2000) rawText.take(2000) else rawText

        val sys = "Parse receipt to JSON only."
        val user = """
            Receipt:
            $trimmed

            JSON: {"merchant":string,"total":number,"items":[{"name":string,"price":number}]}
        """.trimIndent()

        val raw = engine.complete(sys, user, timeoutMs = 120_000L) ?: return null
        return parseReceiptJson(raw)
    }

    // ── JSON parsers (unchanged from before) ──────────────────────────────────

    private fun parseExpenseJson(raw: String): VoiceParseResult.ExpenseResult? {
        return try {
            val json = JSONObject(extractJsonObject(raw) ?: return null)
            val name = json.optString("name").ifBlank { return null }
            VoiceParseResult.ExpenseResult(
                name = name,
                amount = json.optDouble("amount").takeIf { !it.isNaN() && it > 0 },
                category = json.optString("category").ifBlank { null }?.takeIf { it != "null" },
                location = json.optString("location").ifBlank { null }?.takeIf { it != "null" },
                confidence = if (json.optDouble("amount").let { !it.isNaN() && it > 0 })
                    VoiceParseResult.ExpenseResult.Confidence.HIGH
                else VoiceParseResult.ExpenseResult.Confidence.LOW
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseExpenseJson failed: ${e.message}; raw=$raw")
            null
        }
    }

    private fun parseShoppingListJson(raw: String): VoiceParseResult.ShoppingListResult? {
        return try {
            val json = JSONObject(extractJsonObject(raw) ?: return null)
            val name = json.optString("listName").ifBlank { return null }
            val arr = json.optJSONArray("items") ?: JSONArray()
            val items = (0 until arr.length()).map { arr.getString(it) }
                .filter { it.isNotBlank() && it.length > 1 }
                .distinct()
            if (items.isEmpty()) return null
            VoiceParseResult.ShoppingListResult(
                listName = name, items = items,
                confidence = VoiceParseResult.ShoppingListResult.Confidence.HIGH
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseShoppingListJson failed: ${e.message}; raw=$raw")
            null
        }
    }

    private fun parseReceiptJson(raw: String): ReceiptResult? {
        return try {
            val json = JSONObject(extractJsonObject(raw) ?: return null)
            val merchant = json.optString("merchant", "Unknown Store").ifBlank { "Unknown Store" }
            val address = json.optString("address").ifBlank { null }?.takeIf { it != "null" }
            var total = json.optDouble("total", 0.0).let { if (it.isNaN()) 0.0 else it }

            val items = mutableListOf<Pair<String, Double>>()
            val arr = json.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        val itemName = obj.optString("name")
                        if (itemName.isNotBlank()) {
                            val price = obj.optDouble("price", 0.0).let { if (it.isNaN()) 0.0 else it }
                            items.add(itemName to price)
                        }
                    }
                }
            }
            val itemsSum = items.sumOf { it.second }
            if (total == 0.0 && itemsSum > 0) total = itemsSum
            if (itemsSum > 0 && total > 0 && kotlin.math.abs(total - itemsSum) / itemsSum > 0.30) {
                Log.w(TAG, "Total ($total) diverges from items sum ($itemsSum) by >30%, using items sum")
                total = itemsSum
            }

            ReceiptResult(merchant, address, total, items)
        } catch (e: Exception) {
            Log.w(TAG, "parseReceiptJson failed: ${e.message}; raw=$raw")
            null
        }
    }

    private fun extractJsonObject(raw: String): String? {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    companion object {
        private const val TAG = "LocalLlmParser"
    }
}