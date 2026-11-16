package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.OutfitFontFamily
import com.example.util.AgentIdCardHelper
import com.example.util.QrCodeHelper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class CardActivityTheme(
    val title: String,
    val shortBadge: String,
    val startColor: Color,
    val midColor: Color,
    val endColor: Color,
    val accentColor: Color,
    val startHex: String,
    val midHex: String,
    val endHex: String,
    val accentHex: String
) {
    UTILISATEUR(
        title = "Carte Utilisateur",
        shortBadge = "UTILISATEUR",
        startColor = Color(0xFF0F172A),
        midColor = Color(0xFF1E3A8A),
        endColor = Color(0xFFEA580C),
        accentColor = Color(0xFFF59E0B),
        startHex = "#0F172A",
        midHex = "#1E3A8A",
        endHex = "#EA580C",
        accentHex = "#F59E0B"
    ),
    RESTAURANT_CANTINE(
        title = "Restaurants & Cantines",
        shortBadge = "CANTINE / RESTO",
        startColor = Color(0xFF7C2D12),
        midColor = Color(0xFFEA580C),
        endColor = Color(0xFFD97706),
        accentColor = Color(0xFFFDE68A),
        startHex = "#7C2D12",
        midHex = "#EA580C",
        endHex = "#D97706",
        accentHex = "#FDE68A"
    ),
    COUTURE(
        title = "Atelier Couture",
        shortBadge = "COUTURE",
        startColor = Color(0xFF3B0764),
        midColor = Color(0xFF6D28D9),
        endColor = Color(0xFFB45309),
        accentColor = Color(0xFFFCD34D),
        startHex = "#3B0764",
        midHex = "#6D28D9",
        endHex = "#B45309",
        accentHex = "#FCD34D"
    ),
    SALUBRITE_POUBELLES(
        title = "Salubrité & Poubelles",
        shortBadge = "POUBELLES PRO",
        startColor = Color(0xFF064E3B),
        midColor = Color(0xFF047857),
        endColor = Color(0xFF0EA5E9),
        accentColor = Color(0xFF6EE7B7),
        startHex = "#064E3B",
        midHex = "#047857",
        endHex = "#0EA5E9",
        accentHex = "#6EE7B7"
    ),
    TONTINE(
        title = "Tontine 30 Jours",
        shortBadge = "TONTINE 30J",
        startColor = Color(0xFF78350F),
        midColor = Color(0xFFB45309),
        endColor = Color(0xFFDC2626),
        accentColor = Color(0xFFFDE047),
        startHex = "#78350F",
        midHex = "#B45309",
        endHex = "#DC2626",
        accentHex = "#FDE047"
    ),
    BOUTIQUE(
        title = "Commerce & Boutique",
        shortBadge = "BOUTIQUE",
        startColor = Color(0xFF06245E),
        midColor = Color(0xFF0F4C81),
        endColor = Color(0xFF0284C7),
        accentColor = Color(0xFF38BDF8),
        startHex = "#06245E",
        midHex = "#0F4C81",
        endHex = "#0284C7",
        accentHex = "#38BDF8"
    );

    companion object {
        fun fromRaw(raw: String?): CardActivityTheme {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UTILISATEUR
        }
    }
}

/**
 * Carte d'identité numérique premium KONAB POS • Conakry Web
 * avec photo du titulaire, QR code scannable, logo officiel et fond thématique selon l'activité.
 */
