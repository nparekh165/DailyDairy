package com.khodiyar.dairybilling.pdf

import com.khodiyar.dairybilling.domain.billing.BalanceSummary
import com.khodiyar.dairybilling.domain.billing.DailyLine
import com.khodiyar.dairybilling.domain.billing.InvoiceLine
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity

/** Localized captions used on generated documents. Defaults are English. */
data class PdfLabels(
    val invoice: String = "INVOICE",
    val draftInvoice: String = "DRAFT INVOICE",
    val draftNote: String = "Draft for review - not a final bill",
    val invoiceNo: String = "Invoice no.",
    val date: String = "Date",
    val period: String = "Billing month",
    val billTo: String = "Bill to",
    val house: String = "House",
    val code: String = "Code",
    val mobile: String = "Mobile",
    val item: String = "Item",
    val days: String = "Days",
    val quantity: String = "Quantity",
    val rate: String = "Rate",
    val amount: String = "Amount",
    val total: String = "Total",
    val noDeliveries: String = "No deliveries in this billing month.",
    val charges: String = "Charges this month",
    val previous: String = "Previous balance",
    val adjustments: String = "Adjustments",
    val payments: String = "Payments received",
    val amountDue: String = "Amount due",
    val advance: String = "Advance (credit) balance",
    val paymentInfo: String = "Payment information",
    val upi: String = "UPI",
    val signature: String = "Authorised signatory",
    val page: String = "Page",
    val of: String = "of",
    val generated: String = "Computer-generated document",
    val revisionOf: String = "Revision of",
    val receipt: String = "PAYMENT RECEIPT",
    val receiptNo: String = "Receipt no.",
    val receivedFrom: String = "Received from",
    val method: String = "Method",
    val reference: String = "Reference",
    val amountReceived: String = "Amount received",
    val balanceAfter: String = "Outstanding after this payment",
    val phone: String = "Phone",
)

data class BusinessInfo(
    val name: String,
    val address: String,
    val phone: String,
    val header: String,
    val footer: String,
    val paymentInstructions: String,
    val upiId: String,
    val currency: String,
)

data class InvoiceDocument(
    val business: BusinessInfo,
    /** null for a draft. */
    val invoiceNumber: String?,
    val revisionOf: String?,
    val dateLabel: String,
    val periodLabel: String,
    val customerName: String,
    val customerCode: String,
    val houseNo: String,
    val mobile: String,
    val address: String,
    val lines: List<InvoiceLine>,
    val summary: BalanceSummary,
    /** Day-wise rows. When present the bill lists every day and item instead of one line per item. */
    val dailyLines: List<DailyLine> = emptyList(),
    /** Formats an epoch day for the date column. */
    val formatDate: (Long) -> String = { java.time.LocalDate.ofEpochDay(it).toString() },
)

object InvoicePdfBuilder {

