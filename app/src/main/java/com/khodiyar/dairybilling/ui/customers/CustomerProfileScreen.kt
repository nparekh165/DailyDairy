@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.BillingAdjustmentEntity
import com.khodiyar.dairybilling.data.db.PaymentEntity
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.model.AdjustmentType
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.model.InvoiceStatus
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.model.RecordStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.share.ShareHelper
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.DateField
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.KeyValueRow
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.TonalButton
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

class CustomerProfileViewModel(c: AppContainer, val id: Long) : AppViewModel(c) {
    val customer = c.customers.observe(id).stateInVm(null)
    val ledger = c.billing.observeLedger(id).stateInVm(null)
    private val today = c.time.today()
    val deliveries = c.deliveries.observeCustomer(id, today.minusDays(120).toEpochDay(), today.toEpochDay()).stateInVm(emptyList())
    val payments = c.payments.observeForCustomer(id).stateInVm(emptyList())
    val adjustments = c.payments.observeAdjustments(id).stateInVm(emptyList())
    val invoices = c.billing.observeInvoicesForCustomer(id).stateInVm(emptyList())
    val products = combine(c.products.observeAll(), c.settings.observe()) { p, s -> p.associateBy { it.id } to s.currencySymbol }.stateInVm(emptyMap<Long, com.khodiyar.dairybilling.data.db.ProductEntity>() to "₹")
    val now: LocalDate get() = c.time.today()
    fun can(p: Permission) = c.session.has(p)

    fun reverse(p: PaymentEntity, reason: String) = action(success = UiText.res(R.string.msg_payment_reversed)) { c.payments.reverse(p.id, reason) }
    fun addAdjustment(type: AdjustmentType, amount: Long, date: LocalDate, reason: String, onDone: () -> Unit) =
        action(success = UiText.res(R.string.msg_adjustment_added)) { c.payments.addAdjustment(id, date.toEpochDay(), type, amount, reason); onDone() }
    fun voidAdjustment(a: BillingAdjustmentEntity) = action(success = UiText.res(R.string.msg_adjustment_voided)) { c.payments.voidAdjustment(a.id) }
}

@Composable
fun methodLabel(m: PaymentMethod): String = when (m) {
    PaymentMethod.CASH -> stringResource(R.string.method_cash)
    PaymentMethod.UPI -> stringResource(R.string.method_upi)
    PaymentMethod.BANK_TRANSFER -> stringResource(R.string.method_bank)
    PaymentMethod.OTHER -> stringResource(R.string.method_other)
}

