package com.khodiyar.dairybilling.ui.billing

import android.content.Context
import com.khodiyar.dairybilling.R
import com.khodiyar.dairybilling.data.db.AppSettingsEntity
import com.khodiyar.dairybilling.data.repo.InvoiceView
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.time.Dates
import com.khodiyar.dairybilling.pdf.BusinessInfo
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.pdf.InvoiceDocument

fun AppSettingsEntity.toBusiness() = BusinessInfo(
    name = dairyName, address = address, phone = phone, header = invoiceHeader, footer = invoiceFooter,
    paymentInstructions = paymentInstructions, upiId = upiId, currency = currencySymbol,
)

/** Builds the printable document from exactly what the screen shows (snapshot if finalized). */
fun InvoiceView.toDocument(todayLabel: String): InvoiceDocument {
    val inv = invoice
    val prev = inv?.supersedesId?.let { id -> history.firstOrNull { it.id == id }?.invoiceNumber }
    return InvoiceDocument(
        business = settings.toBusiness(),
        invoiceNumber = inv?.invoiceNumber,
        revisionOf = prev,
        dateLabel = inv?.let { Dates.formatDateTime(it.finalizedAt).substringBefore(',') } ?: todayLabel,
        periodLabel = Dates.formatMonth(period),
        customerName = inv?.customerName ?: customer.name,
        customerCode = inv?.customerCode ?: customer.code,
        houseNo = inv?.houseNo ?: customer.houseNo,
        mobile = inv?.mobile ?: customer.mobile,
        address = inv?.address ?: customer.address,
        lines = displayLines,
        summary = displaySummary,
    )
}

fun InvoiceView.fileName(): String =
    DocumentFiles.safeName(settings.dairyName, invoice?.customerCode ?: customer.code, period.toString(), if (invoice == null) "DRAFT" else if ((invoice?.revision ?: 0) > 0) "R${invoice!!.revision}" else "", extension = "pdf")

/**
 * WhatsApp/share text that mirrors the invoice exactly (same balance policy). It states that the
 * bill is attached; it never claims the bill was delivered.
 */
fun InvoiceView.shareMessage(context: Context): String {
    val s = displaySummary
    val cur = settings.currencySymbol
    val closing = s.closingBalancePaise
    val lines = buildList {
        add(context.getString(R.string.wa_greeting, invoice?.customerName ?: customer.name))
        add(context.getString(if (invoice == null) R.string.wa_ready_draft else R.string.wa_ready, Dates.formatMonth(period)))
        invoice?.let { add(context.getString(R.string.wa_invoice_no, it.invoiceNumber)) }
        add(context.getString(R.string.wa_charges, Money.format(s.chargesPaise, cur)))
        add(context.getString(R.string.wa_previous, Money.format(s.previousBalancePaise, cur)))
        if (s.adjustmentsPaise != 0L) add(context.getString(R.string.wa_adjustments, Money.format(s.adjustmentsPaise, cur)))
        add(context.getString(R.string.wa_payments, Money.format(s.paymentsPaise, cur)))
        add(
            if (closing < 0) context.getString(R.string.wa_advance, Money.format(-closing, cur))
            else context.getString(R.string.wa_due, Money.format(closing, cur))
        )
        if (settings.upiId.isNotBlank() && closing > 0) add(context.getString(R.string.wa_upi, settings.upiId))
        add(context.getString(R.string.wa_attached))
        add(context.getString(R.string.wa_thanks, settings.dairyName))
    }
    return lines.joinToString("\n")
}
