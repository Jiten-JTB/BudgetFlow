package com.example.budgetflow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

// One row shown per income source. Carries both the template and its current slot.
data class BudgetSourceRow(
    val template: BudgetEntry,       // LUMP_SUM or TIMELY_TEMPLATE
    val activeSlot: BudgetEntry?     // null for lump sum or when no slot is active right now
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    entries: List<BudgetEntry>,
    savingsAmount: Double,
    transactions: List<Transaction>,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember { LocalDateTime.now() }

    // ── Period dropdown ───────────────────────────────────────────────────────
    var viewPeriod     by remember { mutableStateOf(ViewPeriod.MONTHLY) }
    var periodExpanded by remember { mutableStateOf(false) }

    // ── Derived numbers ───────────────────────────────────────────────────────
    val totalBudget   = budgetForViewPeriod(entries, savingsAmount, viewPeriod, now)
    val spent         = spentInViewPeriod(transactions, viewPeriod)
    val remaining     = (totalBudget - spent).coerceAtLeast(0.0)
    val spentFraction = if (totalBudget > 0) (spent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f

    val visible    = entries.filter { !it.isDeleted && !it.isExpired }
    val hasSavings = savingsAmount > 0.001

    // Build one row per source
    val sourceRows: List<BudgetSourceRow> = buildList {
        visible.filter { it.kind == EntryKind.LUMP_SUM }.forEach { add(BudgetSourceRow(it, null)) }
        visible.filter { it.kind == EntryKind.TIMELY_TEMPLATE }.forEach { tmpl ->
            val slot = visible.firstOrNull { e ->
                e.kind == EntryKind.TIMELY_SLOT && e.templateId == tmpl.id && e.isActiveNow(now)
            }
            add(BudgetSourceRow(tmpl, slot))
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    var pendingDelete by remember { mutableStateOf<BudgetSourceRow?>(null) }

    pendingDelete?.let { row ->
        val deletingSlot = row.activeSlot != null
        val isTimely     = row.template.kind == EntryKind.TIMELY_TEMPLATE
        val name         = row.template.name
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.dp),
            title = {
                Text(
                    if (isTimely) "Remove this source?" else "Remove this source?",
                    color = TextPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    when {
                        isTimely ->
                            "\"$name\" will be removed and will no longer generate new periods. " +
                                    "Past history and your savings are not affected."
                        else ->
                            "\"$name\" will be removed from your budget. " +
                                    "Your savings are not affected."
                    },
                    color = TextMuted, fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Delete the SLOT if one is active (preserves template + history).
                        // Delete the TEMPLATE only when there is no active slot (nothing to lose).
                        // Always delete the template — this stops all future slot generation.
                        // Past slots (expired) are historical records and remain untouched.
                        // The active slot will disappear from the UI because the template
                        // is filtered out of visibleEntries, so its slot won't be shown either.
                        onDeleteEntry(row.template.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    shape  = RoundedCornerShape(10.dp)
                ) { Text("Remove", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Header + period dropdown
        item {
            Text("BUDGETFLOW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Budget", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                ExposedDropdownMenuBox(expanded = periodExpanded, onExpandedChange = { periodExpanded = it }) {
                    Surface(
                        modifier = Modifier.menuAnchor(),
                        shape    = RoundedCornerShape(20.dp),
                        color    = Accent.copy(alpha = 0.15f),
                        onClick  = { periodExpanded = !periodExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(viewPeriod.label, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("▾", color = Accent, fontSize = 11.sp)
                        }
                    }
                    ExposedDropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false },
                        modifier = Modifier.background(BgCardAlt)
                    ) {
                        ViewPeriod.values().forEach { vp ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (vp == viewPeriod) "✓" else "  ", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(vp.label, color = TextPrimary)
                                    }
                                },
                                onClick = { viewPeriod = vp; periodExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        // Donut card
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard), elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (totalBudget == 0.0) {
                        Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(BgCardAlt), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("₹0", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                Text("No budget set", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    } else {
                        BudgetDonut(spent = spent.toFloat(), total = totalBudget.toFloat(), modifier = Modifier.size(200.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        BudgetStat("Budget",    "₹${"%.0f".format(totalBudget)}", Accent)
                        Box(Modifier.width(1.dp).height(36.dp).background(Divider))
                        BudgetStat("Spent",     "₹${"%.0f".format(spent)}", AccentRed)
                        Box(Modifier.width(1.dp).height(36.dp).background(Divider))
                        BudgetStat("Remaining", "₹${"%.0f".format(remaining)}", AccentGreen)
                    }
                }
            }
        }

        // Progress bar
        if (totalBudget > 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Budget Used", fontSize = 13.sp, color = TextMuted)
                                Text("${viewPeriod.label} view", fontSize = 11.sp, color = TextMuted.copy(alpha = 0.6f))
                            }
                            Text(
                                "${"%.1f".format(spentFraction * 100)}%",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = when {
                                    spentFraction > 0.90f -> AccentRed
                                    spentFraction > 0.75f -> Color(0xFFFBBF24)
                                    else -> AccentGreen
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress   = { spentFraction },
                            modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                            color      = when {
                                spentFraction > 0.90f -> AccentRed
                                spentFraction > 0.75f -> Color(0xFFFBBF24)
                                else -> AccentGreen
                            },
                            trackColor = BgCardAlt
                        )
                    }
                }
            }
        }

        // Savings
        if (hasSavings) {
            item { Text("Savings", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen) }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.07f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("🏦", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Savings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Unused amounts carried forward", fontSize = 11.sp, color = AccentGreen.copy(alpha = 0.8f))
                        }
                        Text("+₹${"%.0f".format(savingsAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                }
            }
        }

        // Source rows — one card per income source
        if (sourceRows.isNotEmpty()) {
            item { Text("Sources", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }
            items(sourceRows, key = { it.template.id }) { row ->
                BudgetSourceRowCard(row = row, onDelete = { pendingDelete = row })
            }
        }

        // Empty state
        if (sourceRows.isEmpty() && !hasSavings) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No budget sources yet", color = TextMuted, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap + to add one", color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(88.dp)) }
    }
}

// ─── One card per source ──────────────────────────────────────────────────────

@Composable
private fun BudgetSourceRowCard(row: BudgetSourceRow, onDelete: () -> Unit) {
    val isLumpSum  = row.template.kind == EntryKind.LUMP_SUM
    val freq       = row.template.frequency
    val slot       = row.activeSlot
    val hasSlot    = slot != null

    val emoji = if (isLumpSum) "💰" else "🔁"

    // Subtitle: for timely, show frequency pill + slot status
    val slotStatus = when {
        isLumpSum -> "One-time · expires today"
        hasSlot   -> {
            val s = slot!!.periodStart ?: ""
            val e = slot.periodEnd ?: ""
            if (s == e) "Active: $s" else "Active: $s → $e"
        }
        else -> "No active slot this period"
    }

    // Amount shown is the ACTIVE slot amount (what's in budget now),
    // or the template amount for lump sum / template-only display
    val displayAmount = slot?.amount ?: row.template.amount

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isLumpSum) AccentGreen.copy(alpha = 0.12f) else Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 20.sp) }

            Spacer(Modifier.width(14.dp))

            // Text block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.template.name,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isLumpSum && freq != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = Accent.copy(alpha = 0.12f)) {
                            Text(
                                freq.label,
                                fontSize = 10.sp, color = Accent, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        slotStatus, fontSize = 11.sp,
                        color = if (hasSlot || isLumpSum) TextMuted else TextMuted.copy(alpha = 0.5f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Amount
            Text(
                "+₹${"%.0f".format(displayAmount)}",
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (hasSlot || isLumpSum) AccentGreen else TextMuted
            )

            Spacer(Modifier.width(2.dp))

            // Single delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint     = AccentRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun BudgetStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextMuted, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun BudgetDonut(spent: Float, total: Float, modifier: Modifier = Modifier, thickness: Dp = 18.dp) {
    val sweepAngle       = if (total > 0f) (spent / total * 360f).coerceIn(0f, 360f) else 0f
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(spent, total) { animatedProgress.snapTo(0f); animatedProgress.animateTo(1f, animationSpec = tween(1200)) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(thickness / 2)) {
            val strokePx = thickness.toPx()
            val arcSize  = Size(size.width - strokePx, size.height - strokePx)
            val topLeft  = Offset(strokePx / 2, strokePx / 2)
            drawArc(color = Color(0xFF1A2B4A), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(strokePx, cap = StrokeCap.Round), size = arcSize, topLeft = topLeft)
            if (sweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF6495ED), Color(0xFF9BB5F0), Color(0xFF6495ED))),
                    startAngle = -90f, sweepAngle = sweepAngle * animatedProgress.value,
                    useCenter = false, style = Stroke(strokePx, cap = StrokeCap.Round), size = arcSize, topLeft = topLeft
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spent", fontSize = 12.sp, color = TextMuted)
            Text("₹${"%.0f".format(spent)}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
            Text("of ₹${"%.0f".format(total)}", fontSize = 13.sp, color = TextMuted)
        }
    }
}