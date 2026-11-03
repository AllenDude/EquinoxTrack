package com.equinoxtrack.app

import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.data.db.AssetValuationEntity
import com.equinoxtrack.app.data.db.BillPaymentEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.data.db.GoalEntryEntity
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.AppSettings
import com.equinoxtrack.app.domain.AssetKind
import com.equinoxtrack.app.domain.BalanceCalculator
import com.equinoxtrack.app.domain.BillFrequency
import com.equinoxtrack.app.domain.BillSchedule
import com.equinoxtrack.app.domain.BudgetPeriod
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.DateRange
import com.equinoxtrack.app.domain.FinanceData
import com.equinoxtrack.app.domain.GoalCalculator
import com.equinoxtrack.app.domain.Ledger
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.SafeToSpendCalculator
import com.equinoxtrack.app.domain.SafeToSpendInput
import com.equinoxtrack.app.domain.SafeToSpendLimit
import com.equinoxtrack.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BalanceCalculatorTest {
    private val accounts = listOf(Fx.account(1, opening = 100_00), Fx.account(2, opening = 0))

    @Test fun incomeExpenseTransferAdjustment() {
        val txs = listOf(
            Fx.tx(TxType.INCOME, 1, 500_00),
            Fx.tx(TxType.EXPENSE, 1, 120_00),
            Fx.tx(TxType.TRANSFER, 1, 200_00, to = 2),
            Fx.tx(TxType.ADJUSTMENT, 2, -5_00),
        ).map { it.tx }
        val b = BalanceCalculator.balances(accounts, txs)
        assertEquals(280_00L, b[1])
        assertEquals(195_00L, b[2])
    }

    @Test fun editingATransactionRecomputesBalances() {
        val original = Fx.tx(TxType.EXPENSE, 1, 50_00, id = 1).tx
        val edited = original.copy(amountMinor = 80_00, accountId = 2)
        assertEquals(50_00L, BalanceCalculator.balances(accounts, listOf(original))[1])
        val after = BalanceCalculator.balances(accounts, listOf(edited))
        assertEquals(100_00L, after[1]) // old account fully restored
        assertEquals(-80_00L, after[2]) // new account debited
    }

    @Test fun deletingATransactionReversesIt() {
        val t = Fx.tx(TxType.TRANSFER, 1, 40_00, to = 2).tx
        assertEquals(60_00L, BalanceCalculator.balances(accounts, listOf(t))[1])
        val after = BalanceCalculator.balances(accounts, emptyList())
        assertEquals(100_00L, after[1])
        assertEquals(0L, after[2])
    }

    @Test fun crossCurrencyTransferUsesDestinationAmount() {
        val accts = listOf(Fx.account(1, opening = 1000_00, currency = "USD"), Fx.account(2, currency = "PHP"))
        val t = Fx.tx(TxType.TRANSFER, 1, 100_00, to = 2, toAmount = 5_800_00).tx
        val b = BalanceCalculator.balances(accts, listOf(t))
        assertEquals(900_00L, b[1])
        assertEquals(5_800_00L, b[2])
    }

    @Test fun adjustmentForTargetBalance() {
        assertEquals(-1_50L, BalanceCalculator.adjustmentFor(10_00, 8_50))
    }

    @Test fun dailyBalanceSeries() {
        val d = Fx.today
        val txs = listOf(Fx.tx(TxType.INCOME, 1, 10_00, date = d.minusDays(5)), Fx.tx(TxType.EXPENSE, 1, 3_00, date = d)).map { it.tx }
        val series = BalanceCalculator.dailyBalances(accounts[0], txs, DateRange(d.minusDays(1), d), Fx.zone)
        assertEquals(110_00L, series[0].second)
        assertEquals(107_00L, series[1].second)
    }
}

class LedgerTest {
    private fun ledger(data: FinanceData, settings: AppSettings = AppSettings()) = Ledger(data, settings, Fx.today, Fx.zone)
    private val month = Periods.month(YearMonth.from(Fx.today))

    @Test fun transfersAndAdjustmentsAreNotIncomeOrExpense() {
        val l = ledger(
            FinanceData(
                accounts = listOf(Fx.account(1, 1000_00), Fx.account(2)),
                categories = listOf(Fx.category(1), Fx.category(2, CategoryKind.INCOME)),
                transactions = listOf(
                    Fx.tx(TxType.INCOME, 1, 300_00, category = 2),
                    Fx.tx(TxType.EXPENSE, 1, 50_00),
                    Fx.tx(TxType.TRANSFER, 1, 400_00, to = 2),
                    Fx.tx(TxType.ADJUSTMENT, 2, 25_00),
                ),
            )
        )
        val t = l.totals(month)
        assertEquals(300_00L, t.income)
        assertEquals(50_00L, t.expense)
        assertEquals(1275_00L, l.totalBalance)
    }

