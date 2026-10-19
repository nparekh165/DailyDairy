package com.khodiyar.dairybilling.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewModelScope
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.backup.BackupPackage
import com.khodiyar.dairybilling.data.prefs.UiPrefs
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.ExportRequest
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.common.rememberExporter
import com.khodiyar.dairybilling.ui.components.AppCard
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.KeyValueRow
import com.khodiyar.dairybilling.ui.components.PinConfirmDialog
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SecondaryButton
import com.khodiyar.dairybilling.ui.components.SectionHeader
import com.khodiyar.dairybilling.ui.components.ThinDivider
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.format.DateTimeFormatter

class BackupViewModel(c: AppContainer) : AppViewModel(c) {
    val prefs = c.prefs.prefs.stateInVm(UiPrefs())
    val inspected = MutableStateFlow<BackupPackage?>(null)
    val status = MutableStateFlow<UiText?>(null)
    val safety = MutableStateFlow<List<File>>(emptyList())
    init { refreshSafety() }
    private fun refreshSafety() = viewModelScope.launch { safety.value = withContext(Dispatchers.IO) { c.backup.listSafetyBackups(c.safetyBackupDir) } }

    fun fileName(): String = "khodiyar_backup_" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip"

    suspend fun writeBackup(out: OutputStream) {
        val m = c.backup.createBackup(out)
        c.prefs.setLastBackup(m.createdAt)
        status.value = UiText.res(R.string.msg_backup_done, m.counts["customers"] ?: 0, m.counts["deliveries"] ?: 0)
    }

    fun inspect(context: android.content.Context, uri: Uri) = action {
        status.value = null
        inspected.value = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { c.backup.read(it) } ?: throw AppException(R.string.err_backup_corrupt)
        }
    }

    fun restore(onRestored: () -> Unit) = action {
        val pkg = inspected.value ?: return@action
        val safety = withContext(Dispatchers.IO) { c.backup.restore(pkg, c.safetyBackupDir) }
        inspected.value = null
        refreshSafety()
        c.notify(UiText.res(R.string.msg_restore_done, safety.name), MessageKind.SUCCESS)
        c.auth.logout()
        onRestored()
    }

    fun failed(t: UiText) { status.value = t }
}

@Composable
fun BackupScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    val vm = appViewModel { BackupViewModel(it) }
    val prefs by vm.prefs.collectAsState()
    val pkg by vm.inspected.collectAsState()
    val status by vm.status.collectAsState()
    val safety by vm.safety.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    val exporter = rememberExporter()
    var confirmRestore by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.inspect(context, uri) }

    Scaffold(contentWindowInsets = WindowInsets(0), topBar = { AppTopBar(stringResource(R.string.nav_backup), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            InlineNotice(stringResource(R.string.backup_device_warning), Tone.WARNING, icon = Icons.Rounded.Shield)
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_create_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_create_msg), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                Spacer(Modifier.height(Space.s))
                KeyValueRow(stringResource(R.string.label_last_backup), if (prefs.lastBackupAt == 0L) stringResource(R.string.label_never) else Dates.formatDateTime(prefs.lastBackupAt))
                Spacer(Modifier.height(Space.s))
                PrimaryButton(stringResource(R.string.action_create_backup), {
                    exporter(ExportRequest(vm.fileName(), "application/zip") { out -> vm.writeBackup(out) })
                }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Backup)
            }
            status?.let {
                val text = when (it) { is UiText.Res -> stringResource(it.id, *it.args.toTypedArray()); is UiText.Raw -> it.text }
                InlineNotice(text, Tone.SUCCESS, icon = Icons.Rounded.CheckCircle)
            }
            AppCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_restore_msg), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                Spacer(Modifier.height(Space.s))
                SecondaryButton(stringResource(R.string.action_choose_backup), { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Restore)
                pkg?.let { p ->
                    Spacer(Modifier.height(Space.m))
                    ThinDivider()
                    Spacer(Modifier.height(Space.s))
                    InlineNotice(stringResource(R.string.backup_valid), Tone.SUCCESS, icon = Icons.Rounded.CheckCircle)
                    KeyValueRow(stringResource(R.string.label_backup_business), p.manifest.dairyName)
                    KeyValueRow(stringResource(R.string.label_backup_created), Dates.formatDateTime(p.manifest.createdAt))
                    KeyValueRow(stringResource(R.string.label_backup_by), p.manifest.createdBy)
                    KeyValueRow(stringResource(R.string.label_backup_schema), "v${p.manifest.schemaVersion} · app ${p.manifest.appVersion}")
                    KeyValueRow(stringResource(R.string.nav_customers), (p.manifest.counts["customers"] ?: 0).toString())
                    KeyValueRow(stringResource(R.string.label_backup_deliveries), (p.manifest.counts["deliveries"] ?: 0).toString())
                    KeyValueRow(stringResource(R.string.nav_payments), (p.manifest.counts["payments"] ?: 0).toString())
                    KeyValueRow(stringResource(R.string.label_backup_invoices), (p.manifest.counts["invoices"] ?: 0).toString())
                    Spacer(Modifier.height(Space.s))
                    InlineNotice(stringResource(R.string.backup_restore_warning), Tone.DANGER)
                    Spacer(Modifier.height(Space.s))
                    PrimaryButton(stringResource(R.string.action_restore), { confirmRestore = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Restore, loading = busy)
                }
            }
            if (safety.isNotEmpty()) {
                SectionHeader(stringResource(R.string.section_safety_backups))
                AppCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_safety_msg), style = MaterialTheme.typography.bodySmall, color = AppTheme.ext.textSecondary)
                    safety.take(5).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Dates.formatDateTime(f.lastModified()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exporter(ExportRequest(f.name, "application/zip") { out -> f.inputStream().use { it.copyTo(out) } }) }) {
                                Text(stringResource(R.string.action_save_copy))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
    if (confirmRestore) PinConfirmDialog(
        title = stringResource(R.string.dlg_restore_title),
        message = stringResource(R.string.dlg_restore_msg),
        confirmLabel = stringResource(R.string.action_restore),
        verify = vm::verifyPin, onConfirmed = { vm.restore(onRestored) }, onDismiss = { confirmRestore = false },
    )
}
