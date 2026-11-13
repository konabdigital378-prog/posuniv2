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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.screens.AgentUser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for CONAPES Agent Profile Photos, ISO/IEC 7810 ID-1 (CR80: 85.60mm x 53.98mm)
 * Official Agent ID Card generation, verification QR payloads, Gallery download, PDF export, and PrintManager printing.
 */
object AgentIdCardHelper {

    // ISO/IEC 7810 ID-1 (CR80) Standard ID Card Dimensions
    const val CR80_WIDTH_MM = 85.60f
    const val CR80_HEIGHT_MM = 53.98f
    const val CR80_ASPECT_RATIO = CR80_WIDTH_MM / CR80_HEIGHT_MM // ~1.5858f

    // 300 DPI Render Resolution for CR80 Card (85.60mm x 53.98mm @ 300 DPI = 1011 x 638 px)
    const val CARD_BITMAP_WIDTH = 1011
    const val CARD_BITMAP_HEIGHT = 638

    data class VerifiedConapesCard(
        val agentId: String,
        val name: String,
        val phone: String,
        val zone: String,
        val isValidChecksum: Boolean,
        val rawPayload: String
    )

    fun getConapesReferenceCode(agentId: String): String {
        val clean = agentId.trim().uppercase(Locale.ROOT).ifBlank { "AGT-000" }
        return if (clean.startsWith("KONAB-")) clean else "KONAB-$clean"
    }

    /**
     * Builds a compact, standard, checksum-verified QR payload (<= 115 bytes)
     * so QrCodeHelper generates a crisp high-contrast matrix.
     */
    fun buildConapesQrPayload(agent: AgentUser): String {
        val idPart = agent.id.trim().uppercase(Locale.ROOT).take(14)
        val namePart = agent.name.trim().replace("|", " ").take(28)
        val phonePart = agent.phone.trim().replace("|", " ").take(16)
        val zonePart = agent.zone.trim().replace("|", " ").take(20)
        val base = "KONAB|$idPart|$namePart|$phonePart|$zonePart"
        val sig = LicenseHelper.calculateChecksum(base)
        return "$base|$sig"
    }

    /**
     * Parses and verifies a scanned KONAB POS (or legacy) QR code or manual reference.
     */
    fun parseAndVerifyConapesQr(raw: String): VerifiedConapesCard? {
        val clean = raw.trim()
        if (clean.isBlank()) return null

        val parts = clean.split("|")
        if (parts.size >= 5 && (parts[0].equals("KONAB", ignoreCase = true) || parts[0].equals("CONAPES", ignoreCase = true))) {
            val prefix = parts[0].trim().uppercase(Locale.ROOT)
            val idPart = parts[1].trim().uppercase(Locale.ROOT)
            val namePart = parts[2].trim()
            val phonePart = parts[3].trim()
            val zonePart = parts[4].trim()
            val sigPart = parts.getOrNull(5)?.trim() ?: ""
            val base = "$prefix|$idPart|$namePart|$phonePart|$zonePart"
            val expectedSig = LicenseHelper.calculateChecksum(base)
            return VerifiedConapesCard(
                agentId = idPart,
                name = namePart,
                phone = phonePart,
                zone = zonePart,
                isValidChecksum = sigPart.equals(expectedSig, ignoreCase = true),
                rawPayload = clean
            )
        }

        // Also support direct KONAB-AGT-XXX / CONAPES-AGT-XXX reference lookup
        val upper = clean.uppercase(Locale.ROOT)
        if (upper.startsWith("KONAB-") || upper.startsWith("CONAPES-")) {
            val idOnly = upper.removePrefix("KONAB-").removePrefix("CONAPES-")
            return VerifiedConapesCard(
                agentId = idOnly,
                name = "",
                phone = "",
                zone = "",
                isValidChecksum = true,
                rawPayload = clean
            )
        }

        return null
    }

