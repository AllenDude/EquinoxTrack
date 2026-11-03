package com.equinoxtrack.app.domain

import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.BillPaymentEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/* ------------------------------------------------------------------ */
/* Bills                                                               */
/* ------------------------------------------------------------------ */

data class BillDue(
    val bill: BillEntity,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val overdue: Boolean,
    val daysUntil: Long,
)

object BillSchedule {
    private const val MAX_ITERATIONS = 2_000

    /** The [index]-th due date of a bill, computed from the anchor so month-end dates never drift. */
    fun occurrence(bill: BillEntity, index: Int): LocalDate {
        val anchor = LocalDate.ofEpochDay(bill.anchorEpochDay)
        val step = bill.interval.coerceAtLeast(1).toLong() * index
        return when (bill.frequency) {
            BillFrequency.ONCE -> anchor
            BillFrequency.WEEKLY -> anchor.plusWeeks(step)
            BillFrequency.MONTHLY -> anchor.plusMonths(step)
            BillFrequency.YEARLY -> anchor.plusYears(step)
        }
    }

    /** All due dates in [from, to] (inclusive), honouring the optional end date. */
    fun occurrencesBetween(bill: BillEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
        val end = bill.endEpochDay?.let { LocalDate.ofEpochDay(it) }
        val last = if (end != null && end.isBefore(to)) end else to
        val out = ArrayList<LocalDate>()
        if (bill.frequency == BillFrequency.ONCE) {
            val d = occurrence(bill, 0)
            if (!d.isBefore(from) && !d.isAfter(last)) out += d
            return out
        }
        var i = 0
        while (i < MAX_ITERATIONS) {
            val d = occurrence(bill, i)
            if (d.isAfter(last)) break
            if (!d.isBefore(from)) out += d
            i++
        }
        return out
    }

    private fun settledDays(bill: BillEntity, payments: List<BillPaymentEntity>): Set<Long> =
        payments.asSequence().filter { it.billId == bill.id }.map { it.dueEpochDay }.toHashSet()

    /** Occurrences from the anchor up to [until] that have neither been paid nor skipped. */
    fun unpaidOccurrences(bill: BillEntity, payments: List<BillPaymentEntity>, until: LocalDate): List<LocalDate> {
        if (!bill.active) return emptyList()
        val settled = settledDays(bill, payments)
        return occurrencesBetween(bill, LocalDate.ofEpochDay(bill.anchorEpochDay), until)
            .filter { it.toEpochDay() !in settled }
    }

    /** Earliest unsettled due date (may be overdue), looking up to two years past [today]. */
    fun nextUnpaid(bill: BillEntity, payments: List<BillPaymentEntity>, today: LocalDate): LocalDate? =
        unpaidOccurrences(bill, payments, today.plusYears(2)).firstOrNull()

    /** Unpaid occurrences of all active bills due on or before [until], overdue first. */
    fun dues(
        bills: List<BillEntity>,
        payments: List<BillPaymentEntity>,
        today: LocalDate,
        until: LocalDate,
    ): List<BillDue> = bills.filter { it.active }.flatMap { bill ->
        unpaidOccurrences(bill, payments, until).map { date ->
            BillDue(bill, date, bill.amountMinor, date.isBefore(today), ChronoUnit.DAYS.between(today, date))
        }
    }.sortedWith(compareBy({ it.dueDate }, { it.bill.name }))

    /** Approximate monthly cost, used for subscription summaries. */
    fun monthlyEquivalent(bill: BillEntity): Long {
        val n = bill.interval.coerceAtLeast(1)
        return when (bill.frequency) {
            BillFrequency.ONCE -> 0
            BillFrequency.WEEKLY -> bill.amountMinor * 52 / (12L * n)
            BillFrequency.MONTHLY -> bill.amountMinor / n
            BillFrequency.YEARLY -> bill.amountMinor / (12L * n)
        }
    }

    fun yearlyEquivalent(bill: BillEntity): Long {
        val n = bill.interval.coerceAtLeast(1)
        return when (bill.frequency) {
            BillFrequency.ONCE -> 0
            BillFrequency.WEEKLY -> bill.amountMinor * 52 / n
            BillFrequency.MONTHLY -> bill.amountMinor * 12 / n
            BillFrequency.YEARLY -> bill.amountMinor / n
        }
    }

    fun describe(bill: BillEntity): String {
        val n = bill.interval.coerceAtLeast(1)
        return when (bill.frequency) {
            BillFrequency.ONCE -> "One time"
            BillFrequency.WEEKLY -> if (n == 1) "Every week" else "Every $n weeks"
            BillFrequency.MONTHLY -> if (n == 1) "Every month" else "Every $n months"
            BillFrequency.YEARLY -> if (n == 1) "Every year" else "Every $n years"
        }
    }
}

