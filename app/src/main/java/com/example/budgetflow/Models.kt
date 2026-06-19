package com.example.budgetflow

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// BUDGET MODEL
//
//  SAVINGS  (scalar Double, stored separately in DataStore)
//    • Starts at 0.
//    • Grows when a LUMP_SUM or TIMELY_SLOT expires with unused amount.
//    • Never touched by any deletion.
//
//  LUMP_SUM  (e.g. Bonus, Loan)
//    • One-time. Active for the calendar day it was created.
//    • On expiry: unused portion → Savings.
//    • Deletion: removed from budget. Savings untouched.
//
//  TIMELY_TEMPLATE  (e.g. Salary, Pocket Money)
//    • Pure definition — NEVER counted in budget.
//    • Engine creates one TIMELY_SLOT per period from it.
//    • Deleting template: stops future slots. Past slots / savings untouched.
//
//  TIMELY_SLOT  (engine-generated child of a template)
//    • Counted in budget while today is inside [periodStart, periodEnd].
//    • On expiry: unused portion → Savings. New slot for next period generated.
//    • Deleting a slot: removed from budget. Template and other slots untouched.
//      Engine will NOT recreate this slot for the same period.
//      Engine WILL create a fresh slot when the NEXT period starts.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Transaction(
    val id: Long = System.currentTimeMillis(),
    val description: String,
    val amount: Double,
    val date: String,            // ISO yyyy-MM-dd
    val time: String = "00:00",  // HH:mm
    val vendor: String,
    val category: String = "General"
)

@Serializable
enum class EntryKind { LUMP_SUM, TIMELY_TEMPLATE, TIMELY_SLOT }

