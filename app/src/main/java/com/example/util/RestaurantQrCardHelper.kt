package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.remote.supabase.RestaurantTableRecord
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Generates clean, high-resolution printable/downloadable PNG table QR cards
 * for Restaurant mode and supports saving to Gallery, FileProvider sharing, and Android PrintManager.
 */
object RestaurantQrCardHelper {

    fun buildTableQrCardBitmap(
        shopName: String,
        table: RestaurantTableRecord,
        city: String = "",
        phone: String = "",
        context: Context? = null,
        logoUri: String? = null
    ): Bitmap {
        val width = 900
        val height = 1240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean light canvas backdrop
        canvas.drawColor(Color.parseColor("#F1F5F9"))

        val cardLeft = 36f
        val cardTop = 36f
        val cardRight = width - 36f
        val cardBottom = height - 36f
        val cardRadius = 42f
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // Subtle ambient elevation shadow behind card
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 6, 36, 94)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(cardLeft + 4f, cardTop + 8f, cardRight + 4f, cardBottom + 10f),
            cardRadius, cardRadius, shadowPaint
        )

        // Main white card surface
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)

        // Clip inside card bounds for clean header/footer corners
        val cardClipPath = Path().apply {
            addRoundRect(cardRect, cardRadius, cardRadius, Path.Direction.CW)
        }
        val cardSave = canvas.save()
        canvas.clipPath(cardClipPath)

        // 1. Rich Royal Konab Blue Gradient Header
        val headerBottom = 326f
        val headerRect = RectF(cardLeft, cardTop, cardRight, headerBottom)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardLeft, cardTop, cardRight, headerBottom,
                intArrayOf(
                    Color.parseColor("#06245E"),
                    Color.parseColor("#073B9A"),
                    Color.parseColor("#0E4DA4")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(headerRect, headerPaint)

        // Subtle decorative geometric arcs inside header
        val decorLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 18
        }
        canvas.drawCircle(cardRight - 40f, cardTop + 40f, 175f, decorLight)
        val decorWarm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA43A")
            alpha = 26
        }
        canvas.drawCircle(cardLeft + 55f, headerBottom - 10f, 130f, decorWarm)

        // Vibrant Orange-Gold separator bar under header
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardLeft, 0f, cardRight, 0f,
                Color.parseColor("#FF7900"),
                Color.parseColor("#FFA43A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(cardLeft, headerBottom - 9f, cardRight, headerBottom, stripePaint)

        // 2. Restaurant Logo Badge (centered at top of header)
        val logoCx = width / 2f
        val logoCy = 114f
        val logoRadius = 56f
        val logoRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFA43A")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(logoCx, logoCy, logoRadius + 5f, logoRingPaint)

        val logoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(logoCx, logoCy, logoRadius, logoBgPaint)

        val shopLogoBitmap = context?.let { ctx ->
            val raw = logoUri?.trim().orEmpty()
            if (raw.isNotBlank()) ShopLogoHelper.loadLogoBitmap(ctx, raw) else null
        }
        if (shopLogoBitmap != null) {
            drawCenterCroppedCircleBitmap(canvas, shopLogoBitmap, logoCx, logoCy, logoRadius - 4f)
        } else {
            // Crisp branded monogram fallback when no custom logo is uploaded yet
            val monoRect = RectF(logoCx - logoRadius + 4f, logoCy - logoRadius + 4f, logoCx + logoRadius - 4f, logoCy + logoRadius - 4f)
            val monoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    monoRect.left, monoRect.top, monoRect.right, monoRect.bottom,
                    Color.parseColor("#FF7900"),
                    Color.parseColor("#EA580C"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawOval(monoRect, monoBg)
            val initial = shopName.trim().take(1).uppercase(Locale.ROOT).ifBlank { "R" }
            val monoText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 54f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canvas.drawText(initial, logoCx, logoCy + 19f, monoText)
        }

        // 3. Restaurant Name (prominent below logo)
        val displayShopName = shopName.ifBlank { "Mon Restaurant" }.take(30)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = fitTextSize(displayShopName, 50f, 30f, 740f, this)
        }
        canvas.drawText(displayShopName, width / 2f, 224f, titlePaint)

        // Restaurant badge & city/phone subtitle
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("RESTAURANT • MENU DU JOUR", width / 2f, 262f, badgePaint)

        val subText = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Commande Directe à Table" }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(subText.take(46), width / 2f, 296f, subPaint)

        // 4. Prominent Table Number Pill Badge
        val tableLabel = table.tableNumber.trim().ifBlank { "TABLE 1" }.uppercase(Locale.ROOT)
        val pillRect = RectF(108f, 354f, width - 108f, 452f)
        val pillShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(24, 234, 88, 12)
        }
        canvas.drawRoundRect(
            RectF(pillRect.left, pillRect.top + 5f, pillRect.right, pillRect.bottom + 5f),
            28f, 28f, pillShadow
        )
        val tablePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF7ED")
        }
        val tablePillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7900")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(pillRect, 28f, 28f, tablePillPaint)
        canvas.drawRoundRect(pillRect, 28f, 28f, tablePillStroke)

        val tableTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9A3412")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = fitTextSize(tableLabel, 50f, 32f, pillRect.width() - 64f, this)
        }
        val textCenterY = pillRect.centerY() - ((tableTitlePaint.descent() + tableTitlePaint.ascent()) / 2f)
        canvas.drawText(tableLabel, width / 2f, textCenterY, tableTitlePaint)

        // Zone & Seats metadata
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("${table.zone} • ${table.seats} places", width / 2f, 490f, metaPaint)

        // 5. QR Code Showcase Frame with Modern Corner Accents
        val qrBox = RectF(185f, 514f, width - 185f, 1044f)
        val qrBoxShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 15, 23, 42)
        }
        canvas.drawRoundRect(
            RectF(qrBox.left + 3f, qrBox.top + 6f, qrBox.right + 3f, qrBox.bottom + 6f),
            34f, 34f, qrBoxShadow
        )
        canvas.drawRoundRect(qrBox, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawRoundRect(qrBox, 34f, 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        })

        // Orange/Blue corner brackets around QR frame
        drawQrCornerBrackets(
            canvas = canvas,
            rect = qrBox,
            cornerRadius = 34f,
            armLen = 46f,
            color = Color.parseColor("#FF7900"),
            strokeWidth = 7f
        )

        val qrUri = table.buildQrUri(shopName)
        val qrBitmap = QrCodeHelper.generateQrBitmap(qrUri, sizePx = 472, margin = 3)
        canvas.drawBitmap(qrBitmap, 214f, 543f, null)

        // 6. Scan CTA above bottom Konab POS footer
        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 27f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Scannez pour voir le Menu & Commander", width / 2f, 1084f, ctaPaint)

        // 7. Bottom Footer Bar with Konab POS Logo at the Bottom
        val footerTop = 1114f
        val footerRect = RectF(cardLeft, footerTop, cardRight, cardBottom)
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                cardLeft, footerTop, cardRight, cardBottom,
                Color.parseColor("#06245E"),
                Color.parseColor("#073B9A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(footerRect, footerPaint)
        canvas.drawRect(cardLeft, footerTop, cardRight, footerTop + 5f, stripePaint)

        drawKonabFooterBranding(
            canvas = canvas,
            context = context,
            centerX = width / 2f,
            centerY = (footerTop + cardBottom) / 2f + 2f,
            tagline = "Menu Digital & Commande Directe"
        )

        canvas.restoreToCount(cardSave)

        // Crisp outer card stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderPaint)

        return bitmap
    }

    internal fun drawCenterCroppedCircleBitmap(
        canvas: Canvas,
        bmp: Bitmap,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        if (bmp.width <= 0 || bmp.height <= 0) return
        val save = canvas.save()
        val clipPath = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        val diameter = radius * 2f
        val srcRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val drawRect = if (srcRatio >= 1f) {
            val scaledW = diameter * srcRatio
            val dx = (scaledW - diameter) / 2f
            RectF(cx - radius - dx, cy - radius, cx + radius + dx, cy + radius)
        } else {
            val scaledH = diameter / srcRatio
            val dy = (scaledH - diameter) / 2f
            RectF(cx - radius, cy - radius - dy, cx + radius, cy + radius + dy)
        }
        canvas.drawBitmap(bmp, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restoreToCount(save)
    }

    internal fun drawQrCornerBrackets(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        armLen: Float,
        color: Int,
        strokeWidth: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val d = cornerRadius * 2f
        // Top-left
        val tl = Path().apply {
            moveTo(rect.left, rect.top + armLen)
            lineTo(rect.left, rect.top + cornerRadius)
            arcTo(RectF(rect.left, rect.top, rect.left + d, rect.top + d), 180f, 90f, false)
            lineTo(rect.left + armLen, rect.top)
        }
        canvas.drawPath(tl, paint)
        // Top-right
        val tr = Path().apply {
            moveTo(rect.right - armLen, rect.top)
            lineTo(rect.right - cornerRadius, rect.top)
            arcTo(RectF(rect.right - d, rect.top, rect.right, rect.top + d), 270f, 90f, false)
            lineTo(rect.right, rect.top + armLen)
        }
        canvas.drawPath(tr, paint)
        // Bottom-right
        val br = Path().apply {
            moveTo(rect.right, rect.bottom - armLen)
            lineTo(rect.right, rect.bottom - cornerRadius)
            arcTo(RectF(rect.right - d, rect.bottom - d, rect.right, rect.bottom), 0f, 90f, false)
            lineTo(rect.right - armLen, rect.bottom)
        }
        canvas.drawPath(br, paint)
        // Bottom-left
        val bl = Path().apply {
            moveTo(rect.left + armLen, rect.bottom)
            lineTo(rect.left + cornerRadius, rect.bottom)
            arcTo(RectF(rect.left, rect.bottom - d, rect.left + d, rect.bottom), 90f, 90f, false)
            lineTo(rect.left, rect.bottom - armLen)
        }
        canvas.drawPath(bl, paint)
    }

    internal fun drawKonabFooterBranding(
        canvas: Canvas,
        context: Context?,
        centerX: Float,
        centerY: Float,
        tagline: String
    ) {
        val brandText = "KONAB POS"
        val sepText = " • $tagline"

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val logoRadius = 24f
        val gap = 14f
        val brandWidth = brandPaint.measureText(brandText)
        val sepWidth = sepPaint.measureText(sepText)
        val totalWidth = (logoRadius * 2f) + gap + brandWidth + sepWidth
        val startX = centerX - (totalWidth / 2f)

        val logoCx = startX + logoRadius
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7900")
        }
        canvas.drawCircle(logoCx, centerY, logoRadius + 2.5f, ringPaint)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(logoCx, centerY, logoRadius, whitePaint)

        val konabBmp = context?.let { ctx ->
            try {
                BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_konab_logo)
                    ?: ContextCompat.getDrawable(ctx, R.drawable.ic_konab_logo)?.let { d ->
                        val size = (logoRadius * 2f).toInt().coerceAtLeast(32)
                        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                        val c = Canvas(b)
                        d.setBounds(0, 0, size, size)
                        d.draw(c)
                        b
                    }
            } catch (_: Throwable) {
                null
            }
        }

        if (konabBmp != null) {
            drawCenterCroppedCircleBitmap(canvas, konabBmp, logoCx, centerY, logoRadius - 3f)
        } else {
            val kPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#073B9A")
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val kY = centerY - ((kPaint.descent() + kPaint.ascent()) / 2f)
            canvas.drawText("K", logoCx, kY, kPaint)
        }

        val textX = startX + (logoRadius * 2f) + gap
        val brandY = centerY - ((brandPaint.descent() + brandPaint.ascent()) / 2f)
        canvas.drawText(brandText, textX, brandY, brandPaint)

        val sepY = centerY - ((sepPaint.descent() + sepPaint.ascent()) / 2f)
        canvas.drawText(sepText, textX + brandWidth, sepY, sepPaint)
    }

    internal fun fitTextSize(
        text: String,
        maxSize: Float,
        minSize: Float,
        maxWidthPx: Float,
        paint: Paint
    ): Float {
        var size = maxSize
        while (size > minSize) {
            paint.textSize = size
            if (paint.measureText(text) <= maxWidthPx) {
                return size
            }
            size -= 2f
        }
        return minSize
    }

    private fun sanitizedFileName(shopName: String, tableName: String): String {
        val raw = "QR_${shopName}_${tableName}".replace(Regex("[^A-Za-z0-9_-]"), "_")
        return "${raw.take(48)}_${System.currentTimeMillis() % 10000}.png"
    }

    fun saveTableQrPngToDevice(
        context: Context,
        bitmap: Bitmap,
        shopName: String,
        tableName: String
    ): Result<String> {
        return try {
            val fileName = sanitizedFileName(shopName, tableName)
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

    fun shareTableQrPng(
        context: Context,
        bitmap: Bitmap,
        shopName: String,
        tableName: String
    ): Result<Unit> {
        return try {
            val dir = File(context.cacheDir, "qr_cards")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, sanitizedFileName(shopName, tableName))
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "QR Code $tableName - $shopName")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "QR Code Menu du Jour ($tableName) pour $shopName"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Partager le QR Code ($tableName)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun printTableQrCard(
        context: Context,
        bitmap: Bitmap,
        shopName: String,
        tableName: String
    ): Result<Unit> {
        return try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return Result.failure(IllegalStateException("Service d'impression indisponible sur cet appareil."))
            val jobName = "Konab_QR_${shopName}_${tableName}".take(48)
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
                        if (cancellationSignal?.isCanceled == true) {
                            callback.onWriteCancelled()
                            doc.close()
                            return
                        }
                        val canvas = page.canvas
                        val pageW = canvas.width.toFloat()
                        val pageH = canvas.height.toFloat()
                        val margin = 36f
                        val availW = (pageW - margin * 2f).coerceAtLeast(100f)
                        val availH = (pageH - margin * 2f).coerceAtLeast(100f)
                        val scale = minOf(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
                        val drawW = bitmap.width * scale
                        val drawH = bitmap.height * scale
                        val left = (pageW - drawW) / 2f
                        val top = (pageH - drawH) / 2f
                        val destRect = RectF(left, top, left + drawW, top + drawH)
                        canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                        doc.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            doc.writeTo(out)
                        }
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
