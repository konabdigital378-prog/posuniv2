package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KonabDatabase
import com.example.data.local.datastore.KonabDataStore
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.data.repository.KonabRepository
import com.example.ui.screens.AgentUser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class ShopCreationDeduplicationTest {

    private lateinit var context: Context
    private lateinit var db: KonabDatabase
    private lateinit var repository: KonabRepository

    private class FakeSupabaseInterceptor : Interceptor {
        val shopProfiles = LinkedHashMap<Long, JSONObject>()
        val agentDeployments = LinkedHashMap<String, JSONObject>()
        val agents = LinkedHashMap<String, JSONObject>()

        @Synchronized
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            val method = req.method.uppercase()
            val bodyStr = req.bodyAsString()

            return when {
                path.endsWith("/shop_profile") && method == "GET" -> {
                    val arr = JSONArray()
                    shopProfiles.values.forEach { arr.put(it) }
                    jsonResponse(req, 200, arr.toString())
                }
                path.endsWith("/shop_profile") && method == "POST" -> {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getLong("id")
                        val normName = AgentDeploymentStorage.normalizeShopName(obj.optString("name", ""))
                        val duplicate = shopProfiles.values.firstOrNull {
                            it.getLong("id") != id &&
                                AgentDeploymentStorage.normalizeShopName(it.optString("name", "")) == normName
                        }
                        if (duplicate != null) {
                            return jsonResponse(
                                req,
                                409,
                                """{"code":"23505","message":"duplicate key value violates unique constraint idx_shop_profile_unique_name"}"""
                            )
                        }
                        shopProfiles[id] = obj
                    }
                    jsonResponse(req, 201, "[]")
                }
                path.endsWith("/agent_deployments") && method == "GET" -> {
                    val arr = JSONArray()
                    agentDeployments.values.forEach { arr.put(it) }
                    jsonResponse(req, 200, arr.toString())
                }
                path.endsWith("/agent_deployments") && method == "POST" -> {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        val normName = AgentDeploymentStorage.normalizeShopName(obj.optString("shop_name", ""))
                        val duplicate = agentDeployments.values.firstOrNull {
                            it.getString("id") != id &&
                                AgentDeploymentStorage.normalizeShopName(it.optString("shop_name", "")) == normName
                        }
                        if (duplicate != null) {
                            return jsonResponse(
                                req,
                                409,
                                """{"code":"23505","message":"duplicate key value violates unique constraint idx_agent_deployments_unique_shop_name"}"""
                            )
                        }
                        agentDeployments[id] = obj
                    }
                    jsonResponse(req, 201, "[]")
                }
                path.endsWith("/agents") && method == "POST" -> {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        agents[obj.getString("id")] = obj
                    }
                    jsonResponse(req, 201, "[]")
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
        context.getSharedPreferences("konab_agent_deployments_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("konab_agent_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("konab_licenses_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        db = Room.inMemoryDatabaseBuilder(context, KonabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KonabRepository(db.konabDao(), KonabDataStore(context))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun testAgent() = AgentUser(
        id = "AGT-26-1001",
        name = "Moussa Koné",
        phone = "70112233",
        zone = "Ouagadougou",
        pin = "1234"
    )

    @Test
    fun `saveDeployment deduplicates by normalized shop name and keeps single canonical id`() {
        val d1 = AgentDeploymentRecord(
            id = "DEP-100",
            agentId = "AGT-26-1001",
            agentName = "Moussa",
            agentPhone = "70112233",
            shopName = "  Boutique   Ouaga ",
            tier = "Standard",
            durationDays = 30,
            licenseKey = "KNB-STD-1M-AAAA-BBBB",
            timestamp = 1000L
        )
        val d2 = d1.copy(
            id = "DEP-200",
            shopName = "boutique ouaga",
            tier = "Professionnel",
            durationDays = 365,
            licenseKey = "KNB-PRO-1A-CCCC-DDDD",
            timestamp = 2000L
        )
        val d3 = d1.copy(
            id = "DEP-300",
            shopName = "BOUTIQUE OUAGA",
            tier = "Premium",
            durationDays = 3650,
            licenseKey = "KNB-VIP-VIE-EEEE-FFFF",
            timestamp = 3000L
        )

        AgentDeploymentStorage.saveDeployment(context, d1)
        AgentDeploymentStorage.saveDeployment(context, d2)
        AgentDeploymentStorage.saveDeployment(context, d3)

        val all = AgentDeploymentStorage.getAllDeployments(context)
        assertEquals("Une seule entrée locale doit exister par boutique", 1, all.size)
        assertEquals("L'ID canonique initial doit être conservé", "DEP-100", all.first().id)
        assertEquals("Premium", all.first().tier)
        assertEquals("KNB-VIP-VIE-EEEE-FFFF", all.first().licenseKey)
    }

    @Test
    fun `createShopExclusive creates shop once without false duplicate error and repeated syncs do not duplicate`() = runBlocking {
        val fake = FakeSupabaseInterceptor()
        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncManager = SupabaseSyncManager(context, apiClient)
        val agent = testAgent()
        AgentDeploymentStorage.saveAgentSession(context, agent)

        val result = syncManager.createShopExclusive(
            repository = repository,
            shopName = "Boutique Faso Test",
            slogan = "Qualité & Prix",
            phone = "70112233",
            address = "Secteur 15",
            city = "Ouagadougou",
            currency = "FCFA",
            logoIcon = "storefront",
            logoColorHex = 0xFF087A45,
            businessType = "Boutique",
            adminName = "Moussa",
            adminPhone = "70112233",
            adminPin = "1234",
            preloadDemoData = false,
            subscriptionTier = "Professionnel",
            activeAgent = agent
        )

        assertTrue("La création initiale doit réussir : ${result.exceptionOrNull()?.message}", result.isSuccess)
        val created = result.getOrThrow()
        assertTrue("La synchro Cloud doit réussir", created.isCloudSyncSuccess)
        assertEquals("Une seule ligne dans shop_profile", 1, fake.shopProfiles.size)
        assertEquals("Une seule ligne dans agent_deployments", 1, fake.agentDeployments.size)
        assertEquals("Une seule entrée locale dans AgentDeploymentStorage", 1, AgentDeploymentStorage.getAllDeployments(context).size)

        // Simuler des synchronisations concurrentes et répétées (démarrage, périodique, mise à jour)
        val syncResults = (1..4).map {
            async { syncManager.syncAll(repository) }
        }.awaitAll()
        syncResults.forEach { res ->
            assertTrue("Les syncs répétées ne doivent pas échouer en faux conflit : ${res.exceptionOrNull()?.message}", res.isSuccess)
        }

        val profile = repository.getShopProfileOnce()!!
        val updateRes = syncManager.pushShopProfileDirectly(profile.copy(slogan = "Mis à jour"), isNewShop = false)
        assertTrue("La mise à jour du profil existant doit réussir : ${updateRes.exceptionOrNull()?.message}", updateRes.isSuccess)

        assertEquals("shop_profile doit toujours contenir exactement 1 ligne", 1, fake.shopProfiles.size)
        assertEquals("agent_deployments doit toujours contenir exactement 1 ligne", 1, fake.agentDeployments.size)

        // Tenter une seconde création sur le même appareil doit échouer proprement sans créer de doublon
        val secondCreate = syncManager.createShopExclusive(
            repository = repository,
            shopName = "Boutique Faso Test",
            slogan = "",
            phone = "70112233",
            address = "",
            city = "Ouagadougou",
            currency = "FCFA",
            logoIcon = "storefront",
            logoColorHex = 0xFF087A45,
            businessType = "Boutique",
            adminName = "Moussa",
            adminPhone = "70112233",
            adminPin = "1234",
            preloadDemoData = false,
            subscriptionTier = "Standard",
            activeAgent = agent
        )
        assertTrue("Une recréation doit être refusée", secondCreate.isFailure)
        assertEquals(1, fake.shopProfiles.size)
        assertEquals(1, fake.agentDeployments.size)
    }

    @Test
    fun `createShopExclusive aborts cleanly on remote duplicate without leaving local deployment or Room data`() = runBlocking {
        val fake = FakeSupabaseInterceptor()
        fake.shopProfiles[999999L] = JSONObject().apply {
            put("id", 999999L)
            put("name", "Alimentation Koudougou")
            put("deployer_id", "AGT-OTHER-01")
        }

        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncManager = SupabaseSyncManager(context, apiClient)
        val agent = testAgent()
        AgentDeploymentStorage.saveAgentSession(context, agent)

        val result = syncManager.createShopExclusive(
            repository = repository,
            shopName = "  alimentation   koudougou ",
            slogan = "",
            phone = "70998877",
            address = "",
            city = "Koudougou",
            currency = "FCFA",
            logoIcon = "storefront",
            logoColorHex = 0xFF087A45,
            businessType = "Alimentation",
            adminName = "Ali",
            adminPhone = "70998877",
            adminPin = "4321",
            preloadDemoData = false,
            subscriptionTier = "Standard",
            activeAgent = agent
        )

        assertTrue("Un nom déjà présent sur Supabase doit être refusé", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("existe déjà", ignoreCase = true) == true)
        assertNull("Room ne doit pas contenir de boutique configurée en cas d'échec", repository.getShopProfileOnce())
        assertTrue("Aucun déploiement local ne doit être sauvegardé en cas de conflit", AgentDeploymentStorage.getAllDeployments(context).isEmpty())
        assertEquals("Aucun déploiement distant ne doit être créé", 0, fake.agentDeployments.size)
        assertEquals("shop_profile distant doit rester inchangé", 1, fake.shopProfiles.size)
    }

    @Test
    fun `pushAgentDeployments reuses existing remote id by shopName to prevent duplicate or 409`() = runBlocking {
        val fake = FakeSupabaseInterceptor()
        fake.agentDeployments["DEP-REMOTE-1"] = JSONObject().apply {
            put("id", "DEP-REMOTE-1")
            put("agent_id", "AGT-26-1001")
            put("agent_name", "Moussa")
            put("shop_name", "Salon Étoile")
            put("tier", "Standard")
            put("duration_days", 30)
            put("license_key", "KNB-STD-1M-1111-2222")
            put("timestamp", 1000L)
        }

        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val res = apiClient.pushAgentDeployments(
            listOf(
                AgentDeploymentRecord(
                    id = "DEP-LOCAL-999",
                    agentId = "AGT-26-1001",
                    agentName = "Moussa",
                    agentPhone = "70112233",
                    shopName = "  salon   étoile ",
                    tier = "Premium",
                    durationDays = 3650,
                    licenseKey = "KNB-VIP-VIE-3333-4444",
                    timestamp = 5000L
                )
            )
        )

        assertTrue("La mise à jour par nom de boutique doit réussir sans 409 : ${res.exceptionOrNull()?.message}", res.isSuccess)
        assertEquals("Une seule ligne doit exister dans agent_deployments", 1, fake.agentDeployments.size)
        assertTrue(fake.agentDeployments.containsKey("DEP-REMOTE-1"))
        assertEquals("Premium", fake.agentDeployments["DEP-REMOTE-1"]?.optString("tier"))
    }
}
