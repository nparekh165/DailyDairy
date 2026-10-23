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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.DeliveryEntryEntity
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.domain.model.DeliverySession
import com.khodiyar.dairybilling.domain.model.DeliveryStatus
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppSearchField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.DateStepper
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
import com.khodiyar.dairybilling.ui.theme.Radius
import com.khodiyar.dairybilling.ui.theme.Space

@Composable
fun statusLabel(s: DeliveryStatus): String = when (s) {
    DeliveryStatus.DELIVERED -> stringResource(R.string.status_delivered)
    DeliveryStatus.NO_DELIVERY -> stringResource(R.string.status_no_delivery)
    DeliveryStatus.MISSED -> stringResource(R.string.status_missed)
}

@Composable
fun sessionLabel(s: DeliverySession): String = when (s) {
    DeliverySession.MORNING -> stringResource(R.string.session_morning)
    DeliverySession.EVENING -> stringResource(R.string.session_evening)
}

enum class EntryMode { BY_DATE, BY_CUSTOMER }

/** Delivery tab: all customers for one day, or one customer for every day of a month. */
@Composable
fun DeliveryScreen(navigate: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(EntryMode.BY_DATE) }
    var monthRequest by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val dayVm = appViewModel { DeliveryViewModel(it) }
    val monthVm = appViewModel { CustomerMonthViewModel(it) }
    val modeSwitch: @Composable () -> Unit = {
        ChoiceChips(
            EntryMode.entries, mode,
            {
                when (it) {
                    EntryMode.BY_DATE -> stringResource(R.string.mode_by_date)
                    EntryMode.BY_CUSTOMER -> stringResource(R.string.mode_by_customer)
                }
            },
            { m ->
                if (m != mode && !(if (mode == EntryMode.BY_DATE) dayVm.blockedByDrafts() else monthVm.blockedByDrafts())) mode = m
            },
        )
    }
    when (mode) {
        EntryMode.BY_DATE -> DailyEntryScreen(navigate, modeSwitch) { customerId, epochDay ->
            if (!dayVm.blockedByDrafts()) {
                monthRequest = customerId to epochDay
                mode = EntryMode.BY_CUSTOMER
            }
        }
        EntryMode.BY_CUSTOMER -> CustomerMonthScreen(navigate, monthRequest, { monthRequest = null }, modeSwitch)
    }
}

