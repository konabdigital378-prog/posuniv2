package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import java.io.File
import java.io.FileOutputStream

object ReceiptPrinterHelper {

    fun generateReceiptText(
        shop: ShopProfileEntity,
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("         ${shop.name.uppercase()}\n")
        if (shop.slogan.isNotBlank()) sb.append("     ${shop.slogan}\n")
        sb.append("  ${shop.address}\n")
        sb.append("  Tél: ${shop.phone}\n")
        sb.append("================================\n")
        sb.append("REÇU DE VENTE\n")
        sb.append("N° : ${sale.transactionNumber}\n")
        sb.append("Date : ${CurrencyFormatter.formatDateTime(sale.timestamp)}\n")
        sb.append("Client : ${sale.customerName}\n")
        sb.append("Caissier : ${sale.cashierName}\n")
        sb.append("--------------------------------\n")
        sb.append(String.format("%-16s %3s %9s\n", "PRODUIT", "QTÉ", "TOTAL"))
        sb.append("--------------------------------\n")

        for (item in items) {
            val nameTrunc = if (item.productName.length > 16) item.productName.substring(0, 14) + ".." else item.productName
            sb.append(String.format("%-16s %3d %9s\n", nameTrunc, item.quantity, CurrencyFormatter.formatFCFA(item.totalPrice)))
        }

        sb.append("--------------------------------\n")
        if (sale.discountAmount > 0) {
            sb.append("Sous-total : ${CurrencyFormatter.formatFCFA(sale.subtotal)}\n")
            sb.append("Réduction  : -${CurrencyFormatter.formatFCFA(sale.discountAmount)}\n")
        }
        sb.append("TOTAL : ${CurrencyFormatter.formatFCFA(sale.totalAmount)}\n")
        sb.append("Mode de paiement : ${sale.paymentMethod}\n")
        sb.append("Statut : ${sale.paymentStatus}\n")
        sb.append("--------------------------------\n")
        sb.append("${shop.receiptFooter}\n")
        sb.append("Application : Konab POS\n")
        sb.append("================================\n")
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

        // 80mm thermal receipt dimensions in points (72 points = 1 inch, 80mm ~= 226 points)
        val pageWidth = 320
        val baseHeight = 500 + (items.size * 28)
        val pageHeight = maxOf(600, baseHeight)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        val textPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            textSize = 12f
        }

