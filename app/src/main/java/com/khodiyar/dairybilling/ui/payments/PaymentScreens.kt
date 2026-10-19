package com.khodiyar.dairybilling.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.data.repo.HouseNumberComparator
import com.khodiyar.dairybilling.data.repo.PaymentInput
import com.khodiyar.dairybilling.data.repo.PaymentSaveResult
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.RecordStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.customers.PaymentRow
import com.khodiyar.dairybilling.ui.customers.methodLabel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.DateField
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.KeyValueRow
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.MetricTile
import com.khodiyar.dairybilling.ui.components.MonthStepper
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SecondaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModel(c: AppContainer) : AppViewModel(c) {
    val month = MutableStateFlow(YearMonth.from(c.time.today()))
    val method = MutableStateFlow<PaymentMethod?>(null)
    val currentMonth: YearMonth get() = YearMonth.from(c.time.today())
    val payments = month.flatMapLatest { m -> c.payments.observeRange(Dates.periodStart(m), Dates.periodEnd(m)) }
    val data = combine(payments, method, c.customers.observeAll(), c.settings.observe()) { list, m, cs, st ->
        val byId = cs.associateBy { it.id }
        Triple(list.filter { m == null || it.method == m }.map { it to byId[it.customerId] }, st.currencySymbol, list)
    }.stateInVm(null)
    fun can(p: Permission) = c.session.has(p)
    fun reverse(p: PaymentEntity, reason: String) = action(success = UiText.res(R.string.msg_payment_reversed)) { c.payments.reverse(p.id, reason) }
}

@Composable
fun PaymentsScreen(onBack: (() -> Unit)?, navigate: (String) -> Unit) {
    val vm = appViewModel { PaymentsViewModel(it) }
    val month by vm.month.collectAsState()
    val method by vm.method.collectAsState()
    val data by vm.data.collectAsState()
    var reversing by remember { mutableStateOf<PaymentEntity?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(R.string.nav_payments), onBack = onBack) },
        floatingActionButton = {
            if (vm.can(Permission.RECORD_PAYMENTS)) ExtendedFloatingActionButton(onClick = { navigate(Routes.paymentNew()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.action_record_payment)) })
        },
    ) { padding ->
        val d = data
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    MonthStepper(month, { vm.month.value = it }, Modifier.fillMaxWidth(), maxMonth = vm.currentMonth)
                    ChoiceChips(listOf<PaymentMethod?>(null) + PaymentMethod.entries, method, { it?.let { m -> methodLabel(m) } ?: stringResource(R.string.filter_all) }, { vm.method.value = it })
                    if (d != null && vm.can(Permission.VIEW_REPORTS)) {
                        val active = d.third.filter { it.status == RecordStatus.ACTIVE }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            MetricTile(stringResource(R.string.metric_collected), Money.format(active.sumOf { it.amountPaise }, d.second), Modifier.weight(1f), icon = Icons.Rounded.Payments, tone = Tone.SUCCESS)
                            MetricTile(stringResource(R.string.metric_receipts), active.size.toString(), Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.ReceiptLong)
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
            if (d == null) item { LoadingList() }
            else if (d.first.isEmpty()) item { EmptyState(Icons.Rounded.Payments, stringResource(R.string.empty_payments), stringResource(R.string.empty_payments_month)) }
            else items(d.first, key = { it.first.id }) { (p, cust) ->
                PaymentRow(
                    p, d.second,
                    title = cust?.let { "${it.houseNo} · ${it.name}" } ?: "—",
                    subtitle = Dates.formatDay(p.date) + "  ·  ",
                    onClick = { navigate(Routes.receiptPdf(p.id)) },
                    onReverse = if (vm.can(Permission.REVERSE_PAYMENTS) && p.status == RecordStatus.ACTIVE) ({ reversing = p }) else null,
                )
                ThinDivider(Modifier.padding(horizontal = Space.screen))
            }
        }
    }
    reversing?.let { p ->
        PinConfirmDialog(
            title = stringResource(R.string.dlg_reverse_payment_title),
            message = stringResource(R.string.dlg_reverse_payment_msg, Money.format(p.amountPaise, data?.second ?: "₹"), Dates.formatDay(p.date)),
            confirmLabel = stringResource(R.string.action_reverse),
            verify = vm::verifyPin, onConfirmed = {}, onDismiss = { reversing = null },
            requireReason = true, reasonLabel = stringResource(R.string.field_reason),
            onConfirmedWithReason = { vm.reverse(p, it) },
        )
    }
}