    fun layout(doc: InvoiceDocument, labels: PdfLabels, measurer: TextMeasurer, spec: PageSpec = PageSpec()): PdfLayout {
        val l = PdfLayout(spec, measurer)
        val cur = doc.business.currency
        fun money(p: Long) = Money.format(p, cur)
        val half = spec.contentWidth * 0.56f

        // Header: business (left) and document identity (right)
        val top = l.y
        l.paragraph(doc.business.name, PdfStyles.TITLE, maxWidth = half)
        if (doc.business.address.isNotBlank()) l.paragraph(doc.business.address, PdfStyles.SMALL, maxWidth = half)
        if (doc.business.phone.isNotBlank()) l.paragraph("${labels.phone}: ${doc.business.phone}", PdfStyles.SMALL, maxWidth = half)
        val leftBottom = l.y

        val rx = l.right
        var ry = top
        val title = if (doc.invoiceNumber == null) labels.draftInvoice else labels.invoice
        l.text(rx, ry + 12f, title, PdfStyles.LABEL.copy(size = 10f, color = if (doc.invoiceNumber == null) PdfColors.ACCENT else PdfColors.BRAND), Align.RIGHT)
        ry += 20f
        fun meta(label: String, value: String) {
            l.text(rx, ry + 9f, value, PdfStyles.BODY_BOLD, Align.RIGHT)
            l.text(rx - measurer.width(value, PdfStyles.BODY_BOLD) - 6f, ry + 9f, label, PdfStyles.SMALL, Align.RIGHT)
            ry += 13.5f
        }
        meta(labels.invoiceNo, doc.invoiceNumber ?: "—")
        meta(labels.date, doc.dateLabel)
        meta(labels.period, doc.periodLabel)
        if (doc.revisionOf != null) meta(labels.revisionOf, doc.revisionOf)
        l.y = maxOf(leftBottom, ry) + 6f
        if (doc.business.header.isNotBlank()) l.paragraph(doc.business.header, PdfStyles.SMALL)
        if (doc.invoiceNumber == null && labels.draftNote.isNotBlank()) l.paragraph(labels.draftNote.uppercase(), PdfStyles.WATERMARK)
        l.y += 4f
        l.line(l.left, l.y, l.right, l.y, PdfColors.BRAND, 1.4f)
        l.y += 14f

        // Bill-to (left) and amount due highlight (right)
        val billTop = l.y
        val boxW = 190f
        val leftW = spec.contentWidth - boxW - 16f
        l.text(l.left, l.y + 7.5f, labels.billTo.uppercase(), PdfStyles.LABEL)
        l.y += 13f
        l.paragraph(doc.customerName, PdfStyles.H1, maxWidth = leftW)
        l.paragraph("${labels.house}: ${doc.houseNo}   ·   ${labels.code}: ${doc.customerCode}", PdfStyles.BODY, maxWidth = leftW)
        if (doc.mobile.isNotBlank()) l.paragraph("${labels.mobile}: ${doc.mobile}", PdfStyles.BODY, maxWidth = leftW)
        if (doc.address.isNotBlank()) l.paragraph(doc.address, PdfStyles.SMALL, maxWidth = leftW)
        val closing = doc.summary.closingBalancePaise
        val boxLeft = l.right - boxW
        val boxBottom = billTop + 58f
        l.rect(boxLeft, billTop, l.right, boxBottom, PdfColors.TINT, PdfColors.RULE, 6f)
        l.text(boxLeft + 12f, billTop + 17f, (if (closing < 0) labels.advance else labels.amountDue).uppercase(), PdfStyles.LABEL)
        l.text(boxLeft + 12f, billTop + 42f, money(kotlin.math.abs(closing)), PdfStyles.TOTAL)
        l.y = maxOf(l.y, boxBottom) + 18f

        if (doc.dailyLines.isNotEmpty()) {
            // Day-wise: every day and item with the quantity given, then a total row.
            val daily = TableWriter(
                l,
                listOf(
                    Column(labels.date, 1.7f),
                    Column(labels.item, 2.8f),
                    Column(labels.quantity, 1.5f, Align.RIGHT),
                    Column(labels.rate, 1.7f, Align.RIGHT),
                    Column(labels.amount, 1.7f, Align.RIGHT),
                ),
            )
            daily.header()
            var lastDate = Long.MIN_VALUE
            for (d in doc.dailyLines) {
                daily.row(
                    listOf(
                        if (d.date != lastDate) doc.formatDate(d.date) else "",
                        d.productName,
                        Quantity.formatWithUnit(d.quantityMilli, d.unitLabel),
                        money(d.ratePaise) + if (d.unitLabel.isNotBlank()) " / ${d.unitLabel}" else "",
                        money(d.amountPaise),
                    )
                )
                lastDate = d.date
            }
            // Item totals for the month (quantity per item), so the customer sees how much of each was given.
            for (line in doc.lines) {
                daily.row(listOf("", "${labels.total}: ${line.productName}", Quantity.formatWithUnit(line.quantityMilli, line.unitLabel), "", money(line.amountPaise)), bold = true, shaded = true)
            }
            daily.end()
        } else {
        // Itemized lines
        val table = TableWriter(
            l,
            listOf(
                Column(labels.item, 3.2f),
                Column(labels.days, 0.9f, Align.RIGHT),
                Column(labels.quantity, 1.6f, Align.RIGHT),
                Column(labels.rate, 1.7f, Align.RIGHT),
                Column(labels.amount, 1.8f, Align.RIGHT),
            ),
        )
        table.header()
        if (doc.lines.isEmpty()) {
            l.ensureSpace(24f)
            l.text(l.left + 6f, l.y + 15f, labels.noDeliveries, PdfStyles.SMALL)
            l.y += 24f
            l.line(l.left, l.y, l.right, l.y)
        }
        for (line in doc.lines) {
            table.row(
                listOf(
                    line.productName,
                    line.deliveryDays.toString(),
                    Quantity.formatWithUnit(line.quantityMilli, line.unitLabel),
                    money(line.ratePaise) + if (line.unitLabel.isNotBlank()) " / ${line.unitLabel}" else "",
                    money(line.amountPaise),
                )
            )
        }
        table.end()
        }
        l.y += 14f

        // Totals block (kept together)
        val rows = buildList {
            add(labels.charges to money(doc.summary.chargesPaise))
            add(labels.previous to money(doc.summary.previousBalancePaise))
            if (doc.summary.adjustmentsPaise != 0L) add(labels.adjustments to money(doc.summary.adjustmentsPaise))
            add(labels.payments to "- " + money(doc.summary.paymentsPaise))
        }
        val totalsH = rows.size * 16f + 40f
        l.ensureSpace(totalsH)
        val tx = l.right - 250f
        val totalsTop = l.y
        for ((k, v) in rows) {
            l.text(tx, l.y + 10f, k, PdfStyles.BODY.copy(color = PdfColors.MUTED))
            l.text(l.right, l.y + 10f, v, PdfStyles.BODY, Align.RIGHT)
            l.y += 16f
        }
        l.line(tx, l.y + 2f, l.right, l.y + 2f, PdfColors.TEXT, 0.9f)
        l.y += 8f
        l.text(tx, l.y + 15f, if (closing < 0) labels.advance else labels.amountDue, PdfStyles.H2)
        l.text(l.right, l.y + 16f, money(kotlin.math.abs(closing)), PdfStyles.TOTAL, Align.RIGHT)
        l.y += 30f

        // Payment info sits to the left of the totals when there is room
        val infoText = listOfNotNull(
            doc.business.paymentInstructions.takeIf { it.isNotBlank() },
            doc.business.upiId.takeIf { it.isNotBlank() }?.let { "${labels.upi}: $it" },
        )
        if (infoText.isNotEmpty()) {
            val after = l.y
            l.y = totalsTop
            l.text(l.left, l.y + 7.5f, labels.paymentInfo.uppercase(), PdfStyles.LABEL)
            l.y += 13f
            val startPage = l.pages.size
            infoText.forEach { l.paragraph(it, PdfStyles.SMALL, maxWidth = tx - l.left - 20f) }
            if (l.pages.size == startPage) l.y = maxOf(l.y, after)
        }

        // Footer note and signature
        l.y += 20f
        l.ensureSpace(60f)
        if (doc.business.footer.isNotBlank()) l.paragraph(doc.business.footer, PdfStyles.BODY, maxWidth = spec.contentWidth * 0.55f)
        val sigY = maxOf(l.y + 24f, l.y)
        l.line(l.right - 150f, sigY, l.right, sigY, PdfColors.MUTED, 0.6f)
        l.text(l.right, sigY + 11f, labels.signature, PdfStyles.SMALL, Align.RIGHT)
        l.y = sigY + 18f

        l.finish { n, count ->
            "${doc.business.name}  ·  ${doc.invoiceNumber ?: labels.draftInvoice}  ·  ${labels.generated}" to "${labels.page} $n ${labels.of} $count"
        }
        return l
    }
}

