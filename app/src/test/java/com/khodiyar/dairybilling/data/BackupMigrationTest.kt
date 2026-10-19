package com.khodiyar.dairybilling.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khodiyar.dairybilling.backup.BackupManager
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.Migrations
import com.khodiyar.dairybilling.data.repo.DeliveryDraft
import com.khodiyar.dairybilling.data.repo.PaymentInput
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupMigrationTest {
    private lateinit var env: TestEnv

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Before fun setUp() {
        env = TestEnv()
        env.time.date = LocalDate.of(2026, 9, 30)
        env.setupAdmin()
    }

    @After fun tearDown() { env.close() }

    private fun seed() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1", opening = 1000)
        val b = env.customer("C-2")
        env.deliveries.saveBatch((1..30).flatMap { day ->
            listOf(a, b).map { DeliveryDraft(it, milk, env.day(2026, 9, day), DeliverySession.MORNING, DeliveryStatus.DELIVERED, 1500) }
        })
        env.payments.record(PaymentInput(a, env.day(2026, 9, 20), 50_000, PaymentMethod.UPI, "UTR", "r1"))
        env.time.date = LocalDate.of(2026, 10, 1)
        env.billing.finalizeAll(YearMonth.of(2026, 9))
        env.billing.lock(YearMonth.of(2026, 9))
    }

    private fun backupBytes(): ByteArray = runBlocking { ByteArrayOutputStream().also { env.backup.createBackup(it) }.toByteArray() }

    @Test fun backupRestoreRoundTripIsLossless() = runBlocking {
        seed()
        val bytes = backupBytes()
        val before = env.backup.snapshot() // includes the BACKUP_CREATED audit entry, like the file
        // Wreck current data, then restore.
        env.time.date = LocalDate.of(2026, 10, 2)
        val c3 = env.customer("C-3")
        env.payments.record(PaymentInput(c3, env.day(2026, 10, 2), 999, PaymentMethod.CASH, "", "junk"))
        val pkg = env.backup.read(ByteArrayInputStream(bytes))
        assertEquals(2, pkg.manifest.counts["customers"])
        val safetyDir = File(env.context.cacheDir, "safety_test").apply { deleteRecursively() }
        val safety = env.backup.restore(pkg, safetyDir)
        assertTrue(safety.exists())
        val after = env.backup.snapshot()
        assertEquals(before.customers, after.customers)
        assertEquals(before.deliveries, after.deliveries)
        assertEquals(before.payments, after.payments)
        assertEquals(before.invoices, after.invoices)
        assertEquals(before.invoiceLines, after.invoiceLines)
        assertEquals(before.rates, after.rates)
        assertEquals(before.users, after.users)
        assertEquals(before.periods, after.periods)
        assertEquals(before.settings, after.settings)
        // Audit history is preserved plus one restore entry.
        assertEquals(before.audit.size + 1, after.audit.size)
        // Safety backup contains the pre-restore state (3 customers) and is itself restorable.
        val safetyPkg = safety.inputStream().use { env.backup.read(it) }
        assertEquals(3, safetyPkg.manifest.counts["customers"])
        // Balances match after restore.
        val a = after.customers.first { it.code == "C-1" }.id
        assertEquals(1000 + 30 * 9000L - 50_000, env.billing.outstandingAll()[a])
    }

    @Test fun corruptAndTamperedBackupsRejected() {
        seed()
        val bytes = backupBytes()
        // Truncated file
        expectError { env.backup.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2))) }
        // Random bytes
        expectError { env.backup.read(ByteArrayInputStream(ByteArray(500) { it.toByte() })) }
        // Data changed without updating the checksum
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { entries[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val tampered = entries.toMutableMap()
        tampered[BackupManager.DATA] = String(entries[BackupManager.DATA]!!).replace("\"amountPaise\":50000", "\"amountPaise\":5").toByteArray()
        expectError { env.backup.read(ByteArrayInputStream(zip(tampered))) }
        // Missing data entry
        expectError { env.backup.read(ByteArrayInputStream(zip(mapOf(BackupManager.MANIFEST to entries[BackupManager.MANIFEST]!!)))) }
        // Newer schema than this app understands
        val newer = entries.toMutableMap()
        newer[BackupManager.MANIFEST] = String(entries[BackupManager.MANIFEST]!!).replace("\"schemaVersion\":${AppDatabase.VERSION}", "\"schemaVersion\":99").toByteArray()
        expectError { env.backup.read(ByteArrayInputStream(zip(newer))) }
    }

    @Test fun integrityCheckRejectsDanglingReferences() = runBlocking {
        seed()
        val snap = env.backup.snapshot()
        val broken = snap.copy(customers = snap.customers.drop(1))
        try { env.backup.validateIntegrity(broken); fail() } catch (e: AppException) { }
        val dup = snap.copy(payments = snap.payments + snap.payments)
        try { env.backup.validateIntegrity(dup); fail() } catch (e: AppException) { }
    }

    private fun expectError(block: () -> Unit) {
        try { block(); fail("expected rejection") } catch (e: AppException) { }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (k, v) -> z.putNextEntry(ZipEntry(k)); z.write(v); z.closeEntry() } }
    }.toByteArray()

    @Test fun migrate1To2_preservesData() {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO products (id, name, unit, unitLabel, stepMilli, active, sortOrder, createdAt, updatedAt) VALUES (1, 'Milk', 'LITER', '', 500, 1, 0, 0, 0)")
            execSQL("INSERT INTO customers (id, code, name, houseNo, mobile, address, notes, openingBalancePaise, defaultProductId, defaultQuantityMilli, active, createdAt, updatedAt) VALUES (7, 'C-7', 'Asha', 'B-12', '', '', '', 1500, 1, 2000, 1, 0, 0)")
            execSQL("INSERT INTO deliveries (customerId, productId, date, session, status, quantityMilli, ratePaise, amountPaise, note, createdBy, createdAt, updatedBy, updatedAt) VALUES (7, 1, 20000, 'MORNING', 'DELIVERED', 2000, 6000, 12000, '', NULL, 0, NULL, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true, *Migrations.ALL)
        db.query("SELECT code, routeOrder, openingBalancePaise FROM customers WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("C-7", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(1500, c.getLong(2))
        }
        db.query("SELECT COUNT(*) FROM deliveries").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.close()
        // The real Room database opens the migrated file with the production migration list.
        val room = Room.databaseBuilder(env.context, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        runBlocking { assertEquals("Asha", room.customers().get(7)!!.name) }
        room.close()
    }
}
