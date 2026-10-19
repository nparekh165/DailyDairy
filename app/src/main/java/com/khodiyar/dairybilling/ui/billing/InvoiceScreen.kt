package com.khodiyar.dairybilling.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.repo.BillState
import com.khodiyar.dairybilling.data.repo.InvoiceView
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.billing.PaidState
import com.khodiyar.dairybilling.domain.model.InvoiceStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SecondaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.combine

class InvoiceViewModel(c: AppContainer, val customerId: Long, val periodKey: Int) : AppViewModel(c) {
    val period = Dates.fromPeriodKey(periodKey)
    val view = combine(c.billing.observeInvoice(customerId, period), c.prefs.features) { v, f -> v?.forFeatures(f) }.stateInVm(null)
    fun can(p: Permission) = c.session.has(p)
    fun finalize() = action(success = UiText.res(R.string.msg_invoice_finalized)) { c.billing.finalize(customerId, period) }
    fun revise(reason: String) = action(success = UiText.res(R.string.msg_invoice_revised)) {
        val inv = view.value?.invoice ?: return@action
        c.billing.revise(inv.id, reason)
    }
}

@Composable
fun InvoiceScreen(customerId: Long, periodKey: Int, onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel(key = "inv_${customerId}_$periodKey") { InvoiceViewModel(it, customerId, periodKey) }
    val v by vm.view.collectAsState()
    var finalizing by remember { mutableStateOf(false) }
    var revising by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(R.string.title_invoice), subtitle = Dates.formatMonth(vm.period), onBack = onBack) },
    ) { padding ->
        val view = v
        if (view == null) { LoadingList(); return@Scaffold }
        val cur = view.settings.currencySymbol
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                if (!view.simple) when (view.state) {
                    BillState.DRAFT -> StatusBadge(stringResource(R.string.status_draft), Tone.NEUTRAL, Icons.Rounded.EditNote)
                    BillState.FINALIZED -> StatusBadge(stringResource(R.string.status_finalized), Tone.BRAND, Icons.Rounded.CheckCircle)
                    BillState.NEEDS_REVISION -> StatusBadge(stringResource(R.string.status_needs_revision), Tone.WARNING, Icons.Rounded.Warning)
                }
                if (view.invoice != null || view.simple) when (view.paidState) {
                    PaidState.PAID, PaidState.SETTLED -> StatusBadge(stringResource(R.string.status_paid), Tone.SUCCESS, Icons.Rounded.CheckCircle)
                    PaidState.PARTIALLY_PAID -> StatusBadge(stringResource(R.string.status_partial), Tone.ACCENT)
                    PaidState.CREDIT -> StatusBadge(stringResource(R.string.status_credit), Tone.INFO)
                    PaidState.UNPAID -> StatusBadge(stringResource(R.string.status_outstanding), Tone.BRAND)
                }
                if (view.locked) StatusBadge(stringResource(R.string.status_locked), Tone.WARNING, Icons.Rounded.Lock)
            }
            Spacer(Modifier.height(Space.m))
            if (view.state == BillState.NEEDS_REVISION) {
                InlineNotice(stringResource(R.string.notice_invoice_changed, Money.format(view.live.summary.closingBalancePaise, cur)), Tone.WARNING)
                Spacer(Modifier.height(Space.m))
            }
            InvoicePreview(view)
            Spacer(Modifier.height(Space.l))
            PrimaryButton(stringResource(R.string.action_pdf_share), { navigate(Routes.invoicePdf(customerId, periodKey)) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PictureAsPdf)
            Spacer(Modifier.height(Space.s))
            if (!view.simple && vm.can(Permission.FINALIZE_INVOICES) && !view.locked) {
                if (view.invoice == null) SecondaryButton(stringResource(R.string.action_finalize_invoice), { finalizing = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle)
                else if (view.state == BillState.NEEDS_REVISION) SecondaryButton(stringResource(R.string.action_issue_revision), { revising = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.History)
            }
            if (view.history.size > 1 || view.history.any { it.status == InvoiceStatus.SUPERSEDED }) {
                SectionHeader(stringResource(R.string.section_invoice_history), Modifier.padding(top = Space.l))
                AppCard(Modifier.fillMaxWidth()) {
                    view.history.forEach { h ->
                        Surface(onClick = { navigate(Routes.invoicePdf(customerId, periodKey, h.id)) }, color = Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.invoiceNumber, style = MaterialTheme.typography.titleSmall)
                                    Text(Dates.formatDateTime(h.finalizedAt) + if (h.revisionReason.isNotBlank()) "  ·  ${h.revisionReason}" else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                                }
                                Text(Money.format(h.closingBalancePaise, cur), style = AppType.money)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
        if (finalizing) PinConfirmDialog(
            title = stringResource(R.string.dlg_finalize_title),
            message = stringResource(R.string.dlg_finalize_msg, Money.format(view.live.summary.closingBalancePaise, cur)),
            confirmLabel = stringResource(R.string.action_finalize),
            verify = vm::verifyPin, onConfirmed = { vm.finalize() }, onDismiss = { finalizing = false },
        )
        if (revising) PinConfirmDialog(
            title = stringResource(R.string.dlg_revise_title),
            message = stringResource(R.string.dlg_revise_msg, view.invoice?.invoiceNumber ?: ""),
            confirmLabel = stringResource(R.string.action_issue_revision),
            verify = vm::verifyPin, onConfirmed = {}, onDismiss = { revising = false },
            requireReason = true, reasonLabel = stringResource(R.string.field_reason), onConfirmedWithReason = { vm.revise(it) },
        )
    }
}

/** On-screen bill laid out like the PDF: header, bill-to, itemized lines, totals, amount due. */
@Composable
fun InvoicePreview(view: InvoiceView) {
    val cur = view.settings.currencySymbol
    val s = view.displaySummary
    val inv = view.invoice
    Surface(shape = MaterialTheme.shapes.large, color = AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.ext.border)) {
        Column(Modifier.fillMaxWidth().padding(Space.l)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(view.settings.dairyName, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.primary)
                    if (view.settings.phone.isNotBlank()) Text(view.settings.phone, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(if (inv == null && !view.simple) R.string.pdf_draft_invoice else R.string.pdf_invoice), style = AppType.overline,
                        color = if (inv == null) AppTheme.ext.accent else AppTheme.colors.primary)
                    Text(inv?.invoiceNumber ?: "—", style = MaterialTheme.typography.titleSmall)
                    Text(Dates.formatMonth(view.period), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Space.m), thickness = 1.5.dp, color = AppTheme.colors.primary)
            Text(stringResource(R.string.pdf_bill_to).uppercase(), style = AppType.overline, color = AppTheme.ext.textSecondary)
            Text(inv?.customerName ?: view.customer.name, style = MaterialTheme.typography.titleMedium)
            Text("${stringResource(R.string.pdf_house)}: ${inv?.houseNo ?: view.customer.houseNo}  ·  ${stringResource(R.string.pdf_code)}: ${inv?.customerCode ?: view.customer.code}",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth().background(AppTheme.colors.surfaceContainer, MaterialTheme.shapes.small).padding(horizontal = Space.s, vertical = 6.dp)) {
                Text(stringResource(R.string.pdf_item).uppercase(), style = AppType.overline, modifier = Modifier.weight(1.6f), color = AppTheme.ext.textSecondary)
                Text(stringResource(R.string.pdf_quantity).uppercase(), style = AppType.overline, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = AppTheme.ext.textSecondary)
                Text(stringResource(R.string.pdf_amount).uppercase(), style = AppType.overline, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End, color = AppTheme.ext.textSecondary)
            }
            val lines = view.displayLines
            if (lines.isEmpty()) Text(stringResource(R.string.pdf_no_deliveries), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, modifier = Modifier.padding(Space.s))
            // Day-wise rows, the same as the PDF: date, item and quantity given that day.
            var lastDate = Long.MIN_VALUE
            view.live.dailyLines.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(if (d.date != lastDate) Dates.formatDay(d.date) else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, modifier = Modifier.weight(1.1f))
                    Text(d.productName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
                    Text(Quantity.formatWithUnit(d.quantityMilli, d.unitLabel), style = AppType.tableValue, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(Money.format(d.amountPaise, cur), style = AppType.tableValue, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                }
                lastDate = d.date
            }
            if (view.live.dailyLines.isNotEmpty()) HorizontalDivider(color = AppTheme.ext.border)
            // Month total per item
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.6f)) {
                        Text(l.productName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text("${Money.format(l.ratePaise, cur)} / ${l.unitLabel}  ·  ${stringResource(R.string.label_days_count, l.deliveryDays)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    Text(Quantity.formatWithUnit(l.quantityMilli, l.unitLabel), style = AppType.tableValue, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text(Money.format(l.amountPaise, cur), style = AppType.tableValue, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                }
                HorizontalDivider(color = AppTheme.ext.border)
            }
            Spacer(Modifier.height(Space.m))
            Totals(stringResource(R.string.pdf_charges), Money.format(s.chargesPaise, cur))
            Totals(stringResource(R.string.pdf_previous), Money.format(s.previousBalancePaise, cur))
            if (s.adjustmentsPaise != 0L) Totals(stringResource(R.string.pdf_adjustments), Money.format(s.adjustmentsPaise, cur))
            Totals(stringResource(R.string.pdf_payments), "- " + Money.format(s.paymentsPaise, cur))
            Spacer(Modifier.height(Space.s))
            val closing = s.closingBalancePaise
            Row(
                Modifier.fillMaxWidth().background(AppTheme.colors.primaryContainer, MaterialTheme.shapes.medium).padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(if (closing < 0) R.string.pdf_advance else R.string.pdf_amount_due), style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.onPrimaryContainer, modifier = Modifier.weight(1f))
                Text(Money.format(kotlin.math.abs(closing), cur), style = AppType.moneyLarge.copy(fontSize = AppType.money.fontSize * 1.5f), color = AppTheme.colors.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun Totals(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AppType.tableValue)
    }
}
