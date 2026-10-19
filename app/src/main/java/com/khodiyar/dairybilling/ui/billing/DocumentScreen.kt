package com.khodiyar.dairybilling.ui.billing

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.khodiyar.dairybilling.AppContainer
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.pdf.AndroidPdfWriter
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.pdf.InvoicePdfBuilder
import com.khodiyar.dairybilling.pdf.PdfLabels
import com.khodiyar.dairybilling.pdf.ReceiptDocument
import com.khodiyar.dairybilling.pdf.ReceiptPdfBuilder
import com.khodiyar.dairybilling.share.ShareHelper
import com.khodiyar.dairybilling.ui.common.AppViewModel
import com.khodiyar.dairybilling.ui.common.ExportRequest
import com.khodiyar.dairybilling.ui.common.appViewModel
import com.khodiyar.dairybilling.ui.common.pdfLabelsOf
import com.khodiyar.dairybilling.ui.common.rememberExporter
import com.khodiyar.dairybilling.ui.components.AppTopBar
import com.khodiyar.dairybilling.ui.components.ErrorState
import com.khodiyar.dairybilling.ui.components.InlineNotice
import com.khodiyar.dairybilling.ui.components.LoadingList
import com.khodiyar.dairybilling.ui.components.PrimaryButton
import com.khodiyar.dairybilling.ui.components.SecondaryButton
import com.khodiyar.dairybilling.ui.components.Tone
import com.khodiyar.dairybilling.ui.components.asString
import com.khodiyar.dairybilling.ui.customers.methodLabel
import com.khodiyar.dairybilling.ui.theme.AppTheme
import com.khodiyar.dairybilling.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DocState {
    data object Loading : DocState
    data class Ready(
        val file: File,
        val pages: List<Bitmap>,
        val title: String,
        val message: String,
        val mobile: String,
        val isDraft: Boolean,
    ) : DocState
    data class Failed(val text: UiText) : DocState
}

class DocumentViewModel(c: AppContainer, private val kind: String, private val a: Long, private val b: Long, private val cId: Long) : AppViewModel(c) {
    val state = MutableStateFlow<DocState>(DocState.Loading)

    suspend fun generate(context: android.content.Context, labels: PdfLabels, methodName: (com.khodiyar.dairybilling.domain.model.PaymentMethod) -> String) {
        state.value = DocState.Loading
        state.value = try {
            withContext(Dispatchers.IO) {
                if (kind == "receipt") receipt(context, labels, methodName) else invoice(context, labels)
            }
        } catch (e: AppException) {
            DocState.Failed(e.text)
        } catch (e: Exception) {
            DocState.Failed(UiText.res(R.string.err_pdf_failed, e.message ?: ""))
        }
    }

    private suspend fun invoice(context: android.content.Context, labels: PdfLabels): DocState {
        val period = Dates.fromPeriodKey(b.toInt())
        val view = c.billing.invoiceView(a, period, cId)?.forFeatures(c.prefs.features.first()) ?: throw AppException(R.string.err_not_found)
        val layout = InvoicePdfBuilder.layout(view.toDocument(Dates.formatDay(c.time.today())), labels.forView(view), AndroidPdfWriter.measurer)
        val file = DocumentFiles.newFile(context, view.fileName())
        AndroidPdfWriter.write(layout, file)
        if (AndroidPdfWriter.verify(file) == 0) throw AppException(R.string.err_pdf_unreadable)
        view.invoice?.let { c.billing.markPdfGenerated(it.id) }
        return DocState.Ready(file, AndroidPdfWriter.renderPages(file, 1080),
            view.invoice?.invoiceNumber ?: context.getString(if (view.simple) R.string.pdf_invoice else R.string.pdf_draft_invoice),
            view.shareMessage(context), view.invoice?.mobile ?: view.customer.mobile, view.invoice == null && !view.simple)
    }

    private suspend fun receipt(context: android.content.Context, labels: PdfLabels, methodName: (com.khodiyar.dairybilling.domain.model.PaymentMethod) -> String): DocState {
        val p = c.payments.get(a) ?: throw AppException(R.string.err_not_found)
        if (p.status != com.khodiyar.dairybilling.domain.model.RecordStatus.ACTIVE) throw AppException(R.string.err_receipt_reversed)
        val cust = c.customers.get(p.customerId) ?: throw AppException(R.string.err_not_found)
        val settings = c.settings.get()
        val number = "RCPT-" + Dates.periodKey(Dates.periodOf(p.date)) + "-" + p.id.toString().padStart(5, '0')
        val outstanding = c.billing.outstandingAll()[cust.id] ?: 0L
        val doc = ReceiptDocument(settings.toBusiness(), number, Dates.formatDay(p.date), cust.name, cust.code, cust.houseNo, methodName(p.method), p.reference, p.amountPaise, outstanding)
        val layout = ReceiptPdfBuilder.layout(doc, labels, AndroidPdfWriter.measurer)
        val file = DocumentFiles.newFile(context, DocumentFiles.safeName(settings.dairyName, "Receipt", cust.code, number, extension = "pdf"))
        AndroidPdfWriter.write(layout, file)
        if (AndroidPdfWriter.verify(file) == 0) throw AppException(R.string.err_pdf_unreadable)
        val msg = context.getString(R.string.wa_receipt, cust.name, com.khodiyar.dairybilling.domain.money.Money.format(p.amountPaise, settings.currencySymbol), Dates.formatDay(p.date), settings.dairyName)
        return DocState.Ready(file, AndroidPdfWriter.renderPages(file, 900), number, msg, cust.mobile, false)
    }

