package com.khodiyar.dairybilling.data.repo

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.flow.Flow

/** One row to save. [expectedUpdatedAt] is the version the user edited (null = they saw no entry). */
data class DeliveryDraft(
    val customerId: Long,
    val productId: Long,
    val date: Long,
    val session: DeliverySession,
    val status: DeliveryStatus,
    val quantityMilli: Long,
    val note: String = "",
    val expectedUpdatedAt: Long? = null,
)

data class DeliverySaveResult(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val unchanged: Int,
    /** Customers whose entry changed since it was loaded; skipped so nothing is silently overwritten. */
    val conflicts: List<Long>,
)

class DeliveryRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    private val dao = db.deliveries()

    fun observeDay(date: Long, s: DeliverySession): Flow<List<DeliveryEntryEntity>> = dao.observeDay(date, s)
    fun observeDayAll(date: Long): Flow<List<DeliveryEntryEntity>> = dao.observeDayAllSessions(date)
    fun observeCustomer(customerId: Long, from: Long, to: Long) = dao.observeCustomerRange(customerId, from, to)
    suspend fun forDayProduct(date: Long, s: DeliverySession, productId: Long) = dao.forDayProduct(date, s, productId)

    private suspend fun checkDateAllowed(date: Long, isEdit: Boolean) {
        val today = time.today().toEpochDay()
        if (date > today) throw AppException(R.string.err_future_date)
        if (date < today && !session.has(Permission.EDIT_PAST_DELIVERIES)) throw AppException(R.string.err_past_not_permitted)
        val key = Dates.periodKey(Dates.periodOf(date))
        if (db.periods().get(key)?.locked == true) throw AppException(R.string.err_period_locked, Dates.formatMonth(Dates.periodOf(date)))
    }

    /** Saves many entries atomically: either every valid row is written or none are. */
    suspend fun saveBatch(drafts: List<DeliveryDraft>, deleteIds: List<Long> = emptyList()): DeliverySaveResult {
        val user = session.require(Permission.RECORD_DELIVERIES)
        val now = time.nowMillis()
        val today = time.today().toEpochDay()
        val seen = HashSet<String>()
        for (d in drafts) {
            val k = "${d.customerId}:${d.productId}:${d.date}:${d.session}"
            if (!seen.add(k)) throw AppException(R.string.err_duplicate_in_batch)
            if (d.quantityMilli < 0) throw AppException(R.string.err_qty_negative)
            if (d.quantityMilli > Quantity.MAX_MILLI) throw AppException(R.string.err_qty_large)
            if (d.status == DeliveryStatus.DELIVERED && d.quantityMilli == 0L) throw AppException(R.string.err_qty_zero_delivered)
            if (d.status != DeliveryStatus.DELIVERED && d.quantityMilli != 0L) throw AppException(R.string.err_qty_nodelivery)
        }
        return db.withTransaction {
            var created = 0
            var updated = 0
            var unchanged = 0
            var deleted = 0
            val conflicts = ArrayList<Long>()
            val rateCache = HashMap<Pair<Long, Long>, Long?>()
            val checkedDates = HashSet<Long>()
            for (d in drafts) {
                if (checkedDates.add(d.date)) checkDateAllowed(d.date, isEdit = false)
                val customer = db.customers().get(d.customerId) ?: throw AppException(R.string.err_not_found)
                val product = db.products().get(d.productId) ?: throw AppException(R.string.err_not_found)
                val existing = dao.findKey(d.customerId, d.productId, d.date, d.session)
                if (existing == null && (!customer.active || !product.active)) throw AppException(R.string.err_inactive_record, customer.name)
                if (existing?.updatedAt != d.expectedUpdatedAt) {
                    conflicts += d.customerId
                    continue
                }
                val rate = if (d.status == DeliveryStatus.DELIVERED) {
                    rateCache.getOrPut(d.productId to d.date) { db.rates().rateOn(d.productId, d.date)?.ratePaise }
                        ?: throw AppException(R.string.err_no_rate, product.name, Dates.formatDay(d.date))
                } else existing?.ratePaise ?: 0L
                val amount = if (d.status == DeliveryStatus.DELIVERED) BillingEngine.lineAmount(d.quantityMilli, rate) else 0L
                if (existing == null) {
                    dao.insert(
                        DeliveryEntryEntity(
                            customerId = d.customerId, productId = d.productId, date = d.date, session = d.session,
                            status = d.status, quantityMilli = d.quantityMilli, ratePaise = rate, amountPaise = amount,
                            note = d.note, createdBy = user.id, createdAt = now, updatedBy = user.id, updatedAt = now,
                        )
                    )
                    created++
                    if (d.date < today) audit.log(AuditLogger.Actions.DELIVERY_BACKDATED, "customer", d.customerId,
                        "${product.name} ${Dates.formatIso(d.date)} ${d.status} ${Quantity.format(d.quantityMilli)}")
                } else {
                    if (existing.status == d.status && existing.quantityMilli == d.quantityMilli && existing.note == d.note && existing.ratePaise == rate) {
                        unchanged++
                        continue
                    }
                    if (existing.createdBy != user.id && !session.has(Permission.EDIT_PAST_DELIVERIES)) throw AppException(R.string.err_edit_others)
                    dao.update(existing.copy(status = d.status, quantityMilli = d.quantityMilli, ratePaise = rate, amountPaise = amount, note = d.note, updatedBy = user.id, updatedAt = now))
                    updated++
                    audit.log(AuditLogger.Actions.DELIVERY_UPDATED, "delivery", existing.id,
                        "${customer.code} ${product.name} ${Dates.formatIso(d.date)}: ${existing.status} ${Quantity.format(existing.quantityMilli)} @${Money.plain(existing.ratePaise)} -> ${d.status} ${Quantity.format(d.quantityMilli)} @${Money.plain(rate)}")
                }
            }
            for (id in deleteIds) {
                val e = dao.get(id) ?: continue
                checkDateAllowed(e.date, isEdit = true)
                if (e.createdBy != user.id && !session.has(Permission.EDIT_PAST_DELIVERIES)) throw AppException(R.string.err_edit_others)
                dao.delete(id)
                deleted++
                audit.log(AuditLogger.Actions.DELIVERY_DELETED, "delivery", id,
                    "customer=${e.customerId} product=${e.productId} ${Dates.formatIso(e.date)} ${e.status} ${Quantity.format(e.quantityMilli)} @${Money.plain(e.ratePaise)} = ${Money.plain(e.amountPaise)}")
            }
            DeliverySaveResult(created, updated, deleted, unchanged, conflicts)
        }
    }
}
