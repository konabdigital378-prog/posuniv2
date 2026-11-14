package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KonabDatabase
import com.example.data.local.datastore.KonabDataStore
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.ConnectedShopSession
import com.example.data.remote.supabase.MultiShopStorage
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseConfig
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
class MultiShopRemoteSyncTest {

    private lateinit var context: Context
    private lateinit var db: KonabDatabase
    private lateinit var repository: KonabRepository

    private class FakeMultiShopSupabaseInterceptor : Interceptor {
        val tables = mutableMapOf<String, LinkedHashMap<String, JSONObject>>()

        private fun table(name: String): LinkedHashMap<String, JSONObject> {
            return tables.getOrPut(name) { LinkedHashMap() }
        }

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            val tableName = path.substringAfterLast("/").substringBefore("?")
            val method = req.method.uppercase()
            val bodyStr = req.bodyAsString()

            return when (method) {
                "GET" -> {
                    val rows = table(tableName).values.toList()
                    val shopFilter = req.url.queryParameter("shop_id")
                        ?.removePrefix("eq.")
                        ?.toLongOrNull()
                    val deployerFilter = req.url.queryParameter("deployer_id")
                        ?.removePrefix("eq.")
                    val filtered = rows.filter { row ->
                        val shopOk = if (shopFilter != null) {
                            row.optLong("shop_id", 0L) == shopFilter
                        } else true
                        val depOk = if (deployerFilter != null) {
                            row.optString("deployer_id", "").equals(deployerFilter, ignoreCase = true)
                        } else true
                        shopOk && depOk
                    }
                    val arr = JSONArray()
                    filtered.forEach { arr.put(it) }
                    jsonResponse(req, 200, arr.toString())
                }

                "POST" -> {
                    val arr = JSONArray(bodyStr.ifBlank { "[]" })
                    val store = table(tableName)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val key = obj.opt("id")?.toString() ?: "$tableName-$i-${System.nanoTime()}"
                        store[key] = obj
                    }
                    jsonResponse(req, 201, "[]")
                }

                "DELETE" -> {
                    val idFilter = req.url.queryParameter("id")?.removePrefix("eq.")
                    if (idFilter != null) {
                        table(tableName).remove(idFilter)
                    }
                    jsonResponse(req, 200, "[]")
                }

                else -> jsonResponse(req, 200, "[]")
            }
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
        listOf(
            "konab_agent_deployments_prefs",
            "konab_agent_session_prefs",
            "konab_supabase_prefs",
            "konab_licenses_prefs",
            "konab_multishop_prefs"
        ).forEach { pref ->
            context.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear().commit()
        }
        MultiShopStorage.clearConnectedShops(context)

        db = Room.inMemoryDatabaseBuilder(context, KonabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KonabRepository(db.konabDao(), KonabDataStore(context))
    }

    @After
    fun tearDown() {
        db.close()
        MultiShopStorage.clearConnectedShops(context)
    }

    private fun testAgent() = AgentUser(
        id = "AGT-26-2042",
        name = "Awa Sawadogo",
        phone = "70445566",
        zone = "Bobo-Dioulasso",
        pin = "1234"
    )

    @Test
    fun `scopedCloudId isolates identical local Room IDs across shops and remains idempotent`() {
        val shopAId = 1710000100001L
        val shopBId = 1710000200002L

        val prodAScoped = MultiShopStorage.scopedCloudId(shopAId, 1L)
        val prodBScoped = MultiShopStorage.scopedCloudId(shopBId, 1L)

        assertNotEquals("Deux boutiques doivent avoir des IDs Cloud distincts pour le produit #1", prodAScoped, prodBScoped)
        assertEquals("scopedCloudId doit être idempotent sur un ID déjà scopé", prodAScoped, MultiShopStorage.scopedCloudId(shopAId, prodAScoped))

        val sql = SupabaseConfig.generateSqlSchema()
        assertTrue("Le schéma SQL doit inclure admin_pin", sql.contains("admin_pin"))
        assertTrue("Le schéma SQL doit inclure admin_phone", sql.contains("admin_phone"))
        assertTrue("Le schéma SQL doit inclure shop_id sur les tables métier", sql.contains("shop_id BIGINT"))
    }

