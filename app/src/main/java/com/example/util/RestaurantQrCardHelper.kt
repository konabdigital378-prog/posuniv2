package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.core.content.FileProvider
import com.example.data.remote.supabase.RestaurantTableRecord
import java.io.File
import java.io.FileOutputStream

/**
 * Generates clean, high-resolution printable/downloadable PNG table QR cards
 * for Restaurant mode and supports saving to Gallery, FileProvider sharing, and Android PrintManager.
 */
object RestaurantQrCardHelper {

    fun buildTableQrCardBitmap(
        shopName: String,
        table: RestaurantTableRecord,
        city: String = "",
        phone: String = ""
    ): Bitmap {
        val width = 900
        val height = 1240
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm off-white background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCE6F5")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val cardRect = RectF(36f, 36f, width - 36f, height - 36f)
        canvas.drawRoundRect(cardRect, 42f, 42f, cardPaint)
        canvas.drawRoundRect(cardRect, 42f, 42f, borderPaint)

        // Header banner (Royal Konab Blue)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B57D0")
            style = Paint.Style.FILL
        }
        val headerRect = RectF(36f, 36f, width - 36f, 290f)
        canvas.drawRoundRect(headerRect, 42f, 42f, headerPaint)
        // Square off bottom corners of header banner
        canvas.drawRect(36f, 240f, width - 36f, 292f, headerPaint)

        // Accent gold/orange stripe
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA580C")
        }
        canvas.drawRect(36f, 286f, width - 36f, 298f, accentPaint)

        // Header badge text
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FED7AA")
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        canvas.drawText("RESTAURANT • MENU DU JOUR", width / 2f, 108f, badgePaint)

        // Shop name
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (shopName.length > 20) 44f else 54f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(shopName.ifBlank { "Mon Restaurant" }.take(28), width / 2f, 186f, titlePaint)

        // Subtitle (City / Phone)
        val subText = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            phone.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Service Rapide & Commande Directe" }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(subText.take(46), width / 2f, 244f, subPaint)

        // Table pill badge
        val tablePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF7ED")
        }
        val tablePillStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA580C")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val pillRect = RectF(170f, 336f, width - 170f, 434f)
        canvas.drawRoundRect(pillRect, 49f, 49f, tablePillPaint)
        canvas.drawRoundRect(pillRect, 49f, 49f, tablePillStroke)

        val tableTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9A3412")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(table.tableNumber.uppercase(), width / 2f, 400f, tableTitlePaint)

        // Zone & Seats metadata
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("${table.zone} • ${table.seats} places", width / 2f, 482f, metaPaint)

        // QR Frame box
        val qrFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val qrFrameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B57D0")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val qrBox = RectF(170f, 514f, width - 170f, 1074f)
        canvas.drawRoundRect(qrBox, 32f, 32f, qrFramePaint)
        canvas.drawRoundRect(qrBox, 32f, 32f, qrFrameBorder)

        val qrUri = table.buildQrUri(shopName)
        val qrBitmap = QrCodeHelper.generateQrBitmap(qrUri, sizePx = 500, margin = 2)
        canvas.drawBitmap(qrBitmap, 200f, 544f, null)

        // Call to action instructions
        val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 31f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Scannez pour voir le Menu du Jour", width / 2f, 1134f, ctaPaint)

        val ctaSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#059669")
            textSize = 25f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Choisissez vos plats • Envoyez directement en salle", width / 2f, 1176f, ctaSubPaint)

        return bitmap
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
