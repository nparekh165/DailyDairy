package com.khodiyar.dairybilling

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khodiyar.dairybilling.domain.billing.BalanceSummary
import com.khodiyar.dairybilling.domain.billing.InvoiceLine
import com.khodiyar.dairybilling.pdf.AndroidPdfWriter
import com.khodiyar.dairybilling.pdf.BusinessInfo
import com.khodiyar.dairybilling.pdf.DocumentFiles
import com.khodiyar.dairybilling.pdf.InvoiceDocument
import com.khodiyar.dairybilling.pdf.InvoicePdfBuilder
import com.khodiyar.dairybilling.pdf.PdfLabels
import com.khodiyar.dairybilling.share.ShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device/emulator: real android.graphics.pdf output, PdfRenderer read-back and FileProvider. */
@RunWith(AndroidJUnit4::class)
class PdfShareInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun doc(lines: Int) = InvoiceDocument(
        BusinessInfo("Khodiyar Dairy", "Main Road", "9876543210", "", "Thank you", "Pay by 10th", "dairy@upi", "₹"),
        "KD-2026-10-0001", null, "01 Nov 2026", "October 2026", "रमेश पटेल (Ramesh Patel)", "C-0001", "A-001", "9876543210", "Flat 1",
        (1..lines).map { InvoiceLine(it.toLong(), "दूध Milk $it", "L", 6000, 30_000, 180_000, 30) },
        BalanceSummary(0, lines * 180_000L, 0, 0),
    )

    @Test fun pdfIsGeneratedAndOpens() {
        val file = DocumentFiles.newFile(context, "test_invoice.pdf")
        AndroidPdfWriter.write(InvoicePdfBuilder.layout(doc(3), PdfLabels(), AndroidPdfWriter.measurer), file)
        assertTrue(file.length() > 1000)
        assertEquals(1, AndroidPdfWriter.verify(file))
        val pages = AndroidPdfWriter.renderPages(file, 600)
        assertEquals(1, pages.size)
        assertEquals(600, pages[0].width)
    }

    @Test fun longInvoiceHasMultiplePages() {
        val file = DocumentFiles.newFile(context, "test_long.pdf")
        val layout = InvoicePdfBuilder.layout(doc(90), PdfLabels(), AndroidPdfWriter.measurer)
        AndroidPdfWriter.write(layout, file)
        assertTrue(layout.pages.size > 1)
        assertEquals(layout.pages.size, AndroidPdfWriter.verify(file))
    }

    @Test fun fileProviderUriAndShareIntent() {
        val file = DocumentFiles.newFile(context, "share.pdf")
        AndroidPdfWriter.write(InvoicePdfBuilder.layout(doc(1), PdfLabels(), AndroidPdfWriter.measurer), file)
        val uri = ShareHelper.uriFor(context, file)
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".fileprovider", uri.authority)
        assertFalse("private path must not leak", uri.toString().contains(context.cacheDir.absolutePath))
        context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
        assertEquals("application/pdf", context.contentResolver.getType(uri))
        val intent = ShareHelper.buildSendIntent(context, file, "application/pdf", "Bill", "Bill")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
    }

    @Test fun shareHandlesMissingOrPresentTargetsGracefully() {
        val file = DocumentFiles.newFile(context, "share2.pdf")
        AndroidPdfWriter.write(InvoicePdfBuilder.layout(doc(1), PdfLabels(), AndroidPdfWriter.measurer), file)
        val outcome = ShareHelper.share(context, file, "application/pdf", "Bill", "Share")
        if (ShareHelper.hasShareTargets(context, "application/pdf")) assertEquals(ShareHelper.Outcome.OPENED, outcome)
        else assertEquals(ShareHelper.Outcome.NO_APP, outcome)
        if (ShareHelper.installedWhatsApp(context) == null) {
            assertEquals(ShareHelper.Outcome.NO_APP, ShareHelper.shareToWhatsApp(context, file, "Bill"))
            assertEquals(ShareHelper.Outcome.NO_APP, ShareHelper.openWhatsAppChat(context, "9876543210", "Bill"))
        }
    }
}
