package com.khodiyar.dairybilling.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Business dates (delivery date, payment date, adjustment date) are stored as epoch days (Long),
 * independent of time zone. Audit/creation timestamps are epoch milliseconds.
 * Billing periods are calendar months keyed as yyyymm (e.g. 202610).
 */
object Dates {
    fun periodKey(ym: YearMonth): Int = ym.year * 100 + ym.monthValue
    fun fromPeriodKey(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)
    fun periodStart(ym: YearMonth): Long = ym.atDay(1).toEpochDay()
    fun periodEnd(ym: YearMonth): Long = ym.atEndOfMonth().toEpochDay()
    fun periodOf(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))

    private val day = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val dayShort = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val monthShort = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(day)
    fun formatDay(date: LocalDate): String = date.format(day)
    fun formatDayShort(date: LocalDate): String = date.format(dayShort)
    fun formatMonth(ym: YearMonth): String = ym.format(month)
    fun formatMonthShort(ym: YearMonth): String = ym.format(monthShort)
    fun formatIso(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(iso)
    fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)
}

/** Injectable clock so business logic and tests agree on "today". */
interface TimeProvider {
    fun nowMillis(): Long
    fun today(): LocalDate
}

object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
}