        val boldPaint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 14f
        }

        val greenPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#087A45")
            isAntiAlias = true
            isFakeBoldText = true
            textSize = 18f
        }

        val grayPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#687780")
            isAntiAlias = true
            textSize = 10f
        }

        val linePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#CCCCCC")
            strokeWidth = 1f
        }

        var y = 40f

        // Brand Name
        val shopName = shop.name.ifBlank { "KONAB POS" }
        val nameWidth = greenPaint.measureText(shopName)
        canvas.drawText(shopName, (pageWidth - nameWidth) / 2, y, greenPaint)
        y += 18f

        if (shop.slogan.isNotBlank()) {
            val sloganWidth = grayPaint.measureText(shop.slogan)
            canvas.drawText(shop.slogan, (pageWidth - sloganWidth) / 2, y, grayPaint)
            y += 16f
        }

        val addressWidth = grayPaint.measureText("${shop.address} • Tél: ${shop.phone}")
        canvas.drawText("${shop.address} • Tél: ${shop.phone}", (pageWidth - addressWidth) / 2, y, grayPaint)
        y += 20f

        // Divider
        canvas.drawLine(20f, y, (pageWidth - 20).toFloat(), y, linePaint)
        y += 20f

        // Receipt title and info
        boldPaint.textSize = 13f
        canvas.drawText("REÇU DE VENTE", 20f, y, boldPaint)
        y += 16f

        textPaint.textSize = 10f
        canvas.drawText("N° : ${sale.transactionNumber}", 20f, y, textPaint)
        y += 14f
        canvas.drawText("Date : ${CurrencyFormatter.formatDateTime(sale.timestamp)}", 20f, y, textPaint)
        y += 14f
        canvas.drawText("Client : ${sale.customerName}", 20f, y, textPaint)
        y += 14f
        canvas.drawText("Caissier : ${sale.cashierName}", 20f, y, textPaint)
        y += 20f

        // Items Header
        canvas.drawLine(20f, y, (pageWidth - 20).toFloat(), y, linePaint)
        y += 15f

        boldPaint.textSize = 10f
        canvas.drawText("PRODUIT", 20f, y, boldPaint)
        canvas.drawText("QTÉ", 170f, y, boldPaint)
        canvas.drawText("P.U", 210f, y, boldPaint)
        val totalHeaderWidth = boldPaint.measureText("TOTAL")
        canvas.drawText("TOTAL", (pageWidth - 20) - totalHeaderWidth, y, boldPaint)
        y += 8f
        canvas.drawLine(20f, y, (pageWidth - 20).toFloat(), y, linePaint)
        y += 16f

        // Items rows
        textPaint.textSize = 10f
        for (item in items) {
            val itemName = if (item.productName.length > 20) item.productName.substring(0, 18) + ".." else item.productName
            canvas.drawText(itemName, 20f, y, textPaint)
            canvas.drawText("${item.quantity}", 175f, y, textPaint)
            canvas.drawText("${item.unitPrice}", 210f, y, textPaint)

            val totalStr = CurrencyFormatter.formatNumber(item.totalPrice)
            val totalW = textPaint.measureText(totalStr)
            canvas.drawText(totalStr, (pageWidth - 20) - totalW, y, textPaint)
            y += 18f
        }

        canvas.drawLine(20f, y, (pageWidth - 20).toFloat(), y, linePaint)
        y += 20f

        // Totals
        if (sale.discountAmount > 0) {
            canvas.drawText("Sous-total :", 140f, y, textPaint)
            val stStr = CurrencyFormatter.formatFCFA(sale.subtotal)
            canvas.drawText(stStr, (pageWidth - 20) - textPaint.measureText(stStr), y, textPaint)
            y += 16f

            canvas.drawText("Réduction :", 140f, y, textPaint)
            val discStr = "-${CurrencyFormatter.formatFCFA(sale.discountAmount)}"
            canvas.drawText(discStr, (pageWidth - 20) - textPaint.measureText(discStr), y, textPaint)
            y += 16f
        }

        boldPaint.textSize = 14f
        canvas.drawText("TOTAL :", 130f, y, boldPaint)
        val totalFormatted = CurrencyFormatter.formatFCFA(sale.totalAmount)
        canvas.drawText(totalFormatted, (pageWidth - 20) - boldPaint.measureText(totalFormatted), y, boldPaint)
        y += 20f

        textPaint.textSize = 10f
        canvas.drawText("Règlement : ${sale.paymentMethod} (${sale.paymentStatus})", 20f, y, textPaint)
        y += 24f

        // Footer Thank You
        val thankYou = shop.receiptFooter.ifBlank { "Merci pour votre confiance ! À très bientôt !" }
        grayPaint.textSize = 11f
        val tyWidth = grayPaint.measureText(thankYou)
        canvas.drawText(thankYou, (pageWidth - tyWidth) / 2, y, grayPaint)
        y += 24f

        // Barcode simulation lines
        val barPaint = Paint().apply { color = Color.BLACK; strokeWidth = 2f }
        val startX = (pageWidth - 160) / 2f
        var currentX = startX
        val barPattern = intArrayOf(2, 1, 3, 1, 1, 4, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 4, 1, 2, 1)
        for (w in barPattern) {
            barPaint.strokeWidth = w.toFloat()
            canvas.drawLine(currentX, y, currentX, y + 25f, barPaint)
            currentX += (w + 2).toFloat()
        }
        y += 35f

        val codeText = sale.transactionNumber
        val codeW = grayPaint.measureText(codeText)
        canvas.drawText(codeText, (pageWidth - codeW) / 2, y, grayPaint)

        document.finishPage(page)

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        return pdfFile
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
            // Fallback to generic share chooser
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
