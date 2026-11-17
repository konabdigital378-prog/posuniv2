package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.remote.supabase.DeliveryDriverProfile
import com.example.data.remote.supabase.DeliveryOrderRecord
import com.example.data.remote.supabase.DeliveryQrParser
import com.example.data.remote.supabase.DeliveryStatus
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object DeliveryReceiptHelper {

    fun buildDeliveryReceiptBitmap(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ): Bitmap {
        val width = 840
        val height = 1160
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        canvas.drawColor(Color.parseColor("#F8FAFC"))

        // En-tête dégradé cyan/marine
        val headerRect = RectF(0f, 0f, width.toFloat(), 220f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 220f,
                intArrayOf(
                    Color.parseColor("#0C4A6E"),
                    Color.parseColor("#0284C7"),
                    Color.parseColor("#0EA5E9")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(headerRect, headerPaint)

        // Logo Konab
        val logoCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(92f, 108f, 44f, logoCircle)
        try {
            ContextCompat.getDrawable(context, R.drawable.ic_konab_logo)?.let { drawable ->
                drawable.setBounds(56, 72, 128, 144)
                drawable.draw(canvas)
            }
        } catch (_: Exception) {}

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("REÇU DE LIVRAISON", 156f, 96f, brandPaint)

        val subBrandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0F2FE")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KONAB POS • N° ${delivery.effectiveReceiptNumber}", 156f, 134f, subBrandPaint)

        // Statut badge
        val isValidated = delivery.status == DeliveryStatus.VALIDEE
        val statusPillColor = if (isValidated) Color.parseColor("#10B981") else Color.parseColor("#F59E0B")
        val statusRect = RectF(156f, 152f, 520f, 196f)
        canvas.drawRoundRect(statusRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusPillColor })
        val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val statusLabel = if (isValidated) "✓ LIVRAISON VALIDÉE PAR QR" else "🚚 LIVRAISON EN COURS"
        canvas.drawText(statusLabel, statusRect.centerX(), 181f, statusTextPaint)

        // Corps blanc central
        val cardRect = RectF(36f, 246f, width - 36f, height - 106f)
        canvas.drawRoundRect(cardRect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.parseColor("#CBD5E1")
        }
        canvas.drawRoundRect(cardRect, 26f, 26f, borderPaint)

        val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 20f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 2f
        }

        var y = 294f
        val leftX = 72f

        // Livreur
        canvas.drawText("LIVREUR CERTIFIÉ", leftX, y, sectionTitlePaint)
        y += 34f
        canvas.drawText(delivery.driverName.ifBlank { driver?.fullName ?: "Livreur" }, leftX, y, valuePaint)
        y += 30f
        val driverPhone = driver?.phone?.ifBlank { delivery.driverPhone } ?: delivery.driverPhone
        if (driverPhone.isNotBlank()) {
            canvas.drawText("Tél : $driverPhone • ID : ${delivery.driverId}", leftX, y, labelPaint)
            y += 28f
        } else {
            canvas.drawText("ID Livreur : ${delivery.driverId}", leftX, y, labelPaint)
            y += 28f
        }

        canvas.drawLine(leftX, y + 6f, width - 72f, y + 6f, dividerPaint)
        y += 42f

        // Client & Coordonnées
        canvas.drawText("DESTINATAIRE / COORDONNÉES CLIENT", leftX, y, sectionTitlePaint)
        y += 34f
        canvas.drawText(delivery.clientName.ifBlank { "Client Konab POS" }, leftX, y, valuePaint)
        y += 30f
        if (delivery.clientPhone.isNotBlank()) {
            canvas.drawText("Téléphone : ${delivery.clientPhone}", leftX, y, labelPaint)
            y += 28f
        }
        val clientPlace = listOf(delivery.clientNeighborhood, delivery.clientCity)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
        if (clientPlace.isNotBlank()) {
            canvas.drawText("Localité : $clientPlace", leftX, y, labelPaint)
            y += 28f
        }

        canvas.drawLine(leftX, y + 6f, width - 72f, y + 6f, dividerPaint)
        y += 42f

        // Détails de course
        canvas.drawText("DÉTAILS DE LA LIVRAISON", leftX, y, sectionTitlePaint)
        y += 34f
        canvas.drawText("Colis / Commande : ${delivery.title.ifBlank { "Livraison" }.take(36)}", leftX, y, valuePaint)
        y += 30f
        if (delivery.pickupAddress.isNotBlank()) {
            canvas.drawText("Départ : ${delivery.pickupAddress.take(42)}", leftX, y, labelPaint)
            y += 28f
        }
        if (delivery.dropoffAddress.isNotBlank()) {
            canvas.drawText("Destination : ${delivery.dropoffAddress.take(40)}", leftX, y, labelPaint)
            y += 28f
        }
        if (delivery.validatedAt > 0L) {
            canvas.drawText("Validée par QR le : ${delivery.formattedDateTime(delivery.validatedAt)}", leftX, y, labelPaint)
            y += 28f
        } else {
            canvas.drawText("Créée le : ${delivery.formattedDateTime(delivery.createdAt)}", leftX, y, labelPaint)
            y += 28f
        }

        // Encadré prix
        val priceBoxTop = maxOf(y + 20f, 750f)
        val priceRect = RectF(leftX, priceBoxTop, width - 72f, priceBoxTop + 122f)
        val priceBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECFEFF") }
        val priceBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#0284C7")
        }
        canvas.drawRoundRect(priceRect, 20f, 20f, priceBg)
        canvas.drawRoundRect(priceRect, 20f, 20f, priceBorder)

        val priceCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0369A1")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("MONTANT TOTAL LIVRAISON", leftX + 24f, priceBoxTop + 42f, priceCaptionPaint)

        val priceValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0C4A6E")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(delivery.formattedPrice(), leftX + 24f, priceBoxTop + 96f, priceValuePaint)

        // Petit QR de vérification à droite dans l'encadré ou en bas
        val qrSize = 110
        val qrPayload = DeliveryQrParser.buildDriverQrUri(delivery.driverId)
        QrCodeHelper.generateQrBitmap(qrPayload, sizePx = 220)?.let { qrBmp ->
            val qrLeft = width - 72f - qrSize - 16f
            val qrTop = priceBoxTop + 6f
            val qrDest = RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
            canvas.drawRoundRect(
                RectF(qrLeft - 4f, qrTop - 4f, qrLeft + qrSize + 4f, qrTop + qrSize + 4f),
                12f, 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            )
            canvas.drawBitmap(qrBmp, null, qrDest, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Pied de page Konab POS
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Reçu officiel authentifié par KONAB POS • ${delivery.id}",
            width / 2f,
            height - 46f,
            footerPaint
        )

        return bmp
    }

    fun generateDeliveryReceiptPdf(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ): File {
        val dir = File(context.cacheDir, "delivery_receipts").apply { mkdirs() }
        val file = File(dir, "Recu_Livraison_${delivery.effectiveReceiptNumber}.pdf")
        val bmp = buildDeliveryReceiptBitmap(context, delivery, driver)
        val nativeWorked = try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val destRect = RectF(18f, 18f, 577f, 824f)
            page.canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            true
        } catch (_: Throwable) {
            false
        }
        if (!nativeWorked) {
            writeFallbackBitmapPdf(bmp, file)
        }
        return file
    }

    private fun writeFallbackBitmapPdf(bitmap: Bitmap, outFile: File) {
        val jpegStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, jpegStream)
        val imgBytes = jpegStream.toByteArray()

        val baos = java.io.ByteArrayOutputStream()
        fun writeAscii(s: String) = baos.write(s.toByteArray(Charsets.US_ASCII))
        val offsets = mutableListOf<Int>()

        writeAscii("%PDF-1.4\n")
        offsets.add(baos.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(baos.size())
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets.add(baos.size())
        writeAscii(
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                "/Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>\nendobj\n"
        )

        val contentStream = "q\n559 0 0 806 18 18 cm\n/Im0 Do\nQ\n"
        offsets.add(baos.size())
        writeAscii(
            "4 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n"
        )

        offsets.add(baos.size())
        writeAscii(
            "5 0 obj\n<< /Type /XObject /Subtype /Image /Width ${bitmap.width} /Height ${bitmap.height} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${imgBytes.size} >>\nstream\n"
        )
        baos.write(imgBytes)
        writeAscii("\nendstream\nendobj\n")

        val xrefPos = baos.size()
        writeAscii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        writeAscii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")

        FileOutputStream(outFile).use { it.write(baos.toByteArray()) }
    }

    fun saveReceiptPngToGallery(context: Context, bitmap: Bitmap, delivery: DeliveryOrderRecord): Boolean {
        val cleanName = "Recu_${delivery.effectiveReceiptNumber}_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KonabPOS_Livraisons")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, cleanName)
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            Toast.makeText(context, "Reçu enregistré dans Images/KonabPOS_Livraisons ✓", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur sauvegarde : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun sendReceiptToClientWhatsApp(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ) {
        val message = delivery.buildReceiptText(driver)
        val cleanPhone = delivery.clientPhone.replace(Regex("[^0-9]"), "")
        try {
            val url = if (cleanPhone.length >= 6) {
                "https://wa.me/$cleanPhone?text=${Uri.encode(message)}"
            } else {
                "https://wa.me/?text=${Uri.encode(message)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            shareReceiptTextFallback(context, message)
        }
    }

    fun sendReceiptViaSms(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ) {
        val message = delivery.buildReceiptText(driver)
        val cleanPhone = delivery.clientPhone.replace(Regex("[^0-9+]"), "")
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanPhone")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            shareReceiptTextFallback(context, message)
        }
    }

    fun shareReceiptPng(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ) {
        try {
            val bmp = buildDeliveryReceiptBitmap(context, delivery, driver)
            val cacheDir = File(context.cacheDir, "delivery_receipts").apply { mkdirs() }
            val outFile = File(cacheDir, "recu_${delivery.effectiveReceiptNumber}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, delivery.buildReceiptText(driver))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Envoyer le reçu à ${delivery.clientName}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de partager le reçu : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareReceiptPdf(
        context: Context,
        delivery: DeliveryOrderRecord,
        driver: DeliveryDriverProfile? = null
    ) {
        try {
            val pdfFile = generateDeliveryReceiptPdf(context, delivery, driver)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reçu de livraison ${delivery.effectiveReceiptNumber}")
                putExtra(Intent.EXTRA_TEXT, delivery.buildReceiptText(driver))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Envoyer le PDF à ${delivery.clientName}")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de partager le PDF : ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareReceiptTextFallback(context: Context, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(intent, "Envoyer le reçu au client")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
