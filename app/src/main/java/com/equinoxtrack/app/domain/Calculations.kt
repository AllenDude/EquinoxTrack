package com.equinoxtrack.app.domain

import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.data.db.AssetValuationEntity
import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.data.db.GoalEntryEntity
import com.equinoxtrack.app.data.db.TransactionEntity
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/* ------------------------------------------------------------------ */
/* Currency conversion                                                 */
/* ------------------------------------------------------------------ */

class CurrencyConverter(rates: List<ExchangeRateEntity>) {
    private val perReference: Map<String, BigDecimal> = rates.mapNotNull { r ->
        runCatching { BigDecimal(r.perReference) }.getOrNull()
            ?.takeIf { it.signum() > 0 }
            ?.let { r.currency to it }
    }.toMap()

    fun hasRate(currency: String) = perReference.containsKey(currency)

    /** Converts minor units between currencies. Returns null when a rate is missing. */
    fun convert(minor: Long, from: String, to: String): Long? {
        if (from == to || minor == 0L) return minor
        val rFrom = perReference[from] ?: return null
        val rTo = perReference[to] ?: return null
        val amount = Money.toDecimal(minor, from)
        val converted = amount.divide(rFrom, MathContext.DECIMAL128).multiply(rTo, MathContext.DECIMAL128)
        return Money.toMinor(converted, to)
    }

    /** How many units of [to] one unit of [from] buys, or null if unknown. */
    fun rate(from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val rFrom = perReference[from] ?: return null
        val rTo = perReference[to] ?: return null
        return rTo.divide(rFrom, MathContext.DECIMAL64)
    }

    companion object {
        /**
         * Produces the stored per-reference value for [currency] given "1 [currency] = [unitsOfBase] base",
         * keeping the base currency's own stored value as the anchor (defaults to 1).
         */
        fun perReferenceFor(unitsOfBase: BigDecimal, basePerReference: BigDecimal?): BigDecimal {
            val anchor = basePerReference ?: BigDecimal.ONE
            return anchor.divide(unitsOfBase, MathContext.DECIMAL64)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Periods                                                             */
/* ------------------------------------------------------------------ */

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
    val lengthDays: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
    fun daysLeftFrom(today: LocalDate): Long =
        if (today.isAfter(endInclusive)) 0 else ChronoUnit.DAYS.between(maxOf(today, start), endInclusive) + 1
}

enum class PeriodKind(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

object Periods {
    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun localDateTime(epochMillis: Long, zone: ZoneId): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun startMillis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Budget month that contains [today] when months start on [startDay] (1..28). */
    fun budgetMonth(today: LocalDate, startDay: Int): DateRange {
        val day = startDay.coerceIn(1, 28)
        val start = if (today.dayOfMonth >= day) today.withDayOfMonth(day) else today.minusMonths(1).withDayOfMonth(day)
        return DateRange(start, start.plusMonths(1).minusDays(1))
    }

    fun budgetMonthOf(month: YearMonth, startDay: Int): DateRange {
        val day = startDay.coerceIn(1, 28)
        val start = month.atDay(day)
        return DateRange(start, start.plusMonths(1).minusDays(1))
    }

    fun month(ym: YearMonth) = DateRange(ym.atDay(1), ym.atEndOfMonth())

    fun week(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
        return DateRange(start, start.plusDays(6))
    }

    fun year(year: Int) = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    fun period(kind: PeriodKind, anchor: LocalDate): DateRange = when (kind) {
        PeriodKind.WEEK -> week(anchor)
        PeriodKind.MONTH -> month(YearMonth.from(anchor))
        PeriodKind.YEAR -> year(anchor.year)
    }

    fun shift(kind: PeriodKind, anchor: LocalDate, by: Long): LocalDate = when (kind) {
        PeriodKind.WEEK -> anchor.plusWeeks(by)
        PeriodKind.MONTH -> anchor.plusMonths(by)
        PeriodKind.YEAR -> anchor.plusYears(by)
    }
}

/* ------------------------------------------------------------------ */
/* Account balances                                                    */
/* ------------------------------------------------------------------ */

object BalanceCalculator {

    /** Effect of one transaction on one account's balance, in that account's currency. */
    fun delta(tx: TransactionEntity, accountId: Long): Long {
        var d = 0L
        if (tx.accountId == accountId) {
            d += when (tx.type) {
                TxType.INCOME -> tx.amountMinor
                TxType.EXPENSE -> -tx.amountMinor
                TxType.TRANSFER -> -tx.amountMinor
                TxType.ADJUSTMENT -> tx.amountMinor
            }
        }
        if (tx.type == TxType.TRANSFER && tx.toAccountId == accountId) {
            d += tx.toAmountMinor ?: tx.amountMinor
        }
        return d
    }

    /** Current balance of every account: opening balance plus the effect of the whole ledger. */
    fun balances(accounts: List<AccountEntity>, txs: List<TransactionEntity>): Map<Long, Long> {
        val result = HashMap<Long, Long>(accounts.size * 2)
        accounts.forEach { result[it.id] = it.openingBalanceMinor }
        for (tx in txs) {
            when (tx.type) {
                TxType.INCOME -> result.computeIfPresent(tx.accountId) { _, v -> v + tx.amountMinor }
                TxType.EXPENSE -> result.computeIfPresent(tx.accountId) { _, v -> v - tx.amountMinor }
                TxType.ADJUSTMENT -> result.computeIfPresent(tx.accountId) { _, v -> v + tx.amountMinor }
                TxType.TRANSFER -> {
                    result.computeIfPresent(tx.accountId) { _, v -> v - tx.amountMinor }
                    val to = tx.toAccountId
                    if (to != null) result.computeIfPresent(to) { _, v -> v + (tx.toAmountMinor ?: tx.amountMinor) }
                }
            }
        }
        return result
    }

    /** Balance of [account] at the end of each day in [range] (for trend charts). */
    fun dailyBalances(
        account: AccountEntity,
        txs: List<TransactionEntity>,
        range: DateRange,
        zone: ZoneId,
    ): List<Pair<LocalDate, Long>> {
        val relevant = txs.filter { it.accountId == account.id || it.toAccountId == account.id }
        var running = account.openingBalanceMinor
        val byDay = sortedMapOf<LocalDate, Long>()
        relevant.forEach { tx ->
            val day = Periods.localDate(tx.occurredAt, zone)
            byDay[day] = (byDay[day] ?: 0L) + delta(tx, account.id)
        }
        byDay.filterKeys { it.isBefore(range.start) }.values.forEach { running += it }
        val out = ArrayList<Pair<LocalDate, Long>>()
        var d = range.start
        while (!d.isAfter(range.endInclusive)) {
            running += byDay[d] ?: 0L
            out += d to running
            d = d.plusDays(1)
        }
        return out
    }

    /** Signed adjustment needed so that the account shows [actualBalance]. */
    fun adjustmentFor(currentBalance: Long, actualBalance: Long): Long = actualBalance - currentBalance
}

/* ------------------------------------------------------------------ */
/* Goals                                                               */
/* ------------------------------------------------------------------ */

data class GoalProgress(
    val goal: GoalEntity,
    val saved: Long,
    val remaining: Long,
    val fraction: Float,
    val percent: Int,
    val completed: Boolean,
    val daysLeft: Long?,
    /** Suggested monthly contribution to finish on time (null without a target date). */
    val monthlyNeeded: Long?,
)

object GoalCalculator {
    fun saved(entries: List<GoalEntryEntity>): Long = entries.sumOf { it.amountMinor }

    fun progress(goal: GoalEntity, entries: List<GoalEntryEntity>, today: LocalDate): GoalProgress {
        val saved = saved(entries.filter { it.goalId == goal.id })
        val remaining = (goal.targetMinor - saved).coerceAtLeast(0)
        val fraction = if (goal.targetMinor <= 0) 0f else (saved.toDouble() / goal.targetMinor).coerceIn(0.0, 1.0).toFloat()
        val percent = if (goal.targetMinor <= 0) 0 else
            BigDecimal(saved.coerceAtLeast(0)).multiply(BigDecimal(100))
                .divide(BigDecimal(goal.targetMinor), 0, RoundingMode.FLOOR).toInt().coerceAtMost(100)
        val target = goal.targetEpochDay?.let { LocalDate.ofEpochDay(it) }
        val daysLeft = target?.let { ChronoUnit.DAYS.between(today, it) }
        val monthly = if (target != null && remaining > 0) {
            val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)).coerceAtLeast(0) + 1
            ceilDiv(remaining, months)
        } else null
        return GoalProgress(goal, saved, remaining, fraction, percent, saved >= goal.targetMinor && goal.targetMinor > 0, daysLeft, monthly)
    }

    fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) a else (a + b - 1) / b
}

