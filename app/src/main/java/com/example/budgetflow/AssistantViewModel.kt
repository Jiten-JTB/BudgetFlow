package com.example.budgetflow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

// ─── Chat message model ───────────────────────────────────────────────────────

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: MessageRole,
    val text: String,
    val actions: List<AgentActionPayload> = emptyList(),
    val isLoading: Boolean = false
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

// ─── UI state ─────────────────────────────────────────────────────────────────

data class AssistantUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            role = MessageRole.ASSISTANT,
            text = "Hi! I'm your BudgetFlow assistant. You can tell me things like:\n\n" +
                    "• \"I got an internship stipend of ₹50,000 monthly\"\n" +
                    "• \"I bought ice cream for ₹50 and milk for ₹40\"\n" +
                    "• \"How much did I spend last week?\"\n\n" +
                    "What would you like to do?"
        )
    ),
    val isLoading: Boolean = false,
    val serverUrl: String = "",
    val hasApiKey: Boolean = false,
    val configError: String? = null
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    // Callbacks into MainActivity to mutate app state
    var onAddTransaction: ((Transaction) -> Unit)? = null
    var onAddBudgetEntry: ((BudgetEntry) -> Unit)? = null

    // Live app data injected from BudgetFlowApp
    var currentTransactions: List<Transaction> = emptyList()
    var currentBudgetEntries: List<BudgetEntry> = emptyList()
    var currentSavingsAmount: Double = 0.0
    val secureStorage = SecureStorage.getInstance(context)

    init {
        viewModelScope.launch {
            // Load settings
//            val apiKey   = getGeminiApiKey(context).ifBlank { "" }
            val apiKey   = secureStorage.getString("geminiApiKey")
            val url      = getServerUrl(context).ifBlank { "http://10.0.2.2:3000" } // emulator localhost
            _uiState.value = _uiState.value.copy(
                serverUrl = url,
                hasApiKey = apiKey.isNotBlank(),
                configError = when {
                    apiKey.isBlank() -> "Add your Gemini API key in Settings to use the assistant."
                    url.isBlank()    -> "Set the server URL in Settings."
                    else             -> null
                }
            )
        }
    }

    fun refreshSettings() {
        viewModelScope.launch {
//            val apiKey = getGeminiApiKey(context).ifBlank { "" }
            val apiKey   = secureStorage.getString("geminiApiKey")
            val url    = getServerUrl(context).ifBlank { "http://10.0.2.2:3000" }
            _uiState.value = _uiState.value.copy(
                serverUrl = url,
                hasApiKey = apiKey.isNotBlank(),
                configError = when {
                    apiKey.isBlank() -> "Add your Gemini API key in Settings to use the assistant."
                    url.isBlank()    -> "Set the server URL in Settings."
                    else             -> null
                }
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val trimmed = text.trim()

        // Optimistically add user message
        val userMsg = ChatMessage(role = MessageRole.USER, text = trimmed)
        val loadingMsg = ChatMessage(
            id = System.currentTimeMillis() + 1,
            role = MessageRole.ASSISTANT,
            text = "Thinking…",
            isLoading = true
        )
        _uiState.value = _uiState.value.copy(
            messages  = _uiState.value.messages + userMsg + loadingMsg,
            isLoading = true
        )

        viewModelScope.launch {
//            val apiKey    = getGeminiApiKey(context)
            val apiKey   = secureStorage.getString("geminiApiKey")
            val serverUrl = getServerUrl(context).ifBlank { "http://10.0.2.2:3000" }

            if (apiKey.isBlank()) {
                replaceLoading(loadingMsg.id, "Please add your Gemini API key in Settings first.")
                return@launch
            }

            // Build history (exclude the loading message and welcome message)
            val history = _uiState.value.messages
                .filter { !it.isLoading && it.role != MessageRole.SYSTEM }
                .dropLast(2) // drop the user message + loading we just added
                .takeLast(10) // keep last 10 for context window
                .map { ChatHistoryItem(
                    role    = if (it.role == MessageRole.USER) "user" else "assistant",
                    content = it.text
                )}

            val result = repository.chat(
                userMessage   = trimmed,
                transactions  = currentTransactions,
                budgetEntries = currentBudgetEntries,
                savingsAmount = currentSavingsAmount,
                history       = history,
                serverUrl     = serverUrl,
                geminiApiKey  = apiKey
            )

            result.fold(
                onSuccess = { response ->
                    // Execute actions in order
                    val executedActions = executeActions(response.actions)
                    replaceLoading(
                        id      = loadingMsg.id,
                        text    = response.reply,
                        actions = executedActions
                    )
                },
                onFailure = { error ->
                    replaceLoading(
                        id   = loadingMsg.id,
                        text = "⚠️ ${error.message ?: "Something went wrong."}"
                    )
                }
            )
        }
    }

    // ─── Execute parsed actions against the app state ─────────────────────────

    private fun executeActions(actions: List<AgentActionPayload>): List<AgentActionPayload> {
        val executed = mutableListOf<AgentActionPayload>()
        val now = LocalDateTime.now()

        for (action in actions) {
            try {
                when (action.type) {
                    "ADD_BUDGET_ENTRY" -> {
                        val kind = when (action.kind) {
                            "TIMELY_TEMPLATE" -> EntryKind.TIMELY_TEMPLATE
                            else              -> EntryKind.LUMP_SUM
                        }
                        val freq = action.frequency?.let {
                            runCatching { Frequency.valueOf(it) }.getOrNull()
                        }
                        val entry = BudgetEntry(
                            name        = action.name ?: "Unknown",
                            amount      = action.amount ?: 0.0,
                            kind        = kind,
                            frequency   = freq,
                            createdDate = now.toLocalDate().toString()
                        )
                        onAddBudgetEntry?.invoke(entry)
                        executed.add(action)
                    }
                    "ADD_TRANSACTION" -> {
                        val tx = Transaction(
                            description = action.description ?: "Purchase",
                            amount      = action.amount ?: 0.0,
                            date        = action.date ?: LocalDate.now().toString(),
                            time        = action.time ?: "12:00",
                            vendor      = action.vendor ?: "Unknown",
                            category    = action.category ?: "General"
                        )
                        onAddTransaction?.invoke(tx)
                        executed.add(action)
                    }
                    "QUERY_STATS", "ERROR" -> {
                        executed.add(action)
                    }
                }
            } catch (_: Exception) {
                // Skip malformed actions silently
            }
        }
        return executed
    }

    private fun replaceLoading(id: Long, text: String, actions: List<AgentActionPayload> = emptyList()) {
        val messages = _uiState.value.messages.map { msg ->
            if (msg.id == id) msg.copy(text = text, isLoading = false, actions = actions)
            else msg
        }
        _uiState.value = _uiState.value.copy(messages = messages, isLoading = false)
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    class Factory(
        private val repository: AssistantRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            AssistantViewModel(repository, context) as T
    }
}