    /**
     * Copies a profile photo chosen via the zero-permission Android Photo Picker
     * into internal app storage (`filesDir/agent_photos/`) and scales it safely.
     */
    fun saveAgentPhotoToInternalStorage(
        context: Context,
        sourceUri: Uri,
        agentKey: String = "agent"
    ): String? {
        return try {
            val dir = File(context.filesDir, "agent_photos")
            if (!dir.exists()) dir.mkdirs()

            val safeKey = agentKey.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "agent" }
            val destFile = File(dir, "${safeKey}_${System.currentTimeMillis()}.jpg")

            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null

            // Scale down proportionally to max 600px on longest side
            val maxDim = 600
            val w = decoded.width
            val h = decoded.height
            val scale = if (w > maxDim || h > maxDim) {
                maxDim.toFloat() / maxOf(w, h).toFloat()
            } else 1f
            val finalBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            } else decoded

            FileOutputStream(destFile).use { out ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (finalBmp !== decoded) decoded.recycle()
            Uri.fromFile(destFile).toString()
        } catch (e: Exception) {
            android.util.Log.e("AgentIdCardHelper", "Erreur sauvegarde photo agent: ${e.message}", e)
            null
        }
    }

    fun loadAgentPhotoBitmap(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return try {
            when {
                photoUri.startsWith("file://") -> BitmapFactory.decodeFile(photoUri.removePrefix("file://"))
                photoUri.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(photoUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                else -> BitmapFactory.decodeFile(photoUri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadKonabLogoBitmap(context: Context, sizePx: Int): Bitmap? {
        return try {
            val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.ic_konab_logo)
            if (decoded != null) {
                Bitmap.createScaledBitmap(decoded, sizePx, sizePx, true)
            } else {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_konab_logo) ?: return null
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(c)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders an official ISO/IEC 7810 ID-1 (CR80: 85.60mm x 53.98mm @ 300 DPI = 1011 x 638 px)
     * CONAPES Agent ID Card Bitmap with CONAPES logo, Agent Name, Profile Photo, and Verification QR Code.
     */
    fun renderIdCardBitmap(context: Context, agent: AgentUser): Bitmap {
        val w = CARD_BITMAP_WIDTH
        val h = CARD_BITMAP_HEIGHT
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. White Card Base with subtle security tint
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F1F5F9"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 34f, 34f, bgPaint)

        // Subtle guilloche security pattern
        val secLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B57D0")
            alpha = 14
            strokeWidth = 2f
        }
        var xSec = -h.toFloat()
        while (xSec < w + h) {
            canvas.drawLine(xSec, 0f, xSec + h * 0.55f, h.toFloat(), secLinePaint)
            xSec += 28f
        }

        // 2. Top Institutional Navy Header
        val headerHeight = 162f
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), headerHeight,
                Color.parseColor("#06245E"),
                Color.parseColor("#073B9A"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), headerHeight, headerPaint)

        // Gold & Emerald tricolor institutional accent bar under header
        val stripeY = headerHeight
        val goldPaint = Paint().apply { color = Color.parseColor("#F59E0B") }
        val greenPaint = Paint().apply { color = Color.parseColor("#10B981") }
        val orangePaint = Paint().apply { color = Color.parseColor("#FF7900") }
        canvas.drawRect(0f, stripeY, w * 0.45f, stripeY + 10f, orangePaint)
        canvas.drawRect(w * 0.45f, stripeY, w * 0.78f, stripeY + 10f, goldPaint)
        canvas.drawRect(w * 0.78f, stripeY, w.toFloat(), stripeY + 10f, greenPaint)

        // 3. KONAB POS Logo in Circular White Badge
        val logoSize = 96
        val logoBadgeRect = RectF(32f, 28f, 32f + logoSize + 12f, 28f + logoSize + 12f)
        val badgeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawOval(logoBadgeRect, badgeWhitePaint)
        val logoBmp = loadKonabLogoBitmap(context, logoSize)
        if (logoBmp != null) {
            val saveLogo = canvas.save()
            val logoClip = android.graphics.Path().apply {
                addOval(RectF(38f, 34f, 38f + logoSize, 34f + logoSize), android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(logoClip)
            canvas.drawBitmap(logoBmp, 38f, 34f, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(saveLogo)
        }

        // Header Titles
        val orgTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KONAB POS", 160f, 76f, orgTitlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("CARTE PROFESSIONNELLE AGENT DÉPLOYEUR", 160f, 112f, subtitlePaint)

        val standardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 16f
        }
        canvas.drawText("Norme ISO/IEC 7810 ID-1 (CR80) • Réseau Agréé", 160f, 140f, standardPaint)

        // "VALIDE" Badge top-right
        val validBadgeRect = RectF(w - 208f, 44f, w - 34f, 100f)
        val validBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
        canvas.drawRoundRect(validBadgeRect, 24f, 24f, validBgPaint)
        val validTxtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✓ VALIDE", validBadgeRect.centerX(), validBadgeRect.centerY() + 8f, validTxtPaint)

        // 4. Agent Profile Photo (Left side, framed)
        val photoLeft = 36f
        val photoTop = 204f
        val photoW = 218f
        val photoH = 264f
        val photoRect = RectF(photoLeft, photoTop, photoLeft + photoW, photoTop + photoH)

        val photoFrameBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E2E8F0") }
        canvas.drawRoundRect(photoRect, 20f, 20f, photoFrameBg)

        val agentPhoto = loadAgentPhotoBitmap(context, agent.photoUri)
        if (agentPhoto != null) {
            val saveCount = canvas.save()
            val clipPath = android.graphics.Path().apply {
                addRoundRect(photoRect, 20f, 20f, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            // Center-crop scale
            val srcRatio = agentPhoto.width.toFloat() / agentPhoto.height.toFloat()
            val dstRatio = photoW / photoH
            val drawRect = if (srcRatio > dstRatio) {
                val scaledW = photoH * srcRatio
                val offset = (scaledW - photoW) / 2f
                RectF(photoLeft - offset, photoTop, photoLeft + photoW + offset, photoTop + photoH)
            } else {
                val scaledH = photoW / srcRatio
                val offset = (scaledH - photoH) / 2f
                RectF(photoLeft, photoTop - offset, photoLeft + photoW, photoTop + photoH + offset)
            }
            canvas.drawBitmap(agentPhoto, null, drawRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(saveCount)
        } else {
            val initBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#073B9A") }
            canvas.drawRoundRect(photoRect, 20f, 20f, initBg)
            val initTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 96f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val initial = agent.name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "A" }
            canvas.drawText(initial, photoRect.centerX(), photoRect.centerY() + 34f, initTxt)
        }

        // Photo gold/navy border
        val photoBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.parseColor("#073B9A")
        }
        canvas.drawRoundRect(photoRect, 20f, 20f, photoBorderPaint)

        // Reference pill under photo
        val refLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#06245E")
            textSize = 20f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(getConapesReferenceCode(agent.id), photoRect.centerX(), photoTop + photoH + 38f, refLabelPaint)

        // 5. Agent Details (Middle Column)
        val infoX = 286f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val nameValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("NOM & PRÉNOMS", infoX, 226f, labelPaint)
        val safeName = agent.name.ifBlank { "Agent KONAB POS" }
        val displayName = if (safeName.length > 24) safeName.take(22) + "…" else safeName
        canvas.drawText(displayName.uppercase(Locale.ROOT), infoX, 266f, nameValuePaint)

        canvas.drawText("FONCTION & ACCRÉDITATION", infoX, 314f, labelPaint)
        canvas.drawText(agent.role.ifBlank { "Agent Déployeur Agréé" }, infoX, 346f, detailValuePaint)

        canvas.drawText("ZONE / VILLE D'INTERVENTION", infoX, 396f, labelPaint)
        canvas.drawText(agent.zone.ifBlank { "National" }, infoX, 428f, detailValuePaint)

        canvas.drawText("TÉLÉPHONE OFFICIEL", infoX, 478f, labelPaint)
        canvas.drawText(agent.phone.ifBlank { "Non renseigné" }, infoX, 510f, detailValuePaint)

        // 6. Verification QR Code (Right Column)
        val qrSize = 246
        val qrLeft = w - qrSize - 38f
        val qrTop = 204f
        val qrBoxRect = RectF(qrLeft - 14f, qrTop - 14f, qrLeft + qrSize + 14f, qrTop + qrSize + 52f)
        val qrBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val qrBoxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#CBD5E1")
        }
        canvas.drawRoundRect(qrBoxRect, 20f, 20f, qrBoxBg)
        canvas.drawRoundRect(qrBoxRect, 20f, 20f, qrBoxBorder)

        val qrPayload = buildConapesQrPayload(agent)
        val qrBmp = QrCodeHelper.generateQrBitmap(qrPayload, sizePx = qrSize, margin = 2)
        canvas.drawBitmap(qrBmp, qrLeft, qrTop, null)

        val qrCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#073B9A")
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCANNER POUR RÉFÉRENCER", qrBoxRect.centerX(), qrTop + qrSize + 30f, qrCaptionPaint)

        // 7. Bottom Footer Strip
        val footerPaint = Paint().apply { color = Color.parseColor("#06245E") }
        canvas.drawRect(0f, h - 56f, w.toFloat(), h.toFloat(), footerPaint)

        val issuedDate = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
        }
        canvas.drawText("Émise le $issuedDate • Matricule : ${agent.id.uppercase(Locale.ROOT)}", 36f, h - 21f, footerTextPaint)

        val footerRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Réseau Officiel KONAB POS", w - 36f, h - 21f, footerRightPaint)

        // Crisp outer card border
        val outerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#06245E")
        }
        canvas.drawRoundRect(RectF(2f, 2f, w - 2f, h - 2f), 34f, 34f, outerBorder)

        return bitmap
    }

    /**
     * Downloads the CR80 CONAPES Agent Card PNG into the device's Pictures/CONAPES folder
     * using zero-permission MediaStore on Android 10+, with safe internal fallback on older APIs.
     */
    fun downloadIdCardPng(context: Context, agent: AgentUser): File? {
        val cardBmp = renderIdCardBitmap(context, agent)
        val fileName = "Carte_KONAB_POS_${agent.id.uppercase(Locale.ROOT)}_${System.currentTimeMillis()}.png"

        return try {
            // Save a working copy in cacheDir/receipts so it can also be opened/shared via FileProvider
            val receiptsDir = File(context.cacheDir, "receipts")
            if (!receiptsDir.exists()) receiptsDir.mkdirs()
            val cacheFile = File(receiptsDir, fileName)
            FileOutputStream(cacheFile).use { out ->
                cardBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KONAB_POS")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        cardBmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
            }
            Toast.makeText(
                context,
                "Carte KONAB POS téléchargée dans Images/KONAB_POS !",
                Toast.LENGTH_LONG
            ).show()
            cacheFile
        } catch (e: Exception) {
            android.util.Log.e("AgentIdCardHelper", "Erreur téléchargement carte: ${e.message}", e)
            Toast.makeText(context, "Erreur lors du téléchargement de la carte.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * Generates an A4 print-ready PDF containing the KONAB POS Agent Card at exact ISO/IEC 7810 ID-1
     * physical dimensions (85.60 mm × 53.98 mm = 242.6 pt × 153.0 pt) with cut marks and QR verification info.
     */
    fun generateIdCardPdf(context: Context, agent: AgentUser): File {
        val receiptsDir = File(context.cacheDir, "receipts")
        if (!receiptsDir.exists()) receiptsDir.mkdirs()
        val pdfFile = File(receiptsDir, "Carte_KONAB_POS_${agent.id.uppercase(Locale.ROOT)}.pdf")

        val document = PdfDocument()
        // A4 page in PostScript points (1/72 inch): 595 x 842
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        drawCardPdfPage(context, canvas, 595, 842, agent)

        document.finishPage(page)
        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return pdfFile
    }

    private fun drawCardPdfPage(
        context: Context,
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        agent: AgentUser
    ) {
        canvas.drawColor(Color.WHITE)

        // Exact ISO/IEC 7810 ID-1 physical size in PostScript points (1 mm = 72 / 25.4 pt = 2.83465 pt)
        val cardPtWidth = CR80_WIDTH_MM * 2.83465f   // ~242.65 pt
        val cardPtHeight = CR80_HEIGHT_MM * 2.83465f // ~153.01 pt

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#06245E")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("CARTE OFFICIELLE AGENT KONAB POS — FORMAT STANDARD ISO/IEC 7810 ID-1", pageWidth / 2f, 52f, titlePaint)
        canvas.drawText("Dimensions exactes à l'impression (échelle 100%) : 85,60 mm × 53,98 mm (CR80)", pageWidth / 2f, 70f, subtitlePaint)

        val cardLeft = (pageWidth - cardPtWidth) / 2f
        val cardTop = 105f
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardPtWidth, cardTop + cardPtHeight)

        // Subtle cut-guide crop marks around exact CR80 borders
        val cropPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            strokeWidth = 0.8f
        }
        val m = 12f
        // Top-left
        canvas.drawLine(cardLeft - m, cardTop, cardLeft - 3f, cardTop, cropPaint)
        canvas.drawLine(cardLeft, cardTop - m, cardLeft, cardTop - 3f, cropPaint)
        // Top-right
        canvas.drawLine(cardRect.right + 3f, cardTop, cardRect.right + m, cardTop, cropPaint)
        canvas.drawLine(cardRect.right, cardTop - m, cardRect.right, cardTop - 3f, cropPaint)
        // Bottom-left
        canvas.drawLine(cardLeft - m, cardRect.bottom, cardLeft - 3f, cardRect.bottom, cropPaint)
        canvas.drawLine(cardLeft, cardRect.bottom + 3f, cardLeft, cardRect.bottom + m, cropPaint)
        // Bottom-right
        canvas.drawLine(cardRect.right + 3f, cardRect.bottom, cardRect.right + m, cardRect.bottom, cropPaint)
        canvas.drawLine(cardRect.right, cardRect.bottom + 3f, cardRect.right, cardRect.bottom + m, cropPaint)

        val cardBmp = renderIdCardBitmap(context, agent)
        canvas.drawBitmap(cardBmp, null, cardRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Informational block below the physical card
        val infoTop = cardRect.bottom + 42f
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Découpez le long des repères pour obtenir votre badge au format portefeuille CR80.", pageWidth / 2f, infoTop, sectionPaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 10f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Référence KONAB POS : ${getConapesReferenceCode(agent.id)} • Agent : ${agent.name} • Zone : ${agent.zone}", pageWidth / 2f, infoTop + 20f, metaPaint)
    }

    /**
     * Shares or opens the PDF version of the KONAB POS Agent Card.
     */
    fun shareIdCardPdf(context: Context, agent: AgentUser) {
        try {
            val pdfFile = generateIdCardPdf(context, agent)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🪪 Carte Officielle Agent KONAB POS - ${agent.name} (${getConapesReferenceCode(agent.id)})"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Exporter / Partager la Carte KONAB POS (PDF)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de partager le PDF : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prints the KONAB POS Agent Card at exact ISO/IEC 7810 ID-1 physical size using Android's PrintManager.
     */
    fun printIdCard(context: Context, agent: AgentUser) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Service d'impression indisponible sur cet appareil.", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "Carte_KONAB_POS_${agent.id.uppercase(Locale.ROOT)}"
        val adapter = object : PrintDocumentAdapter() {
            private var printAttributes: PrintAttributes? = null

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                printAttributes = newAttributes
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val attrs = printAttributes ?: PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
                val pdfDoc = PrintedPdfDocument(context, attrs)
                try {
                    val page = pdfDoc.startPage(0)
                    if (cancellationSignal?.isCanceled == true) {
                        pdfDoc.close()
                        callback.onWriteCancelled()
                        return
                    }
                    drawCardPdfPage(
                        context = context,
                        canvas = page.canvas,
                        pageWidth = page.info.pageWidth,
                        pageHeight = page.info.pageHeight,
                        agent = agent
                    )
                    pdfDoc.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        pdfDoc.writeTo(out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                } finally {
                    pdfDoc.close()
                }
            }
        }

        try {
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
            printManager.print(jobName, adapter, attrs)
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur d'impression : ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
