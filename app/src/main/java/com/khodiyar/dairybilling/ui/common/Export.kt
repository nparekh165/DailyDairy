package com.khodiyar.dairybilling.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.khodiyar.dairybilling.MessageKind
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.domain.AppException
import com.khodiyar.dairybilling.domain.UiText
import com.khodiyar.dairybilling.export.CsvWriter
import com.khodiyar.dairybilling.pdf.AndroidPdfWriter
import com.khodiyar.dairybilling.pdf.PdfLabels
import com.khodiyar.dairybilling.pdf.ReportPdfBuilder
import com.khodiyar.dairybilling.pdf.TableDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** A file the user saves through the Storage Access Framework (they choose the location). */
class ExportRequest(val fileName: String, val mime: String, val write: suspend (OutputStream) -> Unit)

private class MimeHolder { var mime: String = "application/octet-stream"; var pending: ExportRequest? = null }

@Composable
fun rememberExporter(): (ExportRequest) -> Unit {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val holder = remember { MimeHolder() }
    val launcher = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).setType(holder.mime)
        }
    ) { uri: Uri? ->
        val req = holder.pending ?: return@rememberLauncherForActivityResult
        holder.pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { req.write(it) } ?: throw AppException(R.string.err_save_failed)
                }
                container.notify(UiText.res(R.string.msg_file_saved, req.fileName), MessageKind.SUCCESS)
            } catch (e: AppException) {
                container.notify(e.text, MessageKind.ERROR)
            } catch (e: Exception) {
                container.notify(UiText.res(R.string.err_save_failed), MessageKind.ERROR)
            }
        }
    }
    return { req ->
        holder.mime = req.mime
        holder.pending = req
        launcher.launch(req.fileName)
    }
}

/** Builds CSV and PDF export requests from the same table so both formats always agree. */
object TableExports {
    fun csv(name: String, doc: TableDocument): ExportRequest = ExportRequest("$name.csv", "text/csv") { out ->
        val rows = doc.rows + listOfNotNull(doc.totals)
        CsvWriter.write(out, doc.columns.map { it.title }, rows)
    }

    fun pdf(name: String, doc: TableDocument, labels: PdfLabels): ExportRequest = ExportRequest("$name.pdf", "application/pdf") { out ->
        val layout = withContext(Dispatchers.Default) { ReportPdfBuilder.layout(doc, labels, AndroidPdfWriter.measurer) }
        AndroidPdfWriter.write(layout, out)
    }
}

@Composable
fun pdfLabels(): PdfLabels {
    val c = LocalContext.current
    return remember(c.resources.configuration) { pdfLabelsOf(c) }
}

fun pdfLabelsOf(c: Context): PdfLabels = PdfLabels(
    invoice = c.getString(R.string.pdf_invoice),
    draftInvoice = c.getString(R.string.pdf_draft_invoice),
    draftNote = c.getString(R.string.pdf_draft_note),
    invoiceNo = c.getString(R.string.pdf_invoice_no),
    date = c.getString(R.string.pdf_date),
    period = c.getString(R.string.pdf_period),
    billTo = c.getString(R.string.pdf_bill_to),
    house = c.getString(R.string.pdf_house),
    code = c.getString(R.string.pdf_code),
    mobile = c.getString(R.string.pdf_mobile),
    item = c.getString(R.string.pdf_item),
    days = c.getString(R.string.pdf_days),
    quantity = c.getString(R.string.pdf_quantity),
    rate = c.getString(R.string.pdf_rate),
    amount = c.getString(R.string.pdf_amount),
    noDeliveries = c.getString(R.string.pdf_no_deliveries),
    charges = c.getString(R.string.pdf_charges),
    previous = c.getString(R.string.pdf_previous),
    adjustments = c.getString(R.string.pdf_adjustments),
    payments = c.getString(R.string.pdf_payments),
    amountDue = c.getString(R.string.pdf_amount_due),
    advance = c.getString(R.string.pdf_advance),
    paymentInfo = c.getString(R.string.pdf_payment_info),
    upi = c.getString(R.string.pdf_upi),
    signature = c.getString(R.string.pdf_signature),
    page = c.getString(R.string.pdf_page),
    of = c.getString(R.string.pdf_of),
    generated = c.getString(R.string.pdf_generated),
    revisionOf = c.getString(R.string.pdf_revision_of),
    receipt = c.getString(R.string.pdf_receipt),
    receiptNo = c.getString(R.string.pdf_receipt_no),
    receivedFrom = c.getString(R.string.pdf_received_from),
    method = c.getString(R.string.pdf_method),
    reference = c.getString(R.string.pdf_reference),
    amountReceived = c.getString(R.string.pdf_amount_received),
    balanceAfter = c.getString(R.string.pdf_balance_after),
    phone = c.getString(R.string.pdf_phone),
)
