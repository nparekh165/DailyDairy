package com.khodiyar.dairybilling.data.repo

import com.khodiyar.dairybilling.data.db.AuditDao
import com.khodiyar.dairybilling.data.db.AuditLogEntity
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.SessionManager

/** Appends immutable audit records. Called inside the same transaction as the change it describes. */
class AuditLogger(
    private val dao: AuditDao,
    private val session: SessionManager,
    private val time: TimeProvider,
) {
    suspend fun log(action: String, entityType: String, entityId: Long?, details: String) {
        val user = session.current
        dao.insert(
            AuditLogEntity(
                timestamp = time.nowMillis(),
                userId = user?.id,
                userName = user?.displayName ?: "system",
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details,
            )
        )
    }

    object Actions {
        const val SETUP = "SETUP"
        const val LOGIN = "LOGIN"
        const val LOGIN_FAILED = "LOGIN_FAILED"
        const val LOGOUT = "LOGOUT"
        const val USER_CREATED = "USER_CREATED"
        const val USER_UPDATED = "USER_UPDATED"
        const val PIN_RESET = "PIN_RESET"
        const val PIN_CHANGED = "PIN_CHANGED"
        const val CUSTOMER_CREATED = "CUSTOMER_CREATED"
        const val CUSTOMER_UPDATED = "CUSTOMER_UPDATED"
        const val CUSTOMER_STATUS = "CUSTOMER_STATUS"
        const val PRODUCT_CREATED = "PRODUCT_CREATED"
        const val PRODUCT_UPDATED = "PRODUCT_UPDATED"
        const val RATE_ADDED = "RATE_ADDED"
        const val RATE_DELETED = "RATE_DELETED"
        const val RATE_REPRICED = "RATE_REPRICED"
        const val DELIVERY_UPDATED = "DELIVERY_UPDATED"
        const val DELIVERY_DELETED = "DELIVERY_DELETED"
        const val DELIVERY_BACKDATED = "DELIVERY_BACKDATED"
        const val PAYMENT_RECORDED = "PAYMENT_RECORDED"
        const val PAYMENT_REVERSED = "PAYMENT_REVERSED"
        const val ADJUSTMENT_ADDED = "ADJUSTMENT_ADDED"
        const val ADJUSTMENT_VOIDED = "ADJUSTMENT_VOIDED"
        const val INVOICE_FINALIZED = "INVOICE_FINALIZED"
        const val INVOICE_REVISED = "INVOICE_REVISED"
        const val INVOICE_PDF = "INVOICE_PDF"
        const val PERIOD_LOCKED = "PERIOD_LOCKED"
        const val PERIOD_REOPENED = "PERIOD_REOPENED"
        const val SETTINGS_UPDATED = "SETTINGS_UPDATED"
        const val BACKUP_CREATED = "BACKUP_CREATED"
        const val BACKUP_RESTORED = "BACKUP_RESTORED"
    }
}