/** Generic tabular report document used for every report export. */
data class TableDocument(
    val title: String,
    val subtitle: String,
    val businessName: String,
    val columns: List<Column>,
    val rows: List<List<String>>,
    val totals: List<String>? = null,
    val summary: List<Pair<String, String>> = emptyList(),
)

object ReportPdfBuilder {
    fun layout(doc: TableDocument, labels: PdfLabels, measurer: TextMeasurer, spec: PageSpec = PageSpec()): PdfLayout {
        val l = PdfLayout(spec, measurer)
        l.paragraph(doc.businessName, PdfStyles.SMALL.copy(color = PdfColors.BRAND, font = PdfFont.BOLD))
        l.paragraph(doc.title, PdfStyles.H1.copy(size = 16f))
        if (doc.subtitle.isNotBlank()) l.paragraph(doc.subtitle, PdfStyles.SMALL)
        l.y += 6f
        l.line(l.left, l.y, l.right, l.y, PdfColors.BRAND, 1.2f)
        l.y += 12f
        if (doc.summary.isNotEmpty()) {
            for ((k, v) in doc.summary) {
                l.ensureSpace(15f)
                l.text(l.left, l.y + 10f, k, PdfStyles.BODY.copy(color = PdfColors.MUTED))
                l.text(l.left + 200f, l.y + 10f, v, PdfStyles.BODY_BOLD)
                l.y += 15f
            }
            l.y += 10f
        }
        val t = TableWriter(l, doc.columns)
        t.header()
        doc.rows.forEach { t.row(it, PdfStyles.BODY.copy(size = 8.5f)) }
        doc.totals?.let { t.row(it, PdfStyles.BODY.copy(size = 8.5f), bold = true, shaded = true) }
        t.end()
        l.finish { n, c -> "${doc.businessName}  ·  ${doc.title}" to "${labels.page} $n ${labels.of} $c" }
        return l
    }
}

