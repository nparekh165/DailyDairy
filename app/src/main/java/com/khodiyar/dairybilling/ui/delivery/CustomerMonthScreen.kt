package com.khodiyar.dairybilling.ui.delivery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.CustomerEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.HouseBadge
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.components.asString
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * "By customer" mode of the Delivery tab: one customer, one row per day of the month.
 * [request] opens a specific customer and month (customerId to epochDay) when set from the daily list.
 */
@Composable
fun CustomerMonthScreen(
    navigate: (String) -> Unit,
    request: Pair<Long, Long>?,
    onRequestHandled: () -> Unit,
    modeSwitch: @Composable () -> Unit,
) {
    val vm = appViewModel { CustomerMonthViewModel(it) }
    val s by vm.ui.collectAsState()
    val busy by vm.busy.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var pendingSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        if (!vm.blockedByDrafts()) {
            vm.setCustomer(r.first)
            vm.setMonth(YearMonth.from(java.time.LocalDate.ofEpochDay(r.second)))
        }
        onRequestHandled()
    }

    // Changing customer or month drops unsaved days, so ask first.
    fun guarded(block: () -> Unit) { if (vm.hasDrafts) pendingSwitch = block else block() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_daily_entry),
                subtitle = s.product?.let { p ->
                    p.name + "  ·  " + (s.ratePaise?.let { Money.format(it, s.currency) + " / " + p.displayUnit } ?: stringResource(R.string.label_no_rate))
                },
                actions = {
                    IconButton(onClick = { navigate(Routes.dailySummary(minOf(s.today, s.month.atEndOfMonth()).toEpochDay())) }) {
                        Icon(Icons.Rounded.Summarize, stringResource(R.string.nav_daily_summary))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_fill_usual)) },
                                leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null) },
                                enabled = s.customer != null && !s.locked,
                                onClick = { menu = false; vm.fillDefaults() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_discard_changes)) },
                                leadingIcon = { Icon(Icons.Rounded.Undo, null) },
                                enabled = s.unsavedCount > 0,
                                onClick = { menu = false; vm.discard() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            MonthBottomBar(
                s, busy,
                onSave = { if (s.pendingDeletes > 0) confirmDelete = true else vm.save() },
                onPrevious = vm.neighbour(-1)?.let { c -> { vm.setCustomer(c.id) } },
                onNext = vm.neighbour(1)?.let { c -> { vm.setCustomer(c.id) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.background)) {
            Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                modeSwitch()
                if (s.sessionsEnabled) {
                    ChoiceChips(DeliverySession.entries, s.session, { sessionLabel(it) }, { vm.setSession(it) })
                }
                if (s.products.size > 1) {
                    ChoiceChips(s.products, s.product ?: s.products.first(), { it.name }, { vm.setProduct(it.id) })
                }
                AppSearchField(s.query, vm::setQuery, stringResource(R.string.hint_search_customer))
                if (s.query.isBlank()) {
                    ChoiceChips(
                        RowFilter.entries, s.filter,
                        {
                            when (it) {
                                RowFilter.ALL -> stringResource(R.string.filter_all_count, s.totalCount)
                                RowFilter.PENDING -> stringResource(R.string.filter_pending_count, s.pendingCount)
                                RowFilter.DONE -> stringResource(R.string.filter_done_count, s.doneCount)
                            }
                        },
                        vm::setFilter,
                    )
                    if (s.locked) InlineNotice(stringResource(R.string.notice_period_locked), Tone.WARNING, icon = Icons.Rounded.Lock)
                    else if (!s.canEditPast) InlineNotice(stringResource(R.string.notice_past_readonly), Tone.INFO, icon = Icons.Rounded.Lock)
                    if (s.product != null && s.ratePaise == null) InlineNotice(stringResource(R.string.notice_no_rate, s.product!!.name), Tone.DANGER,
                        action = stringResource(R.string.action_set_rate), onAction = { navigate(Routes.product(s.product!!.id)) })
                }
            }
            Spacer(Modifier.height(Space.s))
            when {
                s.loading -> LoadingList()
                s.product == null -> EmptyState(Icons.Rounded.Block, stringResource(R.string.empty_products_title), stringResource(R.string.empty_products_msg),
                    action = stringResource(R.string.nav_products), onAction = { navigate(Routes.PRODUCTS) })
                s.customers.isEmpty() -> EmptyState(Icons.Rounded.Groups, stringResource(R.string.empty_customers_title), stringResource(R.string.empty_customers_msg),
                    action = stringResource(R.string.action_add_customer), onAction = { navigate(Routes.customerEdit()) })
                s.query.isNotBlank() -> CustomerMatches(s.matches) { c -> guarded { vm.setCustomer(c.id) } }
                else -> {
                    MonthHeader(s.month, s.today) { m -> guarded { vm.setMonth(m) } }
                    if (s.rows.isEmpty()) {
                        EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.empty_filter_title), stringResource(R.string.empty_filter_msg))
                    } else {
                        DayList(s, s.product!!, s.customer!!, vm)
                    }
                }
            }
        }
    }

    pendingSwitch?.let { block ->
        ConfirmDialog(
            title = stringResource(R.string.dlg_unsaved_title),
            message = stringResource(R.string.dlg_unsaved_msg, s.unsavedCount),
            confirmLabel = stringResource(R.string.action_discard),
            destructive = true,
            onConfirm = { block() },
            onDismiss = { pendingSwitch = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_remove_entries_title),
            message = stringResource(R.string.dlg_remove_entries_msg, s.pendingDeletes),
            confirmLabel = stringResource(R.string.action_save),
            destructive = true,
            onConfirm = { vm.save() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, today: java.time.LocalDate, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(Dates.formatMonth(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_previous_month)) }
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) {
            Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_month))
        }
    }
}

