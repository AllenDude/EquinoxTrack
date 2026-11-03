package com.equinoxtrack.app.data.repo

import androidx.room.withTransaction
import com.equinoxtrack.app.data.db.AccountEntity
import com.equinoxtrack.app.data.db.AssetEntity
import com.equinoxtrack.app.data.db.AssetValuationEntity
import com.equinoxtrack.app.data.db.BillEntity
import com.equinoxtrack.app.data.db.BillPaymentEntity
import com.equinoxtrack.app.data.db.BudgetEntity
import com.equinoxtrack.app.data.db.CategoryEntity
import com.equinoxtrack.app.data.db.EquinoxDatabase
import com.equinoxtrack.app.data.db.ExchangeRateEntity
import com.equinoxtrack.app.data.db.GoalEntity
import com.equinoxtrack.app.data.db.GoalEntryEntity
import com.equinoxtrack.app.data.db.TransactionEntity
import com.equinoxtrack.app.data.db.TransactionLineEntity
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.BalanceCalculator
import com.equinoxtrack.app.domain.CurrencyConverter
import com.equinoxtrack.app.domain.Defaults
import com.equinoxtrack.app.domain.FinanceData
import com.equinoxtrack.app.domain.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate

/** One category allocation of a transaction being saved. */
data class LineDraft(val categoryId: Long?, val amountMinor: Long, val note: String = "")

/** Everything needed to create or update a transaction atomically. */
data class TransactionDraft(
    val id: Long = 0,
    val type: TxType,
    val accountId: Long,
    val toAccountId: Long? = null,
    val amountMinor: Long,
    val toAmountMinor: Long? = null,
    val note: String = "",
    val occurredAt: Long,
    val lines: List<LineDraft> = emptyList(),
    val billId: Long? = null,
)

class ValidationException(message: String) : IllegalArgumentException(message)

/**
 * Single entry point for all writes. Every operation touching more than one row runs inside a
 * Room database transaction so the ledger can never be left half-updated.
 */
class FinanceRepository(private val db: EquinoxDatabase) {
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val bills = db.billDao()
    private val plans = db.planDao()
    private val assets = db.assetDao()

    val data: Flow<FinanceData> = combine(
        combine(accounts.observeAll(), categories.observeAll(), transactions.observeAll()) { a, c, t -> Triple(a, c, t) },
        combine(bills.observeAll(), bills.observePayments(), plans.observeBudgets()) { b, p, bu -> Triple(b, p, bu) },
        combine(plans.observeGoals(), plans.observeGoalEntries()) { g, ge -> g to ge },
        combine(assets.observeAssets(), assets.observeValuations(), assets.observeRates()) { a, v, r -> Triple(a, v, r) },
    ) { first, second, goals, assetData ->
        FinanceData(
            accounts = first.first,
            categories = first.second,
            transactions = first.third,
            bills = second.first,
            billPayments = second.second,
            budgets = second.third,
            goals = goals.first,
            goalEntries = goals.second,
            assets = assetData.first,
            valuations = assetData.second,
            rates = assetData.third,
        )
    }

    /** One-shot read used by widgets and workers (no observers needed). */
    suspend fun loadData(): FinanceData = db.withTransaction {
        FinanceData(
            accounts = accounts.getAll(),
            categories = categories.getAll(),
            transactions = transactions.getAll(),
            bills = bills.getAll(),
            billPayments = bills.getAllPayments(),
            budgets = plans.getBudgets(),
            goals = plans.getGoals(),
            goalEntries = plans.getGoalEntries(),
            assets = assets.getAssets(),
            valuations = assets.getValuations(),
            rates = assets.getRates(),
        )
    }

    suspend fun allLines(): List<TransactionLineEntity> = transactions.getAllLines()

    /* ---------------- Setup ---------------- */

    suspend fun seedCategoriesIfEmpty() = db.withTransaction {
        if (categories.count() == 0) {
            categories.insertAll(Defaults.categories.mapIndexed { i, c ->
                CategoryEntity(name = c.name, kind = c.kind, color = c.color, icon = c.icon, sortOrder = i)
            })
        }
    }

    /* ---------------- Transactions ---------------- */

