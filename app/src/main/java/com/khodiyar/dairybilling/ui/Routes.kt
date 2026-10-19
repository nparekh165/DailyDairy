package com.khodiyar.dairybilling.ui

object Routes {
    const val DASHBOARD = "dashboard"
    const val DELIVERY = "delivery"
    const val DAILY_SUMMARY = "daily_summary/{date}"
    const val CUSTOMERS = "customers"
    const val CUSTOMER = "customer/{id}"
    const val CUSTOMER_EDIT = "customer_edit/{id}"
    const val PRODUCTS = "products"
    const val PRODUCT = "product/{id}"
    const val PRODUCT_EDIT = "product_edit/{id}"
    const val PAYMENTS = "payments"
    const val PAYMENT_NEW = "payment_new/{customerId}"
    const val BILLING = "billing"
    const val INVOICE = "invoice/{customerId}/{period}"
    const val DOCUMENT = "document/{kind}/{a}/{b}/{c}"
    const val REPORTS = "reports"
    const val BACKUP = "backup"
    const val STAFF = "staff"
    const val SETTINGS = "settings"
    const val AUDIT = "audit"
    const val MORE = "more"

    fun dailySummary(epochDay: Long) = "daily_summary/$epochDay"
    fun customer(id: Long) = "customer/$id"
    fun customerEdit(id: Long = 0) = "customer_edit/$id"
    fun product(id: Long) = "product/$id"
    fun productEdit(id: Long = 0) = "product_edit/$id"
    fun paymentNew(customerId: Long = 0) = "payment_new/$customerId"
    fun invoice(customerId: Long, period: Int) = "invoice/$customerId/$period"

    /** Invoice PDF: a=customerId, b=periodKey, c=invoiceId (0 = current). */
    fun invoicePdf(customerId: Long, period: Int, invoiceId: Long = 0) = "document/invoice/$customerId/$period/$invoiceId"
    /** Payment receipt PDF: a=paymentId. */
    fun receiptPdf(paymentId: Long) = "document/receipt/$paymentId/0/0"

    val TOP_LEVEL = setOf(DASHBOARD, DELIVERY, CUSTOMERS, BILLING, MORE)
}
