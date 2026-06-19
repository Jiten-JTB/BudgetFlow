package com.example.budgetflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.budgetflow.ui.theme.BudgetFlowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BudgetFlowTheme { BudgetFlowApp() } }
    }
}

@Composable
fun BudgetFlowApp() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val txFlow      = transactionsFlow(context).collectAsState(initial = null)
    val entriesFlow = budgetEntriesFlow(context).collectAsState(initial = null)
    val savingsFlow = savingsFlow(context).collectAsState(initial = null)
    val lastRunFlow = lastRunDateFlow(context).collectAsState(initial = null)

    val txInit      = txFlow.value
    val entriesInit = entriesFlow.value
    val savingsInit = savingsFlow.value
    val lastRunInit = lastRunFlow.value

    if (txInit == null || entriesInit == null || savingsInit == null || lastRunInit == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0F1E)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6495ED))
        }
        return
    }

    var transactions  by remember { mutableStateOf(txInit) }
    var budgetEntries by remember { mutableStateOf(entriesInit) }
    var savingsAmount by remember { mutableStateOf(savingsInit) }

    // ── Assistant ViewModel (survives recomposition) ──────────────────────────
    val assistantViewModel: AssistantViewModel = viewModel(
        factory = AssistantViewModel.Factory(AssistantRepository(), context)
    )

    // Keep ViewModel's snapshot of app data current so queries are accurate
    LaunchedEffect(transactions, budgetEntries, savingsAmount) {
        assistantViewModel.currentTransactions  = transactions
        assistantViewModel.currentBudgetEntries = budgetEntries
        assistantViewModel.currentSavingsAmount = savingsAmount
    }

    // Wire ViewModel action callbacks into the app state
    LaunchedEffect(Unit) {
        assistantViewModel.onAddTransaction = { tx ->
            val updated = listOf(tx) + transactions
            transactions = updated
            scope.launch { saveTransactions(context, updated) }
        }
        assistantViewModel.onAddBudgetEntry = { entry ->
            val now   = LocalDateTime.now()
            val toAdd = mutableListOf(entry)
            if (entry.kind == EntryKind.TIMELY_TEMPLATE) {
                val freq = entry.frequency!!
                val (ps, pe) = freq.periodRange(now)
                val stableId = (entry.id.toString() + freq.periodKey(now)).hashCode().toLong()
                    .let { if (it < 0) it + Long.MAX_VALUE else it }
                toAdd.add(BudgetEntry(
                    id = stableId, name = entry.name, amount = entry.amount,
                    kind = EntryKind.TIMELY_SLOT, frequency = freq,
                    periodStart = ps, periodEnd = pe,
                    templateId = entry.id, createdDate = now.toLocalDate().toString()
                ))
            }
            val updated = budgetEntries + toAdd
            budgetEntries = updated
            scope.launch { saveBudgetEntries(context, updated) }
        }
    }

    // ── Recurrence engine loop ────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            val (updatedEntries, updatedSavings) = runRecurrenceEngine(
                entries       = budgetEntries,
                transactions  = transactions,
                savingsAmount = savingsAmount,
                now           = now
            )
            if (updatedEntries != budgetEntries || updatedSavings != savingsAmount) {
                budgetEntries = updatedEntries
                savingsAmount = updatedSavings
                saveBudgetEntries(context, updatedEntries)
                saveSavings(context, updatedSavings)
            }
            saveLastRunDate(context, now.toLocalDate().toString())
            delay(60_000L)
        }
    }

    var currentDestination  by remember { mutableStateOf(AppDestinations.HOME) }
    var showForm            by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Refresh assistant settings whenever user navigates to ASSISTANT tab
    LaunchedEffect(currentDestination) {
        if (currentDestination == AppDestinations.ASSISTANT) {
            assistantViewModel.refreshSettings()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { dest ->
                item(
                    icon     = { Icon(painterResource(dest.icon), contentDescription = dest.label) },
                    label    = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick  = { currentDestination = dest; selectedTransaction = null }
                )
            }
        }
    ) {
        Scaffold(
            modifier       = Modifier.fillMaxSize(),
            containerColor = Color(0xFF0A0F1E),
            floatingActionButton = {
                if (selectedTransaction == null &&
                    (currentDestination == AppDestinations.HOME ||
                            currentDestination == AppDestinations.BUDGET)
                ) {
                    FloatingActionButton(
                        onClick        = { showForm = true },
                        containerColor = Color(0xFF6495ED),
                        contentColor   = Color.White,
                        elevation      = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Icon(painterResource(R.drawable.baseline_add_24), "Add")
                    }
                }
            }
        ) { innerPadding ->
            when {
                selectedTransaction != null -> TransactionDetailScreen(
                    transaction = selectedTransaction!!,
                    onBack      = { selectedTransaction = null },
                    modifier    = Modifier.padding(innerPadding)
                )
                currentDestination == AppDestinations.HOME -> HomeScreen(
                    transactions       = transactions,
                    onTransactionClick = { selectedTransaction = it },
                    modifier           = Modifier.padding(innerPadding)
                )
                currentDestination == AppDestinations.BUDGET -> BudgetScreen(
                    entries       = budgetEntries,
                    savingsAmount = savingsAmount,
                    transactions  = transactions,
                    onDeleteEntry = { templateId ->
                        val updated = budgetEntries.map { e ->
                            when {
                                e.id == templateId -> e.copy(isDeleted = true)
                                e.templateId == templateId && !e.isExpired -> e.copy(isDeleted = true)
                                else -> e
                            }
                        }
                        budgetEntries = updated
                        scope.launch { saveBudgetEntries(context, updated) }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                currentDestination == AppDestinations.ASSISTANT -> AssistantScreen(
                    viewModel = assistantViewModel,
                    modifier  = Modifier.padding(innerPadding)
                )
                currentDestination == AppDestinations.ANALYTICS -> AnalyticsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
                currentDestination == AppDestinations.SETTINGS -> SettingsScreen(
                    modifier       = Modifier.padding(innerPadding),
                    onSettingsSaved = { assistantViewModel.refreshSettings() }
                )
            }

            if (showForm) {
                PopUpForm(
                    currentPage = currentDestination,
                    onDismiss   = { showForm = false },
                    onTransactionAdded = { tx ->
                        val updated = listOf(tx) + transactions
                        transactions = updated
                        scope.launch { saveTransactions(context, updated) }
                    },
                    onBudgetEntryAdded = { entry ->
                        val now   = LocalDateTime.now()
                        val toAdd = mutableListOf(entry)
                        if (entry.kind == EntryKind.TIMELY_TEMPLATE) {
                            val freq = entry.frequency!!
                            val (ps, pe) = freq.periodRange(now)
                            val stableId = (entry.id.toString() + freq.periodKey(now)).hashCode().toLong()
                                .let { if (it < 0) it + Long.MAX_VALUE else it }
                            toAdd.add(BudgetEntry(
                                id = stableId, name = entry.name, amount = entry.amount,
                                kind = EntryKind.TIMELY_SLOT, frequency = freq,
                                periodStart = ps, periodEnd = pe,
                                templateId = entry.id, createdDate = now.toLocalDate().toString()
                            ))
                        }
                        val updated = budgetEntries + toAdd
                        budgetEntries = updated
                        scope.launch { saveBudgetEntries(context, updated) }
                    }
                )
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: Int) {
    HOME("Home",           R.drawable.outline_home_24),
    BUDGET("Budget",       R.drawable.outline_wallet_24),
    ASSISTANT("Assistant", R.drawable.outline_robot_2_24),
    ANALYTICS("Analytics", R.drawable.outline_analytics_24),
    SETTINGS("Settings",   R.drawable.outline_settings_24),
}