    @Test fun savingsContributionsAreNotIncomeAndNotDoubleCountedInNetWorth() {
        val goal = GoalEntity(id = 1, name = "Trip", targetMinor = 10_000_00, currency = "PHP", color = 0, icon = "flight")
        val l = ledger(
            FinanceData(
                accounts = listOf(Fx.account(1, 5_000_00)),
                goals = listOf(goal),
                goalEntries = listOf(
                    GoalEntryEntity(id = 1, goalId = 1, amountMinor = 2_000_00, epochDay = Fx.today.toEpochDay()),
                    GoalEntryEntity(id = 2, goalId = 1, amountMinor = -500_00, epochDay = Fx.today.toEpochDay()),
                ),
            )
        )
        assertEquals(0L, l.totals(month).income)
        assertEquals(5_000_00L, l.netWorth.total)
        val p = l.goalProgress().single()
        assertEquals(1_500_00L, p.saved)
        assertEquals(8_500_00L, p.remaining)
        assertEquals(15, p.percent)
    }

    @Test fun netWorthAddsAssetsOnceUsingLatestValuation() {
        val asset = AssetEntity(id = 1, name = "Car", kind = AssetKind.VEHICLE, currency = "PHP", color = 0)
        val l = ledger(
            FinanceData(
                accounts = listOf(Fx.account(1, 1_000_00), Fx.account(2, 2_000_00, type = AccountType.BANK), Fx.account(3, 9_999_00, inTotals = false)),
                assets = listOf(asset),
                valuations = listOf(
                    AssetValuationEntity(id = 1, assetId = 1, valueMinor = 500_000_00, epochDay = Fx.today.minusDays(100).toEpochDay()),
                    AssetValuationEntity(id = 2, assetId = 1, valueMinor = 450_000_00, epochDay = Fx.today.toEpochDay()),
                ),
            )
        )
        val nw = l.netWorth
        assertEquals(3_000_00L, nw.accountsTotal)
        assertEquals(450_000_00L, nw.assetsTotal)
        assertEquals(453_000_00L, nw.total)
        assertEquals(2_000_00L, nw.byType[AccountType.BANK])
    }

    @Test fun multiCurrencyTotalsAndMissingRates() {
        val l = ledger(
            FinanceData(
                accounts = listOf(Fx.account(1, 100_00, currency = "USD"), Fx.account(2, 1_000_00), Fx.account(3, 10_00, currency = "EUR")),
                rates = listOf(ExchangeRateEntity("USD", "1"), ExchangeRateEntity("PHP", "58")),
            )
        )
        assertEquals(6_800_00L, l.totalBalance)
        assertEquals(setOf("EUR"), l.missingRates)
    }

    @Test fun splitLinesDriveCategoryTotals() {
        val split = Fx.tx(TxType.EXPENSE, 1, 150_00).let { t ->
            t.copy(lines = listOf(t.lines[0].copy(categoryId = 1, amountMinor = 100_00), t.lines[0].copy(id = 99, categoryId = 2, amountMinor = 50_00)))
        }
        val l = ledger(FinanceData(accounts = listOf(Fx.account(1)), categories = listOf(Fx.category(1), Fx.category(2)), transactions = listOf(split)))
        val by = l.byCategory(month, CategoryKind.EXPENSE)
        assertEquals(100_00L, by.first { it.categoryId == 1L }.amount)
        assertEquals(50_00L, by.first { it.categoryId == 2L }.amount)
        assertEquals(150_00L, l.totals(month).expense)
        assertEquals("Split · 2 categories", l.titleOf(split))
    }

    @Test fun budgetStatusTracksCategorySpending() {
        val budget = BudgetEntity(id = 1, categoryId = 1, limitMinor = 200_00, currency = "PHP", period = BudgetPeriod.MONTHLY)
        val l = ledger(
            FinanceData(
                accounts = listOf(Fx.account(1, 1_000_00)),
                categories = listOf(Fx.category(1), Fx.category(2)),
                budgets = listOf(budget),
                transactions = listOf(
                    Fx.tx(TxType.EXPENSE, 1, 150_00, category = 1),
                    Fx.tx(TxType.EXPENSE, 1, 80_00, category = 1),
                    Fx.tx(TxType.EXPENSE, 1, 999_00, category = 2),
                    Fx.tx(TxType.EXPENSE, 1, 50_00, category = 1, date = Fx.today.minusMonths(1)),
                ),
            )
        )
        val s = l.budgetStatus(budget)
        assertEquals(230_00L, s.spent)
        assertEquals(-30_00L, s.remaining)
        assertTrue(s.over)
    }

