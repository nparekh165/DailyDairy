package com.khodiyar.dairybilling.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.khodiyar.dairybilling.domain.model.DeliverySession
import kotlinx.coroutines.flow.Flow

/** Aggregate row: a sum per customer. */
data class CustomerSum(val customerId: Long, val total: Long)

/** Aggregate row: quantity and amount per product. */
data class ProductTotal(val productId: Long, val quantityMilli: Long, val amountPaise: Long, val entries: Int)

/** Aggregate row: amount per day. */
data class DayTotal(val date: Long, val amountPaise: Long)

/** Aggregate row: lines grouped by product and applied rate, used to build invoice lines. */
data class ProductRateTotal(
    val productId: Long,
    val ratePaise: Long,
    val quantityMilli: Long,
    val amountPaise: Long,
    val days: Int,
)

@Dao
interface UserDao {
    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY role, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = 1")
    suspend fun activeAdminCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users") suspend fun all(): List<UserEntity>
    @Query("DELETE FROM users") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<UserEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)

    @Query("SELECT * FROM app_settings") suspend fun all(): List<AppSettingsEntity>
    @Query("DELETE FROM app_settings") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<AppSettingsEntity>)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE active = 1 ORDER BY name COLLATE NOCASE")
    suspend fun active(): List<CustomerEntity>

    @Query("SELECT COUNT(*) FROM customers WHERE active = 1")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT code FROM customers")
    suspend fun allCodes(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM customers") suspend fun all(): List<CustomerEntity>
    @Query("DELETE FROM customers") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<CustomerEntity>)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY active DESC, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM deliveries WHERE productId = :productId")
    suspend fun deliveryCount(productId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products") suspend fun all(): List<ProductEntity>
    @Query("DELETE FROM products") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<ProductEntity>)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM product_rates WHERE productId = :productId ORDER BY effectiveFrom DESC")
    fun observeForProduct(productId: Long): Flow<List<ProductRateEntity>>

    @Query("SELECT * FROM product_rates ORDER BY productId, effectiveFrom")
    fun observeAll(): Flow<List<ProductRateEntity>>

    @Query("SELECT * FROM product_rates WHERE productId = :productId AND effectiveFrom <= :date ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun rateOn(productId: Long, date: Long): ProductRateEntity?

    @Query("SELECT * FROM product_rates WHERE productId = :productId AND effectiveFrom = :date LIMIT 1")
    suspend fun findExact(productId: Long, date: Long): ProductRateEntity?

    @Query("SELECT * FROM product_rates WHERE id = :id")
    suspend fun get(id: Long): ProductRateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rate: ProductRateEntity): Long

    @Query("DELETE FROM product_rates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM product_rates") suspend fun all(): List<ProductRateEntity>
    @Query("DELETE FROM product_rates") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<ProductRateEntity>)
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries WHERE date = :date AND session = :session")
    fun observeDay(date: Long, session: DeliverySession): Flow<List<DeliveryEntryEntity>>

    @Query("SELECT * FROM deliveries WHERE date = :date")
    fun observeDayAllSessions(date: Long): Flow<List<DeliveryEntryEntity>>

    @Query("SELECT * FROM deliveries WHERE date = :date AND session = :session AND productId = :productId")
    suspend fun forDayProduct(date: Long, session: DeliverySession, productId: Long): List<DeliveryEntryEntity>

    @Query("SELECT * FROM deliveries WHERE customerId = :customerId AND productId = :productId AND date = :date AND session = :session LIMIT 1")
    suspend fun findKey(customerId: Long, productId: Long, date: Long, session: DeliverySession): DeliveryEntryEntity?

    @Query("SELECT * FROM deliveries WHERE id = :id")
    suspend fun get(id: Long): DeliveryEntryEntity?

    @Query("SELECT * FROM deliveries WHERE customerId = :customerId AND date BETWEEN :from AND :to ORDER BY date DESC, session")
    fun observeCustomerRange(customerId: Long, from: Long, to: Long): Flow<List<DeliveryEntryEntity>>

    @Query("SELECT * FROM deliveries WHERE customerId = :customerId AND date BETWEEN :from AND :to ORDER BY date, session")
    suspend fun customerRange(customerId: Long, from: Long, to: Long): List<DeliveryEntryEntity>

    @Query("SELECT * FROM deliveries WHERE date BETWEEN :from AND :to ORDER BY date, customerId")
    suspend fun range(from: Long, to: Long): List<DeliveryEntryEntity>

    @Query("SELECT * FROM deliveries WHERE productId = :productId AND date >= :from")
    suspend fun productFrom(productId: Long, from: Long): List<DeliveryEntryEntity>

    @Query("SELECT customerId, COALESCE(SUM(amountPaise),0) AS total FROM deliveries WHERE date < :before GROUP BY customerId")
    suspend fun chargesBefore(before: Long): List<CustomerSum>

    @Query("SELECT customerId, COALESCE(SUM(amountPaise),0) AS total FROM deliveries WHERE date BETWEEN :from AND :to GROUP BY customerId")
    suspend fun chargesBetween(from: Long, to: Long): List<CustomerSum>

    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM deliveries WHERE customerId = :customerId AND date < :before")
    suspend fun customerChargesBefore(customerId: Long, before: Long): Long

    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM deliveries WHERE customerId = :customerId")
    suspend fun customerChargesAll(customerId: Long): Long

    @Query(
        """SELECT productId, ratePaise, COALESCE(SUM(quantityMilli),0) AS quantityMilli,
           COALESCE(SUM(amountPaise),0) AS amountPaise, COUNT(DISTINCT date) AS days
           FROM deliveries WHERE customerId = :customerId AND status = 'DELIVERED' AND date BETWEEN :from AND :to
           GROUP BY productId, ratePaise ORDER BY productId, ratePaise"""
    )
    suspend fun customerLines(customerId: Long, from: Long, to: Long): List<ProductRateTotal>

    @Query(
        """SELECT productId, COALESCE(SUM(quantityMilli),0) AS quantityMilli, COALESCE(SUM(amountPaise),0) AS amountPaise,
           COUNT(*) AS entries FROM deliveries WHERE status = 'DELIVERED' AND date BETWEEN :from AND :to GROUP BY productId"""
    )
    suspend fun productTotals(from: Long, to: Long): List<ProductTotal>

    @Query("SELECT date, COALESCE(SUM(amountPaise),0) AS amountPaise FROM deliveries WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    suspend fun dailyTotals(from: Long, to: Long): List<DayTotal>

    @Query("SELECT MIN(date) FROM deliveries WHERE customerId = :customerId")
    suspend fun firstDate(customerId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: DeliveryEntryEntity): Long

    @Update
    suspend fun update(entry: DeliveryEntryEntity)

    @Query("DELETE FROM deliveries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM deliveries") suspend fun all(): List<DeliveryEntryEntity>
    @Query("DELETE FROM deliveries") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<DeliveryEntryEntity>)
}

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun get(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE requestId = :requestId LIMIT 1")
    suspend fun findByRequest(requestId: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE customerId = :customerId AND status = 'ACTIVE' AND amountPaise = :amount AND createdAt >= :since ORDER BY createdAt DESC LIMIT 1")
    suspend fun recentSimilar(customerId: Long, amount: Long, since: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY date DESC, createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeRange(from: Long, to: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE date BETWEEN :from AND :to ORDER BY date, createdAt")
    suspend fun range(from: Long, to: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE customerId = :customerId AND date BETWEEN :from AND :to ORDER BY date, createdAt")
    suspend fun customerRange(customerId: Long, from: Long, to: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PaymentEntity>>

    @Query("SELECT customerId, COALESCE(SUM(amountPaise),0) AS total FROM payments WHERE status = 'ACTIVE' AND date < :before GROUP BY customerId")
    suspend fun paidBefore(before: Long): List<CustomerSum>

    @Query("SELECT customerId, COALESCE(SUM(amountPaise),0) AS total FROM payments WHERE status = 'ACTIVE' AND date BETWEEN :from AND :to GROUP BY customerId")
    suspend fun paidBetween(from: Long, to: Long): List<CustomerSum>

    @Query("SELECT customerId, COALESCE(SUM(amountPaise),0) AS total FROM payments WHERE status = 'ACTIVE' AND date > :after GROUP BY customerId")
    suspend fun paidAfter(after: Long): List<CustomerSum>

    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE status = 'ACTIVE' AND customerId = :customerId AND date < :before")
    suspend fun customerPaidBefore(customerId: Long, before: Long): Long

    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE status = 'ACTIVE' AND customerId = :customerId")
    suspend fun customerPaidAll(customerId: Long): Long

    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE status = 'ACTIVE' AND date BETWEEN :from AND :to")
    suspend fun totalBetween(from: Long, to: Long): Long

    @Query("SELECT * FROM payments") suspend fun all(): List<PaymentEntity>
    @Query("DELETE FROM payments") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<PaymentEntity>)
}

@Dao
interface AdjustmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(adjustment: BillingAdjustmentEntity): Long

    @Update
    suspend fun update(adjustment: BillingAdjustmentEntity)

    @Query("SELECT * FROM adjustments WHERE id = :id")
    suspend fun get(id: Long): BillingAdjustmentEntity?

    @Query("SELECT * FROM adjustments WHERE customerId = :customerId ORDER BY date DESC, createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<BillingAdjustmentEntity>>

    @Query("SELECT * FROM adjustments WHERE customerId = :customerId AND status = 'ACTIVE' AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun customerRange(customerId: Long, from: Long, to: Long): List<BillingAdjustmentEntity>

    @Query("SELECT * FROM adjustments WHERE status = 'ACTIVE' AND date BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<BillingAdjustmentEntity>

    @Query("SELECT * FROM adjustments WHERE status = 'ACTIVE' AND date < :before")
    suspend fun before(before: Long): List<BillingAdjustmentEntity>

    @Query("SELECT * FROM adjustments WHERE status = 'ACTIVE' AND date > :after")
    suspend fun after(after: Long): List<BillingAdjustmentEntity>

    @Query("SELECT * FROM adjustments WHERE status = 'ACTIVE' AND customerId = :customerId")
    suspend fun customerActive(customerId: Long): List<BillingAdjustmentEntity>

    @Query("SELECT * FROM adjustments") suspend fun all(): List<BillingAdjustmentEntity>
    @Query("DELETE FROM adjustments") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<BillingAdjustmentEntity>)
}

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(invoice: MonthlyBillEntity): Long

    @Update
    suspend fun update(invoice: MonthlyBillEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun get(id: Long): MonthlyBillEntity?

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND periodKey = :periodKey AND status = 'FINALIZED' LIMIT 1")
    suspend fun activeFor(customerId: Long, periodKey: Int): MonthlyBillEntity?

    @Query("SELECT * FROM invoices WHERE periodKey = :periodKey AND status = 'FINALIZED'")
    suspend fun activeForPeriod(periodKey: Int): List<MonthlyBillEntity>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY periodKey DESC, revision DESC")
    fun observeForCustomer(customerId: Long): Flow<List<MonthlyBillEntity>>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND periodKey = :periodKey ORDER BY revision DESC")
    suspend fun historyFor(customerId: Long, periodKey: Int): List<MonthlyBillEntity>

    @Query("SELECT COALESCE(MAX(sequenceNo),0) FROM invoices WHERE periodKey = :periodKey")
    suspend fun maxSequence(periodKey: Int): Int

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY lineOrder")
    suspend fun lines(invoiceId: Long): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoices") suspend fun all(): List<MonthlyBillEntity>
    @Query("SELECT * FROM invoice_lines") suspend fun allLines(): List<InvoiceLineEntity>
    @Query("DELETE FROM invoices") suspend fun deleteAll()
    @Query("DELETE FROM invoice_lines") suspend fun deleteAllLines()
    @Insert suspend fun insertAll(rows: List<MonthlyBillEntity>)
    @Insert suspend fun insertAllLines(rows: List<InvoiceLineEntity>)
}

@Dao
interface PeriodDao {
    @Query("SELECT * FROM billing_periods WHERE periodKey = :periodKey")
    suspend fun get(periodKey: Int): BillingPeriodEntity?

    @Query("SELECT * FROM billing_periods WHERE periodKey = :periodKey")
    fun observe(periodKey: Int): Flow<BillingPeriodEntity?>

    @Query("SELECT periodKey FROM billing_periods WHERE locked = 1")
    suspend fun lockedKeys(): List<Int>

    @Upsert
    suspend fun upsert(period: BillingPeriodEntity)

    @Query("SELECT * FROM billing_periods") suspend fun all(): List<BillingPeriodEntity>
    @Query("DELETE FROM billing_periods") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<BillingPeriodEntity>)
}

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC")
    suspend fun forEntity(type: String, id: Long): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log") suspend fun all(): List<AuditLogEntity>
    @Query("DELETE FROM audit_log") suspend fun deleteAll()
    @Insert suspend fun insertAll(rows: List<AuditLogEntity>)
}
