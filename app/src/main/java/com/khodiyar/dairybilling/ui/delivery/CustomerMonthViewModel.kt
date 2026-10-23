package com.khodiyar.dairybilling.ui.delivery

import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.ProductRateEntity
import com.khodiyar.dairybilling.data.repo.DeliveryDraft
import com.khodiyar.dairybilling.data.repo.HouseNumberComparator
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.billing.BillingEngine
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.common.AppViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One day of the month for the selected customer and product. */
data class DayRow(
    val date: LocalDate,
    val existing: DeliveryEntryEntity?,
    val text: String,
    val status: DeliveryStatus?,
    val amountPaise: Long?,
    val dirty: Boolean,
    val error: UiText?,
    val editable: Boolean,
    val future: Boolean,
) {
    val key: Long get() = date.toEpochDay()
    val isDone: Boolean get() = existing != null && !dirty
}

data class CustomerMonthUi(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val session: DeliverySession = DeliverySession.MORNING,
    val sessionsEnabled: Boolean = false,
    val products: List<ProductEntity> = emptyList(),
    val product: ProductEntity? = null,
    val ratePaise: Long? = null,
    val customers: List<CustomerEntity> = emptyList(),
    val customer: CustomerEntity? = null,
    val matches: List<CustomerEntity> = emptyList(),
    val rows: List<DayRow> = emptyList(),
    val filter: RowFilter = RowFilter.ALL,
    val query: String = "",
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val doneCount: Int = 0,
    val savedQuantityMilli: Long = 0,
    val savedAmountPaise: Long = 0,
    val unsavedCount: Int = 0,
    val pendingDeletes: Int = 0,
    val hasErrors: Boolean = false,
    val currency: String = "₹",
    val locked: Boolean = false,
    val canEditPast: Boolean = true,
)

private data class MonthInputs(
    val month: YearMonth,
    val session: DeliverySession,
    val productId: Long,
    val customerId: Long,
    val query: String,
)

