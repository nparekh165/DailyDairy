package com.khodiyar.dairybilling.ui.billing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.repo.BillState
import com.khodiyar.dairybilling.data.repo.CustomerBillRow
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.billing.PaidState
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.pdf.AndroidPdfWriter
import com.khodiyar.dairybilling.pdf.InvoicePdfBuilder
import com.khodiyar.dairybilling.pdf.PdfLabels
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.common.pdfLabels
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.MetricTile
import com.khodiyar.dairybilling.ui.components.MonthStepper
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import com.khodiyar.dairybilling.ui.common.LocalFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.YearMonth

enum class BillFilter { ALL, DRAFT, FINALIZED, ATTENTION, DUE }

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModel(c: AppContainer) : AppViewModel(c) {
    val month = MutableStateFlow(YearMonth.from(c.time.today()).minusMonths(if (c.time.today().dayOfMonth <= 10) 1 else 0))
    val filter = MutableStateFlow(BillFilter.ALL)
    val query = MutableStateFlow("")
    val currentMonth: YearMonth get() = YearMonth.from(c.time.today())
    val overview = month.flatMapLatest { c.billing.observeOverview(it) }.stateInVm(null)
    val settings = c.settings.observe().stateInVm(null)
    /** With bill steps switched off, every amount shown is the live one. */
    private val simple = c.prefs.features.map { !it.billingSteps }
    val rows = combine(overview, filter, query, simple) { ov, f, q, liveOnly ->
        val needle = q.trim().lowercase()
        ov?.rows?.filter { r ->
            when (f) {
                BillFilter.ALL -> true
                BillFilter.DRAFT -> r.state == BillState.DRAFT
                BillFilter.FINALIZED -> r.state == BillState.FINALIZED
                BillFilter.ATTENTION -> r.state == BillState.NEEDS_REVISION || r.missingDays > 0
                BillFilter.DUE -> if (liveOnly) r.summary.closingBalancePaise > 0
                    else (r.invoice?.closingBalancePaise ?: r.summary.closingBalancePaise) > 0 && r.paidState != PaidState.PAID
            }
        }?.filter { needle.isEmpty() || it.customer.name.lowercase().contains(needle) || it.customer.houseNo.lowercase().contains(needle) || it.customer.code.lowercase().contains(needle) }
    }.stateInVm(null)

    fun can(p: Permission) = c.session.has(p)
    fun finalizeAll() = action {
        val n = c.billing.finalizeAll(month.value)
        c.notify(UiText.res(R.string.msg_finalized_count, n), MessageKind.SUCCESS)
    }
    fun lock() = action(success = UiText.res(R.string.msg_period_locked)) { c.billing.lock(month.value) }
    fun reopen(reason: String) = action(success = UiText.res(R.string.msg_period_reopened)) { c.billing.reopen(month.value, reason) }

    /** Generates one PDF per customer into a user-chosen folder (Storage Access Framework). */
    fun exportAll(context: android.content.Context, tree: Uri, labels: PdfLabels, onlyFinalized: Boolean) = action {
        val ov = overview.value ?: return@action
        val features = c.prefs.features.first()
        val dir = DocumentFile.fromTreeUri(context, tree) ?: throw com.khodiyar.dairybilling.domain.AppException(R.string.err_save_failed)
        var count = 0
        withContext(Dispatchers.IO) {
            for (row in ov.rows) {
                if (onlyFinalized && row.invoice == null) continue
                val view = c.billing.invoiceView(row.customer.id, ov.period)?.forFeatures(features) ?: continue
                val layout = InvoicePdfBuilder.layout(view.toDocument(Dates.formatDay(c.time.today())), labels.forView(view), AndroidPdfWriter.measurer)
                val name = view.fileName()
                dir.findFile(name)?.delete()
                val file = dir.createFile("application/pdf", name.removeSuffix(".pdf")) ?: continue
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { AndroidPdfWriter.write(layout, it) }
                view.invoice?.let { c.billing.markPdfGenerated(it.id) }
                count++
            }
        }
        c.notify(UiText.res(R.string.msg_pdfs_exported, count), MessageKind.SUCCESS)
    }
}

