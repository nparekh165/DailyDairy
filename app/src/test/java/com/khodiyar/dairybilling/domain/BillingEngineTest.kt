package com.khodiyar.dairybilling.domain

import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.billing.LedgerAdjustment
import com.khodiyar.dairybilling.domain.billing.LedgerDelivery
import com.khodiyar.dairybilling.domain.billing.LedgerPayment
import com.khodiyar.dairybilling.domain.billing.PaidState
import com.khodiyar.dairybilling.domain.billing.ProductInfo
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillingEngineTest {
    private val milk = ProductInfo(1, "Milk", "L", 0)
    private val curd = ProductInfo(2, "Curd", "kg", 1)
    private val products = mapOf(1L to milk, 2L to curd)
    private val oct = YearMonth.of(2026, 10)

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun milk(date: Long, qtyMilli: Long, rate: Long = 6000) =
        LedgerDelivery(date, 1, DeliveryStatus.DELIVERED, qtyMilli, rate, BillingEngine.lineAmount(qtyMilli, rate))

    @Test fun oneDelivery() {
        val r = BillingEngine.compute(oct, 0, listOf(milk(day(2026, 10, 5), 2000)), emptyList(), emptyList(), products)
        assertEquals(1, r.lines.size)
        assertEquals(12000, r.summary.chargesPaise)
        assertEquals(12000, r.summary.closingBalancePaise)
        assertEquals(1, r.lines[0].deliveryDays)
    }

    @Test fun multipleDeliveriesInMonth() {
        val d = (1..31).map { milk(day(2026, 10, it), 1500) }
        val r = BillingEngine.compute(oct, 0, d, emptyList(), emptyList(), products)
        assertEquals(46_500, r.lines.single().quantityMilli)
        assertEquals(31 * 9000L, r.summary.chargesPaise)
        assertEquals(31, r.lines.single().deliveryDays)
    }

    @Test fun fractionalQuantitiesAreNotDiscarded() {
        val d = listOf(milk(day(2026, 10, 1), 250, 6100), milk(day(2026, 10, 2), 333, 6100))
        val r = BillingEngine.compute(oct, 0, d, emptyList(), emptyList(), products)
        assertEquals(583, r.lines.single().quantityMilli)
        assertEquals(1525 + 2031L, r.summary.chargesPaise)
    }

    @Test fun multipleProducts() {
        val d = listOf(
            milk(day(2026, 10, 1), 2000),
            LedgerDelivery(day(2026, 10, 1), 2, DeliveryStatus.DELIVERED, 500, 8000, 4000),
        )
        val r = BillingEngine.compute(oct, 0, d, emptyList(), emptyList(), products)
        assertEquals(listOf("Milk", "Curd"), r.lines.map { it.productName })
        assertEquals(16000, r.summary.chargesPaise)
    }

    @Test fun differentRatesDuringMonthBecomeSeparateLines() {
        val d = (1..10).map { milk(day(2026, 10, it), 1000, 6000) } + (11..31).map { milk(day(2026, 10, it), 1000, 6400) }
        val r = BillingEngine.compute(oct, 0, d, emptyList(), emptyList(), products)
        assertEquals(2, r.lines.size)
        assertEquals(6000, r.lines[0].ratePaise)
        assertEquals(60_000, r.lines[0].amountPaise)
        assertEquals(6400, r.lines[1].ratePaise)
        assertEquals(21 * 6400L, r.lines[1].amountPaise)
        assertEquals(60_000 + 21 * 6400L, r.summary.chargesPaise)
    }

    @Test fun partialPayment() {
        val r = BillingEngine.compute(oct, 0, listOf(milk(day(2026, 10, 1), 10_000)), emptyList(),
            listOf(LedgerPayment(day(2026, 10, 20), 40_000)), products)
        assertEquals(60_000, r.summary.chargesPaise)
        assertEquals(40_000, r.summary.paymentsPaise)
        assertEquals(20_000, r.summary.closingBalancePaise)
    }

    @Test fun fullPayment() {
        val r = BillingEngine.compute(oct, 0, listOf(milk(day(2026, 10, 1), 10_000)), emptyList(),
            listOf(LedgerPayment(day(2026, 10, 31), 60_000)), products)
        assertEquals(0, r.summary.closingBalancePaise)
    }

    @Test fun previousOutstandingBalanceCarriesForward() {
        // Opening ₹500 owed; September charges ₹600, September payment ₹300 -> previous for October = ₹800
        val history = listOf(milk(day(2026, 9, 10), 10_000), milk(day(2026, 10, 3), 2000))
        val pays = listOf(LedgerPayment(day(2026, 9, 30), 30_000))
        val r = BillingEngine.compute(oct, 50_000, history, emptyList(), pays, products)
        assertEquals(80_000, r.summary.previousBalancePaise)
        assertEquals(12_000, r.summary.chargesPaise)
        assertEquals(0, r.summary.paymentsPaise)
        assertEquals(92_000, r.summary.closingBalancePaise)
        // November previous balance equals October closing (carry-forward).
        val nov = BillingEngine.compute(YearMonth.of(2026, 11), 50_000, history, emptyList(), pays, products)
        assertEquals(r.summary.closingBalancePaise, nov.summary.previousBalancePaise)
    }

    @Test fun creditAndDebitAdjustments() {
        val r = BillingEngine.compute(
            oct, 0, listOf(milk(day(2026, 10, 1), 10_000)),
            listOf(LedgerAdjustment(day(2026, 10, 15), -5_000), LedgerAdjustment(day(2026, 10, 16), 1_000), LedgerAdjustment(day(2026, 10, 17), -999_00, active = false)),
            emptyList(), products,
        )
        assertEquals(-4_000, r.summary.adjustmentsPaise)
        assertEquals(56_000, r.summary.closingBalancePaise)
    }

    @Test fun monthAndYearBoundaries() {
        val d = listOf(
            milk(day(2026, 12, 31), 1000), // December 2026
            milk(day(2027, 1, 1), 2000), // January 2027
            milk(day(2027, 1, 31), 3000),
            milk(day(2027, 2, 1), 4000), // February
        )
        val pays = listOf(LedgerPayment(day(2026, 12, 31), 1000), LedgerPayment(day(2027, 1, 1), 2000))
        val jan = BillingEngine.compute(YearMonth.of(2027, 1), 0, d, emptyList(), pays, products)
        assertEquals(6000 - 1000L, jan.summary.previousBalancePaise)
        assertEquals(30_000, jan.summary.chargesPaise) // 5 L x ₹60 = ₹300
        assertEquals(2000, jan.summary.paymentsPaise)
        assertEquals(2, jan.lines.single().deliveryDays)
        val dec = BillingEngine.compute(YearMonth.of(2026, 12), 0, d, emptyList(), pays, products)
        assertEquals(6000, dec.summary.chargesPaise)
        assertEquals(dec.summary.closingBalancePaise, jan.summary.previousBalancePaise)
    }

    @Test fun leapYearFebruary() {
        val d = listOf(milk(day(2028, 2, 29), 1000), milk(day(2028, 3, 1), 1000))
        val feb = BillingEngine.compute(YearMonth.of(2028, 2), 0, d, emptyList(), emptyList(), products)
        assertEquals(6000, feb.summary.chargesPaise)
    }

    @Test fun zeroDeliveryCustomer() {
        val r = BillingEngine.compute(oct, 25_000, emptyList(), emptyList(), emptyList(), products)
        assertEquals(0, r.lines.size)
        assertEquals(0, r.summary.chargesPaise)
        assertEquals(25_000, r.summary.previousBalancePaise)
        assertEquals(25_000, r.summary.closingBalancePaise)
    }

    @Test fun noDeliveryAndMissedAreNotBilled() {
        val d = listOf(
            milk(day(2026, 10, 1), 1000),
            LedgerDelivery(day(2026, 10, 2), 1, DeliveryStatus.NO_DELIVERY, 0, 6000, 0),
            LedgerDelivery(day(2026, 10, 3), 1, DeliveryStatus.MISSED, 0, 6000, 0),
        )
        val r = BillingEngine.compute(oct, 0, d, emptyList(), emptyList(), products)
        assertEquals(6000, r.summary.chargesPaise)
        assertEquals(1, r.lines.single().deliveryDays)
    }

    @Test fun reversedPaymentsNeverCount() {
        val pays = listOf(LedgerPayment(day(2026, 10, 2), 10_000, active = false), LedgerPayment(day(2026, 10, 3), 5_000))
        val r = BillingEngine.compute(oct, 0, listOf(milk(day(2026, 10, 1), 10_000)), emptyList(), pays, products)
        assertEquals(5_000, r.summary.paymentsPaise)
        assertEquals(55_000, r.summary.closingBalancePaise)
    }

    @Test fun paymentsAreNeverDoubleCountedAcrossMonths() {
        val d = (1..3).flatMap { m -> listOf(milk(day(2026, m, 5), 10_000)) }
        val pays = listOf(LedgerPayment(day(2026, 1, 20), 60_000), LedgerPayment(day(2026, 2, 20), 30_000))
        val totalPaid = (1..3).sumOf { m -> BillingEngine.compute(YearMonth.of(2026, m), 0, d, emptyList(), pays, products).summary.paymentsPaise }
        assertEquals(90_000, totalPaid)
        val march = BillingEngine.compute(YearMonth.of(2026, 3), 0, d, emptyList(), pays, products)
        assertEquals(BillingEngine.outstanding(0, 180_000, 0, 90_000), march.summary.closingBalancePaise)
    }

    @Test fun advancePaymentProducesCredit() {
        val r = BillingEngine.compute(oct, 0, listOf(milk(day(2026, 10, 1), 1000)), emptyList(), listOf(LedgerPayment(day(2026, 10, 1), 10_000)), products)
        assertEquals(-4_000, r.summary.closingBalancePaise)
        assertEquals(PaidState.CREDIT, BillingEngine.paidState(r.summary.closingBalancePaise, 0))
    }

    @Test fun paidStateFifo() {
        assertEquals(PaidState.UNPAID, BillingEngine.paidState(10_000, 0))
        assertEquals(PaidState.PARTIALLY_PAID, BillingEngine.paidState(10_000, 4_000))
        assertEquals(PaidState.PAID, BillingEngine.paidState(10_000, 10_000))
        assertEquals(PaidState.SETTLED, BillingEngine.paidState(0, 0))
    }

    @Test fun invoiceNumberFormat() {
        assertEquals("KD-2026-10-0001", BillingEngine.invoiceNumber("kd", oct, 1))
        assertEquals("KD-2026-10-0042-R2", BillingEngine.invoiceNumber("KD", oct, 42, 2))
        assertEquals("INV-2026-10-0001", BillingEngine.invoiceNumber("--", oct, 1))
    }
}
