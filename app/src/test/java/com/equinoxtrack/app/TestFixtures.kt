package com.equinoxtrack.app

import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.CategoryEntity
import com.equinoxtrack.app.data.db.TransactionEntity
import com.equinoxtrack.app.data.db.TransactionLineEntity
import com.equinoxtrack.app.data.db.TransactionWithLines
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.BillFrequency
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.Periods
import com.equinoxtrack.app.domain.TxType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

object Fx {
    val zone: ZoneId = ZoneOffset.UTC
    val today: LocalDate = LocalDate.of(2026, 3, 15)

    fun account(id: Long, opening: Long = 0, currency: String = "PHP", type: AccountType = AccountType.CASH, spendable: Boolean = true, inTotals: Boolean = true) =
        AccountEntity(id = id, name = "Acc$id", type = type, currency = currency, openingBalanceMinor = opening, color = 0, icon = "wallet", includeInTotals = inTotals, includeInSafeToSpend = spendable)

    fun category(id: Long, kind: CategoryKind = CategoryKind.EXPENSE) =
        CategoryEntity(id = id, name = "Cat$id", kind = kind, color = 0, icon = "tag")

    private var nextId = 1000L

    fun at(date: LocalDate, hour: Int = 12): Long = Periods.startMillis(date, zone) + hour * 3_600_000L

    fun tx(
        type: TxType,
        account: Long,
        amount: Long,
        date: LocalDate = today,
        category: Long? = 1,
        to: Long? = null,
        toAmount: Long? = null,
        billId: Long? = null,
        id: Long = nextId++,
    ): TransactionWithLines {
        val t = TransactionEntity(id = id, type = type, accountId = account, toAccountId = to, amountMinor = amount, toAmountMinor = toAmount, occurredAt = at(date), billId = billId)
        val lines = if (type == TxType.INCOME || type == TxType.EXPENSE) listOf(TransactionLineEntity(id = id * 10, transactionId = id, categoryId = category, amountMinor = amount)) else emptyList()
        return TransactionWithLines(t, lines)
    }

    fun bill(id: Long, amount: Long, anchor: LocalDate, frequency: BillFrequency = BillFrequency.MONTHLY, account: Long = 1) =
        BillEntity(id = id, name = "Bill$id", amountMinor = amount, currency = "PHP", accountId = account, categoryId = 1, frequency = frequency, anchorEpochDay = anchor.toEpochDay(), color = 0, icon = "bolt")
}
