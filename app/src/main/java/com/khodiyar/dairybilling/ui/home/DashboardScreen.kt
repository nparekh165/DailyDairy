package com.khodiyar.dairybilling.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.prefs.UiPrefs
import com.khodiyar.dairybilling.data.repo.Dashboard
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LabeledProgress
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.MetricTile
import com.khodiyar.dairybilling.ui.components.MiniBarChart
import com.khodiyar.dairybilling.ui.components.QuickAction
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.map

class DashboardViewModel(c: AppContainer) : AppViewModel(c) {
    val dashboard = c.reports.observeDashboard().stateInVm(null)
    val settings = c.settings.observe().stateInVm(null)
    val prefs = c.prefs.prefs.stateInVm(UiPrefs())
    val user = c.session.user
    fun can(p: Permission) = c.session.has(p)
    val now: Long get() = c.time.nowMillis()
}

@Composable
fun DashboardScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val d by vm.dashboard.collectAsState()
    val settings by vm.settings.collectAsState()
    val user by vm.user.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    val showMoney = vm.can(Permission.VIEW_REPORTS)
    val billingSteps = com.khodiyar.dairybilling.ui.common.LocalFeatures.current.billingSteps

    LazyColumn(
        Modifier.fillMaxSize().background(AppTheme.colors.background),
        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = Space.l, bottom = Space.xs)) {
                Text(
                    stringResource(R.string.dash_greeting, user?.displayName?.substringBefore(' ') ?: ""),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (settings?.dairyName ?: "") + "  ·  " + Dates.formatDayShort(d?.today ?: java.time.LocalDate.now()),
                    style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary,
                )
            }
        }
        val dash = d
        if (dash == null) {
            item { LoadingList(4) }
            return@LazyColumn
        }
        item { TodayHero(dash, cur, showMoney) { navigate(Routes.DELIVERY) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                QuickAction(stringResource(R.string.nav_daily_entry), Icons.Rounded.LocalShipping, { navigate(Routes.DELIVERY) }, Modifier.weight(1f))
                QuickAction(stringResource(R.string.nav_customers), Icons.Rounded.Groups, { navigate(Routes.CUSTOMERS) }, Modifier.weight(1f))
                if (vm.can(Permission.RECORD_PAYMENTS)) QuickAction(stringResource(R.string.action_record_payment), Icons.Rounded.Payments, { navigate(Routes.paymentNew()) }, Modifier.weight(1f), Tone.SUCCESS)
                if (vm.can(Permission.VIEW_BILLING)) QuickAction(stringResource(R.string.action_generate_bills), Icons.AutoMirrored.Rounded.ReceiptLong, { navigate(Routes.BILLING) }, Modifier.weight(1f), Tone.ACCENT)
            }
        }
        val warnings = buildList {
            if (dash.productsWithoutRate.isNotEmpty()) add(Triple(R.string.warn_no_rate, dash.productsWithoutRate.joinToString(), Routes.PRODUCTS))
            val pending = dash.expectedToday - dash.enteredToday
            if (pending > 0) add(Triple(R.string.warn_pending_today, pending.toString(), Routes.DELIVERY))
            if (billingSteps && dash.lastMonthNeedsAttention > 0) add(Triple(R.string.warn_last_month_bills, dash.lastMonthNeedsAttention.toString(), Routes.BILLING))
            if (vm.can(Permission.BACKUP)) {
                val days = if (prefs.lastBackupAt == 0L) -1 else ((vm.now - prefs.lastBackupAt) / 86_400_000L).toInt()
                if (days < 0 || days >= 7) add(Triple(if (days < 0) R.string.warn_never_backed_up else R.string.warn_backup_old, days.toString(), Routes.BACKUP))
            }
        }
        if (warnings.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dash_attention)) }
            warnings.forEach { (res, arg, route) ->
                item {
                    InlineNotice(stringResource(res, arg), Tone.WARNING, action = stringResource(R.string.action_open), onAction = { navigate(route) })
                }
            }
        }
        if (showMoney) {
            item { SectionHeader(stringResource(R.string.dash_this_month, Dates.formatMonth(java.time.YearMonth.from(dash.today)))) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MetricTile(stringResource(R.string.metric_month_sales), Money.compact(dash.monthSalesPaise, cur), Modifier.weight(1f),
                        icon = Icons.Rounded.Sell, tone = Tone.BRAND, onClick = { navigate(Routes.REPORTS) })
                    MetricTile(stringResource(R.string.metric_collected), Money.compact(dash.monthCollectedPaise, cur), Modifier.weight(1f),
                        icon = Icons.Rounded.Payments, tone = Tone.SUCCESS, onClick = { navigate(Routes.PAYMENTS) })
                }
            }
            item {
                MetricTile(
                    stringResource(R.string.metric_outstanding),
                    Money.format(dash.outstandingPaise, cur),
                    Modifier.fillMaxWidth(),
                    supporting = stringResource(R.string.metric_outstanding_sub, dash.customersWithDues),
                    icon = Icons.Rounded.AccountBalanceWallet,
                    tone = Tone.ACCENT,
                    onClick = { navigate(Routes.REPORTS) },
                )
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dash_last_14), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(Money.compact(dash.last14Days.sumOf { it.amountPaise }, cur), style = AppType.money)
                    }
                    Spacer(Modifier.height(Space.m))
                    MiniBarChart(
                        dash.last14Days.map { it.amountPaise },
                        description = stringResource(R.string.cd_sales_chart, Money.format(dash.last14Days.sumOf { it.amountPaise }, cur)),
                    )
                    Spacer(Modifier.height(Space.xs))
                    Row {
                        Text(Dates.formatDayShort(dash.last14Days.first().date), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textTertiary, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.label_today), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textTertiary)
                    }
                }
            }
            if (dash.recentPayments.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.dash_recent_payments), action = stringResource(R.string.action_see_all), onAction = { navigate(Routes.PAYMENTS) }) }
                item {
                    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                        dash.recentPayments.forEachIndexed { i, rp ->
                            if (i > 0) ThinDivider(Modifier.padding(horizontal = Space.l))
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(AppTheme.ext.successContainer), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Payments, null, tint = AppTheme.ext.onSuccessContainer, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(Space.m))
                                Column(Modifier.weight(1f)) {
                                    Text(rp.customer?.name ?: "—", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${rp.customer?.houseNo ?: ""} · ${Dates.formatDay(rp.payment.date)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                                }
                                Text(Money.format(rp.payment.amountPaise, cur), style = AppType.money,
                                    color = if (rp.payment.status == com.khodiyar.dairybilling.domain.model.RecordStatus.ACTIVE) AppTheme.colors.onSurface else AppTheme.ext.textTertiary)
                            }
                        }
                    }
                }
            }
        }
        if (vm.can(Permission.BACKUP)) {
            item {
                QuickAction(stringResource(R.string.nav_backup), Icons.Rounded.Backup, { navigate(Routes.BACKUP) }, Modifier.fillMaxWidth(), Tone.NEUTRAL)
            }
        }
    }
}

