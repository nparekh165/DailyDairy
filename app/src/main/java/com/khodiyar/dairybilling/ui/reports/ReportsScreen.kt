@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.khodiyar.dairybilling.ui.reports

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
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppDatabase
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.repo.CustomerStatement
import com.khodiyar.dairybilling.data.repo.MonthOverview
import com.khodiyar.dairybilling.data.repo.OutstandingRow
import com.khodiyar.dairybilling.data.repo.RangeReport
import com.khodiyar.dairybilling.data.repo.StatementLine
import com.khodiyar.dairybilling.domain.model.PaymentMethod
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.pdf.Align
import com.khodiyar.dairybilling.pdf.Column as PdfColumn
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.pdf.TableDocument
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.TableExports
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.common.pdfLabels
import com.khodiyar.dairybilling.ui.common.rememberExporter
import com.khodiyar.dairybilling.ui.customers.methodLabel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.DateField
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.KeyValueRow
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.MetricTile
import com.khodiyar.dairybilling.ui.components.MiniBarChart
import com.khodiyar.dairybilling.ui.components.MonthStepper
import com.khodiyar.dairybilling.ui.components.SectionHeader
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
import java.time.YearMonth

enum class ReportTab { SALES, MONTHLY, OUTSTANDING, STATEMENT, CUSTOMERS }

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(c: AppContainer) : AppViewModel(c) {
    val tab = MutableStateFlow(ReportTab.SALES)
    val from = MutableStateFlow(c.time.today().withDayOfMonth(1))
    val to = MutableStateFlow(c.time.today())
    val productId = MutableStateFlow<Long?>(null)
    val month = MutableStateFlow(YearMonth.from(c.time.today()))
    val includeInactive = MutableStateFlow(false)
    val customerId = MutableStateFlow(0L)
    val customerQuery = MutableStateFlow("")
    val today: LocalDate get() = c.time.today()
    private val changes = c.db.changes(*AppDatabase.LEDGER_TABLES)

    val settings = c.settings.observe().stateInVm(null)
    val products = c.products.observeAll().stateInVm(emptyList())
    val customers = c.customers.observeAll().stateInVm(emptyList())

    val range = combine(from, to, productId, changes) { f, t, p, _ -> Triple(f, t, p) }
        .mapLatest { (f, t, p) -> runCatching { c.reports.range(minOf(f, t), maxOf(f, t), p) }.getOrNull() }.stateInVm(null)
    val monthly = combine(month, changes) { m, _ -> m }.mapLatest { m -> c.billing.overview(m) to runCatching { c.reports.range(m.atDay(1), m.atEndOfMonth(), null) }.getOrNull() }.stateInVm(null)
    val outstanding = combine(includeInactive, changes) { i, _ -> i }.mapLatest { runCatching { c.reports.outstanding(it) }.getOrNull() }.stateInVm(null)
    val statement = combine(customerId, from, to, changes) { id, f, t, _ -> Triple(id, f, t) }
        .mapLatest { (id, f, t) -> if (id == 0L) null else c.reports.statement(id, minOf(f, t), maxOf(f, t)) }.stateInVm(null)
}

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportsViewModel(it) }
    val tab by vm.tab.collectAsState()
    val settings by vm.settings.collectAsState()
    val cur = settings?.currencySymbol ?: "â‚¹"
    val business = settings?.dairyName ?: ""
    val exporter = rememberExporter()
    val labels = pdfLabels()

    val range by vm.range.collectAsState()
    val monthly by vm.monthly.collectAsState()
    val outstanding by vm.outstanding.collectAsState()
    val statement by vm.statement.collectAsState()
    val customers by vm.customers.collectAsState()

    val salesTitle = stringResource(R.string.report_sales)
    val monthlyTitle = stringResource(R.string.report_monthly)
    val outTitle = stringResource(R.string.report_outstanding)
    val stTitle = stringResource(R.string.report_statement)
    val custTitle = stringResource(R.string.report_customers)
    val hDate = stringResource(R.string.col_date); val hProduct = stringResource(R.string.col_product); val hQty = stringResource(R.string.col_quantity)
    val hAmount = stringResource(R.string.col_amount); val hHouse = stringResource(R.string.col_house); val hCustomer = stringResource(R.string.col_customer)
    val hCode = stringResource(R.string.col_code); val hPrev = stringResource(R.string.pdf_previous); val hCharges = stringResource(R.string.pdf_charges)
    val hPaid = stringResource(R.string.pdf_payments); val hAdj = stringResource(R.string.pdf_adjustments); val hClosing = stringResource(R.string.label_closing)
    val hInvoice = stringResource(R.string.col_invoice); val hDebit = stringResource(R.string.col_debit); val hCredit = stringResource(R.string.col_credit)
    val hBalance = stringResource(R.string.col_balance); val hDetail = stringResource(R.string.col_detail); val hMobile = stringResource(R.string.col_mobile)
    val hStatus = stringResource(R.string.col_status); val hOutstanding = stringResource(R.string.label_outstanding); val hEntries = stringResource(R.string.col_entries)
    val activeS = stringResource(R.string.status_active); val inactiveS = stringResource(R.string.status_inactive); val totalS = stringResource(R.string.label_total)
    val collectedS = stringResource(R.string.metric_collected)

    fun currentDoc(): Pair<String, TableDocument>? = when (tab) {
        ReportTab.SALES -> range?.let { r ->
            "sales_${r.from}_${r.to}" to TableDocument(
                salesTitle, Dates.formatDay(r.from) + " â€“ " + Dates.formatDay(r.to), business,
                listOf(PdfColumn(hProduct, 2.5f), PdfColumn(hEntries, 1f, Align.RIGHT), PdfColumn(hQty, 1.5f, Align.RIGHT), PdfColumn(hAmount, 1.6f, Align.RIGHT)),
                r.products.map { listOf(it.product.name, it.entries.toString(), Quantity.formatWithUnit(it.quantityMilli, it.product.displayUnit), Money.plain(it.amountPaise)) },
                listOf(totalS, "", "", Money.plain(r.salesPaise)),
                listOf(salesTitle to Money.format(r.salesPaise, cur), collectedS to Money.format(r.collectedPaise, cur)),
            )
        }
        ReportTab.MONTHLY -> monthly?.first?.let { ov ->
            "monthly_${ov.period}" to monthlyDoc(ov, monthlyTitle, business, cur, listOf(hHouse, hCustomer, hPrev, hCharges, hAdj, hPaid, hClosing, hInvoice), totalS)
        }
        ReportTab.OUTSTANDING -> outstanding?.let { rows ->
            "outstanding_${vm.today}" to TableDocument(
                outTitle, Dates.formatDay(vm.today), business,
                listOf(PdfColumn(hHouse, 1f), PdfColumn(hCustomer, 2.6f), PdfColumn(hMobile, 1.6f), PdfColumn(hOutstanding, 1.6f, Align.RIGHT)),
                rows.filter { it.outstandingPaise != 0L }.map { listOf(it.customer.houseNo, it.customer.name, it.customer.mobile, Money.plain(it.outstandingPaise)) },
                listOf("", totalS, "", Money.plain(rows.sumOf { it.outstandingPaise.coerceAtLeast(0) })),
            )
        }
        ReportTab.STATEMENT -> statement?.let { st ->
            "statement_${st.customer.code}_${st.from}_${st.to}" to TableDocument(
                stTitle + " â€” " + st.customer.name, "${st.customer.houseNo} Â· ${st.customer.code} Â· " + Dates.formatDay(st.from) + " â€“ " + Dates.formatDay(st.to), business,
                listOf(PdfColumn(hDate, 1.3f), PdfColumn(hDetail, 2.8f), PdfColumn(hDebit, 1.3f, Align.RIGHT), PdfColumn(hCredit, 1.3f, Align.RIGHT), PdfColumn(hBalance, 1.4f, Align.RIGHT)),
                listOf(listOf(Dates.formatIso(st.from.toEpochDay()), hPrev, "", "", Money.plain(st.openingPaise))) +
                    st.lines.map { listOf(Dates.formatIso(it.date), it.description.ifBlank { it.kind.name }, if (it.debitPaise != 0L) Money.plain(it.debitPaise) else "", if (it.creditPaise != 0L) Money.plain(it.creditPaise) else "", Money.plain(it.balancePaise)) },
                listOf("", hClosing, "", "", Money.plain(st.closingPaise)),
            )
        }
        ReportTab.CUSTOMERS -> "customers_${vm.today}" to TableDocument(
            custTitle, Dates.formatDay(vm.today), business,
            listOf(PdfColumn(hCode, 1.2f), PdfColumn(hHouse, 1f), PdfColumn(hCustomer, 2.5f), PdfColumn(hMobile, 1.6f), PdfColumn(hStatus, 1.1f)),
            customers.sortedWith(compareBy(com.khodiyar.dairybilling.data.repo.HouseNumberComparator) { it.houseNo })
                .map { listOf(it.code, it.houseNo, it.name, it.mobile, if (it.active) activeS else inactiveS) },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(stringResource(R.string.nav_reports), onBack = onBack, actions = {
                IconButton(onClick = { currentDoc()?.let { (n, d) -> exporter(TableExports.csv(DocumentFiles.safeName(business, n, extension = "x").removeSuffix(".x"), d)) } }) {
                    Icon(Icons.Rounded.TableChart, stringResource(R.string.action_export_csv))
                }
                IconButton(onClick = { currentDoc()?.let { (n, d) -> exporter(TableExports.pdf(DocumentFiles.safeName(business, n, extension = "x").removeSuffix(".x"), d, labels)) } }) {
                    Icon(Icons.Rounded.PictureAsPdf, stringResource(R.string.action_export_pdf))
                }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = Space.screen, containerColor = AppTheme.colors.background) {
                ReportTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { vm.tab.value = t }, text = {
                        Text(when (t) {
                            ReportTab.SALES -> stringResource(R.string.report_sales)
                            ReportTab.MONTHLY -> stringResource(R.string.report_monthly)
                            ReportTab.OUTSTANDING -> stringResource(R.string.report_outstanding)
                            ReportTab.STATEMENT -> stringResource(R.string.report_statement)
                            ReportTab.CUSTOMERS -> stringResource(R.string.report_customers)
                        })
                    })
                }
            }
            when (tab) {
                ReportTab.SALES -> SalesTab(vm, range, cur)
                ReportTab.MONTHLY -> MonthlyTab(vm, monthly, cur)
                ReportTab.OUTSTANDING -> OutstandingTab(vm, outstanding, cur)
                ReportTab.STATEMENT -> StatementTab(vm, statement, customers, cur)
                ReportTab.CUSTOMERS -> CustomersTab(customers)
            }
        }
    }
}

