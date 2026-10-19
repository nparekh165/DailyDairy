package com.khodiyar.dairybilling.domain.money

import com.khodiyar.dairybilling.domain.model.ProductUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** Quantities are Long "milli-units" (x1000). Never stored as Double. */
object Quantity {
    const val SCALE = 1000L

    sealed interface ParseResult {
        data class Ok(val milli: Long) : ParseResult
        data object Empty : ParseResult
        data object Invalid : ParseResult
        data object Negative : ParseResult
        data class TooPrecise(val maxDecimals: Int) : ParseResult
        data object TooLarge : ParseResult
    }

    /** Upper bound per entry; protects against typos such as 2000 instead of 2.000. */
    const val MAX_MILLI = 100_000_000L // 100,000 units

    fun parse(input: String, unit: ProductUnit): ParseResult {
        val s = input.trim().replace(",", ".")
        if (s.isEmpty()) return ParseResult.Empty
        if (s.startsWith("-")) return ParseResult.Negative
        if (!Regex("^\\d{0,9}(\\.\\d*)?$").matches(s) || s == ".") return ParseResult.Invalid
        val bd = try { BigDecimal(s) } catch (e: NumberFormatException) { return ParseResult.Invalid }
        val stripped = bd.stripTrailingZeros()
        if (stripped.scale() > unit.maxDecimals) return ParseResult.TooPrecise(unit.maxDecimals)
        val milli = bd.movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        if (milli > MAX_MILLI) return ParseResult.TooLarge
        return ParseResult.Ok(milli)
    }

    /** 1500 -> "1.5", 2000 -> "2", 250 -> "0.25". */
    fun format(milli: Long): String =
        BigDecimal.valueOf(milli).movePointLeft(3).stripTrailingZeros().toPlainString()

    fun formatWithUnit(milli: Long, unitLabel: String): String =
        if (unitLabel.isBlank()) format(milli) else format(milli) + " " + unitLabel
}
