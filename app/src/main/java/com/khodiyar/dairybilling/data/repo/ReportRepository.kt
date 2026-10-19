package com.khodiyar.dairybilling.data.repo

import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.BillingAdjustmentEntity
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.RecordStatus
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.domain.time.TimeProvider
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.security.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.YearMonth

data class ProductQty(val product: ProductEntity, val quantityMilli: Long, val amountPaise: Long, val entries: Int)

data class DayPoint(val date: LocalDate, val amountPaise: Long)

data class RecentPayment(val payment: PaymentEntity, val customer: CustomerEntity?)

data class Dashboard(
    val today: LocalDate,
    val todayProducts: List<ProductQty>,
    val todaySalesPaise: Long,
    val expectedToday: Int,
    val enteredToday: Int,
    val activeCustomers: Int,
    val monthSalesPaise: Long,
    val monthCollectedPaise: Long,
    val outstandingPaise: Long,
    val customersWithDues: Int,
    val last14Days: List<DayPoint>,
    val recentPayments: List<RecentPayment>,
    val productsWithoutRate: List<String>,
    val lastMonthNeedsAttention: Int,
)

data class DailyRow(val customer: CustomerEntity, val product: ProductEntity, val entry: DeliveryEntryEntity)

data class DailyReport(
    val date: LocalDate,
    val products: List<ProductQty>,
    val rows: List<DailyRow>,
    val delivered: Int,
    val noDelivery: Int,
    val missed: Int,
    val pending: List<CustomerEntity>,
) {
    val salesPaise: Long get() = products.sumOf { it.amountPaise }
}

data class RangeReport(
    val from: LocalDate,
    val to: LocalDate,
    val products: List<ProductQty>,
    val daily: List<DayPoint>,
    val salesPaise: Long,
    val collectedPaise: Long,
    val collectionsByMethod: Map<PaymentMethod, Long>,
    val payments: List<RecentPayment>,
)

data class OutstandingRow(val customer: CustomerEntity, val outstandingPaise: Long)

data class StatementLine(val date: Long, val kind: Kind, val description: String, val debitPaise: Long, val creditPaise: Long, val balancePaise: Long) {
    enum class Kind { DELIVERY, PAYMENT, ADJUSTMENT, NO_DELIVERY, MISSED }
}

