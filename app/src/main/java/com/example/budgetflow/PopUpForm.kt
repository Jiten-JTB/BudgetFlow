package com.example.budgetflow

import androidx.compose.runtime.Composable

@Composable
fun PopUpForm(
    currentPage: AppDestinations,
    onDismiss: () -> Unit,
    onTransactionAdded: (Transaction) -> Unit = {},
    onBudgetEntryAdded: (BudgetEntry) -> Unit = {}
) {
    when (currentPage) {
        AppDestinations.HOME   -> TransactionForm(onDismiss = onDismiss, onTransactionAdded = onTransactionAdded)
        AppDestinations.BUDGET -> BudgetForm(onDismiss = onDismiss, onBudgetEntryAdded = onBudgetEntryAdded)
        else -> { /* no form on analytics/settings */ }
    }
}