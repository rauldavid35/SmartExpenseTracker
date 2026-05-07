package com.example.smartexpensetracker.utils

import android.graphics.Bitmap
import android.util.Log
import com.example.smartexpensetracker.model.ProductResult
import com.example.smartexpensetracker.model.StoreLink
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class ReceiptResult(
    val merchant: String,
    val address: String?,
    val total: Double,
    val items: List<Pair<String, Double>>
)

private data class StoreTemplate(val name: String, val urlTemplate: String)

private val STORE_REGISTRY: Map<String, List<StoreTemplate>> = mapOf(
    "RO" to listOf(
        StoreTemplate("eMAG Romania",  "https://www.emag.ro/search/{QUERY}"),
        StoreTemplate("Altex Romania", "https://www.altex.ro/cauta/{QUERY}")
    ),
    "DE" to listOf(
        StoreTemplate("MediaMarkt DE", "https://www.mediamarkt.de/de/search.html?query={QUERY}"),
        StoreTemplate("Amazon DE",     "https://www.amazon.de/s?k={QUERY}")
    ),
    "FR" to listOf(
        StoreTemplate("Amazon FR",     "https://www.amazon.fr/s?k={QUERY}"),
        StoreTemplate("Fnac France",   "https://www.fnac.com/SearchResult/ResultSet.aspx?SCat=0&sft=1&Search={QUERY}")
    ),
    "GB" to listOf(
        StoreTemplate("Amazon UK",     "https://www.amazon.co.uk/s?k={QUERY}"),
        StoreTemplate("Currys UK",     "https://www.currys.co.uk/search?q={QUERY}")
    ),
    "US" to listOf(
        StoreTemplate("Amazon US",     "https://www.amazon.com/s?k={QUERY}"),
        StoreTemplate("Best Buy US",   "https://www.bestbuy.com/site/searchpage.jsp?st={QUERY}")
    ),
    "CA" to listOf(
        StoreTemplate("Amazon CA",     "https://www.amazon.ca/s?k={QUERY}"),
        StoreTemplate("Best Buy CA",   "https://www.bestbuy.ca/en-ca/search?query={QUERY}")
    ),
    "AU" to listOf(
        StoreTemplate("Amazon AU",     "https://www.amazon.com.au/s?k={QUERY}"),
        StoreTemplate("JB Hi-Fi",      "https://www.jbhifi.com.au/search?q={QUERY}")
    ),
    "_INTERNATIONAL" to listOf(
        StoreTemplate("Amazon",        "https://www.amazon.com/s?k={QUERY}"),
        StoreTemplate("eBay",          "https://www.ebay.com/sch/i.html?_nkw={QUERY}")
    )
)

private fun storesFor(countryIso: String?): List<StoreTemplate> =
    if (countryIso != null) STORE_REGISTRY[countryIso.uppercase()]
        ?: STORE_REGISTRY["_INTERNATIONAL"]!!
    else STORE_REGISTRY["_INTERNATIONAL"]!!

private fun buildSearchUrl(template: String, productName: String): String =
    template.replace("{QUERY}", URLEncoder.encode(productName, "UTF-8"))

// ─────────────────────────────────────────────────────────────────────────────
// GeminiReceiptParser
// ─────────────────────────────────────────────────────────────────────────────

class GeminiReceiptParser(private val apiKey: String) {

    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT,        BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH,       BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val generativeModel = GenerativeModel(
        modelName      = "gemini-2.5-flash",
        apiKey         = apiKey,
        safetySettings = safetySettings
    )

    // ── Receipt parsing ───────────────────────────────────────────────────────

