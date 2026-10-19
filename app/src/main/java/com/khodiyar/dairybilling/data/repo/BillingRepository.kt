package com.khodiyar.dairybilling.data.repo

import androidx.room.withTransaction
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.db.BillingPeriodEntity
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.CustomerSum
import com.khodiyar.dairybilling.data.db.InvoiceLineEntity
import com.khodiyar.dairybilling.data.db.MonthlyBillEntity
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.billing.BalanceSummary
import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.billing.InvoiceComputation
import com.khodiyar.dairybilling.domain.billing.InvoiceLine
import com.khodiyar.dairybilling.domain.billing.LedgerAdjustment
import com.khodiyar.dairybilling.domain.billing.LedgerDelivery
import com.khodiyar.dairybilling.domain.billing.LedgerPayment
import com.khodiyar.dairybilling.domain.billing.PaidState
import com.khodiyar.dairybilling.domain.billing.ProductInfo
import com.khodiyar.dairybilling.domain.model.AdjustmentType
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.InvoiceStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.YearMonth

/** What the billing screens show for a customer-month. Never relies on color alone in the UI. */
enum class BillState { DRAFT, FINALIZED, NEEDS_REVISION }

data class CustomerBillRow(
    val customer: CustomerEntity,
    val summary: BalanceSummary,
    val invoice: MonthlyBillEntity?,
    val state: BillState,
    val paidState: PaidState,
    val missingDays: Int,
)

data class MonthOverview(
    val period: YearMonth,
    val periodState: BillingPeriodEntity?,
    val rows: List<CustomerBillRow>,
) {
    val locked: Boolean get() = periodState?.locked == true
    val totalCharges: Long get() = rows.sumOf { it.summary.chargesPaise }
    val totalPayments: Long get() = rows.sumOf { it.summary.paymentsPaise }
    val totalClosing: Long get() = rows.sumOf { it.summary.closingBalancePaise.coerceAtLeast(0) }
    val draftCount: Int get() = rows.count { it.state == BillState.DRAFT }
    val finalizedCount: Int get() = rows.count { it.state != BillState.DRAFT }
    val needsRevision: Int get() = rows.count { it.state == BillState.NEEDS_REVISION }
    val missingEntryCustomers: Int get() = rows.count { it.missingDays > 0 }
}

data class InvoiceView(
    val customer: CustomerEntity,
    val period: YearMonth,
    /** Live calculation from current records. */
    val live: InvoiceComputation,
    /** Finalized snapshot (lines exactly as issued), if any. */
    val invoice: MonthlyBillEntity?,
    val invoiceLines: List<InvoiceLineEntity>,
    val history: List<MonthlyBillEntity>,
    val state: BillState,
    val paidState: PaidState,
    val locked: Boolean,
    val settings: AppSettingsEntity,
    /** True when bill steps are switched off: a plain live bill, never labelled "draft". */
    val simple: Boolean = false,
) {
    /** What the bill shows: the issued snapshot when finalized, otherwise the live draft. */
    val displayLines: List<InvoiceLine>
        get() = if (invoice != null) invoiceLines.map {
            InvoiceLine(it.productId, it.productName, it.unitLabel, it.ratePaise, it.quantityMilli, it.amountPaise, it.deliveryDays)
        } else live.lines
    val displaySummary: BalanceSummary
        get() = invoice?.let { BalanceSummary(it.previousBalancePaise, it.chargesPaise, it.adjustmentsPaise, it.paymentsPaise) } ?: live.summary
}

data class MonthStatement(val period: YearMonth, val summary: BalanceSummary, val invoiceNumber: String?)

