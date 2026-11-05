package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

enum class PaperWidth(val columns: Int, val label: String) {
    WIDTH_58MM(32, "58 mm (Standard Mobile)"),
    WIDTH_80MM(48, "80 mm (Grand format de caisse)")
}

data class BluetoothPrinterDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false
)

object BluetoothPrinterHelper {

    private const val PREFS_NAME = "konab_printer_prefs"
    private const val KEY_PRINTER_ADDRESS = "saved_printer_address"
    private const val KEY_PRINTER_NAME = "saved_printer_name"
    private const val KEY_PAPER_WIDTH = "saved_paper_width"

    // Standard SPP UUID for Bluetooth Serial Port Profile (used by POS thermal printers)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS Commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val GS_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    private val GS_TEXT_DOUBLE_WIDTH = byteArrayOf(0x1D, 0x21, 0x10)
    private val GS_TEXT_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val GS_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val FEED_LINE = byteArrayOf(0x0A)
    private val PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0

    private val CP850: Charset = try {
        Charset.forName("CP850")
    } catch (e: Exception) {
        Charset.defaultCharset()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedPrinter(context: Context): BluetoothPrinterDevice? {
        val prefs = getPrefs(context)
        val address = prefs.getString(KEY_PRINTER_ADDRESS, null) ?: return null
        val name = prefs.getString(KEY_PRINTER_NAME, "Imprimante POS") ?: "Imprimante POS"
        return BluetoothPrinterDevice(name = name, address = address)
    }

    fun savePrinter(context: Context, printer: BluetoothPrinterDevice) {
        getPrefs(context).edit()
            .putString(KEY_PRINTER_ADDRESS, printer.address)
            .putString(KEY_PRINTER_NAME, printer.name)
            .apply()
    }

    fun getSavedPaperWidth(context: Context): PaperWidth {
        val name = getPrefs(context).getString(KEY_PAPER_WIDTH, PaperWidth.WIDTH_58MM.name)
        return try {
            PaperWidth.valueOf(name ?: PaperWidth.WIDTH_58MM.name)
        } catch (e: Exception) {
            PaperWidth.WIDTH_58MM
        }
    }

    fun savePaperWidth(context: Context, paperWidth: PaperWidth) {
        getPrefs(context).edit()
            .putString(KEY_PAPER_WIDTH, paperWidth.name)
            .apply()
    }

    /**
     * Lists paired/bonded Bluetooth devices that can be thermal printers
     */
    @SuppressLint("MissingPermission")
    fun getBondedPrinters(context: Context): List<BluetoothPrinterDevice> {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                return emptyList()
            }

            adapter.bondedDevices.map { device ->
                BluetoothPrinterDevice(
                    name = device.name ?: "Appareil Bluetooth (${device.address})",
                    address = device.address
                )
            }.sortedBy { it.name }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Sends raw ESC/POS bytes to the given Bluetooth printer address via RFCOMM
     */
    @SuppressLint("MissingPermission")
    suspend fun printBytes(context: Context, deviceAddress: String, data: ByteArray): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
                    ?: return@withContext Result.failure(IOException("Bluetooth non disponible sur cet appareil"))

                if (!adapter.isEnabled) {
                    return@withContext Result.failure(IOException("Le Bluetooth est désactivé. Veuillez l'activer."))
                }

                val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
                    ?: return@withContext Result.failure(IOException("Périphérique Bluetooth introuvable ($deviceAddress)"))

                // Cancel discovery as it slows down connection
                try {
                    adapter.cancelDiscovery()
                } catch (ignored: Exception) {}

                // Create RFCOMM socket to SPP UUID
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                val outputStream = socket.outputStream
                outputStream.write(data)
                outputStream.flush()

                // Brief pause to allow physical thermal head buffer to complete
                kotlinx.coroutines.delay(200)

                Result.success(Unit)
            } catch (se: SecurityException) {
                Result.failure(IOException("Permission Bluetooth non accordée. Autorisez la recherche et la connexion Bluetooth."))
            } catch (ioe: IOException) {
                Result.failure(IOException("Erreur de connexion à l'imprimante : ${ioe.localizedMessage ?: "Vérifiez que l'imprimante est allumée et appairée."}"))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Formats receipt bytes in ESC/POS according to 58mm or 80mm column width
     */
    fun buildSaleReceiptBytes(
        shop: ShopProfileEntity,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        paperWidth: PaperWidth
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cols = paperWidth.columns

        fun print(text: String) {
            out.write(text.toByteArray(CP850))
        }

        fun println(text: String = "") {
            print(text)
            out.write(FEED_LINE)
        }

        fun divider(char: Char = '-') {
            println(char.toString().repeat(cols))
        }

        fun doubleDivider() {
            divider('=')
        }

        // Initialize printer
        out.write(ESC_INIT)

        // HEADER
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write(GS_TEXT_DOUBLE_HEIGHT)
        val shopName = shop.name.ifBlank { "KONAB POS" }.uppercase()
        println(shopName)
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)

        if (shop.slogan.isNotBlank()) {
            println(shop.slogan)
        }
        if (shop.address.isNotBlank()) {
            println(shop.address)
        }
        if (shop.phone.isNotBlank()) {
            println("Tél: ${shop.phone}")
        }

        doubleDivider()

        // RECEIPT INFO
        out.write(ESC_ALIGN_LEFT)
        out.write(ESC_BOLD_ON)
        println("TICKET DE CAISSE")
        out.write(ESC_BOLD_OFF)
        println("N° Ticket : ${sale.transactionNumber}")
        println("Date      : ${CurrencyFormatter.formatDateTime(sale.timestamp)}")
        println("Client    : ${sale.customerName}")
        println("Caissier  : ${sale.cashierName}")

        divider()

        // ITEM TABLE
        out.write(ESC_BOLD_ON)
        if (paperWidth == PaperWidth.WIDTH_58MM) {
            // 32 columns: "DESIGNATION     QTE    TOTAL"
            //               15 chars       5ch   12ch = 32
            val col1 = "ARTICLE".padEnd(16)
            val col2 = "QTE".padStart(4)
            val col3 = "TOTAL".padStart(12)
            println("$col1$col2$col3")
        } else {
            // 48 columns: "ARTICLE                       QTE     P.U       TOTAL"
            //              24 chars                      4ch    9ch       11ch = 48
            val col1 = "ARTICLE".padEnd(24)
            val col2 = "QTE".padStart(4)
            val col3 = "P.U".padStart(9)
            val col4 = "TOTAL".padStart(11)
            println("$col1$col2$col3$col4")
        }
        out.write(ESC_BOLD_OFF)
        divider()

        // Items rows
        for (item in items) {
            val totalStr = CurrencyFormatter.formatFCFA(item.totalPrice)
            if (paperWidth == PaperWidth.WIDTH_58MM) {
                val maxNameLen = 16
                val name = if (item.productName.length > maxNameLen) {
                    item.productName.take(maxNameLen - 1) + "."
                } else {
                    item.productName.padEnd(maxNameLen)
                }
                val qte = item.quantity.toString().padStart(4)
                val tot = totalStr.padStart(12)
                println("$name$qte$tot")
            } else {
                val maxNameLen = 24
                val name = if (item.productName.length > maxNameLen) {
                    item.productName.take(maxNameLen - 1) + "."
                } else {
                    item.productName.padEnd(maxNameLen)
                }
                val qte = item.quantity.toString().padStart(4)
                val pu = CurrencyFormatter.formatNumber(item.unitPrice).padStart(9)
                val tot = totalStr.padStart(11)
                println("$name$qte$pu$tot")
            }
        }

        divider()

        // TOTALS
        if (sale.discountAmount > 0) {
            printRowJustified("Sous-total :", CurrencyFormatter.formatFCFA(sale.subtotal), cols, out)
            printRowJustified("Remise :", "-${CurrencyFormatter.formatFCFA(sale.discountAmount)}", cols, out)
            divider()
        }

        // Grand Total in Bold Double Height
        out.write(ESC_ALIGN_LEFT)
        out.write(ESC_BOLD_ON)
        out.write(GS_TEXT_DOUBLE_SIZE)
        val totalLabel = "TOTAL :"
        val totalVal = CurrencyFormatter.formatFCFA(sale.totalAmount)
        val remainingSpace = cols / 2 - totalLabel.length - totalVal.length
        if (remainingSpace > 0) {
            println(totalLabel + " ".repeat(remainingSpace) + totalVal)
        } else {
            println("$totalLabel $totalVal")
        }
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)

        divider()

        // PAYMENT INFO
        printRowJustified("Règlement :", sale.paymentMethod, cols, out)
        printRowJustified("Statut    :", sale.paymentStatus, cols, out)

        divider()

        // FOOTER
        out.write(ESC_ALIGN_CENTER)
        val footer = shop.receiptFooter.ifBlank { "Merci pour votre visite ! A bientot !" }
        println(footer)
        println("Propulse par Konab POS")
        println("www.konabpos.com")

        // Extra line feeds and partial paper cut
        out.write(FEED_LINE)
        out.write(FEED_LINE)
        out.write(FEED_LINE)
        out.write(PAPER_CUT)

        return out.toByteArray()
    }

    /**
     * Generates a test receipt to verify printer connectivity and paper width
     */
    fun buildTestReceiptBytes(paperWidth: PaperWidth): ByteArray {
        val out = ByteArrayOutputStream()
        val cols = paperWidth.columns

        fun println(text: String = "") {
            out.write(text.toByteArray(CP850))
            out.write(FEED_LINE)
        }

        out.write(ESC_INIT)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        out.write(GS_TEXT_DOUBLE_HEIGHT)
        println("KONAB POS")
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)
        println("TEST D'IMPRESSION BLUETOOTH")
        println("=".repeat(cols))

        out.write(ESC_ALIGN_LEFT)
        println("Format papier : ${paperWidth.label}")
        println("Nombre de colonnes : $cols")
        println("-".repeat(cols))

        // Ruler test to visually verify margin and wrap
        val ruler = (1..cols).joinToString("") { (it % 10).toString() }
        println("Echelle colonnes :")
        println(ruler)
        println("-".repeat(cols))

        out.write(ESC_BOLD_ON)
        println("Test Texte Gras : SUCCES")
        out.write(ESC_BOLD_OFF)

        out.write(ESC_ALIGN_CENTER)
        println("Connexion Bluetooth operationnelle !")
        println("Date : ${CurrencyFormatter.formatDateTime(System.currentTimeMillis())}")
        println("=".repeat(cols))

        out.write(FEED_LINE)
        out.write(FEED_LINE)
        out.write(PAPER_CUT)

        return out.toByteArray()
    }