data class CustomerStatement(
    val customer: CustomerEntity,
    val from: LocalDate,
    val to: LocalDate,
    val openingPaise: Long,
    val lines: List<StatementLine>,
    val closingPaise: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportRepository(
    private val db: AppDatabase,
    private val session: SessionManager,
    private val billing: BillingRepository,
    private val time: TimeProvider,
) {
    fun observeDashboard(): Flow<Dashboard> = db.changes(*AppDatabase.LEDGER_TABLES).mapLatest { dashboard() }

    suspend fun dashboard(): Dashboard {
        val today = time.today()
        val t = today.toEpochDay()
        val products = db.products().all().associateBy { it.id }
        val customers = db.customers().all()
        val active = customers.filter { it.active }
        val todayTotals = db.deliveries().productTotals(t, t).mapNotNull { pt -> products[pt.productId]?.let { ProductQty(it, pt.quantityMilli, pt.amountPaise, pt.entries) } }
            .sortedBy { it.product.sortOrder }
        val todayEntries = db.deliveries().range(t, t)
        val enteredCustomers = todayEntries.map { it.customerId }.toSet()
        val expected = active.filter { it.defaultProductId != null && it.defaultQuantityMilli > 0 }
        val ym = YearMonth.from(today)
        val start = Dates.periodStart(ym)
        val monthSales = db.deliveries().chargesBetween(start, t).sumOf { it.total }
        val collected = db.payments().totalBetween(start, t)
        val outstanding = billing.outstandingAll()
        val from14 = t - 13
        val daily = db.deliveries().dailyTotals(from14, t).associate { it.date to it.amountPaise }
        val last14 = (from14..t).map { DayPoint(LocalDate.ofEpochDay(it), daily[it] ?: 0) }
        val byId = customers.associateBy { it.id }
        val recent = db.payments().range(t - 60, t).sortedByDescending { it.createdAt }.take(6).map { RecentPayment(it, byId[it.customerId]) }
        val rates = db.rates().all().groupBy { it.productId }
        val noRate = products.values.filter { p -> p.active && rates[p.id].orEmpty().none { it.effectiveFrom <= t } }.map { it.name }
        val lastMonth = ym.minusMonths(1)
        val attention = if (session.has(Permission.VIEW_BILLING)) {
            val ov = billing.overview(lastMonth)
            if (ov.locked) 0 else ov.draftCount + ov.needsRevision
        } else 0
        return Dashboard(
            today = today,
            todayProducts = todayTotals,
            todaySalesPaise = todayTotals.sumOf { it.amountPaise },
            expectedToday = expected.size,
            enteredToday = expected.count { it.id in enteredCustomers },
            activeCustomers = active.size,
            monthSalesPaise = monthSales,
            monthCollectedPaise = collected,
            outstandingPaise = outstanding.values.filter { it > 0 }.sum(),
            customersWithDues = outstanding.values.count { it > 0 },
            last14Days = last14,
            recentPayments = recent,
            productsWithoutRate = noRate,
            lastMonthNeedsAttention = attention,
        )
    }

    suspend fun daily(date: LocalDate): DailyReport {
        val d = date.toEpochDay()
        val products = db.products().all().associateBy { it.id }
        val customers = db.customers().all().associateBy { it.id }
        val entries = db.deliveries().range(d, d)
        val rows = entries.mapNotNull { e ->
            val c = customers[e.customerId] ?: return@mapNotNull null
            val p = products[e.productId] ?: return@mapNotNull null
            DailyRow(c, p, e)
        }.sortedWith(compareBy<DailyRow, String>(HouseNumberComparator) { it.customer.houseNo }.thenBy { it.product.sortOrder })
        val totals = db.deliveries().productTotals(d, d).mapNotNull { pt -> products[pt.productId]?.let { ProductQty(it, pt.quantityMilli, pt.amountPaise, pt.entries) } }
            .sortedBy { it.product.sortOrder }
        val entered = entries.map { it.customerId }.toSet()
        val pending = customers.values.filter { it.active && it.defaultProductId != null && it.defaultQuantityMilli > 0 && it.id !in entered }
            .sortedWith(compareBy(HouseNumberComparator) { it.houseNo })
        return DailyReport(
            date = date,
            products = totals,
            rows = rows,
            delivered = entries.count { it.status == DeliveryStatus.DELIVERED },
            noDelivery = entries.count { it.status == DeliveryStatus.NO_DELIVERY },
            missed = entries.count { it.status == DeliveryStatus.MISSED },
            pending = pending,
        )
    }

    suspend fun range(from: LocalDate, to: LocalDate, productId: Long? = null): RangeReport {
        session.require(Permission.VIEW_REPORTS)
        val f = from.toEpochDay()
        val t = to.toEpochDay()
        val products = db.products().all().associateBy { it.id }
        val customers = db.customers().all().associateBy { it.id }
        val totals = db.deliveries().productTotals(f, t)
            .filter { productId == null || it.productId == productId }
            .mapNotNull { pt -> products[pt.productId]?.let { ProductQty(it, pt.quantityMilli, pt.amountPaise, pt.entries) } }
            .sortedBy { it.product.sortOrder }
        val dailyRaw = if (productId == null) {
            db.deliveries().dailyTotals(f, t).associate { it.date to it.amountPaise }
        } else {
            db.deliveries().range(f, t).filter { it.productId == productId }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountPaise } }
        }
        val payments = db.payments().range(f, t)
        val active = payments.filter { it.status == RecordStatus.ACTIVE }
        return RangeReport(
            from = from,
            to = to,
            products = totals,
            daily = (f..t).map { DayPoint(LocalDate.ofEpochDay(it), dailyRaw[it] ?: 0) },
            salesPaise = totals.sumOf { it.amountPaise },
            collectedPaise = active.sumOf { it.amountPaise },
            collectionsByMethod = active.groupBy { it.method }.mapValues { e -> e.value.sumOf { it.amountPaise } },
            payments = payments.sortedByDescending { it.date }.map { RecentPayment(it, customers[it.customerId]) },
        )
    }

    suspend fun outstanding(includeInactive: Boolean): List<OutstandingRow> {
        session.require(Permission.VIEW_REPORTS)
        val map = billing.outstandingAll()
        return db.customers().all()
            .filter { includeInactive || it.active }
            .map { OutstandingRow(it, map[it.id] ?: 0) }
            .sortedByDescending { it.outstandingPaise }
    }

    /** Chronological statement with running balance; opening = balance at the start of [from]. */
    suspend fun statement(customerId: Long, from: LocalDate, to: LocalDate): CustomerStatement? {
        val c = db.customers().get(customerId) ?: return null
        val f = from.toEpochDay()
        val t = to.toEpochDay()
        val products = db.products().all().associateBy { it.id }
        val chargesBefore = db.deliveries().customerChargesBefore(c.id, f)
        val paidBefore = db.payments().customerPaidBefore(c.id, f)
        val adjAll = db.adjustments().customerActive(c.id)
        val opening = BillingEngine.outstanding(c.openingBalancePaise, chargesBefore, adjAll.filter { it.date < f }.sumOf { it.signedPaise }, paidBefore)
        data class Ev(val date: Long, val order: Int, val line: (Long) -> StatementLine, val delta: Long)
        val events = ArrayList<Ev>()
        db.deliveries().customerRange(c.id, f, t).forEach { e ->
            val p = products[e.productId]
            val label = (p?.name ?: "?") + " " + com.khodiyar.dairybilling.domain.money.Quantity.formatWithUnit(e.quantityMilli, p?.displayUnit ?: "")
            val kind = when (e.status) {
                DeliveryStatus.DELIVERED -> StatementLine.Kind.DELIVERY
                DeliveryStatus.NO_DELIVERY -> StatementLine.Kind.NO_DELIVERY
                DeliveryStatus.MISSED -> StatementLine.Kind.MISSED
            }
            events += Ev(e.date, 0, { bal -> StatementLine(e.date, kind, if (kind == StatementLine.Kind.DELIVERY) label else (p?.name ?: ""), e.amountPaise, 0, bal) }, e.amountPaise)
        }
        adjAll.filter { it.date in f..t }.forEach { a: BillingAdjustmentEntity ->
            events += Ev(a.date, 1, { bal -> StatementLine(a.date, StatementLine.Kind.ADJUSTMENT, a.reason, if (a.signedPaise > 0) a.amountPaise else 0, if (a.signedPaise < 0) a.amountPaise else 0, bal) }, a.signedPaise)
        }
        db.payments().customerRange(c.id, f, t).filter { it.status == RecordStatus.ACTIVE }.forEach { p ->
            events += Ev(p.date, 2, { bal -> StatementLine(p.date, StatementLine.Kind.PAYMENT, p.method.name + if (p.reference.isNotBlank()) " · ${p.reference}" else "", 0, p.amountPaise, bal) }, -p.amountPaise)
        }
        var bal = opening
        val lines = events.sortedWith(compareBy({ it.date }, { it.order })).map { ev ->
            bal += ev.delta
            ev.line(bal)
        }
        return CustomerStatement(c, from, to, opening, lines, bal)
    }

    suspend fun customers(): List<CustomerEntity> = db.customers().all().sortedWith(compareBy(HouseNumberComparator) { it.houseNo })
}