    @Test fun billPaymentIsCountedOnceInSafeToSpend() {
        val bill = Fx.bill(1, 1_000_00, anchor = Fx.today.plusDays(5))
        val base = FinanceData(accounts = listOf(Fx.account(1, 10_000_00)), categories = listOf(Fx.category(1)), bills = listOf(bill))
        val before = ledger(base).safeToSpend
        assertEquals(1_000_00L, before.input.upcomingBills)
        assertEquals(9_000_00L, before.availableAfterReserves)

        // Pay it: expense transaction + payment record. Balance drops, reservation disappears.
        val payment = Fx.tx(TxType.EXPENSE, 1, 1_000_00, billId = 1, id = 77)
        val paid = base.copy(
            transactions = listOf(payment),
            billPayments = listOf(BillPaymentEntity(id = 1, billId = 1, dueEpochDay = bill.anchorEpochDay, paidAt = 0, amountMinor = 1_000_00, transactionId = 77)),
        )
        val after = ledger(paid).safeToSpend
        assertEquals(0L, after.input.upcomingBills)
        assertEquals(9_000_00L, after.availableAfterReserves) // identical: not double counted
        assertEquals(0L, after.input.discretionarySpentToday) // bill payments are not discretionary
        assertEquals(1_000_00L, ledger(paid).totals(month).expense) // but still an expense, once
    }

    @Test fun goalProtectionReservesSavedMoney() {
        val goal = GoalEntity(id = 1, name = "Fund", targetMinor = 5_000_00, currency = "PHP", color = 0, icon = "x")
        val data = FinanceData(
            accounts = listOf(Fx.account(1, 3_000_00)),
            goals = listOf(goal),
            goalEntries = listOf(GoalEntryEntity(id = 1, goalId = 1, amountMinor = 1_000_00, epochDay = 0)),
        )
        assertEquals(2_000_00L, ledger(data).safeToSpend.availableAfterReserves)
        assertEquals(3_000_00L, ledger(data, AppSettings(protectGoals = false)).safeToSpend.availableAfterReserves)
    }

    @Test fun nonSpendableAccountsAreExcludedFromSafeToSpend() {
        val data = FinanceData(accounts = listOf(Fx.account(1, 1_000_00), Fx.account(2, 50_000_00, spendable = false)))
        assertEquals(1_000_00L, ledger(data).spendableBalance)
        assertEquals(51_000_00L, ledger(data).totalBalance)
    }
}

class BillScheduleTest {
    @Test fun monthlyFromMonthEndDoesNotDrift() {
        val bill = Fx.bill(1, 100, anchor = LocalDate.of(2026, 1, 31))
        val dates = BillSchedule.occurrencesBetween(bill, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)
    }