@Composable
private fun DailyEntryScreen(navigate: (String) -> Unit, modeSwitch: @Composable () -> Unit, openMonth: (Long, Long) -> Unit) {
    val vm = appViewModel { DeliveryViewModel(it) }
    val s by vm.ui.collectAsState()
    val busy by vm.busy.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetCustomer by remember { mutableStateOf<com.khodiyar.dairybilling.data.db.CustomerEntity?>(null) }

    fun changeDate(d: java.time.LocalDate) {
        if (vm.hasDrafts) pendingDate = d else vm.setDate(d)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_daily_entry),
                subtitle = s.product?.let { p ->
                    p.name + "  ·  " + (s.ratePaise?.let { Money.format(it, s.currency) + " / " + p.displayUnit } ?: stringResource(R.string.label_no_rate))
                },
                actions = {
                    IconButton(onClick = { navigate(Routes.dailySummary(s.date.toEpochDay())) }) {
                        Icon(Icons.Rounded.Summarize, stringResource(R.string.nav_daily_summary))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more_options)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_fill_usual)) },
                                leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null) },
                                enabled = s.dateEditable,
                                onClick = { menu = false; vm.fillDefaults() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy_previous_day)) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                enabled = s.dateEditable,
                                onClick = { menu = false; vm.copyPreviousDay() },
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
            DeliveryBottomBar(s, busy) {
                if (s.pendingDeletes > 0) confirmDelete = true else vm.save()
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.background)) {
            Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                modeSwitch()
                DateStepper(s.date, ::changeDate, Modifier.fillMaxWidth(), maxDate = s.today, today = s.today)
                if (s.sessionsEnabled) {
                    ChoiceChips(DeliverySession.entries, s.session, { sessionLabel(it) }, { vm.setSession(it) })
                }
                if (s.products.size > 1) {
                    ChoiceChips(s.products, s.product ?: s.products.first(), { it.name }, { vm.setProduct(it.id) })
                }
                AppSearchField(s.query, vm::setQuery, stringResource(R.string.hint_search_customer))
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
                else if (!s.dateEditable) InlineNotice(stringResource(R.string.notice_past_readonly), Tone.INFO, icon = Icons.Rounded.Lock)
                if (s.product != null && s.ratePaise == null) InlineNotice(stringResource(R.string.notice_no_rate, s.product!!.name), Tone.DANGER,
                    action = stringResource(R.string.action_set_rate), onAction = { navigate(Routes.product(s.product!!.id)) })
            }
            Spacer(Modifier.height(Space.s))
            when {
                s.loading -> LoadingList()
                s.product == null -> EmptyState(Icons.Rounded.Block, stringResource(R.string.empty_products_title), stringResource(R.string.empty_products_msg),
                    action = stringResource(R.string.nav_products), onAction = { navigate(Routes.PRODUCTS) })
                s.rows.isEmpty() && s.totalCount == 0 -> EmptyState(Icons.Rounded.Groups, stringResource(R.string.empty_customers_title), stringResource(R.string.empty_customers_msg),
                    action = stringResource(R.string.action_add_customer), onAction = { navigate(Routes.customerEdit()) })
                s.rows.isEmpty() -> EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.empty_filter_title), stringResource(R.string.empty_filter_msg))
                else -> {
                    val product = s.product!!
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxl)) {
                        items(s.rows, key = { it.key }) { row ->
                            DeliveryRowItem(
                                row = row,
                                product = product,
                                currency = s.currency,
                                onText = { vm.onText(row.customer.id, it) },
                                onStep = { up -> vm.step(row.customer.id, up) },
                                onMark = { st -> vm.mark(row.customer.id, st) },
                                onClear = { vm.clear(row.customer.id) },
                                onOpen = { sheetCustomer = row.customer },
                                onMonth = { openMonth(row.customer.id, s.date.toEpochDay()) },
                                modifier = Modifier.animateItem(),
                            )
                            ThinDivider(Modifier.padding(start = 76.dp))
                        }
                    }
                }
            }
        }
    }

    pendingDate?.let { d ->
        ConfirmDialog(
            title = stringResource(R.string.dlg_unsaved_title),
            message = stringResource(R.string.dlg_unsaved_msg, s.unsavedCount),
            confirmLabel = stringResource(R.string.action_discard),
            destructive = true,
            onConfirm = { vm.setDate(d) },
            onDismiss = { pendingDate = null },
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
    sheetCustomer?.let { cust ->
        CustomerDaySheet(vm, cust, s.currency, s.dateEditable) { sheetCustomer = null }
    }
}

@Composable
private fun DeliveryBottomBar(s: DeliveryUi, busy: Boolean, onSave: () -> Unit) {
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
        }
    }
}