    @Test
    fun `manager can log in remotely from another device and access exact shop products and past sales`() = runBlocking {
        val fake = FakeMultiShopSupabaseInterceptor()
        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val agent = testAgent()

        // --- Appareil 1 : Création de Boutique Alpha + produits + vente historique ---
        val syncDevice1 = SupabaseSyncManager(context, apiClient)
        AgentDeploymentStorage.saveAgentSession(context, agent)

        val createAlpha = syncDevice1.createShopExclusive(
            repository = repository,
            shopName = "Boutique Alpha",
            slogan = "Qualité Faso",
            phone = "65413799",
            address = "Centre-ville",
            city = "Ouagadougou",
            currency = "FCFA",
            logoIcon = "storefront",
            logoColorHex = 0xFF087A45,
            businessType = "Alimentation",
            adminName = "Ibrahim Traoré",
            adminPhone = "65413799",
            adminPin = "4455",
            preloadDemoData = false,
            subscriptionTier = "Professionnel",
            activeAgent = agent
        )
        assertTrue("Création Alpha réussie : ${createAlpha.exceptionOrNull()?.message}", createAlpha.isSuccess)
        val alphaCloudId = createAlpha.getOrThrow().cloudShopId

        val prodRizId = repository.insertProduct(
            ProductEntity(
                name = "Riz Parfumé 25kg",
                category = "Céréales",
                buyPrice = 11500L,
                sellPrice = 13500L,
                stockQuantity = 20,
                unit = "Sac"
            )
        )
        val prodHuileId = repository.insertProduct(
            ProductEntity(
                name = "Huile Végétale 5L",
                category = "Épicerie",
                buyPrice = 5200L,
                sellPrice = 6500L,
                stockQuantity = 15,
                unit = "Bidon"
            )
        )
        val insertedRiz = repository.getDao().getProductById(prodRizId)!!
        val alphaSale = repository.recordSale(
            customerId = null,
            customerName = "Client Passage",
            items = listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = insertedRiz.id,
                    productName = insertedRiz.name,
                    quantity = 2,
                    unitPrice = insertedRiz.sellPrice,
                    unitBuyPrice = insertedRiz.buyPrice,
                    totalPrice = insertedRiz.sellPrice * 2
                )
            ),
            subtotal = insertedRiz.sellPrice * 2,
            discountAmount = 0L,
            taxAmount = 0L,
            totalAmount = insertedRiz.sellPrice * 2,
            paymentMethod = "Espèces",
            isCredit = false,
            cashierName = "Ibrahim Traoré",
            notes = "Vente Alpha #1"
        )
        assertTrue(syncDevice1.syncFullStore(repository).isSuccess)

        // --- Simuler une Boutique Beta totalement séparée dans le Cloud ---
        val betaCloudId = 1799000555999L
        fake.tables.getOrPut("shop_profile") { LinkedHashMap() }[betaCloudId.toString()] = JSONObject().apply {
            put("id", betaCloudId)
            put("name", "Boutique Beta")
            put("business_type", "Mode")
            put("phone", "70112233")
            put("city", "Bobo-Dioulasso")
            put("currency", "FCFA")
            put("admin_name", "Mariam Konaté")
            put("admin_phone", "70112233")
            put("admin_pin", "8899")
            put("cashier_pin", "0000")
            put("deployer_id", agent.id)
        }
        val betaProdScopedId = MultiShopStorage.scopedCloudId(betaCloudId, 1L)
        fake.tables.getOrPut("products") { LinkedHashMap() }[betaProdScopedId.toString()] = JSONObject().apply {
            put("id", betaProdScopedId)
            put("shop_id", betaCloudId)
            put("name", "Bazin Riche VIP")
            put("category", "Tissus")
            put("buy_price", 20000L)
            put("sell_price", 28000L)
            put("stock_quantity", 8)
            put("min_stock_alert", 2)
            put("unit", "Pièce")
            put("is_available", true)
        }
        val betaSaleScopedId = MultiShopStorage.scopedCloudId(betaCloudId, 1L)
        fake.tables.getOrPut("sales") { LinkedHashMap() }[betaSaleScopedId.toString()] = JSONObject().apply {
            put("id", betaSaleScopedId)
            put("shop_id", betaCloudId)
            put("transaction_number", "KPOS-BETA-001")
            put("timestamp", System.currentTimeMillis() - 3600_000L)
            put("customer_name", "Client Beta")
            put("total_amount", 28000L)
            put("subtotal", 28000L)
            put("payment_method", "Orange Money")
            put("payment_status", "PAYÉ")
            put("cashier_name", "Mariam Konaté")
        }

        // --- Appareil 2 (vierge) : Réinitialiser la base locale et se connecter à distance ---
        repository.clearAllDemoData(keepShopProfile = false)
        MultiShopStorage.clearConnectedShops(context)
        context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(repository.getAllProductsOnce().isEmpty())
        assertTrue(repository.getAllSalesOnce().isEmpty())

        val remoteDeviceSync = SupabaseSyncManager(context, apiClient)

        // 1) Mauvais code PIN -> doit être refusé proprement
        val wrongPinRes = remoteDeviceSync.loginAndHydrateShopRemote("65413799", "0000", repository)
        assertTrue("Un mauvais PIN doit être rejeté", wrongPinRes.isFailure)
        assertTrue(repository.getAllProductsOnce().isEmpty())

        // 2) Bons identifiants gérant (téléphone + PIN) -> doit charger Boutique Alpha
        val loginAlpha = remoteDeviceSync.loginAndHydrateShopRemote("+226 65 41 37 99", "4455", repository)
        assertTrue("Connexion distante Alpha doit réussir : ${loginAlpha.exceptionOrNull()?.message}", loginAlpha.isSuccess)
        assertEquals("Boutique Alpha", repository.getShopProfileOnce()?.name)
        assertEquals("4455", repository.getShopProfileOnce()?.adminPin)

        val loadedProductsAlpha = repository.getAllProductsOnce()
        val loadedSalesAlpha = repository.getAllSalesOnce()
        assertEquals("Les 2 produits d'Alpha doivent être chargés à distance", 2, loadedProductsAlpha.size)
        assertTrue(loadedProductsAlpha.any { it.name == "Riz Parfumé 25kg" && it.stockQuantity == 18 })
        assertTrue(loadedProductsAlpha.any { it.name == "Huile Végétale 5L" })
        assertFalse("Aucun produit de Beta ne doit fuiter dans Alpha", loadedProductsAlpha.any { it.name == "Bazin Riche VIP" })

        assertEquals("L'ancienne vente d'Alpha doit être chargée", 1, loadedSalesAlpha.size)
        assertEquals(alphaSale.transactionNumber, loadedSalesAlpha.first().transactionNumber)
        assertEquals(27000L, loadedSalesAlpha.first().totalAmount)
        val loadedSaleItemsAlpha = repository.getSaleItems(loadedSalesAlpha.first().id)
        assertEquals("Les lignes d'articles de la vente doivent être restaurées", 1, loadedSaleItemsAlpha.size)
        assertEquals("Riz Parfumé 25kg", loadedSaleItemsAlpha.first().productName)

        // 3) Basculer vers Boutique Beta par nom + PIN -> doit charger uniquement Boutique Beta
        val loginBeta = remoteDeviceSync.loginAndHydrateShopRemote("Boutique Beta", "8899", repository)
        assertTrue("Connexion distante Beta doit réussir : ${loginBeta.exceptionOrNull()?.message}", loginBeta.isSuccess)
        assertEquals("Boutique Beta", repository.getShopProfileOnce()?.name)
        assertEquals(1, repository.getAllProductsOnce().size)
        assertEquals("Bazin Riche VIP", repository.getAllProductsOnce().first().name)
        assertEquals(1, repository.getAllSalesOnce().size)
        assertEquals("KPOS-BETA-001", repository.getAllSalesOnce().first().transactionNumber)

        // 4) Les 2 boutiques sont maintenant enregistrées dans la liste multiboutique du gérant
        val connectedList = MultiShopStorage.getConnectedShops(context)
        assertEquals("2 boutiques doivent figurer dans le sélecteur multiboutique", 2, connectedList.size)

        // 5) Simuler une nouvelle vente faite sur place à Boutique Alpha puis re-basculer depuis l'appareil distant
        val newRemoteSaleAlphaId = MultiShopStorage.scopedCloudId(alphaCloudId, 99L)
        fake.tables.getOrPut("sales") { LinkedHashMap() }[newRemoteSaleAlphaId.toString()] = JSONObject().apply {
            put("id", newRemoteSaleAlphaId)
            put("shop_id", alphaCloudId)
            put("transaction_number", "KPOS-ALPHA-REMOTE-002")
            put("timestamp", System.currentTimeMillis())
            put("customer_name", "Aïssata")
            put("total_amount", 6500L)
            put("subtotal", 6500L)
            put("payment_method", "Wave")
            put("payment_status", "PAYÉ")
            put("cashier_name", "Caissier 1")
        }

        val alphaSession: ConnectedShopSession = connectedList.first { it.shopName == "Boutique Alpha" }
        val switchBack = remoteDeviceSync.switchToConnectedShop(alphaSession, repository)
        assertTrue("Bascule vers Alpha réussie", switchBack.isSuccess)
        assertEquals("Boutique Alpha", repository.getShopProfileOnce()?.name)
        val refreshedSalesAlpha = repository.getAllSalesOnce()
        assertEquals("Les 2 ventes d'Alpha (historique + nouvelle à distance) doivent être visibles", 2, refreshedSalesAlpha.size)
        assertTrue(refreshedSalesAlpha.any { it.transactionNumber == "KPOS-ALPHA-REMOTE-002" })
    }
}
