package com.khodiyar.dairybilling.pdf

/*
 * A tiny, platform-independent page layout engine. Builders emit drawing operations onto pages
 * with automatic pagination; the Android renderer replays them onto android.graphics.pdf.PdfDocument.
 * Keeping layout free of Android types lets pagination/clipping be unit-tested on the JVM.
 */

enum class PdfFont { REGULAR, BOLD }

enum class Align { LEFT, RIGHT, CENTER }

data class PdfStyle(val size: Float, val font: PdfFont = PdfFont.REGULAR, val color: Long = PdfColors.TEXT, val letterSpacing: Float = 0f) {
    val lineHeight: Float get() = size * 1.38f
}

object PdfColors {
    const val TEXT = 0xFF17211DL
    const val MUTED = 0xFF5D6B65L
    const val BRAND = 0xFF0F5C4DL
    const val RULE = 0xFFD9DFDBL
    const val TINT = 0xFFF1F5F3L
    const val ACCENT = 0xFFB7791FL
}

object PdfStyles {
    val TITLE = PdfStyle(19f, PdfFont.BOLD, PdfColors.BRAND)
    val H1 = PdfStyle(13f, PdfFont.BOLD)
    val H2 = PdfStyle(11f, PdfFont.BOLD)
    val BODY = PdfStyle(9.5f)
    val BODY_BOLD = PdfStyle(9.5f, PdfFont.BOLD)
    val SMALL = PdfStyle(8f, color = PdfColors.MUTED)
    val LABEL = PdfStyle(7.5f, PdfFont.BOLD, PdfColors.MUTED, letterSpacing = 0.08f)
    val TOTAL = PdfStyle(15f, PdfFont.BOLD, PdfColors.BRAND)
    val WATERMARK = PdfStyle(10f, PdfFont.BOLD, PdfColors.ACCENT, letterSpacing = 0.1f)
}

sealed interface DrawOp {
    data class Text(val x: Float, val baseline: Float, val text: String, val style: PdfStyle, val align: Align = Align.LEFT) : DrawOp
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Long = PdfColors.RULE, val width: Float = 0.6f) : DrawOp
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val fill: Long?, val stroke: Long? = null, val radius: Float = 0f) : DrawOp
}

class PdfPage(val number: Int) {
    val ops = ArrayList<DrawOp>()
}

/** Measures text width in points for a style. Android uses Paint.measureText; tests use a fake. */
fun interface TextMeasurer {
    fun width(text: String, style: PdfStyle): Float
}

data class PageSpec(val width: Float = 595f, val height: Float = 842f, val margin: Float = 40f, val footerHeight: Float = 28f) {
    val contentWidth: Float get() = width - 2 * margin
    val bottomLimit: Float get() = height - margin - footerHeight
}

class PdfLayout(val spec: PageSpec, val measurer: TextMeasurer) {
    val pages = ArrayList<PdfPage>()
    var y: Float = spec.margin
    private var onNewPage: ((PdfLayout) -> Unit)? = null

    val page: PdfPage get() = pages.last()
    val left: Float get() = spec.margin
    val right: Float get() = spec.width - spec.margin

    init {
        pages += PdfPage(1)
    }

    /** Called at the top of every continuation page (e.g. to repeat a table header). */
    fun setContinuationHeader(block: ((PdfLayout) -> Unit)?) {
        onNewPage = block
    }

    fun newPage() {
        pages += PdfPage(pages.size + 1)
        y = spec.margin
        onNewPage?.invoke(this)
    }

    /** Starts a new page if [height] does not fit in the remaining space. */
    fun ensureSpace(height: Float) {
        if (y + height > spec.bottomLimit) newPage()
    }

    fun text(x: Float, baseline: Float, text: String, style: PdfStyle, align: Align = Align.LEFT) {
        page.ops += DrawOp.Text(x, baseline, text, style, align)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Long = PdfColors.RULE, width: Float = 0.6f) {
        page.ops += DrawOp.Line(x1, y1, x2, y2, color, width)
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, fill: Long?, stroke: Long? = null, radius: Float = 0f) {
        page.ops += DrawOp.Rect(l, t, r, b, fill, stroke, radius)
    }

