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
        assertNull(RestaurantQrParser.parseTableQrUri("konabpos://couture?shopId=77&ref=CMD-1"))

        // JSON table QR payload format
        val jsonQr = RestaurantQrParser.parseTableQrUri(
            """{"shopId":42519,"shopName":"Chez Awa","table":"Terrasse 7"}"""
        )
        assertNotNull(jsonQr)
        assertEquals(42519L, jsonQr!!.shopId)
        assertEquals("Chez Awa", jsonQr.shopName)
        assertEquals("Terrasse 7", jsonQr.tableName)

        // Short konab.menu URL format
        val shortDomain = RestaurantQrParser.parseTableQrUri("https://konab.menu/r?shopId=9001&table=Salle%202")
        assertNotNull(shortDomain)
        assertEquals(9001L, shortDomain!!.shopId)
        assertEquals("Salle 2", shortDomain.tableName)

        // Effective cloudShopId binding when local table had placeholder shopId <= 1L
        val localTable = RestaurantTableRecord(
            id = "TBL-LOCAL",
            shopId = 1L,
            tableNumber = "Table VIP 1"
        )
        val cloudShopId = 884422L
        val effectiveTable = if (localTable.shopId <= 1L && cloudShopId > 1L) {
            localTable.copy(shopId = cloudShopId)
        } else localTable
        val resolvedQr = RestaurantQrParser.parseTableQrUri(effectiveTable.buildQrUri("Le Gourmet"))
        assertNotNull(resolvedQr)
        assertEquals(cloudShopId, resolvedQr!!.shopId)
        assertEquals("Table VIP 1", resolvedQr.tableName)
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
    fun `qr code encoder satisfies ISO 18004 format info, Reed-Solomon syndromes, and UTF-8 round-trip`() {
        val shortPayload = RestaurantQrParser.buildTableQrUri(
            shopId = 42519L,
            shopName = "Le Maquis D'Or",
            tableName = "Table VIP 2"
        )
        // Longer payload (>133 bytes) forces Version 7 (2 blocks + version info bits)
        val longPayload = RestaurantQrParser.buildTableQrUri(
            shopId = 9988776655L,
            shopName = "Grand Restaurant Gastronomique Du Plateau Centrale",
            tableName = "Salon Privé Terrasse Panorama 14"
        )
        assertTrue("Expected >133 bytes to exercise multi-block V7+", longPayload.toByteArray(Charsets.UTF_8).size > 133)

        listOf(
            "konabpos://menu?shopId=1",
            shortPayload,
            longPayload
        ).forEach { payload ->
            val matrix = QrCodeHelper.encodeToMatrix(payload)
            val decoded = decodeIso18004LevelLMask0(matrix)
            assertEquals("QR round-trip decode mismatch for $payload", payload, decoded)
        }
    }

    /**
     * Strict ISO/IEC 18004 verifier/decoder (Level L, Mask 0, Versions 1..9) that checks:
     * - Format Info copies match 0x77C4 (Level L + Mask 0)
     * - Dark module at (4*version + 9, 8)
     * - Timing patterns at row/col 6
     * - Reed-Solomon GF(256) syndromes == 0 for every interleaved block
     * - Byte-mode (0100) UTF-8 payload reconstruction
     */
    private fun decodeIso18004LevelLMask0(matrix: Array<BooleanArray>): String {
        val dim = matrix.size
        val version = (dim - 17) / 4
        assertEquals(dim, 17 + 4 * version)

        data class Spec(val totalCw: Int, val blocks: Int, val ecPerBlock: Int)
        val specs = mapOf(
            1 to Spec(26, 1, 7),
            2 to Spec(44, 1, 10),
            3 to Spec(70, 1, 15),
            4 to Spec(100, 1, 20),
            5 to Spec(134, 1, 26),
            6 to Spec(172, 2, 18),
            7 to Spec(196, 2, 20),
            8 to Spec(242, 2, 24),
            9 to Spec(292, 2, 30)
        )
        val spec = specs[version] ?: error("Unsupported version $version")

        // Verify dark module
        assertTrue("Dark module must be true at (${4 * version + 9}, 8)", matrix[4 * version + 9][8])

        // Verify timing patterns
        for (i in 8 until dim - 8) {
            assertEquals("Row timing mismatch at col $i", i % 2 == 0, matrix[6][i])
            assertEquals("Col timing mismatch at row $i", i % 2 == 0, matrix[i][6])
        }

        // Verify Format Info copy 1 & copy 2 both equal 0x77C4
        var fmt1 = 0
        var fmt2 = 0
        for (bitIdx in 0..14) {
            val b1 = when {
                bitIdx <= 5 -> matrix[bitIdx][8]
                bitIdx == 6 -> matrix[7][8]
                bitIdx == 7 -> matrix[8][8]
                bitIdx == 8 -> matrix[8][7]
                else -> matrix[8][14 - bitIdx]
            }
            if (b1) fmt1 = fmt1 or (1 shl bitIdx)

            val b2 = if (bitIdx <= 7) matrix[8][dim - 1 - bitIdx] else matrix[dim - 15 + bitIdx][8]
            if (b2) fmt2 = fmt2 or (1 shl bitIdx)
        }
        assertEquals("Format Info copy 1 mismatch", 0x77C4, fmt1)
        assertEquals("Format Info copy 2 mismatch", 0x77C4, fmt2)

        // Mark function modules (reserved) exactly per ISO/IEC 18004
        val reserved = Array(dim) { BooleanArray(dim) }
        fun markFinder(top: Int, left: Int) {
            for (r in -1..7) for (c in -1..7) {
                val ar = top + r
                val ac = left + c
                if (ar in 0 until dim && ac in 0 until dim) reserved[ar][ac] = true
            }
        }
        markFinder(0, 0)
        markFinder(0, dim - 7)
        markFinder(dim - 7, 0)
        if (version >= 2) {
            val alignPos = when (version) {
                2 -> intArrayOf(6, 18)
                3 -> intArrayOf(6, 22)
                4 -> intArrayOf(6, 26)
                5 -> intArrayOf(6, 30)
                6 -> intArrayOf(6, 34)
                7 -> intArrayOf(6, 22, 38)
                8 -> intArrayOf(6, 24, 42)
                else -> intArrayOf(6, 26, 46)
            }
            for (r in alignPos) for (c in alignPos) {
                if (!reserved[r][c]) {
                    for (dr in -2..2) for (dc in -2..2) reserved[r + dr][c + dc] = true
                }
            }
        }
        for (i in 8 until dim - 8) {
            reserved[6][i] = true
            reserved[i][6] = true
        }
        reserved[4 * version + 9][8] = true
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in (dim - 8) until dim) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        if (version >= 7) {
            for (i in 0 until 18) {
                val a = i / 3
                val b = dim - 11 + (i % 3)
                reserved[a][b] = true
                reserved[b][a] = true
            }
        }

        // Read unmasked zig-zag bits
        val rawBits = mutableListOf<Int>()
        var upward = true
        var col = dim - 1
        while (col > 0) {
            if (col == 6) col--
            val rows = if (upward) (dim - 1 downTo 0).toList() else (0 until dim).toList()
            for (r in rows) {
                for (c in listOf(col, col - 1)) {
                    if (!reserved[r][c]) {
                        val mask = ((r + c) % 2 == 0)
                        val bit = if (matrix[r][c] xor mask) 1 else 0
                        rawBits.add(bit)
                    }
                }
            }
            col -= 2
            upward = !upward
        }

        val codewords = IntArray(spec.totalCw) { cwIdx ->
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or rawBits[cwIdx * 8 + b]
            }
            v
        }

        // De-interleave data and EC blocks
        val totalEc = spec.blocks * spec.ecPerBlock
        val dataCwCount = spec.totalCw - totalEc
        val dataPerBlock = dataCwCount / spec.blocks
        val dataBlocks = Array(spec.blocks) { IntArray(dataPerBlock) }
        val ecBlocks = Array(spec.blocks) { IntArray(spec.ecPerBlock) }

        var idx = 0
        for (i in 0 until dataPerBlock) {
            for (b in 0 until spec.blocks) dataBlocks[b][i] = codewords[idx++]
        }
        for (i in 0 until spec.ecPerBlock) {
            for (b in 0 until spec.blocks) ecBlocks[b][i] = codewords[idx++]
        }

        // GF(256) syndrome verification: P(alpha^i) == 0 for i in 0 until ecPerBlock
        val exp = IntArray(512)
        val log = IntArray(256)
        var gx = 1
        for (i in 0 until 255) {
            exp[i] = gx
            exp[i + 255] = gx
            log[gx] = i
            gx = gx shl 1
            if (gx >= 256) gx = gx xor 0x11D
        }
        fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

        for (b in 0 until spec.blocks) {
            val poly = dataBlocks[b] + ecBlocks[b]
            for (rootIdx in 0 until spec.ecPerBlock) {
                val root = exp[rootIdx]
                var eval = 0
                for (coeff in poly) {
                    eval = gfMul(eval, root) xor coeff
                }
                assertEquals("RS syndrome failed for block $b root $rootIdx (v$version)", 0, eval)
            }
        }

        // Reconstruct data bits from blocks and parse Byte mode (0100)
        val dataBits = mutableListOf<Int>()
        for (b in 0 until spec.blocks) {
            for (byteVal in dataBlocks[b]) {
                for (bit in 7 downTo 0) dataBits.add((byteVal shr bit) and 1)
            }
        }
        val mode = (dataBits[0] shl 3) or (dataBits[1] shl 2) or (dataBits[2] shl 1) or dataBits[3]
        assertEquals("Expected Byte mode 0100", 4, mode)
        var charCount = 0
        for (i in 4 until 12) charCount = (charCount shl 1) or dataBits[i]
        val outBytes = ByteArray(charCount) { byteIndex ->
            var byteVal = 0
            val offset = 12 + byteIndex * 8
            for (bit in 0 until 8) {
                byteVal = (byteVal shl 1) or dataBits[offset + bit]
            }
            byteVal.toByte()
        }
        return String(outBytes, Charsets.UTF_8)
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
