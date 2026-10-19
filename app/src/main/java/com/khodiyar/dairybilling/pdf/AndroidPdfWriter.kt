package com.khodiyar.dairybilling.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Replays [PdfLayout] operations onto Android's offline PdfDocument and verifies the result. */
object AndroidPdfWriter {

    private val regular: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private fun paintFor(style: PdfStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = style.size
        typeface = if (style.font == PdfFont.BOLD) bold else regular
        color = style.color.toInt()
        letterSpacing = style.letterSpacing
        isSubpixelText = true
    }

    val measurer = TextMeasurer { text, style -> paintFor(style).measureText(text) }

    fun write(layout: PdfLayout, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { write(layout, it) }
    }

    fun write(layout: PdfLayout, out: java.io.OutputStream) {
        val doc = PdfDocument()
        try {
            val spec = layout.spec
            for (p in layout.pages) {
                val info = PdfDocument.PageInfo.Builder(spec.width.toInt(), spec.height.toInt(), p.number).create()
                val page = doc.startPage(info)
                val c = page.canvas
                for (op in p.ops) when (op) {
                    is DrawOp.Rect -> {
                        val r = RectF(op.left, op.top, op.right, op.bottom)
                        op.fill?.let { c.drawRoundRect(r, op.radius, op.radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it.toInt(); style = Paint.Style.FILL }) }
                        op.stroke?.let { c.drawRoundRect(r, op.radius, op.radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.6f }) }
                    }
                    is DrawOp.Line -> c.drawLine(op.x1, op.y1, op.x2, op.y2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = op.color.toInt(); strokeWidth = op.width })
                    is DrawOp.Text -> {
                        val paint = paintFor(op.style).apply {
                            textAlign = when (op.align) { Align.LEFT -> Paint.Align.LEFT; Align.RIGHT -> Paint.Align.RIGHT; Align.CENTER -> Paint.Align.CENTER }
                        }
                        c.drawText(op.text, op.x, op.baseline, paint)
                    }
                }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Opens the generated file with the platform renderer; returns the page count (0 = unreadable). */
    fun verify(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    } catch (e: Exception) {
        0
    }

    /** Renders pages to bitmaps for the in-app preview (exactly what will be shared/printed). */
    fun renderPages(file: File, targetWidthPx: Int, maxPages: Int = 20): List<Bitmap> {
        val out = ArrayList<Bitmap>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                    renderer.openPage(i).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val bmp = Bitmap.createBitmap(targetWidthPx, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out += bmp
                    }
                }
            }
        }
        return out
    }
}

/** Generated documents live under cache/shared so FileProvider exposes nothing else. */
object DocumentFiles {
    fun sharedDir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

    /** Filesystem-safe name, e.g. Khodiyar_Dairy_A002_2026-10.pdf */
    fun safeName(vararg parts: String, extension: String): String {
        val body = parts.filter { it.isNotBlank() }.joinToString("_") { part ->
            part.trim().replace(Regex("[^A-Za-z0-9-]+"), "_").trim('_')
        }.replace(Regex("_+"), "_").ifEmpty { "document" }.take(80)
        return "$body.$extension"
    }

    fun newFile(context: Context, name: String): File = File(sharedDir(context), name).also { if (it.exists()) it.delete() }

    /** Removes old generated files so the cache does not grow without bound. */
    fun cleanup(context: Context, olderThanMillis: Long = 7L * 24 * 3600 * 1000) {
        val cutoff = System.currentTimeMillis() - olderThanMillis
        sharedDir(context).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }
}
