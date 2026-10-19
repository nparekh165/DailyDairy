package com.khodiyar.dairybilling.domain.model

/**
 * Units a product can be sold in. Quantities are stored as "milli-units" (value x 1000) in a Long,
 * so 1.5 L is stored as 1500. [maxDecimals] is the precision accepted at entry time for each unit.
 * Each product has exactly one unit, so incompatible units are never summed together.
 */
enum class ProductUnit(val symbol: String, val maxDecimals: Int, val defaultStepMilli: Long) {
    LITER("L", 3, 500),
    MILLILITER("ml", 0, 250_000),
    KILOGRAM("kg", 3, 250),
    GRAM("g", 0, 100_000),
    PIECE("pc", 0, 1000),
    PACKET("pkt", 0, 1000),
    OTHER("", 3, 1000);
}

enum class UserRole { ADMIN, STAFF }

enum class DeliverySession { MORNING, EVENING }

/**
 * DELIVERED: quantity > 0 delivered and billed.
 * NO_DELIVERY: customer did not need delivery that day (holiday, away). Not billed.
 * MISSED: the dairy failed to deliver. Not billed, tracked separately for follow-up.
 * A day with no row at all means "not yet entered" (pending).
 */
enum class DeliveryStatus { DELIVERED, NO_DELIVERY, MISSED }

enum class PaymentMethod { CASH, UPI, BANK_TRANSFER, OTHER }

enum class RecordStatus { ACTIVE, REVERSED }

/** DEBIT increases what the customer owes, CREDIT reduces it (discount, goodwill, correction). */
enum class AdjustmentType { DEBIT, CREDIT }

enum class InvoiceStatus { FINALIZED, SUPERSEDED }
