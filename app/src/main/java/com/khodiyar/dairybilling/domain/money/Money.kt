package com.khodiyar.dairybilling.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money is held as integer paise (1 rupee = 100 paise) in a Long. No floating point is used for
 * money anywhere in the app. Rounding rule: a delivery line amount (quantity x rate) is rounded
 * HALF_UP to the nearest paisa once, when the line is computed; totals are exact sums of lines.
 */
object Money {

    /** quantityMilli is quantity x 1000; ratePaise is paise per one unit. Result rounded HALF_UP. */
    fun lineAmount(quantityMilli: Long, ratePaise: Long): Long {
        require(quantityMilli >= 0) { "Quantity cannot be negative" }
        require(ratePaise >= 0) { "Rate cannot be negative" }
        return BigDecimal.valueOf(quantityMilli)
            .multiply(BigDecimal.valueOf(ratePaise))
            .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /**
     * Formats paise using the Indian digit grouping: 12,34,567.50.
     * @param symbol currency symbol prefix (default ₹)
     * @param withPaise when false and the amount is a whole number of rupees, decimals are dropped.
     */
    fun format(paise: Long, symbol: String = "₹", withPaise: Boolean = true): String {
        val negative = paise < 0
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = abs / 100
        val fraction = abs % 100
        val grouped = groupIndian(rupees.toString())
        val decimals = if (withPaise || fraction != 0L) "." + fraction.toString().padStart(2, '0') else ""
        return (if (negative) "-" else "") + symbol + grouped + decimals
    }

    /** Plain decimal string without symbol or grouping, e.g. "1234.50" (for CSV). */
    fun plain(paise: Long): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        return (if (negative) "-" else "") + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }

    /** Compact dashboard style: ₹1.2L, ₹3.4Cr, ₹12,345. */
    fun compact(paise: Long, symbol: String = "₹"): String {
        val rupees = paise / 100
        val abs = kotlin.math.abs(rupees)
        val sign = if (rupees < 0) "-" else ""
        return when {
            abs >= 1_00_00_000 -> sign + symbol + oneDecimal(abs, 1_00_00_000) + "Cr"
            abs >= 1_00_000 -> sign + symbol + oneDecimal(abs, 1_00_000) + "L"
            else -> sign + symbol + groupIndian(abs.toString())
        }
    }

    private fun oneDecimal(value: Long, unit: Long): String {
        val bd = BigDecimal.valueOf(value).divide(BigDecimal.valueOf(unit), 1, RoundingMode.HALF_UP)
        return bd.stripTrailingZeros().toPlainString()
    }

    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /**
     * Parses user input like "60", "60.5", "1,250.75", "₹ 99" into paise.
     * Returns null for invalid input or more than 2 decimal places.
     */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("₹", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!Regex("^-?\\d{1,12}(\\.\\d{0,2})?$").matches(cleaned)) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Editable representation for text fields: 6050 -> "60.50", 6000 -> "60". */
    fun toInput(paise: Long): String {
        val abs = kotlin.math.abs(paise)
        val base = (abs / 100).toString()
        val frac = abs % 100
        val body = if (frac == 0L) base else base + "." + frac.toString().padStart(2, '0')
        return if (paise < 0) "-$body" else body
    }
}