@Composable
private fun DeliveryRowItem(
    row: DeliveryRow,
    product: ProductEntity,
    currency: String,
    onText: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onMark: (DeliveryStatus) -> Unit,
    onClear: () -> Unit,
    onOpen: () -> Unit,
    onMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        when {
            row.error != null -> AppTheme.ext.dangerContainer.copy(alpha = 0.45f)
            row.dirty -> AppTheme.ext.warningContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        label = "rowBg",
    )
    var menu by remember { mutableStateOf(false) }
    val savedDesc = stringResource(R.string.state_saved)
    val unsavedDesc = stringResource(R.string.state_unsaved)
    Row(
        modifier.fillMaxWidth().background(bg).padding(start = Space.screen, end = Space.xs, top = Space.s, bottom = Space.s)
            .semantics { stateDescription = if (row.dirty) unsavedDesc else if (row.existing != null) savedDesc else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HouseBadge(row.customer.houseNo, active = row.customer.active)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).padding(vertical = 2.dp)) {
            Text(row.customer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    row.error != null -> Text(row.error.asString(), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.error, maxLines = 2)
                    row.status == DeliveryStatus.NO_DELIVERY -> StatusBadge(stringResource(R.string.status_no_delivery), Tone.NEUTRAL, Icons.Rounded.DoNotDisturbOn)
                    row.status == DeliveryStatus.MISSED -> StatusBadge(stringResource(R.string.status_missed), Tone.DANGER, Icons.Rounded.EventBusy)
                    else -> {
                        Text(
                            row.customer.code + (row.amountPaise?.let { "  ·  " + Money.format(it, currency) } ?: ""),
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
                style = AppType.quantity, modifier = Modifier.padding(horizontal = Space.m),
            )
            Icon(Icons.Rounded.Lock, stringResource(R.string.cd_read_only), tint = AppTheme.ext.textTertiary, modifier = Modifier.padding(end = Space.s).size(18.dp))
        } else {
            QuantityStepper(
                text = if (row.status == DeliveryStatus.DELIVERED || row.status == null) row.text else "",
                unit = product.displayUnit,
                onText = onText,
                onStep = onStep,
                placeholder = if (row.status == DeliveryStatus.NO_DELIVERY || row.status == DeliveryStatus.MISSED) "0" else "–",
                customerName = row.customer.name,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_row_options, row.customer.name)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.action_mark_no_delivery)) }, { menu = false; onMark(DeliveryStatus.NO_DELIVERY) }, leadingIcon = { Icon(Icons.Rounded.DoNotDisturbOn, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_mark_missed)) }, { menu = false; onMark(DeliveryStatus.MISSED) }, leadingIcon = { Icon(Icons.Rounded.EventBusy, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_all_products)) }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Rounded.Groups, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.action_month_entry)) }, { menu = false; onMonth() }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) })
                    if (row.existing != null || row.dirty) {
                        DropdownMenuItem({ Text(stringResource(R.string.action_clear_entry)) }, { menu = false; onClear() }, leadingIcon = { Icon(Icons.Rounded.Close, null) })
                    }
                }
            }
        }
    }
}