    /** Word-wraps [text] to [maxWidth]; very long words are broken by characters. Never clips. */
    fun wrap(text: String, style: PdfStyle, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isBlank()) { out += ""; continue }
            var current = ""
            for (word in paragraph.trim().split(Regex("\\s+"))) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measurer.width(candidate, style) <= maxWidth) {
                    current = candidate
                    continue
                }
                if (current.isNotEmpty()) out += current
                if (measurer.width(word, style) <= maxWidth) {
                    current = word
                } else {
                    var chunk = ""
                    for (ch in word) {
                        if (measurer.width(chunk + ch, style) > maxWidth && chunk.isNotEmpty()) {
                            out += chunk
                            chunk = ""
                        }
                        chunk += ch
                    }
                    current = chunk
                }
            }
            if (current.isNotEmpty()) out += current
        }
        return out
    }

    /** Writes wrapped text in a block at the cursor, paginating line by line. Returns lines written. */
    fun paragraph(text: String, style: PdfStyle, x: Float = left, maxWidth: Float = spec.contentWidth, align: Align = Align.LEFT): Int {
        val lines = wrap(text, style, maxWidth)
        for (l in lines) {
            ensureSpace(style.lineHeight)
            val anchor = when (align) { Align.LEFT -> x; Align.RIGHT -> x + maxWidth; Align.CENTER -> x + maxWidth / 2 }
            text(anchor, y + style.size, l, style, align)
            y += style.lineHeight
        }
        return lines.size
    }

    /** Draws footers once the page count is known. */
    fun finish(footer: (pageNumber: Int, pageCount: Int) -> Pair<String, String>) {
        val count = pages.size
        for (p in pages) {
            val (l, r) = footer(p.number, count)
            val fy = spec.height - spec.margin
            p.ops += DrawOp.Line(left, fy - 14f, right, fy - 14f, PdfColors.RULE, 0.5f)
            p.ops += DrawOp.Text(left, fy, l, PdfStyles.SMALL, Align.LEFT)
            p.ops += DrawOp.Text(right, fy, r, PdfStyles.SMALL, Align.RIGHT)
        }
    }
}

/** A column for [TableWriter]: weight-based width and alignment. */
data class Column(val title: String, val weight: Float, val align: Align = Align.LEFT)

/**
 * Table with a repeated header on each page and wrapped cells. Rows never split across pages;
 * a row taller than a page is still placed on its own fresh page and wraps (it is not clipped).
 */
class TableWriter(private val layout: PdfLayout, private val columns: List<Column>, private val x: Float = layout.left, private val width: Float = layout.spec.contentWidth) {
    private val padding = 6f
    private val total = columns.sumOf { it.weight.toDouble() }.toFloat()
    private val widths = columns.map { width * it.weight / total }
    private val headerHeight = 22f

    fun header() {
        layout.ensureSpace(headerHeight + 24f)
        drawHeader()
        layout.setContinuationHeader { drawHeader() }
    }

    private fun drawHeader() {
        val top = layout.y
        layout.rect(x, top, x + width, top + headerHeight, PdfColors.TINT)
        var cx = x
        columns.forEachIndexed { i, c ->
            val anchor = when (c.align) { Align.LEFT -> cx + padding; Align.RIGHT -> cx + widths[i] - padding; Align.CENTER -> cx + widths[i] / 2 }
            layout.text(anchor, top + 14.5f, c.title.uppercase(), PdfStyles.LABEL, c.align)
            cx += widths[i]
        }
        layout.y = top + headerHeight
    }

    fun row(cells: List<String>, style: PdfStyle = PdfStyles.BODY, bold: Boolean = false, shaded: Boolean = false) {
        val s = if (bold) style.copy(font = PdfFont.BOLD) else style
        val wrapped = cells.mapIndexed { i, text -> layout.wrap(text, s, widths[i] - 2 * padding).ifEmpty { listOf("") } }
        val lines = wrapped.maxOf { it.size }
        val height = lines * s.lineHeight + 9f
        layout.ensureSpace(height)
        val top = layout.y
        if (shaded) layout.rect(x, top, x + width, top + height, PdfColors.TINT)
        var cx = x
        wrapped.forEachIndexed { i, cellLines ->
            val c = columns[i]
            cellLines.forEachIndexed { li, t ->
                val anchor = when (c.align) { Align.LEFT -> cx + padding; Align.RIGHT -> cx + widths[i] - padding; Align.CENTER -> cx + widths[i] / 2 }
                layout.text(anchor, top + 5f + s.size + li * s.lineHeight, t, s, c.align)
            }
            cx += widths[i]
        }
        layout.y = top + height
        layout.line(x, layout.y, x + width, layout.y)
    }

    fun end() {
        layout.setContinuationHeader(null)
    }
}
