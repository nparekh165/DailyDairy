package com.khodiyar.dairybilling.ui.admin

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.BuildConfig
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.db.UserEntity
import com.khodiyar.dairybilling.data.prefs.Features
import com.khodiyar.dairybilling.data.prefs.ThemeMode
import com.khodiyar.dairybilling.data.prefs.UiPrefs
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.model.UserRole
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.Permission
import com.khodiyar.dairybilling.ui.Routes
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.LocalFeatures
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ChoiceChips
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.StatusBadge
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.AppType
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------------- More hub ----------------

class MoreViewModel(c: AppContainer) : AppViewModel(c) {
    val user = c.session.user
    val settings = c.settings.observe().stateInVm(null)
    fun can(p: Permission) = c.session.has(p)
    fun logout() = viewModelScope.launch { c.prefs.clearSession(); c.auth.logout() }
}

@Composable
fun MoreScreen(navigate: (String) -> Unit) {
    val vm = appViewModel { MoreViewModel(it) }
    val user by vm.user.collectAsState()
    val settings by vm.settings.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }
    var dairies by remember { mutableStateOf(false) }
    val features = LocalFeatures.current
    val entries = buildList {
        add(Triple(R.string.nav_payments, Icons.Rounded.Payments, Routes.PAYMENTS).takeIf { vm.can(Permission.RECORD_PAYMENTS) || vm.can(Permission.VIEW_REPORTS) })
        add(Triple(R.string.nav_reports, Icons.Rounded.Insights, Routes.REPORTS).takeIf { vm.can(Permission.VIEW_REPORTS) })
        add(Triple(R.string.nav_products, Icons.Rounded.Inventory2, Routes.PRODUCTS))
        add(Triple(R.string.nav_staff, Icons.Rounded.ManageAccounts, Routes.STAFF).takeIf { features.advanced && vm.can(Permission.MANAGE_USERS) })
        add(Triple(R.string.nav_backup, Icons.Rounded.Backup, Routes.BACKUP).takeIf { vm.can(Permission.BACKUP) })
        add(Triple(R.string.nav_audit, Icons.Rounded.Policy, Routes.AUDIT).takeIf { features.advanced && vm.can(Permission.VIEW_AUDIT) })
        add(Triple(R.string.nav_settings, Icons.Rounded.Settings, Routes.SETTINGS))
    }.filterNotNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.xxl)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = Space.l, bottom = Space.m)) {
                Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.headlineMedium)
                Text(settings?.dairyName ?: "", style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary)
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(if (user?.role == UserRole.ADMIN) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Badge, null, tint = AppTheme.colors.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(user?.displayName ?: "", style = MaterialTheme.typography.titleMedium)
                        Text("@${user?.username ?: ""}  ·  " + stringResource(if (user?.role == UserRole.ADMIN) R.string.role_admin else R.string.role_staff), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    // With sign-in switched off there is nothing to sign out to.
                    if (features.requireSignIn) TextButton(onClick = { confirmLogout = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            DairyNotice(onSwitch = { dairies = true })
        }
        item {
            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xs)) {
                entries.forEachIndexed { i, (res, icon, route) ->
                    if (i > 0) ThinDivider(Modifier.padding(start = 64.dp))
                    NavRow(stringResource(res), icon) { navigate(route) }
                }
                ThinDivider(Modifier.padding(start = 64.dp))
                NavRow(stringResource(R.string.nav_dairies), Icons.Rounded.Storefront) { dairies = true }
            }
        }
        item {
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.label_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textTertiary, modifier = Modifier.fillMaxWidth())
        }
    }
    if (dairies) DairySwitcherSheet(onDismiss = { dairies = false })
    if (confirmLogout) ConfirmDialog(stringResource(R.string.action_logout), stringResource(R.string.dlg_logout_msg), stringResource(R.string.action_logout), onConfirm = { vm.logout() }, onDismiss = { confirmLogout = false })
}

@Composable
private fun NavRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = AppTheme.colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.m))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = AppTheme.ext.textTertiary)
        }
    }
}