fun monthlyDoc(ov: MonthOverview, title: String, business: String, cur: String, h: List<String>, total: String) = TableDocument(
    title, Dates.formatMonth(ov.period), business,
    listOf(PdfColumn(h[0], 0.9f), PdfColumn(h[1], 2.2f), PdfColumn(h[2], 1.3f, Align.RIGHT), PdfColumn(h[3], 1.3f, Align.RIGHT), PdfColumn(h[4], 1.1f, Align.RIGHT),
        PdfColumn(h[5], 1.3f, Align.RIGHT), PdfColumn(h[6], 1.3f, Align.RIGHT), PdfColumn(h[7], 2f)),
    ov.rows.map {
        val s = it.summary
        listOf(it.customer.houseNo, it.customer.name, Money.plain(s.previousBalancePaise), Money.plain(s.chargesPaise), Money.plain(s.adjustmentsPaise),
            Money.plain(s.paymentsPaise), Money.plain(s.closingBalancePaise), it.invoice?.invoiceNumber ?: "")
    },
    listOf("", total, Money.plain(ov.rows.sumOf { it.summary.previousBalancePaise }), Money.plain(ov.totalCharges), Money.plain(ov.rows.sumOf { it.summary.adjustmentsPaise }),
        Money.plain(ov.totalPayments), Money.plain(ov.rows.sumOf { it.summary.closingBalancePaise }), ""),
)