data class ReceiptDocument(
    val business: BusinessInfo,
    val receiptNumber: String,
    val dateLabel: String,
    val customerName: String,
    val customerCode: String,
    val houseNo: String,
    val method: String,
    val reference: String,
    val amountPaise: Long,
    val outstandingAfterPaise: Long,
)

object ReceiptPdfBuilder {
    val SPEC = PageSpec(width = 420f, height = 595f, margin = 32f)

    fun layout(doc: ReceiptDocument, labels: PdfLabels, measurer: TextMeasurer): PdfLayout {
        val l = PdfLayout(SPEC, measurer)
        val cur = doc.business.currency
        l.paragraph(doc.business.name, PdfStyles.TITLE.copy(size = 16f))
        if (doc.business.address.isNotBlank()) l.paragraph(doc.business.address, PdfStyles.SMALL)
        if (doc.business.phone.isNotBlank()) l.paragraph("${labels.phone}: ${doc.business.phone}", PdfStyles.SMALL)
        l.y += 8f
        l.line(l.left, l.y, l.right, l.y, PdfColors.BRAND, 1.2f)
        l.y += 14f
        l.text(l.left, l.y + 10f, labels.receipt, PdfStyles.LABEL.copy(size = 10f, color = PdfColors.BRAND))
        l.y += 22f
        fun kv(k: String, v: String) {
            l.text(l.left, l.y + 10f, k, PdfStyles.BODY.copy(color = PdfColors.MUTED))
            val lines = l.wrap(v, PdfStyles.BODY_BOLD, SPEC.contentWidth - 130f)
            lines.forEachIndexed { i, s -> l.text(l.left + 130f, l.y + 10f + i * PdfStyles.BODY_BOLD.lineHeight, s, PdfStyles.BODY_BOLD) }
            l.y += maxOf(1, lines.size) * PdfStyles.BODY_BOLD.lineHeight + 4f
        }
        kv(labels.receiptNo, doc.receiptNumber)
        kv(labels.date, doc.dateLabel)
        kv(labels.receivedFrom, doc.customerName)
        kv("${labels.house} / ${labels.code}", "${doc.houseNo} / ${doc.customerCode}")
        kv(labels.method, doc.method)
        if (doc.reference.isNotBlank()) kv(labels.reference, doc.reference)
        l.y += 10f
        val top = l.y
        l.rect(l.left, top, l.right, top + 56f, PdfColors.TINT, PdfColors.RULE, 6f)
        l.text(l.left + 12f, top + 18f, labels.amountReceived.uppercase(), PdfStyles.LABEL)
        l.text(l.left + 12f, top + 43f, Money.format(doc.amountPaise, cur), PdfStyles.TOTAL)
        l.y = top + 70f
        val due = doc.outstandingAfterPaise
        kv(if (due < 0) labels.advance else labels.balanceAfter, Money.format(kotlin.math.abs(due), cur))
        l.y += 30f
        l.line(l.right - 130f, l.y, l.right, l.y, PdfColors.MUTED)
        l.text(l.right, l.y + 11f, labels.signature, PdfStyles.SMALL, Align.RIGHT)
        l.finish { _, _ -> labels.generated to doc.receiptNumber }
        return l
    }
}
