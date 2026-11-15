package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KonabDatabase
import com.example.data.local.datastore.KonabDataStore
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.MultiShopStorage
import com.example.data.remote.supabase.RestaurantOrderItem
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.data.remote.supabase.RestaurantStorage
import com.example.data.remote.supabase.RestaurantTableRecord
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.data.repository.KonabRepository
import com.example.ui.screens.AgentUser
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossDeviceRoleAndMenuSyncTest {

    private lateinit var context: Context
    private lateinit var db: KonabDatabase
    private lateinit var repository: KonabRepository

    /**
     * Simulates real production Supabase PostgREST constraints:
     * 1) `products` table lacks `image_uri` column -> returns 400 `42703` if payload contains `image_uri`.
     * 2) Optional `simulateMissingRestaurantTables = true` -> returns 404 `PGRST205` on `restaurant_*`,
     *    verifying automatic fallback via `app_notifications` cloud mirror.
     * 3) Supports PostgREST `eq.` filtering on GET & DELETE.
     */
    private class RealisticSupabaseInterceptor(
        var simulateMissingRestaurantTables: Boolean = true,
        var rejectProductsImageUri: Boolean = true
    ) : Interceptor {
        val tables = mutableMapOf<String, LinkedHashMap<String, JSONObject>>()

        fun table(name: String): LinkedHashMap<String, JSONObject> =
            tables.getOrPut(name) { LinkedHashMap() }

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            val tableName = path.substringAfterLast("/").substringBefore("?")
            val method = req.method.uppercase()
            val bodyStr = req.bodyAsString()

            if (simulateMissingRestaurantTables && tableName.startsWith("restaurant_")) {
                return jsonResponse(
                    req,
                    404,
                    """{"code":"PGRST205","details":null,"hint":null,"message":"Could not find the table 'public.$tableName' in the schema cache"}"""
                )
            }

            return when (method) {
                "GET" -> {
                    val rows = table(tableName).values.toList()
                    val filtered = rows.filter { row ->
                        matchesFilter(req, "id", row) &&
                            matchesFilter(req, "shop_id", row) &&
                            matchesFilter(req, "type", row) &&
                            matchesFilter(req, "action_route", row) &&
                            matchesFilter(req, "deployer_id", row)
                    }
                    val arr = JSONArray()
                    filtered.forEach { arr.put(it) }
                    jsonResponse(req, 200, arr.toString())
                }

                "POST" -> {
                    val arr = JSONArray(bodyStr.ifBlank { "[]" })
                    if (rejectProductsImageUri && tableName == "products") {
                        for (i in 0 until arr.length()) {
                            if (arr.getJSONObject(i).has("image_uri")) {
                                return jsonResponse(
                                    req,
                                    400,
                                    """{"code":"42703","message":"column \"image_uri\" of relation \"products\" does not exist"}"""
                                )
                            }
                        }
                    }
                    val store = table(tableName)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val key = obj.opt("id")?.toString() ?: "$tableName-$i-${System.nanoTime()}"
                        store[key] = obj
                    }
                    jsonResponse(req, 201, "[]")
                }

                "DELETE" -> {
                    val store = table(tableName)
                    val keysToRemove = store.entries
                        .filter { (_, row) ->
                            matchesFilter(req, "id", row) &&
                                matchesFilter(req, "shop_id", row) &&
                                matchesFilter(req, "type", row) &&
                                matchesFilter(req, "action_route", row)
                        }
                        .map { it.key }
                    keysToRemove.forEach { store.remove(it) }
                    jsonResponse(req, 200, "[]")
                }

                else -> jsonResponse(req, 200, "[]")
            }
        }

        private fun matchesFilter(req: Request, param: String, row: JSONObject): Boolean {
            val raw = req.url.queryParameter(param) ?: return true
            val expected = raw.removePrefix("eq.")
            if (param == "id" || param == "shop_id") {
                val expectedLong = expected.toLongOrNull()
                if (expectedLong != null && row.has(param)) {
                    val rowLong = row.optLong(param, Long.MIN_VALUE)
                    if (rowLong != Long.MIN_VALUE) return rowLong == expectedLong
                }
            }
            return row.optString(param, "").equals(expected, ignoreCase = true)
        }

        private fun Request.bodyAsString(): String {
            val b = body ?: return ""
            val buffer = Buffer()
            b.writeTo(buffer)
            return buffer.readUtf8()
        }

        private fun jsonResponse(req: Request, code: Int, json: String): Response {
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearAllDeviceState()
        db = Room.inMemoryDatabaseBuilder(context, KonabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KonabRepository(db.konabDao(), KonabDataStore(context))
    }

    @After
    fun tearDown() {
        db.close()
        clearAllDeviceState()
    }

    private fun clearAllDeviceState() {
        listOf(
            "konab_agent_deployments_prefs",
            "konab_agent_session_prefs",
            "konab_supabase_prefs",
            "konab_licenses_prefs",
            "konab_multishop_prefs",
            "konab_restaurant_sync_prefs"
        ).forEach { pref ->
            context.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear().commit()
        }
        MultiShopStorage.clearConnectedShops(context)
    }

    private fun testAgent() = AgentUser(
        id = "AGT-26-7700",
        name = "Salif Ouédraogo",
        phone = "70110022",
        zone = "Ouagadougou",
        pin = "1234"
    )

    @Test
    fun `public restaurant menu loads dishes and images even when image_uri column and restaurant tables are missing`() = runBlocking {
        val fake = RealisticSupabaseInterceptor(
            simulateMissingRestaurantTables = true,
            rejectProductsImageUri = true
        )
        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncDevice1 = SupabaseSyncManager(context, apiClient)
        val agent = testAgent()
        AgentDeploymentStorage.saveAgentSession(context, agent)

        // Appareil 1 : Création Restaurant + plats avec images + Menu du Jour + Tables
        val createRes = syncDevice1.createShopExclusive(
            repository = repository,
            shopName = "Le Savoureux Faso",
            slogan = "Cuisine Burkinabè Authentique",
            phone = "70223344",
            address = "Avenue Kwamé Nkrumah",
            city = "Ouagadougou",
            currency = "FCFA",
            logoIcon = "restaurant",
            logoColorHex = 0xFFEA580C,
            businessType = "Restaurant",
            adminName = "Chef Aminata",
            adminPhone = "70223344",
            adminPin = "2468",
            preloadDemoData = false,
            subscriptionTier = "Professionnel",
            activeAgent = agent
        )
        assertTrue("Création restaurant réussie : ${createRes.exceptionOrNull()?.message}", createRes.isSuccess)
        val shopId = createRes.getOrThrow().cloudShopId

        val pouletId = repository.insertProduct(
            ProductEntity(
                name = "Poulet Bicyclette Grillé",
                category = "Grillades",
                buyPrice = 2000L,
                sellPrice = 4500L,
                stockQuantity = 25,
                unit = "Plat",
                imageUri = "https://cdn.konab.bf/poulet-bicyclette.jpg"
            )
        )
        val toId = repository.insertProduct(
            ProductEntity(
                name = "Riz Gras au Mouton",
                category = "Plats chauds",
                buyPrice = 1200L,
                sellPrice = 2500L,
                stockQuantity = 40,
                unit = "Plat",
                imageUri = "https://cdn.konab.bf/riz-gras.jpg"
            )
        )
        RestaurantStorage.setMenuOfDayProductIds(context, shopId, setOf(pouletId, toId))
        RestaurantStorage.upsertTable(
            context,
            shopId,
            RestaurantTableRecord(id = "TBL-1", shopId = shopId, tableNumber = "Table VIP 1", zone = "Terrasse", seats = 4)
        )

        val syncRes = syncDevice1.syncFullStore(repository)
        assertTrue("Sync complète restaurant réussie : ${syncRes.exceptionOrNull()?.message}", syncRes.isSuccess)

        // Simuler Appareil Client neuf : base locale + prefs totalement vidées
        repository.clearAllDemoData(keepShopProfile = false)
        clearAllDeviceState()

        // L'utilisateur ouvre l'annuaire des restaurants puis clique sur "Voir le menu"
        val dirRes = apiClient.fetchPublicRestaurantsDirectory()
        assertTrue(dirRes.isSuccess)
        val listing = dirRes.getOrThrow().firstOrNull { it.name == "Le Savoureux Faso" }
        assertNotNull("Le restaurant doit apparaître dans l'annuaire public", listing)

        val menuBundleRes = apiClient.fetchPublicRestaurantMenuBundle(
            shopId = listing!!.shopId,
            fallbackShopName = listing.name
        )
        assertTrue("Le menu public doit charger sans erreur : ${menuBundleRes.exceptionOrNull()?.message}", menuBundleRes.isSuccess)
        val bundle = menuBundleRes.getOrThrow()

        assertEquals("Les 2 plats doivent apparaître dans Voir le menu (jamais vide)", 2, bundle.products.size)
        val poulet = bundle.products.firstOrNull { it.name == "Poulet Bicyclette Grillé" }
        assertNotNull(poulet)
        assertEquals("L'image produit doit être restaurée via gallery_images_json", "https://cdn.konab.bf/poulet-bicyclette.jpg", poulet!!.imageUri)
        assertTrue("Le Menu du Jour doit être synchronisé même sans table restaurant_menu_day", bundle.menuDayIds.isNotEmpty())
        assertTrue("Les tables QR doivent être synchronisées via le miroir cloud", bundle.tables.any { it.tableNumber == "Table VIP 1" })
    }

    @Test
    fun `multi-user cross-device login gives immediate access to cash register, receipts and restaurant data by role`() = runBlocking {
        val fake = RealisticSupabaseInterceptor(
            simulateMissingRestaurantTables = true,
            rejectProductsImageUri = true
        )
        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncDevice1 = SupabaseSyncManager(context, apiClient)
        val agent = testAgent()
        AgentDeploymentStorage.saveAgentSession(context, agent)

        // --- Appareil 1 : Restaurant complet avec équipe multi-rôles, caisse ouverte, dépenses, vente & reçu ---
        val createRes = syncDevice1.createShopExclusive(
            repository = repository,
            shopName = "Maquis Le Baobab",
            slogan = "Saveurs du Burkina",
            phone = "76554433",
            address = "Secteur 15",
            city = "Bobo-Dioulasso",
            currency = "FCFA",
            logoIcon = "restaurant",
            logoColorHex = 0xFFEA580C,
            businessType = "Restaurant",
            adminName = "Moussa Kaboré",
            adminPhone = "76554433",
            adminPin = "1122",
            preloadDemoData = false,
            subscriptionTier = "Professionnel",
            activeAgent = agent
        )
        assertTrue(createRes.isSuccess)
        val shopId = createRes.getOrThrow().cloudShopId

        val staffManager = RestaurantStaffAccount(
            id = "STAFF-MGR-1",
            shopId = shopId,
            name = "Moussa Kaboré",
            phone = "76554433",
            role = RestaurantStaffRole.MANAGER,
            pin = "1122"
        )
        val staffCashier = RestaurantStaffAccount(
            id = "STAFF-CSH-1",
            shopId = shopId,
            name = "Fatimata Zongo",
            phone = "70889900",
            role = RestaurantStaffRole.CASHIER,
            pin = "3344"
        )
        val staffWaiter = RestaurantStaffAccount(
            id = "STAFF-WTR-1",
            shopId = shopId,
            name = "Issa Ouattara",
            phone = "71223344",
            role = RestaurantStaffRole.WAITER,
            pin = "5566"
        )
        val staffKitchen = RestaurantStaffAccount(
            id = "STAFF-KIT-1",
            shopId = shopId,
            name = "Chef Mariam",
            phone = "72334455",
            role = RestaurantStaffRole.KITCHEN,
            pin = "7788"
        )
        RestaurantStorage.saveStaffAccounts(
            context,
            shopId,
            listOf(staffManager, staffCashier, staffWaiter, staffKitchen)
        )

        val brochetteId = repository.insertProduct(
            ProductEntity(
                name = "Brochettes Capitaine",
                category = "Grillades",
                buyPrice = 1500L,
                sellPrice = 3000L,
                stockQuantity = 30,
                unit = "Assiette"
            )
        )
        val product = repository.getDao().getProductById(brochetteId)!!

        // Ouverture de caisse + dépense + vente encaissée liée à une commande restaurant
        repository.openCashSession(openingBalance = 50_000L, openedBy = "Fatimata Zongo (CAISSE)")
        repository.addCashMovement(type = "EXPENSE", amount = 8_500L, reason = "Achat charbon et épices", operatorName = "Moussa Kaboré (GÉRANT)")

        val posSale = repository.recordSale(
            customerId = null,
            customerName = "Client Table 2",
            items = listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = product.id,
                    productName = product.name,
                    quantity = 4,
                    unitPrice = product.sellPrice,
                    unitBuyPrice = product.buyPrice,
                    totalPrice = product.sellPrice * 4
                )
            ),
            subtotal = 12_000L,
            discountAmount = 0L,
            taxAmount = 0L,
            totalAmount = 12_000L,
            paymentMethod = "Espèces",
            isCredit = false,
            cashierName = "Fatimata Zongo (CAISSE)",
            notes = "Commande Restaurant #CMD-T2 • Table 2"
        )

        RestaurantStorage.upsertOrder(
            context,
            shopId,
            RestaurantOrderRecord(
                id = "ORD-T2-001",
                shopId = shopId,
                orderNumber = "CMD-T2",
                tableName = "Table 2",
                customerName = "Client Table 2",
                items = listOf(RestaurantOrderItem(productId = product.id, productName = product.name, unitPrice = 3000L, quantity = 4)),
                status = RestaurantOrderStatus.PAID,
                source = "WAITER",
                createdByName = "Issa Ouattara",
                assignedToKitchenBy = "Moussa Kaboré",
                notes = "Encaissé par Fatimata Zongo • Espèces",
                saleId = posSale.id
            )
        )

        assertTrue(syncDevice1.syncFullStore(repository).isSuccess)

        // --- Appareil 2 (nouveau téléphone vierge) ---
        repository.clearAllDemoData(keepShopProfile = false)
        clearAllDeviceState()
        assertTrue(repository.getAllSalesOnce().isEmpty())
        assertTrue(repository.getDao().getAllCashMovementsOnce().isEmpty())
        assertNull(repository.getDao().getActiveCashSessionOnce())

        val syncDevice2 = SupabaseSyncManager(context, apiClient)

        // 1) Connexion multi-utilisateur Caissier avec téléphone personnel du caissier + son PIN
        val cashierLogin = syncDevice2.loginAndHydrateShopRemote("70889900", "3344", repository)
        assertTrue("Connexion Caissier distante réussie : ${cashierLogin.exceptionOrNull()?.message}", cashierLogin.isSuccess)
        val restoredCashier = RestaurantStorage.getActiveStaff(context, shopId)
        assertNotNull("Le profil actif Caissier doit être restauré", restoredCashier)
        assertEquals(RestaurantStaffRole.CASHIER, restoredCashier!!.role)
        assertEquals("Fatimata Zongo", restoredCashier.name)

        // La caisse ouverte, les mouvements et les reçus doivent réapparaître immédiatement !
        val restoredSession = repository.getDao().getActiveCashSessionOnce()
        assertNotNull("La session de caisse ouverte doit réapparaître immédiatement", restoredSession)
        assertEquals(50_000L, restoredSession!!.openingBalance)

        val restoredMovements = repository.getDao().getAllCashMovementsOnce()
        assertTrue("L'ouverture de caisse doit réapparaître", restoredMovements.any { it.type == "CASH_IN" && it.amount == 50_000L })
        assertTrue("La dépense doit réapparaître", restoredMovements.any { it.type == "EXPENSE" && it.amount == 8_500L })
        assertTrue("L'encaissement espèce doit réapparaître", restoredMovements.any { it.type == "SALE" && it.amount == 12_000L })

        val restoredSales = repository.getAllSalesOnce()
        assertEquals("Le reçu de vente doit réapparaître immédiatement", 1, restoredSales.size)
        assertEquals(posSale.transactionNumber, restoredSales.first().transactionNumber)
        assertEquals("Fatimata Zongo (CAISSE)", restoredSales.first().cashierName)
        val restoredSaleItems = repository.getSaleItems(restoredSales.first().id)
        assertEquals("Les articles du reçu doivent réapparaître", 1, restoredSaleItems.size)
        assertEquals("Brochettes Capitaine", restoredSaleItems.first().productName)

        val restoredOrders = RestaurantStorage.getOrders(context, shopId)
        assertTrue("La commande restaurant payée doit réapparaître", restoredOrders.any { it.orderNumber == "CMD-T2" && it.status == RestaurantOrderStatus.PAID })

        // 2) Connexion Serveur depuis un appareil vierge avec Nom du restaurant + PIN Serveur
        repository.clearAllDemoData(keepShopProfile = false)
        clearAllDeviceState()
        val waiterLogin = syncDevice2.loginAndHydrateShopRemote("Maquis Le Baobab", "5566", repository)
        assertTrue("Connexion Serveur réussie : ${waiterLogin.exceptionOrNull()?.message}", waiterLogin.isSuccess)
        assertEquals(RestaurantStaffRole.WAITER, RestaurantStorage.getActiveStaff(context, shopId)?.role)
        assertEquals(1, repository.getAllSalesOnce().size)
        assertTrue(RestaurantStorage.getOrders(context, shopId).any { it.orderNumber == "CMD-T2" })

        // 3) Connexion Cuisine avec PIN Cuisine -> rôle KITCHEN restauré
        repository.clearAllDemoData(keepShopProfile = false)
        clearAllDeviceState()
        val kitchenLogin = syncDevice2.loginAndHydrateShopRemote("76554433", "7788", repository)
        assertTrue("Connexion Cuisine réussie : ${kitchenLogin.exceptionOrNull()?.message}", kitchenLogin.isSuccess)
        assertEquals(RestaurantStaffRole.KITCHEN, RestaurantStorage.getActiveStaff(context, shopId)?.role)

        // 4) Nouvelle vente créée sur place pendant que l'Appareil 2 est déjà connecté -> pullLedgerAndRestaurantNow doit la récupérer sans doublon !
        val secondOrderSaleId = MultiShopStorage.scopedCloudId(shopId, 777L)
        fake.table("sales")[secondOrderSaleId.toString()] = JSONObject().apply {
            put("id", secondOrderSaleId)
            put("shop_id", shopId)
            put("transaction_number", "20261115-0099-AB")
            put("timestamp", System.currentTimeMillis())
            put("customer_name", "Table 5")
            put("total_amount", 6000L)
            put("subtotal", 6000L)
            put("payment_method", "Wave")
            put("payment_status", "PAYÉ")
            put("cashier_name", "Moussa Kaboré (GÉRANT)")
        }
        val pullRes = syncDevice2.pullLedgerAndRestaurantNow(repository)
        assertTrue(pullRes.isSuccess)
        val mergedSales = repository.getAllSalesOnce()
        assertEquals("Le nouveau reçu créé à distance doit apparaître via pullLedgerAndRestaurantNow", 2, mergedSales.size)
        assertTrue(mergedSales.any { it.transactionNumber == "20261115-0099-AB" })
    }
}