@Composable
fun CustomerProfileScreen(id: Long, onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel(key = "cprof_$id") { CustomerProfileViewModel(it, id) }
    val customer by vm.customer.collectAsState()
    val ledger by vm.ledger.collectAsState()
    val deliveries by vm.deliveries.collectAsState()
    val payments by vm.payments.collectAsState()
    val adjustments by vm.adjustments.collectAsState()
    val invoices by vm.invoices.collectAsState()
    val pc by vm.products.collectAsState()
    val (products, cur) = pc
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var reversing by remember { mutableStateOf<PaymentEntity?>(null) }
    var voiding by remember { mutableStateOf<BillingAdjustmentEntity?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val c = customer
    val showMoney = vm.can(Permission.VIEW_REPORTS)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(c?.name ?: "", subtitle = c?.let { "${it.code} · ${it.houseNo}" }, onBack = onBack, actions = {
                if (c != null && c.mobile.isNotBlank()) IconButton(onClick = { ShareHelper.dial(context, c.mobile) }) { Icon(Icons.Rounded.Call, stringResource(R.string.action_call)) }
                if (vm.can(Permission.MANAGE_CUSTOMERS)) IconButton(onClick = { navigate(Routes.customerEdit(id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            })
        },
    ) { padding ->
        if (c == null) { LoadingList(); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Space.xxl)) {
            item {
                Column(Modifier.padding(horizontal = Space.screen)) {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HouseBadge(c.houseNo, active = c.active)
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(c.code, c.mobile).filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                            }
                            if (!c.active) StatusBadge(stringResource(R.string.status_inactive), Tone.NEUTRAL, Icons.Rounded.Block)
                        }
                        if (c.address.isNotBlank()) {
                            Spacer(Modifier.height(Space.s))
                            Text(c.address, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                        }
                        if (showMoney) {
                            Spacer(Modifier.height(Space.l))
                            val out = ledger?.outstandingPaise
                            Text(stringResource(if ((out ?: 0) < 0) R.string.label_advance_balance else R.string.label_outstanding).uppercase(), style = AppType.overline, color = AppTheme.ext.textSecondary)
                            Text(out?.let { Money.format(kotlin.math.abs(it), cur) } ?: "…", style = AppType.moneyLarge,
                                color = if ((out ?: 0) > 0) AppTheme.colors.onSurface else AppTheme.ext.success)
                        }
                        if (c.defaultProductId != null && c.defaultQuantityMilli > 0) {
                            Spacer(Modifier.height(Space.xs))
                            val p = products[c.defaultProductId]
                            Text(stringResource(R.string.label_usual, Quantity.formatWithUnit(c.defaultQuantityMilli, p?.displayUnit ?: ""), p?.name ?: ""), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        if (vm.can(Permission.RECORD_PAYMENTS)) TonalButton(stringResource(R.string.action_payment), { navigate(Routes.paymentNew(id)) }, Modifier.weight(1f), Icons.Rounded.Payments)
                        if (vm.can(Permission.VIEW_BILLING)) TonalButton(stringResource(R.string.action_bill), { navigate(Routes.invoice(id, Dates.periodKey(YearMonth.from(vm.now)))) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.ReceiptLong)
                        if (vm.can(Permission.ADJUSTMENTS)) TonalButton(stringResource(R.string.action_adjust), { adjusting = true }, Modifier.weight(1f), Icons.Rounded.PlaylistAdd)
                    }
                }
            }
            item {
                val tabs = buildList {
                    add(R.string.tab_ledger)
                    add(R.string.tab_deliveries)
                    add(R.string.tab_payments)
                    add(R.string.tab_bills)
                }
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = Space.screen, containerColor = AppTheme.colors.background, modifier = Modifier.padding(top = Space.m)) {
                    tabs.forEachIndexed { i, res -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) }) }
                }
                Spacer(Modifier.height(Space.s))
            }
            when (tab) {
                0 -> {
                    val l = ledger
                    if (l == null) item { LoadingList(3) }
                    else if (!showMoney) item { EmptyState(Icons.Rounded.Block, stringResource(R.string.err_not_permitted), "") }
                    else {
                        if (c.openingBalancePaise != 0L) item {
                            Text(stringResource(R.string.label_opening_balance_value, Money.format(c.openingBalancePaise, cur)),
                                style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, modifier = Modifier.padding(horizontal = Space.screen))
                        }
                        items(l.months, key = { it.period.toString() }) { m ->
                            AppCard(
                                Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.xs),
                                onClick = if (vm.can(Permission.VIEW_BILLING)) ({ navigate(Routes.invoice(id, Dates.periodKey(m.period))) }) else null,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Dates.formatMonth(m.period), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    if (m.invoiceNumber != null) StatusBadge(stringResource(R.string.status_finalized), Tone.BRAND, Icons.Rounded.CheckCircle)
                                    else StatusBadge(stringResource(R.string.status_draft), Tone.NEUTRAL)
                                }
                                Spacer(Modifier.height(Space.xs))
                                KeyValueRow(stringResource(R.string.pdf_previous), Money.format(m.summary.previousBalancePaise, cur))
                                KeyValueRow(stringResource(R.string.pdf_charges), Money.format(m.summary.chargesPaise, cur))
                                if (m.summary.adjustmentsPaise != 0L) KeyValueRow(stringResource(R.string.pdf_adjustments), Money.format(m.summary.adjustmentsPaise, cur))
                                KeyValueRow(stringResource(R.string.pdf_payments), "- " + Money.format(m.summary.paymentsPaise, cur))
                                ThinDivider()
                                KeyValueRow(stringResource(R.string.label_closing), Money.format(m.summary.closingBalancePaise, cur), emphasize = true)
                            }
                        }
                    }
                }
                1 -> {
                    if (deliveries.isEmpty()) item { EmptyState(Icons.Rounded.EventBusy, stringResource(R.string.empty_deliveries), stringResource(R.string.empty_deliveries_msg)) }
                    items(deliveries, key = { it.id }) { e ->
                        val p = products[e.productId]
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Dates.formatDayShort(LocalDate.ofEpochDay(e.date)), style = MaterialTheme.typography.titleSmall)
                                Text((p?.name ?: "") + if (showMoney && e.status == DeliveryStatus.DELIVERED) "  ·  " + Money.format(e.ratePaise, cur) + " / " + (p?.displayUnit ?: "") else "",
                                    style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                            }
                            when (e.status) {
                                DeliveryStatus.DELIVERED -> Column(horizontalAlignment = Alignment.End) {
                                    Text(Quantity.formatWithUnit(e.quantityMilli, p?.displayUnit ?: ""), style = AppType.money)
                                    if (showMoney) Text(Money.format(e.amountPaise, cur), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                                }
                                DeliveryStatus.NO_DELIVERY -> StatusBadge(stringResource(R.string.status_no_delivery), Tone.NEUTRAL, Icons.Rounded.DoNotDisturbOn)
                                DeliveryStatus.MISSED -> StatusBadge(stringResource(R.string.status_missed), Tone.DANGER, Icons.Rounded.EventBusy)
                            }
                        }
                        ThinDivider(Modifier.padding(horizontal = Space.screen))
                    }
                }
                2 -> {
                    if (payments.isEmpty() && adjustments.isEmpty()) item { EmptyState(Icons.Rounded.Payments, stringResource(R.string.empty_payments), stringResource(R.string.empty_payments_msg)) }
                    items(payments, key = { "p" + it.id }) { p ->
                        PaymentRow(p, cur, onReceipt = { navigate(Routes.receiptPdf(p.id)) }, onReverse = if (vm.can(Permission.REVERSE_PAYMENTS) && p.status == RecordStatus.ACTIVE) ({ reversing = p }) else null)
                        ThinDivider(Modifier.padding(horizontal = Space.screen))
                    }
                    if (adjustments.isNotEmpty()) item {
                        com.khodiyar.dairybilling.ui.components.SectionHeader(stringResource(R.string.section_adjustments), Modifier.padding(horizontal = Space.screen))
                    }
                    items(adjustments, key = { "a" + it.id }) { a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.reason, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(Dates.formatDay(a.date) + "  ·  " + stringResource(if (a.type == AdjustmentType.CREDIT) R.string.adj_credit else R.string.adj_debit),
                                    style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text((if (a.type == AdjustmentType.CREDIT) "- " else "+ ") + Money.format(a.amountPaise, cur), style = AppType.money,
                                    color = if (a.status == RecordStatus.ACTIVE) AppTheme.colors.onSurface else AppTheme.ext.textTertiary)
                                if (a.status != RecordStatus.ACTIVE) StatusBadge(stringResource(R.string.status_voided), Tone.NEUTRAL)
                                else if (vm.can(Permission.ADJUSTMENTS)) TextButton(onClick = { voiding = a }) { Text(stringResource(R.string.action_void)) }
                            }
                        }
                    }
                }
                3 -> {
                    if (invoices.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.empty_invoices), stringResource(R.string.empty_invoices_msg)) }
                    items(invoices, key = { it.id }) { inv ->
                        AppCard(
                            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.xs),
                            onClick = { navigate(Routes.invoicePdf(id, inv.periodKey, inv.id)) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(inv.invoiceNumber, style = MaterialTheme.typography.titleSmall)
                                    Text(Dates.formatMonth(Dates.fromPeriodKey(inv.periodKey)) + "  ·  " + Dates.formatDateTime(inv.finalizedAt), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(inv.closingBalancePaise, cur), style = AppType.money)
                                    if (inv.status == InvoiceStatus.SUPERSEDED) StatusBadge(stringResource(R.string.status_superseded), Tone.NEUTRAL, Icons.Rounded.Undo)
                                    else StatusBadge(stringResource(R.string.status_finalized), Tone.BRAND, Icons.Rounded.CheckCircle)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reversing?.let { p ->
        PinConfirmDialog(
            title = stringResource(R.string.dlg_reverse_payment_title),
            message = stringResource(R.string.dlg_reverse_payment_msg, Money.format(p.amountPaise, cur), Dates.formatDay(p.date)),
            confirmLabel = stringResource(R.string.action_reverse),
            verify = vm::verifyPin,
            onConfirmed = {},
            onDismiss = { reversing = null },
            requireReason = true,
            reasonLabel = stringResource(R.string.field_reason),
            onConfirmedWithReason = { reason -> vm.reverse(p, reason) },
        )
    }
    voiding?.let { a ->
        PinConfirmDialog(
            title = stringResource(R.string.dlg_void_adjustment_title),
            message = stringResource(R.string.dlg_void_adjustment_msg, Money.format(a.amountPaise, cur)),
            confirmLabel = stringResource(R.string.action_void),
            verify = vm::verifyPin,
            onConfirmed = { vm.voidAdjustment(a) },
            onDismiss = { voiding = null },
        )
    }
    if (adjusting) AdjustmentDialog(vm.now, cur, onDismiss = { adjusting = false }) { type, amount, date, reason ->
        vm.addAdjustment(type, amount, date, reason) { adjusting = false }
    }
}

@Composable
fun PaymentRow(p: PaymentEntity, cur: String, title: String? = null, subtitle: String? = null, onReceipt: (() -> Unit)? = null, onReverse: (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val active = p.status == RecordStatus.ACTIVE
    androidx.compose.material3.Surface(onClick = onClick ?: onReceipt ?: {}, enabled = onClick != null || onReceipt != null, color = AppTheme.colors.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title ?: Dates.formatDay(p.date), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (subtitle ?: "") + methodLabel(p.method) + if (p.reference.isNotBlank()) "  ·  ${p.reference}" else "",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!active) Text(stringResource(R.string.label_reversed_reason, p.reversalReason), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.error, maxLines = 2)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(p.amountPaise, cur), style = AppType.money.copy(textDecoration = if (active) null else androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    color = if (active) AppTheme.ext.success else AppTheme.ext.textTertiary)
                if (!active) StatusBadge(stringResource(R.string.status_reversed), Tone.DANGER, Icons.Rounded.Undo)
                else if (onReverse != null) TextButton(onClick = onReverse, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.action_reverse)) }
            }
        }
    }
}

@Composable
private fun AdjustmentDialog(today: LocalDate, cur: String, onDismiss: () -> Unit, onSave: (AdjustmentType, Long, LocalDate, String) -> Unit) {
    var type by remember { mutableStateOf(AdjustmentType.CREDIT) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    val paise = Money.parseToPaise(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_adjustment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ChoiceChips(AdjustmentType.entries, type, { stringResource(if (it == AdjustmentType.CREDIT) R.string.adj_credit_long else R.string.adj_debit_long) }, { type = it })
                AppTextField(amount, { amount = it }, stringResource(R.string.field_amount), prefix = "$cur ", keyboardType = KeyboardType.Decimal,
                    error = if (amount.isNotBlank() && (paise == null || paise <= 0)) stringResource(R.string.err_amount_invalid) else null)
                DateField(stringResource(R.string.field_date), date, { date = it }, maxDate = today)
                AppTextField(reason, { reason = it }, stringResource(R.string.field_reason), singleLine = false, minLines = 2)
                Text(stringResource(R.string.hint_adjustment), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
            }
        },
        confirmButton = {
            Button(enabled = paise != null && paise > 0 && reason.isNotBlank(), onClick = { onSave(type, paise!!, date, reason) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
