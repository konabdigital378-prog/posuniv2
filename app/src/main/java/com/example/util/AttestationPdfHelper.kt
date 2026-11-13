package com.example.util

import android.content.ClipData
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
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActivationAttestationData(
    val shopName: String,
    val clientPhone: String,
    val shopCity: String = "",
    val businessType: String = "Boutique",
    val tier: String,
    val durationDays: Int,
    val licenseKey: String,
    val agentId: String = "",
    val agentName: String,
    val agentPhone: String = "",
    val activatedAtMillis: Long = System.currentTimeMillis()
)

object AttestationPdfHelper {

    fun normalizePhoneForWhatsApp(rawPhone: String): String {
        var digits = rawPhone.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+")) {
            digits = digits.substring(1)
        } else if (digits.startsWith("00")) {
            digits = digits.substring(2)
        }
        digits = digits.replace(Regex("[^0-9]"), "")
        // Default Burkina Faso country code (+226) when 8-digit national number is provided
        if (digits.length == 8) {
            digits = "226$digits"
        }
        return digits
    }

    fun generateActivationAttestationPdf(
        context: Context,
        data: ActivationAttestationData
    ): File? {
        return try {
            val outDir = File(context.cacheDir, "receipts")
            if (!outDir.exists()) outDir.mkdirs()

            val safeName = data.shopName
                .ifBlank { "Boutique" }
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .take(28)
            val pdfFile = File(outDir, "Attestation_KONAB_${safeName}_${data.activatedAtMillis}.pdf")

            val pageWidth = 595
            val pageHeight = 842
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // 1. Clean White Page Background
            canvas.drawColor(Color.WHITE)

            // 2. Executive Header Banner
            val headerHeight = 148f
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, pageWidth.toFloat(), headerHeight,
                    Color.parseColor("#06245E"),
                    Color.parseColor("#0B4BD9"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerHeight, headerPaint)

            // Accent Orange-Green bar under header
            val accentPaint = Paint().apply { color = Color.parseColor("#FF7900") }
            canvas.drawRect(0f, headerHeight, pageWidth.toFloat(), headerHeight + 6f, accentPaint)

            // KONAB POS Logo
            try {
                val logoRaw = BitmapFactory.decodeResource(context.resources, R.drawable.ic_konab_logo)
                if (logoRaw != null) {
                    val logoBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                    canvas.drawCircle(64f, 74f, 34f, logoBg)
                    val scaled = Bitmap.createScaledBitmap(logoRaw, 54, 54, true)
                    canvas.drawBitmap(scaled, 37f, 47f, null)
                }
            } catch (_: Exception) {}

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 20f
            }
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FED7AA")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11.5f
            }
            val metaHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 10.5f
            }

            canvas.drawText("KONAB POS • ATTESTATION D'ACTIVATION", 114f, 54f, titlePaint)
            canvas.drawText("CERTIFICAT OFFICIEL DE DÉPLOIEMENT & LICENCE CLIENT", 114f, 76f, subHeaderPaint)
            val refId = "ATT-${data.activatedAtMillis.toString().takeLast(8)}"
            val dateStr = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(data.activatedAtMillis))
            canvas.drawText("Référence : $refId   |   Émise le : $dateStr", 114f, 102f, metaHeaderPaint)
            canvas.drawText("Statut : COMPTE ACTIVÉ & SYNCHRONISÉ CLOUD", 114f, 120f, metaHeaderPaint)

            // Typography paints
            val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#06245E")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13.5f
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 11.5f
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 12f
            }
            val greenValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#059669")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13f
            }
            val boxBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
            }
            val boxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            var y = 186f

            // 3. Section 1: Informations de la Boutique & Client
            val shopBox = RectF(36f, y, pageWidth - 36f, y + 168f)
            canvas.drawRoundRect(shopBox, 12f, 12f, boxBgPaint)
            canvas.drawRoundRect(shopBox, 12f, 12f, boxBorderPaint)

            canvas.drawText("1. IDENTITÉ DE LA BOUTIQUE ACTIVÉE", 54f, y + 28f, sectionTitlePaint)
            val dividerPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }
            canvas.drawLine(54f, y + 40f, pageWidth - 54f, y + 40f, dividerPaint)

            var rowY = y + 66f
            fun drawPair(label: String, value: String, vPaint: Paint = valuePaint) {
                canvas.drawText(label, 54f, rowY, labelPaint)
                canvas.drawText(value.ifBlank { "Non renseigné" }, 235f, rowY, vPaint)
                rowY += 26f
            }

            drawPair("Nom unique de la boutique :", data.shopName.uppercase(Locale.FRANCE))
            drawPair("Téléphone client (WhatsApp) :", data.clientPhone, greenValuePaint)
            drawPair("Secteur d'activité :", data.businessType)
            drawPair("Ville / Localisation :", data.shopCity.ifBlank { "Burkina Faso" })

            y += 190f

            // 4. Section 2: Licence & Forfait Activé
            val licenseBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#ECFDF5") }
            val licenseBoxBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#10B981")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            val licenseBox = RectF(36f, y, pageWidth - 36f, y + 168f)
            canvas.drawRoundRect(licenseBox, 12f, 12f, licenseBoxBg)
            canvas.drawRoundRect(licenseBox, 12f, 12f, licenseBoxBorder)

            canvas.drawText("2. LICENCE & FORFAIT ACTIVÉ", 54f, y + 28f, sectionTitlePaint)
            canvas.drawLine(54f, y + 40f, pageWidth - 54f, y + 40f, dividerPaint)

            val expiryMillis = data.activatedAtMillis + (data.durationDays.toLong() * 24L * 60L * 60L * 1000L)
            val expiryStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(expiryMillis))
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0B4BD9")
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 13f
            }

            rowY = y + 66f
            drawPair("Forfait souscrit :", data.tier, greenValuePaint)
            drawPair("Durée de validité :", "${data.durationDays} jours (jusqu'au $expiryStr)")
            drawPair("Clé de licence officielle :", data.licenseKey.ifBlank { "ACTIVÉE PAR AGENT" }, monoPaint)
            drawPair("Stockage produits :", "Temporaire local (confidentialité préservée)")

            y += 190f

            // 5. Section 3: Agent Déployeur & QR Code d'Authenticité
            val agentBox = RectF(36f, y, pageWidth - 36f, y + 178f)
            canvas.drawRoundRect(agentBox, 12f, 12f, boxBgPaint)
            canvas.drawRoundRect(agentBox, 12f, 12f, boxBorderPaint)

            canvas.drawText("3. AGENT DÉPLOYEUR AGRÉÉ & CONTRÔLE QR", 54f, y + 28f, sectionTitlePaint)
            canvas.drawLine(54f, y + 40f, pageWidth - 54f, y + 40f, dividerPaint)

            rowY = y + 66f
            canvas.drawText("Agent déployeur :", 54f, rowY, labelPaint)
            canvas.drawText(data.agentName.ifBlank { "Agent KONAB POS" }, 185f, rowY, valuePaint)
            rowY += 24f
            canvas.drawText("Matricule agent :", 54f, rowY, labelPaint)
            canvas.drawText(data.agentId.ifBlank { "KONAB-AGT" }, 185f, rowY, valuePaint)
            rowY += 24f
            canvas.drawText("Contact agent :", 54f, rowY, labelPaint)
            canvas.drawText(data.agentPhone.ifBlank { "Support KONAB POS" }, 185f, rowY, valuePaint)
            rowY += 24f
            canvas.drawText("Date validation :", 54f, rowY, labelPaint)
            canvas.drawText(dateStr, 185f, rowY, valuePaint)

            // Compact QR payload <= 110 bytes for QrCodeHelper Version 5/6
            val qrPayload = "KONAB-ATT|${data.shopName.take(18)}|${data.tier.take(12)}|${data.licenseKey.take(22)}|${data.agentId.take(12)}"
            try {
                val qrBmp = QrCodeHelper.generateQrBitmap(qrPayload, sizePx = 112, margin = 1)
                canvas.drawBitmap(qrBmp, pageWidth - 166f, y + 50f, null)
                val qrCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#475569")
                    textSize = 8.5f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("Contrôle d'authenticité", pageWidth - 110f, y + 170f, qrCaptionPaint)
            } catch (_: Exception) {}

            // 6. Footer Banner
            val footerY = pageHeight - 62f
            canvas.drawLine(36f, footerY - 12f, pageWidth - 36f, footerY - 12f, dividerPaint)
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 9.5f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "Cette attestation certifie l'activation officielle du point de vente sous KONAB POS.",
                pageWidth / 2f,
                footerY + 4f,
                footerPaint
            )
            canvas.drawText(
                "Document généré automatiquement • Service Client & Réseau Agents KONAB POS",
                pageWidth / 2f,
                footerY + 20f,
                footerPaint
            )

            document.finishPage(page)
            FileOutputStream(pdfFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()
            pdfFile
        } catch (e: Exception) {
            android.util.Log.e("AttestationPdfHelper", "Erreur génération attestation PDF: ${e.message}", e)
            null
        }
    }

    fun sendAttestationPdfToClientWhatsApp(
        context: Context,
        data: ActivationAttestationData,
        existingPdfFile: File? = null
    ): Boolean {
        val pdfFile = existingPdfFile?.takeIf { it.exists() }
            ?: generateActivationAttestationPdf(context, data)
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(context, "Impossible de générer l'attestation PDF.", Toast.LENGTH_SHORT).show()
            return false
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur accès fichier PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }

        val caption = buildString {
            appendLine("Bonjour *${data.shopName}*,")
            appendLine("Votre boutique *KONAB POS* est officiellement activée !")
            appendLine("• Forfait : *${data.tier}* (${data.durationDays} jours)")
            if (data.licenseKey.isNotBlank()) appendLine("• Clé : `${data.licenseKey}`")
            appendLine("• Agent déployeur : ${data.agentName}${if (data.agentPhone.isNotBlank()) " (${data.agentPhone})" else ""}")
            append("Veuillez trouver ci-joint votre attestation officielle d'activation en PDF.")
        }

        val normalizedPhone = normalizePhoneForWhatsApp(data.clientPhone)

        fun buildWhatsAppIntent(packageName: String?): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Attestation Activation KONAB POS - ${data.shopName}")
                putExtra(Intent.EXTRA_TEXT, caption)
                if (normalizedPhone.isNotBlank()) {
                    putExtra("jid", "$normalizedPhone@s.whatsapp.net")
                }
                clipData = ClipData.newUri(context.contentResolver, "Attestation PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!packageName.isNullOrBlank()) {
                    setPackage(packageName)
                }
            }
        }

        val pm = context.packageManager
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in whatsappPackages) {
            val intent = buildWhatsAppIntent(pkg)
            if (intent.resolveActivity(pm) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {}
            }
        }

        // Fallback to system share chooser with the PDF attached
        return try {
            if (normalizedPhone.isBlank()) {
                Toast.makeText(context, "Sélectionnez WhatsApp pour envoyer l'attestation PDF.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "WhatsApp non détecté directement — ouverture du partage PDF.", Toast.LENGTH_SHORT).show()
            }
            val chooser = Intent.createChooser(buildWhatsAppIntent(null), "Envoyer l'attestation PDF au client")
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Aucune application compatible pour partager le PDF.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openAttestationPdfPreview(
        context: Context,
        data: ActivationAttestationData,
        existingPdfFile: File? = null
    ) {
        val pdfFile = existingPdfFile?.takeIf { it.exists() }
            ?: generateActivationAttestationPdf(context, data)
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(context, "Impossible de générer le PDF.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Ouvrir l'attestation PDF"))
        } catch (_: Exception) {
            Toast.makeText(context, "Aucun lecteur PDF installé sur cet appareil.", Toast.LENGTH_SHORT).show()
        }
    }
}
