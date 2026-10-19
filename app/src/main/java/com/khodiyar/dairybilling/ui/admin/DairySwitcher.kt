@file:OptIn(ExperimentalMaterial3Api::class)

package com.khodiyar.dairybilling.ui.admin

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.DairyApp
import com.khodiyar.dairybilling.MainActivity
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.ui.components.AppTextField
import com.khodiyar.dairybilling.ui.components.ConfirmDialog
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.components.asString
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.Space
import com.khodiyar.dairybilling.workspace.DairyProfile
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun dairyLabel(d: DairyProfile): String = d.name.ifBlank { stringResource(R.string.dairy_unnamed) }

/** Opens [id] and restarts the screen stack on it. Signs out of the current dairy first. */
private suspend fun openDairy(context: Context, id: String) {
    val app = context.applicationContext as DairyApp
    val old = app.container
    old.prefs.clearSession()
    old.prefs.setLastProduct(0)
    old.session.logout()
    app.switchDairy(id)
    val activity = context.findActivity() ?: return
    activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}

/** A compact "Dairy name ▸ switch" button for the sign-in and setup screens. */
@Composable
fun DairySwitchButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Rounded.Storefront, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.action_switch_dairy))
    }
    if (open) DairySwitcherSheet(onDismiss = { open = false })
}

@Composable
fun DairySwitcherSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DairyApp
    val list by app.registry.state.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DairyProfile?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppTheme.colors.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            Text(stringResource(R.string.dairy_switch_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.xs))
            Text(stringResource(R.string.dairy_switch_hint), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
            Spacer(Modifier.height(Space.m))
            list.dairies.forEachIndexed { i, d ->
                if (i > 0) ThinDivider(Modifier.padding(start = 52.dp))
                val current = d.id == list.activeId
                Surface(
                    onClick = {
                        if (current) onDismiss()
                        else if (!switching) { switching = true; scope.launch { openDairy(context, d.id) } }
                    },
                    color = AppTheme.colors.surface,
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (current) AppTheme.colors.primaryContainer else AppTheme.colors.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Storefront, null, tint = if (current) AppTheme.colors.onPrimaryContainer else AppTheme.colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(Space.m))
                        Text(dairyLabel(d), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (current) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.dairy_current), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.primary)
                        } else {
                            IconButton(onClick = { deleting = d }, enabled = !switching) {
                                Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.action_delete), tint = AppTheme.colors.error)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            TextButton(onClick = { adding = true }, enabled = !switching) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_add_dairy))
            }
            Spacer(Modifier.height(Space.l))
        }
    }

    deleting?.let { d ->
        val deletedMsg = stringResource(R.string.msg_dairy_deleted)
        ConfirmDialog(
            title = stringResource(R.string.dlg_delete_dairy_title, dairyLabel(d)),
            message = stringResource(R.string.dlg_delete_dairy_msg),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                app.deleteDairy(d.id)
                android.widget.Toast.makeText(context, deletedMsg, android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }

    if (adding) AddDairyDialog(
        onDismiss = { adding = false },
        onAdd = { name ->
            val profile = app.registry.add(name) // throws AppException for blank or duplicate names
            adding = false
            switching = true
            scope.launch { openDairy(context, profile.id) }
        },
    )
}

@Composable
private fun AddDairyDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<com.khodiyar.dairybilling.domain.UiText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_add_dairy_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.dlg_add_dairy_msg), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.m))
                AppTextField(name, { name = it; error = null }, stringResource(R.string.field_dairy_name), leadingIcon = Icons.Rounded.Storefront,
                    error = error?.asString(), capitalizeWords = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                try { onAdd(name) } catch (e: AppException) { error = e.text }
            }) { Text(stringResource(R.string.action_add_dairy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Tells the user which dairy they are in; used on the More screen. */
@Composable
fun DairyNotice(onSwitch: () -> Unit) {
    val app = LocalContext.current.applicationContext as DairyApp
    val list by app.registry.state.collectAsState()
    if (list.dairies.size > 1) {
        InlineNotice(stringResource(R.string.dairy_signed_in_to, dairyLabel(list.active)), Tone.INFO, icon = Icons.Rounded.Storefront,
            action = stringResource(R.string.action_switch_dairy), onAction = onSwitch)
        Spacer(Modifier.height(Space.m))
    }
}