@Composable
private fun TodayHero(d: Dashboard, cur: String, showMoney: Boolean, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().animateContentSize(), onClick = onOpen, color = AppTheme.colors.primary, border = false) {
        val on = AppTheme.colors.onPrimary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, null, tint = on.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.dash_today).uppercase(), style = AppType.overline, color = on.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(Space.s))
        if (showMoney) {
            Text(Money.format(d.todaySalesPaise, cur), style = AppType.moneyLarge, color = on)
            Text(stringResource(R.string.dash_today_sales), style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.75f))
            Spacer(Modifier.height(Space.m))
        }
        if (d.todayProducts.isEmpty()) {
            Text(stringResource(R.string.dash_no_entries_today), style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.85f))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                d.todayProducts.take(3).forEach { pq ->
                    Column {
                        Text(Quantity.formatWithUnit(pq.quantityMilli, pq.product.displayUnit), style = MaterialTheme.typography.titleLarge, color = on)
                        Text(pq.product.name, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
        if (d.expectedToday > 0) {
            Spacer(Modifier.height(Space.l))
            val progress = d.enteredToday.toFloat() / d.expectedToday
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = AppTheme.colors.tertiaryContainer,
                trackColor = on.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.dash_progress, d.enteredToday, d.expectedToday), style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.85f))
        }
    }
}