// ------------- Record payment -------------

class PaymentEntryViewModel(c: AppContainer, initialCustomer: Long) : AppViewModel(c) {
    /** One idempotency key per form; regenerated only after a successful save. */
    var requestId: String = UUID.randomUUID().toString()
        private set
    val customerId = MutableStateFlow(initialCustomer)
    val customers = c.customers.observeAll().stateInVm(emptyList())
    val settings = c.settings.observe().stateInVm(null)
    val today: LocalDate get() = c.time.today()
    val duplicate = MutableStateFlow<PaymentEntity?>(null)
    val saved = MutableStateFlow<PaymentEntity?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val outstanding = combine(customerId, c.db.changes(*AppDatabase.LEDGER_TABLES)) { id, _ -> id }
        .mapLatest { id -> if (id == 0L) null else c.billing.outstandingAll()[id] }.stateInVm(null)

    fun save(amount: Long, method: PaymentMethod, date: LocalDate, ref: String, confirmDuplicate: Boolean = false) {
        val id = customerId.value
        if (id == 0L) { c.notify(UiText.res(R.string.err_select_customer), MessageKind.ERROR); return }
        action {
            when (val r = c.payments.record(PaymentInput(id, date.toEpochDay(), amount, method, ref, requestId), confirmDuplicate)) {
                is PaymentSaveResult.Saved -> { saved.value = r.payment; requestId = UUID.randomUUID().toString(); c.notify(UiText.res(R.string.msg_payment_saved), MessageKind.SUCCESS) }
                is PaymentSaveResult.AlreadySaved -> { saved.value = r.payment; c.notify(UiText.res(R.string.msg_payment_already), MessageKind.INFO) }
                is PaymentSaveResult.PossibleDuplicate -> duplicate.value = r.existing
            }
        }
    }
}

