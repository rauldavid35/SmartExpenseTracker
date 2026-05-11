package com.example.smartexpensetracker.utils

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class VoiceParseResult {
    data class NoteResult(val text: String) : VoiceParseResult()

    data class ExpenseResult(
        val name: String,
        val amount: Double?,
        val category: String?,
        val location: String?,
        val confidence: Confidence
    ) : VoiceParseResult() {
        enum class Confidence { HIGH, LOW }
    }

    data class ShoppingListResult(
        val listName: String,
        val items: List<String>,
        val confidence: Confidence
    ) : VoiceParseResult() {
        enum class Confidence { HIGH, LOW }
    }
}

/**
 * VoiceParser — three-tier fallback chain:
 *
 *   1. Gemini cloud (best quality) — used when [preferOnline] is true AND we have
 *      an API key. Skipped silently on failure (network, quota, rate limit).
 *   2. Local LLM (Qwen 1.5B on-device) — used when the model is downloaded.
 *      Much better than regex on natural-language variation; slower than Gemini.
 *   3. Regex (always available) — final fallback, never fails but limited.
 *
 * The order can be flipped via [preferOnline]: set false to prefer the local
 * model (privacy mode / data-saver mode) and only fall back to Gemini if the
 * local model fails.
 *
 * Construction takes a Context now so we can build the LocalLlmParser. The
 * apiKey can be empty — Gemini is just skipped in that case.
 */
