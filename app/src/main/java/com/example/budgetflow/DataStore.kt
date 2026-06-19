package com.example.budgetflow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "budgetflow_v2")

private val KEY_TRANSACTIONS   = stringPreferencesKey("transactions")
private val KEY_BUDGET_ENTRIES = stringPreferencesKey("budget_entries")
private val KEY_SAVINGS        = doublePreferencesKey("savings_amount")
private val KEY_LAST_RUN_DATE  = stringPreferencesKey("last_run_date")
private val KEY_SERVER_URL     = stringPreferencesKey("server_url")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ─── Transactions ─────────────────────────────────────────────────────────────

fun transactionsFlow(context: Context): Flow<List<Transaction>> =
    context.dataStore.data.map { prefs ->
        prefs[KEY_TRANSACTIONS]
            ?.let { runCatching { json.decodeFromString<List<Transaction>>(it) }.getOrNull() }
            ?: emptyList()
    }

suspend fun saveTransactions(context: Context, list: List<Transaction>) {
    context.dataStore.edit { it[KEY_TRANSACTIONS] = json.encodeToString(list) }
}

// ─── Budget entries ───────────────────────────────────────────────────────────

fun budgetEntriesFlow(context: Context): Flow<List<BudgetEntry>> =
    context.dataStore.data.map { prefs ->
        prefs[KEY_BUDGET_ENTRIES]
            ?.let { runCatching { json.decodeFromString<List<BudgetEntry>>(it) }.getOrNull() }
            ?: emptyList()
    }

suspend fun saveBudgetEntries(context: Context, list: List<BudgetEntry>) {
    context.dataStore.edit { it[KEY_BUDGET_ENTRIES] = json.encodeToString(list) }
}

// ─── Savings ─────────────────────────────────────────────────────────────────

fun savingsFlow(context: Context): Flow<Double> =
    context.dataStore.data.map { prefs -> prefs[KEY_SAVINGS] ?: 0.0 }

suspend fun saveSavings(context: Context, amount: Double) {
    context.dataStore.edit { it[KEY_SAVINGS] = amount }
}

// ─── Last run date ────────────────────────────────────────────────────────────

fun lastRunDateFlow(context: Context): Flow<String> =
    context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_RUN_DATE] ?: LocalDate.now().minusDays(1).toString()
    }

suspend fun saveLastRunDate(context: Context, date: String) {
    context.dataStore.edit { it[KEY_LAST_RUN_DATE] = date }
}

// ─── Backend server URL ───────────────────────────────────────────────────────

suspend fun getServerUrl(context: Context): String =
    context.dataStore.data.first()[KEY_SERVER_URL] ?: ""

suspend fun saveServerUrl(context: Context, url: String) {
    context.dataStore.edit { it[KEY_SERVER_URL] = url }
}