package com.example.budgetflow

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetForm(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onBudgetEntryAdded: (BudgetEntry) -> Unit = {}
) {
    val context = LocalContext.current

    var name         by rememberSaveable { mutableStateOf("") }
    var amount       by rememberSaveable { mutableStateOf("") }
    var selectedKind by rememberSaveable { mutableStateOf(EntryKind.LUMP_SUM) }
    var frequency    by rememberSaveable { mutableStateOf(Frequency.MONTHLY) }
    var freqExpanded by remember { mutableStateOf(false) }

    var nameError   by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape            = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text("BUDGET", fontSize = 10.sp, color = AccentGreen, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("Add Budget Source", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BfTextField(
                    value         = name,
                    onValueChange = { name = it; nameError = false },
                    label         = "Source name (e.g. Salary)",
                    isError       = nameError,
                    errorMsg      = "Source name is required"
                )
                BfTextField(
                    value         = amount,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() || it == '.' }) { amount = v; amountError = false }
                    },
                    label         = "Amount (₹)",
                    isError       = amountError,
                    errorMsg      = "Enter a valid amount",
                    keyboardType  = KeyboardType.Decimal
                )

                HorizontalDivider(color = Divider)
                Text("Payment Type", fontSize = 13.sp, color = TextMuted, fontWeight = FontWeight.Medium)

                // Kind selector: Lump Sum | Timely
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        EntryKind.LUMP_SUM        to Pair("Lump Sum",  "One-time · e.g. Bonus"),
                        EntryKind.TIMELY_TEMPLATE to Pair("Timely",    "Recurring · e.g. Salary")
                    ).forEach { (kind, labels) ->
                        val selected = selectedKind == kind
                        Card(
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = if (selected) Accent.copy(alpha = 0.15f) else BgCardAlt
                            ),
                            border  = BorderStroke(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) Accent else Divider
                            ),
                            onClick = { selectedKind = kind }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selected,
                                        onClick  = { selectedKind = kind },
                                        colors   = RadioButtonDefaults.colors(
                                            selectedColor   = Accent,
                                            unselectedColor = TextMuted
                                        )
                                    )
                                    Text(
                                        labels.first, color = TextPrimary,
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    labels.second, color = TextMuted,
                                    fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Frequency picker — only for Timely
                if (selectedKind == EntryKind.TIMELY_TEMPLATE) {
                    HorizontalDivider(color = Divider)

                    ExposedDropdownMenuBox(
                        expanded         = freqExpanded,
                        onExpandedChange = { freqExpanded = it }
                    ) {
                        OutlinedTextField(
                            value          = frequency.label,
                            onValueChange  = {},
                            readOnly       = true,
                            label          = { Text("How Often?", color = TextMuted) },
                            trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                            colors         = bfTextFieldColors(),
                            modifier       = Modifier.menuAnchor().fillMaxWidth(),
                            shape          = RoundedCornerShape(12.dp),
                            supportingText = {
                                val amtStr = amount.ifBlank { "0" }
                                Text(
                                    when (frequency) {
                                        Frequency.EVERY_MINUTE -> "₹$amtStr added every minute. Unspent carries to Savings."
                                        Frequency.DAILY        -> "₹$amtStr added each day. Unspent carries to Savings."
                                        Frequency.WEEKLY       -> "₹$amtStr added each week automatically."
                                        Frequency.MONTHLY      -> "₹$amtStr added each month automatically."
                                        Frequency.YEARLY       -> "₹$amtStr added each year automatically."
                                    },
                                    fontSize = 11.sp, color = TextMuted
                                )
                            }
                        )
                        ExposedDropdownMenu(
                            expanded         = freqExpanded,
                            onDismissRequest = { freqExpanded = false },
                            modifier         = Modifier.background(BgCardAlt)
                        ) {
                            Frequency.values().forEach { freq ->
                                DropdownMenuItem(
                                    text    = { Text(freq.label, color = TextPrimary) },
                                    onClick = { frequency = freq; freqExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    nameError   = name.isBlank()
                    amountError = amount.isBlank()
                    if (!nameError && !amountError) {
                        val entry = BudgetEntry(
                            name      = name.trim(),
                            amount    = amount.toDoubleOrNull() ?: 0.0,
                            kind      = selectedKind,
                            frequency = if (selectedKind == EntryKind.TIMELY_TEMPLATE) frequency else null
                        )
                        onBudgetEntryAdded(entry)
                        Toast.makeText(context, "Budget Source Added!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Text("Add Source", color = Color(0xFF0A0F1E), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}