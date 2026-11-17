package com.example

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.CoutureClientAccount
import com.example.data.remote.supabase.CoutureDirectoryHelper
import com.example.data.remote.supabase.CoutureMeasurements
import com.example.data.remote.supabase.CoutureModelRecord
import com.example.data.remote.supabase.CoutureOrderRecord
import com.example.data.remote.supabase.CoutureQrParser
import com.example.data.remote.supabase.CoutureStage
import com.example.data.remote.supabase.CoutureStorage
import com.example.data.remote.supabase.PublicCouturierListing
import com.example.util.BusinessTypePresets
import com.example.util.CoutureQrCardHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoutureManagementTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_couture_sync_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `main activity launches without exception`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun `couture QR uri build and parse handles catalog model and non-couture barcodes`() {
        val catalogUri = CoutureQrParser.buildCatalogQrUri(
            shopId = 88421L,
            shopName = "Atelier Faso Couture"
        )
        assertTrue(catalogUri.startsWith("konabpos://couture"))

        val parsedCatalog = CoutureQrParser.parseCoutureQrUri(catalogUri)
        assertNotNull(parsedCatalog)
        assertEquals(88421L, parsedCatalog!!.shopId)
        assertEquals("Atelier Faso Couture", parsedCatalog.shopName)
        assertNull(parsedCatalog.modelId)

        val modelUri = CoutureQrParser.buildCatalogQrUri(
            shopId = 88421L,
            shopName = "Atelier Faso Couture",
            modelId = "MOD-88421-2"
        )
        val parsedModel = CoutureQrParser.parseCoutureQrUri(modelUri)
        assertNotNull(parsedModel)
        assertEquals(88421L, parsedModel!!.shopId)
        assertEquals("MOD-88421-2", parsedModel.modelId)

        val compact = CoutureQrParser.parseCoutureQrUri("konabpos://couture?s=512&n=Chez%20Aicha&m=MOD-9")
        assertNotNull(compact)
        assertEquals(512L, compact!!.shopId)
        assertEquals("Chez Aicha", compact.shopName)
        assertEquals("MOD-9", compact.modelId)

        assertNull(CoutureQrParser.parseCoutureQrUri("konabpos://menu?shopId=10&table=T1"))
        assertNull(CoutureQrParser.parseCoutureQrUri("6901234567890"))
    }

    @Test
    fun `couture poster bitmap renders cleanly with catalog QR code`() {
        val poster = CoutureQrCardHelper.buildCatalogQrCardBitmap(
            shopName = "Atelier Elégance Bobo",
            shopId = 9001L,
            city = "Bobo-Dioulasso",
            phone = "70223344",
            modelsCount = 6,
            specificModelId = "MOD-9001-1",
            specificModelTitle = "Boubou Bazin Brodé"
        )
        assertEquals(900, poster.width)
        assertEquals(1240, poster.height)

        var darkPixels = 0
        for (x in 210 until 690 step 6) {
            for (y in 554 until 1034 step 6) {
                if (poster.getPixel(x, y) == Color.BLACK) darkPixels++
            }
        }
        assertTrue("Generated QR poster must contain dark QR modules", darkPixels > 50)

        val brandedPoster = CoutureQrCardHelper.buildCatalogQrCardBitmap(
            shopName = "Atelier Elégance Bobo",
            shopId = 9001L,
            city = "Bobo-Dioulasso",
            phone = "70223344",
            modelsCount = 6,
            context = context,
            logoUri = "drawable:img_prod_mode"
        )
        assertEquals(900, brandedPoster.width)
        assertEquals(1240, brandedPoster.height)
    }

    @Test
    fun `couture measurements orders stages and client tracking round trip accurately`() {
        val shopId = 7720L
        val defaults = CoutureStorage.ensureDefaultModelsForAtelier(context, shopId, "Atelier Sanga")
        assertTrue(defaults.size >= 4)

        val customModel = CoutureModelRecord(
            id = "MOD-$shopId-CUSTOM",
            shopId = shopId,
            shopName = "Atelier Sanga",
            title = "Ensemble Koko Dunda",
            category = "Tenues Cérémonie",
            price = 28000L,
            estimatedDays = 6,
            imageUri = "img_prod_mode"
        )
        val afterUpsert = CoutureStorage.upsertModel(context, shopId, customModel)
        assertTrue(afterUpsert.any { it.id == "MOD-$shopId-CUSTOM" })

        val measurements = CoutureMeasurements(
            bodyProfile = "Homme",
            shoulders = "46",
            chest = "102",
            waist = "88",
            sleeveLength = "63",
            topLength = "82",
            bottomLength = "104",
            extraNotes = "Col officier avec broderie dorée"
        )
        val parsedMeasurements = CoutureMeasurements.fromJson(measurements.toJson())
        assertEquals(measurements, parsedMeasurements)
        assertTrue(parsedMeasurements.nonEmptyEntries().size >= 6)

        val order = CoutureOrderRecord(
            id = "CTRO-7720-1",
            shopId = shopId,
            shopName = "Atelier Sanga",
            orderNumber = CoutureStorage.nextOrderNumber(context, shopId),
            customerFirstName = "Salif",
            customerLastName = "Kaboré",
            customerPhone = "+226 70 55 44 33",
            modelId = customModel.id,
            modelTitle = customModel.title,
            fabricPhotoUri = "file:///tmp/tissu_test.jpg",
            measurements = parsedMeasurements,
            status = CoutureStage.MESURES_PRISES,
            appointmentAt = System.currentTimeMillis() + 86_400_000L,
            totalPrice = 28000L,
            advancePaid = 10000L
        )
        assertEquals("CTR-001", order.orderNumber)
        assertEquals("Salif Kaboré", order.fullCustomerName)
        assertEquals(18000L, order.remainingBalance)

        CoutureStorage.upsertOrder(context, shopId, order)
        val loadedOrders = CoutureStorage.getOrders(context, shopId)
        assertEquals(1, loadedOrders.size)
        assertEquals(18000L, loadedOrders.first().remainingBalance)

        // Stage progression
        assertEquals(CoutureStage.COUPE_EN_COURS, CoutureStage.MESURES_PRISES.nextStage())
        assertEquals(CoutureStage.COUTURE_ASSEMBLAGE, CoutureStage.COUPE_EN_COURS.nextStage())
        assertEquals(CoutureStage.BRODERIE_FINITIONS, CoutureStage.COUTURE_ASSEMBLAGE.nextStage())
        assertEquals(CoutureStage.ESSAYAGE_PRET, CoutureStage.BRODERIE_FINITIONS.nextStage())
        assertEquals(CoutureStage.LIVRE_ENCAISSE, CoutureStage.ESSAYAGE_PRET.nextStage())
        assertNull(CoutureStage.LIVRE_ENCAISSE.nextStage())

        // Client account & phone matching tolerance (+226 prefix vs local 8 digits)
        val client = CoutureClientAccount(
            id = "CLI-70554433",
            firstName = "Salif",
            lastName = "Kaboré",
            phone = "70554433",
            city = "Ouagadougou"
        )
        CoutureStorage.saveClientAccount(context, client)
        assertEquals("Salif Kaboré", CoutureStorage.getClientAccount(context)?.fullName)
        assertTrue(CoutureDirectoryHelper.matchesClientOrder(order, "70 55 44 33", null))
        assertFalse(CoutureDirectoryHelper.matchesClientOrder(order, "76 99 88 77", "Autre"))
    }

    @Test
    fun `couture directory filters ateliers only and preset resolves correctly`() {
        assertTrue(CoutureDirectoryHelper.isCoutureShop("Couture"))
        assertTrue(CoutureDirectoryHelper.isCoutureShop("Atelier de Stylisme"))
        assertFalse(CoutureDirectoryHelper.isCoutureShop("Quincaillerie"))

        val preset = BusinessTypePresets.getPreset("Couture")
        assertEquals("Couture", preset.id)
        assertTrue(preset.isServiceSector)

        val localShop = ShopProfileEntity(
            id = 1,
            name = "Maison Bazini",
            businessType = "Couture",
            city = "Ouagadougou",
            isConfigured = true
        )
        val nonCoutureShop = ShopProfileEntity(
            id = 2,
            name = "Quincaillerie Centrale",
            businessType = "Quincaillerie",
            city = "Ouagadougou",
            isConfigured = true
        )
        val directory = CoutureDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = listOf(
                PublicCouturierListing(shopId = 99L, name = "Maison Bazini", city = "Ouagadougou", businessType = "Couture", modelsCount = 5)
            ),
            cachedShops = listOf(nonCoutureShop),
            localShop = localShop,
            localShopCloudId = 99L,
            modelCountsByShopId = mapOf(99L to 8)
        )
        assertEquals(1, directory.size)
        assertEquals("Maison Bazini", directory.first().name)
        assertEquals(8, directory.first().modelsCount)
    }
}