@Composable
private fun CustomerMatches(matches: List<CustomerEntity>, onPick: (CustomerEntity) -> Unit) {
    if (matches.isEmpty()) {
        EmptyState(Icons.Rounded.Groups, stringResource(R.string.empty_filter_title), stringResource(R.string.empty_search_msg))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxl)) {
        items(matches, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = Space.screen, vertical = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HouseBadge(c.houseNo)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.code + if (c.mobile.isNotBlank()) "  ·  ${c.mobile}" else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = AppTheme.ext.textTertiary)
            }
            ThinDivider(Modifier.padding(start = 76.dp))
        }
    }
}

@Composable
private fun DayList(s: CustomerMonthUi, product: ProductEntity, customer: CustomerEntity, vm: CustomerMonthViewModel) {
    val list = rememberLazyListState()
    // Index of the first day row that is not on screen yet, for the "more days" hint.
    val nextHidden by remember(s.rows) {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf null
            if (last < s.rows.lastIndex) last + 1 else null
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = Space.xxl)) {
            items(s.rows, key = { it.key }) { row ->
                DayRowItem(
                    row = row,
                    customer = customer,
                    product = product,
                    currency = s.currency,
                    isToday = row.date == s.today,
                    onText = { vm.onText(row.key, it) },
                    onStep = { up -> vm.step(row.key, up) },
                    onMark = { st -> vm.mark(row.key, st) },
                    onClear = { vm.clear(row.key) },
                    modifier = Modifier.animateItem(),
                )
                ThinDivider(Modifier.padding(start = 76.dp))
            }
        }
        nextHidden?.let { i ->
            Surface(color = AppTheme.colors.background.copy(alpha = 0.92f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Row(Modifier.padding(vertical = Space.xs), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ExpandMore, null, tint = AppTheme.ext.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        stringResource(R.string.label_scroll_more_days, s.rows[i].date.dayOfMonth, s.rows.last().date.dayOfMonth),
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBadge(date: java.time.LocalDate, isToday: Boolean, dim: Boolean) {
    val bg = if (isToday) AppTheme.colors.primaryContainer else AppTheme.colors.surfaceContainerHigh
    val fg = when {
        isToday -> AppTheme.colors.onPrimaryContainer
        dim -> AppTheme.ext.textTertiary
        else -> AppTheme.colors.onSurface
    }
    Column(
        Modifier.width(48.dp).clip(MaterialTheme.shapes.small).background(bg).padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("%02d".format(date.dayOfMonth), style = MaterialTheme.typography.titleMedium, color = fg)
        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun DayRowItem(
    row: DayRow,
    customer: CustomerEntity,
    product: ProductEntity,
    currency: String,
    isToday: Boolean,
    onText: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onMark: (DeliveryStatus) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        when {
            row.error != null -> AppTheme.ext.dangerContainer.copy(alpha = 0.45f)
            row.dirty -> AppTheme.ext.warningContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        label = "dayBg",
    )
    var menu by remember { mutableStateOf(false) }
    val savedDesc = stringResource(R.string.state_saved)
    val unsavedDesc = stringResource(R.string.state_unsaved)
    val dayName = Dates.formatDay(row.date)
    Row(
        modifier.fillMaxWidth().background(bg).padding(start = Space.screen, end = Space.xs, top = Space.s, bottom = Space.s)
            .semantics { stateDescription = if (row.dirty) unsavedDesc else if (row.existing != null) savedDesc else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayBadge(row.date, isToday, dim = row.future)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (row.future) AppTheme.ext.textTertiary else Color.Unspecified)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    row.error != null -> Text(row.error.asString(), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.error, maxLines = 2)
                    row.status == DeliveryStatus.NO_DELIVERY -> StatusBadge(stringResource(R.string.status_no_delivery), Tone.NEUTRAL, Icons.Rounded.DoNotDisturbOn)
                    row.status == DeliveryStatus.MISSED -> StatusBadge(stringResource(R.string.status_missed), Tone.DANGER, Icons.Rounded.EventBusy)
                    else -> {
                        Text(
                            customer.code + (row.amountPaise?.let { "  ·  " + Money.format(it, currency) } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary, maxLines = 1,
                        )
                        if (row.existing != null && !row.dirty) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.CheckCircle, savedDesc, tint = AppTheme.ext.success, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        if (!row.editable) {
            Text(
                if (row.status == DeliveryStatus.DELIVERED) Quantity.formatWithUnit(row.existing?.quantityMilli ?: 0, product.displayUnit) else "—",
                style = AppType.quantity, color = if (row.future) AppTheme.ext.textTertiary else Color.Unspecified,
                modifier = Modifier.padding(horizontal = Space.m),
            )
            if (!row.future) Icon(Icons.Rounded.Lock, stringResource(R.string.cd_read_only), tint = AppTheme.ext.textTertiary, modifier = Modifier.padding(end = Space.s).size(18.dp))
        } else {
            QuantityStepper(
                text = if (row.status == DeliveryStatus.DELIVERED || row.status == null) row.text else "",
                unit = product.displayUnit,
                onText = onText,
                onStep = onStep,
                placeholder = if (row.status == DeliveryStatus.NO_DELIVERY || row.status == DeliveryStatus.MISSED) "0" else "–",
                customerName = "${customer.name}, $dayName",
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_row_options, dayName)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.action_mark_no_delivery)) }, { menu = false; onMark(DeliveryStatus.NO_DELIVERY) }, leadingIcon = { Icon(Icons.Rounded.DoNotDisturbOn, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_mark_missed)) }, { menu = false; onMark(DeliveryStatus.MISSED) }, leadingIcon = { Icon(Icons.Rounded.EventBusy, null) })
                    if (row.existing != null || row.dirty) {
                        DropdownMenuItem({ Text(stringResource(R.string.action_clear_entry)) }, { menu = false; onClear() }, leadingIcon = { Icon(Icons.Rounded.Close, null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthBottomBar(s: CustomerMonthUi, busy: Boolean, onSave: () -> Unit, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    Surface(color = AppTheme.colors.surface, shadowElevation = 8.dp, border = BorderStroke(1.dp, AppTheme.ext.border)) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.screen, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (s.unsavedCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AppTheme.ext.warning))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.label_unsaved_count, s.unsavedCount), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(stringResource(R.string.label_tap_save), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                } else {
                    val p = s.product
                    Text(
                        if (p != null) Quantity.formatWithUnit(s.savedQuantityMilli, p.displayUnit) + "  ·  " + Money.format(s.savedAmountPaise, s.currency) else "",
                        style = AppType.money,
                    )
                    Text(stringResource(R.string.label_saved_progress, s.doneCount, s.totalCount), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
            }
            AnimatedVisibility(s.unsavedCount > 0, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                PrimaryButton(stringResource(R.string.action_save_all), onSave, icon = Icons.Rounded.Save, loading = busy, enabled = !s.hasErrors)
            }
            if (s.unsavedCount == 0 && s.customer != null) {
                IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                    Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_previous_customer))
                }
                IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                    Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_customer))
                }
            }
        }
    }
}
