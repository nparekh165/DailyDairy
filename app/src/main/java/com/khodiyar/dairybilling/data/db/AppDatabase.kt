package com.khodiyar.dairybilling.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

@Database(
    entities = [
        UserEntity::class,
        AppSettingsEntity::class,
        CustomerEntity::class,
        ProductEntity::class,
        ProductRateEntity::class,
        DeliveryEntryEntity::class,
        PaymentEntity::class,
        BillingAdjustmentEntity::class,
        MonthlyBillEntity::class,
        InvoiceLineEntity::class,
        BillingPeriodEntity::class,
        AuditLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UserDao
    abstract fun settings(): SettingsDao
    abstract fun customers(): CustomerDao
    abstract fun products(): ProductDao
    abstract fun rates(): RateDao
    abstract fun deliveries(): DeliveryDao
    abstract fun payments(): PaymentDao
    abstract fun adjustments(): AdjustmentDao
    abstract fun invoices(): InvoiceDao
    abstract fun periods(): PeriodDao
    abstract fun audit(): AuditDao

    /** Emits Unit immediately and whenever any of [tables] changes; used for computed (non-DAO) views. */
    fun changes(vararg tables: String): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(tables) {
            override fun onInvalidated(tables: Set<String>) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        invalidationTracker.addObserver(observer)
        awaitClose { invalidationTracker.removeObserver(observer) }
    }.conflate()

    companion object {
        const val VERSION = 2
        const val NAME = "dairy_billing.db"

        val LEDGER_TABLES = arrayOf("customers", "deliveries", "payments", "adjustments", "invoices", "billing_periods", "products", "product_rates")

        fun build(context: Context, name: String? = NAME): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder
                .addMigrations(*Migrations.ALL)
                // No fallbackToDestructiveMigration: an upgrade must never wipe business data.
                .build()
        }
    }
}