class VoiceParser(
    context: Context,
    apiKey: String,
    private val preferOnline: Boolean = true,
) {

    companion object {
        const val STT_LOCALE = "en-US"
        private const val TAG = "VoiceParser"
    }

    private val appContext = context.applicationContext
    private val localParser = LocalLlmParser(appContext)
    private val hasGeminiKey = apiKey.isNotBlank()

    private val gemini = if (hasGeminiKey) GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey,
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT,        BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH,       BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
        )
    ) else null

    fun parseNote(transcript: String): VoiceParseResult.NoteResult =
        VoiceParseResult.NoteResult(transcript.trim())

    // ── Expenses ──────────────────────────────────────────────────────────────

    suspend fun parseExpense(
        transcript: String,
        knownCategories: List<String> = emptyList()
    ): VoiceParseResult.ExpenseResult = withContext(Dispatchers.IO) {

        // Build the chain based on preferences
        val chain: List<suspend () -> VoiceParseResult.ExpenseResult?> = if (preferOnline) {
            listOf(
                { tryGeminiExpense(transcript, knownCategories) },
                { tryLocalExpense(transcript, knownCategories) },
                { offlineParseExpense(transcript, knownCategories) }
            )
        } else {
            listOf(
                { tryLocalExpense(transcript, knownCategories) },
                { tryGeminiExpense(transcript, knownCategories) },
                { offlineParseExpense(transcript, knownCategories) }
            )
        }

        for ((idx, step) in chain.withIndex()) {
            val result = step.invoke()
            if (result != null) {
                Log.d(TAG, "parseExpense: tier $idx succeeded")
                return@withContext result
            }
        }
        // Regex always returns non-null, so this path is theoretically unreachable.
        // Kept as a defensive last resort.
        offlineParseExpense(transcript, knownCategories)
    }

    private suspend fun tryGeminiExpense(
        transcript: String,
        knownCategories: List<String>
    ): VoiceParseResult.ExpenseResult? {
        val g = gemini ?: return null
        return try {
            val categoryHint = if (knownCategories.isNotEmpty())
                "Known categories (pick closest or null): ${knownCategories.joinToString()}"
            else ""

            val prompt = """
                LANGUAGE RULE: You MUST respond only in English. All field values must be in English.

                You are a voice-command parser for an expense-tracker app.
                Extract structured data from the transcript below.
                $categoryHint

                Rules:
                1. "name"     = merchant or item name (string, required). Translate to English if needed.
                2. "amount"   = numeric value only, no currency symbol (double or null).
                3. "category" = best match from known list, or infer one in English (string or null).
                4. "location" = shop or place name if mentioned, else null.
                5. Return ONLY a raw JSON object — no markdown fences, no explanation.

                Format: { "name": "...", "amount": 45.0, "category": "...", "location": "..." }

                Transcript: "$transcript"
            """.trimIndent()

            val response = g.generateContent(prompt)
            parseExpenseJson(response.text ?: "", VoiceParseResult.ExpenseResult.Confidence.HIGH)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini expense parse failed: ${e.message}")
            null
        }
    }

    private suspend fun tryLocalExpense(
        transcript: String,
        knownCategories: List<String>
    ): VoiceParseResult.ExpenseResult? = if (localParser.isReady())
        localParser.parseExpense(transcript, knownCategories)
    else null

    // ── Shopping lists ────────────────────────────────────────────────────────

    suspend fun parseShoppingList(
        transcript: String
    ): VoiceParseResult.ShoppingListResult = withContext(Dispatchers.IO) {

        val chain: List<suspend () -> VoiceParseResult.ShoppingListResult?> = if (preferOnline) {
            listOf(
                { tryGeminiList(transcript) },
                { if (localParser.isReady()) localParser.parseShoppingList(transcript) else null },
                { offlineParseShoppingList(transcript) }
            )
        } else {
            listOf(
                { if (localParser.isReady()) localParser.parseShoppingList(transcript) else null },
                { tryGeminiList(transcript) },
                { offlineParseShoppingList(transcript) }
            )
        }

        for ((idx, step) in chain.withIndex()) {
            val result = step.invoke()
            if (result != null) {
                Log.d(TAG, "parseShoppingList: tier $idx succeeded")
                return@withContext result
            }
        }
        offlineParseShoppingList(transcript)
    }

    private suspend fun tryGeminiList(
        transcript: String
    ): VoiceParseResult.ShoppingListResult? {
        val g = gemini ?: return null
        return try {
            val prompt = """
                LANGUAGE RULE: You MUST respond only in English. All field values must be in English.

                You are a voice-command parser for a shopping-list app.
                Extract a list name and list of items from the transcript below.

                Rules:
                1. "listName" = a short meaningful English name for the list.
                   Infer from context if not explicit (e.g. "Groceries", "Weekend", "Pharmacy").
                   NEVER use generic command words like "add", "create", "grocery list" as the name.
                2. "items"    = array of individual product strings in English.
                   Each entry is ONE item only — never merge multiple items.
                3. Return ONLY a raw JSON object — no markdown, no explanation.

                Format: { "listName": "Weekend", "items": ["Milk", "Bread", "Eggs"] }

                Transcript: "$transcript"
            """.trimIndent()

            val response = g.generateContent(prompt)
            parseShoppingListJson(response.text ?: "", VoiceParseResult.ShoppingListResult.Confidence.HIGH)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini list parse failed: ${e.message}")
            null
        }
    }

    // ── Offline regex fallbacks (unchanged from original) ─────────────────────

    internal fun offlineParseExpense(
        transcript: String,
        knownCategories: List<String>
    ): VoiceParseResult.ExpenseResult {
        var working = transcript.trim()

        val amountRegex = Regex("""(?<!\w)(\d{1,6}(?:[.,]\d{1,2})?)(?!\w)""")
        val amountMatch = amountRegex.find(working)
        val amount = amountMatch?.value?.replace(',', '.')?.toDoubleOrNull()
        if (amountMatch != null) working = working.removeRange(amountMatch.range).trim()

        val locationRegex = Regex(
            """(?:la|at|@|from|din|de la)\s+([A-Za-zÀ-žÁ-ź\s]{2,25})""",
            RegexOption.IGNORE_CASE
        )
        val locationMatch = locationRegex.find(working)
        val location = locationMatch?.groupValues?.getOrNull(1)?.trim()
            ?.replace(Regex("""(?i)\b(and|si|și|cu)\b.*"""), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (locationMatch != null) working = working.removeRange(locationMatch.range).trim()

        val category = matchCategory(working, knownCategories)

        val fillerRegex = Regex(
            """(?i)\b(am platit|am cumparat|cumparat|platit|spent|bought|paid|for|pe|ron|lei|euro|eur|usd|\$|€)\b"""
        )
        val name = working.replace(fillerRegex, "")
            .replace(Regex("""\s{2,}"""), " ").trim()
            .ifBlank { transcript.take(40) }

        val confidence = if (amount != null) VoiceParseResult.ExpenseResult.Confidence.HIGH
        else VoiceParseResult.ExpenseResult.Confidence.LOW

        return VoiceParseResult.ExpenseResult(
            name = name, amount = amount, category = category,
            location = location, confidence = confidence
        )
    }

    private fun matchCategory(text: String, known: List<String>): String? {
        val normalized = text.lowercase()
        val fromUser = known.firstOrNull { normalized.contains(it.lowercase()) }
        if (fromUser != null) return fromUser

        val keywordMap = mapOf(
            "Food"          to listOf("food", "restaurant", "burger", "pizza", "sushi",
                "groceries", "grocery", "supermarket", "coffee", "cafe",
                "mancare", "aliment", "lapte", "paine"),
            "Transport"     to listOf("transport", "uber", "taxi", "bus", "metro",
                "train", "ticket", "fuel", "gas", "parking", "benzina", "tren"),
            "Bills"         to listOf("bill", "electricity", "internet", "phone",
                "subscription", "factura", "curent", "abonament", "utilities"),
            "Health"        to listOf("health", "doctor", "pharmacy", "medicine",
                "hospital", "farmacie", "medicament", "sanatate", "drug"),
            "Entertainment" to listOf("cinema", "movie", "bar", "club", "concert",
                "netflix", "spotify", "entertainment", "game", "divertisment"),
            "Shopping"      to listOf("shopping", "clothes", "shoes", "mall",
                "online", "emag", "haine", "pantofi", "cumparaturi", "store")
        )

        return keywordMap.entries.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it) }
        }?.key
    }

    internal fun offlineParseShoppingList(
        transcript: String
    ): VoiceParseResult.ShoppingListResult {
        val commandPrefix = Regex(
            """^(?:please\s+)?(?:add|create|make|start|new|open|build)(?:\s+(?:a|an|the|my|new))?\s+""",
            RegexOption.IGNORE_CASE
        )
        var working = transcript.trim().replace(commandPrefix, "")

        var listName: String? = null

        val listKeyword = Regex(
            """^((?:(?!list\b)[\w\sÀ-žÁ-ź])+?)\s+list\s*(?::|with|of|for|and)?\s+(.+)""",
            RegexOption.IGNORE_CASE
        )
        val listaPattern = Regex(
            """^lista\s+([\w\sÀ-žÁ-ź]{1,30}?)\s*[:\-]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        )
        val colonPattern = Regex(
            """^([A-Z][A-Za-zÀ-žÁ-ź\s]{1,25}):\s*(.+)"""
        )

        val filler = setOf("a", "an", "the", "my", "new", "some", "grocery",
            "shopping", "groceries", "items", "stuff")

        for (pattern in listOf(listKeyword, listaPattern, colonPattern)) {
            val m = pattern.find(working) ?: continue
            val candidate = m.groupValues[1].trim()
            if (candidate.lowercase() !in filler && candidate.isNotBlank()) {
                listName = candidate.replaceFirstChar { it.uppercase() }
                working  = m.groupValues[2].trim()
                break
            }
        }

        if (listName == null) listName = "Shopping List"

        val explicitDelimiter = Regex(
            """(?i)\s*,\s*|\s+(?:and|then|also|plus|with|si|și|cu)\s+"""
        )
        var rawItems = working.split(explicitDelimiter)
            .map { it.trim().replaceFirstChar { c -> c.uppercase() } }
            .filter { it.isNotBlank() && it.length > 1 }

        if (rawItems.size <= 1 && working.contains(' ')) {
            rawItems = working.split(Regex("""\s+"""))
                .map { it.trim().replaceFirstChar { c -> c.uppercase() } }
                .filter { it.isNotBlank() && it.length > 1 }
        }

        val items = rawItems.distinct()

        val confidence = if (items.isNotEmpty()) VoiceParseResult.ShoppingListResult.Confidence.HIGH
        else VoiceParseResult.ShoppingListResult.Confidence.LOW

        return VoiceParseResult.ShoppingListResult(
            listName = listName, items = items, confidence = confidence
        )
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun parseExpenseJson(
        raw: String,
        confidence: VoiceParseResult.ExpenseResult.Confidence
    ): VoiceParseResult.ExpenseResult? {
        try {
            val json       = JSONObject(extractJson(raw))
            val parsedName = json.optString("name").ifBlank { null } ?: return null
            return VoiceParseResult.ExpenseResult(
                name       = parsedName,
                amount     = json.optDouble("amount").takeIf { !it.isNaN() },
                category   = json.optString("category").ifBlank { null },
                location   = json.optString("location").ifBlank { null },
                confidence = confidence
            )
        } catch (e: Exception) { return null }
    }

    private fun parseShoppingListJson(
        raw: String,
        confidence: VoiceParseResult.ShoppingListResult.Confidence
    ): VoiceParseResult.ShoppingListResult? {
        try {
            val json       = JSONObject(extractJson(raw))
            val parsedName = json.optString("listName").ifBlank { null } ?: return null
            val itemsArr   = json.optJSONArray("items") ?: JSONArray()
            val items      = (0 until itemsArr.length()).map { itemsArr.getString(it) }
            return VoiceParseResult.ShoppingListResult(
                listName = parsedName, items = items, confidence = confidence
            )
        } catch (e: Exception) { return null }
    }

    private fun extractJson(raw: String): String {
        val s     = raw.replace("```json", "").replace("```", "").trim()
        val start = s.indexOf('{')
        val end   = s.lastIndexOf('}')
        return if (start != -1 && end != -1) s.substring(start, end + 1) else s
    }
}