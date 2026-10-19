package com.khodiyar.dairybilling.ui.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.repo.CustomerInput
import com.khodiyar.dairybilling.data.repo.HouseNumberComparator
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class StatusFilter { ACTIVE, INACTIVE, ALL }
enum class CustomerSort { HOUSE, NAME, DUES }

data class CustomerListUi(
    val loading: Boolean = true,
    val rows: List<Pair<CustomerEntity, Long>> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val status: StatusFilter = StatusFilter.ACTIVE,
    val sort: CustomerSort = CustomerSort.HOUSE,
    val currency: String = "â‚¹",
)

class CustomerListViewModel(c: AppContainer) : AppViewModel(c) {
    private val query = MutableStateFlow("")
    private val status = MutableStateFlow(StatusFilter.ACTIVE)
    private val sort = MutableStateFlow(CustomerSort.HOUSE)
    private val balances = MutableStateFlow<Map<Long, Long>>(emptyMap())

    init {
        viewModelScope.launch {
            c.db.changes(*com.khodiyar.dairybilling.data.db.AppDatabase.LEDGER_TABLES).collect { balances.value = c.billing.outstandingAll() }
        }
    }

    val ui = combine(c.customers.observeAll(), query, status, sort, combine(balances, c.settings.observe()) { b, s -> b to s.currencySymbol }) { all, q, st, so, (bal, cur) ->
        val needle = q.trim().lowercase()
        val filtered = all.filter {
            when (st) {
                StatusFilter.ACTIVE -> it.active
                StatusFilter.INACTIVE -> !it.active
                StatusFilter.ALL -> true
            }
        }.filter {
            needle.isEmpty() || it.name.lowercase().contains(needle) || it.code.lowercase().contains(needle) ||
                it.houseNo.lowercase().contains(needle) || it.mobile.contains(needle)
        }
        val sorted = when (so) {
            CustomerSort.HOUSE -> filtered.sortedWith(compareBy(HouseNumberComparator) { it.houseNo })
            CustomerSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            CustomerSort.DUES -> filtered.sortedByDescending { bal[it.id] ?: 0 }
        }
        CustomerListUi(false, sorted.map { it to (bal[it.id] ?: 0L) }, all.size, q, st, so, cur)
    }.stateInVm(CustomerListUi())

    val canManage get() = c.session.has(Permission.MANAGE_CUSTOMERS)
    val canSeeMoney get() = c.session.has(Permission.VIEW_REPORTS)
    fun setQuery(q: String) { query.value = q }
    fun setStatus(s: StatusFilter) { status.value = s }
    fun setSort(s: CustomerSort) { sort.value = s }
}

@Composable
fun CustomerListScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { CustomerListViewModel(it) }
    val s by vm.ui.collectAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(R.string.nav_customers), subtitle = stringResource(R.string.label_count_customers, s.total)) },
        floatingActionButton = {
            if (vm.canManage) ExtendedFloatingActionButton(
                onClick = { navigate(Routes.customerEdit()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.action_add_customer)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                AppSearchField(s.query, vm::setQuery, stringResource(R.string.hint_search_customer))
                ChoiceChips(StatusFilter.entries, s.status, {
                    when (it) {
                        StatusFilter.ACTIVE -> stringResource(R.string.filter_active)
                        StatusFilter.INACTIVE -> stringResource(R.string.filter_inactive)
                        StatusFilter.ALL -> stringResource(R.string.filter_all)
                    }
                }, vm::setStatus)
                ChoiceChips(if (vm.canSeeMoney) CustomerSort.entries else listOf(CustomerSort.HOUSE, CustomerSort.NAME), s.sort, {
                    when (it) {
                        CustomerSort.HOUSE -> stringResource(R.string.sort_house)
                        CustomerSort.NAME -> stringResource(R.string.sort_name)
                        CustomerSort.DUES -> stringResource(R.string.sort_dues)
                    }
                }, vm::setSort)
            }
            Spacer(Modifier.height(Space.s))
            when {
                s.loading -> LoadingList()
                s.total == 0 -> EmptyState(Icons.Rounded.Groups, stringResource(R.string.empty_customers_title), stringResource(R.string.empty_customers_msg),
                    action = if (vm.canManage) stringResource(R.string.action_add_customer) else null, onAction = { navigate(Routes.customerEdit()) })
                s.rows.isEmpty() -> EmptyState(Icons.Rounded.Groups, stringResource(R.string.empty_filter_title), stringResource(R.string.empty_search_msg))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(s.rows, key = { it.first.id }) { (cust, bal) ->
                        CustomerRow(cust, if (vm.canSeeMoney) bal else null, s.currency) { navigate(Routes.customer(cust.id)) }
                        ThinDivider(Modifier.padding(start = 80.dp))
                    }
                }
            }
        }
    }
}


