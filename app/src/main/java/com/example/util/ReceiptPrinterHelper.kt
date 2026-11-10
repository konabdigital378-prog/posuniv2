package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import java.io.File
import java.io.FileOutputStream

object ReceiptPrinterHelper {

    fun buildQrPayload(shop: ShopProfileEntity, sale: SaleEntity): String {
        return when (shop.invoiceQrType) {
            "WHATSAPP" -> {
                val cleanPhone = shop.phone.replace(" ", "").replace("-", "")
                "https://wa.me/$cleanPhone?text=Bonjour%20${shop.name}%20suite%20au%20ticket%20${sale.transactionNumber}"
            }
            "CUSTOM" -> {
                if (shop.invoiceQrCustomText.isNotBlank()) shop.invoiceQrCustomText
                else "KONAB-POS|TICKET:${sale.transactionNumber}|MONTANT:${sale.totalAmount}|BOUTIQUE:${shop.name}"
            }
            else -> "KONAB-POS|TICKET:${sale.transactionNumber}|MONTANT:${sale.totalAmount}|DATE:${sale.timestamp}|BOUTIQUE:${shop.name}"
        }
    }

    fun generateReceiptText(
        shop: ShopProfileEntity,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("🧾 *TICKET DE CAISSE - ${shop.name.uppercase()}*\n")
        if (shop.slogan.isNotBlank()) sb.append("_${shop.slogan}_\n")
        sb.append("📍 ${shop.address}\n")
        sb.append("📞 Tél : ${shop.phone}\n")
        if (shop.invoiceTaxNumber.isNotBlank()) {
            sb.append("📋 IFU/NIF : ${shop.invoiceTaxNumber}\n")
        }
        sb.append("════════════════════════════\n")
        sb.append("🔹 *Reçu N°* : ${sale.transactionNumber}\n")
        sb.append("🔹 *Date* : ${CurrencyFormatter.formatDateTime(sale.timestamp)}\n")
        sb.append("🔹 *Client* : ${sale.customerName}\n")
        if (shop.invoiceShowCashier) {
            sb.append("🔹 *Caissier* : ${sale.cashierName}\n")
        }
        sb.append("────────────────────────────\n")
        sb.append(String.format("%-16s %3s %9s\n", "ARTICLE", "QTÉ", "TOTAL"))
        sb.append("────────────────────────────\n")

        for (item in items) {
            val nameTrunc = if (item.productName.length > 16) item.productName.substring(0, 14) + ".." else item.productName
            sb.append(String.format("%-16s %3d %9s\n", nameTrunc, item.quantity, CurrencyFormatter.formatFCFA(item.totalPrice)))
        }

        sb.append("────────────────────────────\n")
        if (sale.discountAmount > 0) {
            sb.append("Sous-total : ${CurrencyFormatter.formatFCFA(sale.subtotal)}\n")
            sb.append("Réduction  : -${CurrencyFormatter.formatFCFA(sale.discountAmount)}\n")
        }
        sb.append("💰 *TOTAL NET : ${CurrencyFormatter.formatFCFA(sale.totalAmount)}*\n")
        sb.append("💳 Règlement : ${sale.paymentMethod} (${sale.paymentStatus})\n")

        if (shop.invoiceShowPaymentDetails && shop.invoicePaymentDetails.isNotBlank()) {
            sb.append("──────── Paiement Mobile ────────\n")
            sb.append("📱 ${shop.invoicePaymentDetails}\n")
        }

        sb.append("════════════════════════════\n")
        if (shop.invoiceLegalNotice.isNotBlank()) {
            sb.append("⚠️ _${shop.invoiceLegalNotice}_\n")
        }
        sb.append("✨ _${shop.receiptFooter}_\n\n")
        sb.append("🔒 Reçu certifié et authentifié par *Konab POS*\n")
        return sb.toString()
    }

    fun generateReceiptPdf(
        context: Context,
        shop: ShopProfileEntity,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): File {
        val receiptDir = File(context.cacheDir, "receipts")
        if (!receiptDir.exists()) receiptDir.mkdirs()

        val pdfFile = File(receiptDir, "Recu_${sale.transactionNumber}.pdf")

        // Dimensions according to paperSize
        val isA4 = shop.invoicePaperSize == "A4_INVOICE"
        val is80mm = shop.invoicePaperSize == "THERMAL_80"
        val pageWidth = if (isA4) 520 else if (is80mm) 430 else 380

        val extraHeight = (if (shop.invoiceShowPaymentDetails && shop.invoicePaymentDetails.isNotBlank()) 60 else 0) +
                (if (shop.invoiceTaxNumber.isNotBlank()) 20 else 0) +
                (if (shop.invoiceShowQrCode) 150 else 20) +
                (items.size * 26)
        val pageHeight = maxOf(760, 640 + extraHeight)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        val textPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
            textSize = 11f
        }

