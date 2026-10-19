package com.khodiyar.dairybilling.ui.delivery

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
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.repo.DailyReport
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.pdf.Align
import com.khodiyar.dairybilling.pdf.Column as PdfColumn
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.pdf.TableDocument
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.TableExports
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.common.pdfLabels
import com.khodiyar.dairybilling.ui.common.rememberExporter
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.DateStepper
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.KeyValueRow
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.MetricTile
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DailySummaryViewModel(c: AppContainer, initial: LocalDate) : AppViewModel(c) {
    val date = MutableStateFlow(initial)
    val report = combine(date, c.db.changes(*AppDatabase.LEDGER_TABLES)) { d, _ -> d }
        .mapLatest { c.reports.daily(it) }.stateInVm(null)
    val currency = c.settings.observe().stateInVm(null)
    val today: LocalDate get() = c.time.today()
    val canSeeMoney: Boolean get() = c.session.has(Permission.VIEW_REPORTS)
    val canExport: Boolean get() = c.session.has(Permission.VIEW_REPORTS)
}

fun dailyTable(r: DailyReport, business: String, cur: String, statusName: (DeliveryStatus) -> String, headers: List<String>, title: String): TableDocument =
    TableDocument(
        title = title,
        subtitle = Dates.formatDay(r.date),
        businessName = business,
        columns = listOf(
            PdfColumn(headers[0], 1.1f), PdfColumn(headers[1], 2.6f), PdfColumn(headers[2], 1.6f), PdfColumn(headers[3], 1.4f),
            PdfColumn(headers[4], 1.3f, Align.RIGHT), PdfColumn(headers[5], 1.3f, Align.RIGHT), PdfColumn(headers[6], 1.5f, Align.RIGHT),
        ),
        rows = r.rows.map {
            listOf(
                it.customer.houseNo, it.customer.name, it.product.name, statusName(it.entry.status),
                Quantity.formatWithUnit(it.entry.quantityMilli, it.product.displayUnit), Money.plain(it.entry.ratePaise), Money.plain(it.entry.amountPaise),
            )
        },
        totals = listOf("", "", "", "", "", "", Money.plain(r.salesPaise)),
        summary = r.products.map { it.product.name to (Quantity.formatWithUnit(it.quantityMilli, it.product.displayUnit) + "  ·  " + Money.format(it.amountPaise, cur)) },
    )

@Composable
fun DailySummaryScreen(epochDay: Long, onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel(key = "daily_$epochDay") { DailySummaryViewModel(it, LocalDate.ofEpochDay(epochDay)) }
    val r by vm.report.collectAsState()
    val date by vm.date.collectAsState()
    val settings by vm.currency.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    val exporter = rememberExporter()
    val labels = pdfLabels()
    val headers = listOf(
        stringResource(R.string.col_house), stringResource(R.string.col_customer), stringResource(R.string.col_product), stringResource(R.string.col_status),
        stringResource(R.string.col_quantity), stringResource(R.string.col_rate), stringResource(R.string.col_amount),
    )
    val title = stringResource(R.string.nav_daily_summary)
    val delivered = stringResource(R.string.status_delivered)
    val noDel = stringResource(R.string.status_no_delivery)
    val missed = stringResource(R.string.status_missed)
    val statusName: (DeliveryStatus) -> String = { when (it) { DeliveryStatus.DELIVERED -> delivered; DeliveryStatus.NO_DELIVERY -> noDel; DeliveryStatus.MISSED -> missed } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(title, onBack = onBack, actions = {
                val rep = r
                if (rep != null && vm.canExport) {
                    val name = DocumentFiles.safeName(settings?.dairyName ?: "", "daily", Dates.formatIso(rep.date.toEpochDay()), extension = "x").removeSuffix(".x")
                    IconButton(onClick = { exporter(TableExports.csv(name, dailyTable(rep, settings?.dairyName ?: "", cur, statusName, headers, title))) }) {
                        Icon(Icons.Rounded.TableChart, stringResource(R.string.action_export_csv))
                    }
                    IconButton(onClick = { exporter(TableExports.pdf(name, dailyTable(rep, settings?.dairyName ?: "", cur, statusName, headers, title), labels)) }) {
                        Icon(Icons.Rounded.PictureAsPdf, stringResource(R.string.action_export_pdf))
                    }
                }
            })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { DateStepper(date, { vm.date.value = it }, Modifier.fillMaxWidth(), maxDate = vm.today, today = vm.today) }
            val rep = r
            if (rep == null) { item { LoadingList(4) }; return@LazyColumn }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MetricTile(stringResource(R.string.status_delivered), rep.delivered.toString(), Modifier.weight(1f), icon = Icons.Rounded.LocalShipping, tone = Tone.SUCCESS)
                    MetricTile(stringResource(R.string.status_pending), rep.pending.size.toString(), Modifier.weight(1f), icon = Icons.Rounded.PendingActions, tone = Tone.WARNING)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MetricTile(stringResource(R.string.status_no_delivery), rep.noDelivery.toString(), Modifier.weight(1f), icon = Icons.Rounded.DoNotDisturbOn)
                    MetricTile(stringResource(R.string.status_missed), rep.missed.toString(), Modifier.weight(1f), icon = Icons.Rounded.EventBusy, tone = Tone.DANGER)
                }
            }
            item { SectionHeader(stringResource(R.string.section_product_totals)) }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    if (rep.products.isEmpty()) Text(stringResource(R.string.empty_day), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary)
                    rep.products.forEach { pq ->
                        KeyValueRow(pq.product.name, Quantity.formatWithUnit(pq.quantityMilli, pq.product.displayUnit) + if (vm.canSeeMoney) "  ·  " + Money.format(pq.amountPaise, cur) else "")
                    }
                    if (vm.canSeeMoney && rep.products.isNotEmpty()) {
                        ThinDivider(Modifier.padding(vertical = Space.xs))
                        KeyValueRow(stringResource(R.string.label_total_sales), Money.format(rep.salesPaise, cur), emphasize = true)
                    }
                }
            }
            if (rep.pending.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.section_pending_customers, rep.pending.size), action = stringResource(R.string.action_enter_now), onAction = { navigate(com.khodiyar.dairybilling.ui.Routes.DELIVERY) }) }
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(rep.pending.joinToString("  ·  ") { it.houseNo + " " + it.name }, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.section_entries, rep.rows.size)) }
            if (rep.rows.isEmpty()) item { EmptyState(Icons.Rounded.LocalShipping, stringResource(R.string.empty_day), stringResource(R.string.empty_day_msg)) }
            items(rep.rows, key = { it.entry.id }) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    HouseBadge(row.customer.houseNo)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(row.customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.product.name + if (vm.canSeeMoney && row.entry.status == DeliveryStatus.DELIVERED) "  ·  " + Money.format(row.entry.amountPaise, cur) else "",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    when (row.entry.status) {
                        DeliveryStatus.DELIVERED -> Text(Quantity.formatWithUnit(row.entry.quantityMilli, row.product.displayUnit), style = AppType.money)
                        DeliveryStatus.NO_DELIVERY -> StatusBadge(noDel, Tone.NEUTRAL, Icons.Rounded.DoNotDisturbOn)
                        DeliveryStatus.MISSED -> StatusBadge(missed, Tone.DANGER, Icons.Rounded.EventBusy)
                    }
                }
            }
            item { Spacer(Modifier.height(Space.l)) }
        }
    }
}
