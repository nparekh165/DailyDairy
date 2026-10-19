package com.khodiyar.dairybilling.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.repo.LoginResult
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.security.PasswordHasher
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.Radius
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
private fun BrandMark(size: Int = 64) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(Radius.l)).background(AppTheme.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.WaterDrop, null, tint = AppTheme.colors.onPrimary, modifier = Modifier.size((size * 0.55f).dp))
    }
}

class SetupViewModel(c: AppContainer) : AppViewModel(c) {
    fun complete(dairy: String, phone: String, address: String, name: String, username: String, pin: String, starter: Boolean) =
        action(success = UiText.res(R.string.msg_setup_done)) {
            c.auth.completeSetup(dairy, phone, address, name, username, pin, starter)
            c.prefs.setLastUsername(username.trim().lowercase())
        }
}

@Composable
fun SetupScreen() {
    val vm = appViewModel { SetupViewModel(it) }
    val busy by vm.busy.collectAsState()
    var dairy by rememberSaveable { mutableStateOf("Khodiyar Dairy") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("admin") }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var starter by rememberSaveable { mutableStateOf(true) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val pinWeak = pin.isNotEmpty() && !PasswordHasher.isAcceptable(pin)
    val mismatch = pin2.isNotEmpty() && pin != pin2
    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            Spacer(Modifier.height(Space.xxl))
            BrandMark()
            Spacer(Modifier.height(Space.l))
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(Space.xs))
            Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary)
            Spacer(Modifier.height(Space.xl))

            SectionHeader(stringResource(R.string.setup_business))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(dairy, { dairy = it }, stringResource(R.string.field_dairy_name), leadingIcon = Icons.Rounded.Storefront,
                    error = if (tried && dairy.isBlank()) stringResource(R.string.err_required) else null, capitalizeWords = true)
                Spacer(Modifier.height(Space.s))
                AppTextField(phone, { phone = it }, stringResource(R.string.field_phone), leadingIcon = Icons.Rounded.Phone, keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(Space.s))
                AppTextField(address, { address = it }, stringResource(R.string.field_address), singleLine = false, minLines = 2)
            }
            Spacer(Modifier.height(Space.l))
            SectionHeader(stringResource(R.string.setup_admin))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(name, { name = it }, stringResource(R.string.field_your_name), leadingIcon = Icons.Rounded.Person,
                    error = if (tried && name.isBlank()) stringResource(R.string.err_required) else null, capitalizeWords = true)
                Spacer(Modifier.height(Space.s))
                AppTextField(username, { username = it.trim() }, stringResource(R.string.field_username), leadingIcon = Icons.Rounded.AdminPanelSettings,
                    supporting = stringResource(R.string.hint_username))
                Spacer(Modifier.height(Space.s))
                AppTextField(pin, { pin = it }, stringResource(R.string.field_new_pin), leadingIcon = Icons.Rounded.Lock, password = true,
                    keyboardType = KeyboardType.Password, error = if (pinWeak) stringResource(R.string.err_pin_weak) else null,
                    supporting = stringResource(R.string.hint_pin))
                Spacer(Modifier.height(Space.s))
                AppTextField(pin2, { pin2 = it }, stringResource(R.string.field_confirm_pin), leadingIcon = Icons.Rounded.Lock, password = true,
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    error = if (mismatch) stringResource(R.string.err_pin_mismatch) else null)
            }
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = starter, onCheckedChange = { starter = it })
                Column {
                    Text(stringResource(R.string.setup_starter_products), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.setup_starter_hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                }
            }
            Spacer(Modifier.height(Space.m))
            InlineNotice(stringResource(R.string.setup_offline_note), Tone.INFO)
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                stringResource(R.string.action_create_account),
                onClick = {
                    tried = true
                    if (pin == pin2 && !pinWeak && dairy.isNotBlank() && name.isNotBlank() && pin.isNotEmpty()) {
                        vm.complete(dairy, phone, address, name, username, pin, starter)
                    }
                },
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

class LoginViewModel(c: AppContainer) : AppViewModel(c) {
    val dairyName = MutableStateFlow("")
    val lastUser = MutableStateFlow("")
    val error = MutableStateFlow<UiText?>(null)

    init {
        viewModelScope.launch {
            dairyName.value = c.settings.get().dairyName
            lastUser.value = c.prefs.prefs.first().lastUsername
        }
    }

    fun login(username: String, pin: String) = action {
        error.value = null
        when (val r = c.auth.login(username, pin)) {
            is LoginResult.Success -> {
                c.prefs.setLastUsername(r.user.username)
                c.session.autoLockMinutes = c.settings.get().autoLockMinutes
            }
            LoginResult.InvalidCredentials -> error.value = UiText.res(R.string.err_login_invalid)
            LoginResult.Inactive -> error.value = UiText.res(R.string.err_login_inactive)
            is LoginResult.LockedOut -> error.value = UiText.res(R.string.err_login_locked, Dates.formatDateTime(r.untilMillis))
        }
    }

    fun notifyForgot() = c.notify(UiText.res(R.string.msg_forgot_pin), MessageKind.INFO)
}

@Composable
fun LoginScreen() {
    val vm = appViewModel { LoginViewModel(it) }
    val busy by vm.busy.collectAsState()
    val dairy by vm.dairyName.collectAsState()
    val last by vm.lastUser.collectAsState()
    val error by vm.error.collectAsState()
    var username by rememberSaveable(last) { mutableStateOf(last) }
    var pin by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(AppTheme.colors.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            BrandMark(72)
            Spacer(Modifier.height(Space.l))
            Text(dairy.ifBlank { stringResource(R.string.app_name) }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyMedium, color = AppTheme.ext.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.xxl))
            AppCard(Modifier.fillMaxWidth()) {
                AppTextField(username, { username = it.trim() }, stringResource(R.string.field_username), leadingIcon = Icons.Rounded.Person)
                Spacer(Modifier.height(Space.s))
                AppTextField(
                    pin, { pin = it }, stringResource(R.string.field_pin), leadingIcon = Icons.Rounded.Lock, password = true,
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                    onImeAction = { if (username.isNotBlank() && pin.isNotEmpty()) vm.login(username, pin) },
                )
                if (error != null) {
                    Spacer(Modifier.height(Space.s))
                    InlineNotice(
                        when (val e = error) {
                            is UiText.Res -> stringResource(e.id, *e.args.toTypedArray())
                            is UiText.Raw -> e.text
                            null -> ""
                        },
                        Tone.DANGER,
                    )
                }
                Spacer(Modifier.height(Space.l))
                PrimaryButton(
                    stringResource(R.string.action_sign_in),
                    onClick = { vm.login(username, pin); pin = "" },
                    enabled = username.isNotBlank() && pin.isNotEmpty(),
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Space.m))
            androidx.compose.material3.TextButton(onClick = vm::notifyForgot) { Text(stringResource(R.string.action_forgot_pin)) }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = AppTheme.ext.textTertiary)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.login_device_note), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textTertiary)
            }
        }
    }
}