/** − [ qty ] + with a numeric keyboard; Next moves to the following customer. */
@Composable
fun QuantityStepper(text: String, unit: String, onText: (String) -> Unit, onStep: (Boolean) -> Unit, placeholder: String, customerName: String) {
    val focus = LocalFocusManager.current
    val decrease = stringResource(R.string.cd_decrease, customerName)
    val increase = stringResource(R.string.cd_increase, customerName)
    val qtyDesc = stringResource(R.string.cd_quantity_for, customerName)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, decrease) { onStep(false) }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = AppTheme.colors.surface,
            border = BorderStroke(1.dp, if (text.isNotEmpty()) AppTheme.colors.primary.copy(alpha = 0.6f) else AppTheme.colors.outline),
            modifier = Modifier.width(76.dp).heightIn(min = 48.dp),
        ) {
            Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BasicTextField(
                    value = text,
                    onValueChange = onText,
                    singleLine = true,
                    textStyle = AppType.quantity.copy(textAlign = TextAlign.Center, color = AppTheme.colors.onSurface),
                    cursorBrush = SolidColor(AppTheme.colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = qtyDesc },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (text.isEmpty()) Text(placeholder, style = AppType.quantity, color = AppTheme.ext.textTertiary)
                            inner()
                        }
                    },
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textTertiary)
            }
        }
        StepButton(Icons.Rounded.Add, increase) { onStep(true) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(AppTheme.colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDaySheet(vm: DeliveryViewModel, customer: com.khodiyar.dairybilling.data.db.CustomerEntity, currency: String, editable: Boolean, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loaded by remember { mutableStateOf<List<Pair<ProductEntity, DeliveryEntryEntity?>>?>(null) }
    val values = remember { mutableStateMapOf<Long, DraftEntry>() }
    val rates = remember { mutableStateMapOf<Long, Long?>() }
    val busy by vm.busy.collectAsState()
    LaunchedEffect(customer.id) {
        val l = vm.loadCustomerDay(customer.id)
        l.forEach { (p, e) ->
            values[p.id] = DraftEntry(if (e != null && e.status == DeliveryStatus.DELIVERED) Quantity.format(e.quantityMilli) else "", e?.status ?: DeliveryStatus.DELIVERED)
            rates[p.id] = vm.rateOn(p.id)
        }
        loaded = l
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = AppTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen).padding(bottom = Space.xxl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HouseBadge(customer.houseNo)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(customer.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(customer.code + if (customer.mobile.isNotBlank()) "  ·  ${customer.mobile}" else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
            }
            Spacer(Modifier.height(Space.l))
            val l = loaded
            if (l == null) {
                LoadingList(3)
            } else {
                l.forEach { (p, _) ->
                    val v = values[p.id] ?: DraftEntry("", DeliveryStatus.DELIVERED)
                    val rate = rates[p.id]
                    val milli = (Quantity.parse(v.text, p.unit) as? Quantity.ParseResult.Ok)?.milli
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            val sub = when {
                                v.status == DeliveryStatus.NO_DELIVERY -> stringResource(R.string.status_no_delivery)
                                v.status == DeliveryStatus.MISSED -> stringResource(R.string.status_missed)
                                rate == null -> stringResource(R.string.label_no_rate)
                                milli != null -> Money.format(rate, currency) + " / " + p.displayUnit + "  =  " + Money.format(Money.lineAmount(milli, rate), currency)
                                else -> Money.format(rate, currency) + " / " + p.displayUnit
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                        }
                        if (editable) {
                            QuantityStepper(
                                text = if (v.status == DeliveryStatus.DELIVERED) v.text else "",
                                unit = p.displayUnit,
                                onText = { t -> values[p.id] = DraftEntry(t.replace(',', '.').filter { it.isDigit() || it == '.' }, DeliveryStatus.DELIVERED) },
                                onStep = { up ->
                                    val cur = milli ?: 0L
                                    val next = if (up) cur + p.stepMilli else (cur - p.stepMilli).coerceAtLeast(0)
                                    values[p.id] = DraftEntry(if (next == 0L) "" else Quantity.format(next), DeliveryStatus.DELIVERED)
                                },
                                placeholder = "–",
                                customerName = customer.name + " " + p.name,
                            )
                        } else {
                            Text(if (v.text.isBlank()) "—" else v.text + " " + p.displayUnit, style = AppType.quantity)
                        }
                    }
                    if (editable) {
                        ChoiceChips(
                            listOf(DeliveryStatus.DELIVERED, DeliveryStatus.NO_DELIVERY, DeliveryStatus.MISSED), v.status,
                            { statusLabel(it) },
                            { st -> values[p.id] = DraftEntry(if (st == DeliveryStatus.DELIVERED) v.text else "", st) },
                        )
                    }
                    ThinDivider(Modifier.padding(top = Space.s))
                }
                Spacer(Modifier.height(Space.l))
                if (editable) {
                    PrimaryButton(
                        stringResource(R.string.action_save),
                        onClick = {
                            vm.saveCustomerDay(customer.id, l.associate { (p, _) -> p to (values[p.id] ?: DraftEntry("", DeliveryStatus.DELIVERED)) }, l.associate { (p, e) -> p.id to e }) { onDismiss() }
                        },
                        icon = Icons.Rounded.Save,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    InlineNotice(stringResource(R.string.notice_past_readonly), Tone.INFO, icon = Icons.Rounded.Lock)
                }
                Spacer(Modifier.height(Radius.s))
            }
        }
    }
}