@Serializable
enum class Frequency(val label: String) {
    EVERY_MINUTE("Every Minute"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

@Serializable
data class BudgetEntry(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val amount: Double,
    val kind: EntryKind,

    // TIMELY_TEMPLATE + TIMELY_SLOT
    val frequency: Frequency? = null,

    // TIMELY_SLOT: period this slot covers (ISO yyyy-MM-ddTHH:mm or yyyy-MM-dd)
    val periodStart: String? = null,
    val periodEnd: String? = null,

    // TIMELY_SLOT: template that spawned this slot
    val templateId: Long? = null,

    // When user created this entry
    val createdDate: String = LocalDate.now().toString(),

    // Engine sets true when leftover moved to savings; slot is done
    val isExpired: Boolean = false,

    // User sets true; entry deducted from budget. Never cascades to other entries.
    val isDeleted: Boolean = false
)

// ─── Period helpers ───────────────────────────────────────────────────────────

/**
 * ISO timestamp key that uniquely identifies the period containing [now]
 * for this frequency. Used as a stable slot identifier.
 */
fun Frequency.periodKey(now: LocalDateTime): String = when (this) {
    Frequency.EVERY_MINUTE ->
        // "yyyy-MM-ddTHH:mm" — changes every minute
        "%04d-%02d-%02dT%02d:%02d".format(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
    Frequency.DAILY   -> now.toLocalDate().toString()
    Frequency.WEEKLY  -> {
        val wf = WeekFields.of(Locale.getDefault())
        now.toLocalDate().with(wf.dayOfWeek(), 1).toString()
    }
    Frequency.MONTHLY -> "%04d-%02d-01".format(now.year, now.monthValue)
    Frequency.YEARLY  -> "%04d-01-01".format(now.year)
}

/**
 * Start and end of the period containing [now] for this frequency.
 * Both are returned as ISO strings matching the format stored in BudgetEntry.
 */
fun Frequency.periodRange(now: LocalDateTime): Pair<String, String> = when (this) {
    Frequency.EVERY_MINUTE -> {
        val key = periodKey(now)
        key to key   // start == end for minute slots (same minute string)
    }
    Frequency.DAILY -> {
        val d = now.toLocalDate().toString()
        d to d
    }
    Frequency.WEEKLY -> {
        val wf = WeekFields.of(Locale.getDefault())
        val start = now.toLocalDate().with(wf.dayOfWeek(), 1)
        start.toString() to start.plusDays(6).toString()
    }
    Frequency.MONTHLY -> {
        val start = now.toLocalDate().withDayOfMonth(1)
        start.toString() to start.plusMonths(1).minusDays(1).toString()
    }
    Frequency.YEARLY -> {
        val start = now.toLocalDate().withDayOfYear(1)
        start.toString() to start.plusYears(1).minusDays(1).toString()
    }
}

/**
 * Returns true if [now] falls inside this slot's [periodStart, periodEnd].
 * Handles both minute-precision ("yyyy-MM-ddTHH:mm") and date-only strings.
 */
fun BudgetEntry.isActiveNow(now: LocalDateTime): Boolean {
    if (kind != EntryKind.TIMELY_SLOT || isDeleted || isExpired) return false
    val s = periodStart ?: return false
    val e = periodEnd   ?: return false
    return if (s.contains('T')) {
        // Minute-precision: compare as strings (lexicographic == chronological for this format)
        val key = "%04d-%02d-%02dT%02d:%02d".format(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
        key >= s && key <= e
    } else {
        val today = now.toLocalDate()
        val start = runCatching { LocalDate.parse(s) }.getOrNull() ?: return false
        val end   = runCatching { LocalDate.parse(e) }.getOrNull() ?: return false
        today >= start && today <= end
    }
}

/**
 * Returns true if this slot's period has ended relative to [now].
 */
fun BudgetEntry.isPeriodOver(now: LocalDateTime): Boolean {
    if (kind != EntryKind.TIMELY_SLOT) return false
    val e = periodEnd ?: return false
    return if (e.contains('T')) {
        val key = "%04d-%02d-%02dT%02d:%02d".format(
            now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute
        )
        key > e
    } else {
        val today = now.toLocalDate()
        val end   = runCatching { LocalDate.parse(e) }.getOrNull() ?: return false
        today > end
    }
}

// ─── Chart view period ────────────────────────────────────────────────────────

enum class ViewPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

fun ViewPeriod.dateRange(): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    return when (this) {
        ViewPeriod.DAILY   -> today to today
        ViewPeriod.WEEKLY  -> {
            val wf = WeekFields.of(Locale.getDefault())
            val s = today.with(wf.dayOfWeek(), 1)
            s to s.plusDays(6)
        }
        ViewPeriod.MONTHLY -> {
            val s = today.withDayOfMonth(1)
            s to s.plusMonths(1).minusDays(1)
        }
        ViewPeriod.YEARLY  -> {
            val s = today.withDayOfYear(1)
            s to s.plusYears(1).minusDays(1)
        }
    }
}

// ─── Budget totals ────────────────────────────────────────────────────────────

/**
 * True live budget right now:
 *   savings + active LUMP_SUM created today + active TIMELY_SLOT whose period contains now.
 */
fun computeTotalBudget(
    entries: List<BudgetEntry>,
    savingsAmount: Double,
    now: LocalDateTime = LocalDateTime.now()
): Double {
    val today = now.toLocalDate()
    return savingsAmount + entries
        .filter { !it.isDeleted && !it.isExpired }
        .sumOf { entry ->
            when (entry.kind) {
                EntryKind.LUMP_SUM -> {
                    val created = runCatching { LocalDate.parse(entry.createdDate) }.getOrNull()
                    if (created == today) entry.amount else 0.0
                }
                EntryKind.TIMELY_SLOT -> if (entry.isActiveNow(now)) entry.amount else 0.0
                EntryKind.TIMELY_TEMPLATE -> 0.0
            }
        }
}

/**
 * Budget for the donut chart view period.
 * Counts savings + LUMP_SUM created within the view period +
 * one active/non-deleted slot per template that overlaps the view period.
 */
fun budgetForViewPeriod(
    entries: List<BudgetEntry>,
    savingsAmount: Double,
    period: ViewPeriod,
    now: LocalDateTime = LocalDateTime.now()
): Double {
    val (viewStart, viewEnd) = period.dateRange()
    val countedTemplates     = mutableSetOf<Long>()
    var total                = savingsAmount

    entries.filter { !it.isDeleted && !it.isExpired }.forEach { entry ->
        when (entry.kind) {
            EntryKind.LUMP_SUM -> {
                val created = runCatching { LocalDate.parse(entry.createdDate) }.getOrNull()
                    ?: now.toLocalDate()
                if (created >= viewStart && created <= viewEnd) total += entry.amount
            }
            EntryKind.TIMELY_SLOT -> {
                val tid = entry.templateId ?: return@forEach
                if (tid in countedTemplates) return@forEach
                // slot overlaps view period?
                val slotEndStr = entry.periodEnd ?: return@forEach
                val slotStartStr = entry.periodStart ?: return@forEach
                val slotStart = runCatching {
                    LocalDate.parse(if (slotStartStr.contains('T')) slotStartStr.take(10) else slotStartStr)
                }.getOrNull() ?: return@forEach
                val slotEnd = runCatching {
                    LocalDate.parse(if (slotEndStr.contains('T')) slotEndStr.take(10) else slotEndStr)
                }.getOrNull() ?: return@forEach
                if (slotStart <= viewEnd && slotEnd >= viewStart) {
                    total += entry.amount
                    countedTemplates.add(tid)
                }
            }
            EntryKind.TIMELY_TEMPLATE -> {}
        }
    }
    return total
}

/** Transactions within a view period's date range. */
fun spentInViewPeriod(transactions: List<Transaction>, period: ViewPeriod): Double {
    val (start, end) = period.dateRange()
    return transactions.filter { tx ->
        runCatching {
            val d = LocalDate.parse(tx.date)
            d >= start && d <= end
        }.getOrDefault(false)
    }.sumOf { it.amount }
}

// ─── Recurrence engine ────────────────────────────────────────────────────────

/**
 * Called on app open (and can be called every minute for EVERY_MINUTE sources).
 *
 * For each TIMELY_SLOT that has expired:
 *   → Calculate leftover (slot.amount − spending during that slot's period)
 *   → Add leftover to savings
 *   → Mark slot isExpired = true
 *
 * For each active TIMELY_TEMPLATE (not deleted):
 *   → If no slot exists for the current period → create one
 *   → If a slot was deleted for this period → do NOT recreate it (respect user's deletion)
 *
 * Deleted slots from past periods do NOT block future-period slot creation.
 */
fun runRecurrenceEngine(
    entries: List<BudgetEntry>,
    transactions: List<Transaction>,
    savingsAmount: Double,
    now: LocalDateTime = LocalDateTime.now()
): Pair<List<BudgetEntry>, Double> {

    val mutable = entries.toMutableList()
    var savings = savingsAmount

    fun spentDuring(startStr: String, endStr: String): Double {
        // Handles both "yyyy-MM-dd" and "yyyy-MM-ddTHH:mm" bounds
        val startDate = runCatching {
            LocalDate.parse(if (startStr.contains('T')) startStr.take(10) else startStr)
        }.getOrNull() ?: return 0.0
        val endDate = runCatching {
            LocalDate.parse(if (endStr.contains('T')) endStr.take(10) else endStr)
        }.getOrNull() ?: return 0.0
        return transactions.filter { tx ->
            runCatching {
                val d = LocalDate.parse(tx.date)
                d >= startDate && d <= endDate
            }.getOrDefault(false)
        }.sumOf { it.amount }
    }

    // ── Step 1: Expire overdue slots ──────────────────────────────────────────
    val afterExpiry = mutable.map { entry ->
        if (entry.isDeleted || entry.isExpired || entry.kind != EntryKind.TIMELY_SLOT)
            return@map entry
        if (entry.isPeriodOver(now)) {
            val spent    = spentDuring(entry.periodStart!!, entry.periodEnd!!).coerceAtMost(entry.amount)
            val leftover = entry.amount - spent
            if (leftover > 0.001) savings += leftover
            entry.copy(isExpired = true)
        } else entry
    }.toMutableList()

    // ── Step 2: Expire overdue lump sums ─────────────────────────────────────
    val afterLumpExpiry = afterExpiry.map { entry ->
        if (entry.isDeleted || entry.isExpired || entry.kind != EntryKind.LUMP_SUM)
            return@map entry
        val created = runCatching { LocalDate.parse(entry.createdDate) }.getOrNull()
            ?: return@map entry
        if (created < now.toLocalDate()) {
            val spent    = spentDuring(entry.createdDate, entry.createdDate).coerceAtMost(entry.amount)
            val leftover = entry.amount - spent
            if (leftover > 0.001) savings += leftover
            entry.copy(isExpired = true)
        } else entry
    }.toMutableList()

    // ── Step 3: Backfill missed periods + generate current slot ──────────────
    //
    // When the app is closed across multiple periods (e.g. closed at 20:27, opened at 20:29
    // for an EVERY_MINUTE source), the engine was never called for 20:28. That means no slot
    // was created for 20:28, so Step 1 has nothing to expire for it, and savings never grows.
    //
    // Fix: for each template, find the most recent slot we know about, then walk forward
    // period-by-period up to (but not including) the current period, creating a slot for each
    // missed period and immediately expiring it with its full amount added to savings (since
    // there can be no transactions recorded during a period the app wasn't running).
    // Finally create the live slot for the current period as before.

    val activeTemplates = afterLumpExpiry.filter {
        it.kind == EntryKind.TIMELY_TEMPLATE && !it.isDeleted
    }

    for (template in activeTemplates) {
        val freq = template.frequency ?: continue
        val currentPeriodKey = freq.periodKey(now)
        val (currentPeriodStart, currentPeriodEnd) = freq.periodRange(now)

        // All slots we already know about for this template (any state)
        val knownSlots = afterLumpExpiry.filter { e ->
            e.kind == EntryKind.TIMELY_SLOT && e.templateId == template.id
        }
        val knownPeriodStarts = knownSlots.map { it.periodStart }.toSet()

        // Find the most recent slot's period start to know where to begin backfilling.
        // For non-minute frequencies this is a date string; for EVERY_MINUTE it's "yyyy-MM-ddTHH:mm".
        val latestKnownStart: LocalDateTime? = knownSlots
            .mapNotNull { slot ->
                val s = slot.periodStart ?: return@mapNotNull null
                runCatching {
                    if (s.contains('T')) LocalDateTime.parse(s + ":00") // append seconds for parse
                    else LocalDateTime.of(LocalDate.parse(s), java.time.LocalTime.MIDNIGHT)
                }.getOrNull()
            }
            .maxOrNull()

        // Determine the starting point for backfill: one period after the latest known slot,
        // or the template's creation date if no slots exist yet.
        val backfillStart: LocalDateTime = if (latestKnownStart != null) {
            when (freq) {
                Frequency.EVERY_MINUTE -> latestKnownStart.plusMinutes(1)
                Frequency.DAILY        -> latestKnownStart.plusDays(1)
                Frequency.WEEKLY       -> latestKnownStart.plusWeeks(1)
                Frequency.MONTHLY      -> latestKnownStart.plusMonths(1)
                Frequency.YEARLY       -> latestKnownStart.plusYears(1)
            }
        } else {
            // No slots at all yet — start from when template was created
            runCatching {
                LocalDateTime.of(LocalDate.parse(template.createdDate), java.time.LocalTime.MIDNIGHT)
            }.getOrElse { now }
        }

        // Walk from backfillStart up to (not including) the current period,
        // creating and immediately expiring a slot for each missed period.
        var cursor = backfillStart
        var safetyLimit = 0 // prevent infinite loops (max 10000 periods backfilled)
        while (freq.periodKey(cursor) != currentPeriodKey && safetyLimit < 10000) {
            safetyLimit++
            val (ps, pe) = freq.periodRange(cursor)

            // Only create if this period isn't already tracked
            if (ps !in knownPeriodStarts) {
                val stableId = (template.id.toString() + ps).hashCode().toLong()
                    .let { if (it < 0) it + Long.MAX_VALUE else it }

                // Missed period: app was closed, no transactions possible → full amount to savings
                savings += template.amount

                afterLumpExpiry.add(
                    BudgetEntry(
                        id          = stableId,
                        name        = template.name,
                        amount      = template.amount,
                        kind        = EntryKind.TIMELY_SLOT,
                        frequency   = freq,
                        periodStart = ps,
                        periodEnd   = pe,
                        templateId  = template.id,
                        createdDate = now.toLocalDate().toString(),
                        isExpired   = true   // immediately expired — it's already in the past
                    )
                )
            }

            cursor = when (freq) {
                Frequency.EVERY_MINUTE -> cursor.plusMinutes(1)
                Frequency.DAILY        -> cursor.plusDays(1)
                Frequency.WEEKLY       -> cursor.plusWeeks(1)
                Frequency.MONTHLY      -> cursor.plusMonths(1)
                Frequency.YEARLY       -> cursor.plusYears(1)
            }
        }

        // Create the live slot for the current period (if not already tracked)
        if (currentPeriodStart !in knownPeriodStarts) {
            val stableId = (template.id.toString() + currentPeriodKey).hashCode().toLong()
                .let { if (it < 0) it + Long.MAX_VALUE else it }
            afterLumpExpiry.add(
                BudgetEntry(
                    id          = stableId,
                    name        = template.name,
                    amount      = template.amount,
                    kind        = EntryKind.TIMELY_SLOT,
                    frequency   = freq,
                    periodStart = currentPeriodStart,
                    periodEnd   = currentPeriodEnd,
                    templateId  = template.id,
                    createdDate = now.toLocalDate().toString()
                )
            )
        }
    }

    return afterLumpExpiry to savings
}