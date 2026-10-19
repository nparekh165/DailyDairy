package com.khodiyar.dairybilling.data.repo

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.ProductRateEntity
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull

class SettingsRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    fun observe(): Flow<AppSettingsEntity> = db.settings().observe().filterNotNull()
    suspend fun get(): AppSettingsEntity = db.settings().get() ?: AppSettingsEntity(dairyName = "Khodiyar Dairy", updatedAt = 0)

    suspend fun update(settings: AppSettingsEntity) {
        session.require(Permission.SETTINGS)
        if (settings.dairyName.isBlank()) throw AppException(R.string.err_required_dairy_name)
        if (settings.currencySymbol.isBlank()) throw AppException(R.string.err_required_currency)
        val prefix = settings.invoicePrefix.uppercase().filter { it.isLetterOrDigit() }
        if (prefix.isEmpty() || prefix.length > 8) throw AppException(R.string.err_invoice_prefix)
        if (settings.autoLockMinutes !in 0..240) throw AppException(R.string.err_auto_lock)
        val clean = settings.copy(id = 1, invoicePrefix = prefix, dairyName = settings.dairyName.trim(), updatedAt = time.nowMillis())
        db.withTransaction {
            db.settings().upsert(clean)
            audit.log(AuditLogger.Actions.SETTINGS_UPDATED, "settings", 1, "name=${clean.dairyName} prefix=$prefix sessions=${clean.sessionsEnabled}")
        }
        session.autoLockMinutes = clean.autoLockMinutes
    }
}

data class CustomerInput(
    val code: String,
    val name: String,
    val houseNo: String,
    val mobile: String,
    val address: String,
    val notes: String,
    val openingBalancePaise: Long,
    val defaultProductId: Long?,
    val defaultQuantityMilli: Long,
    val routeOrder: Int = 0,
)

class CustomerRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    private val dao = db.customers()

    fun observeAll(): Flow<List<CustomerEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<CustomerEntity?> = dao.observe(id)
    suspend fun get(id: Long): CustomerEntity? = dao.get(id)

    /** Suggests the next free code like C-0001, following the highest numeric suffix in use. */
    suspend fun suggestCode(): String {
        val max = dao.allCodes().mapNotNull { Regex("(\\d+)$").find(it)?.value?.toIntOrNull() }.maxOrNull() ?: 0
        return "C-" + (max + 1).toString().padStart(4, '0')
    }

    private fun validate(input: CustomerInput): CustomerInput {
        val code = input.code.trim().uppercase()
        if (code.isEmpty()) throw AppException(R.string.err_required_code)
        if (code.length > 20 || !Regex("^[A-Z0-9/_-]+$").matches(code)) throw AppException(R.string.err_code_format)
        if (input.name.isBlank()) throw AppException(R.string.err_required_name)
        if (input.houseNo.isBlank()) throw AppException(R.string.err_required_house)
        val mobile = input.mobile.filter { it.isDigit() || it == '+' }
        if (mobile.isNotEmpty() && mobile.count { it.isDigit() } !in 10..13) throw AppException(R.string.err_mobile_format)
        if (input.defaultQuantityMilli < 0) throw AppException(R.string.err_qty_negative)
        return input.copy(code = code, name = input.name.trim(), houseNo = input.houseNo.trim(), mobile = mobile, address = input.address.trim(), notes = input.notes.trim())
    }

    suspend fun create(input: CustomerInput): Long {
        session.require(Permission.MANAGE_CUSTOMERS)
        val v = validate(input)
        val now = time.nowMillis()
        return db.withTransaction {
            if (dao.findByCode(v.code) != null) throw AppException(R.string.err_code_taken, v.code)
            val id = dao.insert(
                CustomerEntity(
                    code = v.code, name = v.name, houseNo = v.houseNo, mobile = v.mobile, address = v.address, notes = v.notes,
                    openingBalancePaise = v.openingBalancePaise, defaultProductId = v.defaultProductId,
                    defaultQuantityMilli = v.defaultQuantityMilli, routeOrder = v.routeOrder, createdAt = now, updatedAt = now,
                )
            )
            audit.log(AuditLogger.Actions.CUSTOMER_CREATED, "customer", id, "${v.code} ${v.name} opening=${Money.plain(v.openingBalancePaise)}")
            id
        }
    }

    suspend fun update(id: Long, input: CustomerInput) {
        session.require(Permission.MANAGE_CUSTOMERS)
        val v = validate(input)
        db.withTransaction {
            val existing = dao.get(id) ?: throw AppException(R.string.err_not_found)
            dao.findByCode(v.code)?.let { if (it.id != id) throw AppException(R.string.err_code_taken, v.code) }
            if (existing.openingBalancePaise != v.openingBalancePaise) {
                // Opening balance feeds every invoice; changing it is an Admin-only correction.
                session.require(Permission.ADJUSTMENTS)
                if (db.invoices().all().any { it.customerId == id }) throw AppException(R.string.err_opening_locked)
            }
            val updated = existing.copy(
                code = v.code, name = v.name, houseNo = v.houseNo, mobile = v.mobile, address = v.address, notes = v.notes,
                openingBalancePaise = v.openingBalancePaise, defaultProductId = v.defaultProductId,
                defaultQuantityMilli = v.defaultQuantityMilli, routeOrder = v.routeOrder, updatedAt = time.nowMillis(),
            )
            dao.update(updated)
            val changes = buildList {
                if (existing.code != updated.code) add("code ${existing.code}->${updated.code}")
                if (existing.name != updated.name) add("name ${existing.name}->${updated.name}")
                if (existing.houseNo != updated.houseNo) add("house ${existing.houseNo}->${updated.houseNo}")
                if (existing.mobile != updated.mobile) add("mobile changed")
                if (existing.openingBalancePaise != updated.openingBalancePaise) add("opening ${Money.plain(existing.openingBalancePaise)}->${Money.plain(updated.openingBalancePaise)}")
            }.joinToString("; ").ifEmpty { "details updated" }
            audit.log(AuditLogger.Actions.CUSTOMER_UPDATED, "customer", id, changes)
        }
    }

    /** Customers are never physically deleted; deactivation keeps all history accessible. */
    suspend fun setActive(id: Long, active: Boolean) {
        session.require(Permission.MANAGE_CUSTOMERS)
        db.withTransaction {
            val c = dao.get(id) ?: throw AppException(R.string.err_not_found)
            dao.update(c.copy(active = active, updatedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.CUSTOMER_STATUS, "customer", id, if (active) "Reactivated" else "Deactivated")
        }
    }
}

