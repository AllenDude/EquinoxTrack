package com.equinoxtrack.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.AssetKind
import com.equinoxtrack.app.domain.BillFrequency
import com.equinoxtrack.app.domain.BudgetPeriod
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.TxType
import kotlinx.serialization.Serializable

/*
 * Money is always stored as Long minor units (e.g. centavos) in the currency of the owning record.
 * Balances are never stored: they are derived from the opening balance plus the ledger, so editing
 * or deleting any transaction automatically corrects every affected balance.
 */

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long = 0,
    val color: Long,
    val icon: String,
    /** Counted in total balance and net worth. */
    val includeInTotals: Boolean = true,
    /** Money in this account may be spent day-to-day (feeds Safe-to-Spend). */
    val includeInSafeToSpend: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val color: Long,
    val icon: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("occurredAt"), Index("billId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val accountId: Long,
    /** Destination account for transfers. */
    val toAccountId: Long? = null,
    /** Positive amount in the source account's currency. Signed only for ADJUSTMENT. */
    val amountMinor: Long,
    /** Amount credited to [toAccountId] in its currency (transfers between currencies). */
    val toAmountMinor: Long? = null,
    val note: String = "",
    val occurredAt: Long,
    /** Set when this expense is the payment of a bill. */
    val billId: Long? = null,
    val createdAt: Long = 0,
)

/** Category allocation of an income/expense. Every income/expense has at least one line. */
@Serializable
@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("transactionId"), Index("categoryId")],
)
data class TransactionLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val categoryId: Long?,
    val amountMinor: Long,
    val note: String = "",
)

data class TransactionWithLines(
    @Embedded val tx: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val lines: List<TransactionLineEntity>,
)

@Serializable
@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("categoryId")],
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    /** Default account the bill is paid from. */
    val accountId: Long?,
    val categoryId: Long?,
    val frequency: BillFrequency,
    /** Repeat every N weeks/months/years. */
    val interval: Int = 1,
    /** First due date as LocalDate epoch day. */
    val anchorEpochDay: Long,
    /** Optional last due date (inclusive). */
    val endEpochDay: Long? = null,
    val isSubscription: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val active: Boolean = true,
    val note: String = "",
    val color: Long,
    val icon: String,
    val createdAt: Long = 0,
)

/**
 * Settles one occurrence of a bill. A paid occurrence links to the expense transaction that paid it;
 * deleting that transaction cascades here and the occurrence becomes unpaid again.
 */
@Serializable
@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("billId"), Index("transactionId")],
)
data class BillPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val dueEpochDay: Long,
    val paidAt: Long,
    val amountMinor: Long,
    val transactionId: Long? = null,
    val skipped: Boolean = false,
)

@Serializable
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null means an overall budget covering every expense. */
    val categoryId: Long?,
    val limitMinor: Long,
    val currency: String,
    val period: BudgetPeriod,
    val createdAt: Long = 0,
)

@Serializable
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    val targetEpochDay: Long? = null,
    val color: Long,
    val icon: String,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0,
)

/** Contribution (+) or withdrawal (-) earmarked for a goal. Never income, never a transfer. */
@Serializable
@Entity(
    tableName = "goal_entries",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class GoalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = 0,
)

/** A personal asset that is NOT money held in an account (home, vehicle, valuables...). */
@Serializable
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AssetKind,
    val currency: String,
    val includeInNetWorth: Boolean = true,
    val note: String = "",
    val color: Long,
    val archived: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
@Entity(
    tableName = "asset_valuations",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assetId")],
)
data class AssetValuationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val valueMinor: Long,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long = 0,
)

/**
 * Units of [currency] per one unit of a common reference currency, stored as a decimal string.
 * Converting A -> B is amount / rate(A) * rate(B), so any reference works as long as it is shared.
 */
@Serializable
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val currency: String,
    val perReference: String,
    val updatedAt: Long = 0,
)
