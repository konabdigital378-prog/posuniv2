package com.example

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.supabase.DeliveryDriverProfile
import com.example.data.remote.supabase.DeliveryOrderRecord
import com.example.data.remote.supabase.DeliveryQrParser
import com.example.data.remote.supabase.DeliveryStatus
import com.example.data.remote.supabase.DeliveryStorage
import com.example.data.remote.supabase.PublicUserAccount
import com.example.util.DeliveryReceiptHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeliveryManagementTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_delivery_portal_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `delivery QR uri build and parse handles driver uri direct id and non-delivery barcodes`() {
        val driverId = "LIV-202601"
        val qrUri = DeliveryQrParser.buildDriverQrUri(driverId)
        assertEquals("konabpos://delivery?driverId=LIV-202601", qrUri)

        val parsedUri = DeliveryQrParser.parseDeliveryQrUri(qrUri)
        assertNotNull(parsedUri)
        assertEquals(driverId, parsedUri!!.driverId)

        val parsedWithDeliveryId = DeliveryQrParser.parseDeliveryQrUri(
            "konabpos://livraison?livreurId=LIV-9988&deliveryId=DLV-554"
        )
        assertNotNull(parsedWithDeliveryId)
        assertEquals("LIV-9988", parsedWithDeliveryId!!.driverId)
        assertEquals("DLV-554", parsedWithDeliveryId.deliveryId)

        val parsedDirect = DeliveryQrParser.parseDeliveryQrUri("LIV-4455")
        assertNotNull(parsedDirect)
        assertEquals("LIV-4455", parsedDirect!!.driverId)

        assertNull(DeliveryQrParser.parseDeliveryQrUri("konabpos://couture?s=12"))
        assertNull(DeliveryQrParser.parseDeliveryQrUri("konabpos://menu?shopId=10&table=T1"))
        assertNull(DeliveryQrParser.parseDeliveryQrUri("6901234567890"))
    }

    @Test
    fun `delivery driver 1000 FCFA plan activation and storage merge persist accurately`() {
        val now = System.currentTimeMillis()
        val uncertified = DeliveryDriverProfile(
            id = "LIV-100",
            fullName = "Moussa Traore",
            phone = "70112233",
            city = "Ouagadougou",
            neighborhood = "Pissy",
            vehicleType = "Moto",
            planPaid = false,
            updatedAt = now - 5000L
        )
        assertFalse(uncertified.isPlanActive)
        DeliveryStorage.upsertDriver(context, uncertified)

        val certified = uncertified.copy(
            planPaid = true,
            planAmountFcfa = 1000L,
            planPaymentRef = "SAS-1000-XYZ",
            planPaidAt = now,
            planExpiresAt = now + 30L * 24L * 60L * 60L * 1000L,
            updatedAt = now
        )
        assertTrue(certified.isPlanActive)

        val merged = DeliveryStorage.mergeDriversFromCloud(context, listOf(certified))
        assertEquals(1, merged.size)
        assertTrue(merged.first().isPlanActive)
        assertEquals(1000L, merged.first().planAmountFcfa)
        assertEquals("SAS-1000-XYZ", merged.first().planPaymentRef)

        val reloaded = DeliveryDriverProfile.fromJson(merged.first().toJson())
        assertEquals(certified.id, reloaded.id)
        assertEquals(certified.fullName, reloaded.fullName)
        assertTrue(reloaded.isPlanActive)
    }

    @Test
    fun `client deliveries matching QR validation price update and receipt formatting work end to end`() {
        val client = PublicUserAccount(
            id = "USR-CLIENT-01",
            fullName = "Awa Sawadogo",
            phone = "+226 76 55 44 33",
            city = "Ouagadougou",
            neighborhood = "Karpala"
        )
        val otherClientOrder = DeliveryOrderRecord(
            id = "DLV-OTHER",
            driverId = "LIV-200",
            driverName = "Issa Kaboré",
            clientUserId = "USR-OTHER",
            clientName = "Autre Client",
            clientPhone = "70000001",
            priceFcfa = 1500L,
            status = DeliveryStatus.EN_COURS
        )
        val ongoingByPhone = DeliveryOrderRecord(
            id = "DLV-101",
            driverId = "LIV-200",
            driverName = "Issa Kaboré",
            driverPhone = "70998877",
            clientUserId = "",
            clientName = client.fullName,
            clientPhone = "76554433",
            clientCity = client.city,
            clientNeighborhood = client.neighborhood,
            title = "Colis Pharmacie",
            pickupAddress = "Ouaga 2000",
            dropoffAddress = "Karpala Secteur 51",
            priceFcfa = 0L,
            status = DeliveryStatus.EN_COURS,
            updatedAt = 1000L
        )
        val oldValidatedById = DeliveryOrderRecord(
            id = "DLV-100",
            driverId = "LIV-200",
            driverName = "Issa Kaboré",
            clientUserId = client.id,
            clientName = client.fullName,
            clientPhone = client.phone,
            title = "Repas Midi",
            priceFcfa = 1000L,
            status = DeliveryStatus.VALIDEE,
            validatedAt = 900L,
            updatedAt = 950L
        )

        DeliveryStorage.upsertDelivery(context, otherClientOrder)
        DeliveryStorage.upsertDelivery(context, oldValidatedById)
        DeliveryStorage.upsertDelivery(context, ongoingByPhone)

        val matched = DeliveryStorage.findDeliveriesForClient(
            DeliveryStorage.getDeliveries(context),
            client
        )
        assertEquals(2, matched.size)
        assertEquals("DLV-101", matched[0].id)
        assertEquals("DLV-100", matched[1].id)

        // Simule le scan du QR du livreur LIV-200 par le client + saisie du prix par le livreur
        val qrParsed = DeliveryQrParser.parseDeliveryQrUri("konabpos://delivery?driverId=LIV-200")
        assertNotNull(qrParsed)
        assertEquals(ongoingByPhone.driverId, qrParsed!!.driverId)

        val validatedOrder = ongoingByPhone.copy(
            status = DeliveryStatus.VALIDEE,
            priceFcfa = 2500L,
            validatedAt = System.currentTimeMillis(),
            validatedQrRaw = "konabpos://delivery?driverId=LIV-200",
            updatedAt = System.currentTimeMillis()
        )
        DeliveryStorage.upsertDelivery(context, validatedOrder)

        val stored = DeliveryStorage.getDeliveries(context).first { it.id == "DLV-101" }
        assertEquals(DeliveryStatus.VALIDEE, stored.status)
        assertEquals(2500L, stored.priceFcfa)
        assertTrue(stored.validatedAt > 0L)

        val receiptText = stored.buildReceiptText()
        assertTrue(receiptText.contains("Issa Kaboré"))
        assertTrue(receiptText.contains("Awa Sawadogo"))
        assertTrue(receiptText.contains("76554433"))
        assertTrue(receiptText.contains("Karpala"))
        assertTrue(receiptText.contains("2 500") || receiptText.contains("2500"))
    }

    @Test
    fun `delivery receipt helper renders branded bitmap and exports pdf`() {
        val driver = DeliveryDriverProfile(
            id = "LIV-777",
            fullName = "Boubacar Diallo",
            phone = "78123456",
            city = "Bobo-Dioulasso",
            neighborhood = "Sarfalao",
            planPaid = true
        )
        val delivery = DeliveryOrderRecord(
            id = "DLV-777-01",
            driverId = driver.id,
            driverName = driver.fullName,
            driverPhone = driver.phone,
            clientUserId = "USR-90",
            clientName = "Mariam Ouédraogo",
            clientPhone = "71223344",
            clientCity = "Bobo-Dioulasso",
            clientNeighborhood = "Accart-Ville",
            title = "Colis Express Mode",
            pickupAddress = "Grand Marché",
            dropoffAddress = "Accart-Ville Rue 12",
            priceFcfa = 2000L,
            status = DeliveryStatus.VALIDEE,
            validatedAt = System.currentTimeMillis()
        )

        val bitmap = DeliveryReceiptHelper.buildDeliveryReceiptBitmap(context, delivery, driver)
        assertEquals(840, bitmap.width)
        assertEquals(1160, bitmap.height)

        var coloredPixels = 0
        for (x in 100 until 740 step 20) {
            for (y in 40 until 200 step 20) {
                if (bitmap.getPixel(x, y) != Color.WHITE) coloredPixels++
            }
        }
        assertTrue("Header gradient should render colored pixels", coloredPixels > 20)

        val pdfFile = DeliveryReceiptHelper.generateDeliveryReceiptPdf(context, delivery, driver)
        assertTrue(pdfFile.exists())
        assertTrue("Generated PDF receipt must be non-empty", pdfFile.length() > 1000L)
    }
}
