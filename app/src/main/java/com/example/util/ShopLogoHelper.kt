package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.util.Base64
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.data.remote.supabase.SupabaseConfig
import com.example.ui.theme.KonabOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class PresetLogoItem(
    val key: String,
    val label: String,
    val category: String,
    val drawableRes: Int
)

object ShopLogoHelper {

    private fun resolveHttpClient(context: Context, overrideClient: OkHttpClient?): OkHttpClient {
        return overrideClient ?: KonabNetworkClient.getMediaClient(context)
    }

    val PRESET_LOGOS: List<PresetLogoItem> = listOf(
        PresetLogoItem(
            key = "drawable:ic_konab_logo",
            label = "Logo Officiel Konab POS",
            category = "Officiel",
            drawableRes = R.drawable.ic_konab_logo
        ),
        PresetLogoItem(
            key = "drawable:burkina_commerce_faso",
            label = "Commerce & Boutique Faso",
            category = "Boutique",
            drawableRes = R.drawable.burkina_commerce_faso
        ),
        PresetLogoItem(
            key = "drawable:burkina_ouaga_market",
            label = "Grand Marché & Étalage",
            category = "Marché",
            drawableRes = R.drawable.burkina_ouaga_market
        ),
        PresetLogoItem(
            key = "drawable:burkina_bobo_dioulasso",
            label = "Artisanat & Culture",
            category = "Artisanat",
            drawableRes = R.drawable.burkina_bobo_dioulasso
        ),
        PresetLogoItem(
            key = "drawable:img_prod_mode",
            label = "Mode & Prêt-à-Porter",
            category = "Mode",
            drawableRes = R.drawable.img_prod_mode
        ),
        PresetLogoItem(
            key = "drawable:img_prod_resto",
            label = "Restauration & Maquis",
            category = "Restaurant",
            drawableRes = R.drawable.img_prod_resto
        ),
        PresetLogoItem(
            key = "drawable:img_prod_epicerie",
            label = "Alimentation & Supérette",
            category = "Épicerie",
            drawableRes = R.drawable.img_prod_epicerie
        ),
        PresetLogoItem(
            key = "drawable:img_prod_quincaillerie",
            label = "Quincaillerie & Matériaux",
            category = "Outillage",
            drawableRes = R.drawable.img_prod_quincaillerie
        )
    )

    fun resolvePresetDrawable(uriOrKey: String?): Int? {
        if (uriOrKey.isNullOrBlank()) return null
        return when (uriOrKey.trim()) {
            "drawable:ic_konab_logo" -> R.drawable.ic_konab_logo
            "drawable:burkina_commerce_faso" -> R.drawable.burkina_commerce_faso
            "drawable:burkina_ouaga_market" -> R.drawable.burkina_ouaga_market
            "drawable:burkina_bobo_dioulasso" -> R.drawable.burkina_bobo_dioulasso
            "drawable:img_prod_mode" -> R.drawable.img_prod_mode
            "drawable:img_prod_resto" -> R.drawable.img_prod_resto
            "drawable:img_prod_epicerie" -> R.drawable.img_prod_epicerie
            "drawable:img_prod_quincaillerie" -> R.drawable.img_prod_quincaillerie
            else -> null
        }
    }

    /**
     * Normalizes raw logo strings across platforms:
     * - Strips null/undefined/blank sentinel strings
     * - Resolves relative Supabase Storage paths into full public HTTPS URLs
     */
    fun normalizeLogoUri(
        logoUri: String?,
        supabaseBaseUrl: String = SupabaseConfig.rawUrl
    ): String? {
        val clean = logoUri?.trim().orEmpty()
        if (clean.isBlank() ||
            clean.equals("null", ignoreCase = true) ||
            clean.equals("undefined", ignoreCase = true)
        ) {
            return null
        }
        val base = supabaseBaseUrl.trim().trimEnd('/')
        if (base.isNotBlank()) {
            if (clean.startsWith("/storage/v1/")) {
                return "$base$clean"
            }
            if (clean.startsWith("storage/v1/")) {
                return "$base/$clean"
            }
            if (clean.startsWith("shop-logos/") || clean.startsWith("shop-ads/")) {
                return "$base/storage/v1/object/public/$clean"
            }
        }
        return clean
    }

