package com.example.util

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.supabase.SupabaseConfig
import java.io.File
import java.io.FileOutputStream

data class ProductPresetImage(
    val key: String,
    val label: String,
    val category: String,
    val drawableRes: Int
)

object ProductImageHelper {

    val PRESET_IMAGES: List<ProductPresetImage> = listOf(
        ProductPresetImage(
            key = "img_prod_mode",
            label = "Mode & Vêtements",
            category = "Mode",
            drawableRes = R.drawable.img_prod_mode
        ),
        ProductPresetImage(
            key = "img_prod_epicerie",
            label = "Épicerie & Alimentation",
            category = "Alimentation",
            drawableRes = R.drawable.img_prod_epicerie
        ),
        ProductPresetImage(
            key = "img_prod_boisson",
            label = "Boissons & Jus Frais",
            category = "Boissons",
            drawableRes = R.drawable.img_prod_boisson
        ),
        ProductPresetImage(
            key = "img_prod_resto",
            label = "Restauration & Plats",
            category = "Restauration",
            drawableRes = R.drawable.img_prod_resto
        ),
        ProductPresetImage(
            key = "img_prod_quincaillerie",
            label = "Quincaillerie & Outils",
            category = "Quincaillerie",
            drawableRes = R.drawable.img_prod_quincaillerie
        ),
        ProductPresetImage(
            key = "burkina_ouaga_market",
            label = "Grand Marché & Étalage",
            category = "Commerce",
            drawableRes = R.drawable.burkina_ouaga_market
        ),
        ProductPresetImage(
            key = "burkina_commerce_faso",
            label = "Boutique & Vente",
            category = "Boutique",
            drawableRes = R.drawable.burkina_commerce_faso
        ),
        ProductPresetImage(
            key = "burkina_bobo_dioulasso",
            label = "Artisanat & Tradition",
            category = "Artisanat",
            drawableRes = R.drawable.burkina_bobo_dioulasso
        )
    )

    fun resolveDrawableRes(keyOrUri: String?): Int? {
        if (keyOrUri.isNullOrBlank()) return null
        val cleanKey = keyOrUri.removePrefix("drawable:").trim()
        return when (cleanKey) {
            "img_prod_mode" -> R.drawable.img_prod_mode
            "img_prod_epicerie" -> R.drawable.img_prod_epicerie
            "img_prod_boisson" -> R.drawable.img_prod_boisson
            "img_prod_resto" -> R.drawable.img_prod_resto
            "img_prod_quincaillerie" -> R.drawable.img_prod_quincaillerie
            "burkina_ouaga_market" -> R.drawable.burkina_ouaga_market
            "burkina_commerce_faso" -> R.drawable.burkina_commerce_faso
            "burkina_bobo_dioulasso" -> R.drawable.burkina_bobo_dioulasso
            else -> null
        }
    }

    fun normalizeProductImageUri(
        imageUri: String?,
        supabaseBaseUrl: String = SupabaseConfig.rawUrl
    ): String {
        val clean = imageUri?.trim().orEmpty()
        if (clean.isBlank() ||
            clean.equals("null", ignoreCase = true) ||
            clean.equals("undefined", ignoreCase = true)
        ) {
            return ""
        }
        val base = supabaseBaseUrl.trim().trimEnd('/')
        if (base.isNotBlank()) {
            if (clean.startsWith("/storage/v1/")) return "$base$clean"
            if (clean.startsWith("storage/v1/")) return "$base/$clean"
            if (clean.startsWith("product-images/") ||
                clean.startsWith("shop-logos/") ||
                clean.startsWith("shop-ads/")
            ) {
                return "$base/storage/v1/object/public/$clean"
            }
        }
        return clean
    }

    fun isLocalUnpublishedProductImageUri(uri: String?): Boolean {
        val clean = normalizeProductImageUri(uri)
        if (clean.isBlank()) return false
        if (resolveDrawableRes(clean) != null) return false
        if (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("data:image/", ignoreCase = true) ||
            clean.startsWith("drawable:", ignoreCase = true)
        ) {
            return false
        }
        return clean.startsWith("content://", ignoreCase = true) ||
            clean.startsWith("file://", ignoreCase = true) ||
            clean.startsWith("/")
    }