    fun buildPriceTagBytes(
        shopName: String,
        productName: String,
        barcode: String,
        price: Long,
        currency: String = "FCFA",
        paperWidth: PaperWidth = PaperWidth.WIDTH_58MM
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val cols = paperWidth.columns

        fun println(text: String) {
            out.write(text.toByteArray(CP850))
            out.write(FEED_LINE)
        }

        out.write(ESC_INIT)
        out.write(ESC_ALIGN_CENTER)
        out.write(ESC_BOLD_ON)
        println(shopName.take(cols))
        out.write(ESC_BOLD_OFF)
        println("-".repeat(cols))

        out.write(GS_TEXT_DOUBLE_WIDTH)
        out.write(ESC_BOLD_ON)
        println(productName.take(cols / 2))
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)

        out.write(FEED_LINE)
        out.write(GS_TEXT_DOUBLE_SIZE)
        out.write(ESC_BOLD_ON)
        println("${CurrencyFormatter.formatFCFA(price)}")
        out.write(GS_TEXT_NORMAL)
        out.write(ESC_BOLD_OFF)

        if (barcode.isNotBlank()) {
            println("Code: $barcode")
        }

        println("=".repeat(cols))
        out.write(FEED_LINE)
        out.write(FEED_LINE)
        out.write(PAPER_CUT)

        return out.toByteArray()
    }

    suspend fun printPriceTag(
        context: Context,
        shopName: String,
        productName: String,
        barcode: String,
        price: Long,
        currency: String = "FCFA"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val printer = getSavedPrinter(context)
            ?: return@withContext Result.failure(IOException("Aucune imprimante configurée. Rendez-vous dans Paramètres pour associer une imprimante."))
        val bytes = buildPriceTagBytes(
            shopName = shopName,
            productName = productName,
            barcode = barcode,
            price = price,
            currency = currency,
            paperWidth = getSavedPaperWidth(context)
        )
        printBytes(context, printer.address, bytes)
    }

    private fun printRowJustified(left: String, right: String, totalCols: Int, out: ByteArrayOutputStream) {
        val spaceNeeded = totalCols - left.length - right.length
        val line = if (spaceNeeded > 0) {
            left + " ".repeat(spaceNeeded) + right
        } else {
            "$left $right"
        }
        out.write(line.toByteArray(CP850))
        out.write(FEED_LINE)
    }
}