data class CustomerLedger(
    val customer: CustomerEntity,
    val outstandingPaise: Long,
    val months: List<MonthStatement>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillingRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val time: TimeProvider,
) {
    private fun List<CustomerSum>.toMap(): Map<Long, Long> = associate { it.customerId to it.total }

    fun observeOverview(period: YearMonth): Flow<MonthOverview> =
        db.changes(*AppDatabase.LEDGER_TABLES).mapLatest { overview(period) }

    suspend fun overview(period: YearMonth): MonthOverview {
        val start = Dates.periodStart(period)
        val end = Dates.periodEnd(period)
        val key = Dates.periodKey(period)
        val customers = db.customers().all()
        val chargesBefore = db.deliveries().chargesBefore(start).toMap()
        val chargesIn = db.deliveries().chargesBetween(start, end).toMap()
        val paidBefore = db.payments().paidBefore(start).toMap()
        val paidIn = db.payments().paidBetween(start, end).toMap()
        val paidAfter = db.payments().paidAfter(end).toMap()
        val adjBefore = db.adjustments().before(start).groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.signedPaise } }
        val adjIn = db.adjustments().range(start, end).groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.signedPaise } }
        val creditsAfter = db.adjustments().after(end).filter { it.type == AdjustmentType.CREDIT }
            .groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.amountPaise } }
        val invoices = db.invoices().activeForPeriod(key).associateBy { it.customerId }
        val monthEntries = db.deliveries().range(start, end)
        val entryDays = monthEntries.groupBy { it.customerId }.mapValues { e -> e.value.map { it.date }.toSet().size }
        val today = time.today().toEpochDay()
        val daysElapsed = when {
            today < start -> 0
            today > end -> (end - start + 1).toInt()
            else -> (today - start + 1).toInt()
        }

        val rows = customers.mapNotNull { c ->
            val summary = BillingEngine.summarize(
                openingBalance = c.openingBalancePaise,
                chargesBefore = chargesBefore[c.id] ?: 0,
                adjustmentsBefore = adjBefore[c.id] ?: 0,
                paymentsBefore = paidBefore[c.id] ?: 0,
                chargesInPeriod = chargesIn[c.id] ?: 0,
                adjustmentsInPeriod = adjIn[c.id] ?: 0,
                paymentsInPeriod = paidIn[c.id] ?: 0,
            )
            val invoice = invoices[c.id]
            val hasActivity = summary.chargesPaise != 0L || summary.paymentsPaise != 0L || summary.adjustmentsPaise != 0L ||
                summary.previousBalancePaise != 0L
            if (!c.active && !hasActivity && invoice == null) return@mapNotNull null
            val state = when {
                invoice == null -> BillState.DRAFT
                invoice.matches(summary) -> BillState.FINALIZED
                else -> BillState.NEEDS_REVISION
            }
            val closing = invoice?.closingBalancePaise ?: summary.closingBalancePaise
            val later = (paidAfter[c.id] ?: 0) + (creditsAfter[c.id] ?: 0)
            val expectsDaily = c.active && c.defaultProductId != null && c.defaultQuantityMilli > 0 && c.createdAt <= java.time.LocalDate.ofEpochDay(end).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val missing = if (expectsDaily) (daysElapsed - (entryDays[c.id] ?: 0)).coerceAtLeast(0) else 0
            CustomerBillRow(c, summary, invoice, state, BillingEngine.paidState(closing, later), missing)
        }.sortedWith(compareBy(HouseNumberComparator) { it.customer.houseNo })
        return MonthOverview(period, db.periods().get(key), rows)
    }

    private fun MonthlyBillEntity.matches(s: BalanceSummary) =
        previousBalancePaise == s.previousBalancePaise && chargesPaise == s.chargesPaise &&
            adjustmentsPaise == s.adjustmentsPaise && paymentsPaise == s.paymentsPaise

    private suspend fun products(): Map<Long, ProductInfo> =
        db.products().all().associate { it.id to ProductInfo(it.id, it.name, it.displayUnit, it.sortOrder) }

    /** Full-history computation for one customer, the same engine used by tests. */
    suspend fun compute(customer: CustomerEntity, period: YearMonth): InvoiceComputation {
        val deliveries = db.deliveries().customerRange(customer.id, Long.MIN_VALUE, Dates.periodEnd(period))
            .map { LedgerDelivery(it.date, it.productId, it.status, it.quantityMilli, it.ratePaise, it.amountPaise) }
        val payments = db.payments().customerRange(customer.id, Long.MIN_VALUE, Dates.periodEnd(period))
            .map { LedgerPayment(it.date, it.amountPaise, it.status == com.khodiyar.dairybilling.domain.model.RecordStatus.ACTIVE) }
        val adjustments = db.adjustments().customerActive(customer.id).filter { it.date <= Dates.periodEnd(period) }
            .map { LedgerAdjustment(it.date, it.signedPaise) }
        return BillingEngine.compute(period, customer.openingBalancePaise, deliveries, adjustments, payments, products())
    }

    fun observeInvoice(customerId: Long, period: YearMonth): Flow<InvoiceView?> =
        db.changes(*AppDatabase.LEDGER_TABLES, "app_settings").mapLatest { invoiceView(customerId, period) }

    suspend fun invoiceView(customerId: Long, period: YearMonth, invoiceId: Long = 0): InvoiceView? {
        val customer = db.customers().get(customerId) ?: return null
        val key = Dates.periodKey(period)
        val live = compute(customer, period)
        val history = db.invoices().historyFor(customerId, key)
        val invoice = if (invoiceId > 0) history.firstOrNull { it.id == invoiceId } else history.firstOrNull { it.status == InvoiceStatus.FINALIZED }
        val lines = invoice?.let { db.invoices().lines(it.id) }.orEmpty()
        val state = when {
            invoice == null -> BillState.DRAFT
            invoice.status == InvoiceStatus.SUPERSEDED -> BillState.FINALIZED
            invoice.matches(live.summary) && sameLines(lines, live.lines) -> BillState.FINALIZED
            else -> BillState.NEEDS_REVISION
        }
        val end = Dates.periodEnd(period)
        val later = db.payments().customerRange(customerId, end + 1, Long.MAX_VALUE).filter { it.status == com.khodiyar.dairybilling.domain.model.RecordStatus.ACTIVE }.sumOf { it.amountPaise } +
            db.adjustments().customerActive(customerId).filter { it.date > end && it.type == AdjustmentType.CREDIT }.sumOf { it.amountPaise }
        val closing = invoice?.closingBalancePaise ?: live.summary.closingBalancePaise
        val settings = db.settings().get() ?: AppSettingsEntity(dairyName = "Dairy", updatedAt = 0)
        return InvoiceView(customer, period, live, invoice, lines, history, state, BillingEngine.paidState(closing, later), db.periods().get(key)?.locked == true, settings)
    }

    private fun sameLines(saved: List<InvoiceLineEntity>, live: List<InvoiceLine>): Boolean =
        saved.size == live.size && saved.zip(live).all { (a, b) ->
            a.productId == b.productId && a.ratePaise == b.ratePaise && a.quantityMilli == b.quantityMilli && a.amountPaise == b.amountPaise
        }

    private suspend fun ensureCanFinalize(period: YearMonth) {
        session.require(Permission.FINALIZE_INVOICES)
        if (db.periods().get(Dates.periodKey(period))?.locked == true) throw AppException(R.string.err_period_locked, Dates.formatMonth(period))
        if (Dates.periodStart(period) > time.today().toEpochDay()) throw AppException(R.string.err_future_period)
    }

    private suspend fun insertInvoice(customer: CustomerEntity, period: YearMonth, comp: InvoiceComputation, sequence: Int, revision: Int, supersedes: Long?, reason: String): MonthlyBillEntity {
        val settings = db.settings().get() ?: AppSettingsEntity(dairyName = "Dairy", updatedAt = 0)
        val key = Dates.periodKey(period)
        val number = BillingEngine.invoiceNumber(settings.invoicePrefix, period, sequence, revision)
        val entity = MonthlyBillEntity(
            customerId = customer.id, periodKey = key, invoiceNumber = number, sequenceNo = sequence, revision = revision,
            status = InvoiceStatus.FINALIZED, activeSlot = "${customer.id}:$key",
            customerCode = customer.code, customerName = customer.name, houseNo = customer.houseNo, mobile = customer.mobile, address = customer.address,
            previousBalancePaise = comp.summary.previousBalancePaise, chargesPaise = comp.summary.chargesPaise,
            adjustmentsPaise = comp.summary.adjustmentsPaise, paymentsPaise = comp.summary.paymentsPaise,
            closingBalancePaise = comp.summary.closingBalancePaise, supersedesId = supersedes, revisionReason = reason,
            finalizedBy = session.current?.id, finalizedAt = time.nowMillis(),
        )
        val id = db.invoices().insert(entity)
        db.invoices().insertLines(comp.lines.mapIndexed { i, l ->
            InvoiceLineEntity(invoiceId = id, productId = l.productId, productName = l.productName, unitLabel = l.unitLabel, ratePaise = l.ratePaise,
                quantityMilli = l.quantityMilli, amountPaise = l.amountPaise, deliveryDays = l.deliveryDays, lineOrder = i)
        })
        return entity.copy(id = id)
    }

    suspend fun finalize(customerId: Long, period: YearMonth): MonthlyBillEntity {
        ensureCanFinalize(period)
        return db.withTransaction {
            val customer = db.customers().get(customerId) ?: throw AppException(R.string.err_not_found)
            val key = Dates.periodKey(period)
            if (db.invoices().activeFor(customerId, key) != null) throw AppException(R.string.err_already_finalized)
            val comp = compute(customer, period)
            val seq = db.invoices().maxSequence(key) + 1
            val inv = insertInvoice(customer, period, comp, seq, 0, null, "")
            audit.log(AuditLogger.Actions.INVOICE_FINALIZED, "invoice", inv.id, "${inv.invoiceNumber} ${customer.code} due=${Money.plain(inv.closingBalancePaise)}")
            inv
        }
    }

    /** Finalizes every draft with activity in one transaction. Returns how many were issued. */
    suspend fun finalizeAll(period: YearMonth): Int {
        ensureCanFinalize(period)
        return db.withTransaction {
            val ov = overview(period)
            var seq = db.invoices().maxSequence(Dates.periodKey(period))
            var count = 0
            for (row in ov.rows) {
                if (row.state != BillState.DRAFT) continue
                val s = row.summary
                if (s.chargesPaise == 0L && s.paymentsPaise == 0L && s.adjustmentsPaise == 0L && s.previousBalancePaise == 0L) continue
                seq += 1
                val comp = compute(row.customer, period)
                val inv = insertInvoice(row.customer, period, comp, seq, 0, null, "")
                audit.log(AuditLogger.Actions.INVOICE_FINALIZED, "invoice", inv.id, "${inv.invoiceNumber} ${row.customer.code} due=${Money.plain(inv.closingBalancePaise)} (bulk)")
                count++
            }
            count
        }
    }

    /** Controlled correction: the old invoice is kept as SUPERSEDED and a numbered revision is issued. */
    suspend fun revise(invoiceId: Long, reason: String): MonthlyBillEntity {
        if (reason.isBlank()) throw AppException(R.string.err_reason_required)
        return db.withTransaction {
            val old = db.invoices().get(invoiceId) ?: throw AppException(R.string.err_not_found)
            val period = Dates.fromPeriodKey(old.periodKey)
            ensureCanFinalize(period)
            if (old.status != InvoiceStatus.FINALIZED) throw AppException(R.string.err_already_superseded)
            val customer = db.customers().get(old.customerId) ?: throw AppException(R.string.err_not_found)
            db.invoices().update(old.copy(status = InvoiceStatus.SUPERSEDED, activeSlot = null))
            val comp = compute(customer, period)
            val inv = insertInvoice(customer, period, comp, old.sequenceNo, old.revision + 1, old.id, reason.trim())
            audit.log(AuditLogger.Actions.INVOICE_REVISED, "invoice", inv.id,
                "${old.invoiceNumber} -> ${inv.invoiceNumber}: due ${Money.plain(old.closingBalancePaise)} -> ${Money.plain(inv.closingBalancePaise)}. ${reason.trim()}")
            inv
        }
    }

    suspend fun lock(period: YearMonth) {
        val user = session.require(Permission.LOCK_PERIODS)
        db.withTransaction {
            val key = Dates.periodKey(period)
            val existing = db.periods().get(key)
            db.periods().upsert((existing ?: BillingPeriodEntity(periodKey = key, locked = true)).copy(locked = true, lockedBy = user.id, lockedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.PERIOD_LOCKED, "period", key.toLong(), "Locked ${Dates.formatMonth(period)}")
        }
    }

    suspend fun reopen(period: YearMonth, reason: String) {
        val user = session.require(Permission.LOCK_PERIODS)
        if (reason.isBlank()) throw AppException(R.string.err_reason_required)
        db.withTransaction {
            val key = Dates.periodKey(period)
            val existing = db.periods().get(key) ?: throw AppException(R.string.err_not_locked)
            if (!existing.locked) throw AppException(R.string.err_not_locked)
            db.periods().upsert(existing.copy(locked = false, reopenedBy = user.id, reopenedAt = time.nowMillis(), reopenReason = reason.trim()))
            audit.log(AuditLogger.Actions.PERIOD_REOPENED, "period", key.toLong(), "Reopened ${Dates.formatMonth(period)}: ${reason.trim()}")
        }
    }

    suspend fun markPdfGenerated(invoiceId: Long) {
        db.withTransaction {
            val inv = db.invoices().get(invoiceId) ?: return@withTransaction
            db.invoices().update(inv.copy(pdfGeneratedAt = time.nowMillis()))
            audit.log(AuditLogger.Actions.INVOICE_PDF, "invoice", invoiceId, "PDF generated for ${inv.invoiceNumber}")
        }
    }

    fun observeInvoicesForCustomer(customerId: Long) = db.invoices().observeForCustomer(customerId)

    fun observeLedger(customerId: Long): Flow<CustomerLedger?> =
        db.changes(*AppDatabase.LEDGER_TABLES).mapLatest { ledger(customerId) }

    /** Month-by-month statement from the customer's first month to the current month. */
    suspend fun ledger(customerId: Long): CustomerLedger? {
        val c = db.customers().get(customerId) ?: return null
        val all = db.deliveries().customerRange(c.id, Long.MIN_VALUE, Long.MAX_VALUE)
        val pays = db.payments().customerRange(c.id, Long.MIN_VALUE, Long.MAX_VALUE)
        val adjs = db.adjustments().customerActive(c.id)
        val deliveries = all.map { LedgerDelivery(it.date, it.productId, it.status, it.quantityMilli, it.ratePaise, it.amountPaise) }
        val payments = pays.map { LedgerPayment(it.date, it.amountPaise, it.status == com.khodiyar.dairybilling.domain.model.RecordStatus.ACTIVE) }
        val adjustments = adjs.map { LedgerAdjustment(it.date, it.signedPaise) }
        val createdDay = java.time.Instant.ofEpochMilli(c.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        val firstDay = listOfNotNull(createdDay, all.minOfOrNull { it.date }, pays.minOfOrNull { it.date }, adjs.minOfOrNull { it.date }).min()
        var ym = Dates.periodOf(firstDay)
        val last = YearMonth.from(time.today())
        val invoices = db.invoices().all().filter { it.customerId == c.id && it.status == InvoiceStatus.FINALIZED }.associateBy { it.periodKey }
        val products = products()
        val months = ArrayList<MonthStatement>()
        while (!ym.isAfter(last)) {
            val comp = BillingEngine.compute(ym, c.openingBalancePaise, deliveries, adjustments, payments, products)
            months += MonthStatement(ym, comp.summary, invoices[Dates.periodKey(ym)]?.invoiceNumber)
            ym = ym.plusMonths(1)
        }
        val outstanding = BillingEngine.outstanding(
            c.openingBalancePaise,
            deliveries.filter { it.status == DeliveryStatus.DELIVERED }.sumOf { it.amountPaise },
            adjustments.sumOf { it.signedPaise },
            payments.filter { it.active }.sumOf { it.amountPaise },
        )
        return CustomerLedger(c, outstanding, months.reversed())
    }

    /** Outstanding for every customer, from aggregated sums (same policy as [BillingEngine.outstanding]). */
    suspend fun outstandingAll(): Map<Long, Long> {
        val far = Long.MAX_VALUE
        val charges = db.deliveries().chargesBefore(far).toMap()
        val paid = db.payments().paidBefore(far).toMap()
        val adj = db.adjustments().before(far).groupBy { it.customerId }.mapValues { e -> e.value.sumOf { it.signedPaise } }
        return db.customers().all().associate { c ->
            c.id to BillingEngine.outstanding(c.openingBalancePaise, charges[c.id] ?: 0, adj[c.id] ?: 0, paid[c.id] ?: 0)
        }
    }
}

/** Natural ordering for house numbers: "A-2" before "A-10". */
object HouseNumberComparator : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val pa = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val pb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(pa.size, pb.size)) {
            val x = pa[i]
            val y = pb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0').ifEmpty { "0" }
                val ny = y.trimStart('0').ifEmpty { "0" }
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else x.compareTo(y)
            if (c != 0) return c
        }
        return pa.size - pb.size
    }
}
