package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
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
     * Décode une image sans surconsommer la RAM (ImageDecoder sur API 28+ ou fichier temporaire
     * seekable + inSampleSize + correction EXIF), redimensionne à [maxDimPx] puis compresse au
     * format WebP léger (avec repli JPEG si nécessaire) dans [destDir].
     */
    fun compressUriToWebpFile(
        context: Context,
        sourceUri: Uri,
        destDir: File,
        fileNamePrefix: String,
        maxDimPx: Int = 720,
        quality: Int = 80
    ): File? {
        var tempCopy: File? = null
        return try {
            if (!destDir.exists()) destDir.mkdirs()

            val localSourceFile: File = when {
                sourceUri.scheme.equals("file", ignoreCase = true) -> {
                    val path = sourceUri.path
                    val existing = if (!path.isNullOrBlank()) File(path) else null
                    if (existing != null && existing.exists() && existing.length() > 0L) {
                        existing
                    } else {
                        return null
                    }
                }
                else -> {
                    val tmpDir = File(context.cacheDir, "konab_img_tmp").apply { if (!exists()) mkdirs() }
                    val tmp = File(tmpDir, "pick_${System.currentTimeMillis()}_${System.nanoTime()}.bin")
                    val copied = copyUriToTempFile(context, sourceUri, tmp)
                    if (!copied || !tmp.exists() || tmp.length() <= 0L) {
                        tmp.delete()
                        return null
                    }
                    tempCopy = tmp
                    tmp
                }
            }

            val decoded = decodeSampledBitmapFromFile(localSourceFile, maxDimPx) ?: return null
            val scaled = scaleBitmapKeepRatio(decoded, maxDimPx)
            if (scaled !== decoded) {
                decoded.recycle()
            }

            val timestamp = System.currentTimeMillis()
            val safeQuality = quality.coerceIn(45, 95)
            val webpFile = File(destDir, "${fileNamePrefix}_${timestamp}.webp")
            val webpOk = try {
                FileOutputStream(webpFile).use { output ->
                    scaled.compress(webpCompressFormat(), safeQuality, output)
                } && webpFile.exists() && webpFile.length() > 0L
            } catch (_: Throwable) {
                false
            }

            if (webpOk) {
                scaled.recycle()
                return webpFile
            }
            if (webpFile.exists()) webpFile.delete()

            val jpgFile = File(destDir, "${fileNamePrefix}_${timestamp}.jpg")
            val jpgOk = try {
                FileOutputStream(jpgFile).use { output ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, safeQuality, output)
                } && jpgFile.exists() && jpgFile.length() > 0L
            } catch (_: Throwable) {
                false
            }
            scaled.recycle()

            if (jpgOk) jpgFile else {
                if (jpgFile.exists()) jpgFile.delete()
                null
            }
        } catch (e: Throwable) {
            android.util.Log.e("KonabImageOptimizer", "Erreur compression image: ${e.message}", e)
            null
        } finally {
            try {
                tempCopy?.delete()
            } catch (_: Throwable) {}
        }
    }

    private fun copyUriToTempFile(context: Context, sourceUri: Uri, destFile: File): Boolean {
        val stream = try {
            context.contentResolver.openInputStream(sourceUri)
        } catch (_: Throwable) {
            null
        } ?: try {
            context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.createInputStream()
        } catch (_: Throwable) {
            null
        } ?: return false

        return stream.use { input ->
            FileOutputStream(destFile).use { output ->
                val bytesCopied = input.copyTo(output, bufferSize = 16 * 1024)
                output.flush()
                bytesCopied > 0L
            }
        }
    }

    private fun decodeSampledBitmapFromFile(file: File, maxDimPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val maxSide = maxOf(w, h)
                    if (w > 0 && h > 0 && maxSide > maxDimPx && maxDimPx > 0) {
                        val ratio = maxDimPx.toFloat() / maxSide.toFloat()
                        val targetW = (w * ratio).toInt().coerceAtLeast(1)
                        val targetH = (h * ratio).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
                if (decoded.width > 0 && decoded.height > 0) {
                    return decoded
                }
            } catch (t: Throwable) {
                android.util.Log.w("KonabImageOptimizer", "ImageDecoder fallback BitmapFactory: ${t.message}")
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
        var useRgb565 = false
        for (attempt in 0..3) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    return applyExifOrientationIfNeeded(file, bmp)
                }
            } catch (_: OutOfMemoryError) {
                sampleSize *= 2
                useRgb565 = true
            } catch (_: Throwable) {
                break
            }
        }
        return null
    }

    private fun applyExifOrientationIfNeeded(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Throwable) {
            bitmap
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
