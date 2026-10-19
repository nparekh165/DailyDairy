package com.khodiyar.dairybilling.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khodiyar.dairybilling.data.repo.BillState
import com.khodiyar.dairybilling.data.repo.DeliveryDraft
import com.khodiyar.dairybilling.data.repo.PaymentInput
import com.khodiyar.dairybilling.data.repo.PaymentSaveResult
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.billing.PaidState
import com.khodiyar.dairybilling.domain.model.AdjustmentType
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.InvoiceStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.RecordStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BusinessFlowTest {
    private lateinit var env: TestEnv
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)

    @Before fun setUp() {
        env = TestEnv()
        env.time.date = LocalDate.of(2026, 9, 1)
        env.setupAdmin()
    }

    @After fun tearDown() { env.close() }

    private fun d(day: Long, c: Long, p: Long, qty: Long, status: DeliveryStatus = DeliveryStatus.DELIVERED, expected: Long? = null) =
        DeliveryDraft(c, p, day, DeliverySession.MORNING, status, if (status == DeliveryStatus.DELIVERED) qty else 0, expectedUpdatedAt = expected)

    @Test fun deliveryStoresRateSnapshot_rateChangeDoesNotAlterHistory() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val c = env.customer("C-1")
        env.time.date = LocalDate.of(2026, 9, 30)
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 10), c, milk, 2000), d(env.day(2026, 9, 11), c, milk, 1500)))
        env.products.addRate(milk, 6400, env.day(2026, 9, 11), "", repriceUnlocked = false)
        val entries = env.db.deliveries().all().sortedBy { it.date }
        assertEquals(listOf(6000L, 6000L), entries.map { it.ratePaise })
        assertEquals(listOf(12000L, 9000L), entries.map { it.amountPaise })
        // New entries after the change use the new rate.
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 12), c, milk, 1000)))
        assertEquals(6400, env.db.deliveries().all().first { it.date == env.day(2026, 9, 12) }.ratePaise)
    }

    @Test fun backdatedRateCanRepriceUnlockedEntriesOnly() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 8, 1))
        val c = env.customer("C-1")
        env.time.date = LocalDate.of(2026, 10, 5)
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 30), c, milk, 1000), d(env.day(2026, 10, 2), c, milk, 1000)))
        env.billing.lock(sep)
        val n = env.products.addRate(milk, 6200, env.day(2026, 9, 1), "", repriceUnlocked = true)
        assertEquals(1, n)
        val byDate = env.db.deliveries().all().associateBy { it.date }
        assertEquals(6000, byDate[env.day(2026, 9, 30)]!!.ratePaise) // locked September untouched
        assertEquals(6200, byDate[env.day(2026, 10, 2)]!!.ratePaise)
    }

    @Test fun batchSave_validatesAndIsAtomic() = runBlocking {
        val milk = env.milk()
        val a = env.customer("C-1")
        val b = env.customer("C-2")
        val today = env.day(2026, 9, 1)
        try {
            env.deliveries.saveBatch(listOf(d(today, a, milk, 1000), d(today, b, milk, 0)))
            fail("zero delivered quantity accepted")
        } catch (e: AppException) { }
        assertTrue("nothing written on failure", env.db.deliveries().all().isEmpty())
        val r = env.deliveries.saveBatch(listOf(d(today, a, milk, 1000), d(today, b, milk, 0, DeliveryStatus.NO_DELIVERY)))
        assertEquals(2, r.created)
        // Duplicate save of an unchanged row is a no-op, not a second row.
        val existing = env.db.deliveries().findKey(a, milk, today, DeliverySession.MORNING)!!
        val r2 = env.deliveries.saveBatch(listOf(d(today, a, milk, 1000, expected = existing.updatedAt)))
        assertEquals(1, r2.unchanged)
        assertEquals(2, env.db.deliveries().all().size)
    }

    @Test fun conflictingEditIsNotSilentlyOverwritten() = runBlocking {
        val milk = env.milk()
        val a = env.customer("C-1")
        val today = env.day(2026, 9, 1)
        env.deliveries.saveBatch(listOf(d(today, a, milk, 1000)))
        // A second screen that never saw the saved entry tries to save a "new" one.
        val r = env.deliveries.saveBatch(listOf(d(today, a, milk, 3000, expected = null)))
        assertEquals(listOf(a), r.conflicts)
        assertEquals(1000, env.db.deliveries().all().single().quantityMilli)
    }

    @Test fun futureDatesAndMissingRatesRejected() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 15))
        val a = env.customer("C-1")
        try { env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 2), a, milk, 1000))); fail() } catch (e: AppException) { }
        try { env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 1), a, milk, 1000))); fail("no rate yet") } catch (e: AppException) { }
    }

    @Test fun editAndDeleteAreAudited() = runBlocking {
        val milk = env.milk()
        val a = env.customer("C-1")
        val today = env.day(2026, 9, 1)
        env.deliveries.saveBatch(listOf(d(today, a, milk, 1000)))
        val e = env.db.deliveries().all().single()
        env.deliveries.saveBatch(listOf(d(today, a, milk, 2500, expected = e.updatedAt)))
        assertEquals(2500, env.db.deliveries().all().single().quantityMilli)
        env.deliveries.saveBatch(emptyList(), listOf(e.id))
        assertTrue(env.db.deliveries().all().isEmpty())
        val actions = env.db.audit().all().map { it.action }
        assertTrue(actions.contains("DELIVERY_UPDATED"))
        assertTrue(actions.contains("DELIVERY_DELETED"))
    }

    @Test fun paymentsIdempotentDuplicateWarningAndReversal() = runBlocking {
        val a = env.customer("C-1", opening = 50_000)
        val input = PaymentInput(a, env.day(2026, 9, 1), 20_000, PaymentMethod.CASH, "", "req-1")
        val first = env.payments.record(input) as PaymentSaveResult.Saved
        assertTrue(env.payments.record(input) is PaymentSaveResult.AlreadySaved)
        assertTrue(env.payments.record(input.copy(requestId = "req-2")) is PaymentSaveResult.PossibleDuplicate)
        assertTrue(env.payments.record(input.copy(requestId = "req-2"), confirmDuplicate = true) is PaymentSaveResult.Saved)
        assertEquals(10_000L, env.billing.outstandingAll()[a])
        env.payments.reverse(first.payment.id, "Counted twice")
        assertEquals(30_000L, env.billing.outstandingAll()[a])
        val p = env.db.payments().get(first.payment.id)!!
        assertEquals(RecordStatus.REVERSED, p.status)
        assertEquals("Counted twice", p.reversalReason)
        try { env.payments.reverse(first.payment.id, "again"); fail() } catch (e: AppException) { }
    }

    @Test fun monthlyInvoice_finalize_carryForward_paidState() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1", opening = 10_000, productId = milk, qty = 1000)
        env.time.date = LocalDate.of(2026, 9, 30)
        env.deliveries.saveBatch((1..30).map { d(env.day(2026, 9, it), a, milk, 1000) })
        env.payments.record(PaymentInput(a, env.day(2026, 9, 15), 50_000, PaymentMethod.UPI, "UTR1", "p1"))
        env.time.date = LocalDate.of(2026, 10, 2)

        val view = env.billing.invoiceView(a, sep)!!
        assertEquals(BillState.DRAFT, view.state)
        assertEquals(10_000, view.live.summary.previousBalancePaise)
        assertEquals(180_000, view.live.summary.chargesPaise)
        assertEquals(50_000, view.live.summary.paymentsPaise)
        assertEquals(140_000, view.live.summary.closingBalancePaise)

        val inv = env.billing.finalize(a, sep)
        assertEquals("KD-2026-09-0001", inv.invoiceNumber)
        assertEquals(140_000, inv.closingBalancePaise)
        try { env.billing.finalize(a, sep); fail("double finalize") } catch (e: AppException) { }

        // October: previous balance is September's closing (carry forward).
        env.deliveries.saveBatch(listOf(d(env.day(2026, 10, 1), a, milk, 2000)))
        val octView = env.billing.invoiceView(a, oct)!!
        assertEquals(140_000, octView.live.summary.previousBalancePaise)

        // Partial then full payment of September's invoice (FIFO).
        env.payments.record(PaymentInput(a, env.day(2026, 10, 2), 100_000, PaymentMethod.CASH, "", "p2"))
        assertEquals(PaidState.PARTIALLY_PAID, env.billing.invoiceView(a, sep)!!.paidState)
        env.payments.record(PaymentInput(a, env.day(2026, 10, 2), 40_000, PaymentMethod.CASH, "", "p3"), confirmDuplicate = true)
        assertEquals(PaidState.PAID, env.billing.invoiceView(a, sep)!!.paidState)
        // Finalized snapshot stays unchanged by later payments.
        assertEquals(140_000, env.db.invoices().get(inv.id)!!.closingBalancePaise)
    }

    @Test fun correctionAfterFinalize_requiresRevision_numbersNeverReused() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1")
        val b = env.customer("C-2")
        env.time.date = LocalDate.of(2026, 9, 30)
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 5), a, milk, 1000), d(env.day(2026, 9, 5), b, milk, 1000)))
        env.time.date = LocalDate.of(2026, 10, 1)
        assertEquals(2, env.billing.finalizeAll(sep))
        val original = env.db.invoices().activeFor(a, 202609)!!
        // Admin corrects a September entry (allowed while unlocked); invoice is flagged.
        val e = env.db.deliveries().findKey(a, milk, env.day(2026, 9, 5), DeliverySession.MORNING)!!
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 5), a, milk, 2000, expected = e.updatedAt)))
        assertEquals(BillState.NEEDS_REVISION, env.billing.invoiceView(a, sep)!!.state)
        assertEquals(6000, env.db.invoices().get(original.id)!!.chargesPaise) // never silently rewritten
        val rev = env.billing.revise(original.id, "Quantity corrected")
        assertEquals(original.invoiceNumber + "-R1", rev.invoiceNumber)
        assertEquals(12000, rev.chargesPaise)
        assertEquals(InvoiceStatus.SUPERSEDED, env.db.invoices().get(original.id)!!.status)
        assertEquals(BillState.FINALIZED, env.billing.invoiceView(a, sep)!!.state)
        val numbers = env.db.invoices().all().map { it.invoiceNumber }
        assertEquals(numbers.size, numbers.toSet().size)
        assertEquals(3, numbers.size)
    }

    @Test fun lockedPeriodBlocksChanges_reopenIsAudited() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1")
        env.time.date = LocalDate.of(2026, 10, 3)
        env.billing.lock(sep)
        try { env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 20), a, milk, 1000))); fail() } catch (e: AppException) { }
        try { env.payments.record(PaymentInput(a, env.day(2026, 9, 20), 100, PaymentMethod.CASH, "", "x")); fail() } catch (e: AppException) { }
        try { env.payments.addAdjustment(a, env.day(2026, 9, 20), AdjustmentType.CREDIT, 100, "x"); fail() } catch (e: AppException) { }
        try { env.billing.finalize(a, sep); fail() } catch (e: AppException) { }
        try { env.billing.reopen(sep, ""); fail("reason required") } catch (e: AppException) { }
        env.billing.reopen(sep, "Late entry from route 2")
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 20), a, milk, 1000)))
        val reopen = env.db.audit().all().single { it.action == "PERIOD_REOPENED" }
        assertTrue(reopen.details.contains("Late entry"))
    }

    @Test fun adjustmentsFlowIntoInvoiceAndCanBeVoided() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1")
        env.time.date = LocalDate.of(2026, 9, 30)
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 5), a, milk, 10_000)))
        val adj = env.payments.addAdjustment(a, env.day(2026, 9, 30), AdjustmentType.CREDIT, 5_000, "Festival discount")
        assertEquals(-5_000, env.billing.invoiceView(a, sep)!!.live.summary.adjustmentsPaise)
        assertEquals(55_000, env.billing.invoiceView(a, sep)!!.live.summary.closingBalancePaise)
        env.payments.voidAdjustment(adj)
        assertEquals(60_000, env.billing.invoiceView(a, sep)!!.live.summary.closingBalancePaise)
    }

    @Test fun ledgerAndReportsAgreeWithInvoiceEngine() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1", opening = 2_000)
        env.time.date = LocalDate.of(2026, 10, 10)
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 5), a, milk, 1000), d(env.day(2026, 10, 5), a, milk, 2000)))
        env.payments.record(PaymentInput(a, env.day(2026, 10, 6), 3_000, PaymentMethod.CASH, "", "z"))
        val ledger = env.billing.ledger(a)!!
        val outstanding = env.billing.outstandingAll()[a]!!
        assertEquals(2_000 + 6_000 + 12_000 - 3_000L, outstanding)
        assertEquals(outstanding, ledger.outstandingPaise)
        assertEquals(outstanding, ledger.months.first().summary.closingBalancePaise)
        val statement = env.reports.statement(a, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 10))!!
        assertEquals(2_000, statement.openingPaise)
        assertEquals(outstanding, statement.closingPaise)
        val report = env.reports.range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(12_000, report.salesPaise)
        assertEquals(3_000, report.collectedPaise)
        val ov = env.billing.overview(oct)
        assertEquals(12_000, ov.totalCharges)
        assertEquals(outstanding, ov.rows.single().summary.closingBalancePaise)
    }

    @Test fun customerCodesUniqueAndDeactivationKeepsHistory() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val a = env.customer("C-1")
        try { env.customer("c-1"); fail("duplicate code") } catch (e: AppException) { }
        env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 1), a, milk, 1000)))
        env.customers.setActive(a, false)
        assertEquals(1, env.db.deliveries().all().size)
        assertEquals(6_000L, env.billing.outstandingAll()[a])
        try {
            env.time.date = LocalDate.of(2026, 9, 2)
            env.deliveries.saveBatch(listOf(d(env.day(2026, 9, 2), a, milk, 1000)))
            fail("new entry for inactive customer")
        } catch (e: AppException) { }
        assertNotEquals("C-0001", env.customers.suggestCode().also { })
    }

    @Test fun manyCustomersPerformance() = runBlocking {
        val milk = env.milk(6000, env.day(2026, 9, 1))
        val ids = (1..500).map { env.customer("C-%04d".format(it), "H-$it", productId = milk, qty = 1000) }
        env.time.date = LocalDate.of(2026, 9, 1)
        val start = System.nanoTime()
        val r = env.deliveries.saveBatch(ids.map { d(env.day(2026, 9, 1), it, milk, 1500) })
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(500, r.created)
        assertTrue("500-row batch took $ms ms", ms < 15_000)
        val ov = env.billing.overview(sep)
        assertEquals(500, ov.rows.size)
        assertEquals(500 * 9000L, ov.totalCharges)
    }
}