data class ProductWithRate(
    val product: ProductEntity,
    val currentRate: ProductRateEntity?,
    val upcomingRate: ProductRateEntity?,
)

class ProductRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    private val dao = db.products()
    private val rates = db.rates()

    fun observeAll(): Flow<List<ProductEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<ProductEntity?> = dao.observe(id)
    fun observeRates(productId: Long): Flow<List<ProductRateEntity>> = rates.observeForProduct(productId)
    suspend fun get(id: Long) = dao.get(id)

    fun observeWithRates(): Flow<List<ProductWithRate>> = combine(dao.observeAll(), rates.observeAll()) { products, allRates ->
        val today = time.today().toEpochDay()
        val byProduct = allRates.groupBy { it.productId }
        products.map { p ->
            val list = byProduct[p.id].orEmpty()
            ProductWithRate(
                product = p,
                currentRate = list.filter { it.effectiveFrom <= today }.maxByOrNull { it.effectiveFrom },
                upcomingRate = list.filter { it.effectiveFrom > today }.minByOrNull { it.effectiveFrom },
            )
        }
    }

    suspend fun rateOn(productId: Long, epochDay: Long): ProductRateEntity? = rates.rateOn(productId, epochDay)

    private fun validate(name: String, unit: ProductUnit, unitLabel: String, stepMilli: Long) {
        if (name.isBlank()) throw AppException(R.string.err_required_name)
        if (name.length > 40) throw AppException(R.string.err_name_long)
        if (unit == ProductUnit.OTHER && unitLabel.isBlank()) throw AppException(R.string.err_required_unit_label)
        if (stepMilli <= 0) throw AppException(R.string.err_step_positive)
        val decimalsNeeded = Quantity.format(stepMilli).substringAfter('.', "").length
        if (decimalsNeeded > unit.maxDecimals) throw AppException(R.string.err_qty_precision, unit.maxDecimals)
    }

    suspend fun create(name: String, unit: ProductUnit, unitLabel: String, stepMilli: Long, initialRatePaise: Long?, effectiveFrom: Long): Long {
        session.require(Permission.MANAGE_PRODUCTS)
        validate(name, unit, unitLabel, stepMilli)
        if (initialRatePaise != null && initialRatePaise <= 0) throw AppException(R.string.err_rate_positive)
        val now = time.nowMillis()
        return db.withTransaction {
            if (dao.findByName(name.trim()) != null) throw AppException(R.string.err_product_exists)
            val order = (dao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
            val id = dao.insert(ProductEntity(name = name.trim(), unit = unit, unitLabel = unitLabel.trim(), stepMilli = stepMilli, sortOrder = order, createdAt = now, updatedAt = now))
            audit.log(AuditLogger.Actions.PRODUCT_CREATED, "product", id, "${name.trim()} unit=${unit.name}")
            if (initialRatePaise != null) {
                val rid = rates.insert(ProductRateEntity(productId = id, ratePaise = initialRatePaise, effectiveFrom = effectiveFrom, createdBy = session.current?.id, createdAt = now))
                audit.log(AuditLogger.Actions.RATE_ADDED, "product_rate", rid, "${name.trim()} ${Money.plain(initialRatePaise)} from ${Dates.formatIso(effectiveFrom)}")
            }
            id
        }
    }

    suspend fun update(id: Long, name: String, unit: ProductUnit, unitLabel: String, stepMilli: Long, active: Boolean) {
        session.require(Permission.MANAGE_PRODUCTS)
        validate(name, unit, unitLabel, stepMilli)
        db.withTransaction {
            val existing = dao.get(id) ?: throw AppException(R.string.err_not_found)
            dao.findByName(name.trim())?.let { if (it.id != id) throw AppException(R.string.err_product_exists) }
            if (existing.unit != unit && dao.deliveryCount(id) > 0) throw AppException(R.string.err_unit_locked)
            dao.update(existing.copy(name = name.trim(), unit = unit, unitLabel = unitLabel.trim(), stepMilli = stepMilli, active = active, updatedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.PRODUCT_UPDATED, "product", id, "name=${name.trim()} unit=${unit.name} active=$active")
        }
    }

    /**
     * Adds an effective-dated rate. Existing deliveries keep their stored rate unless
     * [repriceUnlocked] is set, in which case DELIVERED entries on/after the date in unlocked
     * periods are re-priced (finalized invoices are snapshots and never change).
     * Returns the number of re-priced entries.
     */
    suspend fun addRate(productId: Long, ratePaise: Long, effectiveFrom: Long, note: String, repriceUnlocked: Boolean): Int {
        session.require(Permission.MANAGE_RATES)
        if (ratePaise <= 0) throw AppException(R.string.err_rate_positive)
        if (ratePaise > 10_000_000) throw AppException(R.string.err_rate_large)
        return db.withTransaction {
            val product = dao.get(productId) ?: throw AppException(R.string.err_not_found)
            if (rates.findExact(productId, effectiveFrom) != null) throw AppException(R.string.err_rate_duplicate, Dates.formatDay(effectiveFrom))
            val now = time.nowMillis()
            val rid = rates.insert(ProductRateEntity(productId = productId, ratePaise = ratePaise, effectiveFrom = effectiveFrom, note = note.trim(), createdBy = session.current?.id, createdAt = now))
            audit.log(AuditLogger.Actions.RATE_ADDED, "product_rate", rid, "${product.name} ${Money.plain(ratePaise)}/${product.displayUnit} from ${Dates.formatIso(effectiveFrom)}")
            var repriced = 0
            if (repriceUnlocked) {
                val locked = db.periods().lockedKeys().toSet()
                val allRates = rates.all().filter { it.productId == productId }
                db.deliveries().productFrom(productId, effectiveFrom).forEach { e ->
                    if (e.status != DeliveryStatus.DELIVERED) return@forEach
                    if (Dates.periodKey(Dates.periodOf(e.date)) in locked) return@forEach
                    val applicable = allRates.filter { it.effectiveFrom <= e.date }.maxByOrNull { it.effectiveFrom } ?: return@forEach
                    if (applicable.ratePaise != e.ratePaise) {
                        db.deliveries().update(e.copy(ratePaise = applicable.ratePaise, amountPaise = BillingEngine.lineAmount(e.quantityMilli, applicable.ratePaise), updatedBy = session.current?.id, updatedAt = now))
                        repriced++
                    }
                }
                if (repriced > 0) audit.log(AuditLogger.Actions.RATE_REPRICED, "product", productId, "$repriced entries re-priced from ${Dates.formatIso(effectiveFrom)}")
            }
            repriced
        }
    }

    /** Only a scheduled (future) rate that no delivery uses yet may be removed. */
    suspend fun deleteFutureRate(rateId: Long) {
        session.require(Permission.MANAGE_RATES)
        db.withTransaction {
            val rate = rates.get(rateId) ?: throw AppException(R.string.err_not_found)
            if (rate.effectiveFrom <= time.today().toEpochDay()) throw AppException(R.string.err_rate_in_use)
            rates.delete(rateId)
            audit.log(AuditLogger.Actions.RATE_DELETED, "product_rate", rateId, "Scheduled ${Money.plain(rate.ratePaise)} from ${Dates.formatIso(rate.effectiveFrom)} removed")
        }
    }
}
