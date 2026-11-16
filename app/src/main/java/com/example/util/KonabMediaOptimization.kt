package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Infrastructure réseau et média partagée pour réduire drastiquement le Cached Egress Supabase :
 * - Pool HTTP unique réutilisable entre PostgREST, Storage et Coil
 * - Cache disque HTTP (48 Mo) + Cache-Control longue durée sur les médias publics
 * - ImageLoader global Coil avec cache mémoire (20%) & cache disque (50 Mo)
 * - Cache vidéo Media3 SimpleCache (80 Mo) pour éviter de re-télécharger les vidéos en boucle
 * - Compression WebP automatique avec sous-échantillonnage inSampleSize
 */
object KonabNetworkClient {
    private val sharedConnectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    private val restClientInstance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var mediaClientInstance: OkHttpClient? = null

    fun getSharedRestClient(): OkHttpClient = restClientInstance

    fun getMediaClient(context: Context): OkHttpClient {
        mediaClientInstance?.let { return it }
        return synchronized(this) {
            mediaClientInstance?.let { return it }
            val appCtx = context.applicationContext
            val cacheDir = File(appCtx.cacheDir, "konab_http_media_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val httpCache = Cache(cacheDir, 48L * 1024L * 1024L)
            OkHttpClient.Builder()
                .connectionPool(sharedConnectionPool)
                .cache(httpCache)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val path = chain.request().url.encodedPath
                    if (chain.request().method.equals("GET", ignoreCase = true) &&
                        response.isSuccessful &&
                        path.contains("/storage/v1/object/public/")
                    ) {
                        response.newBuilder()
                            .removeHeader("Pragma")
                            .header("Cache-Control", "public, max-age=604800, stale-while-revalidate=2592000")
                            .build()
                    } else {
                        response
                    }
                }
                .build()
                .also { mediaClientInstance = it }
        }
    }
}

object KonabCoilSetup {
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            val diskCacheDir = File(appCtx.cacheDir, "konab_coil_disk")
            if (!diskCacheDir.exists()) diskCacheDir.mkdirs()
            val loader = ImageLoader.Builder(appCtx)
                .okHttpClient { KonabNetworkClient.getMediaClient(appCtx) }
                .memoryCache {
                    MemoryCache.Builder(appCtx)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(diskCacheDir)
                        .maxSizeBytes(50L * 1024L * 1024L)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
            Coil.setImageLoader(loader)
            initialized = true
        }
    }
}

object KonabVideoCache {
    @Volatile
    private var simpleCache: SimpleCache? = null

    @OptIn(UnstableApi::class)
    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        return synchronized(this) {
            simpleCache?.let { return it }
            val appCtx = context.applicationContext
            val cacheDir = File(appCtx.cacheDir, "konab_video_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(80L * 1024L * 1024L)
            val dbProvider = StandaloneDatabaseProvider(appCtx)
            SimpleCache(cacheDir, evictor, dbProvider).also { simpleCache = it }
        }
    }

    @OptIn(UnstableApi::class)
    fun getMediaSourceFactory(context: Context): MediaSource.Factory {
        val appCtx = context.applicationContext
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        val upstreamFactory = DefaultDataSource.Factory(appCtx, httpFactory)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(getCache(appCtx))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(cacheFactory)
    }
}

object KonabImageOptimizer {

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqMaxDim: Int): Int {
        var inSampleSize = 1
        val maxSide = maxOf(rawWidth, rawHeight)
        if (maxSide > reqMaxDim && reqMaxDim > 0) {
            var half = maxSide / 2
            while ((half / inSampleSize) >= reqMaxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleBitmapKeepRatio(bmp: Bitmap, maxDimPx: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDimPx || maxSide <= 0) return bmp
        val ratio = maxDimPx.toFloat() / maxSide.toFloat()
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    /**
     * Décode une image sans surconsommer la RAM (via inSampleSize), redimensionne à [maxDimPx]
     * puis compresse au format WebP léger dans [destDir].
     */
    fun compressUriToWebpFile(
        context: Context,
        sourceUri: Uri,
        destDir: File,
        fileNamePrefix: String,
        maxDimPx: Int = 720,
        quality: Int = 80
    ): File? {
        return try {
            if (!destDir.exists()) destDir.mkdirs()

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return null

            val scaled = scaleBitmapKeepRatio(decoded, maxDimPx)
            val destFile = File(destDir, "${fileNamePrefix}_${System.currentTimeMillis()}.webp")
            FileOutputStream(destFile).use { output ->
                scaled.compress(webpCompressFormat(), quality.coerceIn(45, 95), output)
            }
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            if (destFile.exists() && destFile.length() > 0L) destFile else null
        } catch (e: Exception) {
            android.util.Log.e("KonabImageOptimizer", "Erreur compression WebP: ${e.message}", e)
            null
        }
    }

    fun compressBitmapToWebpDataUri(
        bmp: Bitmap,
        maxDimPx: Int = 256,
        quality: Int = 74
    ): String? {
        return try {
            val scaled = scaleBitmapKeepRatio(bmp, maxDimPx)
            val baos = ByteArrayOutputStream()
            scaled.compress(webpCompressFormat(), quality.coerceIn(45, 90), baos)
            if (scaled !== bmp) scaled.recycle()
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/webp;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }

    fun imageMimeTypeForFile(file: File): String {
        return when (file.extension.lowercase()) {
            "webp" -> "image/webp"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
    }
}