@Composable
fun CustomerRow(c: CustomerEntity, balance: Long?, currency: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, color = AppTheme.colors.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            HouseBadge(c.houseNo, active = c.active)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.code + if (c.mobile.isNotBlank()) "  Â·  ${c.mobile}" else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, maxLines = 1)
                    if (!c.active) {
                        Spacer(Modifier.width(Space.s))
                        StatusBadge(stringResource(R.string.status_inactive), Tone.NEUTRAL, Icons.Rounded.Block)
                    }
                }
            }
            if (balance != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(kotlin.math.abs(balance), currency), style = AppType.money,
                        color = when { balance > 0 -> AppTheme.colors.onSurface; balance < 0 -> AppTheme.ext.success; else -> AppTheme.ext.textTertiary })
                    Text(
                        when { balance > 0 -> stringResource(R.string.label_due); balance < 0 -> stringResource(R.string.label_advance); else -> stringResource(R.string.label_settled) },
                        style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textSecondary,
                    )
                }
            }
        }
    }
}

// ---------------- Add / edit ----------------

class CustomerEditViewModel(c: AppContainer, val id: Long) : AppViewModel(c) {
    val existing = MutableStateFlow<CustomerEntity?>(null)
    val suggested = MutableStateFlow("")
    val loaded = MutableStateFlow(id == 0L)
    val products = c.products.observeAll().stateInVm(emptyList())
    val canEditOpening get() = c.session.has(Permission.ADJUSTMENTS)

    init {
        viewModelScope.launch {
            if (id != 0L) {
                existing.value = c.customers.get(id)
                loaded.value = true
            } else suggested.value = c.customers.suggestCode()
        }
    }

    fun save(input: CustomerInput, onDone: (Long) -> Unit) = action(success = UiText.res(R.string.msg_customer_saved)) {
        if (id == 0L) onDone(c.customers.create(input)) else { c.customers.update(id, input); onDone(id) }
    }

    fun setActive(active: Boolean, onDone: () -> Unit) = action(success = UiText.res(if (active) R.string.msg_customer_activated else R.string.msg_customer_deactivated)) {
        c.customers.setActive(id, active); onDone()
    }
}

