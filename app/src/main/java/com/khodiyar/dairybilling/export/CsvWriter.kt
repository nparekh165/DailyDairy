package com.khodiyar.dairybilling.export

import java.io.OutputStream

/** RFC 4180 CSV with a UTF-8 BOM so Excel opens Hindi names and ₹ correctly. */
object CsvWriter {
    fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value.startsWith(" ") || value.endsWith(" ")
        // Neutralise spreadsheet formula injection from user-entered text.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@" && value.toDoubleOrNull() == null) "'$value" else value
        return if (needsQuote || safe != value) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun toCsv(headers: List<String>, rows: List<List<String>>): String = buildString {
        append(headers.joinToString(",") { escape(it) }).append("\r\n")
        rows.forEach { append(it.joinToString(",") { c -> escape(c) }).append("\r\n") }
    }

    fun write(out: OutputStream, headers: List<String>, rows: List<List<String>>) {
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        out.write(toCsv(headers, rows).toByteArray(Charsets.UTF_8))
        out.flush()
    }
}