// ---------------- Staff ----------------

class StaffViewModel(c: AppContainer) : AppViewModel(c) {
    val users = c.auth.observeUsers().stateInVm(null)
    val me get() = c.session.current
    fun create(name: String, username: String, pin: String, perms: Set<Permission>, onDone: () -> Unit) =
        action(success = UiText.res(R.string.msg_staff_created)) { c.auth.createStaff(name, username, pin, perms); onDone() }
    fun update(u: UserEntity, name: String, perms: Set<Permission>, active: Boolean, onDone: () -> Unit) =
        action(success = UiText.res(R.string.msg_staff_updated)) { c.auth.updateStaff(u.id, name, perms, active); onDone() }
    fun resetPin(u: UserEntity, pin: String, onDone: () -> Unit) = action(success = UiText.res(R.string.msg_pin_reset)) { c.auth.resetPin(u.id, pin); onDone() }
}

@Composable
fun permissionLabel(p: Permission): String = when (p) {
    Permission.RECORD_PAYMENTS -> stringResource(R.string.perm_record_payments)
    Permission.EDIT_PAST_DELIVERIES -> stringResource(R.string.perm_edit_past)
    Permission.VIEW_REPORTS -> stringResource(R.string.perm_view_reports)
    Permission.VIEW_BILLING -> stringResource(R.string.perm_view_billing)
    Permission.MANAGE_CUSTOMERS -> stringResource(R.string.perm_manage_customers)
    Permission.MANAGE_RATES -> stringResource(R.string.perm_manage_rates)
    Permission.FINALIZE_INVOICES -> stringResource(R.string.perm_finalize)
    else -> p.name
}

