package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.AdCarouselSettings
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.data.remote.supabase.ShopAdRecord
import com.example.data.remote.supabase.ShopAdStorage
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseSyncManager
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopAdsShowcaseTest {

    private lateinit var context: Context

    private class FakeSupabaseAdInterceptor : Interceptor {
        val shopProfiles = LinkedHashMap<Long, JSONObject>()
        val shopAds = LinkedHashMap<String, JSONObject>()
        val uploadedStoragePaths = mutableListOf<String>()

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
                path.endsWith("/shop_ads") && method == "GET" -> {
                    val arr = JSONArray()
                    shopAds.values.forEach { arr.put(it) }
                    jsonResponse(req, 200, arr.toString())
                }
                path.endsWith("/shop_ads") && method == "POST" -> {
                    val arr = JSONArray(bodyStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        shopAds[obj.getString("id")] = obj
                    }
                    jsonResponse(req, 201, "[]")
                }
                path.contains("/storage/v1/object/shop-ads/") && method == "POST" -> {
                    uploadedStoragePaths.add(path)
                    jsonResponse(req, 200, """{"Key":"$path"}""")
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
                .message("OK")
                .body(json.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_shop_ads_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("konab_agent_deployments_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `clampVideoDurationSec enforces 60s max duration`() {
        assertEquals(0, ShopAdStorage.clampVideoDurationSec(45, hasVideo = false))
        assertEquals(60, ShopAdStorage.clampVideoDurationSec(0, hasVideo = true))
        assertEquals(45, ShopAdStorage.clampVideoDurationSec(45, hasVideo = true))
        assertEquals(60, ShopAdStorage.clampVideoDurationSec(135, hasVideo = true))
    }

    @Test
    fun `mergeShopsAndAds includes every registered shop with logo and prioritizes configured video ads`() {
        val registeredShops = listOf(
            ShopProfileEntity(
                id = 101,
                name = "Boutique Étoile",
                businessType = "Mode",
                city = "Ouagadougou",
                phone = "70112233",
                slogan = "Prêt-à-porter chic",
                logoUri = "drawable:img_prod_mode",
                isConfigured = true
            ),
            ShopProfileEntity(
                id = 102,
                name = "Maquis Le Palmier",
                businessType = "Restaurant",
                city = "Bobo-Dioulasso",
                phone = "70445566",
                slogan = "Grillades & Plats du jour",
                logoUri = "drawable:img_prod_resto",
                isConfigured = true
            )
        )

        val configuredAds = listOf(
            ShopAdRecord(
                id = "AD-PALMIER",
                shopName = "Maquis Le Palmier",
                adTitle = "Menu Spécial Midi -20%",
                adDescription = "Poulet braisé + jus offert",
                videoUri = "https://cdn.example.com/palmier_60s.mp4",
                videoDurationSec = 95, // doit être plafonné à 60s
                isActive = true,
                updatedAt = 2000L
            )
        )

        val deployments = listOf(
            AgentDeploymentRecord(
                id = "DEP-300",
                agentId = "AGT-01",
                agentName = "Agent",
                agentPhone = "70000001",
                shopName = "Quincaillerie Sahel",
                shopPhone = "76123456",
                shopCity = "Koudougou",
                tier = "Pro",
                durationDays = 365,
                licenseKey = "KNB-123"
            )
        )

        val feed = ShopAdStorage.mergeShopsAndAds(
            registeredShops = registeredShops,
            ads = configuredAds,
            localShop = null,
            localDeployments = deployments
        )

        assertEquals("Les 3 boutiques doivent passer en défilement", 3, feed.size)

        // La pub vidéo configurée passe en tête avec le logo et le téléphone enrichis depuis shop_profile
        val first = feed[0]
        assertEquals("Maquis Le Palmier", first.shopName)
        assertTrue(first.hasVideo)
        assertEquals(60, first.videoDurationSec)
        assertEquals("drawable:img_prod_resto", first.logoUri)
        assertEquals("70445566", first.phone)

        // Les autres boutiques inscrites défilent aussi avec leur logo
        val second = feed[1]
        assertEquals("Boutique Étoile", second.shopName)
        assertEquals("drawable:img_prod_mode", second.logoUri)
        assertFalse(second.hasVideo)

        val third = feed[2]
        assertEquals("Quincaillerie Sahel", third.shopName)
        assertEquals("76123456", third.phone)
    }

    @Test
    fun `publishShopAd uploads media to storage and fetchShowcaseFeed delivers video ad and logos to other users`() = runBlocking {
        val fake = FakeSupabaseAdInterceptor()
        fake.shopProfiles[501L] = JSONObject().apply {
            put("id", 501L)
            put("name", "Pharmacie Santé Plus")
            put("business_type", "Pharmacie")
            put("city", "Ouagadougou")
            put("phone", "65001122")
            put("slogan", "À votre service 24h/24")
            put("logo_uri", "drawable:ic_konab_logo")
        }

        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncManager = SupabaseSyncManager(context, apiClient)

        val tempLogo = File(context.cacheDir, "test_logo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tempVideo = File(context.cacheDir, "test_promo.mp4").apply { writeBytes(byteArrayOf(4, 5, 6, 7)) }

        val adToPublish = ShopAdRecord(
            id = "AD-501",
            shopId = 501L,
            shopName = "Boutique Faso Vision",
            businessType = "Boutique",
            city = "Bobo-Dioulasso",
            phone = "70998877",
            whatsappPhone = "70998877",
            logoUri = tempLogo.absolutePath,
            adTitle = "Arrivage Spécial Tabaski",
            adDescription = "Promotions jusqu'à -30% sur toute la boutique",
            videoUri = tempVideo.absolutePath,
            videoDurationSec = 58,
            isActive = true
        )

        val pubRes = syncManager.publishShopAd(
            ad = adToPublish,
            localLogoFile = tempLogo,
            localVideoFile = tempVideo
        )
        assertTrue("La publication doit réussir", pubRes.isSuccess)
        val published = pubRes.getOrThrow()
        assertTrue("La pub doit être marquée synchronisée", published.isSynced)
        assertTrue("La vidéo doit être hébergée en URL publique Storage", published.videoUri.startsWith("https://"))
        assertTrue("Le logo doit être hébergé en URL publique Storage", published.logoUri.startsWith("https://"))
        assertEquals(2, fake.uploadedStoragePaths.size)
        assertEquals(1, fake.shopAds.size)

        // Simuler un autre utilisateur qui vide son cache local et charge le flux d'accueil depuis Supabase
        context.getSharedPreferences("konab_shop_ads_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val otherUserFeed = syncManager.fetchShowcaseFeed(localShop = null)

        assertTrue(otherUserFeed.fromCloud)
        assertEquals(2, otherUserFeed.showcaseAds.size)

        val videoAdForOthers = otherUserFeed.showcaseAds.first()
        assertEquals("Boutique Faso Vision", videoAdForOthers.shopName)
        assertEquals("Arrivage Spécial Tabaski", videoAdForOthers.adTitle)
        assertTrue(videoAdForOthers.hasVideo)
        assertEquals(58, videoAdForOthers.videoDurationSec)
        assertEquals("70998877", videoAdForOthers.phone)

        val registeredShopForOthers = otherUserFeed.showcaseAds[1]
        assertEquals("Pharmacie Santé Plus", registeredShopForOthers.shopName)
        assertEquals("drawable:ic_konab_logo", registeredShopForOthers.logoUri)
    }

    @Test
    fun `carousel settings persist and control partner logo scrolling and video priority`() {
        val customSettings = AdCarouselSettings(
            autoScrollEnabled = false,
            scrollIntervalSeconds = 6,
            includePartnerLogosInScroll = false,
            prioritizeVideoAds = false,
            autoPlayVideoPreviewInCarousel = false
        )
        ShopAdStorage.saveCarouselSettings(context, customSettings)
        val loaded = ShopAdStorage.getCarouselSettings(context)
        assertEquals(customSettings, loaded)

        val registeredShops = listOf(
            ShopProfileEntity(
                id = 201,
                name = "Boutique Étoile",
                logoUri = "drawable:img_prod_mode",
                phone = "70112233",
                isConfigured = true
            ),
            ShopProfileEntity(
                id = 202,
                name = "Salon Prestige",
                logoUri = "drawable:hero_entrepreneur",
                phone = "70990011",
                isConfigured = true
            )
        )
        val ads = listOf(
            ShopAdRecord(
                id = "AD-LOGO-NEW",
                shopName = "Boutique Logo Récemment Mis à Jour",
                logoUri = "drawable:img_prod_epicerie",
                videoUri = "",
                isActive = true,
                updatedAt = 5000L
            ),
            ShopAdRecord(
                id = "AD-VIDEO-OLDER",
                shopName = "Salon Vidéo 60s",
                logoUri = "drawable:hero_entrepreneur",
                videoUri = "https://cdn.example.com/salon_60s.mp4",
                videoDurationSec = 60,
                isActive = true,
                updatedAt = 3000L
            ),
            ShopAdRecord(
                id = "AD-PAUSED",
                shopName = "Boutique Étoile",
                isActive = false,
                updatedAt = 4000L
            )
        )

        // Avec includePartnerLogosInScroll = false & prioritizeVideoAds = false :
        // - Salon Prestige (non configuré en pub) est exclu du défilement
        // - Boutique Étoile (en pause) est exclue
        // - L'ordre suit updatedAt décroissant sans forcer la vidéo en premier
        val restrictedFeed = ShopAdStorage.mergeShopsAndAds(
            registeredShops = registeredShops,
            ads = ads,
            settings = loaded
        )
        assertEquals(2, restrictedFeed.size)
        assertEquals("Boutique Logo Récemment Mis à Jour", restrictedFeed[0].shopName)
        assertEquals("Salon Vidéo 60s", restrictedFeed[1].shopName)

        // Avec les réglages par défaut (includePartnerLogosInScroll = true, prioritizeVideoAds = true) :
        // - Salon Prestige apparaît avec son logo
        // - Boutique Étoile reste exclue car explicitement mise en pause !
        // - La vidéo 60s repasse en tête
        val defaultFeed = ShopAdStorage.mergeShopsAndAds(
            registeredShops = registeredShops,
            ads = ads,
            settings = AdCarouselSettings()
        )
        assertEquals(3, defaultFeed.size)
        assertEquals("Salon Vidéo 60s", defaultFeed[0].shopName)
        assertTrue(defaultFeed[0].hasVideo)
        assertTrue(defaultFeed.any { it.shopName == "Salon Prestige" && it.isFallbackShopOnly })
        assertFalse(
            "Une boutique partenaire mise en pause ne doit pas revenir via le fallback",
            defaultFeed.any { it.shopName == "Boutique Étoile" }
        )

        // buildManageablePartnerShops doit lister toutes les boutiques (actives + en pause + inscrites)
        val manageable = ShopAdStorage.buildManageablePartnerShops(
            registeredShops = registeredShops,
            ads = ads
        )
        assertEquals(4, manageable.size)
        assertTrue(manageable.any { it.shopName == "Boutique Étoile" && !it.isActive })
        assertTrue(manageable.any { it.shopName == "Salon Prestige" && it.isFallbackShopOnly })
        assertTrue(manageable.any { it.shopName == "Salon Vidéo 60s" && it.hasVideo })
    }
}