@Composable
fun PaymentEntryScreen(initialCustomer: Long, onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel(key = "pay_$initialCustomer") { PaymentEntryViewModel(it, initialCustomer) }
    val customerId by vm.customerId.collectAsState()
    val customers by vm.customers.collectAsState()
    val settings by vm.settings.collectAsState()
    val outstanding by vm.outstanding.collectAsState()
    val duplicate by vm.duplicate.collectAsState()
    val saved by vm.saved.collectAsState()
    val busy by vm.busy.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    var amount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var date by remember { mutableStateOf(vm.today) }
    var ref by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(initialCustomer == 0L) }
    var query by rememberSaveable { mutableStateOf("") }
    val customer = customers.firstOrNull { it.id == customerId }
    val paise = Money.parseToPaise(amount)

    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { AppTopBar(stringResource(R.string.action_record_payment), onBack = onBack) }) { padding ->
        if (saved != null) {
            val p = saved!!
            Column(Modifier.fillMaxSize().padding(padding).padding(Space.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(Space.xxl))
                Icon(Icons.Rounded.CheckCircle, null, tint = AppTheme.ext.success, modifier = Modifier.heightIn(min = 64.dp).width(64.dp))
                Spacer(Modifier.height(Space.m))
                Text(Money.format(p.amountPaise, cur), style = AppType.moneyLarge)
                Text(stringResource(R.string.msg_payment_saved_for, customer?.name ?: ""), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary)
                outstanding?.let {
                    Spacer(Modifier.height(Space.s))
                    Text(stringResource(if (it < 0) R.string.label_advance_now else R.string.label_outstanding_now, Money.format(kotlin.math.abs(it), cur)), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(Space.xxl))
                PrimaryButton(stringResource(R.string.action_share_receipt), { navigate(Routes.receiptPdf(p.id)) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.ReceiptLong)
                Spacer(Modifier.height(Space.s))
                SecondaryButton(stringResource(R.string.action_record_another), {
                    vm.saved.value = null; amount = ""; ref = ""; vm.customerId.value = 0; picking = true
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.s))
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_done)) }
            }
            return@Scaffold
        }
        if (picking || customer == null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                AppSearchField(query, { query = it }, stringResource(R.string.hint_search_customer), Modifier.padding(horizontal = Space.screen))
                Spacer(Modifier.height(Space.s))
                val q = query.trim().lowercase()
                val list = customers.filter { it.active }.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.houseNo.lowercase().contains(q) || it.code.lowercase().contains(q) || it.mobile.contains(q) }
                    .sortedWith(compareBy(HouseNumberComparator) { it.houseNo })
                LazyColumn {
                    items(list, key = { it.id }) { c ->
                        androidx.compose.material3.Surface(onClick = { vm.customerId.value = c.id; picking = false }, color = AppTheme.colors.background) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                                HouseBadge(c.houseNo)
                                Spacer(Modifier.width(Space.m))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.code, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            AppCard(Modifier.fillMaxWidth(), onClick = { picking = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HouseBadge(customer.houseNo)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(customer.code, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.action_change_customer), tint = AppTheme.colors.primary)
                }
                outstanding?.let {
                    Spacer(Modifier.height(Space.s))
                    ThinDivider()
                    KeyValueRow(stringResource(if (it < 0) R.string.label_advance_balance else R.string.label_outstanding), Money.format(kotlin.math.abs(it), cur), emphasize = true)
                }
            }
            Spacer(Modifier.height(Space.l))
            AppTextField(amount, { amount = it }, stringResource(R.string.field_amount), prefix = "$cur ", keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done,
                error = if (amount.isNotBlank() && (paise == null || paise <= 0)) stringResource(R.string.err_amount_invalid) else null)
            outstanding?.takeIf { it > 0 }?.let { due ->
                TextButton(onClick = { amount = Money.toInput(due) }) { Text(stringResource(R.string.action_pay_full, Money.format(due, cur))) }
            }
            Spacer(Modifier.height(Space.s))
            SectionHeader(stringResource(R.string.field_method))
            ChoiceChips(PaymentMethod.entries, method, { methodLabel(it) }, { method = it })
            Spacer(Modifier.height(Space.m))
            DateField(stringResource(R.string.field_payment_date), date, { date = it }, maxDate = vm.today)
            Spacer(Modifier.height(Space.s))
            AppTextField(ref, { ref = it }, stringResource(R.string.field_reference), supporting = stringResource(R.string.hint_reference), imeAction = ImeAction.Done)
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                stringResource(R.string.action_save_payment), { vm.save(paise!!, method, date, ref) }, Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Save, enabled = paise != null && paise > 0, loading = busy,
            )
            Spacer(Modifier.height(Space.s))
            Text(stringResource(R.string.hint_payment_policy), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
            Spacer(Modifier.height(Space.xxl))
        }
    }
    duplicate?.let { d ->
        AlertDialog(
            onDismissRequest = { vm.duplicate.value = null },
            title = { Text(stringResource(R.string.dlg_duplicate_title)) },
            text = { Text(stringResource(R.string.dlg_duplicate_msg, Money.format(d.amountPaise, cur), Dates.formatDateTime(d.createdAt))) },
            confirmButton = { Button(onClick = { vm.duplicate.value = null; vm.save(paise ?: return@Button, method, date, ref, confirmDuplicate = true) }) { Text(stringResource(R.string.action_record_anyway)) } },
            dismissButton = { TextButton(onClick = { vm.duplicate.value = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
