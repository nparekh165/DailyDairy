package com.khodiyar.dairybilling.backup

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.db.AuditLogEntity
import com.khodiyar.dairybilling.data.db.BillingAdjustmentEntity
import com.khodiyar.dairybilling.data.db.BillingPeriodEntity
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.InvoiceLineEntity
import com.khodiyar.dairybilling.data.db.MonthlyBillEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.ProductRateEntity
import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.data.repo.AuditLogger
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val format: String = FORMAT,
    val formatVersion: Int = 1,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val createdBy: String,
    val dairyName: String,
    val counts: Map<String, Int>,
    /** SHA-256 (hex) of data.json; detects truncated or tampered files. */
    val dataSha256: String,
) {
    companion object { const val FORMAT = "khodiyar-dairy-billing-backup" }
}

@Serializable
data class BackupData(
    val settings: List<AppSettingsEntity> = emptyList(),
    val users: List<UserEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val rates: List<ProductRateEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val deliveries: List<DeliveryEntryEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList(),
    val adjustments: List<BillingAdjustmentEntity> = emptyList(),
    val invoices: List<MonthlyBillEntity> = emptyList(),
    val invoiceLines: List<InvoiceLineEntity> = emptyList(),
    val periods: List<BillingPeriodEntity> = emptyList(),
    val audit: List<AuditLogEntity> = emptyList(),
) {
    fun counts(): Map<String, Int> = linkedMapOf(
        "customers" to customers.size, "products" to products.size, "rates" to rates.size,
        "deliveries" to deliveries.size, "payments" to payments.size, "adjustments" to adjustments.size,
        "invoices" to invoices.size, "invoiceLines" to invoiceLines.size, "users" to users.size,
        "periods" to periods.size, "audit" to audit.size, "settings" to settings.size,
    )
}

data class BackupPackage(val manifest: BackupManifest, val data: BackupData)

/**
 * Versioned ZIP: manifest.json + data.json. PIN verifiers are included only as salted PBKDF2
 * hashes (never plaintext) so accounts work after restore. Restores are all-or-nothing and a
 * safety backup of the current data is written first.
 */