@Composable
private fun SalesTab(vm: ReportsViewModel, r: RangeReport?, cur: String) {
    val from by vm.from.collectAsState()
    val to by vm.to.collectAsState()
    val pid by vm.productId.collectAsState()
    val products by vm.products.collectAsState()
    LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                DateField(stringResource(R.string.field_from), from, { vm.from.value = it }, Modifier.weight(1f), maxDate = vm.today)
                DateField(stringResource(R.string.field_to), to, { vm.to.value = it }, Modifier.weight(1f), maxDate = vm.today)
            }
            Spacer(Modifier.height(Space.s))
            ChoiceChips(listOf<Long?>(null) + products.map { it.id }, pid, { id -> id?.let { i -> products.firstOrNull { it.id == i }?.name ?: "" } ?: stringResource(R.string.filter_all_products) }, { vm.productId.value = it })
        }
        if (r == null) { item { LoadingList(3) }; return@LazyColumn }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MetricTile(stringResource(R.string.report_sales), Money.format(r.salesPaise, cur), Modifier.weight(1f), icon = Icons.Rounded.Sell, tone = Tone.BRAND)
                MetricTile(stringResource(R.string.metric_collected), Money.format(r.collectedPaise, cur), Modifier.weight(1f), icon = Icons.Rounded.Payments, tone = Tone.SUCCESS)
            }
        }
        if (r.daily.size in 2..62) item {
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.label_daily_sales), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.s))
                MiniBarChart(r.daily.map { it.amountPaise }, highlightIndex = r.daily.indexOfFirst { it.amountPaise == r.daily.maxOf { d -> d.amountPaise } },
                    description = stringResource(R.string.cd_sales_chart, Money.format(r.salesPaise, cur)), height = 96.dp)
            }
        }
        item { SectionHeader(stringResource(R.string.section_product_totals)) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                if (r.products.isEmpty()) Text(stringResource(R.string.empty_range), color = AppTheme.ext.textSecondary)
                r.products.forEach { KeyValueRow(it.product.name + "  Â·  " + Quantity.formatWithUnit(it.quantityMilli, it.product.displayUnit), Money.format(it.amountPaise, cur)) }
            }
        }
        if (r.collectionsByMethod.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.section_collections_by_method)) }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    PaymentMethod.entries.forEach { m -> r.collectionsByMethod[m]?.let { KeyValueRow(methodLabel(m), Money.format(it, cur)) } }
                }
            }
        }
    }
}

