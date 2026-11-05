package com.example.util

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entity.ProductEntity

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
            // Hardware, tools, construction, electric (Checked before generic items)
            listOf("quincaillerie", "outil", "outillage", "ciment", "ampoule", "robinet", "marteau", "mètre", "pointe", "plomberie", "électricité", "vis", "bâtiment", "bricolage").any { combined.contains(it) } -> {
                R.drawable.img_prod_quincaillerie
            }

            // Drinks & Refreshments (ensuring 'eau' matches as an isolated word only)
            words.contains("eau") || listOf("boisson", "jus", "bissap", "café", "thé", "bière", "soda", "gingembre", "gnamakoudji", "cocktail", "bouteille", "canette").any { combined.contains(it) } -> {
                R.drawable.img_prod_boisson
            }

            // Food & Restaurant & Snacks
            listOf("plat", "grillade", "poulet", "alloco", "tô", "brochette", "omelette", "pain", "sandwich", "riz gras", "restaurant", "boeuf", "viande", "repas", "beignet").any { combined.contains(it) } -> {
                R.drawable.img_prod_resto
            }

            // Fashion, clothes, shoes, accessories
            listOf("vêtement", "chaussure", "mode", "t-shirt", "chemise", "pantalon", "jeans", "robe", "pagne", "danfani", "sac", "accessoire", "casquette", "ceinture", "montre", "mèche", "coiffure", "tresse").any { combined.contains(it) } -> {
                R.drawable.img_prod_mode
            }


            // Food grocery & market
            listOf("alimentation", "épicerie", "riz", "huile", "savon", "sucre", "lait", "pâtes", "farine", "sel", "tomate", "magasin").any { combined.contains(it) } -> {
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
        val uri = product.imageUri.trim()
        val isExternalUri = uri.startsWith("content://") || 
                            uri.startsWith("http://") || 
                            uri.startsWith("https://") || 
                            uri.startsWith("file://")

        if (isExternalUri) {
            AsyncImage(
                model = uri,
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
