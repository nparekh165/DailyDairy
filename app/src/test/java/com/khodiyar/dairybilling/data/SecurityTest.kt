package com.khodiyar.dairybilling.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khodiyar.dairybilling.data.repo.LoginResult
import com.khodiyar.dairybilling.data.repo.PaymentInput
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UnauthorizedException
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.security.Permission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SecurityTest {
    private lateinit var env: TestEnv

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    private inline fun expectUnauthorized(block: () -> Unit) {
        try { block(); fail("expected UnauthorizedException") } catch (e: UnauthorizedException) { }
    }

    @Test fun firstRunSetupCreatesAdmin_noDefaultAccount() = runBlocking {
        assertTrue(env.auth.needsSetup())
        assertEquals(LoginResult.InvalidCredentials, env.auth.login("admin", "admin"))
        env.setupAdmin()
        assertFalse(env.auth.needsSetup())
        try { env.setupAdmin(); fail("setup ran twice") } catch (e: AppException) { }
    }

    @Test fun weakPinRejectedAtSetup() = runBlocking {
        try {
            env.auth.completeSetup("D", "", "", "Owner", "owner", "123456", false)
            fail("weak PIN accepted")
        } catch (e: AppException) { }
        assertTrue(env.auth.needsSetup())
    }

    @Test fun pinIsNeverStoredInPlainText() = runBlocking {
        env.setupAdmin()
        val u = env.db.users().findByUsername("owner")!!
        assertFalse(u.pinHash.contains("milk@2026"))
        assertFalse(u.pinSalt.contains("milk@2026"))
        val raw = env.db.openHelper.readableDatabase.query("SELECT * FROM users").use { c ->
            buildString { while (c.moveToNext()) for (i in 0 until c.columnCount) append(runCatching { c.getString(i) }.getOrNull()) }
        }
        assertFalse(raw.contains("milk@2026"))
    }

    @Test fun invalidLoginAndLockout() = runBlocking {
        env.setupAdmin()
        env.session.logout()
        assertEquals(LoginResult.InvalidCredentials, env.auth.login("owner", "wrong-pin"))
        assertEquals(LoginResult.InvalidCredentials, env.auth.login("nobody", "milk@2026"))
        repeat(3) { env.auth.login("owner", "wrong-pin") }
        val r = env.auth.login("owner", "wrong-pin")
        assertTrue("5th failure locks the account", r is LoginResult.LockedOut)
        // Even the right PIN is refused while locked.
        assertTrue(env.auth.login("owner", "milk@2026") is LoginResult.LockedOut)
        assertNull(env.session.current)
        env.time.extraMillis += 10 * 60_000
        assertTrue(env.auth.login("OWNER", "milk@2026") is LoginResult.Success)
        assertEquals(0, env.db.users().findByUsername("owner")!!.failedAttempts)
    }

    @Test fun logoutEndsSession() = runBlocking {
        env.setupAdmin()
        assertTrue(env.session.has(Permission.MANAGE_USERS))
        env.auth.logout()
        assertNull(env.session.current)
        try { env.customers.create(com.khodiyar.dairybilling.data.repo.CustomerInput("C-1", "A", "1", "", "", "", 0, null, 0)); fail() } catch (e: UnauthorizedException) { }
    }

    @Test fun autoLockExpiresIdleSession() = runBlocking {
        env.setupAdmin()
        env.session.autoLockMinutes = 15
        env.time.extraMillis += 16 * 60_000
        assertTrue(env.session.expireIfIdle())
        assertNull(env.session.current)
    }

    @Test fun staffCannotPerformAdminOnlyOperations() = runBlocking {
        env.setupAdmin()
        val milk = env.milk()
        val cust = env.customer("C-1")
        env.auth.createStaff("Ravi", "ravi", "deliver#1", setOf(Permission.RESTORE, Permission.MANAGE_USERS))
        env.auth.logout()
        assertTrue(env.auth.login("ravi", "deliver#1") is LoginResult.Success)
        assertFalse("admin-only perms are ignored for staff", env.session.has(Permission.RESTORE))

        expectUnauthorized { env.products.addRate(milk, 7000, env.day(2026, 10, 19), "", false) }
        expectUnauthorized { env.products.create("Ghee", ProductUnit.KILOGRAM, "", 500, 50000, 0) }
        expectUnauthorized { env.auth.createStaff("X", "xyz", "abc#1234", emptySet()) }
        expectUnauthorized { env.billing.finalize(cust, YearMonth.of(2026, 10)) }
        expectUnauthorized { env.billing.lock(YearMonth.of(2026, 9)) }
        expectUnauthorized { env.payments.record(PaymentInput(cust, env.day(2026, 10, 19), 100, PaymentMethod.CASH, "", "r1")) }
        expectUnauthorized { env.settings.update(env.settings.get()) }
        expectUnauthorized { env.backup.createBackup(java.io.ByteArrayOutputStream()) }
        val pkg = run {
            // Admin-made backup, then try to restore as staff.
            env.backup.validateIntegrity(env.backup.snapshot())
        }
        assertEquals(Unit, pkg)
        expectUnauthorized { env.backup.restore(com.khodiyar.dairybilling.backup.BackupPackage(
            com.khodiyar.dairybilling.backup.BackupManifest(schemaVersion = 2, appVersion = "t", createdAt = 0, createdBy = "", dairyName = "", counts = emptyMap(), dataSha256 = ""),
            env.backup.snapshot()), env.context.cacheDir) }
        // Staff may record today's deliveries.
        val r = env.deliveries.saveBatch(listOf(com.khodiyar.dairybilling.data.repo.DeliveryDraft(cust, milk, env.day(2026, 10, 19),
            com.khodiyar.dairybilling.domain.model.DeliverySession.MORNING, com.khodiyar.dairybilling.domain.model.DeliveryStatus.DELIVERED, 1000)))
        assertEquals(1, r.created)
        // ...but not past dates without the permission.
        try {
            env.deliveries.saveBatch(listOf(com.khodiyar.dairybilling.data.repo.DeliveryDraft(cust, milk, env.day(2026, 10, 18),
                com.khodiyar.dairybilling.domain.model.DeliverySession.MORNING, com.khodiyar.dairybilling.domain.model.DeliveryStatus.DELIVERED, 1000)))
            fail("past entry allowed")
        } catch (e: AppException) { }
    }

    @Test fun grantedStaffPermissionWorks() = runBlocking {
        env.setupAdmin()
        val cust = env.customer("C-1")
        env.auth.createStaff("Ravi", "ravi", "deliver#1", setOf(Permission.RECORD_PAYMENTS))
        env.auth.logout()
        env.auth.login("ravi", "deliver#1")
        val r = env.payments.record(PaymentInput(cust, env.day(2026, 10, 19), 10_000, PaymentMethod.UPI, "", "req-a"))
        assertTrue(r is com.khodiyar.dairybilling.data.repo.PaymentSaveResult.Saved)
        expectUnauthorized { env.payments.reverse((r as com.khodiyar.dairybilling.data.repo.PaymentSaveResult.Saved).payment.id, "mistake") }
    }

    @Test fun reauthVerifiesCurrentUserPin() = runBlocking {
        env.setupAdmin()
        assertTrue(env.auth.verifyCurrentUser("milk@2026"))
        assertFalse(env.auth.verifyCurrentUser("nope"))
    }

    @Test fun lastAdminCannotBeDeactivated() = runBlocking {
        val admin = env.setupAdmin()
        try { env.auth.updateStaff(admin.id, "Owner", emptySet(), false); fail() } catch (e: AppException) { }
    }

    @Test fun sensitiveChangesAreAudited() = runBlocking {
        env.setupAdmin()
        val milk = env.milk()
        env.products.addRate(milk, 6400, env.day(2026, 11, 1), "Nov hike", false)
        val actions = env.db.audit().all().map { it.action }
        assertTrue(actions.contains("SETUP"))
        assertTrue(actions.contains("PRODUCT_CREATED"))
        assertTrue(actions.count { it == "RATE_ADDED" } == 2)
    }
}