/** Day-wise entry: every day of one month for a single customer. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerMonthViewModel(c: AppContainer) : AppViewModel(c) {
    private val month = MutableStateFlow(YearMonth.from(c.deliveryDate.value))
    private val session = MutableStateFlow(DeliverySession.MORNING)
    private val productId = MutableStateFlow(0L)
    private val customerId = MutableStateFlow(0L)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(RowFilter.ALL)
    private val drafts = MutableStateFlow<Map<Long, DraftEntry>>(emptyMap())

    init {
        viewModelScope.launch { productId.value = c.prefs.prefs.first().lastProductId }
    }

    private val inputs: Flow<MonthInputs> = combine(month, session, productId, customerId, query) { m, s, p, cid, q -> MonthInputs(m, s, p, cid, q) }

    private val catalog = combine(c.customers.observeAll(), c.products.observeAll(), c.db.rates().observeAll(), c.settings.observe()) { cs, ps, rs, st ->
        Catalog(cs, ps, rs, st.currencySymbol, st.sessionsEnabled)
    }

    private data class Catalog(
        val customers: List<CustomerEntity>,
        val products: List<ProductEntity>,
        val rates: List<ProductRateEntity>,
        val currency: String,
        val sessionsEnabled: Boolean,
    )

    /** The chosen customer, or the first on the route until one is picked. */
    private val resolvedCustomerId = combine(customerId, c.customers.observeAll()) { id, cs ->
        if (cs.any { it.id == id }) id else routeOrder(cs.filter { it.active }).firstOrNull()?.id ?: 0L
    }.distinctUntilChanged()

    private val monthData = combine(month, resolvedCustomerId) { m, cid -> m to cid }.flatMapLatest { (m, cid) ->
        val entries = if (cid == 0L) flowOf(emptyList<DeliveryEntryEntity>()) else c.deliveries.observeCustomer(cid, Dates.periodStart(m), Dates.periodEnd(m))
        combine(entries, c.db.periods().observe(Dates.periodKey(m))) { e, p -> MonthData(cid, e, p?.locked == true) }
    }

    private data class MonthData(val customerId: Long, val entries: List<DeliveryEntryEntity>, val locked: Boolean)

    private fun routeOrder(cs: List<CustomerEntity>) =
        cs.sortedWith(compareBy<CustomerEntity>({ if (it.routeOrder > 0) 0 else 1 }, { it.routeOrder }).thenComparing({ it.houseNo }, HouseNumberComparator))

    val ui = combine(inputs, monthData, catalog, drafts, filter) { inp, md, cat, dr, f ->
        val locked = md.locked
        val activeProducts = cat.products.filter { it.active }
        val product = activeProducts.firstOrNull { it.id == inp.productId } ?: activeProducts.firstOrNull()
        val customers = routeOrder(cat.customers.filter { it.active })
        val customer = cat.customers.firstOrNull { it.id == md.customerId }
        val today = c.time.today()
        val me = c.session.current
        val canPast = c.session.has(Permission.EDIT_PAST_DELIVERIES)
        val unit = product?.unit
        fun rateOn(day: Long) = product?.let { p -> cat.rates.filter { it.productId == p.id && it.effectiveFrom <= day }.maxByOrNull { it.effectiveFrom }?.ratePaise }
        val byDay = md.entries.filter { it.productId == product?.id && it.session == inp.session }.associateBy { it.date }
        val allRows = if (customer == null) emptyList() else (1..inp.month.lengthOfMonth()).map { d ->
            val date = inp.month.atDay(d)
            val day = date.toEpochDay()
            val ex = byDay[day]
            val dft = dr[day]
            val exText = if (ex != null && ex.status == DeliveryStatus.DELIVERED) Quantity.format(ex.quantityMilli) else ""
            var error: UiText? = null
            var amount: Long? = null
            if (dft != null && unit != null && dft.status == DeliveryStatus.DELIVERED && dft.text.isNotBlank()) {
                val rate = rateOn(day)
                when (val r = Quantity.parse(dft.text, unit)) {
                    is Quantity.ParseResult.Ok -> amount = rate?.let { BillingEngine.lineAmount(r.milli, it) }
                    is Quantity.ParseResult.TooPrecise -> error = UiText.res(R.string.err_qty_precision, r.maxDecimals)
                    Quantity.ParseResult.TooLarge -> error = UiText.res(R.string.err_qty_large)
                    else -> error = UiText.res(R.string.err_qty_invalid)
                }
                if (error == null && rate == null) error = UiText.res(R.string.err_no_rate_short)
            } else if (dft == null && ex != null) {
                amount = ex.amountPaise
            }
            val future = date > today
            val dayEditable = !locked && !future && (date == today || canPast)
            val editable = dayEditable && (ex == null || ex.createdBy == me?.id || canPast) && (ex != null || customer.active)
            DayRow(date, ex, dft?.text ?: exText, dft?.status ?: ex?.status, amount, dft != null, error, editable, future)
        }
        val filtered = when (f) {
            RowFilter.ALL -> allRows
            RowFilter.PENDING -> allRows.filter { !it.future && (it.existing == null || it.dirty) }
            RowFilter.DONE -> allRows.filter { it.isDone }
        }
        val q = inp.query.trim().lowercase()
        val matches = if (q.isEmpty()) emptyList() else customers.filter {
            it.name.lowercase().contains(q) || it.houseNo.lowercase().contains(q) || it.code.lowercase().contains(q) || it.mobile.contains(q)
        }
        val saved = byDay.values
        val refDay = minOf(today, inp.month.atEndOfMonth()).toEpochDay()
        CustomerMonthUi(
            loading = false,
            month = inp.month,
            today = today,
            session = inp.session,
            sessionsEnabled = cat.sessionsEnabled,
            products = activeProducts,
            product = product,
            ratePaise = rateOn(refDay),
            customers = customers,
            customer = customer,
            matches = matches,
            rows = filtered,
            filter = f,
            query = inp.query,
            totalCount = allRows.size,
            pendingCount = allRows.count { !it.future && it.existing == null },
            doneCount = allRows.count { it.existing != null },
            savedQuantityMilli = saved.filter { it.status == DeliveryStatus.DELIVERED }.sumOf { it.quantityMilli },
            savedAmountPaise = saved.sumOf { it.amountPaise },
            unsavedCount = dr.size,
            pendingDeletes = dr.count { (day, d) -> d.text.isBlank() && d.status == DeliveryStatus.DELIVERED && byDay[day] != null },
            hasErrors = allRows.any { it.error != null },
            currency = cat.currency,
            locked = locked,
            canEditPast = canPast,
        )
    }.stateInVm(CustomerMonthUi())

    val hasDrafts: Boolean get() = drafts.value.isNotEmpty()

    /** Switching customer or month throws away unsaved days; the screen confirms first. */
    fun setCustomer(id: Long) { drafts.value = emptyMap(); customerId.value = id; query.value = "" }
    fun setMonth(m: YearMonth) { drafts.value = emptyMap(); month.value = m }

    /** Neighbouring customer in route order (-1 = previous, +1 = next), or null at the ends. */
    fun neighbour(dir: Int): CustomerEntity? {
        val s = ui.value
        val i = s.customers.indexOfFirst { it.id == s.customer?.id }
        return if (i < 0) null else s.customers.getOrNull(i + dir)
    }

    fun blockedByDrafts(): Boolean {
        if (drafts.value.isEmpty()) return false
        c.notify(UiText.res(R.string.msg_save_first), MessageKind.ERROR)
        return true
    }

    fun setSession(s: DeliverySession) { if (!blockedByDrafts()) session.value = s }
    fun setProduct(id: Long) {
        if (blockedByDrafts()) return
        productId.value = id
        viewModelScope.launch { c.prefs.setLastProduct(id) }
    }
    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: RowFilter) { filter.value = f }
    fun discard() { drafts.value = emptyMap() }

    private fun rowFor(day: Long) = ui.value.rows.firstOrNull { it.key == day }

    private fun putDraft(row: DayRow, draft: DraftEntry) {
        val ex = row.existing
        val sameAsSaved = ex != null && ex.status == draft.status &&
            ((draft.status == DeliveryStatus.DELIVERED && Quantity.format(ex.quantityMilli) == draft.text.trim()) ||
                (draft.status != DeliveryStatus.DELIVERED))
        val untouchedEmpty = ex == null && draft.status == DeliveryStatus.DELIVERED && draft.text.isBlank()
        drafts.update { m -> if (sameAsSaved || untouchedEmpty) m - row.key else m + (row.key to draft) }
    }

    fun onText(day: Long, text: String) {
        val row = rowFor(day) ?: return
        val clean = text.replace(',', '.').filter { it.isDigit() || it == '.' }.take(9)
        putDraft(row, DraftEntry(clean, DeliveryStatus.DELIVERED))
    }

    fun step(day: Long, up: Boolean) {
        val row = rowFor(day) ?: return
        val p = ui.value.product ?: return
        val current = (Quantity.parse(row.text, p.unit) as? Quantity.ParseResult.Ok)?.milli ?: 0L
        val next = if (up) current + p.stepMilli else (current - p.stepMilli).coerceAtLeast(0)
        putDraft(row, DraftEntry(if (next == 0L) "" else Quantity.format(next), DeliveryStatus.DELIVERED))
    }

    fun mark(day: Long, status: DeliveryStatus) {
        val row = rowFor(day) ?: return
        putDraft(row, DraftEntry("", status))
    }

    fun clear(day: Long) {
        val row = rowFor(day) ?: return
        putDraft(row, DraftEntry("", DeliveryStatus.DELIVERED))
    }

    /** Prefills the customer's usual quantity on every pending day of the month (unsaved until Save). */
    fun fillDefaults() {
        val s = ui.value
        val p = s.product ?: return
        val cust = s.customer ?: return
        val add = HashMap<Long, DraftEntry>()
        if (cust.defaultProductId == p.id && cust.defaultQuantityMilli > 0) {
            s.rows.filter { it.existing == null && !it.dirty && it.editable }.forEach {
                add[it.key] = DraftEntry(Quantity.format(cust.defaultQuantityMilli), DeliveryStatus.DELIVERED)
            }
        }
        drafts.update { it + add }
        c.notify(UiText.res(R.string.msg_prefilled, add.size))
    }

    fun save(onDone: () -> Unit = {}) {
        val s = ui.value
        val p = s.product ?: return
        val cust = s.customer ?: return
        if (s.hasErrors) {
            c.notify(UiText.res(R.string.err_fix_rows), MessageKind.ERROR)
            return
        }
        val current = drafts.value
        val shown = s.rows.associateBy { it.key }
        action {
            val list = ArrayList<DeliveryDraft>()
            val deletes = ArrayList<Long>()
            // Look up the saved versions directly so filtered-out days are handled too.
            val saved = c.db.deliveries().customerRange(cust.id, Dates.periodStart(s.month), Dates.periodEnd(s.month))
                .filter { it.productId == p.id && it.session == s.session }.associateBy { it.date }
            val savedDays = ArrayList<Long>()
            for ((day, d) in current) {
                val ex = saved[day]
                if (d.status == DeliveryStatus.DELIVERED && d.text.isBlank()) {
                    if (ex != null) deletes += ex.id
                    savedDays += day
                    continue
                }
                val milli = if (d.status == DeliveryStatus.DELIVERED) (Quantity.parse(d.text, p.unit) as? Quantity.ParseResult.Ok)?.milli ?: continue else 0L
                val status = if (d.status == DeliveryStatus.DELIVERED && milli == 0L) DeliveryStatus.NO_DELIVERY else d.status
                val expected = if (shown[day] != null) shown[day]?.existing?.updatedAt else ex?.updatedAt
                list += DeliveryDraft(cust.id, p.id, day, s.session, status, milli, ex?.note ?: "", expectedUpdatedAt = expected)
                savedDays += day
            }
            val r = c.deliveries.saveBatch(list, deletes)
            if (r.conflicts.isNotEmpty()) {
                // Conflicts come back keyed by customer, so work out which days were changed elsewhere and keep only those.
                val stale = list.filter { saved[it.date]?.updatedAt != it.expectedUpdatedAt }.map { it.date }.toSet()
                drafts.update { m -> m.filterKeys { it in stale } }
                c.notify(UiText.res(R.string.msg_save_conflicts, r.conflicts.size), MessageKind.ERROR)
            } else {
                drafts.update { m -> m - savedDays.toSet() }
                c.notify(UiText.res(R.string.msg_saved_entries, r.created + r.updated + r.deleted), MessageKind.SUCCESS)
            }
            onDone()
        }
    }
}