    private fun ensureProductImagesDir(context: Context): File {
        val dir = File(context.filesDir, "product_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLocalFileIfExists(uri: String?): File? {
        val clean = uri?.trim().orEmpty()
        if (clean.isBlank() ||
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
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

    fun saveProductImageToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val dir = ensureProductImagesDir(context)
            val webpFile = KonabImageOptimizer.compressUriToWebpFile(
                context = context,
                sourceUri = sourceUri,
                destDir = dir,
                fileNamePrefix = "prod",
                maxDimPx = 720,
                quality = 80
            )
            if (webpFile != null && webpFile.exists() && webpFile.length() > 0L) {
                return Uri.fromFile(webpFile).toString()
            }
            val destFile = File(dir, "prod_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (destFile.exists() && destFile.length() > 0L) Uri.fromFile(destFile).toString() else null
        } catch (e: Exception) {
            android.util.Log.e("ProductImageHelper", "Erreur sauvegarde photo produit: ${e.message}", e)
            null
        }
    }

    fun getUploadableProductImageFile(context: Context, imageUri: String?): File? {
        val clean = normalizeProductImageUri(imageUri)
        if (!isLocalUnpublishedProductImageUri(clean)) return null
        return try {
            if (clean.startsWith("content://", ignoreCase = true)) {
                val saved = saveProductImageToInternalStorage(context, Uri.parse(clean)) ?: return null
                getLocalFileIfExists(saved)
            } else {
                getLocalFileIfExists(clean)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds the most relevant high-resolution image for any product
     * based on its category, name, or unit.
     */
    fun getFallbackDrawableForProduct(name: String, category: String): Int {
        val lowerName = name.lowercase()
        val lowerCat = category.lowercase()
        val combined = "$lowerName $lowerCat"
        val words = combined.split("[\\s,.-]+".toRegex()).filter { it.isNotBlank() }

        return when {
            // Hardware, tools, construction, electric, tech, automotive, car wash
            listOf("quincaillerie", "outil", "outillage", "ciment", "ampoule", "robinet", "marteau", "mètre", "pointe", "plomberie", "électricité", "vis", "bâtiment", "bricolage", "smartphone", "téléphone", "écran", "chargeur", "câble", "informatique", "clé usb", "lavage", "karcher", "moto", "voiture", "pneu", "huile moteur", "vidange", "frein").any { combined.contains(it) } -> {
                R.drawable.img_prod_quincaillerie
            }

            // Drinks & Refreshments (ensuring 'eau' matches as an isolated word only)
            words.contains("eau") || listOf("boisson", "jus", "bissap", "café", "thé", "bière", "soda", "gingembre", "gnamakoudji", "cocktail", "bouteille", "canette").any { combined.contains(it) } -> {
                R.drawable.img_prod_boisson
            }

            // Food & Restaurant & Bakery & Butchery & Fish
            listOf("plat", "grillade", "poulet", "alloco", "tô", "brochette", "omelette", "pain", "sandwich", "riz gras", "restaurant", "boeuf", "viande", "repas", "beignet", "baguette", "croissant", "gâteau", "boulangerie", "pâtisserie", "boucherie", "mouton", "saucisse", "poisson", "carpe", "capitaine", "tilapia", "crevette", "chinchard").any { combined.contains(it) } -> {
                R.drawable.img_prod_resto
            }

            // Fashion, clothes, shoes, tailoring, pressing, jewelry
            listOf("vêtement", "chaussure", "mode", "t-shirt", "chemise", "pantalon", "jeans", "robe", "pagne", "danfani", "sac", "accessoire", "casquette", "ceinture", "montre", "mèche", "coiffure", "tresse", "couture", "boubou", "bazin", "mercerie", "costume", "pressing", "couette", "drap", "bijou", "bague", "chaîne", "collier", "or 18k", "argent 925").any { combined.contains(it) } -> {
                R.drawable.img_prod_mode
            }

            // Food grocery, agro-pastoral, stationery, library
            listOf("alimentation", "épicerie", "riz", "huile", "savon", "sucre", "lait", "pâtes", "farine", "sel", "tomate", "magasin", "cahier", "stylo", "rame", "livre", "papeterie", "bureautique", "copie", "provende", "tourteau", "engrais", "maïs", "bétail", "poussin").any { combined.contains(it) } -> {
                R.drawable.img_prod_epicerie
            }

            // Pharmacy & health
            listOf("pharmacie", "médicament", "paracétamol", "vitamine", "alcool", "pansement", "santé", "soin", "antalgique").any { combined.contains(it) } -> {
                R.drawable.img_prod_epicerie
            }

            else -> R.drawable.img_prod_epicerie
        }
    }
}

/**
 * High-performance, visually polished Composable displaying the product image.
 * Supports:
 * - Local gallery / camera images via Photo Picker (content:// uri)
 * - Remote image URLs (http/https)
 * - High-res generated drawables (img_prod_*)
 * - Contextual intelligent fallback image per product category
 */
@Composable
fun ProductThumbnail(
    product: ProductEntity,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    val fallbackRes = ProductImageHelper.getFallbackDrawableForProduct(product.name, product.category)
    val customDrawable = ProductImageHelper.resolveDrawableRes(product.imageUri)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val uri = ProductImageHelper.normalizeProductImageUri(product.imageUri)
        val isExternalUri = uri.startsWith("content://") ||
                            uri.startsWith("http://") ||
                            uri.startsWith("https://") ||
                            uri.startsWith("file://") ||
                            uri.startsWith("/")

        if (isExternalUri) {
            val context = LocalContext.current
            val request = androidx.compose.runtime.remember(uri, context) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(320, 320)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                error = painterResource(id = fallbackRes),
                placeholder = painterResource(id = fallbackRes)
            )
        } else if (customDrawable != null) {
            Image(
                painter = painterResource(id = customDrawable),
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Image(
                painter = painterResource(id = fallbackRes),
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}
