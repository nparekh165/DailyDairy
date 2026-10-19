package com.khodiyar.dairybilling

import android.content.Context
import com.khodiyar.dairybilling.backup.BackupManager
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.prefs.AppPreferences
import com.khodiyar.dairybilling.data.repo.AuditLogger
import com.khodiyar.dairybilling.data.repo.AuthRepository
import com.khodiyar.dairybilling.data.repo.BillingRepository
import com.khodiyar.dairybilling.data.repo.CustomerRepository
import com.khodiyar.dairybilling.data.repo.DeliveryRepository
import com.khodiyar.dairybilling.data.repo.PaymentRepository
import com.khodiyar.dairybilling.data.repo.ProductRepository
import com.khodiyar.dairybilling.data.repo.ReportRepository
import com.khodiyar.dairybilling.data.repo.SettingsRepository
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.time.SystemTimeProvider
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.KeystoreCipher
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.LocalDate

enum class MessageKind { INFO, SUCCESS, ERROR }

data class UserMessage(val text: UiText, val kind: MessageKind = MessageKind.INFO)

/** Manual dependency container: one instance of each service for the whole process. */
class AppContainer(context: Context, val time: TimeProvider = SystemTimeProvider, dbName: String? = AppDatabase.NAME) {
    val appContext: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.build(appContext, dbName)
    val session = SessionManager(time)
    val prefs = AppPreferences(appContext, KeystoreCipher())
    val audit = AuditLogger(db.audit(), session, time)
    val auth = AuthRepository(db, session, audit, time)
    val settings = SettingsRepository(db, session, audit, time)
    val customers = CustomerRepository(db, session, audit, time)
    val products = ProductRepository(db, session, audit, time)
    val deliveries = DeliveryRepository(db, session, audit, time)
    val payments = PaymentRepository(db, session, audit, time)
    val billing = BillingRepository(db, session, audit, time)
    val reports = ReportRepository(db, session, billing, time)
    val backup = BackupManager(db, session, audit, time, BuildConfig.VERSION_NAME)
    val safetyBackupDir: File get() = File(appContext.filesDir, "safety_backups")

    /** App-wide snackbar channel. */
    val messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Delivery date chosen in the delivery screen persists for the whole session. */
    val deliveryDate = MutableStateFlow<LocalDate>(time.today())

    fun notify(text: UiText, kind: MessageKind = MessageKind.INFO) {
        messages.tryEmit(UserMessage(text, kind))
    }
}
