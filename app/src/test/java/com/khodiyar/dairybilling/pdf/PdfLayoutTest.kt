package com.khodiyar.dairybilling.pdf

import com.khodiyar.dairybilling.domain.billing.BalanceSummary
import com.khodiyar.dairybilling.domain.billing.InvoiceLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {
    /** Approximates proportional fonts: average glyph width ~0.52em, bold slightly wider. */
    private val measurer = TextMeasurer { text, style -> text.length * style.size * if (style.font == PdfFont.BOLD) 0.56f else 0.52f }

    private val business = BusinessInfo("Khodiyar Dairy", "Shop 4, Main Road, Rajkot", "98765 43210", "", "Thank you", "Pay by 10th", "khodiyar@upi", "₹")

    private fun doc(lines: Int, name: String = "Ramesh Patel") = InvoiceDocument(
        business, "KD-2026-10-0007", null, "01 Nov 2026", "October 2026", name, "C-0007", "A-002", "9876543210", "Flat 2, Shanti Nagar",
        (1..lines).map { InvoiceLine(it.toLong(), "Product $it", "L", 6000, 30_000, 180_000, 30) },
        BalanceSummary(50_000, lines * 180_000L, 0, 100_000),
    )

    private fun texts(l: PdfLayout) = l.pages.flatMap { p -> p.ops.filterIsInstance<DrawOp.Text>().map { it.text } }

    @Test fun invoiceContainsExpectedInformation() {
        val l = InvoicePdfBuilder.layout(doc(2), PdfLabels(), measurer)
        val all = texts(l).joinToString("\n")
        listOf("Khodiyar Dairy", "KD-2026-10-0007", "October 2026", "Ramesh Patel", "A-002", "C-0007", "9876543210",
            "Product 1", "Product 2", "₹60.00 / L", "₹1,800.00", "Charges this month", "₹3,600.00", "Previous balance", "₹500.00",
            "Payments received", "- ₹1,000.00", "Amount due", "₹3,100.00", "khodiyar@upi", "Authorised signatory").forEach {
            assertTrue("missing '$it'", all.contains(it))
        }
        assertEquals(1, l.pages.size)
        assertTrue(all.contains("Page 1 of 1"))
    }

    @Test fun longInvoicePaginatesWithRepeatedHeaderAndNoOverflow() {
        val l = InvoicePdfBuilder.layout(doc(80), PdfLabels(), measurer)
        assertTrue("expected several pages, got ${l.pages.size}", l.pages.size >= 3)
        val spec = l.spec
        l.pages.forEach { page ->
            val texts = page.ops.filterIsInstance<DrawOp.Text>()
            // Every content line is above the footer area; footer lines sit in the bottom margin.
            texts.filter { !it.text.startsWith("Page ") && !it.text.contains("Computer-generated") }.forEach {
                assertTrue("text '${it.text}' at ${it.baseline} overflows on page ${page.number}", it.baseline <= spec.bottomLimit + 0.5f)
                assertTrue(it.x >= 0 && it.x <= spec.width)
            }
            assertTrue("table header repeated on page ${page.number}", texts.any { it.text == "ITEM" })
        }
        val all = texts(l)
        (1..80).forEach { n -> assertEquals("Product $n printed once", 1, all.count { it == "Product $n" }) }
        assertTrue(all.contains("Page ${l.pages.size} of ${l.pages.size}"))
        // Totals are kept together on one page.
        val totalsPages = l.pages.filter { p -> p.ops.filterIsInstance<DrawOp.Text>().any { it.text == "Amount due" } }
        assertEquals(1, totalsPages.size)
    }

    @Test fun longNamesWrapInsteadOfClipping() {
        val longName = "Shrimati Bhagyalakshmi Ramachandran Venkatanarayanan Subramaniam Iyer"
        val l = InvoicePdfBuilder.layout(doc(1, longName), PdfLabels(), measurer)
        val words = longName.split(" ")
        val all = texts(l).joinToString(" ")
        words.forEach { assertTrue(all.contains(it)) }
        val nameLines = l.pages[0].ops.filterIsInstance<DrawOp.Text>().filter { t -> words.any { w -> t.text.contains(w) } && t.style == PdfStyles.H1 }
        assertTrue("name should wrap to multiple lines", nameLines.size >= 2)
        nameLines.forEach { assertTrue(measurer.width(it.text, it.style) <= l.spec.contentWidth) }
    }

    @Test fun wrapBreaksUnbreakableWords() {
        val l = PdfLayout(PageSpec(), measurer)
        val lines = l.wrap("X".repeat(400), PdfStyles.BODY, 100f)
        assertTrue(lines.size > 1)
        lines.forEach { assertTrue(measurer.width(it, PdfStyles.BODY) <= 100f) }
        assertEquals(400, lines.sumOf { it.length })
    }

    @Test fun draftIsClearlyMarked() {
        val l = InvoicePdfBuilder.layout(doc(1).copy(invoiceNumber = null), PdfLabels(), measurer)
        val all = texts(l).joinToString("\n")
        assertTrue(all.contains("DRAFT INVOICE"))
        assertTrue(all.contains("NOT A FINAL BILL"))
    }

    @Test fun advanceBalanceShownAsCredit() {
        val d = doc(1).copy(summary = BalanceSummary(0, 180_000, 0, 200_000))
        val all = texts(InvoicePdfBuilder.layout(d, PdfLabels(), measurer)).joinToString("\n")
        assertTrue(all.contains("Advance (credit) balance"))
        assertTrue(all.contains("₹200.00"))
    }

    @Test fun reportTablePaginates() {
        val doc = TableDocument("Outstanding", "01 Nov 2026", "Khodiyar Dairy",
            listOf(Column("House", 1f), Column("Customer", 3f), Column("Due", 1.5f, Align.RIGHT)),
            (1..500).map { listOf("A-$it", "Customer $it", "100.00") }, listOf("", "Total", "50000.00"))
        val l = ReportPdfBuilder.layout(doc, PdfLabels(), measurer)
        assertTrue(l.pages.size > 5)
        assertEquals(500, texts(l).count { it.startsWith("Customer ") && it != "CUSTOMER" })
    }

    @Test fun receiptFitsOnePage() {
        val r = ReceiptDocument(business, "RCPT-202610-00012", "05 Oct 2026", "Ramesh Patel", "C-0007", "A-002", "UPI", "TXN123", 150_000, 20_000)
        val l = ReceiptPdfBuilder.layout(r, PdfLabels(), measurer)
        assertEquals(1, l.pages.size)
        val all = texts(l).joinToString("\n")
        assertTrue(all.contains("₹1,500.00"))
        assertTrue(all.contains("RCPT-202610-00012"))
    }

    @Test fun safeFileNames() {
        assertEquals("Khodiyar_Dairy_A002_2026-10.pdf", DocumentFiles.safeName("Khodiyar Dairy", "A002", "2026-10", extension = "pdf"))
        assertEquals("a_b_c.pdf", DocumentFiles.safeName("a/b\\c", extension = "pdf"))
        assertEquals("document.pdf", DocumentFiles.safeName("॥", extension = "pdf"))
    }
}