@Composable
private fun MonthlyTab(vm: ReportsViewModel, data: Pair<MonthOverview, RangeReport?>?, cur: String) {
    val month by vm.month.collectAsState()
    LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        item { MonthStepper(month, { vm.month.value = it }, Modifier.fillMaxWidth(), maxMonth = YearMonth.from(vm.today)) }
        if (data == null) { item { LoadingList(3) }; return@LazyColumn }
        val (ov, rr) = data
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MetricTile(stringResource(R.string.metric_month_sales), Money.format(ov.totalCharges, cur), Modifier.weight(1f), icon = Icons.Rounded.Sell, tone = Tone.BRAND)
                MetricTile(stringResource(R.string.metric_collected), Money.format(ov.totalPayments, cur), Modifier.weight(1f), icon = Icons.Rounded.Payments, tone = Tone.SUCCESS)
            }
        }
        rr?.let { r ->
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.section_product_totals), style = MaterialTheme.typography.titleSmall)
                    r.products.forEach { KeyValueRow(it.product.name + "  Â·  " + Quantity.formatWithUnit(it.quantityMilli, it.product.displayUnit), Money.format(it.amountPaise, cur)) }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.section_customer_bills, ov.rows.size)) }
        items(ov.rows, key = { it.customer.id }) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                HouseBadge(row.customer.houseNo, active = row.customer.active)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(row.customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.label_charges_paid, Money.format(row.summary.chargesPaise, cur), Money.format(row.summary.paymentsPaise, cur)),
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                Text(Money.format(row.summary.closingBalancePaise, cur), style = AppType.money)
            }
            ThinDivider()
        }
    }
}

@Composable
private fun OutstandingTab(vm: ReportsViewModel, rows: List<OutstandingRow>?, cur: String) {
    val inactive by vm.includeInactive.collectAsState()
    LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        item {
            ChoiceChips(listOf(false, true), inactive, { stringResource(if (it) R.string.filter_all else R.string.filter_active) }, { vm.includeInactive.value = it })
        }
        if (rows == null) { item { LoadingList(3) }; return@LazyColumn }
        item {
            MetricTile(stringResource(R.string.metric_outstanding), Money.format(rows.sumOf { it.outstandingPaise.coerceAtLeast(0) }, cur), Modifier.fillMaxWidth(),
                supporting = stringResource(R.string.metric_outstanding_sub, rows.count { it.outstandingPaise > 0 }), icon = Icons.Rounded.Insights, tone = Tone.ACCENT)
        }
        val list = rows.filter { it.outstandingPaise != 0L }
        if (list.isEmpty()) item { EmptyState(Icons.Rounded.Payments, stringResource(R.string.empty_outstanding), stringResource(R.string.empty_outstanding_msg)) }
        items(list, key = { it.customer.id }) { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                HouseBadge(r.customer.houseNo, active = r.customer.active)
                Spacer(Modifier.width(Space.m))
                Text(r.customer.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(kotlin.math.abs(r.outstandingPaise), cur), style = AppType.money, color = if (r.outstandingPaise < 0) AppTheme.ext.success else AppTheme.colors.onSurface)
                    Text(stringResource(if (r.outstandingPaise < 0) R.string.label_advance else R.string.label_due), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textSecondary)
                }
            }
            ThinDivider()
        }
    }
}

