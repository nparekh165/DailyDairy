package com.khodiyar.dairybilling.domain.billing

import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import java.time.YearMonth

/*
 * BALANCE POLICY (single source of truth for invoices, ledger, reports and the WhatsApp message)
 *
 *   Opening balance  = what the customer owed when added to the app (negative = advance). It is
 *                      treated as dated before every other ledger entry.
 *   Charges (month)  = sum of line amounts of DELIVERED entries dated inside the calendar month.
 *                      Line amount = quantity x applied rate, rounded HALF_UP to the paisa, per entry.
 *   Adjustments      = explicitly recorded DEBIT (+) / CREDIT (-) entries dated inside the month.
 *   Payments (month) = ACTIVE (not reversed) payments dated inside the month. Reversed payments
 *                      count nowhere.
 *   Previous balance = opening + all charges + adjustments - payments dated before the month.
 *   Closing balance  = previous balance + charges + adjustments - payments (positive = amount due,
 *                      negative = advance/credit carried forward).
 *
 * Every rupee is counted exactly once because each entry belongs to exactly one month by its date.
 * Allocation: payments settle the oldest outstanding amount first (FIFO). A finalized invoice is
 * therefore PAID once payments dated after its month cover its closing balance.
 */

data class LedgerDelivery(
    val date: Long,
    val productId: Long,
    val status: DeliveryStatus,
    val quantityMilli: Long,
    val ratePaise: Long,
    val amountPaise: Long,
)

data class LedgerPayment(val date: Long, val amountPaise: Long, val active: Boolean = true)

data class LedgerAdjustment(val date: Long, val signedPaise: Long, val active: Boolean = true)

data class ProductInfo(val id: Long, val name: String, val unitLabel: String, val sortOrder: Int = 0)

data class InvoiceLine(
    val productId: Long,
    val productName: String,
    val unitLabel: String,
    val ratePaise: Long,
    val quantityMilli: Long,
    val amountPaise: Long,
    val deliveryDays: Int,
)

data class BalanceSummary(
    val previousBalancePaise: Long,
    val chargesPaise: Long,
    val adjustmentsPaise: Long,
    val paymentsPaise: Long,
) {
    val closingBalancePaise: Long get() = previousBalancePaise + chargesPaise + adjustmentsPaise - paymentsPaise
}

data class InvoiceComputation(
    val period: YearMonth,
    val lines: List<InvoiceLine>,
    val summary: BalanceSummary,
)

enum class PaidState { PAID, PARTIALLY_PAID, UNPAID, CREDIT, SETTLED }

object BillingEngine {

    fun lineAmount(quantityMilli: Long, ratePaise: Long): Long = Money.lineAmount(quantityMilli, ratePaise)

    fun summarize(
        openingBalance: Long,
        chargesBefore: Long,
        adjustmentsBefore: Long,
        paymentsBefore: Long,
        chargesInPeriod: Long,
        adjustmentsInPeriod: Long,
        paymentsInPeriod: Long,
    ): BalanceSummary = BalanceSummary(
        previousBalancePaise = openingBalance + chargesBefore + adjustmentsBefore - paymentsBefore,
        chargesPaise = chargesInPeriod,
        adjustmentsPaise = adjustmentsInPeriod,
        paymentsPaise = paymentsInPeriod,
    )

    /**
     * Groups the period's DELIVERED entries into invoice lines, one per (product, applied rate),
     * so a mid-month rate change shows as two clearly priced lines.
     */
    fun buildLines(periodDeliveries: List<LedgerDelivery>, products: Map<Long, ProductInfo>): List<InvoiceLine> =
        periodDeliveries
            .filter { it.status == DeliveryStatus.DELIVERED && it.quantityMilli > 0 }
            .groupBy { it.productId to it.ratePaise }
            .map { (key, rows) ->
                val p = products[key.first]
                InvoiceLine(
                    productId = key.first,
                    productName = p?.name ?: "#${key.first}",
                    unitLabel = p?.unitLabel ?: "",
                    ratePaise = key.second,
                    quantityMilli = rows.sumOf { it.quantityMilli },
                    amountPaise = rows.sumOf { it.amountPaise },
                    deliveryDays = rows.map { it.date }.distinct().size,
                )
            }
            .sortedWith(compareBy<InvoiceLine>({ products[it.productId]?.sortOrder ?: 0 }, { it.productName }, { it.ratePaise }))

    /** Computes a month's invoice from a customer's complete ledger history. */
    fun compute(
        period: YearMonth,
        openingBalance: Long,
        deliveries: List<LedgerDelivery>,
        adjustments: List<LedgerAdjustment>,
        payments: List<LedgerPayment>,
        products: Map<Long, ProductInfo>,
    ): InvoiceComputation {
        val start = Dates.periodStart(period)
        val end = Dates.periodEnd(period)
        val billable = deliveries.filter { it.status == DeliveryStatus.DELIVERED }
        val activeAdj = adjustments.filter { it.active }
        val activePay = payments.filter { it.active }
        val inPeriod = billable.filter { it.date in start..end }
        val lines = buildLines(inPeriod, products)
        val summary = summarize(
            openingBalance = openingBalance,
            chargesBefore = billable.filter { it.date < start }.sumOf { it.amountPaise },
            adjustmentsBefore = activeAdj.filter { it.date < start }.sumOf { it.signedPaise },
            paymentsBefore = activePay.filter { it.date < start }.sumOf { it.amountPaise },
            chargesInPeriod = lines.sumOf { it.amountPaise },
            adjustmentsInPeriod = activeAdj.filter { it.date in start..end }.sumOf { it.signedPaise },
            paymentsInPeriod = activePay.filter { it.date in start..end }.sumOf { it.amountPaise },
        )
        return InvoiceComputation(period, lines, summary)
    }

    /** Current outstanding = opening + every charge and adjustment - every active payment. */
    fun outstanding(openingBalance: Long, totalCharges: Long, totalAdjustments: Long, totalPayments: Long): Long =
        openingBalance + totalCharges + totalAdjustments - totalPayments

    /**
     * FIFO payment state of a finalized invoice. [laterCredits] = payments plus credit adjustments
     * (as positive numbers) dated after the invoice month.
     */
    fun paidState(closingBalance: Long, laterCredits: Long): PaidState = when {
        closingBalance < 0 -> PaidState.CREDIT
        closingBalance == 0L -> PaidState.SETTLED
        laterCredits >= closingBalance -> PaidState.PAID
        laterCredits > 0 -> PaidState.PARTIALLY_PAID
        else -> PaidState.UNPAID
    }

    /** Invoice numbers: PREFIX-YYYY-MM-NNNN; revisions append -R1, -R2 ... */
    fun invoiceNumber(prefix: String, period: YearMonth, sequence: Int, revision: Int = 0): String {
        val clean = prefix.uppercase().filter { it.isLetterOrDigit() }.ifEmpty { "INV" }
        val base = String.format(java.util.Locale.ROOT, "%s-%04d-%02d-%04d", clean, period.year, period.monthValue, sequence)
        return if (revision > 0) "$base-R$revision" else base
    }
}
