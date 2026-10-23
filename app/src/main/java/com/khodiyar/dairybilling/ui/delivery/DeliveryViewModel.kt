package com.khodiyar.dairybilling.ui.delivery

import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DraftEntry(val text: String, val status: DeliveryStatus)

enum class RowFilter { ALL, PENDING, DONE }

data class DeliveryRow(
    val customer: CustomerEntity,
    val existing: DeliveryEntryEntity?,
    val text: String,
    val status: DeliveryStatus?,
    val amountPaise: Long?,
    val dirty: Boolean,
    val error: UiText?,
    val editable: Boolean,
) {
    val key: Long get() = customer.id
    val isDone: Boolean get() = existing != null && !dirty
}

data class DeliveryUi(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val session: DeliverySession = DeliverySession.MORNING,
    val sessionsEnabled: Boolean = false,
    val products: List<ProductEntity> = emptyList(),
    val product: ProductEntity? = null,
    val ratePaise: Long? = null,
    val rows: List<DeliveryRow> = emptyList(),
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
    val dateEditable: Boolean = true,
)

private data class Inputs(
    val date: LocalDate,
    val session: DeliverySession,
    val productId: Long,
    val query: String,
    val filter: RowFilter,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryViewModel(c: AppContainer) : AppViewModel(c) {
    private val session = MutableStateFlow(DeliverySession.MORNING)
    private val productId = MutableStateFlow(0L)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(RowFilter.ALL)
    private val drafts = MutableStateFlow<Map<Long, DraftEntry>>(emptyMap())
    val date = c.deliveryDate

    init {
        viewModelScope.launch { productId.value = c.prefs.prefs.first().lastProductId }
    }

    private val inputs: Flow<Inputs> = combine(date, session, productId, query, filter) { d, s, p, q, f -> Inputs(d, s, p, q, f) }

    private val dayData = combine(date, session) { d, s -> d to s }.flatMapLatest { (d, s) ->
        combine(c.deliveries.observeDay(d.toEpochDay(), s), c.db.periods().observe(Dates.periodKey(Dates.periodOf(d.toEpochDay())))) { e, p -> e to (p?.locked == true) }
    }

    private val catalog = combine(c.customers.observeAll(), c.products.observeAll(), c.db.rates().observeAll(), c.settings.observe()) { cs, ps, rs, st ->
        Catalog(cs, ps, rs, st.currencySymbol, st.sessionsEnabled)
    }

    private data class Catalog(
        val customers: List<CustomerEntity>,
        val products: List<ProductEntity>,
        val rates: List<com.khodiyar.dairybilling.data.db.ProductRateEntity>,
        val currency: String,
        val sessionsEnabled: Boolean,
    )

    val ui = combine(inputs, dayData, catalog, drafts) { inp, (entries, locked), cat, dr ->
        val activeProducts = cat.products.filter { it.active }
        val product = activeProducts.firstOrNull { it.id == inp.productId } ?: activeProducts.firstOrNull()
        val day = inp.date.toEpochDay()
        val rate = product?.let { p -> cat.rates.filter { it.productId == p.id && it.effectiveFrom <= day }.maxByOrNull { it.effectiveFrom }?.ratePaise }
        val today = c.time.today()
        val me = c.session.current
        val canPast = c.session.has(Permission.EDIT_PAST_DELIVERIES)
        val dateEditable = !locked && inp.date <= today && (inp.date == today || canPast)
        val byCustomer = entries.filter { it.productId == product?.id }.associateBy { it.customerId }
        val visibleCustomers = cat.customers.filter { it.active || byCustomer.containsKey(it.id) }
            .sortedWith(compareBy<CustomerEntity>({ if (it.routeOrder > 0) 0 else 1 }, { it.routeOrder }).thenComparing({ it.houseNo }, HouseNumberComparator))
        val unit = product?.unit
        val allRows = visibleCustomers.map { cust ->
            val ex = byCustomer[cust.id]
            val d = dr[cust.id]
            val exText = if (ex != null && ex.status == DeliveryStatus.DELIVERED) Quantity.format(ex.quantityMilli) else ""
            val text = d?.text ?: exText
            val status = d?.status ?: ex?.status
            var error: UiText? = null
            var amount: Long? = null
            if (d != null && unit != null && d.status == DeliveryStatus.DELIVERED && d.text.isNotBlank()) {
                when (val r = Quantity.parse(d.text, unit)) {
                    is Quantity.ParseResult.Ok -> amount = rate?.let { BillingEngine.lineAmount(r.milli, it) }
                    is Quantity.ParseResult.TooPrecise -> error = UiText.res(R.string.err_qty_precision, r.maxDecimals)
                    Quantity.ParseResult.TooLarge -> error = UiText.res(R.string.err_qty_large)
                    else -> error = UiText.res(R.string.err_qty_invalid)
                }
                if (error == null && rate == null) error = UiText.res(R.string.err_no_rate_short)
            } else if (d == null && ex != null) {
                amount = ex.amountPaise
            }
            val editable = dateEditable && (ex == null || ex.createdBy == me?.id || canPast)
            DeliveryRow(cust, ex, text, status, amount, d != null, error, editable)
        }
        val q = inp.query.trim().lowercase()
        val searched = if (q.isEmpty()) allRows else allRows.filter {
            it.customer.name.lowercase().contains(q) || it.customer.houseNo.lowercase().contains(q) ||
                it.customer.code.lowercase().contains(q) || it.customer.mobile.contains(q)
        }
        val filtered = when (inp.filter) {
            RowFilter.ALL -> searched
            RowFilter.PENDING -> searched.filter { it.existing == null || it.dirty }
            RowFilter.DONE -> searched.filter { it.isDone }
        }
        val saved = byCustomer.values
        DeliveryUi(
            loading = false,
            date = inp.date,
            today = today,
            session = inp.session,
            sessionsEnabled = cat.sessionsEnabled,
            products = activeProducts,
            product = product,
            ratePaise = rate,
            rows = filtered,
            filter = inp.filter,
            query = inp.query,
            totalCount = allRows.size,
            pendingCount = allRows.count { it.existing == null },
            doneCount = allRows.count { it.existing != null },
            savedQuantityMilli = saved.filter { it.status == DeliveryStatus.DELIVERED }.sumOf { it.quantityMilli },
            savedAmountPaise = saved.sumOf { it.amountPaise },
            unsavedCount = dr.size,
            pendingDeletes = dr.count { (id, d) -> d.text.isBlank() && d.status == DeliveryStatus.DELIVERED && byCustomer[id] != null },
            hasErrors = allRows.any { it.error != null },
            currency = cat.currency,
            locked = locked,
            dateEditable = dateEditable,
        )
    }.stateInVm(DeliveryUi())

    val hasDrafts: Boolean get() = drafts.value.isNotEmpty()

    fun setDate(d: LocalDate) { drafts.value = emptyMap(); date.value = d }
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

    private fun rowFor(id: Long) = ui.value.rows.firstOrNull { it.customer.id == id }

    private fun putDraft(row: DeliveryRow, draft: DraftEntry) {
        val ex = row.existing
        val sameAsSaved = ex != null && ex.status == draft.status &&
            ((draft.status == DeliveryStatus.DELIVERED && Quantity.format(ex.quantityMilli) == draft.text.trim()) ||
                (draft.status != DeliveryStatus.DELIVERED))
        val untouchedEmpty = ex == null && draft.status == DeliveryStatus.DELIVERED && draft.text.isBlank()
        drafts.update { m -> if (sameAsSaved || untouchedEmpty) m - row.customer.id else m + (row.customer.id to draft) }
    }

    fun onText(customerId: Long, text: String) {
        val row = rowFor(customerId) ?: return
        val clean = text.replace(',', '.').filter { it.isDigit() || it == '.' }.take(9)
        putDraft(row, DraftEntry(clean, DeliveryStatus.DELIVERED))
    }

    fun step(customerId: Long, up: Boolean) {
        val row = rowFor(customerId) ?: return
        val p = ui.value.product ?: return
        val current = (Quantity.parse(row.text, p.unit) as? Quantity.ParseResult.Ok)?.milli ?: 0L
        val next = if (up) current + p.stepMilli else (current - p.stepMilli).coerceAtLeast(0)
        putDraft(row, DraftEntry(if (next == 0L) "" else Quantity.format(next), DeliveryStatus.DELIVERED))
    }

    fun mark(customerId: Long, status: DeliveryStatus) {
        val row = rowFor(customerId) ?: return
        putDraft(row, DraftEntry("", status))
    }

    fun clear(customerId: Long) {
        val row = rowFor(customerId) ?: return
        putDraft(row, DraftEntry("", DeliveryStatus.DELIVERED))
    }

    /** Prefills each pending customer's usual quantity for the selected product (unsaved until Save). */
    fun fillDefaults() {
        val s = ui.value
        val p = s.product ?: return
        var n = 0
        val add = HashMap<Long, DraftEntry>()
        s.rows.filter { it.existing == null && !it.dirty && it.editable && it.customer.defaultProductId == p.id && it.customer.defaultQuantityMilli > 0 }.forEach {
            add[it.customer.id] = DraftEntry(Quantity.format(it.customer.defaultQuantityMilli), DeliveryStatus.DELIVERED)
            n++
        }
        drafts.update { it + add }
        c.notify(UiText.res(R.string.msg_prefilled, n))
    }

    fun copyPreviousDay() {
        val s = ui.value
        val p = s.product ?: return
        viewModelScope.launch {
            val prev = c.deliveries.forDayProduct(s.date.minusDays(1).toEpochDay(), s.session, p.id).associateBy { it.customerId }
            val add = HashMap<Long, DraftEntry>()
            s.rows.filter { it.existing == null && !it.dirty && it.editable }.forEach { r ->
                prev[r.customer.id]?.let { e ->
                    add[r.customer.id] = DraftEntry(if (e.status == DeliveryStatus.DELIVERED) Quantity.format(e.quantityMilli) else "", e.status)
                }
            }
            drafts.update { it + add }
            c.notify(UiText.res(R.string.msg_prefilled, add.size))
        }
    }

    /** Individual entry: every active product for one customer on the selected date/session. */
    suspend fun loadCustomerDay(customerId: Long): List<Pair<ProductEntity, DeliveryEntryEntity?>> {
        val s = ui.value
        val day = s.date.toEpochDay()
        return s.products.map { p -> p to c.db.deliveries().findKey(customerId, p.id, day, s.session) }
    }

    suspend fun rateOn(productId: Long): Long? = c.products.rateOn(productId, ui.value.date.toEpochDay())?.ratePaise

    fun saveCustomerDay(customerId: Long, values: Map<ProductEntity, DraftEntry>, loaded: Map<Long, DeliveryEntryEntity?>, onDone: () -> Unit) {
        val s = ui.value
        val day = s.date.toEpochDay()
        action {
            val list = ArrayList<DeliveryDraft>()
            val deletes = ArrayList<Long>()
            for ((p, d) in values) {
                val ex = loaded[p.id]
                if (d.status == DeliveryStatus.DELIVERED && d.text.isBlank()) {
                    if (ex != null) deletes += ex.id
                    continue
                }
                val milli = if (d.status == DeliveryStatus.DELIVERED) {
                    when (val r = Quantity.parse(d.text, p.unit)) {
                        is Quantity.ParseResult.Ok -> r.milli
                        is Quantity.ParseResult.TooPrecise -> throw com.khodiyar.dairybilling.domain.AppException(R.string.err_qty_precision, r.maxDecimals)
                        else -> throw com.khodiyar.dairybilling.domain.AppException(R.string.err_qty_invalid)
                    }
                } else 0L
                val status = if (d.status == DeliveryStatus.DELIVERED && milli == 0L) DeliveryStatus.NO_DELIVERY else d.status
                if (ex != null && ex.status == status && ex.quantityMilli == milli) continue
                list += DeliveryDraft(customerId, p.id, day, s.session, status, milli, ex?.note ?: "", ex?.updatedAt)
            }
            val r = c.deliveries.saveBatch(list, deletes)
            drafts.update { it - customerId }
            if (r.conflicts.isNotEmpty()) c.notify(UiText.res(R.string.msg_save_conflicts, r.conflicts.size), MessageKind.ERROR)
            else c.notify(UiText.res(R.string.msg_saved_entries, r.created + r.updated + r.deleted), MessageKind.SUCCESS)
            onDone()
        }
    }

    fun save(onDone: () -> Unit = {}) {
        val s = ui.value
        val p = s.product ?: return
        if (s.hasErrors) {
            c.notify(UiText.res(R.string.err_fix_rows), MessageKind.ERROR)
            return
        }
        val current = drafts.value
        val allRows = ui.value.rows.associateBy { it.customer.id }
        val day = s.date.toEpochDay()
        action {
            val list = ArrayList<DeliveryDraft>()
            val deletes = ArrayList<Long>()
            // Look up the saved version directly so filtered-out rows are handled too.
            val saved = c.deliveries.forDayProduct(day, s.session, p.id).associateBy { it.customerId }
            for ((cid, d) in current) {
                val ex = saved[cid]
                if (d.status == DeliveryStatus.DELIVERED && d.text.isBlank()) {
                    if (ex != null) deletes += ex.id
                    continue
                }
                val milli = if (d.status == DeliveryStatus.DELIVERED) (Quantity.parse(d.text, p.unit) as? Quantity.ParseResult.Ok)?.milli ?: continue else 0L
                val status = if (d.status == DeliveryStatus.DELIVERED && milli == 0L) DeliveryStatus.NO_DELIVERY else d.status
                val expected = allRows[cid]?.existing?.updatedAt ?: ex?.updatedAt
                list += DeliveryDraft(cid, p.id, day, s.session, status, milli, ex?.note ?: "", expectedUpdatedAt = if (allRows[cid] != null) allRows[cid]?.existing?.updatedAt else expected)
            }
            val r = c.deliveries.saveBatch(list, deletes)
            drafts.update { m -> m.filterKeys { it in r.conflicts } }
            if (r.conflicts.isNotEmpty()) {
                c.notify(UiText.res(R.string.msg_save_conflicts, r.conflicts.size), MessageKind.ERROR)
            } else {
                c.notify(UiText.res(R.string.msg_saved_entries, r.created + r.updated + r.deleted), MessageKind.SUCCESS)
            }
            onDone()
        }
    }
}