/* ------------------------------------------------------------------ */
/* Net worth                                                           */
/* ------------------------------------------------------------------ */

data class NetWorthBreakdown(
    val byType: Map<AccountType, Long>,
    val accountsTotal: Long,
    val assetsTotal: Long,
    val total: Long,
    val assetValues: Map<Long, Long>,
    val missingRates: Set<String>,
)

object NetWorthCalculator {

    /** Latest valuation of every asset (by date, then insertion order). */
    fun currentAssetValues(valuations: List<AssetValuationEntity>): Map<Long, Long> =
        valuations.groupBy { it.assetId }.mapValues { (_, list) ->
            list.maxWith(compareBy<AssetValuationEntity>({ it.epochDay }, { it.id })).valueMinor
        }

    /**
     * Net worth = money in accounts + separately tracked personal assets.
     * Account money is counted exactly once (from balances). Savings goals are earmarks inside
     * accounts and are therefore never added again.
     */
    fun compute(
        accounts: List<AccountEntity>,
        balances: Map<Long, Long>,
        assets: List<AssetEntity>,
        valuations: List<AssetValuationEntity>,
        converter: CurrencyConverter,
        base: String,
    ): NetWorthBreakdown {
        val missing = mutableSetOf<String>()
        val byType = AccountType.entries.associateWith { 0L }.toMutableMap()
        accounts.filter { it.includeInTotals && !it.archived }.forEach { a ->
            val converted = converter.convert(balances[a.id] ?: 0L, a.currency, base)
            if (converted == null) missing += a.currency else byType[a.type] = byType.getValue(a.type) + converted
        }
        val values = currentAssetValues(valuations)
        var assetsTotal = 0L
        assets.filter { it.includeInNetWorth && !it.archived }.forEach { asset ->
            val converted = converter.convert(values[asset.id] ?: 0L, asset.currency, base)
            if (converted == null) missing += asset.currency else assetsTotal += converted
        }
        val accountsTotal = byType.values.sum()
        return NetWorthBreakdown(byType, accountsTotal, assetsTotal, accountsTotal + assetsTotal, values, missing)
    }
}