@Composable
fun billStateBadge(row: CustomerBillRow) {
    val steps = LocalFeatures.current.billingSteps
    when {
        steps && row.state == BillState.NEEDS_REVISION -> StatusBadge(stringResource(R.string.status_needs_revision), Tone.WARNING, Icons.Rounded.Warning)
        steps && row.state == BillState.DRAFT -> StatusBadge(stringResource(R.string.status_draft), Tone.NEUTRAL, Icons.Rounded.EditNote)
        row.paidState == PaidState.PAID || row.paidState == PaidState.SETTLED -> StatusBadge(stringResource(R.string.status_paid), Tone.SUCCESS, Icons.Rounded.CheckCircle)
        row.paidState == PaidState.CREDIT -> StatusBadge(stringResource(R.string.status_credit), Tone.INFO, Icons.Rounded.Savings)
        row.paidState == PaidState.PARTIALLY_PAID -> StatusBadge(stringResource(R.string.status_partial), Tone.ACCENT, Icons.Rounded.HourglassTop)
        else -> StatusBadge(stringResource(R.string.status_outstanding), Tone.BRAND, Icons.AutoMirrored.Rounded.ReceiptLong)
    }
}

@Composable
fun BillingScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { BillingViewModel(it) }
    val month by vm.month.collectAsState()
    val ov by vm.overview.collectAsState()
    val rows by vm.rows.collectAsState()
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val settings by vm.settings.collectAsState()
    val busy by vm.busy.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    var confirmFinalize by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmReopen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val labels = pdfLabels()
    var onlyFinal by remember { mutableStateOf(true) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.exportAll(context, uri, labels, onlyFinal) }
    val steps = LocalFeatures.current.billingSteps

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(stringResource(R.string.nav_billing), actions = {
                Box0 {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more_options)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.action_export_all_pdfs)) }, { menu = false; onlyFinal = steps; folder.launch(null) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
                        if (steps) DropdownMenuItem({ Text(stringResource(R.string.action_export_draft_pdfs)) }, { menu = false; onlyFinal = false; folder.launch(null) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) })
                        if (vm.can(Permission.LOCK_PERIODS)) {
                            // Reopen stays available with bill steps off, so a month locked earlier never stays stuck.
                            if (ov?.locked == true) DropdownMenuItem({ Text(stringResource(R.string.action_reopen_period)) }, { menu = false; confirmReopen = true }, leadingIcon = { Icon(Icons.Rounded.LockOpen, null) })
                            else if (steps) DropdownMenuItem({ Text(stringResource(R.string.action_lock_period)) }, { menu = false; confirmLock = true }, leadingIcon = { Icon(Icons.Rounded.Lock, null) })
                        }
                    }
                }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = Space.xxl)) {
            item {
                Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    MonthStepper(month, { vm.month.value = it }, Modifier.fillMaxWidth(), maxMonth = vm.currentMonth, locked = ov?.locked == true)
                    val o = ov
                    if (o != null) {
                        AppCard(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.label_month_charges).uppercase(), style = AppType.overline, color = AppTheme.ext.textSecondary)
                            Text(Money.format(o.totalCharges, cur), style = AppType.moneyLarge)
                            Spacer(Modifier.height(Space.s))
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.metric_collected), style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                                    Text(Money.format(o.totalPayments, cur), style = AppType.money)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.label_closing_dues), style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                                    Text(Money.format(o.totalClosing, cur), style = AppType.money)
                                }
                            }
                        }
                        if (steps) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            MetricTile(stringResource(R.string.status_draft), o.draftCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.EditNote)
                            MetricTile(stringResource(R.string.status_finalized), o.finalizedCount.toString(), Modifier.weight(1f), icon = Icons.Rounded.CheckCircle, tone = Tone.BRAND)
                        }
                        if (o.locked) InlineNotice(stringResource(R.string.notice_month_locked), Tone.WARNING, icon = Icons.Rounded.Lock)
                        if (steps && o.needsRevision > 0) InlineNotice(stringResource(R.string.notice_needs_revision, o.needsRevision), Tone.WARNING,
                            action = stringResource(R.string.action_review), onAction = { vm.filter.value = BillFilter.ATTENTION })
                        if (o.missingEntryCustomers > 0 && !o.locked) InlineNotice(stringResource(R.string.notice_missing_entries, o.missingEntryCustomers), Tone.INFO,
                            action = stringResource(R.string.action_review), onAction = { vm.filter.value = BillFilter.ATTENTION })
                        if (month >= vm.currentMonth) InlineNotice(stringResource(R.string.notice_month_running), Tone.INFO)
                        if (steps && vm.can(Permission.FINALIZE_INVOICES) && !o.locked && o.draftCount > 0) {
                            PrimaryButton(stringResource(R.string.action_finalize_all, o.draftCount), { confirmFinalize = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DoneAll, loading = busy)
                        }
                    }
                    AppSearchField(query, { vm.query.value = it }, stringResource(R.string.hint_search_customer))
                    ChoiceChips(if (steps) BillFilter.entries else listOf(BillFilter.ALL, BillFilter.ATTENTION, BillFilter.DUE), filter, {
                        when (it) {
                            BillFilter.ALL -> stringResource(R.string.filter_all)
                            BillFilter.DRAFT -> stringResource(R.string.status_draft)
                            BillFilter.FINALIZED -> stringResource(R.string.status_finalized)
                            BillFilter.ATTENTION -> stringResource(R.string.filter_attention)
                            BillFilter.DUE -> stringResource(R.string.filter_due)
                        }
                    }, { vm.filter.value = it })
                }
                Spacer(Modifier.height(Space.s))
            }
            val r = rows
            if (r == null) item { LoadingList() }
            else if (r.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.empty_bills), stringResource(R.string.empty_bills_msg)) }
            else items(r, key = { it.customer.id }) { row ->
                androidx.compose.material3.Surface(onClick = { navigate(Routes.invoice(row.customer.id, Dates.periodKey(month))) }, color = AppTheme.colors.background) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        HouseBadge(row.customer.houseNo, active = row.customer.active)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(row.customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            billStateBadge(row)
                            if (row.missingDays > 0) Text(stringResource(R.string.label_missing_days, row.missingDays), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.warning)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val closing = if (steps) row.invoice?.closingBalancePaise ?: row.summary.closingBalancePaise else row.summary.closingBalancePaise
                            Text(Money.format(kotlin.math.abs(closing), cur), style = AppType.money)
                            Text(stringResource(if (closing < 0) R.string.label_advance else R.string.label_due) + "  ·  " + Money.format(row.summary.chargesPaise, cur),
                                style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textSecondary)
                        }
                    }
                }
                ThinDivider(Modifier.padding(start = 80.dp))
            }
        }
    }
    if (confirmFinalize) PinConfirmDialog(
        title = stringResource(R.string.dlg_finalize_all_title),
        message = stringResource(R.string.dlg_finalize_all_msg, ov?.draftCount ?: 0, Dates.formatMonth(month)),
        confirmLabel = stringResource(R.string.action_finalize),
        verify = vm::verifyPin, onConfirmed = { vm.finalizeAll() }, onDismiss = { confirmFinalize = false },
    )
    if (confirmLock) PinConfirmDialog(
        title = stringResource(R.string.dlg_lock_title, Dates.formatMonth(month)),
        message = stringResource(R.string.dlg_lock_msg) + if ((ov?.draftCount ?: 0) > 0) "\n\n" + stringResource(R.string.dlg_lock_drafts_warning, ov?.draftCount ?: 0) else "",
        confirmLabel = stringResource(R.string.action_lock_period),
        verify = vm::verifyPin, onConfirmed = { vm.lock() }, onDismiss = { confirmLock = false },
    )
    if (confirmReopen) PinConfirmDialog(
        title = stringResource(R.string.dlg_reopen_title, Dates.formatMonth(month)),
        message = stringResource(R.string.dlg_reopen_msg),
        confirmLabel = stringResource(R.string.action_reopen_period),
        verify = vm::verifyPin, onConfirmed = {}, onDismiss = { confirmReopen = false },
        requireReason = true, reasonLabel = stringResource(R.string.field_reason), onConfirmedWithReason = { vm.reopen(it) },
    )
}

@Composable
private fun Box0(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box { content() }
