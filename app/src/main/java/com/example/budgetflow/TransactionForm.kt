package com.example.budgetflow

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onTransactionAdded: (Transaction) -> Unit = {}
) {
    val context = LocalContext.current

    var description by rememberSaveable { mutableStateOf("") }
    var amount      by rememberSaveable { mutableStateOf("") }
    var vendor      by rememberSaveable { mutableStateOf("") }
    var category    by rememberSaveable { mutableStateOf("General") }
    var categoryExpanded by remember { mutableStateOf(false) }

    // Date & time — default to now
    val nowCal = remember { Calendar.getInstance() }
    var selectedDate by rememberSaveable {
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var selectedTime by rememberSaveable {
        mutableStateOf(
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }

    var descError   by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf(false) }
    var vendorError by rememberSaveable { mutableStateOf(false) }

    val categories = listOf("General", "Food", "Transport", "Entertainment", "Utilities", "Health", "Shopping")

    // ── Date picker ───────────────────────────────────────────────────────────
    val datePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            },
            nowCal.get(Calendar.YEAR),
            nowCal.get(Calendar.MONTH),
            nowCal.get(Calendar.DAY_OF_MONTH)
        ).also { it.datePicker.maxDate = System.currentTimeMillis() } // no future dates
    }

    // ── Time picker ───────────────────────────────────────────────────────────
    val timePicker = remember {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                selectedTime = "%02d:%02d".format(hour, minute)
            },
            nowCal.get(Calendar.HOUR_OF_DAY),
            nowCal.get(Calendar.MINUTE),
            true // 24-hour
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text("ADD TRANSACTION", fontSize = 10.sp, color = Accent, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("New Transaction", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Description
                BfTextField(
                    value = description,
                    onValueChange = { description = it; descError = false },
                    label = "What did you buy?",
                    isError = descError,
                    errorMsg = "Please describe the purchase"
                )

                // Amount
                BfTextField(
                    value = amount,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() || it == '.' }) { amount = v; amountError = false }
                    },
                    label = "Amount (₹)",
                    isError = amountError,
                    errorMsg = "Enter a valid amount",
                    keyboardType = KeyboardType.Decimal
                )

                // Vendor
                BfTextField(
                    value = vendor,
                    onValueChange = { vendor = it; vendorError = false },
                    label = "Where did you buy it?",
                    isError = vendorError,
                    errorMsg = "Please enter the vendor / store"
                )

                // Date button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date picker button
                    OutlinedButton(
                        onClick  = { datePicker.show() },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Icon(
                            Icons.Outlined.DateRange,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            selectedDate,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }

                    // Time picker button
                    OutlinedButton(
                        onClick  = { timePicker.show() },
                        modifier = Modifier.weight(0.6f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Divider)
                    ) {
                        Text("🕐 $selectedTime", fontSize = 13.sp, color = TextPrimary)
                    }
                }

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded          = categoryExpanded,
                    onExpandedChange  = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value          = category,
                        onValueChange  = {},
                        readOnly       = true,
                        label          = { Text("Category", color = TextMuted) },
                        trailingIcon   = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors         = bfTextFieldColors(),
                        modifier       = Modifier.menuAnchor().fillMaxWidth(),
                        shape          = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded         = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier         = Modifier.background(BgCardAlt)
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text    = { Text("${categoryEmoji(cat)}  $cat", color = TextPrimary) },
                                onClick = { category = cat; categoryExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    descError   = description.isBlank()
                    amountError = amount.isBlank()
                    vendorError = vendor.isBlank()
                    if (!descError && !amountError && !vendorError) {
                        onTransactionAdded(
                            Transaction(
                                description = description.trim(),
                                amount      = amount.toDoubleOrNull() ?: 0.0,
                                date        = selectedDate,
                                time        = selectedTime,
                                vendor      = vendor.trim(),
                                category    = category
                            )
                        )
                        Toast.makeText(context, "Transaction added!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Text("Add Transaction", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

// ─── Shared text field helper ─────────────────────────────────────────────────

@Composable
fun BfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorMsg: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, color = TextMuted, fontSize = 13.sp) },
        isError       = isError,
        supportingText = { if (isError) Text(errorMsg, color = AccentRed, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine    = true,
        colors        = bfTextFieldColors(),
        shape         = RoundedCornerShape(12.dp),
        modifier      = Modifier.fillMaxWidth()
    )
}

@Composable
fun bfTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor    = TextPrimary,
    unfocusedTextColor  = TextPrimary,
    focusedBorderColor  = Accent,
    unfocusedBorderColor = Divider,
    cursorColor         = Accent,
    focusedLabelColor   = Accent,
    unfocusedLabelColor = TextMuted,
    errorBorderColor    = AccentRed,
    errorLabelColor     = AccentRed
)