package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.KonabOrange
import java.io.File
import java.io.FileOutputStream

data class PresetLogoItem(
    val key: String,
    val label: String,
    val category: String,
    val drawableRes: Int
)

object ShopLogoHelper {

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
     * Copies the image selected via Android Photo Picker to internal app files
     * to guarantee 100% offline persistence across reboots.
     */
    fun saveLogoToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val logosDir = File(context.filesDir, "logos")
            if (!logosDir.exists()) {
                logosDir.mkdirs()
            }
            // Clear any old logos
            logosDir.listFiles()?.forEach { it.delete() }

            val destFile = File(logosDir, "shop_logo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            android.util.Log.e("ShopLogoHelper", "Erreur sauvegarde logo: ${e.message}", e)
            null
        }
    }

    /**
     * Loads a Bitmap from preset or local file for thermal receipt printing.
     */
    fun loadLogoBitmap(context: Context, logoUri: String?): Bitmap? {
        if (logoUri.isNullOrBlank()) return null
        return try {
            val presetRes = resolvePresetDrawable(logoUri)
            if (presetRes != null) {
                return BitmapFactory.decodeResource(context.resources, presetRes)
            }
            if (logoUri.startsWith("file://")) {
                BitmapFactory.decodeFile(logoUri.removePrefix("file://"))
            } else if (logoUri.startsWith("content://")) {
                val uri = Uri.parse(logoUri)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeFile(logoUri)
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Reusable Shop Logo Composable with support for custom gallery photos,
 * preset drawables, and default Konab POS fallback.
 */
@Composable
fun ShopLogoAvatar(
    logoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    shape: Shape = RoundedCornerShape(18.dp),
    contentDescription: String? = "Logo de la boutique",
    onClick: (() -> Unit)? = null,
    showEditBadge: Boolean = false
) {
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
            val presetRes = ShopLogoHelper.resolvePresetDrawable(logoUri)
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
                !logoUri.isNullOrBlank() -> {
                    AsyncImage(
                        model = logoUri,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(id = R.drawable.ic_konab_logo),
                        placeholder = painterResource(id = R.drawable.ic_konab_logo)
                    )
                }
                else -> {
                    Image(
                        painter = painterResource(id = R.drawable.ic_konab_logo),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp)
                    )
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
