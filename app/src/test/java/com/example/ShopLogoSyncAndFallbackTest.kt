package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KonabDatabase
import com.example.data.local.datastore.KonabDataStore
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.data.remote.supabase.SupabaseConfig
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.data.repository.KonabRepository
import com.example.util.ShopLogoHelper
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShopLogoSyncAndFallbackTest {

    private lateinit var context: Context
    private lateinit var db: KonabDatabase
    private lateinit var repository: KonabRepository

    private class FakeLogoSupabaseInterceptor(
        var failShopLogosBucket: Boolean = false,
        var failAllStorage: Boolean = false
    ) : Interceptor {
        val uploadedObjects = LinkedHashMap<String, ByteArray>()
        val shopProfiles = LinkedHashMap<Long, JSONObject>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            val method = req.method.uppercase()

            // Storage upload: /storage/v1/object/<bucket>/<key>
            if (path.contains("/storage/v1/object/") && (method == "POST" || method == "PUT")) {
                if (failAllStorage) {
                    return textResponse(req, 403, """{"error":"RLS denied"}""", "application/json")
                }
                if (failShopLogosBucket && path.contains("/object/shop-logos/")) {
                    return textResponse(req, 404, """{"error":"Bucket not found"}""", "application/json")
                }
                val bytes = req.bodyBytes()
                val rel = path.substringAfter("/storage/v1/object/")
                uploadedObjects[rel] = bytes
                return textResponse(req, 200, """{"Key":"$rel"}""", "application/json")
            }

            // Storage public fetch: /storage/v1/object/public/<bucket>/<key>
            if (path.contains("/storage/v1/object/public/") && method == "GET") {
                val rel = path.substringAfter("/storage/v1/object/public/")
                val bytes = uploadedObjects[rel]
                return if (bytes != null) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bytes.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                } else {
                    textResponse(req, 404, "<html>404 Not Found</html>", "text/html")
                }
            }

            // shop_profile endpoints
            if (path.endsWith("/shop_profile") && method == "GET") {
                val idQuery = req.url.queryParameter("id")
                val arr = JSONArray()
                if (idQuery != null && idQuery.startsWith("eq.")) {
                    val id = idQuery.removePrefix("eq.").toLongOrNull()
                    if (id != null && shopProfiles[id] != null) arr.put(shopProfiles[id])
                } else {
                    shopProfiles.values.forEach { arr.put(it) }
                }
                return textResponse(req, 200, arr.toString(), "application/json")
            }

            if (path.endsWith("/shop_profile") && method == "POST") {
                val arr = JSONArray(String(req.bodyBytes(), Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    shopProfiles[obj.getLong("id")] = obj
                }
                return textResponse(req, 201, "[]", "application/json")
            }

            return textResponse(req, 200, "[]", "application/json")
        }

        private fun Request.bodyBytes(): ByteArray {
            val b = Buffer()
            body?.writeTo(b)
            return b.readByteArray()
        }

        private fun textResponse(req: Request, code: Int, body: String, mediaType: String): Response =
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(body.toResponseBody(mediaType.toMediaType()))
                .build()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "logos").deleteRecursively()
        SupabaseConfig.saveCustomConfig(context, "https://testproj.supabase.co", "test-anon-key")

        db = Room.inMemoryDatabaseBuilder(context, KonabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KonabRepository(db.konabDao(), KonabDataStore(context))
    }

    @After
    fun tearDown() {
        db.close()
        SupabaseConfig.clearCustomConfig(context)
    }

    private fun createTempJpegFile(name: String = "logo.jpg"): File {
        val f = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(18, 125, 72))
        }
        FileOutputStream(f).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()
        return f
    }

    @Test
    fun `normalizeLogoUri resolves relative Supabase paths strips null sentinels and rejects dead local files`() {
        assertNull(ShopLogoHelper.normalizeLogoUri(null, "https://my.supabase.co"))
        assertNull(ShopLogoHelper.normalizeLogoUri("  null  ", "https://my.supabase.co"))
        assertNull(ShopLogoHelper.normalizeLogoUri("undefined", "https://my.supabase.co"))

        assertEquals(
            "https://my.supabase.co/storage/v1/object/public/shop-logos/shop_99.jpg",
            ShopLogoHelper.normalizeLogoUri("shop-logos/shop_99.jpg", "https://my.supabase.co")
        )
        assertEquals(
            "https://my.supabase.co/storage/v1/object/public/shop-ads/logos/a.jpg",
            ShopLogoHelper.normalizeLogoUri("/storage/v1/object/public/shop-ads/logos/a.jpg", "https://my.supabase.co/")
        )

        // Dead file:/// URI from another device must return null from getLocalFileIfExists
        assertNull(ShopLogoHelper.getLocalFileIfExists("file:///data/user/0/other.device/files/logos/missing.jpg"))

        // Existing local file must be detected
        val realFile = createTempJpegFile("existing_local.jpg")
        assertNotNull(ShopLogoHelper.getLocalFileIfExists(Uri.fromFile(realFile).toString()))
    }

    @Test
    fun `cacheRemoteLogoBlocking persists valid images, ignores HTML 404 errors, and provides fallback when unreachable`() {
        val jpegBytes = ByteArrayOutputStream().apply {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, this)
            bmp.recycle()
        }.toByteArray()

        var serverDown = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            if (serverDown) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("offline".toResponseBody("text/plain".toMediaType()))
                    .build()
            } else if (url.endsWith("/valid.jpg")) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jpegBytes.toResponseBody("image/jpeg".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html>not an image</html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
        }.build()

        // Invalid HTML payload must not be cached
        assertNull(ShopLogoHelper.cacheRemoteLogoBlocking(context, "https://cdn.example.com/bad.jpg", client))
        assertFalse(ShopLogoHelper.getCachedLogoFile(context, "https://cdn.example.com/bad.jpg").exists())

        // Valid JPEG is downloaded and cached persistently
        val cached = ShopLogoHelper.cacheRemoteLogoBlocking(context, "https://cdn.example.com/valid.jpg", client)
        assertNotNull(cached)
        assertTrue(cached!!.exists() && cached.length() > 0L)

        // Even when remote server goes unreachable later, loadLogoBitmap loads from persistent cache
        serverDown = true
        val offlineLoaded = ShopLogoHelper.loadLogoBitmap(context, "https://cdn.example.com/valid.jpg")
        assertNotNull(offlineLoaded)
        assertEquals(32, offlineLoaded!!.width)

        // Totally unreachable never-cached URL returns null on loadLogoBitmap and default drawable on loadLogoBitmapOrFallback
        assertNull(ShopLogoHelper.loadLogoBitmap(context, "https://127.0.0.1:1/unreachable.jpg"))
        val fallbackBmp = ShopLogoHelper.loadLogoBitmapOrFallback(context, "https://127.0.0.1:1/unreachable.jpg")
        assertNotNull("Fallback bitmap must be returned when logo URL is unreachable", fallbackBmp)
    }

    @Test
    fun `uploadShopLogoMedia uploads to shop-logos bucket, falls back to shop-ads, and falls back to Data URI when storage fails`() = runBlocking {
        val localFile = createTempJpegFile("shop_upload_test.jpg")
        val savedUri = ShopLogoHelper.saveLogoToInternalStorage(context, Uri.fromFile(localFile))!!

        // 1. Happy path: shop-logos bucket works
        val fake1 = FakeLogoSupabaseInterceptor(failShopLogosBucket = false)
        val sync1 = SupabaseSyncManager(context, SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake1).build()))
        val url1 = sync1.resolvePortableLogoUri(savedUri, 7777L)
        assertNotNull(url1)
        assertTrue(url1!!.startsWith("https://testproj.supabase.co/storage/v1/object/public/shop-logos/"))
        assertTrue(ShopLogoHelper.getCachedLogoFile(context, url1).exists())

        // 2. Fallback bucket: shop-logos missing (404) -> uses shop-ads automatically
        val fake2 = FakeLogoSupabaseInterceptor(failShopLogosBucket = true)
        val sync2 = SupabaseSyncManager(context, SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake2).build()))
        val url2 = sync2.resolvePortableLogoUri(savedUri, 7777L)
        assertNotNull(url2)
        assertTrue(url2!!.startsWith("https://testproj.supabase.co/storage/v1/object/public/shop-ads/shop-logos/"))

        // 3. Offline / bucket RLS denied -> gracefully produces portable Data URI
        val fake3 = FakeLogoSupabaseInterceptor(failAllStorage = true)
        val sync3 = SupabaseSyncManager(context, SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake3).build()))
        val dataUri = sync3.resolvePortableLogoUri(savedUri, 7777L)
        assertNotNull(dataUri)
        assertTrue(dataUri!!.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `logo changed on device A syncs via Supabase Storage and updates device B even without deployerId`() = runBlocking {
        val fake = FakeLogoSupabaseInterceptor()
        val apiClient = SupabaseApiClient(OkHttpClient.Builder().addInterceptor(fake).build())
        val syncManager = SupabaseSyncManager(context, apiClient)

        val shopId = 900_123L
        syncManager.saveCloudShopId(shopId, "Boutique Solidaire")

        // Device A updates logo from local file (even without deployerId)
        val localFile = createTempJpegFile("device_a_logo.jpg")
        val localSavedUri = ShopLogoHelper.saveLogoToInternalStorage(context, Uri.fromFile(localFile))!!
        val profileA = ShopProfileEntity(
            id = 1,
            name = "Boutique Solidaire",
            phone = "70112233",
            logoUri = localSavedUri,
            isConfigured = true,
            deployerId = null
        )
        repository.saveShopProfile(profileA)
        val pushRes = syncManager.pushShopProfileDirectly(profileA, isNewShop = false, repository = repository)
        assertTrue("Logo update push must succeed: ${pushRes.exceptionOrNull()?.message}", pushRes.isSuccess)

        val syncedProfileA = repository.getShopProfileOnce()!!
        val publishedUrl = syncedProfileA.logoUri!!
        assertTrue("Expected published HTTPS Storage URL, got $publishedUrl", publishedUrl.startsWith("https://testproj.supabase.co/storage/v1/object/public/shop-logos/"))
        assertEquals(publishedUrl, fake.shopProfiles[shopId]?.optString("logo_uri"))

        // Device B starts with empty logoUri (no local unpublished image) and pulls updates
        File(context.filesDir, "logos").deleteRecursively()
        repository.saveShopProfile(profileA.copy(logoUri = null))
        val pullRes = syncManager.pullLedgerAndRestaurantNow(repository)
        assertTrue(pullRes.isSuccess)

        val deviceBProfile = repository.getShopProfileOnce()!!
        assertEquals(publishedUrl, deviceBProfile.logoUri)
        assertTrue(
            "Device B must persistently cache downloaded logo locally",
            ShopLogoHelper.getCachedLogoFile(context, publishedUrl).exists()
        )
    }

    @Test
    fun `supabase schema defines both shop-logos and shop-ads buckets with read insert update delete policies`() {
        val sql = SupabaseConfig.generateSqlSchema()
        assertTrue(sql.contains("VALUES ('shop-logos', 'shop-logos', true)"))
        assertTrue(sql.contains("VALUES ('shop-ads', 'shop-ads', true)"))
        assertTrue(sql.contains("Allow public read shop-logos"))
        assertTrue(sql.contains("Allow anon upload shop-logos"))
        assertTrue(sql.contains("Allow anon update shop-logos"))
        assertTrue(sql.contains("Allow anon delete shop-logos"))
        assertTrue(sql.contains("Allow anon update shop-ads"))
    }
}