    fun shareOutcome(o: ShareHelper.Outcome) {
        if (o == ShareHelper.Outcome.NO_APP) c.notify(UiText.res(R.string.err_no_share_app), MessageKind.ERROR)
    }
    fun whatsappMissing() = c.notify(UiText.res(R.string.err_no_whatsapp), MessageKind.ERROR)
    fun copied() = c.notify(UiText.res(R.string.msg_copied), MessageKind.SUCCESS)
}

@Composable
fun DocumentScreen(kind: String, a: Long, b: Long, cId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "doc_${kind}_${a}_${b}_$cId") { DocumentViewModel(it, kind, a, b, cId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val exporter = rememberExporter()
    val cash = stringResource(R.string.method_cash)
    val upi = stringResource(R.string.method_upi)
    val bank = stringResource(R.string.method_bank)
    val other = stringResource(R.string.method_other)
    LaunchedEffect(kind, a, b, cId) {
        vm.generate(context, pdfLabelsOf(context)) {
            when (it) {
                com.khodiyar.dairybilling.domain.model.PaymentMethod.CASH -> cash
                com.khodiyar.dairybilling.domain.model.PaymentMethod.UPI -> upi
                com.khodiyar.dairybilling.domain.model.PaymentMethod.BANK_TRANSFER -> bank
                com.khodiyar.dairybilling.domain.model.PaymentMethod.OTHER -> other
            }
        }
    }
    val chooserTitle = stringResource(R.string.share_chooser_title)
    val s = state
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(stringResource(if (kind == "receipt") R.string.title_receipt else R.string.title_pdf_preview), subtitle = (s as? DocState.Ready)?.title, onBack = onBack) },
        bottomBar = {
            if (s is DocState.Ready) Surface(color = AppTheme.colors.surface, shadowElevation = 8.dp, border = BorderStroke(1.dp, AppTheme.ext.border)) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        PrimaryButton(stringResource(R.string.action_whatsapp), {
                            val o = ShareHelper.shareToWhatsApp(context, s.file, s.message)
                            if (o == ShareHelper.Outcome.NO_APP) vm.whatsappMissing()
                        }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Send)
                        SecondaryButton(stringResource(R.string.action_share), { vm.shareOutcome(ShareHelper.share(context, s.file, "application/pdf", s.message, chooserTitle)) }, Modifier.weight(1f), icon = Icons.Rounded.Share)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        SecondaryButton(stringResource(R.string.action_save_to_device), {
                            exporter(ExportRequest(s.file.name, "application/pdf") { out -> s.file.inputStream().use { it.copyTo(out) } })
                        }, Modifier.weight(1f), icon = Icons.Rounded.Download)
                        if (ShareHelper.normalizeIndianMobile(s.mobile) != null) {
                            SecondaryButton(stringResource(R.string.action_open_chat), {
                                val o = ShareHelper.openWhatsAppChat(context, s.mobile, s.message)
                                if (o == ShareHelper.Outcome.NO_APP) vm.whatsappMissing()
                            }, Modifier.weight(1f), icon = Icons.Rounded.Sms)
                        } else {
                            SecondaryButton(stringResource(R.string.action_copy_message), { clipboard.setText(AnnotatedString(s.message)); vm.copied() }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy)
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (s) {
            DocState.Loading -> LoadingList(3, Modifier.padding(padding))
            is DocState.Failed -> ErrorState(s.text.asString(), Modifier.padding(padding))
            is DocState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Space.screen),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                item { InlineNotice(stringResource(R.string.notice_share_manual), Tone.INFO) }
                if (s.isDraft) item { InlineNotice(stringResource(R.string.notice_draft_pdf), Tone.WARNING) }
                itemsIndexed(s.pages) { i, bmp ->
                    Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, AppTheme.ext.border), shadowElevation = 2.dp) {
                        Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.cd_pdf_page, i + 1, s.pages.size), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
                item { Spacer(Modifier.height(Space.l)) }
            }
        }
    }
}
