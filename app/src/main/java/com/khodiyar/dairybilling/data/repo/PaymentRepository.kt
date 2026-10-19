package com.khodiyar.dairybilling.data.repo

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.BillingAdjustmentEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.model.AdjustmentType
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.RecordStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.flow.Flow

data class PaymentInput(
    val customerId: Long,
    val date: Long,
    val amountPaise: Long,
    val method: PaymentMethod,
    val reference: String,
    /** Generated once per payment form instance. */
    val requestId: String,
)

sealed interface PaymentSaveResult {
    data class Saved(val payment: PaymentEntity) : PaymentSaveResult
    /** Same form submitted twice: the original payment is returned, nothing new is written. */
    data class AlreadySaved(val payment: PaymentEntity) : PaymentSaveResult
    /** A payment with the same customer and amount was recorded moments ago; needs confirmation. */
    data class PossibleDuplicate(val existing: PaymentEntity) : PaymentSaveResult
}

class PaymentRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    private val dao = db.payments()

    fun observeForCustomer(customerId: Long): Flow<List<PaymentEntity>> = dao.observeForCustomer(customerId)
    fun observeRange(from: Long, to: Long): Flow<List<PaymentEntity>> = dao.observeRange(from, to)
    fun observeRecent(limit: Int) = dao.observeRecent(limit)
    fun observeAdjustments(customerId: Long) = db.adjustments().observeForCustomer(customerId)
    suspend fun get(id: Long) = dao.get(id)

    private suspend fun ensureUnlocked(date: Long) {
        val ym = Dates.periodOf(date)
        if (db.periods().get(Dates.periodKey(ym))?.locked == true) throw AppException(R.string.err_period_locked, Dates.formatMonth(ym))
    }

    suspend fun record(input: PaymentInput, confirmDuplicate: Boolean = false): PaymentSaveResult {
        val user = session.require(Permission.RECORD_PAYMENTS)
        if (input.amountPaise <= 0) throw AppException(R.string.err_amount_positive)
        if (input.amountPaise > 1_000_000_000L) throw AppException(R.string.err_amount_large)
        if (input.date > time.today().toEpochDay()) throw AppException(R.string.err_future_date)
        if (input.requestId.isBlank()) throw AppException(R.string.err_generic)
        return db.withTransaction {
            dao.findByRequest(input.requestId)?.let { return@withTransaction PaymentSaveResult.AlreadySaved(it) }
            val customer = db.customers().get(input.customerId) ?: throw AppException(R.string.err_not_found)
            ensureUnlocked(input.date)
            val now = time.nowMillis()
            if (!confirmDuplicate) {
                dao.recentSimilar(input.customerId, input.amountPaise, now - DUPLICATE_WINDOW_MILLIS)?.let {
                    return@withTransaction PaymentSaveResult.PossibleDuplicate(it)
                }
            }
            val payment = PaymentEntity(
                customerId = input.customerId, date = input.date, amountPaise = input.amountPaise, method = input.method,
                reference = input.reference.trim(), receivedBy = user.id, createdAt = now, requestId = input.requestId,
            )
            val id = dao.insert(payment)
            audit.log(AuditLogger.Actions.PAYMENT_RECORDED, "payment", id,
                "${customer.code} ${Money.plain(input.amountPaise)} ${input.method} on ${Dates.formatIso(input.date)}")
            PaymentSaveResult.Saved(payment.copy(id = id))
        }
    }

    /** Reversal keeps the original row (marked REVERSED) so payment history is never destroyed. */
    suspend fun reverse(paymentId: Long, reason: String) {
        val user = session.require(Permission.REVERSE_PAYMENTS)
        if (reason.isBlank()) throw AppException(R.string.err_reason_required)
        db.withTransaction {
            val p = dao.get(paymentId) ?: throw AppException(R.string.err_not_found)
            if (p.status == RecordStatus.REVERSED) throw AppException(R.string.err_already_reversed)
            ensureUnlocked(p.date)
            dao.update(p.copy(status = RecordStatus.REVERSED, reversedBy = user.id, reversedAt = time.nowMillis(), reversalReason = reason.trim()))
            audit.log(AuditLogger.Actions.PAYMENT_REVERSED, "payment", paymentId, "${Money.plain(p.amountPaise)} reversed: ${reason.trim()}")
        }
    }

    suspend fun addAdjustment(customerId: Long, date: Long, type: AdjustmentType, amountPaise: Long, reason: String): Long {
        session.require(Permission.ADJUSTMENTS)
        if (amountPaise <= 0) throw AppException(R.string.err_amount_positive)
        if (reason.isBlank()) throw AppException(R.string.err_reason_required)
        if (date > time.today().toEpochDay()) throw AppException(R.string.err_future_date)
        return db.withTransaction {
            db.customers().get(customerId) ?: throw AppException(R.string.err_not_found)
            ensureUnlocked(date)
            val id = db.adjustments().insert(
                BillingAdjustmentEntity(customerId = customerId, date = date, type = type, amountPaise = amountPaise, reason = reason.trim(), createdBy = session.current?.id, createdAt = time.nowMillis())
            )
            audit.log(AuditLogger.Actions.ADJUSTMENT_ADDED, "adjustment", id, "$type ${Money.plain(amountPaise)} customer=$customerId: ${reason.trim()}")
            id
        }
    }

    suspend fun voidAdjustment(id: Long) {
        val user = session.require(Permission.ADJUSTMENTS)
        db.withTransaction {
            val a = db.adjustments().get(id) ?: throw AppException(R.string.err_not_found)
            if (a.status == RecordStatus.REVERSED) throw AppException(R.string.err_already_reversed)
            ensureUnlocked(a.date)
            db.adjustments().update(a.copy(status = RecordStatus.REVERSED, voidedBy = user.id, voidedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.ADJUSTMENT_VOIDED, "adjustment", id, "${a.type} ${Money.plain(a.amountPaise)} voided")
        }
    }

    companion object {
        const val DUPLICATE_WINDOW_MILLIS = 2 * 60_000L
    }
}
