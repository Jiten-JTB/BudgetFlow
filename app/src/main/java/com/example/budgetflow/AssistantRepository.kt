package com.example.budgetflow

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

// ─── JSON models matching the backend's TypeScript types ─────────────────────

@Serializable
data class AppContextPayload(
    val transactions: List<TransactionPayload>,
    val budgetEntries: List<BudgetEntryPayload>,
    val savingsAmount: Double,
    val currentDate: String
)

@Serializable
data class TransactionPayload(
    val id: Long,
    val description: String,
    val amount: Double,
    val date: String,
    val time: String,
    val vendor: String,
    val category: String
)

@Serializable
data class BudgetEntryPayload(
    val id: Long,
    val name: String,
    val amount: Double,
    val kind: String,
    val frequency: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val templateId: Long? = null,
    val createdDate: String,
    val isExpired: Boolean,
    val isDeleted: Boolean
)

@Serializable
data class ChatHistoryItem(
    val role: String,   // "user" | "assistant"
    val content: String
)

@Serializable
data class ChatRequestPayload(
    val message: String,
    val context: AppContextPayload,
    val history: List<ChatHistoryItem>
)

@Serializable
data class ChatResponsePayload(
    val reply: String,
    val actions: List<AgentActionPayload>
)

@Serializable
data class AgentActionPayload(
    val type: String,
    // ADD_BUDGET_ENTRY
    val name: String? = null,
    val amount: Double? = null,
    val kind: String? = null,
    val frequency: String? = null,
    // ADD_TRANSACTION
    val description: String? = null,
    val vendor: String? = null,
    val category: String? = null,
    val date: String? = null,
    val time: String? = null,
    // QUERY_STATS + ERROR
    val answer: String? = null,
    val message: String? = null
)

// ─── Repository ───────────────────────────────────────────────────────────────

class AssistantRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun chat(
        userMessage: String,
        transactions: List<Transaction>,
        budgetEntries: List<BudgetEntry>,
        savingsAmount: Double,
        history: List<ChatHistoryItem>,
        serverUrl: String,
        geminiApiKey: String
    ): Result<ChatResponsePayload> = withContext(Dispatchers.IO) {
        runCatching {
            val url = serverUrl.trimEnd('/') + "/chat"

            val payload = ChatRequestPayload(
                message = userMessage,
                context = AppContextPayload(
                    transactions  = transactions.map { it.toPayload() },
                    budgetEntries = budgetEntries.map { it.toPayload() },
                    savingsAmount = savingsAmount,
                    currentDate   = LocalDate.now().toString()
                ),
                history = history
            )

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $geminiApiKey")
                connectTimeout = 30_000
                readTimeout    = 60_000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(json.encodeToString(payload))
                writer.flush()
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                throw Exception(
                    when {
                        errBody.contains("Gemini API key") || errBody.contains("API_KEY") ->
                            "Invalid Gemini API key. Please check Settings."
                        responseCode == 401 -> "API key missing. Please add it in Settings."
                        responseCode == 500 -> "Server error. Check your backend is running."
                        else -> "Error $responseCode: $errBody"
                    }
                )
            }

            json.decodeFromString<ChatResponsePayload>(responseBody)
        }
    }
}

// ─── Extension mappers ────────────────────────────────────────────────────────

private fun Transaction.toPayload() = TransactionPayload(
    id = id, description = description, amount = amount,
    date = date, time = time, vendor = vendor, category = category
)

private fun BudgetEntry.toPayload() = BudgetEntryPayload(
    id = id, name = name, amount = amount, kind = kind.name,
    frequency = frequency?.name, periodStart = periodStart, periodEnd = periodEnd,
    templateId = templateId, createdDate = createdDate,
    isExpired = isExpired, isDeleted = isDeleted
)