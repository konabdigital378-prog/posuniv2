package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Représente une boutique authentifiée avec les identifiants du gérant dans le système multiboutique.
 */
data class ConnectedShopSession(
    val cloudShopId: Long,
    val shopName: String,
    val businessType: String = "Boutique",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val slogan: String = "",
    val currency: String = "FCFA",
    val receiptFooter: String = "Merci pour votre confiance ! À très bientôt !",
    val logoUri: String? = null,
    val adminName: String = "Gérant",
    val adminPhone: String = "",
    val adminPin: String = "1234",
    val cashierPin: String = "0000",
    val subscriptionTier: String = "Premium",
    val deployerId: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
) {
    fun toShopProfileEntity(): ShopProfileEntity = ShopProfileEntity(
        id = 1,
        name = shopName,
        slogan = slogan,
        phone = phone,
        address = address,
        city = city,
        currency = currency,
        businessType = businessType,
        adminPin = adminPin,
        cashierPin = cashierPin,
        subscriptionTier = subscriptionTier,
        isConfigured = true,
        receiptFooter = receiptFooter,
        logoUri = logoUri,
        deployerId = deployerId
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("cloud_shop_id", cloudShopId)
        put("shop_name", shopName)
        put("business_type", businessType)
        put("city", city)
        put("address", address)
        put("phone", phone)
        put("slogan", slogan)
        put("currency", currency)
        put("receipt_footer", receiptFooter)
        put("logo_uri", logoUri ?: "")
        put("admin_name", adminName)
        put("admin_phone", adminPhone)
        put("admin_pin", adminPin)
        put("cashier_pin", cashierPin)
        put("subscription_tier", subscriptionTier)
        put("deployer_id", deployerId ?: "")
        put("last_synced_at", lastSyncedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): ConnectedShopSession {
            val rawLogo = o.optString("logo_uri", "").trim().ifBlank { null }
            val rawDeployer = o.optString("deployer_id", "").trim().ifBlank { null }
            return ConnectedShopSession(
                cloudShopId = o.optLong("cloud_shop_id", 0L),
                shopName = o.optString("shop_name", "Ma Boutique"),
                businessType = o.optString("business_type", "Boutique"),
                city = o.optString("city", ""),
                address = o.optString("address", ""),
                phone = o.optString("phone", ""),
                slogan = o.optString("slogan", ""),
                currency = o.optString("currency", "FCFA"),
                receiptFooter = o.optString("receipt_footer", "Merci pour votre confiance !"),
                logoUri = rawLogo,
                adminName = o.optString("admin_name", "Gérant").ifBlank { "Gérant" },
                adminPhone = o.optString("admin_phone", ""),
                adminPin = o.optString("admin_pin", "1234").ifBlank { "1234" },
                cashierPin = o.optString("cashier_pin", "0000").ifBlank { "0000" },
                subscriptionTier = o.optString("subscription_tier", "Premium"),
                deployerId = rawDeployer,
                lastSyncedAt = o.optLong("last_synced_at", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Résultat d'une authentification gérant distante sur Supabase (`shop_profile`).
 */
data class RemoteShopLoginMatch(
    val cloudShopId: Long,
    val session: ConnectedShopSession,
    val profile: ShopProfileEntity
)

/**
 * Snapshot complet d'une boutique (profil + catalogue + ventes + clients + fournisseurs + caisse)
 * permettant l'hydratation complète à distance et la bascule multiboutique fluide.
 */
data class MultiShopSnapshot(
    val session: ConnectedShopSession,
    val shopProfile: ShopProfileEntity,
    val products: List<ProductEntity> = emptyList(),
    val customers: List<CustomerEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val saleItems: List<SaleItemEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val cashMovements: List<CashMovementEntity> = emptyList(),
    val cashSessions: List<CashSessionEntity> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("session", session.toJson())
        put("products", JSONArray().apply {
            products.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("category", p.category)
                    put("barcode", p.barcode)
                    put("buy_price", p.buyPrice)
                    put("sell_price", p.sellPrice)
                    put("stock_quantity", p.stockQuantity)
                    put("min_stock_alert", p.minStockAlert)
                    put("unit", p.unit)
                    put("icon_name", p.iconName)
                    put("is_available", p.isAvailable)
                    put("image_uri", p.imageUri)
                    put("gallery_images_json", p.galleryImagesJson)
                })
            }
        })
        put("customers", JSONArray().apply {
            customers.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("address", c.address)
                    put("debt_amount", c.debtAmount)
                    put("total_spent", c.totalSpent)
                    put("total_sales_count", c.totalSalesCount)
                    put("status", c.status)
                    put("created_at", c.createdAt)
                })
            }
        })
        put("sales", JSONArray().apply {
            sales.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("transaction_number", s.transactionNumber)
                    put("timestamp", s.timestamp)
                    if (s.customerId != null) put("customer_id", s.customerId)
                    put("customer_name", s.customerName)
                    put("subtotal", s.subtotal)
                    put("discount_amount", s.discountAmount)
                    put("tax_amount", s.taxAmount)
                    put("total_amount", s.totalAmount)
                    put("total_cost_price", s.totalCostPrice)
                    put("payment_method", s.paymentMethod)
                    put("payment_status", s.paymentStatus)
                    put("amount_paid", s.amountPaid)
                    put("cashier_name", s.cashierName)
                    put("notes", s.notes)
                    put("is_cancelled", s.isCancelled)
                    if (s.cancelledAt != null) put("cancelled_at", s.cancelledAt)
                    if (s.cancellationReason != null) put("cancellation_reason", s.cancellationReason)
                    if (s.cancelledBy != null) put("cancelled_by", s.cancelledBy)
                })
            }
        })
        put("sale_items", JSONArray().apply {
            saleItems.forEach { itm ->
                put(JSONObject().apply {
                    put("id", itm.id)
                    put("sale_id", itm.saleId)
                    put("product_id", itm.productId)
                    put("product_name", itm.productName)
                    put("quantity", itm.quantity)
                    put("unit_price", itm.unitPrice)
                    put("unit_buy_price", itm.unitBuyPrice)
                    put("total_price", itm.totalPrice)
                })
            }
        })
        put("suppliers", JSONArray().apply {
            suppliers.forEach { sup ->
                put(JSONObject().apply {
                    put("id", sup.id)
                    put("name", sup.name)
                    put("phone", sup.phone)
                    put("address", sup.address)
                    put("debt_amount", sup.debtAmount)
                    put("total_purchased", sup.totalPurchased)
                    put("created_at", sup.createdAt)
                })
            }
        })
        put("purchases", JSONArray().apply {
            purchases.forEach { pur ->
                put(JSONObject().apply {
                    put("id", pur.id)
                    put("invoice_number", pur.invoiceNumber)
                    put("timestamp", pur.timestamp)
                    put("supplier_id", pur.supplierId)
                    put("supplier_name", pur.supplierName)
                    put("product_id", pur.productId)
                    put("product_name", pur.productName)
                    put("quantity", pur.quantity)
                    put("unit_cost", pur.unitCost)
                    put("total_cost", pur.totalCost)
                    put("is_paid_from_cash", pur.isPaidFromCash)
                    put("notes", pur.notes)
                })
            }
        })
        put("cash_movements", JSONArray().apply {
            cashMovements.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("timestamp", m.timestamp)
                    put("type", m.type)
                    put("amount", m.amount)
                    put("reason", m.reason)
                    put("payment_method", m.paymentMethod)
                    put("operator_name", m.operatorName)
                    if (m.relatedId != null) put("related_id", m.relatedId)
                })
            }
        })
        put("cash_sessions", JSONArray().apply {
            cashSessions.forEach { cs ->
                put(JSONObject().apply {
                    put("id", cs.id)
                    put("opened_at", cs.openedAt)
                    if (cs.closedAt != null) put("closed_at", cs.closedAt)
                    put("opening_balance", cs.openingBalance)
                    put("closing_expected_balance", cs.closingExpectedBalance)
                    put("closing_actual_balance", cs.closingActualBalance)
                    put("difference", cs.difference)
                    put("status", cs.status)
                    put("opened_by", cs.openedBy)
                    if (cs.closedBy != null) put("closed_by", cs.closedBy)
                    put("closing_notes", cs.closingNotes)
                })
            }
        })
    }

    companion object {
        fun fromJson(root: JSONObject): MultiShopSnapshot {
            val session = ConnectedShopSession.fromJson(root.getJSONObject("session"))
            val products = mutableListOf<ProductEntity>().apply {
                val arr = root.optJSONArray("products") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ProductEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Produit"),
                            category = o.optString("category", "Général"),
                            barcode = o.optString("barcode", ""),
                            buyPrice = o.optLong("buy_price", 0L),
                            sellPrice = o.optLong("sell_price", 0L),
                            stockQuantity = o.optInt("stock_quantity", 0),
                            minStockAlert = o.optInt("min_stock_alert", 5),
                            unit = o.optString("unit", "pièce"),
                            iconName = o.optString("icon_name", "default"),
                            isAvailable = o.optBoolean("is_available", true),
                            imageUri = o.optString("image_uri", ""),
                            galleryImagesJson = o.optString("gallery_images_json", "[]")
                        )
                    )
                }
            }
            val customers = mutableListOf<CustomerEntity>().apply {
                val arr = root.optJSONArray("customers") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CustomerEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Client"),
                            phone = o.optString("phone", ""),
                            address = o.optString("address", ""),
                            debtAmount = o.optLong("debt_amount", 0L),
                            totalSpent = o.optLong("total_spent", 0L),
                            totalSalesCount = o.optInt("total_sales_count", 0),
                            status = o.optString("status", "Actif"),
                            createdAt = o.optLong("created_at", 0L)
                        )
                    )
                }
            }
            val sales = mutableListOf<SaleEntity>().apply {
                val arr = root.optJSONArray("sales") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SaleEntity(
                            id = o.optLong("id", 0L),
                            transactionNumber = o.optString("transaction_number", ""),
                            timestamp = o.optLong("timestamp", 0L),
                            customerId = if (o.has("customer_id") && !o.isNull("customer_id")) o.optLong("customer_id") else null,
                            customerName = o.optString("customer_name", "Client de passage"),
                            subtotal = o.optLong("subtotal", 0L),
                            discountAmount = o.optLong("discount_amount", 0L),
                            taxAmount = o.optLong("tax_amount", 0L),
                            totalAmount = o.optLong("total_amount", 0L),
                            totalCostPrice = o.optLong("total_cost_price", 0L),
                            paymentMethod = o.optString("payment_method", "Espèces"),
                            paymentStatus = o.optString("payment_status", "PAYÉ"),
                            amountPaid = o.optLong("amount_paid", o.optLong("total_amount", 0L)),
                            cashierName = o.optString("cashier_name", "Gérant"),
                            notes = o.optString("notes", ""),
                            isCancelled = o.optBoolean("is_cancelled", false),
                            cancelledAt = if (o.has("cancelled_at") && !o.isNull("cancelled_at")) o.optLong("cancelled_at") else null,
                            cancellationReason = if (o.has("cancellation_reason") && !o.isNull("cancellation_reason")) o.optString("cancellation_reason") else null,
                            cancelledBy = if (o.has("cancelled_by") && !o.isNull("cancelled_by")) o.optString("cancelled_by") else null
                        )
                    )
                }
            }
            val saleItems = mutableListOf<SaleItemEntity>().apply {
                val arr = root.optJSONArray("sale_items") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SaleItemEntity(
                            id = o.optLong("id", 0L),
                            saleId = o.optLong("sale_id", 0L),
                            productId = o.optLong("product_id", 0L),
                            productName = o.optString("product_name", "Article"),
                            quantity = o.optInt("quantity", 1),
                            unitPrice = o.optLong("unit_price", 0L),
                            unitBuyPrice = o.optLong("unit_buy_price", 0L),
                            totalPrice = o.optLong("total_price", 0L)
                        )
                    )
                }
            }
            val suppliers = mutableListOf<SupplierEntity>().apply {
                val arr = root.optJSONArray("suppliers") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SupplierEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", "Fournisseur"),
                            phone = o.optString("phone", ""),
                            address = o.optString("address", ""),
                            debtAmount = o.optLong("debt_amount", 0L),
                            totalPurchased = o.optLong("total_purchased", 0L),
                            createdAt = o.optLong("created_at", 0L)
                        )
                    )
                }
            }
            val purchases = mutableListOf<PurchaseEntity>().apply {
                val arr = root.optJSONArray("purchases") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PurchaseEntity(
                            id = o.optLong("id", 0L),
                            invoiceNumber = o.optString("invoice_number", ""),
                            timestamp = o.optLong("timestamp", 0L),
                            supplierId = o.optLong("supplier_id", 0L),
                            supplierName = o.optString("supplier_name", ""),
                            productId = o.optLong("product_id", 0L),
                            productName = o.optString("product_name", ""),
                            quantity = o.optInt("quantity", 0),
                            unitCost = o.optLong("unit_cost", 0L),
                            totalCost = o.optLong("total_cost", 0L),
                            isPaidFromCash = o.optBoolean("is_paid_from_cash", true),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }
            val cashMovements = mutableListOf<CashMovementEntity>().apply {
                val arr = root.optJSONArray("cash_movements") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CashMovementEntity(
                            id = o.optLong("id", 0L),
                            timestamp = o.optLong("timestamp", 0L),
                            type = o.optString("type", "CASH_IN"),
                            amount = o.optLong("amount", 0L),
                            reason = o.optString("reason", ""),
                            paymentMethod = o.optString("payment_method", "Espèces"),
                            operatorName = o.optString("operator_name", "Gérant"),
                            relatedId = if (o.has("related_id") && !o.isNull("related_id")) o.optLong("related_id") else null
                        )
                    )
                }
            }
            val cashSessions = mutableListOf<CashSessionEntity>().apply {
                val arr = root.optJSONArray("cash_sessions") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        CashSessionEntity(
                            id = o.optLong("id", 0L),
                            openedAt = o.optLong("opened_at", 0L),
                            closedAt = if (o.has("closed_at") && !o.isNull("closed_at")) o.optLong("closed_at") else null,
                            openingBalance = o.optLong("opening_balance", 0L),
                            closingExpectedBalance = o.optLong("closing_expected_balance", 0L),
                            closingActualBalance = o.optLong("closing_actual_balance", 0L),
                            difference = o.optLong("difference", 0L),
                            status = o.optString("status", "OPEN"),
                            openedBy = o.optString("opened_by", "Gérant"),
                            closedBy = if (o.has("closed_by") && !o.isNull("closed_by")) o.optString("closed_by") else null,
                            closingNotes = o.optString("closing_notes", "")
                        )
                    )
                }
            }
            return MultiShopSnapshot(
                session = session,
                shopProfile = session.toShopProfileEntity(),
                products = products,
                customers = customers,
                sales = sales,
                saleItems = saleItems,
                suppliers = suppliers,
                purchases = purchases,
                cashMovements = cashMovements,
                cashSessions = cashSessions
            )
        }
    }
}