/* ------------------------------------------------------------------ */
/* Safe-to-Spend                                                       */
/* ------------------------------------------------------------------ */

data class SafeToSpendInput(
    /** Balance of spendable accounts, base currency. */
    val spendableBalance: Long,
    /** Unpaid bills (incl. overdue) due before the period ends. */
    val upcomingBills: Long,
    /** Money earmarked in savings goals (0 when goal protection is off). */
    val goalReserve: Long,
    /** User cushion. */
    val buffer: Long,
    /** Overall monthly budget limit, if one is set. */
    val budgetLimit: Long?,
    /** Every expense in the period, including bill payments. */
    val spentInPeriod: Long,
    /** Expenses in the period that were not bill payments. */
    val discretionarySpentInPeriod: Long,
    /** Non-bill expenses made today. */
    val discretionarySpentToday: Long,
    val today: LocalDate,
    val period: DateRange,
)

enum class SafeToSpendLimit { BALANCE, BUDGET }

data class SafeToSpendResult(
    val daily: Long,
    val leftToday: Long,
    val weekly: Long,
    val remaining: Long,
    val availableAfterReserves: Long,
    val budgetRemaining: Long?,
    val limitedBy: SafeToSpendLimit,
    val daysLeft: Long,
    val discretionarySpent: Long,
    val progress: Float,
    val input: SafeToSpendInput,
)

object SafeToSpendCalculator {
    /**
     * Discretionary money = what is actually in spendable accounts, minus every obligation already
     * known for the period (unpaid bills, goal earmarks, cushion), further capped by the remaining
     * overall budget. Account balances themselves are never modified by this calculation.
     */
    fun calculate(input: SafeToSpendInput): SafeToSpendResult {
        val available = input.spendableBalance - input.upcomingBills - input.goalReserve - input.buffer
        val budgetRemaining = input.budgetLimit?.let { it - input.spentInPeriod - input.upcomingBills }
        val limitedBy = if (budgetRemaining != null && budgetRemaining < available) SafeToSpendLimit.BUDGET else SafeToSpendLimit.BALANCE
        val remaining = if (budgetRemaining != null) minOf(available, budgetRemaining) else available
        val daysLeft = input.period.daysLeftFrom(input.today).coerceAtLeast(1)

        // The daily allowance is fixed for the whole day: add today's spending back before dividing.
        val startOfDay = remaining + input.discretionarySpentToday
        val daily = if (startOfDay > 0) startOfDay / daysLeft else 0L
        val leftToday = daily - input.discretionarySpentToday
        val weekly = if (startOfDay > 0) {
            val days = minOf(7L, daysLeft)
            if (days == daysLeft) startOfDay - input.discretionarySpentToday else daily * days - input.discretionarySpentToday
        } else 0L

        val spent = input.discretionarySpentInPeriod
        val total = spent + remaining.coerceAtLeast(0)
        val progress = if (total <= 0) (if (spent > 0) 1f else 0f) else (spent.toDouble() / total).toFloat().coerceIn(0f, 1f)

        return SafeToSpendResult(
            daily = daily,
            leftToday = leftToday,
            weekly = weekly.coerceAtLeast(0),
            remaining = remaining,
            availableAfterReserves = available,
            budgetRemaining = budgetRemaining,
            limitedBy = limitedBy,
            daysLeft = daysLeft,
            discretionarySpent = spent,
            progress = progress,
            input = input,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Budgets                                                             */
/* ------------------------------------------------------------------ */

data class BudgetStatus(
    val budget: BudgetEntity,
    val period: DateRange,
    /** Limit in base currency (null when the exchange rate is missing). */
    val limit: Long,
    val spent: Long,
    val remaining: Long,
    val fraction: Float,
    val over: Boolean,
    /** Spending pace is ahead of time elapsed in the period. */
    val aheadOfPace: Boolean,
)

object BudgetCalculator {
    fun status(
        budget: BudgetEntity,
        limitBase: Long,
        spentBase: Long,
        period: DateRange,
        today: LocalDate,
    ): BudgetStatus {
        val remaining = limitBase - spentBase
        val fraction = if (limitBase <= 0) (if (spentBase > 0) 1f else 0f) else (spentBase.toDouble() / limitBase).toFloat()
        val elapsed = when {
            today.isBefore(period.start) -> 0.0
            today.isAfter(period.endInclusive) -> 1.0
            else -> (ChronoUnit.DAYS.between(period.start, today) + 1).toDouble() / period.lengthDays
        }
        return BudgetStatus(
            budget = budget,
            period = period,
            limit = limitBase,
            spent = spentBase,
            remaining = remaining,
            fraction = fraction,
            over = spentBase > limitBase,
            aheadOfPace = fraction > elapsed + 0.1 && fraction < 1f,
        )
    }
}