        val boldPaint = Paint().apply {
            color = Color.parseColor("#0F172A")
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 13f
        }

        val brandPaint = Paint().apply {
            color = Color.parseColor("#0B4BD9")
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 18f
        }

        val grayPaint = Paint().apply {
            color = Color.parseColor("#64748B")
            isAntiAlias = true
            textSize = 10f
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        var y = 28f

        // 1. Logo (if enabled)
        if (shop.invoiceShowLogo) {
            try {
                val logoBmp = ShopLogoHelper.loadLogoBitmap(context, shop.logoUri)
                    ?: BitmapFactory.decodeResource(context.resources, com.example.R.drawable.ic_konab_logo)
                if (logoBmp != null) {
                    val logoSize = 52
                    val scaledLogo = Bitmap.createScaledBitmap(logoBmp, logoSize, logoSize, true)
                    canvas.drawBitmap(scaledLogo, ((pageWidth - logoSize) / 2).toFloat(), y, null)
                    y += logoSize + 14f
                }
            } catch (_: Exception) {}
        }

        // 2. Shop Header according to invoiceHeaderStyle
        val shopName = shop.name.ifBlank { "KONAB POS" }.uppercase()

        if (shop.invoiceHeaderStyle == "CADRE") {
            val headerBox = RectF(20f, y, (pageWidth - 20).toFloat(), y + 74f)
            val headerBg = Paint().apply { color = Color.parseColor("#EFF6FF"); isAntiAlias = true }
            val headerBorder = Paint().apply { color = Color.parseColor("#93C5FD"); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
            canvas.drawRoundRect(headerBox, 8f, 8f, headerBg)
            canvas.drawRoundRect(headerBox, 8f, 8f, headerBorder)

            val nameWidth = brandPaint.measureText(shopName)
            canvas.drawText(shopName, (pageWidth - nameWidth) / 2, y + 24f, brandPaint)

            if (shop.slogan.isNotBlank()) {
                val sloganWidth = grayPaint.measureText(shop.slogan)
                canvas.drawText(shop.slogan, (pageWidth - sloganWidth) / 2, y + 42f, grayPaint)
            }
            val addressText = "${shop.address} • Tél: ${shop.phone}"
            val addressWidth = grayPaint.measureText(addressText)
            canvas.drawText(addressText, (pageWidth - addressWidth) / 2, y + 58f, grayPaint)
            y += 86f
        } else {
            val nameWidth = brandPaint.measureText(shopName)
            canvas.drawText(shopName, (pageWidth - nameWidth) / 2, y, brandPaint)
            y += 18f

            if (shop.slogan.isNotBlank()) {
                val sloganWidth = grayPaint.measureText(shop.slogan)
                canvas.drawText(shop.slogan, (pageWidth - sloganWidth) / 2, y, grayPaint)
                y += 15f
            }

            val addressText = "${shop.address} • Tél: ${shop.phone}"
            val addressWidth = grayPaint.measureText(addressText)
            canvas.drawText(addressText, (pageWidth - addressWidth) / 2, y, grayPaint)
            y += 18f
        }

        // Fiscal Number if set
        if (shop.invoiceTaxNumber.isNotBlank()) {
            val taxText = "NIF / IFU / RCCM : ${shop.invoiceTaxNumber}"
            boldPaint.textSize = 10f
            boldPaint.color = Color.parseColor("#334155")
            val taxWidth = boldPaint.measureText(taxText)
            canvas.drawText(taxText, (pageWidth - taxWidth) / 2, y, boldPaint)
            y += 18f
        }

        // Header Pill Tag
        val tagPaint = Paint().apply { color = Color.parseColor("#EFF6FF"); isAntiAlias = true }
        val tagRect = RectF((pageWidth / 2 - 86).toFloat(), y, (pageWidth / 2 + 86).toFloat(), y + 22f)
        canvas.drawRoundRect(tagRect, 6f, 6f, tagPaint)

        val tagTextPaint = Paint().apply { color = Color.parseColor("#0B4BD9"); isAntiAlias = true; isFakeBoldText = true; textSize = 10f }
        val tagTitle = if (isA4) "FACTURE OFFICIELLE" else "TICKET DE VENTE OFFICIEL"
        val tagTitleW = tagTextPaint.measureText(tagTitle)
        canvas.drawText(tagTitle, (pageWidth - tagTitleW) / 2, y + 15f, tagTextPaint)
        y += 34f

        // Transaction Details Box
        val boxPaint = Paint().apply { color = Color.parseColor("#F8FAFC"); isAntiAlias = true }
        val boxBorder = Paint().apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
        val detailBox = RectF(20f, y, (pageWidth - 20).toFloat(), y + 68f)
        canvas.drawRoundRect(detailBox, 10f, 10f, boxPaint)
        canvas.drawRoundRect(detailBox, 10f, 10f, boxBorder)

        boldPaint.textSize = 11f
        canvas.drawText("N° Transaction : ${sale.transactionNumber}", 32f, y + 20f, boldPaint)
        textPaint.textSize = 10f
        canvas.drawText("Date : ${CurrencyFormatter.formatDateTime(sale.timestamp)}", 32f, y + 36f, textPaint)
        canvas.drawText("Client : ${sale.customerName}", 32f, y + 52f, textPaint)

        if (shop.invoiceShowCashier) {
            val cashierText = "Caissier : ${sale.cashierName}"
            val cashierW = textPaint.measureText(cashierText)
            canvas.drawText(cashierText, (pageWidth - 32) - cashierW, y + 36f, textPaint)
        }

        val paymentText = "Paiement : ${sale.paymentMethod}"
        val paymentW = textPaint.measureText(paymentText)
        canvas.drawText(paymentText, (pageWidth - 32) - paymentW, y + 52f, textPaint)

        y += 82f

        // Items Table Header with light background
        val thBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        val thRect = RectF(20f, y, (pageWidth - 20).toFloat(), y + 22f)
        canvas.drawRoundRect(thRect, 6f, 6f, thBg)

        boldPaint.textSize = 10f
        boldPaint.color = Color.parseColor("#334155")
        canvas.drawText("DÉSIGNATION", 30f, y + 15f, boldPaint)
        val colQty = if (isA4) 280f else 200f
        val colPu = if (isA4) 360f else 245f
        canvas.drawText("QTÉ", colQty, y + 15f, boldPaint)
        canvas.drawText("P.U.", colPu, y + 15f, boldPaint)
        val thTotal = "TOTAL (FCFA)"
        val thTotalW = boldPaint.measureText(thTotal)
        canvas.drawText(thTotal, (pageWidth - 30) - thTotalW, y + 15f, boldPaint)
        y += 28f

        // Table Rows
        textPaint.textSize = 10.5f
        textPaint.color = Color.parseColor("#1E293B")
        for (item in items) {
            val maxLen = if (isA4) 34 else 22
            val itemName = if (item.productName.length > maxLen) item.productName.substring(0, maxLen - 2) + ".." else item.productName
            canvas.drawText(itemName, 30f, y, textPaint)
            canvas.drawText("${item.quantity}", colQty + 5f, y, textPaint)
            canvas.drawText(CurrencyFormatter.formatNumber(item.unitPrice), colPu, y, textPaint)

            val totalStr = CurrencyFormatter.formatNumber(item.totalPrice)
            val totalW = textPaint.measureText(totalStr)
            canvas.drawText(totalStr, (pageWidth - 30) - totalW, y, textPaint)

            y += 20f
        }

        canvas.drawLine(20f, y, (pageWidth - 20).toFloat(), y, linePaint)
        y += 16f

        // Subtotals if discount applied
        if (sale.discountAmount > 0) {
            canvas.drawText("Sous-total :", pageWidth - 190f, y, textPaint)
            val stStr = CurrencyFormatter.formatFCFA(sale.subtotal)
            canvas.drawText(stStr, (pageWidth - 30) - textPaint.measureText(stStr), y, textPaint)
            y += 16f

            val discPaint = Paint().apply {
                color = Color.parseColor("#DC2626")
                textSize = 10.5f
                isAntiAlias = true
            }
            canvas.drawText("Réduction appliquée :", pageWidth - 190f, y, discPaint)
            val discStr = "-${CurrencyFormatter.formatFCFA(sale.discountAmount)}"
            canvas.drawText(discStr, (pageWidth - 30) - discPaint.measureText(discStr), y, discPaint)
            y += 18f
        }

        // Prominent Total Net Card
        val totalCard = RectF(20f, y, (pageWidth - 20).toFloat(), y + 42f)
        val totalBg = Paint().apply { color = Color.parseColor("#EFF6FF") }
        val totalBorder = Paint().apply {
            color = Color.parseColor("#3B82F6")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(totalCard, 10f, 10f, totalBg)
        canvas.drawRoundRect(totalCard, 10f, 10f, totalBorder)

        boldPaint.textSize = 14f
        boldPaint.color = Color.parseColor("#1E293B")
        canvas.drawText("TOTAL NET :", 36f, y + 26f, boldPaint)

        val totalValuePaint = Paint().apply {
            color = Color.parseColor("#0B4BD9")
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 17f
        }
        val totalFormatted = CurrencyFormatter.formatFCFA(sale.totalAmount)
        val totalFormattedW = totalValuePaint.measureText(totalFormatted)
        canvas.drawText(totalFormatted, (pageWidth - 36) - totalFormattedW, y + 27f, totalValuePaint)
        y += 58f

        // Payment status badge
        textPaint.textSize = 10f
        textPaint.color = Color.parseColor("#475569")
        val statText = "Règlement : ${sale.paymentMethod} • Statut : ${sale.paymentStatus}"
        val statW = textPaint.measureText(statText)
        canvas.drawText(statText, (pageWidth - statW) / 2, y, textPaint)
        y += 20f

        // Mobile Money Payment Details Box (if enabled)
        if (shop.invoiceShowPaymentDetails && shop.invoicePaymentDetails.isNotBlank()) {
            val payBox = RectF(20f, y, (pageWidth - 20).toFloat(), y + 40f)
            val payBg = Paint().apply { color = Color.parseColor("#F1F5F9"); isAntiAlias = true }
            val payBorder = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true }
            canvas.drawRoundRect(payBox, 8f, 8f, payBg)
            canvas.drawRoundRect(payBox, 8f, 8f, payBorder)

            val payTitle = "Règlement Mobile Money accepté :"
            boldPaint.textSize = 9.5f
            boldPaint.color = Color.parseColor("#334155")
            canvas.drawText(payTitle, 30f, y + 15f, boldPaint)

            textPaint.textSize = 9.5f
            textPaint.color = Color.parseColor("#475569")
            canvas.drawText(shop.invoicePaymentDetails, 30f, y + 30f, textPaint)
            y += 52f
        }

        // Legal Notice if set
        if (shop.invoiceLegalNotice.isNotBlank()) {
            grayPaint.textSize = 9.5f
            val noticeW = grayPaint.measureText(shop.invoiceLegalNotice)
            canvas.drawText(shop.invoiceLegalNotice, (pageWidth - noticeW) / 2, y, grayPaint)
            y += 16f
        }

        // Footer Thank You Note
        val thankYou = shop.receiptFooter.ifBlank { "Merci pour votre confiance ! À très bientôt !" }
        grayPaint.textSize = 11f
        val tyWidth = grayPaint.measureText(thankYou)
        canvas.drawText(thankYou, (pageWidth - tyWidth) / 2, y, grayPaint)
        y += 22f

        // QR Code (if enabled)
        if (shop.invoiceShowQrCode) {
            val qrPayload = buildQrPayload(shop, sale)
            try {
                val qrSize = 100
                val qrBmp = QrCodeHelper.generateQrBitmap(qrPayload, sizePx = qrSize, margin = 1)
                val qrX = ((pageWidth - qrSize) / 2).toFloat()
                canvas.drawBitmap(qrBmp, qrX, y, null)
                y += qrSize + 10f

                val scanHint = when (shop.invoiceQrType) {
                    "WHATSAPP" -> "Scannez pour contacter le magasin sur WhatsApp"
                    "CUSTOM" -> "Scannez pour accéder au lien du magasin"
                    else -> "Scannez ce QR Code pour vérifier l'authenticité"
                }
                val scanW = grayPaint.measureText(scanHint)
                canvas.drawText(scanHint, (pageWidth - scanW) / 2, y, grayPaint)
                y += 14f
            } catch (_: Exception) {}
        }

        val codeText = "Ticket N° ${sale.transactionNumber} • Konab POS"
        val codeW = boldPaint.measureText(codeText)
        boldPaint.textSize = 10f
        boldPaint.color = Color.parseColor("#64748B")
        canvas.drawText(codeText, (pageWidth - codeW) / 2, y, boldPaint)

        document.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return pdfFile
    }

    fun openReceiptPdf(context: Context, pdfFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Reçu enregistré dans le dossier de l'application", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareReceiptToWhatsApp(
        context: Context,
        shop: ShopProfileEntity,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ) {
        val pdfFile = generateReceiptPdf(context, shop, sale, items)
        val textSummary = generateReceiptText(shop, sale, items)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, textSummary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, textSummary)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Partager le reçu")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}