class BackupManager(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
    private val appVersion: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    /** Reads every table inside one transaction, giving a consistent snapshot. */
    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            settings = db.settings().all(), users = db.users().all(), products = db.products().all(), rates = db.rates().all(),
            customers = db.customers().all(), deliveries = db.deliveries().all(), payments = db.payments().all(),
            adjustments = db.adjustments().all(), invoices = db.invoices().all(), invoiceLines = db.invoices().allLines(),
            periods = db.periods().all(), audit = db.audit().all(),
        )
    }

    suspend fun createBackup(out: OutputStream): BackupManifest {
        val user = session.require(Permission.BACKUP)
        audit.log(AuditLogger.Actions.BACKUP_CREATED, "backup", null, "Backup created")
        val data = snapshot()
        return writePackage(out, data, user.displayName)
    }

    private fun writePackage(out: OutputStream, data: BackupData, by: String): BackupManifest {
        val dataBytes = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
        val manifest = BackupManifest(
            schemaVersion = AppDatabase.VERSION,
            appVersion = appVersion,
            createdAt = time.nowMillis(),
            createdBy = by,
            dairyName = data.settings.firstOrNull()?.dairyName ?: "",
            counts = data.counts(),
            dataSha256 = sha256(dataBytes),
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(dataBytes)
            zip.closeEntry()
        }
        return manifest
    }

    /** Parses and fully validates a backup without touching the database. */
    fun read(input: InputStream): BackupPackage {
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST -> manifestBytes = readLimited(zip)
                        DATA -> dataBytes = readLimited(zip)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            throw AppException(R.string.err_backup_corrupt)
        }
        val mb = manifestBytes ?: throw AppException(R.string.err_backup_incomplete)
        val db0 = dataBytes ?: throw AppException(R.string.err_backup_incomplete)
        val manifest = try { json.decodeFromString(BackupManifest.serializer(), mb.toString(Charsets.UTF_8)) } catch (e: Exception) { throw AppException(R.string.err_backup_corrupt) }
        if (manifest.format != BackupManifest.FORMAT) throw AppException(R.string.err_backup_format)
        if (manifest.schemaVersion > AppDatabase.VERSION || manifest.formatVersion > 1) throw AppException(R.string.err_backup_newer)
        if (sha256(db0) != manifest.dataSha256) throw AppException(R.string.err_backup_checksum)
        val data = try { json.decodeFromString(BackupData.serializer(), db0.toString(Charsets.UTF_8)) } catch (e: Exception) { throw AppException(R.string.err_backup_corrupt) }
        if (data.counts() != manifest.counts) throw AppException(R.string.err_backup_incomplete)
        validateIntegrity(data)
        return BackupPackage(manifest, data)
    }

    /** Rejects duplicate IDs and dangling references before anything is written. */
    fun validateIntegrity(d: BackupData) {
        fun <T> unique(list: List<T>, id: (T) -> Any) { if (list.map(id).toSet().size != list.size) throw AppException(R.string.err_backup_integrity) }
        unique(d.users) { it.id }; unique(d.products) { it.id }; unique(d.rates) { it.id }; unique(d.customers) { it.id }
        unique(d.deliveries) { it.id }; unique(d.payments) { it.id }; unique(d.adjustments) { it.id }; unique(d.invoices) { it.id }
        unique(d.invoiceLines) { it.id }; unique(d.periods) { it.periodKey }; unique(d.audit) { it.id }
        unique(d.customers) { it.code.lowercase() }; unique(d.users) { it.username.lowercase() }; unique(d.products) { it.name.lowercase() }
        unique(d.payments) { it.requestId }; unique(d.invoices) { it.invoiceNumber }
        unique(d.deliveries) { "${it.customerId}:${it.productId}:${it.date}:${it.session}" }
        unique(d.rates) { "${it.productId}:${it.effectiveFrom}" }
        unique(d.invoices.filter { it.activeSlot != null }) { it.activeSlot!! }
        val products = d.products.map { it.id }.toSet()
        val customers = d.customers.map { it.id }.toSet()
        val invoices = d.invoices.map { it.id }.toSet()
        val ok = d.rates.all { it.productId in products } &&
            d.customers.all { it.defaultProductId == null || it.defaultProductId in products } &&
            d.deliveries.all { it.customerId in customers && it.productId in products } &&
            d.payments.all { it.customerId in customers } &&
            d.adjustments.all { it.customerId in customers } &&
            d.invoices.all { it.customerId in customers } &&
            d.invoiceLines.all { it.invoiceId in invoices }
        if (!ok) throw AppException(R.string.err_backup_integrity)
        if (d.users.none { it.role == com.khodiyar.dairybilling.domain.model.UserRole.ADMIN && it.active }) throw AppException(R.string.err_backup_no_admin)
    }

    /**
     * Replaces all data with the package contents in one transaction. A safety copy of the current
     * data is saved to [safetyDir] first. Returns the safety backup file.
     */
    suspend fun restore(pkg: BackupPackage, safetyDir: File): File {
        val user = session.require(Permission.RESTORE)
        validateIntegrity(pkg.data)
        safetyDir.mkdirs()
        val safety = File(safetyDir, "safety_before_restore_${time.nowMillis()}.zip")
        val current = snapshot()
        safety.outputStream().use { writePackage(it, current, user.displayName) }
        // Re-read the safety file to prove it is restorable before destroying anything.
        safety.inputStream().use { read(it) }
        val d = pkg.data
        db.withTransaction {
            db.invoices().deleteAllLines(); db.invoices().deleteAll()
            db.deliveries().deleteAll(); db.payments().deleteAll(); db.adjustments().deleteAll()
            db.rates().deleteAll(); db.customers().deleteAll(); db.products().deleteAll()
            db.periods().deleteAll(); db.audit().deleteAll(); db.users().deleteAll(); db.settings().deleteAll()

            db.settings().insertAll(d.settings); db.users().insertAll(d.users); db.products().insertAll(d.products)
            db.rates().insertAll(d.rates); db.customers().insertAll(d.customers); db.deliveries().insertAll(d.deliveries)
            db.payments().insertAll(d.payments); db.adjustments().insertAll(d.adjustments); db.invoices().insertAll(d.invoices)
            db.invoices().insertAllLines(d.invoiceLines); db.periods().insertAll(d.periods); db.audit().insertAll(d.audit)
            audit.log(AuditLogger.Actions.BACKUP_RESTORED, "backup", null,
                "Restored backup of ${pkg.manifest.createdAt} (${pkg.manifest.counts["customers"]} customers). Safety copy: ${safety.name}")
        }
        return safety
    }

    fun listSafetyBackups(dir: File): List<File> = dir.listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }.orEmpty()

    private fun readLimited(stream: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw AppException(R.string.err_backup_too_large)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val MANIFEST = "manifest.json"
        const val DATA = "data.json"
        const val MAX_ENTRY_BYTES = 300L * 1024 * 1024

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