    fun isRemoteHttpUrl(uri: String?): Boolean {
        val clean = uri?.trim().orEmpty()
        return clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
    }

    fun isLocalUnpublishedLogoUri(logoUri: String?): Boolean {
        val clean = normalizeLogoUri(logoUri) ?: return false
        if (resolvePresetDrawable(clean) != null) return false
        if (isRemoteHttpUrl(clean) ||
            clean.startsWith("data:image/", ignoreCase = true) ||
            clean.startsWith("drawable:", ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    fun getLocalFileIfExists(logoUri: String?): File? {
        val clean = normalizeLogoUri(logoUri) ?: return null
        if (isRemoteHttpUrl(clean) ||
            clean.startsWith("data:image/", ignoreCase = true) ||
            clean.startsWith("drawable:", ignoreCase = true) ||
            clean.startsWith("content://", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val path = if (clean.startsWith("file://")) clean.removePrefix("file://") else clean
            val f = File(path)
            if (f.exists() && f.isFile && f.length() > 0L) f else null
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureLogosDir(context: Context): File {
        val dir = File(context.filesDir, "logos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun hashUrlKey(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }

    fun getCachedLogoFile(context: Context, remoteUrl: String): File {
        val dir = ensureLogosDir(context)
        return File(dir, "cloud_cache_${hashUrlKey(remoteUrl.trim())}.jpg")
    }

    /**
     * Immediately seeds the persistent offline cache for [remoteUrl] from [sourceFile]
     * right after uploading so the image renders offline immediately.
     */
    fun seedCachedLogoFromFile(context: Context, remoteUrl: String, sourceFile: File) {
        if (!isRemoteHttpUrl(remoteUrl) || !sourceFile.exists() || sourceFile.length() == 0L) return
        try {
            val dest = getCachedLogoFile(context, remoteUrl)
            sourceFile.copyTo(dest, overwrite = true)
        } catch (_: Exception) {}
    }

    /**
     * Downloads and validates a remote logo image into persistent app storage.
     * Returns the cached File if available (or freshly fetched), or null if unreachable/invalid.
     */
    fun cacheRemoteLogoBlocking(
        context: Context,
        remoteUrl: String,
        httpClient: OkHttpClient? = null
    ): File? {
        val normalized = normalizeLogoUri(remoteUrl) ?: return null
        if (!isRemoteHttpUrl(normalized)) return null
        val cacheFile = getCachedLogoFile(context, normalized)
        if (cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L) {
            return cacheFile
        }
        val activeClient = resolveHttpClient(context, httpClient)
        return try {
            val req = Request.Builder().url(normalized).get().build()
            activeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val contentType = resp.header("Content-Type")?.trim().orEmpty()
                if (contentType.isNotBlank() &&
                    !contentType.startsWith("image/", ignoreCase = true) &&
                    !contentType.startsWith("application/octet-stream", ignoreCase = true)
                ) {
                    return null
                }
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                val headerPreview = String(bytes, 0, minOf(bytes.size, 32), Charsets.UTF_8).trimStart()
                if (headerPreview.startsWith("<") || headerPreview.startsWith("{") || headerPreview.startsWith("[")) {
                    return null
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                FileOutputStream(tmp).use { it.write(bytes) }
                if (cacheFile.exists()) cacheFile.delete()
                tmp.renameTo(cacheFile)
                if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapKeepRatio(bmp: Bitmap, maxDim: Int): Bitmap {
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide <= maxDim || maxSide <= 0) return bmp
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }

    /**
     * Copies & compresses the image selected via Android Photo Picker to internal app files
     * to guarantee 100% offline persistence AND fast cross-device Cloud upload.
     * Preserves `cloud_cache_*` entries used by other connected shops.
     */
    fun saveLogoToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val logosDir = ensureLogosDir(context)
            logosDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("shop_logo_")) {
                    file.delete()
                }
            }

            val webpFile = KonabImageOptimizer.compressUriToWebpFile(
                context = context,
                sourceUri = sourceUri,
                destDir = logosDir,
                fileNamePrefix = "shop_logo",
                maxDimPx = 384,
                quality = 78
            )
            if (webpFile != null) {
                return Uri.fromFile(webpFile).toString()
            }

            val destFile = File(logosDir, "shop_logo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            android.util.Log.e("ShopLogoHelper", "Erreur sauvegarde logo: ${e.message}", e)
            null
        }
    }

    /**
     * Returns a compressed local JPEG File ready for Supabase Storage upload, or null if not local.
     */
    fun getUploadableLogoFile(context: Context, logoUri: String?): File? {
        val clean = normalizeLogoUri(logoUri) ?: return null
        if (!isLocalUnpublishedLogoUri(clean)) return null
        return try {
            if (clean.startsWith("content://")) {
                val uri = Uri.parse(clean)
                val saved = saveLogoToInternalStorage(context, uri) ?: return null
                getLocalFileIfExists(saved)
            } else {
                getLocalFileIfExists(clean)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encodes a local logo file/URI into a compact base64 Data URI (`data:image/jpeg;base64,...`)
     * so shop logos remain 100% portable across devices, platforms, and JSON backups even if
     * cloud bucket storage is unavailable.
     */
    fun encodeLogoToPortableDataUri(
        context: Context,
        logoUri: String?,
        includeRemoteCached: Boolean = false
    ): String? {
        val clean = normalizeLogoUri(logoUri) ?: return null
        if (clean.startsWith("data:image/", ignoreCase = true)) return clean
        val isLocal = isLocalUnpublishedLogoUri(clean)
        val isRemote = isRemoteHttpUrl(clean)
        if (!isLocal && !(includeRemoteCached && isRemote)) return null
        return try {
            val bmp = loadLogoBitmap(context, clean) ?: return null
            val webpUri = KonabImageOptimizer.compressBitmapToWebpDataUri(bmp, maxDimPx = 256, quality = 74)
            bmp.recycle()
            webpUri
        } catch (_: Exception) {
            null
        }
    }

    fun decodeDataUriBitmap(dataUri: String?): Bitmap? {
        val clean = dataUri?.trim().orEmpty()
        if (!clean.startsWith("data:image/", ignoreCase = true)) return null
        val commaIdx = clean.indexOf(',')
        if (commaIdx < 0 || commaIdx >= clean.length - 1) return null
        return try {
            val bytes = Base64.decode(clean.substring(commaIdx + 1), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a Bitmap from preset, base64 data URI, local file, or remote HTTPS URL (using
     * persistent disk cache with network download when off main thread) for thermal/PDF receipts.
     */
    fun loadLogoBitmap(context: Context, logoUri: String?): Bitmap? {
        val clean = normalizeLogoUri(logoUri) ?: return null
        return try {
            val presetRes = resolvePresetDrawable(clean)
            if (presetRes != null) {
                return BitmapFactory.decodeResource(context.resources, presetRes)
            }
            if (clean.startsWith("data:image/", ignoreCase = true)) {
                return decodeDataUriBitmap(clean)
            }
            if (isRemoteHttpUrl(clean)) {
                val existingCache = getCachedLogoFile(context, clean).takeIf { it.exists() && it.length() > 0L }
                val targetFile = existingCache ?: if (Looper.myLooper() != Looper.getMainLooper()) {
                    cacheRemoteLogoBlocking(context, clean)
                } else null
                return targetFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
            if (clean.startsWith("content://")) {
                val uri = Uri.parse(clean)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                getLocalFileIfExists(clean)?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadLogoBitmapOrFallback(
        context: Context,
        logoUri: String?,
        @DrawableRes fallbackRes: Int = R.drawable.ic_konab_logo
    ): Bitmap? {
        return loadLogoBitmap(context, logoUri)
            ?: try {
                BitmapFactory.decodeResource(context.resources, fallbackRes)
            } catch (_: Exception) {
                null
            }
    }
}

@Composable
private fun FallbackShopLogoContent(
    @DrawableRes fallbackRes: Int,
    contentDescription: String?
) {
    Image(
        painter = painterResource(id = fallbackRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    )
}

/**
 * Reusable Shop Logo Composable with support for custom gallery photos,
 * cloud HTTPS URLs, persistent offline cloud cache, portable Base64 Data URIs,
 * preset drawables, and clean fallback rendering whenever an image URL is unreachable.
 */
@Composable
fun ShopLogoAvatar(
    logoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = RoundedCornerShape(18.dp),
    contentDescription: String? = "Logo de la boutique",
    onClick: (() -> Unit)? = null,
    showEditBadge: Boolean = false,
    @DrawableRes fallbackDrawableRes: Int = R.drawable.ic_konab_logo
) {
    val context = LocalContext.current
    val normalizedUri = remember(logoUri) { ShopLogoHelper.normalizeLogoUri(logoUri) }

    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = shape,
            color = Color.White,
            shadowElevation = 2.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxSize()
        ) {
            val presetRes = remember(normalizedUri) { ShopLogoHelper.resolvePresetDrawable(normalizedUri) }
            val dataUriBitmap = remember(normalizedUri) {
                if (normalizedUri != null && normalizedUri.startsWith("data:image/", ignoreCase = true)) {
                    ShopLogoHelper.decodeDataUriBitmap(normalizedUri)?.asImageBitmap()
                } else null
            }
            val localFile = remember(normalizedUri) { ShopLogoHelper.getLocalFileIfExists(normalizedUri) }
            val isRemote = remember(normalizedUri) { ShopLogoHelper.isRemoteHttpUrl(normalizedUri) }
            val isContentUri = remember(normalizedUri) {
                normalizedUri?.startsWith("content://", ignoreCase = true) == true
            }

            when {
                presetRes != null -> {
                    Image(
                        painter = painterResource(id = presetRes),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
                }
                dataUriBitmap != null -> {
                    Image(
                        bitmap = dataUriBitmap,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isRemote && normalizedUri != null -> {
                    var remoteFailed by remember(normalizedUri) { mutableStateOf(false) }
                    var cacheFailed by remember(normalizedUri) { mutableStateOf(false) }
                    var remoteSucceeded by remember(normalizedUri) { mutableStateOf(false) }
                    val cachedFile = remember(normalizedUri) {
                        ShopLogoHelper.getCachedLogoFile(context, normalizedUri)
                            .takeIf { it.exists() && it.length() > 0L }
                    }

                    LaunchedEffect(remoteSucceeded, normalizedUri) {
                        if (remoteSucceeded && !ShopLogoHelper.getCachedLogoFile(context, normalizedUri).exists()) {
                            withContext(Dispatchers.IO) {
                                ShopLogoHelper.cacheRemoteLogoBlocking(context, normalizedUri)
                            }
                        }
                    }

                    val targetModel: Any? = when {
                        cachedFile != null && !cacheFailed -> cachedFile
                        !remoteFailed -> normalizedUri
                        else -> null
                    }

                    if (targetModel != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            var isLoading by remember(targetModel) { mutableStateOf(true) }
                            val request = remember(targetModel, context) {
                                ImageRequest.Builder(context)
                                    .data(targetModel)
                                    .size(256, 256)
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = contentDescription,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onLoading = { isLoading = true },
                                onSuccess = {
                                    isLoading = false
                                    if (!remoteFailed) {
                                        remoteSucceeded = true
                                    }
                                },
                                onError = {
                                    isLoading = false
                                    if (!remoteFailed) {
                                        remoteFailed = true
                                    } else {
                                        cacheFailed = true
                                    }
                                }
                            )
                            if (isLoading && !remoteFailed && cachedFile != null) {
                                val cachedBmp = remember(cachedFile.absolutePath) {
                                    BitmapFactory.decodeFile(cachedFile.absolutePath)?.asImageBitmap()
                                }
                                if (cachedBmp != null) {
                                    Image(
                                        bitmap = cachedBmp,
                                        contentDescription = contentDescription,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                                }
                            } else if (isLoading) {
                                FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                            }
                        }
                    } else {
                        FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                    }
                }
                localFile != null -> {
                    var localFailed by remember(localFile.absolutePath) { mutableStateOf(false) }
                    if (!localFailed) {
                        AsyncImage(
                            model = localFile,
                            contentDescription = contentDescription,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { localFailed = true }
                        )
                    } else {
                        FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                    }
                }
                isContentUri && normalizedUri != null -> {
                    var contentFailed by remember(normalizedUri) { mutableStateOf(false) }
                    if (!contentFailed) {
                        AsyncImage(
                            model = normalizedUri,
                            contentDescription = contentDescription,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { contentFailed = true }
                        )
                    } else {
                        FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                    }
                }
                else -> {
                    FallbackShopLogoContent(fallbackDrawableRes, contentDescription)
                }
            }
        }

        if (showEditBadge) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(CircleShape)
                    .background(KonabOrange)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Changer le logo",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
