package com.example.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.supabase.CoutureQrParser
import java.io.File
import java.io.FileOutputStream

object CouturePhotoHelper {

    fun createCameraPhotoTarget(context: Context, prefix: String = "couture"): Pair<File, Uri>? {
        return try {
            val dir = File(context.cacheDir, "camera_photos")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            file to uri
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies and optimizes any local content:// or file:// image into internal app storage (`files/couture_photos/`)
     * so fabric & model photos remain 100% persistent and uploadable cleanly to Supabase.
     */
    fun saveImageUriToInternalStorage(
        context: Context,
        sourceUri: Uri,
        prefix: String = "photo"
    ): String? {
        return try {
            val dir = File(context.filesDir, "couture_photos")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val rawBytes = input.readBytes()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                val maxDim = maxOf(options.outWidth, options.outHeight)
                val sampleSize = if (maxDim > 1280) (maxDim / 1024).coerceAtLeast(1) else 1
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                if (bmp != null) {
                    FileOutputStream(outFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
                    }
                    bmp.recycle()
                } else {
                    FileOutputStream(outFile).use { out -> out.write(rawBytes) }
                }
            } ?: return null
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            sourceUri.toString()
        }
    }

    fun getUploadableFileForUri(context: Context, uriOrPath: String?): File? {
        val clean = uriOrPath?.trim().orEmpty()
        if (clean.isBlank() || clean.startsWith("http://") || clean.startsWith("https://")) return null
        if (clean.startsWith("file://")) {
            val f = File(Uri.parse(clean).path ?: return null)
            return f.takeIf { it.exists() && it.length() > 0L }
        }
        if (clean.startsWith("/")) {
            val f = File(clean)
            return f.takeIf { it.exists() && it.length() > 0L }
        }
        if (clean.startsWith("content://")) {
            val savedUri = saveImageUriToInternalStorage(context, Uri.parse(clean), "sync") ?: return null
            return getUploadableFileForUri(context, savedUri)
        }
        return null
    }
}

@Composable
fun CouturePhotoImage(
    uriOrKey: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp),
    fallbackDrawable: Int = R.drawable.img_prod_mode
) {
    val clean = uriOrKey?.trim().orEmpty()
    val presetRes = remember(clean) {
        ProductImageHelper.resolveDrawableRes(clean) ?: ShopLogoHelper.resolvePresetDrawable(clean)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val isExternalUri = clean.startsWith("content://") ||
            clean.startsWith("http://") ||
            clean.startsWith("https://") ||
            clean.startsWith("file://")
        when {
            isExternalUri -> {
                AsyncImage(
                    model = clean,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = fallbackDrawable),
                    placeholder = painterResource(id = fallbackDrawable)
                )
            }
            presetRes != null -> {
                Image(
                    painter = painterResource(id = presetRes),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = fallbackDrawable),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CouturePhotoCaptureControl(
    title: String,
    subtitle: String,
    currentPhotoUri: String,
    onPhotoSelected: (String) -> Unit,
    filePrefix: String = "couture",
    cameraButtonTestTag: String = "btn_couture_take_photo",
    galleryButtonTestTag: String = "btn_couture_pick_gallery"
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val persisted = CouturePhotoHelper.saveImageUriToInternalStorage(context, uri, filePrefix) ?: uri.toString()
            onPhotoSelected(persisted)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val target = CouturePhotoHelper.createCameraPhotoTarget(context, filePrefix)
            if (target != null) {
                pendingCameraUri = target.second
                takePictureLauncher.launch(target.second)
            } else {
                Toast.makeText(context, "Impossible de préparer l'appareil photo.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permission caméra refusée. Utilisez la galerie photo.", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val persisted = CouturePhotoHelper.saveImageUriToInternalStorage(context, uri, filePrefix) ?: uri.toString()
            onPhotoSelected(persisted)
        }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val target = CouturePhotoHelper.createCameraPhotoTarget(context, filePrefix)
            if (target != null) {
                pendingCameraUri = target.second
                takePictureLauncher.launch(target.second)
            } else {
                Toast.makeText(context, "Impossible de préparer l'appareil photo.", Toast.LENGTH_SHORT).show()
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentPhotoUri.isNotBlank()) {
                    CouturePhotoImage(
                        uriOrKey = currentPhotoUri,
                        contentDescription = title,
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF6D28D9),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(
                        text = if (currentPhotoUri.isNotBlank()) "Photo enregistrée prête pour synchronisation" else subtitle,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { launchCamera() },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF6D28D9)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(cameraButtonTestTag)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Prendre photo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(galleryButtonTestTag)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Galerie", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Génère l'affiche QR PNG haute résolution pour un atelier de couture
 * (catalogue complet de modèles ou fiche d'un modèle spécifique).
 */
object CoutureQrCardHelper {

    fun buildCatalogQrCardBitmap(
        shopName: String,
        shopId: Long,
        city: String = "",
        phone: String = "",
        modelsCount: Int = 0,
        specificModelId: String? = null,
        specificModelTitle: String? = null
    ): Bitmap {
        val width = 900
        val height = 1240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#FAF5FF"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDD6FE")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val cardRect = RectF(36f, 36f, width - 36f, height - 36f)
        canvas.drawRoundRect(cardRect, 42f, 42f, cardPaint)
        canvas.drawRoundRect(cardRect, 42f, 42f, borderPaint)

        // Header banner (Royal Couture Violet)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B21B6")
            style = Paint.Style.FILL
        }
        val headerRect = RectF(36f, 36f, width - 36f, 290f)
        canvas.drawRoundRect(headerRect, 42f, 42f, headerPaint)
        canvas.drawRect(36f, 240f, width - 36f, 292f, headerPaint)

        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
        }
        canvas.drawRect(36f, 286f, width - 36f, 298f, goldPaint)

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 25f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("ATELIER COUTURE • CATALOGUE MODÈLES", width / 2f, 108f, badgePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (shopName.length > 20) 44f else 54f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(shopName.ifBlank { "Atelier Couture" }.take(28), width / 2f, 186f, titlePaint)

        val subText = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Sur-Mesure • Prise de Mesures & Tissus" }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EDE9FE")
            textSize = 25f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(subText.take(46), width / 2f, 244f, subPaint)

        // Pill badge
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F3FF") }
        val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7C3AED")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val pillRect = RectF(130f, 336f, width - 130f, 434f)
        canvas.drawRoundRect(pillRect, 49f, 49f, pillBg)
        canvas.drawRoundRect(pillRect, 49f, 49f, pillBorder)

        val pillText = if (!specificModelTitle.isNullOrBlank()) {
            specificModelTitle.take(28)
        } else {
            "CATALOGUE DES MODÈLES"
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4C1D95")
            textSize = if (pillText.length > 22) 34f else 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(pillText, width / 2f, 398f, pillPaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 25f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaLabel = if (modelsCount > 0) {
            "$modelsCount modèle(s) exclusifs • Choisissez votre tenue"
        } else {
            "Scannez & choisissez votre modèle sur Konab POS"
        }
        canvas.drawText(metaLabel, width / 2f, 482f, metaPaint)

        val qrBox = RectF(170f, 514f, width - 170f, 1074f)
        canvas.drawRoundRect(qrBox, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(qrBox, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5B21B6")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        })

        val qrUri = CoutureQrParser.buildCatalogQrUri(
            shopId = shopId,
            shopName = shopName,
            modelId = specificModelId
        )
        val qrBitmap = QrCodeHelper.generateQrBitmap(qrUri, sizePx = 500, margin = 2)
        canvas.drawBitmap(qrBitmap, 200f, 544f, null)

        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 31f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Scannez pour voir nos modèles & tarifs", width / 2f, 1134f, ctaPaint)

        val ctaSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6D28D9")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Suivez ensuite l'avancement de votre couture sur l'app", width / 2f, 1176f, ctaSubPaint)

        return bitmap
    }

    private fun sanitizedFileName(shopName: String): String {
        val raw = "QR_COUTURE_${shopName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${raw.take(48)}_${System.currentTimeMillis() % 10000}.png"
    }

    fun saveCatalogQrPngToDevice(
        context: Context,
        bitmap: Bitmap,
        shopName: String
    ): Result<String> {
        return try {
            val fileName = sanitizedFileName(shopName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KonabPOS")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("Impossible d'accéder à la galerie Images."))
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: return Result.failure(IllegalStateException("Flux d'écriture PNG indisponible."))
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("PNG enregistré dans Images/KonabPOS/$fileName")
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                    "KonabPOS_QR"
                )
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Result.success("PNG enregistré : ${outFile.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareCatalogQrPng(
        context: Context,
        bitmap: Bitmap,
        shopName: String
    ): Result<Unit> {
        return try {
            val dir = File(context.cacheDir, "qr_cards")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, sanitizedFileName(shopName))
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Catalogue Modèles Couture - $shopName")
                putExtra(Intent.EXTRA_TEXT, "Scannez ce QR Code pour découvrir les modèles de $shopName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager le QR Catalogue").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun printCatalogQrCard(
        context: Context,
        bitmap: Bitmap,
        shopName: String
    ): Result<Unit> {
        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return Result.failure(IllegalStateException("Service d'impression indisponible."))
            val jobName = "Konab_Couture_QR_${shopName}".take(48)
            val adapter = object : PrintDocumentAdapter() {
                private var pdfDocument: PrintedPdfDocument? = null
                private var currentAttributes: PrintAttributes? = null

                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    currentAttributes = newAttributes
                    pdfDocument = PrintedPdfDocument(context, newAttributes)
                    val info = PrintDocumentInfo.Builder("${jobName}.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                        .setPageCount(1)
                        .build()
                    callback.onLayoutFinished(info, newAttributes != oldAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val doc = pdfDocument ?: PrintedPdfDocument(
                        context,
                        currentAttributes ?: PrintAttributes.Builder().build()
                    )
                    try {
                        val page = doc.startPage(0)
                        val canvas = page.canvas
                        val margin = 36f
                        val availW = (canvas.width - margin * 2f).coerceAtLeast(100f)
                        val availH = (canvas.height - margin * 2f).coerceAtLeast(100f)
                        val scale = minOf(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
                        val drawW = bitmap.width * scale
                        val drawH = bitmap.height * scale
                        val left = (canvas.width - drawW) / 2f
                        val top = (canvas.height - drawH) / 2f
                        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), Paint(Paint.FILTER_BITMAP_FLAG))
                        doc.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use { out -> doc.writeTo(out) }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        doc.close()
                        pdfDocument = null
                    }
                }
            }
            printManager.print(jobName, adapter, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
