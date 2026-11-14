package com.example

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.PublicRestaurantListing
import com.example.data.remote.supabase.RestaurantDirectoryHelper
import com.example.data.remote.supabase.RestaurantKitchenHelper
import com.example.data.remote.supabase.RestaurantOrderItem
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.RestaurantQrParser
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.data.remote.supabase.RestaurantStorage
import com.example.data.remote.supabase.RestaurantTableRecord
import com.example.data.remote.supabase.SupabaseConfig
import com.example.util.QrCodeHelper
import com.example.util.RestaurantQrCardHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestaurantManagementTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_restaurant_sync_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `table QR uri build and parse handles standard compact and web formats`() {
        val uri = RestaurantQrParser.buildTableQrUri(
            shopId = 42519L,
            shopName = "Le Maquis D'Or",
            tableName = "Table VIP 2"
        )
        assertTrue(uri.startsWith("konabpos://menu"))

        val parsed = RestaurantQrParser.parseTableQrUri(uri)
        assertNotNull(parsed)
        assertEquals(42519L, parsed!!.shopId)
        assertEquals("Le Maquis D'Or", parsed.shopName)
        assertEquals("Table VIP 2", parsed.tableName)

        // Compact QR query format
        val compact = RestaurantQrParser.parseTableQrUri("konabpos://menu?s=102&n=Chez%20Awa&t=Terrasse%203")
        assertNotNull(compact)
        assertEquals(102L, compact!!.shopId)
        assertEquals("Chez Awa", compact.shopName)
        assertEquals("Terrasse 3", compact.tableName)

        // Web menu URL format
        val web = RestaurantQrParser.parseTableQrUri("https://konabpos.bf/app?menu=1&shopId=77&shop=Bistro%20Ouaga&table=T-5")
        assertNotNull(web)
        assertEquals(77L, web!!.shopId)
        assertEquals("Bistro Ouaga", web.shopName)
        assertEquals("T-5", web.tableName)

        // Non-restaurant barcodes must return null
        assertNull(RestaurantQrParser.parseTableQrUri("6901234567890"))
    }

    @Test
    fun `qr code encoder encodes long restaurant table uris cleanly and renders poster bitmap`() {
        val longUri = RestaurantQrParser.buildTableQrUri(
            shopId = 987654L,
            shopName = "Restaurant Gastronomique Faso",
            tableName = "Table Terrasse 12"
        )
        val qrBitmap = QrCodeHelper.generateQrBitmap(longUri, sizePx = 280, margin = 2)
        assertEquals(280, qrBitmap.width)
        assertEquals(280, qrBitmap.height)

        var darkPixels = 0
        var lightPixels = 0
        for (x in 0 until qrBitmap.width step 4) {
            for (y in 0 until qrBitmap.height step 4) {
                if (qrBitmap.getPixel(x, y) == Color.BLACK) darkPixels++ else lightPixels++
            }
        }
        assertTrue("QR bitmap must contain black finder/data modules", darkPixels > 100)
        assertTrue("QR bitmap must contain white spaces", lightPixels > 100)

        val table = RestaurantTableRecord(
            id = "TBL-1",
            shopId = 987654L,
            tableNumber = "Table Terrasse 12",
            zone = "Terrasse",
            seats = 6
        )
        val poster = RestaurantQrCardHelper.buildTableQrCardBitmap(
            shopName = "Restaurant Gastronomique Faso",
            table = table,
            city = "Ouagadougou",
            phone = "70112233"
        )
        assertEquals(900, poster.width)
        assertEquals(1240, poster.height)
    }

    @Test
    fun `restaurant storage creates custom team pins batch tables and resolves multi-user LWW sync`() {
        val shopId = 5501L
        val staff = RestaurantStorage.ensureDefaultRestaurantTeam(
            context = context,
            shopId = shopId,
            adminName = "Moussa Gérant",
            adminPhone = "70001122",
            adminPin = "1234",
            kitchenName = "Chef Bouba",
            kitchenPin = "2468",
            waiterName = "Serveuse Aminata",
            waiterPin = "1357",
            cashierName = "Caissier Paul",
            cashierPin = "8888"
        )
        assertEquals(4, staff.size)

        val foundKitchen = RestaurantStorage.findStaffByPin(context, shopId, "2468")
        assertNotNull(foundKitchen)
        assertEquals(RestaurantStaffRole.KITCHEN, foundKitchen!!.role)
        assertEquals("Chef Bouba", foundKitchen.name)

        val foundWaiter = RestaurantStorage.findStaffByPin(context, shopId, "1357")
        assertNotNull(foundWaiter)
        assertEquals(RestaurantStaffRole.WAITER, foundWaiter!!.role)

        // Batch tables
        val initialTables = RestaurantStorage.ensureDefaultTables(context, shopId, count = 4)
        assertEquals(4, initialTables.size)
        val expandedTables = RestaurantStorage.generateBatchTables(context, shopId, countToAdd = 4, zone = "Terrasse")
        assertEquals(8, expandedTables.size)

        // Order lifecycle & LWW cloud merge
        val t1 = 1000L
        val t2 = 2000L
        val localOrder = RestaurantOrderRecord(
            id = "ORD-1",
            shopId = shopId,
            orderNumber = "CMD-001",
            tableName = "Table 2",
            customerName = "Client Salif",
            source = "QR_TABLE",
            status = RestaurantOrderStatus.PENDING_MANAGER,
            items = listOf(RestaurantOrderItem(10L, "Poulet Bicyclette", 4500L, 2, "Bien pimenté")),
            updatedAt = t1
        )
        RestaurantStorage.upsertOrder(context, shopId, localOrder)

        val cloudUpdated = localOrder.copy(
            status = RestaurantOrderStatus.SENT_TO_KITCHEN,
            assignedToKitchenBy = "Moussa Gérant",
            updatedAt = t2
        )
        val merged = RestaurantStorage.mergeOrdersFromCloud(context, shopId, listOf(cloudUpdated))
        assertEquals(1, merged.size)
        assertEquals(RestaurantOrderStatus.SENT_TO_KITCHEN, merged[0].status)
        assertEquals("Moussa Gérant", merged[0].assignedToKitchenBy)

        // Stale older cloud update must NOT overwrite newer local PAID state
        val localPaid = cloudUpdated.copy(
            status = RestaurantOrderStatus.PAID,
            saleId = 99L,
            updatedAt = 3000L
        )
        RestaurantStorage.upsertOrder(context, shopId, localPaid)
        val mergedAfterStale = RestaurantStorage.mergeOrdersFromCloud(context, shopId, listOf(cloudUpdated))
        assertEquals(RestaurantOrderStatus.PAID, mergedAfterStale[0].status)
        assertEquals(99L, mergedAfterStale[0].saleId)

        // Merge cloud staff account from another device
        val extraChef = RestaurantStaffAccount(
            id = "STAFF-KIT-EXTRA",
            shopId = shopId,
            name = "Second Grill",
            role = RestaurantStaffRole.KITCHEN,
            pin = "4321",
            updatedAt = 4000L
        )
        val mergedStaff = RestaurantStorage.mergeStaffFromCloud(context, shopId, listOf(extraChef))
        assertEquals(5, mergedStaff.size)
        assertNotNull(RestaurantStorage.findStaffByPin(context, shopId, "4321"))
    }

    @Test
    fun `supabase schema includes all 4 synchronized restaurant tables and policies`() {
        val sql = SupabaseConfig.generateSqlSchema()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.restaurant_staff"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.restaurant_tables"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.restaurant_orders"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS public.restaurant_menu_day"))
        assertTrue(sql.contains("Allow anon read/write for restaurant_staff"))
        assertTrue(sql.contains("Allow anon read/write for restaurant_tables"))
        assertTrue(sql.contains("Allow anon read/write for restaurant_orders"))
        assertTrue(sql.contains("Allow anon read/write for restaurant_menu_day"))
    }

    @Test
    fun `kitchen helper filters real-time QR table orders FIFO and transitions to ready to serve`() {
        val baseTime = 1_700_000_000_000L
        val qrOrderNew = RestaurantOrderRecord(
            id = "ORD-QR-1",
            shopId = 77L,
            orderNumber = "CMD-010",
            tableName = "Table 3",
            customerName = "Awa",
            source = "QR_TABLE",
            status = RestaurantOrderStatus.PENDING_MANAGER,
            items = listOf(RestaurantOrderItem(1L, "Riz Gras", 2500L, 2, "Sans piment")),
            createdAt = baseTime + 120_000L,
            updatedAt = baseTime + 120_000L
        )
        val qrOrderOldest = RestaurantOrderRecord(
            id = "ORD-QR-0",
            shopId = 77L,
            orderNumber = "CMD-009",
            tableName = "Table 1",
            customerName = "Issa",
            source = "QR_TABLE",
            status = RestaurantOrderStatus.IN_PREPARATION,
            items = listOf(RestaurantOrderItem(2L, "Poulet Braisé", 4000L, 1)),
            createdAt = baseTime,
            updatedAt = baseTime + 60_000L
        )
        val posOrder = RestaurantOrderRecord(
            id = "ORD-POS-1",
            shopId = 77L,
            orderNumber = "CMD-011",
            tableName = "Table 5",
            customerName = "Client Caisse",
            source = "POS",
            status = RestaurantOrderStatus.SENT_TO_KITCHEN,
            items = listOf(RestaurantOrderItem(3L, "Bissap", 500L, 3)),
            createdAt = baseTime + 30_000L,
            updatedAt = baseTime + 30_000L
        )
        val paidQrOrder = qrOrderNew.copy(
            id = "ORD-QR-PAID",
            orderNumber = "CMD-008",
            status = RestaurantOrderStatus.PAID
        )

        val allOrders = listOf(qrOrderNew, qrOrderOldest, posOrder, paidQrOrder)

        // Kitchen QR filter should return only active QR_TABLE orders sorted FIFO (oldest first)
        val activeQr = RestaurantKitchenHelper.filterActiveQrOrders(allOrders)
        assertEquals(listOf("CMD-009", "CMD-010"), activeQr.map { it.orderNumber })

        // All active kitchen orders should include POS order too
        val allActive = RestaurantKitchenHelper.filterAllActiveKitchenOrders(allOrders)
        assertEquals(listOf("CMD-009", "CMD-011", "CMD-010"), allActive.map { it.orderNumber })

        // Tapping "Prêt à servir" moves order to READY -> leaves active queue & enters ready queue
        val readyOrder = qrOrderNew.copy(status = RestaurantOrderStatus.READY, updatedAt = baseTime + 240_000L)
        val updatedOrders = listOf(readyOrder, qrOrderOldest, posOrder, paidQrOrder)

        val remainingQr = RestaurantKitchenHelper.filterActiveQrOrders(updatedOrders)
        assertEquals(listOf("CMD-009"), remainingQr.map { it.orderNumber })

        val readyList = RestaurantKitchenHelper.filterReadyToServeOrders(updatedOrders)
        assertEquals(listOf("CMD-010"), readyList.map { it.orderNumber })

        assertEquals("2x Riz Gras", RestaurantKitchenHelper.formatItemsSummary(qrOrderNew))
        assertEquals("3 min", RestaurantKitchenHelper.formatElapsedLabel(baseTime, baseTime + 180_000L))
        assertEquals("À l'instant", RestaurantKitchenHelper.formatElapsedLabel(baseTime, baseTime + 20_000L))
    }

    @Test
    fun `konabViewModel initializes all StateFlows before startPeriodicSync runs on worker thread`() {
        var uncaughtError: Throwable? = null
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaughtError = e }
        try {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            val vm = com.example.ui.viewmodel.KonabViewModel(app)
            Thread.sleep(350L)
            assertNull("KonabViewModel background sync must not throw NPE", uncaughtError)
            assertNotNull(vm.shopProfile)
            assertNotNull(vm.currentScreen.value)
            assertNotNull(vm.restaurantOrders.value)
            assertNotNull(vm.supabaseAuthState.value)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `public restaurant directory filters only restaurants preserves 64-bit ids deduplicates and formats web slugs`() {
        assertTrue(RestaurantDirectoryHelper.isRestaurantShop("Restaurant"))
        assertTrue(RestaurantDirectoryHelper.isRestaurantShop("Maquis & Grill"))
        assertTrue(RestaurantDirectoryHelper.isRestaurantShop("Café Bistro"))
        assertFalse(RestaurantDirectoryHelper.isRestaurantShop("Quincaillerie"))
        assertFalse(RestaurantDirectoryHelper.isRestaurantShop("Boutique Mode"))

        val bigShopId = 1_731_590_400_123L // exceeds Int.MAX_VALUE
        val remote = listOf(
            PublicRestaurantListing(
                shopId = bigShopId,
                name = "Chez Tantou Grill",
                slogan = "Poulet braisé & alloco",
                city = "Ouagadougou",
                logoUri = "drawable:img_prod_resto",
                businessType = "Restaurant"
            ),
            PublicRestaurantListing(
                shopId = 1_731_590_500_000L,
                name = "Quincaillerie du Centre",
                city = "Bobo-Dioulasso",
                businessType = "Quincaillerie"
            )
        )
        val cachedDup = ShopProfileEntity(
            id = 1,
            name = "chez tantou grill",
            city = "Ouagadougou",
            businessType = "Restaurant",
            isConfigured = true
        )
        val localMaquis = ShopProfileEntity(
            id = 1,
            name = "Le Maquis Étoile",
            slogan = "Spécialités locales",
            city = "Koudougou",
            businessType = "Maquis & Bar",
            isConfigured = true
        )

        val allMerged = RestaurantDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = remote,
            cachedShops = listOf(cachedDup),
            localShop = localMaquis,
            localShopCloudId = 9_999_888_777L
        )
        assertEquals(2, allMerged.size)
        val tantou = allMerged.first { it.name.contains("Tantou") }
        assertEquals(bigShopId, tantou.shopId)
        assertEquals("chez-tantou-grill", tantou.toWebSlug())

        val maquis = allMerged.first { it.name.contains("Maquis") }
        assertEquals(9_999_888_777L, maquis.shopId)
        assertEquals("le-maquis-etoile", maquis.toWebSlug())

        // Search filter by city
        val filteredKoudougou = RestaurantDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = allMerged,
            query = "Koudougou"
        )
        assertEquals(1, filteredKoudougou.size)
        assertEquals("Le Maquis Étoile", filteredKoudougou[0].name)
    }
}