object MultiShopStorage {
    private const val PREFS_NAME = "konab_multi_shop_prefs"
    private const val KEY_CONNECTED_SHOPS = "connected_shops_json"
    private const val KEY_ACTIVE_CLOUD_SHOP_ID = "active_cloud_shop_id"
    private const val SCOPED_ID_THRESHOLD = 1_000_000_000_000L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Génère un identifiant cloud unique par boutique afin d'éviter toute collision de clés primaires Room (1, 2, 3...)
     * entre plusieurs boutiques sur Supabase. Les IDs déjà scopés (>= 1e12) sont conservés intacts.
     */
    fun scopedCloudId(cloudShopId: Long, localId: Long): Long {
        if (localId <= 0L) return localId
        if (cloudShopId <= 1L) return localId
        if (localId >= SCOPED_ID_THRESHOLD) return localId
        val prefix = (abs(cloudShopId) % 8_000_000_000L).coerceAtLeast(1L)
        return prefix * 1_000_000L + localId
    }

    fun normalizePhoneDigits(phone: String): String {
        return phone.filter { it.isDigit() }
    }

    fun phonesMatch(a: String, b: String): Boolean {
        val da = normalizePhoneDigits(a)
        val db = normalizePhoneDigits(b)
        if (da.isBlank() || db.isBlank()) return false
        if (da == db) return true
        // Tolère l'indicatif pays (+226 / 00226 vs numéro local 8+ chiffres)
        return da.length >= 8 && db.length >= 8 && da.takeLast(8) == db.takeLast(8)
    }

