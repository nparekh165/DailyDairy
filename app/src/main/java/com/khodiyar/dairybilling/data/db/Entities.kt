package com.khodiyar.dairybilling.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.khodiyar.dairybilling.domain.model.AdjustmentType
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.InvoiceStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.model.RecordStatus
import com.khodiyar.dairybilling.domain.model.UserRole
import kotlinx.serialization.Serializable

/*
 * Conventions
 *  - Money: Long paise. Quantity: Long milli-units. Business dates: Long epoch day. Timestamps: epoch millis.
 *  - Financial rows are never hard-deleted; they are reversed/superseded with an audit log entry.
 *  - Foreign keys use RESTRICT so history cannot be orphaned; customers/products are deactivated instead.
 */

@Serializable
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val username: String,
    val displayName: String,
    val role: UserRole,
    /** PBKDF2-HMAC-SHA256 verifier, Base64. The PIN/password itself is never stored. */
    val pinHash: String,
    val pinSalt: String,
    val hashIterations: Int,
    /** Comma-separated [com.khodiyar.dairybilling.security.Permission] names granted to staff. */
    val permissions: String = "",
    val active: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "products",
    indices = [Index(value = ["name"], unique = true)],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val unit: ProductUnit,
    /** Display label for OTHER units (e.g. "box"); blank means use [ProductUnit.symbol]. */
    val unitLabel: String = "",
    /** Increment used by the +/- quantity buttons, milli-units. */
    val stepMilli: Long,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val displayUnit: String get() = unitLabel.ifBlank { unit.symbol }
}

@Serializable
@Entity(
    tableName = "product_rates",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["productId", "effectiveFrom"], unique = true)],
)
data class ProductRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    /** Paise per one product unit. */
    val ratePaise: Long,
    /** Epoch day from which this rate applies (inclusive). */
    val effectiveFrom: Long,
    val note: String = "",
    val createdBy: Long? = null,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "customers",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["defaultProductId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index("name"),
        Index("houseNo"),
        Index("mobile"),
        Index("defaultProductId"),
    ],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val code: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val houseNo: String,
    val mobile: String = "",
    val address: String = "",
    val notes: String = "",
    /** Amount owed when the customer was added (negative = advance paid). Precedes all ledger entries. */
    val openingBalancePaise: Long = 0,
    /** Usual product and daily quantity, used to prefill bulk entry. */
    val defaultProductId: Long? = null,
    val defaultQuantityMilli: Long = 0,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    /** Delivery route position (schema v2). 0 = not set; lists then fall back to house number. */
    @ColumnInfo(defaultValue = "0") val routeOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "deliveries",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["customerId", "productId", "date", "session"], unique = true),
        Index("date"),
        Index(value = ["customerId", "date"]),
        Index(value = ["productId", "date"]),
    ],
)
data class DeliveryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val productId: Long,
    val date: Long,
    val session: DeliverySession = DeliverySession.MORNING,
    val status: DeliveryStatus,
    val quantityMilli: Long,
    /** Rate snapshot applied at entry time; later rate changes never alter this row. */
    val ratePaise: Long,
    val amountPaise: Long,
    val note: String = "",
    val createdBy: Long?,
    val createdAt: Long,
    val updatedBy: Long?,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["customerId", "date"]),
        Index("date"),
        Index(value = ["requestId"], unique = true),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val date: Long,
    val amountPaise: Long,
    val method: PaymentMethod,
    val reference: String = "",
    val receivedBy: Long?,
    val createdAt: Long,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val reversedBy: Long? = null,
    val reversedAt: Long? = null,
    val reversalReason: String = "",
    /** Idempotency key generated once per payment form; a double tap cannot insert twice. */
    val requestId: String,
)

@Serializable
@Entity(
    tableName = "adjustments",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["customerId", "date"]), Index("date")],
)
data class BillingAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val date: Long,
    val type: AdjustmentType,
    /** Always positive; sign comes from [type]. */
    val amountPaise: Long,
    val reason: String,
    val createdBy: Long?,
    val createdAt: Long,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val voidedBy: Long? = null,
    val voidedAt: Long? = null,
) {
    val signedPaise: Long get() = if (type == AdjustmentType.DEBIT) amountPaise else -amountPaise
}

@Serializable
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["customerId", "periodKey"]),
        Index("periodKey"),
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["activeSlot"], unique = true),
    ],
)
data class MonthlyBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val periodKey: Int,
    val invoiceNumber: String,
    val sequenceNo: Int,
    val revision: Int = 0,
    val status: InvoiceStatus = InvoiceStatus.FINALIZED,
    /** "customerId:periodKey" while FINALIZED, null once superseded. Unique => one live invoice per month. */
    val activeSlot: String?,
    val customerCode: String,
    val customerName: String,
    val houseNo: String,
    val mobile: String,
    val address: String,
    val previousBalancePaise: Long,
    val chargesPaise: Long,
    val adjustmentsPaise: Long,
    val paymentsPaise: Long,
    val closingBalancePaise: Long,
    val supersedesId: Long? = null,
    val revisionReason: String = "",
    val finalizedBy: Long?,
    val finalizedAt: Long,
    val pdfGeneratedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "invoice_lines",
    foreignKeys = [
        ForeignKey(entity = MonthlyBillEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("invoiceId")],
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val productName: String,
    val unitLabel: String,
    val ratePaise: Long,
    val quantityMilli: Long,
    val amountPaise: Long,
    val deliveryDays: Int,
    val lineOrder: Int,
)

@Serializable
@Entity(tableName = "billing_periods")
data class BillingPeriodEntity(
    @PrimaryKey val periodKey: Int,
    val locked: Boolean,
    val lockedBy: Long? = null,
    val lockedAt: Long? = null,
    val reopenedBy: Long? = null,
    val reopenedAt: Long? = null,
    val reopenReason: String = "",
)

@Serializable
@Entity(tableName = "audit_log", indices = [Index("timestamp"), Index(value = ["entityType", "entityId"])])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val userId: Long?,
    val userName: String,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val details: String,
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val dairyName: String,
    val address: String = "",
    val phone: String = "",
    val invoiceHeader: String = "",
    val invoiceFooter: String = "",
    val paymentInstructions: String = "",
    val upiId: String = "",
    val currencySymbol: String = "₹",
    val invoicePrefix: String = "KD",
    val sessionsEnabled: Boolean = false,
    val autoLockMinutes: Int = 15,
    val updatedAt: Long,
)