@Composable
fun CustomerEditScreen(id: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "cedit_$id") { CustomerEditViewModel(it, id) }
    val existing by vm.existing.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val suggested by vm.suggested.collectAsState()
    val products by vm.products.collectAsState()
    val busy by vm.busy.collectAsState()
    if (!loaded) { LoadingList(); return }
    val e = existing
    var code by rememberSaveable(e, suggested) { mutableStateOf(e?.code ?: suggested) }
    var name by rememberSaveable(e) { mutableStateOf(e?.name ?: "") }
    var house by rememberSaveable(e) { mutableStateOf(e?.houseNo ?: "") }
    var mobile by rememberSaveable(e) { mutableStateOf(e?.mobile ?: "") }
    var address by rememberSaveable(e) { mutableStateOf(e?.address ?: "") }
    var notes by rememberSaveable(e) { mutableStateOf(e?.notes ?: "") }
    var opening by rememberSaveable(e) { mutableStateOf(e?.openingBalancePaise?.takeIf { it != 0L }?.let { Money.toInput(it) } ?: "") }
    var defaultProduct by rememberSaveable(e, products.size) { mutableStateOf(e?.defaultProductId ?: products.firstOrNull { it.active }?.id ?: 0L) }
    var defaultQty by rememberSaveable(e) { mutableStateOf(e?.defaultQuantityMilli?.takeIf { it > 0 }?.let { Quantity.format(it) } ?: "") }
    var route by rememberSaveable(e) { mutableStateOf(e?.routeOrder?.takeIf { it > 0 }?.toString() ?: "") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val prod: ProductEntity? = products.firstOrNull { it.id == defaultProduct }
    val qtyParse = if (defaultQty.isBlank() || prod == null) Quantity.ParseResult.Empty else Quantity.parse(defaultQty, prod.unit)
    val openingPaise = if (opening.isBlank()) 0L else Money.parseToPaise(opening)
    val qtyError = when (qtyParse) {
        is Quantity.ParseResult.TooPrecise -> stringResource(R.string.err_qty_precision, qtyParse.maxDecimals)
        Quantity.ParseResult.Invalid, Quantity.ParseResult.Negative -> stringResource(R.string.err_qty_invalid)
        Quantity.ParseResult.TooLarge -> stringResource(R.string.err_qty_large)
        else -> null
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(if (id == 0L) R.string.title_add_customer else R.string.title_edit_customer), onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.s)) {
            SectionHeader(stringResource(R.string.section_identity))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(name, { name = it }, stringResource(R.string.field_customer_name), leadingIcon = Icons.Rounded.Person, capitalizeWords = true,
                    error = if (tried && name.isBlank()) stringResource(R.string.err_required) else null)
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppTextField(house, { house = it }, stringResource(R.string.field_house), Modifier.weight(1f), leadingIcon = Icons.Rounded.Home,
                        error = if (tried && house.isBlank()) stringResource(R.string.err_required) else null)
                    AppTextField(code, { code = it.uppercase() }, stringResource(R.string.field_code), Modifier.weight(1f), leadingIcon = Icons.Rounded.Tag,
                        error = if (tried && code.isBlank()) stringResource(R.string.err_required) else null)
                }
                Spacer(Modifier.height(Space.s))
                AppTextField(mobile, { mobile = it }, stringResource(R.string.field_mobile), leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(Space.s))
                AppTextField(address, { address = it }, stringResource(R.string.field_address), singleLine = false, minLines = 2)
            }
            SectionHeader(stringResource(R.string.section_usual_delivery), Modifier.padding(top = Space.m))
            AppCard(Modifier.fillMaxWidth()) {
                val active = products.filter { it.active }
                if (active.isNotEmpty()) {
                    ChoiceChips(active, prod ?: active.first(), { it.name }, { defaultProduct = it.id })
                    Spacer(Modifier.height(Space.s))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppTextField(defaultQty, { defaultQty = it }, stringResource(R.string.field_daily_qty), Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                        suffix = prod?.displayUnit, error = qtyError, supporting = stringResource(R.string.hint_daily_qty))
                    AppTextField(route, { route = it.filter(Char::isDigit).take(4) }, stringResource(R.string.field_route), Modifier.weight(1f), leadingIcon = Icons.Rounded.Route,
                        keyboardType = KeyboardType.Number, supporting = stringResource(R.string.hint_route))
                }
            }
            SectionHeader(stringResource(R.string.section_accounts), Modifier.padding(top = Space.m))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(
                    opening, { opening = it }, stringResource(R.string.field_opening_balance), prefix = "â‚¹ ",
                    keyboardType = KeyboardType.Text, enabled = id == 0L || vm.canEditOpening,
                    error = if (openingPaise == null) stringResource(R.string.err_amount_invalid) else null,
                    supporting = stringResource(R.string.hint_opening_balance),
                )
                Spacer(Modifier.height(Space.s))
                AppTextField(notes, { notes = it }, stringResource(R.string.field_notes), leadingIcon = Icons.Rounded.Notes, singleLine = false, minLines = 2, imeAction = ImeAction.Done)
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                stringResource(R.string.action_save), icon = Icons.Rounded.Save, loading = busy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    tried = true
                    val qty = (qtyParse as? Quantity.ParseResult.Ok)?.milli ?: 0L
                    if (name.isNotBlank() && house.isNotBlank() && code.isNotBlank() && qtyError == null && openingPaise != null) {
                        vm.save(
                            CustomerInput(code, name, house, mobile, address, notes, openingPaise, prod?.id, qty, route.toIntOrNull() ?: 0),
                            onSaved,
                        )
                    }
                },
            )
            if (e != null) {
                Spacer(Modifier.height(Space.s))
                com.khodiyar.dairybilling.ui.components.SecondaryButton(
                    stringResource(if (e.active) R.string.action_deactivate else R.string.action_reactivate),
                    onClick = { vm.setActive(!e.active) { onBack() } },
                    destructive = e.active,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                Text(stringResource(R.string.hint_deactivate), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