    fun identifierMatchesSession(
        identifier: String,
        shopName: String,
        shopPhone: String,
        adminName: String,
        adminPhone: String,
        cloudShopId: Long
    ): Boolean {
        val clean = identifier.trim()
        if (clean.isBlank()) return false
        val normInputName = AgentDeploymentStorage.normalizeShopName(clean)
        if (normInputName == AgentDeploymentStorage.normalizeShopName(shopName)) return true
        if (normInputName == AgentDeploymentStorage.normalizeShopName(adminName) && adminName.isNotBlank()) return true
        if (cloudShopId > 0L && clean == cloudShopId.toString()) return true
        if (phonesMatch(clean, adminPhone) || phonesMatch(clean, shopPhone)) return true
        return false
    }

    @Synchronized
    fun getActiveCloudShopId(context: Context): Long {
        val id = getPrefs(context).getLong(KEY_ACTIVE_CLOUD_SHOP_ID, 0L)
        if (id > 1L) return id
        return context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE)
            .getLong("cloud_shop_id", 0L)
    }

    @Synchronized
    fun setActiveCloudShopId(context: Context, cloudShopId: Long) {
        getPrefs(context).edit().putLong(KEY_ACTIVE_CLOUD_SHOP_ID, cloudShopId).apply()
    }

    @Synchronized
    fun saveConnectedShop(context: Context, session: ConnectedShopSession) {
        val current = getConnectedShops(context).toMutableList()
        val normName = AgentDeploymentStorage.normalizeShopName(session.shopName)
        current.removeAll {
            (session.cloudShopId > 0L && it.cloudShopId == session.cloudShopId) ||
                (normName.isNotBlank() && AgentDeploymentStorage.normalizeShopName(it.shopName) == normName)
        }
        current.add(0, session)
        val arr = JSONArray()
        current.take(50).forEach { arr.put(it.toJson()) }
        getPrefs(context).edit()
            .putString(KEY_CONNECTED_SHOPS, arr.toString())
            .putLong(KEY_ACTIVE_CLOUD_SHOP_ID, session.cloudShopId)
            .apply()
    }

    @Synchronized
    fun getConnectedShops(context: Context): List<ConnectedShopSession> {
        val raw = getPrefs(context).getString(KEY_CONNECTED_SHOPS, "[]") ?: "[]"
        val list = mutableListOf<ConnectedShopSession>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val item = ConnectedShopSession.fromJson(arr.getJSONObject(i))
                if (item.shopName.isNotBlank()) {
                    list.add(item)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    fun findConnectedShopByCredentials(
        context: Context,
        identifier: String,
        pin: String
    ): ConnectedShopSession? {
        val cleanPin = pin.trim()
        if (cleanPin.isBlank()) return null
        return getConnectedShops(context).firstOrNull { session ->
            identifierMatchesSession(
                identifier = identifier,
                shopName = session.shopName,
                shopPhone = session.phone,
                adminName = session.adminName,
                adminPhone = session.adminPhone,
                cloudShopId = session.cloudShopId
            ) && session.adminPin.trim() == cleanPin
        }
    }

    @Synchronized
    fun removeConnectedShop(context: Context, cloudShopId: Long) {
        val remaining = getConnectedShops(context).filterNot { it.cloudShopId == cloudShopId }
        val arr = JSONArray()
        remaining.forEach { arr.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_CONNECTED_SHOPS, arr.toString()).apply()
        try {
            snapshotCacheFile(context, cloudShopId).delete()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun clearConnectedShops(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    private fun snapshotCacheFile(context: Context, cloudShopId: Long): File {
        val dir = File(context.filesDir, "multi_shop_snapshots")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "shop_$cloudShopId.json")
    }

    @Synchronized
    fun saveLocalSnapshotCache(context: Context, snapshot: MultiShopSnapshot) {
        if (snapshot.session.cloudShopId <= 0L) return
        try {
            snapshotCacheFile(context, snapshot.session.cloudShopId).writeText(snapshot.toJson().toString())
        } catch (_: Exception) {}
    }

    @Synchronized
    fun loadLocalSnapshotCache(context: Context, cloudShopId: Long): MultiShopSnapshot? {
        if (cloudShopId <= 0L) return null
        return try {
            val f = snapshotCacheFile(context, cloudShopId)
            if (!f.exists() || f.length() == 0L) return null
            MultiShopSnapshot.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }
}