@Composable
fun StaffScreen(onBack: () -> Unit) {
    val vm = appViewModel { StaffViewModel(it) }
    val users by vm.users.collectAsState()
    var editing by remember { mutableStateOf<UserEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf<UserEntity?>(null) }
    var pendingReset by remember { mutableStateOf<UserEntity?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(R.string.nav_staff), onBack = onBack) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Person, null) }, text = { Text(stringResource(R.string.action_add_staff)) }) },
    ) { padding ->
        val list = users
        if (list == null) { LoadingList(); return@Scaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            item { InlineNotice(stringResource(R.string.staff_device_note), Tone.INFO) }
            items(list, key = { it.id }) { u ->
                AppCard(Modifier.fillMaxWidth(), onClick = if (u.role == UserRole.STAFF) ({ editing = u }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                        }
                        if (!u.active) StatusBadge(stringResource(R.string.status_inactive), Tone.NEUTRAL, Icons.Rounded.Block)
                        else StatusBadge(stringResource(if (u.role == UserRole.ADMIN) R.string.role_admin else R.string.role_staff), if (u.role == UserRole.ADMIN) Tone.BRAND else Tone.INFO)
                    }
                    if (u.role == UserRole.STAFF) {
                        val perms = Permission.parse(u.permissions)
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            (listOf(stringResource(R.string.perm_record_deliveries)) + Permission.STAFF_GRANTABLE.filter { it in perms }.map { permissionLabel(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary,
                        )
                    }
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { pendingReset = u }) { Icon(Icons.Rounded.Key, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.action_reset_pin)) }
                    }
                }
            }
        }
    }
    if (creating) StaffDialog(null, onDismiss = { creating = false }) { name, username, pin, perms, _ -> vm.create(name, username, pin, perms) { creating = false } }
    editing?.let { u -> StaffDialog(u, onDismiss = { editing = null }) { name, _, _, perms, active -> vm.update(u, name, perms, active) { editing = null } } }
    pendingReset?.let { u ->
        PinConfirmDialog(stringResource(R.string.dlg_reset_pin_title, u.displayName), stringResource(R.string.dlg_reauth_msg), stringResource(R.string.action_continue),
            verify = vm::verifyPin, onConfirmed = { resetting = u }, onDismiss = { pendingReset = null })
    }
    resetting?.let { u ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetting = null },
            title = { Text(stringResource(R.string.dlg_reset_pin_title, u.displayName)) },
            text = { AppTextField(pin, { pin = it.filter(Char::isDigit) }, stringResource(R.string.field_new_pin), password = true, keyboardType = KeyboardType.NumberPassword, supporting = stringResource(R.string.hint_pin)) },
            confirmButton = { Button(enabled = pin.length >= 6, onClick = { vm.resetPin(u, pin) { resetting = null } }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { resetting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun StaffDialog(user: UserEntity?, onDismiss: () -> Unit, onSave: (String, String, String, Set<Permission>, Boolean) -> Unit) {
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var username by remember { mutableStateOf(user?.username ?: "") }
    var pin by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(user?.active ?: true) }
    var perms by remember { mutableStateOf(user?.let { Permission.parse(it.permissions) } ?: setOf(Permission.RECORD_PAYMENTS)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (user == null) R.string.action_add_staff else R.string.title_edit_staff)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                AppTextField(name, { name = it }, stringResource(R.string.field_staff_name), capitalizeWords = true)
                if (user == null) {
                    AppTextField(username, { username = it.trim() }, stringResource(R.string.field_username), supporting = stringResource(R.string.hint_username))
                    AppTextField(pin, { pin = it.filter(Char::isDigit) }, stringResource(R.string.field_new_pin), password = true, keyboardType = KeyboardType.NumberPassword, supporting = stringResource(R.string.hint_pin))
                }
                Text(stringResource(R.string.section_permissions), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.hint_permissions), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                Permission.STAFF_GRANTABLE.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p in perms, { on -> perms = if (on) perms + p else perms - p })
                        Text(permissionLabel(p), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (user != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_active), modifier = Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && (user != null || (username.length >= 3 && pin.length >= 6)), onClick = { onSave(name, username, pin, perms, active) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ---------------- Settings ----------------

@Composable
private fun FeatureSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
        }
        Spacer(Modifier.width(Space.s))
        Switch(checked, onChange)
    }
}

class SettingsViewModel(c: AppContainer) : AppViewModel(c) {
    val settings = c.settings.observe().stateInVm(null)
    val prefs = c.prefs.prefs.stateInVm(UiPrefs())
    val canEdit get() = c.session.has(Permission.SETTINGS)
    fun save(s: AppSettingsEntity) = action(success = UiText.res(R.string.msg_settings_saved)) {
        c.settings.update(s)
        c.dairyNamed(s.dairyName)
        if (!c.prefs.features.first().requireSignIn) c.session.autoLockMinutes = 0 // no auto-lock without sign-in
    }

    val features = c.prefs.features.stateInVm(Features())

    /** Switching sign-in off also stops auto-lock; switching it on restores the saved auto-lock time. */
    fun setFeatures(f: Features) = action {
        c.prefs.setFeatures(f)
        c.session.autoLockMinutes = if (f.requireSignIn) c.settings.get().autoLockMinutes else 0
    }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { c.prefs.setTheme(t) }
    fun changePin(current: String, new: String, onDone: () -> Unit) = action(success = UiText.res(R.string.msg_pin_changed)) { c.auth.changeOwnPin(current, new); onDone() }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val st by vm.settings.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val busy by vm.busy.collectAsState()
    val feats by vm.features.collectAsState()
    var changingPin by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { AppTopBar(stringResource(R.string.nav_settings), onBack = onBack) }) { padding ->
        val s = st
        if (s == null) { LoadingList(); return@Scaffold }
        var name by rememberSaveable(s) { mutableStateOf(s.dairyName) }
        var address by rememberSaveable(s) { mutableStateOf(s.address) }
        var phone by rememberSaveable(s) { mutableStateOf(s.phone) }
        var header by rememberSaveable(s) { mutableStateOf(s.invoiceHeader) }
        var footer by rememberSaveable(s) { mutableStateOf(s.invoiceFooter) }
        var instructions by rememberSaveable(s) { mutableStateOf(s.paymentInstructions) }
        var upi by rememberSaveable(s) { mutableStateOf(s.upiId) }
        var currency by rememberSaveable(s) { mutableStateOf(s.currencySymbol) }
        var prefix by rememberSaveable(s) { mutableStateOf(s.invoicePrefix) }
        var sessions by rememberSaveable(s) { mutableStateOf(s.sessionsEnabled) }
        var autoLock by rememberSaveable(s) { mutableStateOf(s.autoLockMinutes.toString()) }
        val edit = vm.canEdit
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            SectionHeader(stringResource(R.string.section_appearance))
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.field_theme), style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                ChoiceChips(ThemeMode.entries, prefs.theme, {
                    when (it) { ThemeMode.SYSTEM -> stringResource(R.string.theme_system); ThemeMode.LIGHT -> stringResource(R.string.theme_light); ThemeMode.DARK -> stringResource(R.string.theme_dark) }
                }, { vm.setTheme(it) })
                Spacer(Modifier.height(Space.m))
                Text(stringResource(R.string.field_language), style = MaterialTheme.typography.labelMedium, color = AppTheme.ext.textSecondary)
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "" }
                ChoiceChips(listOf("", "en", "hi"), if (current.startsWith("hi")) "hi" else if (current.startsWith("en")) "en" else "", {
                    when (it) { "en" -> "English"; "hi" -> "हिन्दी"; else -> stringResource(R.string.theme_system) }
                }, { tag -> AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)) })
            }
            if (edit) {
                SectionHeader(stringResource(R.string.section_features), Modifier.padding(top = Space.m))
                AppCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.features_hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    FeatureSwitch(stringResource(R.string.feature_sign_in), stringResource(R.string.feature_sign_in_hint), feats.requireSignIn) { vm.setFeatures(feats.copy(requireSignIn = it)) }
                    FeatureSwitch(stringResource(R.string.feature_pin_actions), stringResource(R.string.feature_pin_actions_hint), feats.pinForActions) { vm.setFeatures(feats.copy(pinForActions = it)) }
                    FeatureSwitch(stringResource(R.string.feature_billing_steps), stringResource(R.string.feature_billing_steps_hint), feats.billingSteps) { vm.setFeatures(feats.copy(billingSteps = it)) }
                    FeatureSwitch(stringResource(R.string.feature_advanced), stringResource(R.string.feature_advanced_hint), feats.advanced) { vm.setFeatures(feats.copy(advanced = it)) }
                }
            }
            SectionHeader(stringResource(R.string.section_business), Modifier.padding(top = Space.m))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(name, { name = it }, stringResource(R.string.field_dairy_name), enabled = edit)
                Spacer(Modifier.height(Space.s))
                AppTextField(phone, { phone = it }, stringResource(R.string.field_phone), keyboardType = KeyboardType.Phone, enabled = edit)
                Spacer(Modifier.height(Space.s))
                AppTextField(address, { address = it }, stringResource(R.string.field_address), singleLine = false, minLines = 2, enabled = edit)
            }
            SectionHeader(stringResource(R.string.section_invoice), Modifier.padding(top = Space.m))
            AppCard(Modifier.fillMaxWidth()) {
                if (feats.advanced) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppTextField(prefix, { prefix = it.uppercase().take(8) }, stringResource(R.string.field_invoice_prefix), Modifier.weight(1f), enabled = edit,
                        supporting = "$prefix-2026-10-0001")
                    AppTextField(currency, { currency = it.take(4) }, stringResource(R.string.field_currency), Modifier.weight(1f), enabled = edit)
                }
                if (feats.advanced) {
                    Spacer(Modifier.height(Space.s))
                    AppTextField(header, { header = it }, stringResource(R.string.field_invoice_header), singleLine = false, enabled = edit)
                    Spacer(Modifier.height(Space.s))
                }
                AppTextField(footer, { footer = it }, stringResource(R.string.field_invoice_footer), singleLine = false, enabled = edit)
                Spacer(Modifier.height(Space.s))
                AppTextField(instructions, { instructions = it }, stringResource(R.string.field_payment_instructions), singleLine = false, enabled = edit)
                Spacer(Modifier.height(Space.s))
                AppTextField(upi, { upi = it.trim() }, stringResource(R.string.field_upi), enabled = edit)
            }
            if (feats.advanced) SectionHeader(stringResource(R.string.section_delivery_billing), Modifier.padding(top = Space.m))
            if (feats.advanced) AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.field_sessions), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.hint_sessions), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    }
                    Switch(sessions, { sessions = it }, enabled = edit)
                }
                Spacer(Modifier.height(Space.s))
                if (feats.requireSignIn) AppTextField(autoLock, { autoLock = it.filter(Char::isDigit).take(3) }, stringResource(R.string.field_auto_lock), keyboardType = KeyboardType.Number, enabled = edit,
                    supporting = stringResource(R.string.hint_auto_lock))
            }
            if (edit) {
                Spacer(Modifier.height(Space.l))
                PrimaryButton(stringResource(R.string.action_save_settings), {
                    vm.save(s.copy(dairyName = name, address = address, phone = phone, invoiceHeader = header, invoiceFooter = footer, paymentInstructions = instructions,
                        upiId = upi, currencySymbol = currency, invoicePrefix = prefix, sessionsEnabled = sessions, autoLockMinutes = autoLock.toIntOrNull() ?: 15))
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Save, loading = busy)
            }
            if (feats.requireSignIn || feats.pinForActions) SectionHeader(stringResource(R.string.section_security), Modifier.padding(top = Space.l))
            if (feats.requireSignIn || feats.pinForActions) AppCard(Modifier.fillMaxWidth()) {
                PrimaryButton(stringResource(R.string.action_change_pin), { changingPin = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Key)
                Spacer(Modifier.height(Space.m))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Security, null, tint = AppTheme.ext.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s))
                    Text(stringResource(R.string.security_limitations), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
    if (changingPin) {
        var current by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changingPin = false },
            title = { Text(stringResource(R.string.action_change_pin)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    AppTextField(current, { current = it.filter(Char::isDigit) }, stringResource(R.string.field_current_pin), password = true, keyboardType = KeyboardType.NumberPassword)
                    AppTextField(new, { new = it.filter(Char::isDigit) }, stringResource(R.string.field_new_pin), password = true, keyboardType = KeyboardType.NumberPassword, supporting = stringResource(R.string.hint_pin))
                    AppTextField(confirm, { confirm = it.filter(Char::isDigit) }, stringResource(R.string.field_confirm_pin), password = true, keyboardType = KeyboardType.NumberPassword,
                        error = if (confirm.isNotEmpty() && confirm != new) stringResource(R.string.err_pin_mismatch) else null)
                }
            },
            confirmButton = { Button(enabled = current.isNotEmpty() && new.length >= 6 && new == confirm, onClick = { vm.changePin(current, new) { changingPin = false } }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { changingPin = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---------------- Audit log ----------------

class AuditViewModel(c: AppContainer) : AppViewModel(c) {
    val entries = c.db.audit().observeRecent(500).stateInVm(null)
}

@Composable
fun AuditScreen(onBack: () -> Unit) {
    val vm = appViewModel { AuditViewModel(it) }
    val list by vm.entries.collectAsState()
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { AppTopBar(stringResource(R.string.nav_audit), subtitle = stringResource(R.string.audit_subtitle), onBack = onBack) }) { padding ->
        val l = list
        if (l == null) { LoadingList(); return@Scaffold }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = Space.xxl)) {
            items(l, key = { it.id }) { e ->
                Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.action.replace('_', ' '), style = AppType.overline, color = AppTheme.colors.primary, modifier = Modifier.weight(1f))
                        Text(Dates.formatDateTime(e.timestamp), style = MaterialTheme.typography.labelSmall, color = AppTheme.ext.textTertiary)
                    }
                    Text(e.details, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(e.userName, style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
                ThinDivider()
            }
        }
    }
}
