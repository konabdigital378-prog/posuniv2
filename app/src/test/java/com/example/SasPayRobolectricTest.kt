package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.saspay.SasPayApiClient
import com.example.data.remote.saspay.SasPayTransactionRecord
import com.example.data.remote.saspay.SasPayTransactionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SasPayRobolectricTest {

    @Test
    fun `parse countries and payment responses accurately`() {
        val apiClient = SasPayApiClient()
        val countriesJson = """
            {
              "results": [
                {
                  "code": "BF",
                  "name": "Burkina Faso",
                  "dial_code": "+226",
                  "currency": "XOF",
                  "networks": [
                    {"code": "orange_bf", "name": "Orange Money BF"},
                    {"code": "moov_bf", "name": "Moov Money BF"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsedCountries = apiClient.parseCountriesJson(countriesJson)
        assertEquals(1, parsedCountries.size)
        assertEquals("BF", parsedCountries[0].code)
        assertEquals(2, parsedCountries[0].networks.size)
        assertEquals("orange_bf", parsedCountries[0].networks[0].code)

        val softpayJson = """
            {
              "payment_id": "pay_bf_123",
              "reference": "REF-9988",
              "status": "pending",
              "amount": "12000.00",
              "message": "Confirmez sur votre téléphone"
            }
        """.trimIndent()
        val softpayResult = apiClient.parsePaymentResponse(softpayJson, 12000L)
        assertEquals("pay_bf_123", softpayResult.paymentId)
        assertEquals("REF-9988", softpayResult.reference)
        assertEquals("PENDING", softpayResult.status)
        assertFalse(softpayResult.isPaid)
        assertEquals(12000L, softpayResult.amountFcfa)

        val verifyPaidJson = """
            {
              "data": {
                "id": "cs_7788",
                "reference": "CS-REF-7788",
                "status": "succeeded",
                "checkout_url": "https://pay.saspay.me/session/cs_7788",
                "amount": 35000.0
              }
            }
        """.trimIndent()
        val verifyResult = apiClient.parsePaymentResponse(verifyPaidJson, 35000L)
        assertEquals("cs_7788", verifyResult.paymentId)
        assertEquals("CS-REF-7788", verifyResult.reference)
        assertEquals("SUCCEEDED", verifyResult.status)
        assertTrue(verifyResult.isPaid)
        assertEquals("https://pay.saspay.me/session/cs_7788", verifyResult.checkoutUrl)
        assertEquals(35000L, verifyResult.amountFcfa)
    }

    @Test
    fun `store and update saspay transaction history`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val record = SasPayTransactionRecord(
            id = "tx_test_001",
            paymentId = "pay_test_001",
            reference = "REF-TEST-001",
            purpose = "REABONNEMENT",
            tier = "Professionnel",
            durationDays = 365,
            amountFcfa = 12000L,
            channel = "Softpay (orange_bf)",
            phone = "+22665413799",
            status = "PENDING",
            verifiedAt = null
        )
        SasPayTransactionStore.saveTransaction(context, record)
        val initial = SasPayTransactionStore.getTransactions(context)
        assertTrue(initial.any { it.paymentId == "pay_test_001" && it.verifiedAt == null })

        SasPayTransactionStore.updateStatus(context, "pay_test_001", "SUCCEEDED", true)
        val updated = SasPayTransactionStore.getTransactions(context)
        assertTrue(updated.any { it.paymentId == "pay_test_001" && it.verifiedAt != null && it.status == "SUCCEEDED" })
    }
}