    suspend fun parseReceiptText(rawText: String): ReceiptResult? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = """
                    LANGUAGE RULE: Respond only in English.

                    You are an expert receipt-parsing engine. Your ONLY output must be a single raw JSON object — no markdown, no code fences, no explanation.

                    STEP 1 – MERCHANT NAME
                    • Usually the largest text at the top of the receipt.
                    • Fix OCR errors: 0↔O, 1↔I/l, rn↔m. Use canonical trademark spelling.

                    STEP 2 – ADDRESS
                    • Look for street, city, or postal code. Romanian signals: "Str.", "Calea", "Bd.", "Nr.", "Jud."
                    • If none found, set address to null — do NOT invent one.

                    STEP 3 – LINE ITEMS
                    • Extract every product line with a name and price.
                    • Ignore subtotals, taxes, discounts, payment rows.
                    • Fix OCR decimals: "1O.5O" → 10.50.

                    STEP 4 – TOTAL
                    • Search for: TOTAL, TOTAL DE PLATA, SUMA, AMOUNT DUE, SUMĂ, TOT, TTL.
                    • Use explicit total if found. Otherwise sum all item prices.
                    • Total must NEVER be 0.0 unless the receipt is genuinely empty.

                    STEP 5 – OUTPUT (exact schema, nothing else):
                    {
                      "merchant": "<string>",
                      "address": "<string or null>",
                      "total": <number>,
                      "items": [ { "name": "<string>", "price": <number> } ]
                    }

                    RECEIPT TEXT:
                    ---
                    $rawText
                    ---
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                parseReceiptResponse(response.text ?: "")
            } catch (e: Exception) {
                Log.e("GeminiAI", "Receipt parsing failed: ${e.message}")
                null
            }
        }
    }

    // ── Product identification — location-aware ───────────────────────────────

    suspend fun identifyProduct(
        image: Bitmap,
        countryIso: String?,
        currency: String = "USD"
    ): ProductResult? {
        return withContext(Dispatchers.IO) {
            try {
                val stores    = storesFor(countryIso)
                val storeList = stores.joinToString(", ") { it.name }
                val isLocal   = countryIso != null && STORE_REGISTRY.containsKey(countryIso.uppercase())
                val marketNote = if (isLocal)
                    "The user is in ${countryIso!!.uppercase()}. Estimate price as sold at: $storeList."
                else
                    "User location unknown. Use international pricing (Amazon, eBay global)."

                Log.d("GeminiAI", "Product scan: countryIso=$countryIso stores=$storeList")

                val prompt = """
                    LANGUAGE RULE: Respond only in English.

                    You are a product identification and pricing agent.
                    $marketNote

                    Analyze the image and identify the product.

                    STEP 1 – IDENTIFY
                    • Full product name: brand + model + key specs.
                    • Example: "Logitech MX Master 3S Wireless Mouse" not just "mouse".
                    • If uncertain: best guess + set price_confidence to "low".

                    STEP 2 – PRICE
                    • Estimate retail price in $currency at: $storeList.
                    • Use your most recent training data for this market — NOT global average prices.
                    • price_confidence = "high" if you are confident. "low" if guessing.
                    • If genuinely unknown: estimated_price = 0, price_confidence = "low".

                    STEP 3 – OUTPUT (exact schema, no markdown):
                    {
                      "product_name": "<full product name in English>",
                      "estimated_price": <number>,
                      "price_confidence": "high" or "low",
                      "currency": "$currency"
                    }
                """.trimIndent()

                val inputContent = content {
                    image(image)
                    text(prompt)
                }

                val response = generativeModel.generateContent(inputContent)
                parseProductResponse(response.text ?: "", countryIso, stores, currency)

            } catch (e: Exception) {
                Log.e("GeminiAI", "Product identification failed: ${e.message}")
                null
            }
        }
    }

    // ── Private parsers ───────────────────────────────────────────────────────

    private fun parseProductResponse(
        aiOutput: String,
        countryIso: String?,
        stores: List<StoreTemplate>,
        currency: String
    ): ProductResult? {
        try {
            val json        = extractJsonObject(aiOutput) ?: return null
            val productName = json.optString("product_name", "").ifBlank { return null }
            val price       = json.optDouble("estimated_price", 0.0)
            val confidence  = json.optString("price_confidence", "low")
            val cur         = json.optString("currency", currency)

            val storeLinks = stores.map { store ->
                StoreLink(
                    storeName = store.name,
                    searchUrl = buildSearchUrl(store.urlTemplate, productName)
                )
            }

            return ProductResult(
                productName     = productName,
                estimatedPrice  = price,
                currency        = cur,
                priceConfidence = confidence,
                countryIso      = countryIso ?: "INTL",
                storeLinks      = storeLinks
            )
        } catch (e: Exception) {
            Log.e("GeminiAI", "parseProductResponse failed: ${e.message}")
            return null
        }
    }

    private fun parseReceiptResponse(aiOutput: String): ReceiptResult? {
        try {
            val json     = extractJsonObject(aiOutput) ?: return null
            val merchant = json.optString("merchant", "Unknown Store")
            val address  = json.optString("address", "").ifBlank { null }
            var total    = json.optDouble("total", 0.0)

            val itemsList  = mutableListOf<Pair<String, Double>>()
            val itemsArray = json.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val obj   = itemsArray.optJSONObject(i) ?: continue
                    val name  = obj.optString("name", "")
                    val price = obj.optDouble("price", 0.0)
                    if (name.isNotBlank()) itemsList.add(name to price)
                }
            }
            if (total == 0.0 && itemsList.isNotEmpty()) total = itemsList.sumOf { it.second }

            return ReceiptResult(merchant, address, total, itemsList)
        } catch (e: Exception) {
            Log.e("GeminiAI", "parseReceiptResponse failed: ${e.message}")
            return null
        }
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "").replace("```", "").trim()
        val start   = cleaned.indexOf('{')
        val end     = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return try { JSONObject(cleaned.substring(start, end + 1)) } catch (_: Exception) { null }
    }
}