@Composable
private fun StatementTab(vm: ReportsViewModel, st: CustomerStatement?, customers: List<CustomerEntity>, cur: String) {
    val from by vm.from.collectAsState()
    val to by vm.to.collectAsState()
    val cid by vm.customerId.collectAsState()
    val q by vm.customerQuery.collectAsState()
    LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                DateField(stringResource(R.string.field_from), from, { vm.from.value = it }, Modifier.weight(1f), maxDate = vm.today)
                DateField(stringResource(R.string.field_to), to, { vm.to.value = it }, Modifier.weight(1f), maxDate = vm.today)
            }
        }
        if (cid == 0L || st == null) {
            item { AppSearchField(q, { vm.customerQuery.value = it }, stringResource(R.string.hint_pick_customer)) }
            val needle = q.trim().lowercase()
            val list = customers.filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.houseNo.lowercase().contains(needle) || it.code.lowercase().contains(needle) }.take(50)
            items(list, key = { it.id }) { c ->
                androidx.compose.material3.Surface(onClick = { vm.customerId.value = c.id }, color = AppTheme.colors.background) {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        HouseBadge(c.houseNo, active = c.active)
                        Spacer(Modifier.width(Space.m))
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@LazyColumn
        }
        item {
            AppCard(Modifier.fillMaxWidth(), onClick = { vm.customerId.value = 0 }) {
                Text(st.customer.name, style = MaterialTheme.typography.titleMedium)
                Text("${st.customer.houseNo} Â· ${st.customer.code}  Â·  " + stringResource(R.string.action_change_customer), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.primary)
                Spacer(Modifier.height(Space.s))
                KeyValueRow(stringResource(R.string.label_opening_for_range), Money.format(st.openingPaise, cur))
                KeyValueRow(stringResource(R.string.label_closing), Money.format(st.closingPaise, cur), emphasize = true)
            }
        }
        if (st.lines.isEmpty()) item { EmptyState(Icons.Rounded.Insights, stringResource(R.string.empty_range), stringResource(R.string.empty_statement_msg)) }
        items(st.lines.size) { i ->
            val l = st.lines[i]
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.description.ifBlank { l.kind.name }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Dates.formatDay(l.date) + when (l.kind) {
                        StatementLine.Kind.NO_DELIVERY -> "  Â·  " + stringResource(R.string.status_no_delivery)
                        StatementLine.Kind.MISSED -> "  Â·  " + stringResource(R.string.status_missed)
                        else -> ""
                    }, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    when {
                        l.creditPaise != 0L -> Text("- " + Money.format(l.creditPaise, cur), style = AppType.tableValue, color = AppTheme.ext.success)
                        l.debitPaise != 0L -> Text(Money.format(l.debitPaise, cur), style = AppType.tableValue)
                        else -> Text("â€”", style = AppType.tableValue, color = AppTheme.ext.textTertiary)
                    }
                    Text(Money.format(l.balancePaise, cur), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textSecondary)
                }
            }
            ThinDivider()
        }
    }
}

@Composable
private fun CustomersTab(customers: List<CustomerEntity>) {
    val active = customers.count { it.active }
    LazyColumn(contentPadding = PaddingValues(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MetricTile(stringResource(R.string.status_active), active.toString(), Modifier.weight(1f), tone = Tone.SUCCESS)
                MetricTile(stringResource(R.string.status_inactive), (customers.size - active).toString(), Modifier.weight(1f))
            }
        }
        items(customers.sortedWith(compareBy<CustomerEntity> { !it.active }.thenComparing({ it.houseNo }, com.khodiyar.dairybilling.data.repo.HouseNumberComparator)), key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                HouseBadge(c.houseNo, active = c.active)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.code + if (c.mobile.isNotBlank()) "  Â·  " + c.mobile else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                Text(stringResource(if (c.active) R.string.status_active else R.string.status_inactive), style = MaterialTheme.typography.labelMedium,
                    color = if (c.active) AppTheme.ext.success else AppTheme.ext.textTertiary)
            }
            ThinDivider()
        }
    }
}
