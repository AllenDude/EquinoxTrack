package com.equinoxtrack.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import com.equinoxtrack.app.domain.AccountType
import com.equinoxtrack.app.domain.AssetKind
import com.equinoxtrack.app.domain.BillFrequency
import com.equinoxtrack.app.domain.BudgetPeriod
import com.equinoxtrack.app.domain.CategoryKind
import com.equinoxtrack.app.domain.TxType
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun txTypeToString(v: TxType): String = v.name
    @TypeConverter fun stringToTxType(v: String): TxType = TxType.valueOf(v)
    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(v: String): AccountType = AccountType.valueOf(v)
    @TypeConverter fun categoryKindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToCategoryKind(v: String): CategoryKind = CategoryKind.valueOf(v)
    @TypeConverter fun frequencyToString(v: BillFrequency): String = v.name
    @TypeConverter fun stringToFrequency(v: String): BillFrequency = BillFrequency.valueOf(v)
    @TypeConverter fun budgetPeriodToString(v: BudgetPeriod): String = v.name
    @TypeConverter fun stringToBudgetPeriod(v: String): BudgetPeriod = BudgetPeriod.valueOf(v)
    @TypeConverter fun assetKindToString(v: AssetKind): String = v.name
    @TypeConverter fun stringToAssetKind(v: String): AssetKind = AssetKind.valueOf(v)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert suspend fun insert(account: AccountEntity): Long
    @Insert suspend fun insertAll(items: List<AccountEntity>)
    @Update suspend fun update(account: AccountEntity)
    @Delete suspend fun delete(account: AccountEntity)
    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert suspend fun insert(category: CategoryEntity): Long
    @Insert suspend fun insertAll(items: List<CategoryEntity>)
    @Update suspend fun update(category: CategoryEntity)
    @Delete suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM transaction_lines WHERE categoryId = :id")
    suspend fun usageCount(id: Long): Int

    @Query("DELETE FROM categories") suspend fun clear()
}

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionWithLines>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    suspend fun getAll(): List<TransactionWithLines>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Insert suspend fun insert(tx: TransactionEntity): Long
    @Insert suspend fun insertAll(items: List<TransactionEntity>)
    @Update suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert suspend fun insertLines(lines: List<TransactionLineEntity>)

    @Query("DELETE FROM transaction_lines WHERE transactionId = :txId")
    suspend fun deleteLines(txId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int

    @Query("DELETE FROM transaction_lines") suspend fun clearLines()
    @Query("DELETE FROM transactions") suspend fun clear()
    @Query("SELECT * FROM transaction_lines") suspend fun getAllLines(): List<TransactionLineEntity>
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills") suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bill_payments ORDER BY dueEpochDay DESC, id DESC")
    fun observePayments(): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payments") suspend fun getAllPayments(): List<BillPaymentEntity>

    @Insert suspend fun insert(bill: BillEntity): Long
    @Insert suspend fun insertAll(items: List<BillEntity>)
    @Update suspend fun update(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert suspend fun insertPayment(payment: BillPaymentEntity): Long
    @Insert suspend fun insertPayments(items: List<BillPaymentEntity>)

    @Query("DELETE FROM bill_payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Query("SELECT * FROM bill_payments WHERE id = :id")
    suspend fun getPayment(id: Long): BillPaymentEntity?

    @Query("DELETE FROM bill_payments") suspend fun clearPayments()
    @Query("DELETE FROM bills") suspend fun clear()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt")
    fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun getBudgets(): List<BudgetEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(budget: BudgetEntity): Long
    @Insert suspend fun insertBudgets(items: List<BudgetEntity>)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: Long)

    @Query("SELECT * FROM goals ORDER BY archived, createdAt")
    fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun getGoals(): List<GoalEntity>
    @Insert suspend fun insertGoal(goal: GoalEntity): Long
    @Insert suspend fun insertGoals(items: List<GoalEntity>)
    @Update suspend fun updateGoal(goal: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM goal_entries ORDER BY epochDay DESC, id DESC")
    fun observeGoalEntries(): Flow<List<GoalEntryEntity>>
    @Query("SELECT * FROM goal_entries") suspend fun getGoalEntries(): List<GoalEntryEntity>
    @Insert suspend fun insertGoalEntry(entry: GoalEntryEntity): Long
    @Insert suspend fun insertGoalEntries(items: List<GoalEntryEntity>)
    @Query("DELETE FROM goal_entries WHERE id = :id") suspend fun deleteGoalEntry(id: Long)

    @Query("DELETE FROM goal_entries") suspend fun clearGoalEntries()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY archived, name COLLATE NOCASE")
    fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM assets") suspend fun getAssets(): List<AssetEntity>
    @Insert suspend fun insertAsset(asset: AssetEntity): Long
    @Insert suspend fun insertAssets(items: List<AssetEntity>)
    @Update suspend fun updateAsset(asset: AssetEntity)
    @Query("DELETE FROM assets WHERE id = :id") suspend fun deleteAsset(id: Long)

    @Query("SELECT * FROM asset_valuations ORDER BY epochDay DESC, id DESC")
    fun observeValuations(): Flow<List<AssetValuationEntity>>
    @Query("SELECT * FROM asset_valuations") suspend fun getValuations(): List<AssetValuationEntity>
    @Insert suspend fun insertValuation(v: AssetValuationEntity): Long
    @Insert suspend fun insertValuations(items: List<AssetValuationEntity>)
    @Query("DELETE FROM asset_valuations WHERE id = :id") suspend fun deleteValuation(id: Long)

    @Query("SELECT * FROM exchange_rates")
    fun observeRates(): Flow<List<ExchangeRateEntity>>
    @Query("SELECT * FROM exchange_rates") suspend fun getRates(): List<ExchangeRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRates(items: List<ExchangeRateEntity>)
    @Query("DELETE FROM exchange_rates WHERE currency = :code") suspend fun deleteRate(code: String)

    @Query("DELETE FROM asset_valuations") suspend fun clearValuations()
    @Query("DELETE FROM assets") suspend fun clearAssets()
    @Query("DELETE FROM exchange_rates") suspend fun clearRates()
}

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionLineEntity::class,
        BillEntity::class,
        BillPaymentEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalEntryEntity::class,
        AssetEntity::class,
        AssetValuationEntity::class,
        ExchangeRateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EquinoxDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun billDao(): BillDao
    abstract fun planDao(): PlanDao
    abstract fun assetDao(): AssetDao

    companion object {
        fun build(context: Context): EquinoxDatabase =
            Room.databaseBuilder(context.applicationContext, EquinoxDatabase::class.java, "equinox.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Cascades (e.g. deleting a payment transaction un-pays its bill) rely on this.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