    private fun validate(d: TransactionDraft) {
        if (d.type == TxType.ADJUSTMENT) {
            if (d.amountMinor == 0L) throw ValidationException("Adjustment amount cannot be zero")
            return
        }
        if (d.amountMinor <= 0) throw ValidationException("Enter an amount greater than zero")
        when (d.type) {
            TxType.TRANSFER -> {
                val to = d.toAccountId ?: throw ValidationException("Choose the destination account")
                if (to == d.accountId) throw ValidationException("Choose two different accounts")
                if ((d.toAmountMinor ?: d.amountMinor) <= 0) throw ValidationException("Destination amount must be positive")
            }
            else -> {
                if (d.lines.isEmpty()) throw ValidationException("Choose a category")
                if (d.lines.any { it.amountMinor <= 0 }) throw ValidationException("Every split needs an amount")
                if (d.lines.sumOf { it.amountMinor } != d.amountMinor) throw ValidationException("Splits must add up to the total")
            }
        }
    }

    /** Creates or replaces a transaction and its category lines in one database transaction. */
    suspend fun saveTransaction(d: TransactionDraft): Long {
        validate(d)
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val isTransfer = d.type == TxType.TRANSFER
            val entity = TransactionEntity(
                id = d.id,
                type = d.type,
                accountId = d.accountId,
                toAccountId = if (isTransfer) d.toAccountId else null,
                amountMinor = d.amountMinor,
                toAmountMinor = if (isTransfer) (d.toAmountMinor ?: d.amountMinor) else null,
                note = d.note.trim(),
                occurredAt = d.occurredAt,
                billId = if (d.type == TxType.EXPENSE) d.billId else null,
                createdAt = now,
            )
            val id = if (d.id == 0L) {
                transactions.insert(entity)
            } else {
                val existing = transactions.get(d.id) ?: throw ValidationException("Transaction no longer exists")
                transactions.update(entity.copy(createdAt = existing.createdAt))
                transactions.deleteLines(d.id)
                d.id
            }
            if (d.type == TxType.INCOME || d.type == TxType.EXPENSE) {
                transactions.insertLines(d.lines.map { TransactionLineEntity(transactionId = id, categoryId = it.categoryId, amountMinor = it.amountMinor, note = it.note) })
            }
            id
        }
    }

    /** Deleting cascades to its lines and to any bill payment it settled (the bill becomes unpaid). */
    suspend fun deleteTransaction(id: Long) = transactions.delete(id)

    /** Records a balance correction so the account shows [actualBalance]. Not income or expense. */
    suspend fun adjustBalance(accountId: Long, actualBalance: Long, note: String, at: Long): Long? = db.withTransaction {
        val account = accounts.get(accountId) ?: return@withTransaction null
        val all = transactions.getAll().map { it.tx }
        val current = BalanceCalculator.balances(listOf(account), all)[accountId] ?: account.openingBalanceMinor
        val diff = BalanceCalculator.adjustmentFor(current, actualBalance)
        if (diff == 0L) return@withTransaction null
        transactions.insert(
            TransactionEntity(type = TxType.ADJUSTMENT, accountId = accountId, amountMinor = diff, note = note.ifBlank { "Balance adjustment" }, occurredAt = at, createdAt = System.currentTimeMillis())
        )
    }

    /* ---------------- Accounts ---------------- */

    suspend fun saveAccount(a: AccountEntity): Long {
        if (a.name.isBlank()) throw ValidationException("Give the account a name")
        return if (a.id == 0L) accounts.insert(a.copy(name = a.name.trim(), createdAt = System.currentTimeMillis()))
        else { accounts.update(a.copy(name = a.name.trim())); a.id }
    }

    suspend fun transactionCount(accountId: Long) = transactions.countForAccount(accountId)

    /** Removes the account and every transaction that touched it (transfers included). */
    suspend fun deleteAccount(a: AccountEntity) = db.withTransaction { accounts.delete(a) }

    suspend fun createStarterAccount(name: String, type: AccountType, currency: String, opening: Long) {
        saveAccount(
            AccountEntity(name = name, type = type, currency = currency, openingBalanceMinor = opening, color = Defaults.accountColor(type), icon = Defaults.accountIcon(type))
        )
    }

    /* ---------------- Categories ---------------- */

    suspend fun saveCategory(c: CategoryEntity): Long {
        if (c.name.isBlank()) throw ValidationException("Give the category a name")
        return if (c.id == 0L) categories.insert(c.copy(name = c.name.trim())) else { categories.update(c.copy(name = c.name.trim())); c.id }
    }

    /** Deletes unused categories; categories with history are archived instead so reports stay intact. */
    suspend fun removeCategory(c: CategoryEntity): Boolean = db.withTransaction {
        if (categories.usageCount(c.id) == 0) { categories.delete(c); true } else { categories.update(c.copy(archived = true)); false }
    }

    /* ---------------- Budgets ---------------- */

    suspend fun saveBudget(b: BudgetEntity): Long {
        if (b.limitMinor <= 0) throw ValidationException("Set a spending limit above zero")
        return plans.upsertBudget(if (b.id == 0L) b.copy(createdAt = System.currentTimeMillis()) else b)
    }

    suspend fun deleteBudget(id: Long) = plans.deleteBudget(id)

    /* ---------------- Bills ---------------- */

    suspend fun saveBill(b: BillEntity): Long {
        if (b.name.isBlank()) throw ValidationException("Give the bill a name")
        if (b.amountMinor <= 0) throw ValidationException("Enter the bill amount")
        return if (b.id == 0L) bills.insert(b.copy(name = b.name.trim(), createdAt = System.currentTimeMillis()))
        else { bills.update(b.copy(name = b.name.trim())); b.id }
    }

    suspend fun deleteBill(id: Long) = bills.delete(id)

    /**
     * Pays one occurrence: a single expense transaction (counted once in spending) linked to a
     * payment record that marks the occurrence settled.
     */
    suspend fun payBill(bill: BillEntity, due: LocalDate, accountId: Long, amountMinor: Long, paidAt: Long, note: String = ""): Long {
        if (amountMinor <= 0) throw ValidationException("Enter the amount paid")
        return db.withTransaction {
            val txId = transactions.insert(
                TransactionEntity(type = TxType.EXPENSE, accountId = accountId, amountMinor = amountMinor, note = note.ifBlank { bill.name }, occurredAt = paidAt, billId = bill.id, createdAt = System.currentTimeMillis())
            )
            transactions.insertLines(listOf(TransactionLineEntity(transactionId = txId, categoryId = bill.categoryId, amountMinor = amountMinor)))
            bills.insertPayment(BillPaymentEntity(billId = bill.id, dueEpochDay = due.toEpochDay(), paidAt = paidAt, amountMinor = amountMinor, transactionId = txId))
            txId
        }
    }

    suspend fun skipOccurrence(bill: BillEntity, due: LocalDate) {
        bills.insertPayment(BillPaymentEntity(billId = bill.id, dueEpochDay = due.toEpochDay(), paidAt = System.currentTimeMillis(), amountMinor = 0, skipped = true))
    }

    /** Undo a payment: removes its expense too, so the money returns to the account. */
    suspend fun undoPayment(paymentId: Long) = db.withTransaction {
        val p = bills.getPayment(paymentId) ?: return@withTransaction
        val tx = p.transactionId
        if (tx != null) transactions.delete(tx) else bills.deletePayment(paymentId)
    }

    /* ---------------- Goals ---------------- */

    suspend fun saveGoal(g: GoalEntity): Long {
        if (g.name.isBlank()) throw ValidationException("Give the goal a name")
        if (g.targetMinor <= 0) throw ValidationException("Set a target amount")
        return if (g.id == 0L) plans.insertGoal(g.copy(name = g.name.trim(), createdAt = System.currentTimeMillis()))
        else { plans.updateGoal(g.copy(name = g.name.trim())); g.id }
    }

    suspend fun deleteGoal(id: Long) = plans.deleteGoal(id)

    suspend fun addGoalEntry(goalId: Long, amountMinor: Long, date: LocalDate, note: String): Long {
        if (amountMinor == 0L) throw ValidationException("Enter an amount")
        return plans.insertGoalEntry(GoalEntryEntity(goalId = goalId, amountMinor = amountMinor, epochDay = date.toEpochDay(), note = note.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteGoalEntry(id: Long) = plans.deleteGoalEntry(id)

    /* ---------------- Assets ---------------- */

    suspend fun saveAsset(a: AssetEntity, initialValue: Long?): Long {
        if (a.name.isBlank()) throw ValidationException("Give the asset a name")
        return db.withTransaction {
            if (a.id == 0L) {
                val id = assets.insertAsset(a.copy(name = a.name.trim(), createdAt = System.currentTimeMillis()))
                assets.insertValuation(AssetValuationEntity(assetId = id, valueMinor = initialValue ?: 0, epochDay = LocalDate.now().toEpochDay(), note = "Initial value", createdAt = System.currentTimeMillis()))
                id
            } else {
                assets.updateAsset(a.copy(name = a.name.trim()))
                a.id
            }
        }
    }

    suspend fun addValuation(assetId: Long, value: Long, date: LocalDate, note: String) {
        if (value < 0) throw ValidationException("Value cannot be negative")
        assets.insertValuation(AssetValuationEntity(assetId = assetId, valueMinor = value, epochDay = date.toEpochDay(), note = note.trim(), createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteValuation(id: Long) = assets.deleteValuation(id)
    suspend fun deleteAsset(id: Long) = assets.deleteAsset(id)

    /* ---------------- Exchange rates ---------------- */

    /** Stores "1 [currency] = [unitsOfBase] [base]". */
    suspend fun setManualRate(currency: String, unitsOfBase: BigDecimal, base: String) = db.withTransaction {
        if (unitsOfBase.signum() <= 0) throw ValidationException("Rate must be positive")
        val existing = assets.getRates().associateBy { it.currency }
        val baseValue = existing[base]?.perReference?.toBigDecimalOrNull()
        val now = System.currentTimeMillis()
        val updates = mutableListOf<ExchangeRateEntity>()
        if (baseValue == null) updates += ExchangeRateEntity(base, "1", now)
        updates += ExchangeRateEntity(currency, CurrencyConverter.perReferenceFor(unitsOfBase, baseValue).toPlainString(), now)
        assets.upsertRates(updates)
    }

    suspend fun replaceRates(perReference: Map<String, BigDecimal>) {
        val now = System.currentTimeMillis()
        assets.upsertRates(perReference.filterValues { it.signum() > 0 }.map { (k, v) -> ExchangeRateEntity(k, v.toPlainString(), now) })
    }

    suspend fun deleteRate(code: String) = assets.deleteRate(code)

    /* ---------------- Bulk operations ---------------- */

    /** Replaces the entire database contents with [payload] atomically. */
    suspend fun replaceAll(payload: FinanceData, lines: List<TransactionLineEntity>) = db.withTransaction {
        transactions.clearLines()
        bills.clearPayments()
        transactions.clear()
        bills.clear()
        plans.clearGoalEntries()
        plans.clearGoals()
        plans.clearBudgets()
        assets.clearValuations()
        assets.clearAssets()
        assets.clearRates()
        categories.clear()
        accounts.clear()

        accounts.insertAll(payload.accounts)
        categories.insertAll(payload.categories)
        bills.insertAll(payload.bills)
        transactions.insertAll(payload.transactions.map { it.tx })
        transactions.insertLines(lines)
        bills.insertPayments(payload.billPayments)
        plans.insertBudgets(payload.budgets)
        plans.insertGoals(payload.goals)
        plans.insertGoalEntries(payload.goalEntries)
        assets.insertAssets(payload.assets)
        assets.insertValuations(payload.valuations)
        assets.upsertRates(payload.rates)
    }

    /** Inserts already-resolved imported transactions (accounts/categories created as needed) atomically. */
    suspend fun importBatch(
        newAccounts: List<AccountEntity>,
        newCategories: List<CategoryEntity>,
        build: suspend (accountIds: Map<String, Long>, categoryIds: Map<String, Long>) -> List<TransactionDraft>,
    ): Int = db.withTransaction {
        val accountIds = HashMap<String, Long>()
        accounts.getAll().forEach { accountIds[it.name.trim().lowercase()] = it.id }
        newAccounts.forEach { a -> accountIds[a.name.trim().lowercase()] = accounts.insert(a.copy(createdAt = System.currentTimeMillis())) }
        val categoryIds = HashMap<String, Long>()
        categories.getAll().forEach { categoryIds["${it.kind}:${it.name.trim().lowercase()}"] = it.id }
        newCategories.forEach { c -> categoryIds["${c.kind}:${c.name.trim().lowercase()}"] = categories.insert(c) }
        val drafts = build(accountIds, categoryIds)
        drafts.forEach { d ->
            val id = transactions.insert(
                TransactionEntity(type = d.type, accountId = d.accountId, toAccountId = d.toAccountId, amountMinor = d.amountMinor, toAmountMinor = d.toAmountMinor, note = d.note, occurredAt = d.occurredAt, createdAt = System.currentTimeMillis())
            )
            if (d.lines.isNotEmpty()) transactions.insertLines(d.lines.map { TransactionLineEntity(transactionId = id, categoryId = it.categoryId, amountMinor = it.amountMinor) })
        }
        drafts.size
    }
}