@Composable
fun ActivityDigitalCard(
    theme: CardActivityTheme,
    holderName: String,
    codeId: String,
    organizationTitle: String,
    phone: String,
    zoneOrAddress: String,
    qrPayload: String,
    photoUri: String?,
    statusBadge: String = "",
    statsLine: String = "",
    onPhotoSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val qrBitmap = remember(qrPayload) {
        QrCodeHelper.generateQrBitmap(qrPayload.ifBlank { "konabpos://card/$codeId" }, sizePx = 320)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && onPhotoSelected != null) {
            val saved = AgentIdCardHelper.saveAgentPhotoToInternalStorage(
                context = context,
                sourceUri = uri,
                agentKey = "card_${codeId.ifBlank { "usr" }}"
            )
            if (saved != null) {
                onPhotoSelected(saved)
                Toast.makeText(context, "Photo ajoutée sur la carte !", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Impossible de charger l'image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_activity_digital_${theme.name}"),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, theme.accentColor.copy(alpha = 0.55f)),
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(theme.startColor, theme.midColor, theme.endColor)
                    )
                )
        ) {
            // Fond décoratif unique par activité
            Canvas(modifier = Modifier.matchParentSize()) {
                when (theme) {
                    CardActivityTheme.SALUBRITE_POUBELLES -> {
                        // Cercles concentriques éco + vagues diagonales
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f),
                            radius = size.height * 0.65f,
                            center = Offset(size.width * 0.9f, size.height * 0.15f)
                        )
                        drawCircle(
                            color = theme.accentColor.copy(alpha = 0.12f),
                            radius = size.height * 0.42f,
                            center = Offset(size.width * 0.12f, size.height * 0.92f)
                        )
                    }
                    CardActivityTheme.TONTINE -> {
                        // Anneaux soleil dorés symbolisant la solidarité
                        for (i in 1..3) {
                            drawCircle(
                                color = theme.accentColor.copy(alpha = 0.09f),
                                radius = size.height * (0.28f * i),
                                center = Offset(size.width * 0.88f, size.height * 0.25f),
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                    CardActivityTheme.COUTURE -> {
                        // Motif losanges / métier à tisser
                        val step = 46f
                        var x = 0f
                        while (x < size.width) {
                            drawLine(
                                color = theme.accentColor.copy(alpha = 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x + size.height * 0.45f, size.height),
                                strokeWidth = 1.5f
                            )
                            x += step
                        }
                    }
                    CardActivityTheme.RESTAURANT_CANTINE -> {
                        // Arcs chaleureux
                        drawCircle(
                            color = Color.White.copy(alpha = 0.09f),
                            radius = size.height * 0.55f,
                            center = Offset(size.width * 0.85f, size.height * 0.85f)
                        )
                        drawCircle(
                            color = theme.accentColor.copy(alpha = 0.14f),
                            radius = size.height * 0.25f,
                            center = Offset(size.width * 0.85f, size.height * 0.85f)
                        )
                    }
                    CardActivityTheme.UTILISATEUR, CardActivityTheme.BOUTIQUE -> {
                        // Lignes orbitales high-tech Conakry Web
                        drawCircle(
                            color = Color.White.copy(alpha = 0.07f),
                            radius = size.height * 0.72f,
                            center = Offset(size.width * 0.95f, size.height * 0.1f),
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = theme.accentColor.copy(alpha = 0.10f),
                            radius = size.height * 0.48f,
                            center = Offset(size.width * 0.08f, size.height * 0.9f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // En-tête : Logo Conakry Web / Konab POS + Badge Activité
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.5.dp, theme.accentColor),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_konab_logo),
                                contentDescription = "Logo Conakry Web • Konab POS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CONAKRY WEB • KONAB POS",
                                color = Color.White,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = theme.title,
                                color = theme.accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = theme.shortBadge,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Corps central : Photo + Informations + QR Code scannable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo du titulaire
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .then(
                                    if (onPhotoSelected != null) {
                                        Modifier.clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AgentPhotoAvatar(
                                name = holderName.ifBlank { "K" },
                                photoUri = photoUri?.ifBlank { null },
                                size = 66.dp,
                                shape = RoundedCornerShape(16.dp),
                                borderColor = theme.accentColor,
                                showEditBadge = onPhotoSelected != null
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.35f)
                        ) {
                            Text(
                                text = codeId.take(12),
                                color = theme.accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Identité & activité
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = holderName.ifBlank { "Titulaire Konab" },
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (organizationTitle.isNotBlank()) {
                            Text(
                                text = organizationTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (phone.isNotBlank()) {
                            Text(
                                text = "Tél : $phone",
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (zoneOrAddress.isNotBlank()) {
                            Text(
                                text = zoneOrAddress,
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (statsLine.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = statsLine,
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // QR Code haute lisibilité
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, theme.accentColor),
                        modifier = Modifier.size(82.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code carte $holderName",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Pied de carte avec vérification et actions rapides
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusBadge.ifBlank { "Carte Officielle Vérifiée" },
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "conakryweb • konabpos",
                        color = theme.accentColor.copy(alpha = 0.9f),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Barre d'actions PNG (Télécharger dans la galerie / Partager sur WhatsApp) pour ActivityDigitalCard.
 */
@Composable
fun ActivityCardExportBar(
    theme: CardActivityTheme,
    holderName: String,
    codeId: String,
    organizationTitle: String,
    phone: String,
    zoneOrAddress: String,
    qrPayload: String,
    photoUri: String?,
    statusBadge: String = "",
    statsLine: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = {
                val bmp = ActivityCardBitmapHelper.renderCardBitmap(
                    context = context,
                    theme = theme,
                    holderName = holderName,
                    codeId = codeId,
                    organizationTitle = organizationTitle,
                    phone = phone,
                    zoneOrAddress = zoneOrAddress,
                    qrPayload = qrPayload,
                    photoUri = photoUri,
                    statusBadge = statusBadge,
                    statsLine = statsLine
                )
                ActivityCardBitmapHelper.downloadCardPng(context, bmp, "Carte_${theme.name}_$codeId")
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_download_activity_card")
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Télécharger", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Button(
            onClick = {
                val bmp = ActivityCardBitmapHelper.renderCardBitmap(
                    context = context,
                    theme = theme,
                    holderName = holderName,
                    codeId = codeId,
                    organizationTitle = organizationTitle,
                    phone = phone,
                    zoneOrAddress = zoneOrAddress,
                    qrPayload = qrPayload,
                    photoUri = photoUri,
                    statusBadge = statusBadge,
                    statsLine = statsLine
                )
                ActivityCardBitmapHelper.shareCardPng(context, bmp, holderName, theme)
            },
            colors = ButtonDefaults.buttonColors(containerColor = theme.midColor),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("btn_share_activity_card")
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Partager carte", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Sélecteur photo compact réutilisable pour membre tontine / famille poubelle / utilisateur public.
 */
@Composable
fun ActivityPhotoPickerRow(
    title: String,
    subtitle: String,
    holderName: String,
    photoUri: String,
    keyHint: String,
    accentColor: Color = KonabGreen,
    onPhotoSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = AgentIdCardHelper.saveAgentPhotoToInternalStorage(context, uri, keyHint)
            if (saved != null) {
                onPhotoSelected(saved)
                Toast.makeText(context, "Photo sélectionnée !", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Impossible de charger l'image.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val launchPicker = {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { launchPicker() }
            .testTag("btn_pick_activity_photo_$keyHint"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (photoUri.isNotBlank()) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AgentPhotoAvatar(
                name = holderName.ifBlank { "K" },
                photoUri = photoUri.ifBlank { null },
                size = 50.dp,
                shape = RoundedCornerShape(12.dp),
                borderColor = accentColor,
                showEditBadge = true
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (photoUri.isBlank()) title else "$title ✓",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = if (photoUri.isNotBlank()) accentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = launchPicker,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 42.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (photoUri.isBlank()) "Photo" else "Changer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

object ActivityCardBitmapHelper {
    const val WIDTH = 1011
    const val HEIGHT = 638

    fun renderCardBitmap(
        context: Context,
        theme: CardActivityTheme,
        holderName: String,
        codeId: String,
        organizationTitle: String,
        phone: String,
        zoneOrAddress: String,
        qrPayload: String,
        photoUri: String?,
        statusBadge: String = "",
        statsLine: String = ""
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)

        val cornerRadius = 38f
        val bgRect = RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                HEIGHT.toFloat(),
                intArrayOf(
                    AndroidColor.parseColor(theme.startHex),
                    AndroidColor.parseColor(theme.midHex),
                    AndroidColor.parseColor(theme.endHex)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

        // Cercles & motifs décoratifs selon l'activité
        val decorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            alpha = 24
        }
        canvas.drawCircle(WIDTH * 0.9f, HEIGHT * 0.14f, 220f, decorPaint)
        val accentTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
            alpha = 32
        }
        canvas.drawCircle(WIDTH * 0.12f, HEIGHT * 0.92f, 165f, accentTintPaint)

        // Bordure extérieure accentuée
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = AndroidColor.parseColor(theme.accentHex)
            alpha = 150
        }
        canvas.drawRoundRect(RectF(6f, 6f, WIDTH - 6f, HEIGHT - 6f), cornerRadius, cornerRadius, borderPaint)

        // Logo circulaire KONAB / Conakry Web
        val logoRadius = 38f
        val logoCx = 72f
        val logoCy = 72f
        val whiteCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        canvas.drawCircle(logoCx, logoCy, logoRadius, whiteCircle)
        try {
            val logoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_konab_logo)
            if (logoDrawable != null) {
                val inset = 8
                logoDrawable.setBounds(
                    (logoCx - logoRadius + inset).toInt(),
                    (logoCy - logoRadius + inset).toInt(),
                    (logoCx + logoRadius - inset).toInt(),
                    (logoCy + logoRadius - inset).toInt()
                )
                logoDrawable.draw(canvas)
            }
        } catch (_: Exception) {}

        // Titre Conakry Web • Konab POS
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("CONAKRY WEB • KONAB POS", 126f, 66f, titlePaint)

        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(theme.title.uppercase(Locale.FRANCE), 126f, 96f, subHeaderPaint)

        // Badge activité à droite
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(60, 255, 255, 255)
        }
        val badgeRect = RectF(WIDTH - 270f, 38f, WIDTH - 36f, 92f)
        canvas.drawRoundRect(badgeRect, 18f, 18f, badgePaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(theme.shortBadge, badgeRect.centerX(), 72f, badgeTextPaint)

        // Photo titulaire (gauche)
        val photoRect = RectF(44f, 162f, 254f, 402f)
        val photoFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
        }
        canvas.drawRoundRect(
            RectF(photoRect.left - 5f, photoRect.top - 5f, photoRect.right + 5f, photoRect.bottom + 5f),
            26f, 26f, photoFramePaint
        )
        val loadedPhoto = photoUri?.takeIf { it.isNotBlank() }?.let { uriStr ->
            AgentIdCardHelper.loadAgentPhotoBitmap(context, uriStr)
                ?: runCatching {
                    val f = File(uriStr.removePrefix("file://"))
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }.getOrNull()
        }
        if (loadedPhoto != null) {
            val saveCount = canvas.save()
            val path = android.graphics.Path().apply {
                addRoundRect(photoRect, 22f, 22f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(loadedPhoto, null, photoRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(saveCount)
        } else {
            val monoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#1E293B") }
            canvas.drawRoundRect(photoRect, 22f, 22f, monoBg)
            val monoText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 96f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val initial = holderName.trim().take(1).uppercase(Locale.ROOT).ifBlank { "K" }
            canvas.drawText(initial, photoRect.centerX(), photoRect.centerY() + 34f, monoText)
        }

        // Code ID sous la photo
        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
            textSize = 20f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(codeId.take(16), photoRect.centerX(), photoRect.bottom + 36f, idPaint)

        // Infos au centre
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(holderName.ifBlank { "Titulaire Konab" }.take(24), 288f, 212f, namePaint)

        val orgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        if (organizationTitle.isNotBlank()) {
            canvas.drawText(organizationTitle.take(30), 288f, 256f, orgPaint)
        }

        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(235, 255, 255, 255)
            textSize = 22f
        }
        if (phone.isNotBlank()) {
            canvas.drawText("Tél : $phone", 288f, 304f, infoPaint)
        }
        if (zoneOrAddress.isNotBlank()) {
            canvas.drawText(zoneOrAddress.take(32), 288f, 344f, infoPaint)
        }
        if (statsLine.isNotBlank()) {
            val pillRect = RectF(288f, 372f, 700f, 422f)
            val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(55, 255, 255, 255) }
            canvas.drawRoundRect(pillRect, 14f, 14f, pillBg)
            canvas.drawText(statsLine.take(34), 304f, 405f, infoPaint)
        }

        // QR Code (droite)
        val qrRect = RectF(726f, 154f, 968f, 396f)
        val qrBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        canvas.drawRoundRect(qrRect, 24f, 24f, qrBg)
        canvas.drawRoundRect(qrRect, 24f, 24f, borderPaint)
        QrCodeHelper.generateQrBitmap(qrPayload.ifBlank { "konabpos://card/$codeId" }, sizePx = 320)?.let { qr ->
            val qrDrawRect = RectF(qrRect.left + 16f, qrRect.top + 16f, qrRect.right - 16f, qrRect.bottom - 16f)
            canvas.drawBitmap(qr, null, qrDrawRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Pied de page sombre
        val footerRect = RectF(36f, HEIGHT - 96f, WIDTH - 36f, HEIGHT - 28f)
        val footerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(95, 0, 0, 0) }
        canvas.drawRoundRect(footerRect, 18f, 18f, footerBg)

        val footerLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("✓ ${statusBadge.ifBlank { "Carte Officielle Vérifiée • Conakry Web" }}", 58f, HEIGHT - 54f, footerLeftPaint)

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.parseColor(theme.accentHex)
            textSize = 19f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("conakryweb • konabpos", WIDTH - 58f, HEIGHT - 54f, footerRightPaint)

        return bmp
    }

    fun downloadCardPng(context: Context, bitmap: Bitmap, fileBaseName: String) {
        val cleanName = "${fileBaseName.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KonabPOS_Cards")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, cleanName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            Toast.makeText(context, "Carte enregistrée dans Images/KonabPOS_Cards ✓", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur sauvegarde : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareCardPng(context: Context, bitmap: Bitmap, holderName: String, theme: CardActivityTheme) {
        try {
            val cacheDir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val outFile = File(cacheDir, "carte_${theme.name.lowercase(Locale.ROOT)}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "🪪 Carte ${theme.title} • Conakry Web / KONAB POS — $holderName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Partager la carte $holderName")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de partager la carte : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
