package com.equinoxtrack.app.domain

import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.data.db.AssetValuationEntity
import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.BillPaymentEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import com.equinoxtrack.app.data.db.CategoryEntity
import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.data.db.GoalEntryEntity
import com.equinoxtrack.app.data.db.TransactionWithLines
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Everything stored in the database, as one immutable value. */
data class FinanceData(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionWithLines> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val billPayments: List<BillPaymentEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val goalEntries: List<GoalEntryEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val valuations: List<AssetValuationEntity> = emptyList(),
    val rates: List<ExchangeRateEntity> = emptyList(),
)

data class FlowTotals(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
    /** Share of income kept, 0..1 (can be negative when spending exceeds income). */
    val savingsRate: Float get() = if (income <= 0) 0f else (net.toDouble() / income).toFloat()
}

data class CategoryAmount(val categoryId: Long?, val amount: Long)

data class SeriesPoint(val label: String, val range: DateRange, val income: Long, val expense: Long)

/**
 * Read model built from [FinanceData] + settings for a given day. All derived numbers in the app
 * (balances, totals, Safe-to-Spend, budgets, net worth, analytics) come from here, so every screen
 * and widget agrees and everything is unit-testable without Android.
 */
class Ledger(
    val data: FinanceData,
    val settings: AppSettings,
    val today: LocalDate,
    val zone: ZoneId,
) {
    val base: String get() = settings.baseCurrency
    val converter = CurrencyConverter(data.rates)

    val accountsById: Map<Long, AccountEntity> = data.accounts.associateBy { it.id }
    val categoriesById: Map<Long, CategoryEntity> = data.categories.associateBy { it.id }
    val activeAccounts: List<AccountEntity> by lazy {
        data.accounts.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
    }

    val balances: Map<Long, Long> by lazy { BalanceCalculator.balances(data.accounts, data.transactions.map { it.tx }) }

    fun balance(accountId: Long): Long = balances[accountId] ?: 0L

    fun currencyOf(accountId: Long): String = accountsById[accountId]?.currency ?: base

    /** Converts to base; missing rates contribute 0 and are reported by [missingRates]. */
    fun toBase(minor: Long, currency: String): Long = converter.convert(minor, currency, base) ?: 0L

    val missingRates: Set<String> by lazy {
        val currencies = data.accounts.filter { !it.archived }.map { it.currency } +
            data.assets.filter { !it.archived }.map { it.currency } +
            data.goals.map { it.currency } + data.budgets.map { it.currency } + data.bills.map { it.currency }
        currencies.filter { it != base && !converter.hasRate(it) }.toSortedSet()
    }

    fun dateOf(tx: TransactionWithLines): LocalDate = Periods.localDate(tx.tx.occurredAt, zone)

    /** Total of accounts included in totals (base currency). */
    val totalBalance: Long by lazy {
        data.accounts.filter { it.includeInTotals && !it.archived }.sumOf { toBase(balance(it.id), it.currency) }
    }

    val netWorth: NetWorthBreakdown by lazy {
        NetWorthCalculator.compute(data.accounts, balances, data.assets, data.valuations, converter, base)
    }

    /* ---------------- Flows & analytics ---------------- */

    fun transactionsIn(range: DateRange, accountId: Long? = null): List<TransactionWithLines> =
        data.transactions.filter { t ->
            dateOf(t) in range && (accountId == null || t.tx.accountId == accountId || t.tx.toAccountId == accountId)
        }

    /** Income and expenses only: transfers and adjustments are never counted. */
    fun totals(range: DateRange, accountId: Long? = null): FlowTotals {
        var income = 0L
        var expense = 0L
        transactionsIn(range, accountId).forEach { t ->
            when (t.tx.type) {
                TxType.INCOME -> if (accountId == null || t.tx.accountId == accountId) income += toBase(t.tx.amountMinor, currencyOf(t.tx.accountId))
                TxType.EXPENSE -> if (accountId == null || t.tx.accountId == accountId) expense += toBase(t.tx.amountMinor, currencyOf(t.tx.accountId))
                else -> Unit
            }
        }
        return FlowTotals(income, expense)
    }

    /** Category totals for [kind], using split lines, largest first. */
    fun byCategory(range: DateRange, kind: CategoryKind, accountId: Long? = null): List<CategoryAmount> {
        val wanted = if (kind == CategoryKind.EXPENSE) TxType.EXPENSE else TxType.INCOME
        val sums = LinkedHashMap<Long?, Long>()
        transactionsIn(range, accountId).filter { it.tx.type == wanted && (accountId == null || it.tx.accountId == accountId) }
            .forEach { t ->
                val cur = currencyOf(t.tx.accountId)
                if (t.lines.isEmpty()) {
                    sums[null] = (sums[null] ?: 0L) + toBase(t.tx.amountMinor, cur)
                } else t.lines.forEach { line ->
                    sums[line.categoryId] = (sums[line.categoryId] ?: 0L) + toBase(line.amountMinor, cur)
                }
            }
        return sums.map { CategoryAmount(it.key, it.value) }.filter { it.amount != 0L }.sortedByDescending { it.amount }
    }

    fun categorySpent(range: DateRange, categoryId: Long): Long =
        byCategory(range, CategoryKind.EXPENSE).firstOrNull { it.categoryId == categoryId }?.amount ?: 0L

    /** Income/expense split into buckets: days of a week, days of a month, or months of a year. */
    fun series(kind: PeriodKind, range: DateRange, accountId: Long? = null): List<SeriesPoint> {
        val buckets: List<Pair<String, DateRange>> = when (kind) {
            PeriodKind.WEEK, PeriodKind.MONTH -> {
                val out = ArrayList<Pair<String, DateRange>>()
                var d = range.start
                while (!d.isAfter(range.endInclusive)) {
                    val label = if (kind == PeriodKind.WEEK) d.dayOfWeek.name.take(1) else d.dayOfMonth.toString()
                    out += label to DateRange(d, d)
                    d = d.plusDays(1)
                }
                out
            }
            PeriodKind.YEAR -> (1..12).map { m ->
                val ym = YearMonth.of(range.start.year, m)
                ym.month.name.take(1) to Periods.month(ym)
            }
        }
        val txs = transactionsIn(range, accountId)
        return buckets.map { (label, r) ->
            var inc = 0L
            var exp = 0L
            txs.forEach { t ->
                if (dateOf(t) in r && (accountId == null || t.tx.accountId == accountId)) {
                    when (t.tx.type) {
                        TxType.INCOME -> inc += toBase(t.tx.amountMinor, currencyOf(t.tx.accountId))
                        TxType.EXPENSE -> exp += toBase(t.tx.amountMinor, currencyOf(t.tx.accountId))
                        else -> Unit
                    }
                }
            }
            SeriesPoint(label, r, inc, exp)
        }
    }

    /** Totals for the [count] months ending with the month of [endMonth], oldest first. */
    fun monthlyHistory(endMonth: YearMonth, count: Int, accountId: Long? = null): List<SeriesPoint> =
        (count - 1 downTo 0).map { back ->
            val ym = endMonth.minusMonths(back.toLong())
            val r = Periods.month(ym)
            val totals = totals(r, accountId)
            SeriesPoint(ym.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, r, totals.income, totals.expense)
        }

    /* ---------------- Planning ---------------- */

    val budgetPeriod: DateRange by lazy { Periods.budgetMonth(today, settings.periodStartDay) }

    fun budgetRange(budget: BudgetEntity, reference: LocalDate = today): DateRange = when (budget.period) {
        BudgetPeriod.MONTHLY -> Periods.budgetMonth(reference, settings.periodStartDay)
        BudgetPeriod.YEARLY -> Periods.year(reference.year)
    }

    fun budgetStatus(budget: BudgetEntity, reference: LocalDate = today): BudgetStatus {
        val range = budgetRange(budget, reference)
        val spent = if (budget.categoryId == null) totals(range).expense else categorySpent(range, budget.categoryId)
        return BudgetCalculator.status(budget, toBase(budget.limitMinor, budget.currency), spent, range, today)
    }

    fun budgetStatuses(reference: LocalDate = today): List<BudgetStatus> = data.budgets.map { budgetStatus(it, reference) }

    val overallMonthlyBudget: BudgetEntity? get() =
        data.budgets.firstOrNull { it.categoryId == null && it.period == BudgetPeriod.MONTHLY }

    fun billDues(until: LocalDate): List<BillDue> = BillSchedule.dues(data.bills, data.billPayments, today, until)

    val upcomingBills: List<BillDue> by lazy { billDues(today.plusDays(30)) }

    fun goalProgress(): List<GoalProgress> =
        data.goals.filter { !it.archived }.map { GoalCalculator.progress(it, data.goalEntries, today) }

    val goalReserve: Long by lazy {
        goalProgress().sumOf { toBase(it.saved.coerceAtLeast(0), it.goal.currency) }
    }

    val spendableBalance: Long by lazy {
        data.accounts.filter { !it.archived && it.includeInTotals && it.includeInSafeToSpend }
            .sumOf { toBase(balance(it.id), it.currency) }
    }

    val safeToSpend: SafeToSpendResult by lazy {
        val period = budgetPeriod
        val periodTx = transactionsIn(period).filter { it.tx.type == TxType.EXPENSE }
        var spentAll = 0L
        var spentDiscretionary = 0L
        var spentToday = 0L
        periodTx.forEach { t ->
            val amt = toBase(t.tx.amountMinor, currencyOf(t.tx.accountId))
            spentAll += amt
            if (t.tx.billId == null) {
                spentDiscretionary += amt
                if (dateOf(t) == today) spentToday += amt
            }
        }
        val bills = billDues(period.endInclusive).sumOf { toBase(it.amountMinor, it.bill.currency) }
        SafeToSpendCalculator.calculate(
            SafeToSpendInput(
                spendableBalance = spendableBalance,
                upcomingBills = bills,
                goalReserve = if (settings.protectGoals) goalReserve else 0L,
                buffer = settings.bufferMinor,
                budgetLimit = overallMonthlyBudget?.let { toBase(it.limitMinor, it.currency) },
                spentInPeriod = spentAll,
                discretionarySpentInPeriod = spentDiscretionary,
                discretionarySpentToday = spentToday,
                today = today,
                period = period,
            )
        )
    }

    /* ---------------- Display helpers ---------------- */

    fun categoryName(id: Long?): String = id?.let { categoriesById[it]?.name } ?: "Uncategorized"

    fun titleOf(t: TransactionWithLines): String {
        val tx = t.tx
        return when (tx.type) {
            TxType.TRANSFER -> "Transfer"
            TxType.ADJUSTMENT -> "Balance adjustment"
            else -> when {
                tx.billId != null -> data.bills.firstOrNull { it.id == tx.billId }?.name ?: categoryName(t.lines.firstOrNull()?.categoryId)
                t.lines.size > 1 -> "Split · ${t.lines.size} categories"
                else -> categoryName(t.lines.firstOrNull()?.categoryId)
            }
        }
    }

    fun subtitleOf(t: TransactionWithLines): String {
        val tx = t.tx
        val from = accountsById[tx.accountId]?.name ?: "Deleted account"
        val base = if (tx.type == TxType.TRANSFER) {
            "$from → ${tx.toAccountId?.let { accountsById[it]?.name } ?: "Deleted account"}"
        } else from
        return if (tx.note.isNotBlank()) "$base · ${tx.note}" else base
    }
}
