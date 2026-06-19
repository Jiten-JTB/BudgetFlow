package com.example.budgetflow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─── Design tokens (shared across all screens) ────────────────────────────────
val BgDeep      = Color(0xFF0A0F1E)
val BgCard      = Color(0xFF111827)
val BgCardAlt   = Color(0xFF1A2235)
val Accent      = Color(0xFF6495ED)
val AccentGreen = Color(0xFF34D399)
val AccentRed   = Color(0xFFF87171)
val TextPrimary = Color(0xFFE8EAF0)
val TextMuted   = Color(0xFF8892A4)
val Divider     = Color(0xFF1E2D45)

enum class SortMode { TIME, AMOUNT }

@Composable
fun HomeScreen(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMode by remember { mutableStateOf(SortMode.TIME) }

    val todayStr          = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todayTransactions = transactions.filter { it.date == todayStr }
    val todayTotal        = todayTransactions.sumOf { it.amount }

    val sorted = when (sortMode) {
        SortMode.TIME   -> todayTransactions.sortedWith(
            compareByDescending<Transaction> { it.date }.thenByDescending { it.time }
        )
        SortMode.AMOUNT -> todayTransactions.sortedByDescending { it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Text("BUDGETFLOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 3.sp)
            Spacer(Modifier.height(4.dp))
            Text("Overview", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }

        // ── Summary card ─────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1B3A6B), Color(0xFF0D1F44))))
                    .padding(24.dp)
            ) {
                Column {
                    Text("Today's Spending", fontSize = 13.sp, color = TextMuted, letterSpacing = 0.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "₹ ${"%,.0f".format(todayTotal)}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Accent
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column {
                            Text("Transactions", fontSize = 11.sp, color = TextMuted, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(todayTransactions.size.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("Avg per txn", fontSize = 11.sp, color = TextMuted, letterSpacing = 0.5.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (todayTransactions.isEmpty()) "₹0"
                                else "₹${"%.0f".format(todayTotal / todayTransactions.size)}",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // ── Section header ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Today's Transactions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortChip("Time",   sortMode == SortMode.TIME)   { sortMode = SortMode.TIME }
                    SortChip("Amount", sortMode == SortMode.AMOUNT) { sortMode = SortMode.AMOUNT }
                }
            }
        }

        // ── Rows ─────────────────────────────────────────────────────────────
        if (sorted.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No transactions today", color = TextMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap + to add one", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(sorted, key = { it.id }) { tx ->
                TransactionRow(tx, onClick = { onTransactionClick(tx) })
            }
        }

        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 12.sp) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Accent,
            selectedLabelColor     = Color.White,
            containerColor         = BgCardAlt,
            labelColor             = TextMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            selectedBorderColor = Accent.copy(alpha = 0.3f),
            borderColor = Divider
        )
    )
}

@Composable
fun TransactionRow(tx: Transaction, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick).animateContentSize(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryEmoji(tx.category), fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.description, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(tx.vendor, fontSize = 12.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("−₹${"%.0f".format(tx.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                Spacer(Modifier.height(2.dp))
                Text("${formatDate(tx.date)}  ${tx.time}", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

fun categoryEmoji(category: String): String = when (category.lowercase()) {
    "food"          -> "🍔"
    "transport"     -> "🚗"
    "entertainment" -> "🎬"
    "utilities"     -> "⚡"
    "health"        -> "💊"
    "shopping"      -> "🛍️"
    else            -> "💳"
}

fun formatDate(iso: String): String {
    return try {
        val date = LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofPattern("d MMM"))
    } catch (e: Exception) { iso }
}