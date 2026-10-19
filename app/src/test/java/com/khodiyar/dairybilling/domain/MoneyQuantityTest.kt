package com.khodiyar.dairybilling.domain

import com.khodiyar.dairybilling.domain.model.ProductUnit
import com.khodiyar.dairybilling.domain.money.Money
import com.khodiyar.dairybilling.domain.money.Quantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyQuantityTest {

    @Test fun lineAmount_wholeAndFractional() {
        assertEquals(12000, Money.lineAmount(2000, 6000)) // 2 L x ₹60 = ₹120
        assertEquals(9000, Money.lineAmount(1500, 6000)) // 1.5 L x ₹60 = ₹90
        assertEquals(1525, Money.lineAmount(250, 6100)) // 0.25 x ₹61 = ₹15.25
    }

    @Test fun lineAmount_roundsHalfUpToPaisa() {
        // 0.333 x ₹61.00 = ₹20.313 -> ₹20.31
        assertEquals(2031, Money.lineAmount(333, 6100))
        // 0.125 x ₹0.60 = 7.5 paise -> 8 paise (half up)
        assertEquals(8, Money.lineAmount(125, 60))
        // 0.005 x ₹1.00 = 0.5 paise -> 1 paisa
        assertEquals(1, Money.lineAmount(5, 100))
        // 0.004 x ₹1.00 = 0.4 paise -> 0
        assertEquals(0, Money.lineAmount(4, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lineAmount_rejectsNegativeQuantity() {
        Money.lineAmount(-1, 100)
    }

    @Test fun lineAmount_noFloatingPointDrift() {
        // Summing 0.1 L x ₹10 one thousand times must be exactly ₹1000.
        val total = (1..1000).sumOf { Money.lineAmount(100, 1000) }
        assertEquals(100_000, total)
    }

    @Test fun format_indianGrouping() {
        assertEquals("₹0.00", Money.format(0))
        assertEquals("₹999.50", Money.format(99950))
        assertEquals("₹1,000.00", Money.format(100000))
        assertEquals("₹12,34,567.89", Money.format(123456789))
        assertEquals("₹1,00,00,000.00", Money.format(1_000_000_000))
        assertEquals("-₹1,250.05", Money.format(-125005))
        assertEquals("₹60", Money.format(6000, withPaise = false))
        assertEquals("₹60.50", Money.format(6050, withPaise = false))
    }

    @Test fun compact_lakhsAndCrores() {
        assertEquals("₹12,345", Money.compact(1_234_500))
        assertEquals("₹1.2L", Money.compact(12_345_600))
        assertEquals("₹2.5Cr", Money.compact(2_500_000_000))
    }

    @Test fun parse_validAndInvalid() {
        assertEquals(6000L, Money.parseToPaise("60"))
        assertEquals(6050L, Money.parseToPaise("60.5"))
        assertEquals(125075L, Money.parseToPaise("1,250.75"))
        assertEquals(9900L, Money.parseToPaise("₹ 99"))
        assertEquals(-50000L, Money.parseToPaise("-500"))
        assertNull(Money.parseToPaise("12.345"))
        assertNull(Money.parseToPaise("abc"))
        assertNull(Money.parseToPaise(""))
        assertEquals("60.50", Money.toInput(6050))
        assertEquals("60", Money.toInput(6000))
        assertEquals("1234.56", Money.plain(123456))
        assertEquals("-0.05", Money.plain(-5))
    }

    @Test fun quantity_parseRespectsUnitPrecision() {
        assertEquals(Quantity.ParseResult.Ok(1500), Quantity.parse("1.5", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.Ok(250), Quantity.parse("0.25", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.Ok(500), Quantity.parse(".5", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.Ok(2000), Quantity.parse("2,0", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.TooPrecise(0), Quantity.parse("1.5", ProductUnit.PIECE))
        assertEquals(Quantity.ParseResult.Ok(3000), Quantity.parse("3.000", ProductUnit.PIECE))
        assertEquals(Quantity.ParseResult.TooPrecise(3), Quantity.parse("0.0005", ProductUnit.KILOGRAM))
        assertEquals(Quantity.ParseResult.Negative, Quantity.parse("-1", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.Invalid, Quantity.parse("1.2.3", ProductUnit.LITER))
        assertEquals(Quantity.ParseResult.Empty, Quantity.parse("  ", ProductUnit.LITER))
        assertTrue(Quantity.parse("999999", ProductUnit.LITER) is Quantity.ParseResult.TooLarge)
    }

    @Test fun quantity_formatStripsZeros() {
        assertEquals("1.5", Quantity.format(1500))
        assertEquals("2", Quantity.format(2000))
        assertEquals("0.25", Quantity.format(250))
        assertEquals("1.5 L", Quantity.formatWithUnit(1500, "L"))
    }
}
