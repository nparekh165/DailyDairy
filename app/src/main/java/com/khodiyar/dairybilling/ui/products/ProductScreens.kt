package com.khodiyar.dairybilling.ui.products

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.ProductEntity
import com.khodiyar.dairybilling.data.db.ProductRateEntity
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.DateField
import com.khodiyar.dairybilling.ui.components.EmptyState
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Radius
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun unitName(u: ProductUnit): String = when (u) {
    ProductUnit.LITER -> stringResource(R.string.unit_liter)
    ProductUnit.MILLILITER -> stringResource(R.string.unit_ml)
    ProductUnit.KILOGRAM -> stringResource(R.string.unit_kg)
    ProductUnit.GRAM -> stringResource(R.string.unit_gram)
    ProductUnit.PIECE -> stringResource(R.string.unit_piece)
    ProductUnit.PACKET -> stringResource(R.string.unit_packet)
    ProductUnit.OTHER -> stringResource(R.string.unit_other)
}

class ProductListViewModel(c: AppContainer) : AppViewModel(c) {
    val products = c.products.observeWithRates().stateInVm(null)
    val currency = c.settings.observe().stateInVm(null)
    val canManage get() = c.session.has(Permission.MANAGE_PRODUCTS)
}

@Composable
fun ProductListScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel { ProductListViewModel(it) }
    val list by vm.products.collectAsState()
    val settings by vm.currency.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(R.string.nav_products), onBack = onBack) },
        floatingActionButton = {
            if (vm.canManage) ExtendedFloatingActionButton(onClick = { navigate(Routes.productEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.action_add_product)) })
        },
    ) { padding ->
        val l = list
        when {
            l == null -> LoadingList()
            l.isEmpty() -> EmptyState(Icons.Rounded.Inventory2, stringResource(R.string.empty_products_title), stringResource(R.string.empty_products_msg), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                items(l, key = { it.product.id }) { pw ->
                    AppCard(Modifier.fillMaxWidth(), onClick = { navigate(Routes.product(pw.product.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(Radius.m)).background(AppTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Inventory2, null, tint = AppTheme.colors.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(pw.product.name, style = MaterialTheme.typography.titleMedium)
                                Text(unitName(pw.product.unit) + if (pw.product.unitLabel.isNotBlank()) " (${pw.product.unitLabel})" else "", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val r = pw.currentRate
                                if (r != null) Text(Money.format(r.ratePaise, cur) + " / " + pw.product.displayUnit, style = AppType.money)
                                else StatusBadge(stringResource(R.string.label_no_rate), Tone.DANGER, Icons.Rounded.WarningAmber)
                                if (!pw.product.active) StatusBadge(stringResource(R.string.status_inactive), Tone.NEUTRAL, Icons.Rounded.Block)
                                else pw.upcomingRate?.let {
                                    Text(stringResource(R.string.label_upcoming_rate, Money.format(it.ratePaise, cur), Dates.formatDay(it.effectiveFrom)),
                                        style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class ProductEditViewModel(c: AppContainer, val id: Long) : AppViewModel(c) {
    val existing = MutableStateFlow<ProductEntity?>(null)
    val loaded = MutableStateFlow(id == 0L)
    val today: LocalDate get() = c.time.today()
    init {
        viewModelScope.launch { if (id != 0L) { existing.value = c.products.get(id); loaded.value = true } }
    }

    fun save(name: String, unit: ProductUnit, label: String, step: Long, active: Boolean, rate: Long?, from: LocalDate, onDone: (Long) -> Unit) =
        action(success = UiText.res(R.string.msg_product_saved)) {
            if (id == 0L) onDone(c.products.create(name, unit, label, step, rate, from.toEpochDay()))
            else { c.products.update(id, name, unit, label, step, active); onDone(id) }
        }
}

@Composable
fun ProductEditScreen(id: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "pedit_$id") { ProductEditViewModel(it, id) }
    val existing by vm.existing.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val busy by vm.busy.collectAsState()
    if (!loaded) { LoadingList(); return }
    val e = existing
    var name by rememberSaveable(e) { mutableStateOf(e?.name ?: "") }
    var unit by rememberSaveable(e) { mutableStateOf(e?.unit ?: ProductUnit.LITER) }
    var label by rememberSaveable(e) { mutableStateOf(e?.unitLabel ?: "") }
    var step by rememberSaveable(e) { mutableStateOf(Quantity.format(e?.stepMilli ?: ProductUnit.LITER.defaultStepMilli)) }
    var active by rememberSaveable(e) { mutableStateOf(e?.active ?: true) }
    var rate by rememberSaveable { mutableStateOf("") }
    var from by remember { mutableStateOf(vm.today) }
    val stepParse = Quantity.parse(step, unit)
    val ratePaise = if (rate.isBlank()) null else Money.parseToPaise(rate)

    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { AppTopBar(stringResource(if (id == 0L) R.string.title_add_product else R.string.title_edit_product), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(name, { name = it }, stringResource(R.string.field_product_name), capitalizeWords = true)
                Spacer(Modifier.height(Space.m))
                Text(stringResource(R.string.field_unit), style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                Spacer(Modifier.height(Space.xs))
                ChoiceChips(ProductUnit.entries, unit, { unitName(it) }, {
                    if (unit != it) { unit = it; step = Quantity.format(it.defaultStepMilli) }
                })
                if (unit == ProductUnit.OTHER) {
                    Spacer(Modifier.height(Space.s))
                    AppTextField(label, { label = it.take(10) }, stringResource(R.string.field_unit_label), supporting = stringResource(R.string.hint_unit_label))
                }
                Spacer(Modifier.height(Space.s))
                AppTextField(step, { step = it }, stringResource(R.string.field_step), keyboardType = KeyboardType.Decimal, suffix = label.ifBlank { unit.symbol },
                    supporting = stringResource(R.string.hint_step, unit.maxDecimals),
                    error = when (stepParse) { is Quantity.ParseResult.Ok -> if (stepParse.milli <= 0) stringResource(R.string.err_step_positive) else null; is Quantity.ParseResult.TooPrecise -> stringResource(R.string.err_qty_precision, stepParse.maxDecimals); else -> stringResource(R.string.err_qty_invalid) })
                if (e != null) {
                    Spacer(Modifier.height(Space.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_active), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(active, { active = it })
                    }
                }
            }
            if (id == 0L) {
                SectionHeader(stringResource(R.string.section_initial_rate), Modifier.padding(top = Space.m))
                AppCard(Modifier.fillMaxWidth()) {
                    AppTextField(rate, { rate = it }, stringResource(R.string.field_rate_per, label.ifBlank { unit.symbol }), prefix = "₹ ", keyboardType = KeyboardType.Decimal,
                        error = if (rate.isNotBlank() && (ratePaise == null || ratePaise <= 0)) stringResource(R.string.err_amount_invalid) else null)
                    Spacer(Modifier.height(Space.s))
                    DateField(stringResource(R.string.field_effective_from), from, { from = it })
                }
            } else {
                Spacer(Modifier.height(Space.s))
                InlineNotice(stringResource(R.string.hint_unit_locked), Tone.INFO)
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                stringResource(R.string.action_save), icon = Icons.Rounded.Save, loading = busy, modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && stepParse is Quantity.ParseResult.Ok && (rate.isBlank() || (ratePaise != null && ratePaise > 0)),
                onClick = { vm.save(name, unit, label, (stepParse as Quantity.ParseResult.Ok).milli, active, ratePaise, from, onSaved) },
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

class ProductDetailViewModel(c: AppContainer, val id: Long) : AppViewModel(c) {
    val product = c.products.observe(id).stateInVm(null)
    val rates = c.products.observeRates(id).stateInVm(null)
    val settings = c.settings.observe().stateInVm(null)
    val today: LocalDate get() = c.time.today()
    val canRates get() = c.session.has(Permission.MANAGE_RATES)
    val canEdit get() = c.session.has(Permission.MANAGE_PRODUCTS)
    fun addRate(paise: Long, from: LocalDate, note: String, reprice: Boolean, onDone: () -> Unit) = action {
        val n = c.products.addRate(id, paise, from.toEpochDay(), note, reprice)
        c.notify(UiText.res(R.string.msg_rate_added, n), com.khodiyar.dairybilling.MessageKind.SUCCESS)
        onDone()
    }
    fun deleteRate(r: ProductRateEntity) = action(success = UiText.res(R.string.msg_rate_deleted)) { c.products.deleteFutureRate(r.id) }
}

@Composable
fun ProductDetailScreen(id: Long, onBack: () -> Unit, navigate: (String) -> Unit) {
    val vm = appViewModel(key = "pdet_$id") { ProductDetailViewModel(it, id) }
    val p by vm.product.collectAsState()
    val rates by vm.rates.collectAsState()
    val settings by vm.settings.collectAsState()
    val cur = settings?.currencySymbol ?: "₹"
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProductRateEntity?>(null) }
    val product = p
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(product?.name ?: "", subtitle = product?.let { unitName(it.unit) }, onBack = onBack, actions = {
                if (vm.canEdit) IconButton(onClick = { navigate(Routes.productEdit(id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            })
        },
        floatingActionButton = {
            if (vm.canRates) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Sell, null) }, text = { Text(stringResource(R.string.action_new_rate)) })
        },
    ) { padding ->
        val r = rates
        if (product == null || r == null) { LoadingList(); return@Scaffold }
        val today = vm.today.toEpochDay()
        val current = r.filter { it.effectiveFrom <= today }.maxByOrNull { it.effectiveFrom }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = 96.dp)) {
            item {
                AppCard(Modifier.fillMaxWidth(), color = AppTheme.colors.primaryContainer, border = false) {
                    Text(stringResource(R.string.label_current_rate).uppercase(), style = AppType.overline, color = AppTheme.colors.onPrimaryContainer)
                    Text(current?.let { Money.format(it.ratePaise, cur) + " / " + product.displayUnit } ?: stringResource(R.string.label_no_rate),
                        style = AppType.moneyLarge, color = AppTheme.colors.onPrimaryContainer)
                    current?.let { Text(stringResource(R.string.label_since, Dates.formatDay(it.effectiveFrom)), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.onPrimaryContainer) }
                }
                Spacer(Modifier.height(Space.s))
                InlineNotice(stringResource(R.string.hint_rate_history), Tone.INFO)
                SectionHeader(stringResource(R.string.section_rate_history), Modifier.padding(top = Space.m))
            }
            if (r.isEmpty()) item { EmptyState(Icons.Rounded.Sell, stringResource(R.string.empty_rates), stringResource(R.string.empty_rates_msg)) }
            items(r, key = { it.id }) { rate ->
                val future = rate.effectiveFrom > today
                Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.format(rate.ratePaise, cur) + " / " + product.displayUnit, style = AppType.money)
                        Text(stringResource(R.string.label_from_date, Dates.formatDay(rate.effectiveFrom)) + if (rate.note.isNotBlank()) "  ·  ${rate.note}" else "",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    when {
                        future -> {
                            StatusBadge(stringResource(R.string.status_scheduled), Tone.ACCENT, Icons.Rounded.Schedule)
                            if (vm.canRates) IconButton(onClick = { deleting = rate }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                        }
                        rate.id == current?.id -> StatusBadge(stringResource(R.string.status_current), Tone.SUCCESS)
                        else -> StatusBadge(stringResource(R.string.status_past), Tone.NEUTRAL)
                    }
                }
                ThinDivider()
            }
        }
    }
    if (adding && product != null) RateDialog(product, cur, vm.today, onDismiss = { adding = false }) { paise, from, note, reprice ->
        vm.addRate(paise, from, note, reprice) { adding = false }
    }
    deleting?.let { rate ->
        ConfirmDialog(stringResource(R.string.dlg_delete_rate_title), stringResource(R.string.dlg_delete_rate_msg, Money.format(rate.ratePaise, cur), Dates.formatDay(rate.effectiveFrom)),
            stringResource(R.string.action_delete), onConfirm = { vm.deleteRate(rate) }, onDismiss = { deleting = null }, destructive = true)
    }
}

@Composable
private fun RateDialog(product: ProductEntity, cur: String, today: LocalDate, onDismiss: () -> Unit, onSave: (Long, LocalDate, String, Boolean) -> Unit) {
    var rate by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var reprice by remember { mutableStateOf(false) }
    val paise = Money.parseToPaise(rate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_new_rate, product.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                AppTextField(rate, { rate = it }, stringResource(R.string.field_rate_per, product.displayUnit), prefix = "$cur ", keyboardType = KeyboardType.Decimal,
                    error = if (rate.isNotBlank() && (paise == null || paise <= 0)) stringResource(R.string.err_amount_invalid) else null)
                DateField(stringResource(R.string.field_effective_from), from, { from = it })
                AppTextField(note, { note = it }, stringResource(R.string.field_note_optional))
                if (from < today) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(reprice, { reprice = it })
                        Text(stringResource(R.string.label_reprice), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = paise != null && paise > 0, onClick = { onSave(paise!!, from, note, reprice) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
