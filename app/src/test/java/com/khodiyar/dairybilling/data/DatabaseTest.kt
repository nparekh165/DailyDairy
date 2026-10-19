package com.khodiyar.dairybilling.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.ProductRateEntity
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.ProductUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var env: TestEnv
    private val now = 1_700_000_000_000L

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    private suspend fun product(name: String = "Milk") =
        env.db.products().insert(ProductEntity(name = name, unit = ProductUnit.LITER, stepMilli = 500, createdAt = now, updatedAt = now))

    private suspend fun customer(code: String) =
        env.db.customers().insert(CustomerEntity(code = code, name = "N $code", houseNo = code, createdAt = now, updatedAt = now))

    private fun entry(c: Long, p: Long, date: Long = 20_000, session: DeliverySession = DeliverySession.MORNING) =
        DeliveryEntryEntity(customerId = c, productId = p, date = date, session = session, status = DeliveryStatus.DELIVERED,
            quantityMilli = 1000, ratePaise = 6000, amountPaise = 6000, createdBy = null, createdAt = now, updatedBy = null, updatedAt = now)

    @Test fun crud() = runBlocking {
        val id = customer("C-1")
        val c = env.db.customers().get(id)!!
        assertEquals("C-1", c.code)
        env.db.customers().update(c.copy(name = "Renamed"))
        assertEquals("Renamed", env.db.customers().get(id)!!.name)
        assertEquals(1, env.db.customers().all().size)
        val pid = product()
        val eid = env.db.deliveries().insert(entry(id, pid))
        assertEquals(1000, env.db.deliveries().get(eid)!!.quantityMilli)
        env.db.deliveries().delete(eid)
        assertNull(env.db.deliveries().get(eid))
    }

    @Test fun foreignKeysAreEnforced() = runBlocking {
        try {
            env.db.deliveries().insert(entry(999, 999))
            fail("expected FK violation")
        } catch (e: SQLiteConstraintException) { }
        val c = customer("C-1")
        val p = product()
        env.db.deliveries().insert(entry(c, p))
        // RESTRICT: a customer with history cannot be physically deleted.
        try {
            env.db.openHelper.writableDatabase.execSQL("DELETE FROM customers WHERE id = $c")
            fail("expected RESTRICT")
        } catch (e: SQLiteConstraintException) { }
        assertEquals(1, env.db.customers().all().size)
    }

    @Test fun uniqueCustomerCodeIsCaseInsensitive() = runBlocking {
        customer("C-1")
        try {
            customer("c-1")
            fail("duplicate code accepted")
        } catch (e: SQLiteConstraintException) { }
    }

    @Test fun duplicateDeliveryKeyRejected_butOtherSessionAllowed() = runBlocking {
        val c = customer("C-1")
        val p = product()
        env.db.deliveries().insert(entry(c, p))
        try {
            env.db.deliveries().insert(entry(c, p))
            fail("duplicate entry accepted")
        } catch (e: SQLiteConstraintException) { }
        env.db.deliveries().insert(entry(c, p, session = DeliverySession.EVENING))
        assertEquals(2, env.db.deliveries().all().size)
    }

    @Test fun duplicateRateDateRejected() = runBlocking {
        val p = product()
        env.db.rates().insert(ProductRateEntity(productId = p, ratePaise = 6000, effectiveFrom = 100, createdAt = now))
        try {
            env.db.rates().insert(ProductRateEntity(productId = p, ratePaise = 6200, effectiveFrom = 100, createdAt = now))
            fail("duplicate rate accepted")
        } catch (e: SQLiteConstraintException) { }
        assertEquals(6000, env.db.rates().rateOn(p, 150)!!.ratePaise)
        assertNull(env.db.rates().rateOn(p, 99))
    }

    @Test fun paymentRequestIdUnique() = runBlocking {
        val c = customer("C-1")
        val p = PaymentEntity(customerId = c, date = 1, amountPaise = 100, method = PaymentMethod.CASH, receivedBy = null, createdAt = now, requestId = "req-1")
        env.db.payments().insert(p)
        try {
            env.db.payments().insert(p.copy(id = 0))
            fail("duplicate payment accepted")
        } catch (e: SQLiteConstraintException) { }
    }

    @Test fun transactionRollsBackCompletely() = runBlocking {
        val c = customer("C-1")
        val p = product()
        try {
            env.db.withTransaction {
                env.db.deliveries().insert(entry(c, p, date = 1))
                env.db.deliveries().insert(entry(c, p, date = 2))
                env.db.deliveries().insert(entry(c, p, date = 1)) // duplicate -> whole batch fails
            }
            fail("expected failure")
        } catch (e: SQLiteConstraintException) { }
        assertTrue(env.db.deliveries().all().isEmpty())
    }
}
