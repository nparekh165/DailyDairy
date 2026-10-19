package com.khodiyar.dairybilling.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khodiyar.dairybilling.backup.BackupManager
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.repo.AuditLogger
import com.khodiyar.dairybilling.data.repo.AuthRepository
import com.khodiyar.dairybilling.data.repo.BillingRepository
import com.khodiyar.dairybilling.data.repo.CustomerInput
import com.khodiyar.dairybilling.data.repo.CustomerRepository
import com.khodiyar.dairybilling.data.repo.DeliveryRepository
import com.khodiyar.dairybilling.data.repo.PaymentRepository
import com.khodiyar.dairybilling.data.repo.ProductRepository
import com.khodiyar.dairybilling.data.repo.ReportRepository
import com.khodiyar.dairybilling.data.repo.SettingsRepository
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

class FakeTime(var date: LocalDate = LocalDate.of(2026, 10, 19)) : TimeProvider {
    private var tick = 0L
    var extraMillis = 0L
    override fun nowMillis(): Long = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + extraMillis + (tick++)
    override fun today(): LocalDate = date
}

/** Wires every repository against an in-memory Room database, exactly like AppContainer. */
class TestEnv {
    val context: Context = ApplicationProvider.getApplicationContext()
    val time = FakeTime()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val session = SessionManager(time)
    val audit = AuditLogger(db.audit(), session, time)
    val auth = AuthRepository(db, session, audit, time, iterations = 1000)
    val settings = SettingsRepository(db, session, audit, time)
    val customers = CustomerRepository(db, session, audit, time)
    val products = ProductRepository(db, session, audit, time)
    val deliveries = DeliveryRepository(db, session, audit, time)
    val payments = PaymentRepository(db, session, audit, time)
    val billing = BillingRepository(db, session, audit, time)
    val reports = ReportRepository(db, session, billing, time)
    val backup = BackupManager(db, session, audit, time, "test")

    fun close() = db.close()

    fun setupAdmin(starter: Boolean = false) = runBlocking {
        auth.completeSetup("Khodiyar Dairy", "9876543210", "Main Road", "Owner", "owner", "milk@2026", starter)
    }

    fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    suspend fun milk(ratePaise: Long = 6000, from: Long = day(2026, 1, 1)): Long =
        products.create("Milk", ProductUnit.LITER, "", 500, ratePaise, from)

    suspend fun customer(code: String, house: String = code, opening: Long = 0, productId: Long? = null, qty: Long = 0): Long =
        customers.create(CustomerInput(code, "Customer $code", house, "", "", "", opening, productId, qty))
}
