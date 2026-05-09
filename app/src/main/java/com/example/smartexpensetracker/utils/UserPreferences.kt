package com.example.smartexpensetracker.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local preferences scoped per user (currency, dark mode, privacy mode, budget cycle).
 *
 * IMPORTANT: This must be a per-userId singleton.
 * Every screen that observes prefs must see the SAME MutableStateFlow instance,
 * otherwise a write in SettingsScreen won't recompose MainApp's theme/privacy
 * wrapper. Use [get] (or the legacy constructor delegate) to obtain instances.
 */
class UserPreferences private constructor(context: Context, userId: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences("user_prefs_$userId", Context.MODE_PRIVATE)

    // ── Keys ──────────────────────────────────────────────────────────────────
    companion object {
        const val KEY_CURRENCY          = "currency"
        const val KEY_DARK_MODE         = "dark_mode"
        const val KEY_PRIVACY_MODE      = "privacy_mode"
        const val KEY_BUDGET_RESET_DAY  = "budget_reset_day"
        const val KEY_NOTIFY_ON_RESET   = "notify_on_reset"

        val SUPPORTED_CURRENCIES = listOf(
            "USD" to "$", "EUR" to "€", "GBP" to "£", "RON" to "lei",
            "JPY" to "¥", "CAD" to "C$", "AUD" to "A$", "CHF" to "Fr",
            "INR" to "₹", "BRL" to "R$", "MXN" to "MX$", "SEK" to "kr"
        )

        // ── Per-userId singleton cache ────────────────────────────────────────
        // Without this, every screen builds its own MutableStateFlow and writes
        // from one screen don't propagate to observers on another.
        @Volatile
        private var instances: MutableMap<String, UserPreferences> = mutableMapOf()

        fun get(context: Context, userId: String): UserPreferences =
            instances[userId] ?: synchronized(this) {
                instances[userId] ?: UserPreferences(context, userId).also {
                    instances[userId] = it
                }
            }

        /**
         * Legacy constructor-style call site:
         *   `UserPreferences(context, uid)` continues to work and returns the
         *   shared singleton. Existing screens don't need to change.
         */
        operator fun invoke(context: Context, userId: String): UserPreferences =
            get(context, userId)
    }

    // ── Backed state flows (so the UI reacts without restart) ─────────────────
    private val _currency       = MutableStateFlow(prefs.getString(KEY_CURRENCY, "USD") ?: "USD")
    private val _darkMode       = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    private val _privacyMode    = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_MODE, false))
    private val _budgetResetDay = MutableStateFlow(prefs.getInt(KEY_BUDGET_RESET_DAY, 1))
    private val _notifyOnReset  = MutableStateFlow(prefs.getBoolean(KEY_NOTIFY_ON_RESET, false))

    val currency:       StateFlow<String>  = _currency.asStateFlow()
    val darkMode:       StateFlow<Boolean> = _darkMode.asStateFlow()
    val privacyMode:    StateFlow<Boolean> = _privacyMode.asStateFlow()
    val budgetResetDay: StateFlow<Int>     = _budgetResetDay.asStateFlow()
    val notifyOnReset:  StateFlow<Boolean> = _notifyOnReset.asStateFlow()

    // ── Setters ───────────────────────────────────────────────────────────────
    fun setCurrency(value: String) {
        prefs.edit().putString(KEY_CURRENCY, value).apply()
        _currency.value = value
    }

    fun setDarkMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
        _darkMode.value = value
    }

    fun setPrivacyMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_MODE, value).apply()
        _privacyMode.value = value
    }

    fun setBudgetResetDay(day: Int) {
        val clamped = day.coerceIn(1, 28)
        prefs.edit().putInt(KEY_BUDGET_RESET_DAY, clamped).apply()
        _budgetResetDay.value = clamped
    }

    fun setNotifyOnReset(value: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_RESET, value).apply()
        _notifyOnReset.value = value
    }

    /** Returns the symbol for the currently selected currency. */
    fun currencySymbol(): String =
        SUPPORTED_CURRENCIES.firstOrNull { it.first == _currency.value }?.second ?: "$"
}