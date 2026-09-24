package com.splitmypay.app

import com.splitmypay.app.data.parser.NotificationPaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPaymentParserTest {

    @Test
    fun testGoogleWalletBulletFormat() {
        val parsed = NotificationPaymentParser.parse(
            title = "Payment successful",
            text = "Mercadona • 18,45 €"
        )
        assertNotNull(parsed)
        assertEquals("Mercadona", parsed!!.merchant)
        assertEquals(18.45, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testGooglePlayServicesFormat() {
        val parsed = NotificationPaymentParser.parse(
            title = "Starbucks",
            text = "$4.50 with Google Pay"
        )
        assertNotNull(parsed)
        assertEquals("Starbucks", parsed!!.merchant)
        assertEquals(4.50, parsed.amount, 0.001)
        assertEquals("USD", parsed.currency)
    }

    @Test
    fun testRevolutPaidToFormat() {
        val parsed = NotificationPaymentParser.parse(
            title = "Revolut",
            text = "Paid €32.10 to Uber"
        )
        assertNotNull(parsed)
        assertEquals("Uber", parsed!!.merchant)
        assertEquals(32.10, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testSpanishBankFormat() {
        val parsed = NotificationPaymentParser.parse(
            title = "BBVA",
            text = "Pago de 24,50 EUR en RESTAURANTE EL RINCON"
        )
        assertNotNull(parsed)
        assertEquals("RESTAURANTE EL RINCON", parsed!!.merchant)
        assertEquals(24.50, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testN26Format() {
        val parsed = NotificationPaymentParser.parse(
            title = "N26",
            text = "Purchase of €45.20 at Fnac"
        )
        assertNotNull(parsed)
        assertEquals("Fnac", parsed!!.merchant)
        assertEquals(45.20, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testWiseGbpFormat() {
        val parsed = NotificationPaymentParser.parse(
            title = "Wise",
            text = "15.00 GBP at Pret A Manger"
        )
        assertNotNull(parsed)
        assertEquals("Pret A Manger", parsed!!.merchant)
        assertEquals(15.00, parsed.amount, 0.001)
        assertEquals("GBP", parsed.currency)
    }

    @Test
    fun testThousandsSeparatorCommaDecimal() {
        val parsed = NotificationPaymentParser.parse(
            title = "Google Wallet",
            text = "Apple Store • 1.299,00 €"
        )
        assertNotNull(parsed)
        assertEquals("Apple Store", parsed!!.merchant)
        assertEquals(1299.00, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testThousandsSeparatorDotDecimal() {
        val parsed = NotificationPaymentParser.parse(
            title = "Google Wallet",
            text = "Apple Store • 1,299.00 €"
        )
        assertNotNull(parsed)
        assertEquals("Apple Store", parsed!!.merchant)
        assertEquals(1299.00, parsed.amount, 0.001)
        assertEquals("EUR", parsed.currency)
    }

    @Test
    fun testNonPaymentNotificationIgnored() {
        val parsed = NotificationPaymentParser.parse(
            title = "Message from Mom",
            text = "See you at 5pm for dinner"
        )
        assertNull(parsed)
    }
}