    @Test fun overdueAndSkippedOccurrences() {
        val bill = Fx.bill(1, 100, anchor = LocalDate.of(2026, 1, 10))
        val payments = listOf(
            BillPaymentEntity(id = 1, billId = 1, dueEpochDay = LocalDate.of(2026, 1, 10).toEpochDay(), paidAt = 0, amountMinor = 100, transactionId = 5),
            BillPaymentEntity(id = 2, billId = 1, dueEpochDay = LocalDate.of(2026, 2, 10).toEpochDay(), paidAt = 0, amountMinor = 0, skipped = true),
        )
        val dues = BillSchedule.dues(listOf(bill), payments, Fx.today, Fx.today.plusDays(30))
        assertEquals(listOf(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10)), dues.map { it.dueDate })
        assertTrue(dues[0].overdue)
        assertFalse(dues[1].overdue)
        assertEquals(LocalDate.of(2026, 3, 10), BillSchedule.nextUnpaid(bill, payments, Fx.today))
    }

    @Test fun deletingPaymentMakesOccurrenceUnpaidAgain() {
        val bill = Fx.bill(1, 100, anchor = Fx.today.plusDays(1))
        val paid = listOf(BillPaymentEntity(id = 1, billId = 1, dueEpochDay = bill.anchorEpochDay, paidAt = 0, amountMinor = 100, transactionId = 9))
        assertEquals(Fx.today.plusMonths(1).plusDays(1), BillSchedule.nextUnpaid(bill, paid, Fx.today))
        assertEquals(Fx.today.plusDays(1), BillSchedule.nextUnpaid(bill, emptyList(), Fx.today))
    }

    @Test fun oneTimeAndEndDate() {
        val once = Fx.bill(1, 100, anchor = Fx.today, frequency = BillFrequency.ONCE)
        assertEquals(1, BillSchedule.occurrencesBetween(once, Fx.today.minusYears(1), Fx.today.plusYears(1)).size)
        val ended = Fx.bill(2, 100, anchor = Fx.today, frequency = BillFrequency.WEEKLY).copy(endEpochDay = Fx.today.plusWeeks(2).toEpochDay())
        assertEquals(3, BillSchedule.occurrencesBetween(ended, Fx.today, Fx.today.plusYears(1)).size)
        val paidOnce = listOf(BillPaymentEntity(billId = 1, dueEpochDay = once.anchorEpochDay, paidAt = 0, amountMinor = 100))
        assertNull(BillSchedule.nextUnpaid(once, paidOnce, Fx.today))
    }

    @Test fun subscriptionEquivalents() {
        val yearly = Fx.bill(1, 1_200_00, Fx.today, BillFrequency.YEARLY)
        assertEquals(100_00L, BillSchedule.monthlyEquivalent(yearly))
        val weekly = Fx.bill(2, 100_00, Fx.today, BillFrequency.WEEKLY)
        assertEquals(5_200_00L, BillSchedule.yearlyEquivalent(weekly))
    }
}

class SafeToSpendTest {
    private val period = DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))

    private fun input(
        balance: Long = 10_000_00, bills: Long = 0, goals: Long = 0, buffer: Long = 0, limit: Long? = null,
        spent: Long = 0, discretionary: Long = 0, today: Long = 0, date: LocalDate = LocalDate.of(2026, 3, 22),
    ) = SafeToSpendInput(balance, bills, goals, buffer, limit, spent, discretionary, today, date, period)

    @Test fun dividesRemainingByDaysLeft() {
        val r = SafeToSpendCalculator.calculate(input()) // 10 days left incl. today
        assertEquals(10L, r.daysLeft)
        assertEquals(1_000_00L, r.daily)
        assertEquals(7_000_00L, r.weekly)
        assertEquals(SafeToSpendLimit.BALANCE, r.limitedBy)
    }

    @Test fun reservesBillsGoalsAndBuffer() {
        val r = SafeToSpendCalculator.calculate(input(bills = 2_000_00, goals = 1_000_00, buffer = 1_000_00))
        assertEquals(6_000_00L, r.remaining)
        assertEquals(600_00L, r.daily)
    }

    @Test fun budgetCapsAllowance() {
        val r = SafeToSpendCalculator.calculate(input(limit = 8_000_00, spent = 5_000_00, bills = 1_000_00))
        assertEquals(2_000_00L, r.budgetRemaining)
        assertEquals(SafeToSpendLimit.BUDGET, r.limitedBy)
        assertEquals(200_00L, r.daily)
    }

    @Test fun dailyAllowanceIsStableDuringTheDay() {
        val morning = SafeToSpendCalculator.calculate(input())
        // Spending ₱300 today lowers balance by ₱300 but the day's allowance stays the same
        val evening = SafeToSpendCalculator.calculate(input(balance = 9_700_00, discretionary = 300_00, today = 300_00))
        assertEquals(morning.daily, evening.daily)
        assertEquals(700_00L, evening.leftToday)
    }

    @Test fun neverNegativeAllowance() {
        val r = SafeToSpendCalculator.calculate(input(balance = 500_00, bills = 2_000_00))
        assertEquals(0L, r.daily)
        assertEquals(-1_500_00L, r.remaining)
    }

    @Test fun budgetMonthWithCustomStartDay() {
        val r = Periods.budgetMonth(LocalDate.of(2026, 3, 10), 25)
        assertEquals(LocalDate.of(2026, 2, 25), r.start)
        assertEquals(LocalDate.of(2026, 3, 24), r.endInclusive)
    }

    @Test fun goalMonthlyNeeded() {
        val goal = GoalEntity(id = 1, name = "x", targetMinor = 12_000_00, currency = "PHP", targetEpochDay = LocalDate.of(2026, 12, 31).toEpochDay(), color = 0, icon = "x")
        val p = GoalCalculator.progress(goal, emptyList(), LocalDate.of(2026, 1, 15))
        assertEquals(1_000_00L, p.monthlyNeeded)
    }
}
