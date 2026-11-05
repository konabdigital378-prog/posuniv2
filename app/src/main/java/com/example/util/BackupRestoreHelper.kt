package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.dao.KonabDao
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupRestoreHelper {

    suspend fun createBackup(context: Context, dao: KonabDao): File {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())
        root.put("app", "Konab POS")

        // 1. Shop profile
        val profile = dao.getShopProfileOnce()
        if (profile != null) {
            val pObj = JSONObject().apply {
                put("name", profile.name)
                put("slogan", profile.slogan)
                put("phone", profile.phone)
                put("address", profile.address)
                put("city", profile.city)
                put("currency", profile.currency)
                put("businessType", profile.businessType)
                put("adminPin", profile.adminPin)
                put("cashierPin", profile.cashierPin)
                put("subscriptionTier", profile.subscriptionTier)
                put("subscriptionExpiresAt", profile.subscriptionExpiresAt)
                put("isConfigured", profile.isConfigured)
                put("taxRate", profile.taxRate)
                put("receiptFooter", profile.receiptFooter)
            }
            root.put("shop_profile", pObj)
        }

        // 2. Products
        val products = dao.getAllProducts().first()
        val prodArray = JSONArray()
        for (p in products) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("category", p.category)
                put("barcode", p.barcode)
                put("buyPrice", p.buyPrice)
                put("sellPrice", p.sellPrice)
                put("stockQuantity", p.stockQuantity)
                put("minStockAlert", p.minStockAlert)
                put("unit", p.unit)
                put("iconName", p.iconName)
                put("isAvailable", p.isAvailable)
                put("imageUri", p.imageUri)
            }
            prodArray.put(obj)
        }
        root.put("products", prodArray)

        // 3. Customers
        val customers = dao.getAllCustomers().first()
        val custArray = JSONArray()
        for (c in customers) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("address", c.address)
                put("debtAmount", c.debtAmount)
                put("totalSpent", c.totalSpent)
                put("totalSalesCount", c.totalSalesCount)
                put("status", c.status)
                put("createdAt", c.createdAt)
            }
            custArray.put(obj)
        }
        root.put("customers", custArray)

        // 4. Suppliers
        val suppliers = dao.getAllSuppliers().first()
        val supArray = JSONArray()
        for (s in suppliers) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("phone", s.phone)
                put("address", s.address)
                put("debtAmount", s.debtAmount)
                put("totalPurchased", s.totalPurchased)
                put("createdAt", s.createdAt)
            }
            supArray.put(obj)
        }
        root.put("suppliers", supArray)

        // 5. Sales & Sale items
        val sales = dao.getAllSales().first()
        val salesArray = JSONArray()
        for (s in sales) {
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("transactionNumber", s.transactionNumber)
                put("timestamp", s.timestamp)
                put("customerId", s.customerId ?: JSONObject.NULL)
                put("customerName", s.customerName)
                put("subtotal", s.subtotal)
                put("discountAmount", s.discountAmount)
                put("taxAmount", s.taxAmount)
                put("totalAmount", s.totalAmount)
                put("totalCostPrice", s.totalCostPrice)
                put("paymentMethod", s.paymentMethod)
                put("paymentStatus", s.paymentStatus)
                put("amountPaid", s.amountPaid)
                put("cashierName", s.cashierName)
                put("notes", s.notes)
            }
            salesArray.put(sObj)
        }
        root.put("sales", salesArray)

        val saleItems = dao.getAllSaleItems().first()
        val itemsArray = JSONArray()
        for (i in saleItems) {
            val iObj = JSONObject().apply {
                put("id", i.id)
                put("saleId", i.saleId)
                put("productId", i.productId)
                put("productName", i.productName)
                put("quantity", i.quantity)
                put("unitPrice", i.unitPrice)
                put("unitBuyPrice", i.unitBuyPrice)
                put("totalPrice", i.totalPrice)
            }
            itemsArray.put(iObj)
        }
        root.put("sale_items", itemsArray)

        // 6. Cash movements
        val cashMovements = dao.getAllCashMovements().first()
        val cmArray = JSONArray()
        for (cm in cashMovements) {
            val cmObj = JSONObject().apply {
                put("id", cm.id)
                put("timestamp", cm.timestamp)
                put("type", cm.type)
                put("amount", cm.amount)
                put("reason", cm.reason)
                put("paymentMethod", cm.paymentMethod)
                put("operatorName", cm.operatorName)
            }
            cmArray.put(cmObj)
        }
        root.put("cash_movements", cmArray)

        // Write to file
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupFile = File(exportDir, "konab_backup_$timeStamp.json")
        backupFile.writeText(root.toString(2))
        return backupFile
    }

    suspend fun restoreFromJson(jsonString: String, dao: KonabDao): Boolean {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("app") || root.getString("app") != "Konab POS") {
                return false
            }

            // Restore Shop Profile
            if (root.has("shop_profile")) {
                val pObj = root.getJSONObject("shop_profile")
                val profile = ShopProfileEntity(
                    id = 1,
                    name = pObj.optString("name", "Boutique Kaba"),
                    slogan = pObj.optString("slogan", ""),
                    phone = pObj.optString("phone", "65413799"),
                    address = pObj.optString("address", "Burkina Faso"),
                    city = pObj.optString("city", "Bobo-Dioulasso"),
                    currency = pObj.optString("currency", "FCFA"),
                    businessType = pObj.optString("businessType", "Boutique"),
                    adminPin = pObj.optString("adminPin", "1234"),
                    cashierPin = pObj.optString("cashierPin", "0000"),
                    subscriptionTier = pObj.optString("subscriptionTier", "Premium"),
                    subscriptionExpiresAt = pObj.optLong("subscriptionExpiresAt", 0L),
                    isConfigured = pObj.optBoolean("isConfigured", true),
                    taxRate = pObj.optDouble("taxRate", 0.0),
                    receiptFooter = pObj.optString("receiptFooter", "Merci pour votre confiance !")
                )
                dao.saveShopProfile(profile)
            }

            // Restore Products
            if (root.has("products")) {
                dao.deleteAllProducts()
                val array = root.getJSONArray("products")
                val list = mutableListOf<ProductEntity>()
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    list.add(
                        ProductEntity(
                            id = o.optLong("id", 0),
                            name = o.getString("name"),
                            category = o.optString("category", "Général"),
                            barcode = o.optString("barcode", ""),
                            buyPrice = o.optLong("buyPrice", 0),
                            sellPrice = o.optLong("sellPrice", 0),
                            stockQuantity = o.optInt("stockQuantity", 0),
                            minStockAlert = o.optInt("minStockAlert", 5),
                            unit = o.optString("unit", "pièce"),
                            iconName = o.optString("iconName", "default"),
                            isAvailable = o.optBoolean("isAvailable", true),
                            imageUri = o.optString("imageUri", "")
                        )
                    )
                }
                dao.insertProducts(list)
            }

            // Restore Customers
            if (root.has("customers")) {
                dao.deleteAllCustomers()
                val array = root.getJSONArray("customers")
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    dao.insertCustomer(
                        CustomerEntity(
                            id = o.optLong("id", 0),
                            name = o.getString("name"),
                            phone = o.getString("phone"),
                            address = o.optString("address", ""),
                            debtAmount = o.optLong("debtAmount", 0),
                            totalSpent = o.optLong("totalSpent", 0),
                            totalSalesCount = o.optInt("totalSalesCount", 0),
                            status = o.optString("status", "Régulier"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Restore Suppliers
            if (root.has("suppliers")) {
                dao.deleteAllSuppliers()
                val array = root.getJSONArray("suppliers")
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    dao.insertSupplier(
                        SupplierEntity(
                            id = o.optLong("id", 0),
                            name = o.getString("name"),
                            phone = o.getString("phone"),
                            address = o.optString("address", ""),
                            debtAmount = o.optLong("debtAmount", 0),
                            totalPurchased = o.optLong("totalPurchased", 0),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun shareBackupFile(context: Context, backupFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sauvegarde Konab POS - ${backupFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Exporter la sauvegarde")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
