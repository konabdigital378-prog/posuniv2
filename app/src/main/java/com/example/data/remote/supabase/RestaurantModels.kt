package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.ShopProfileEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

enum class RestaurantStaffRole(
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    MANAGER("Gérant / Caisse", "GÉRANT", "Reçoit les commandes tables, valide vers la cuisine et encaisse"),
    KITCHEN("Cuisine / Chef", "CUISINE", "Reçoit les bons transmis par le gérant et gère la préparation"),
    WAITER("Serveur / Salle", "SERVEUR", "Prend les commandes à table et sert les plats prêts"),
    CASHIER("Caissier", "CAISSE", "Encaisse les commandes servies sur la plateforme");

    companion object {
        fun fromString(raw: String?): RestaurantStaffRole {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MANAGER
        }
    }
}

data class RestaurantStaffAccount(
    val id: String,
    val shopId: Long = 0L,
    val name: String,
    val phone: String = "",
    val role: RestaurantStaffRole = RestaurantStaffRole.MANAGER,
    val pin: String = "1234",
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("name", name)
        put("phone", phone)
        put("role", role.name)
        put("pin", pin)
        put("is_active", isActive)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): RestaurantStaffAccount {
            return RestaurantStaffAccount(
                id = o.optString("id", "STAFF-${System.currentTimeMillis()}"),
                shopId = o.optLong("shop_id", 0L),
                name = o.optString("name", "Membre").trim(),
                phone = o.optString("phone", "").trim(),
                role = RestaurantStaffRole.fromString(o.optString("role", "MANAGER")),
                pin = o.optString("pin", "1234").trim().ifBlank { "1234" },
                isActive = o.optBoolean("is_active", true),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

data class RestaurantTableRecord(
    val id: String,
    val shopId: Long = 0L,
    val tableNumber: String,
    val zone: String = "Salle Principale",
    val seats: Int = 4,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun buildQrUri(shopName: String): String {
        return RestaurantQrParser.buildTableQrUri(
            shopId = shopId,
            shopName = shopName,
            tableName = tableNumber
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("table_number", tableNumber)
        put("zone", zone)
        put("seats", seats)
        put("is_active", isActive)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): RestaurantTableRecord {
            return RestaurantTableRecord(
                id = o.optString("id", "TBL-${System.currentTimeMillis()}"),
                shopId = o.optLong("shop_id", 0L),
                tableNumber = o.optString("table_number", "Table 1").trim().ifBlank { "Table 1" },
                zone = o.optString("zone", "Salle Principale").trim().ifBlank { "Salle Principale" },
                seats = o.optInt("seats", 4).coerceIn(1, 30),
                isActive = o.optBoolean("is_active", true),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

enum class RestaurantOrderStatus(
    val label: String,
    val shortBadge: String
) {
    PENDING_MANAGER("En attente Gérant", "À VALIDER"),
    SENT_TO_KITCHEN("Transmis Cuisine", "CUISINE"),
    IN_PREPARATION("En préparation", "EN COURS"),
    READY("Prêt à servir", "PRÊT"),
    SERVED("Servi à table", "SERVI"),
    PAID("Encaissé", "PAYÉ"),
    CANCELLED("Annulé", "ANNULÉ");

    val isOpenOrder: Boolean
        get() = this != PAID && this != CANCELLED

    companion object {
        fun fromString(raw: String?): RestaurantOrderStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PENDING_MANAGER
        }
    }
}

data class RestaurantOrderItem(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val note: String = ""
) {
    val totalPrice: Long get() = unitPrice.coerceAtLeast(0L) * quantity.coerceAtLeast(1)

    fun toJson(): JSONObject = JSONObject().apply {
        put("product_id", productId)
        put("product_name", productName)
        put("unit_price", unitPrice)
        put("quantity", quantity)
        put("note", note)
    }

    companion object {
        fun fromJson(o: JSONObject): RestaurantOrderItem {
            return RestaurantOrderItem(
                productId = o.optLong("product_id", 0L),
                productName = o.optString("product_name", "Plat").trim(),
                unitPrice = o.optLong("unit_price", 0L),
                quantity = o.optInt("quantity", 1).coerceAtLeast(1),
                note = o.optString("note", "").trim()
            )
        }
    }
}

data class RestaurantOrderRecord(
    val id: String,
    val shopId: Long = 0L,
    val orderNumber: String,
    val tableName: String,
    val customerName: String = "Client Table",
    val customerPhone: String = "",
    val source: String = "QR_TABLE", // QR_TABLE, WAITER, MANAGER, POS
    val status: RestaurantOrderStatus = RestaurantOrderStatus.PENDING_MANAGER,
    val items: List<RestaurantOrderItem> = emptyList(),
    val totalAmount: Long = items.sumOf { it.totalPrice },
    val notes: String = "",
    val createdByRole: String = "CLIENT_QR",
    val createdByName: String = "Client",
    val assignedToKitchenBy: String = "",
    val saleId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun itemsJsonString(): String {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("shop_id", shopId)
        put("order_number", orderNumber)
        put("table_name", tableName)
        put("customer_name", customerName)
        put("customer_phone", customerPhone)
        put("source", source)
        put("status", status.name)
        put("items_json", itemsJsonString())
        put("total_amount", totalAmount)
        put("notes", notes)
        put("created_by_role", createdByRole)
        put("created_by_name", createdByName)
        put("assigned_to_kitchen_by", assignedToKitchenBy)
        if (saleId != null) put("sale_id", saleId)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun parseItemsJson(rawJson: String?): List<RestaurantOrderItem> {
            if (rawJson.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(rawJson)
                val out = mutableListOf<RestaurantOrderItem>()
                for (i in 0 until arr.length()) {
                    out.add(RestaurantOrderItem.fromJson(arr.getJSONObject(i)))
                }
                out
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun fromJson(o: JSONObject): RestaurantOrderRecord {
            val parsedItems = when {
                o.has("items_json") -> parseItemsJson(o.optString("items_json", "[]"))
                o.has("items") && o.optJSONArray("items") != null -> {
                    val arr = o.getJSONArray("items")
                    (0 until arr.length()).map { RestaurantOrderItem.fromJson(arr.getJSONObject(it)) }
                }
                else -> emptyList()
            }
            val computedTotal = o.optLong("total_amount", parsedItems.sumOf { it.totalPrice })
            val parsedSaleId = if (o.has("sale_id") && !o.isNull("sale_id")) o.optLong("sale_id", 0L).takeIf { it > 0L } else null
            return RestaurantOrderRecord(
                id = o.optString("id", "ORD-${System.currentTimeMillis()}"),
                shopId = o.optLong("shop_id", 0L),
                orderNumber = o.optString("order_number", "CMD-001"),
                tableName = o.optString("table_name", "Table 1").trim().ifBlank { "Table 1" },
                customerName = o.optString("customer_name", "Client Table").trim(),
                customerPhone = o.optString("customer_phone", "").trim(),
                source = o.optString("source", "QR_TABLE").trim(),
                status = RestaurantOrderStatus.fromString(o.optString("status", "PENDING_MANAGER")),
                items = parsedItems,
                totalAmount = computedTotal,
                notes = o.optString("notes", "").trim(),
                createdByRole = o.optString("created_by_role", "CLIENT_QR").trim(),
                createdByName = o.optString("created_by_name", "Client").trim(),
                assignedToKitchenBy = o.optString("assigned_to_kitchen_by", "").trim(),
                saleId = parsedSaleId,
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
                updatedAt = o.optLong("updated_at", System.currentTimeMillis())
            )
        }
    }
}

data class ParsedTableQr(
    val shopId: Long,
    val shopName: String,
    val tableName: String
)

data class RestaurantTeamConfig(
    val kitchenName: String = "Chef Cuisine",
    val kitchenPhone: String = "",
    val kitchenPin: String = "2222",
    val waiterName: String = "Serveur Salle",
    val waiterPhone: String = "",
    val waiterPin: String = "1111",
    val cashierName: String = "Caissier Salle",
    val cashierPin: String = "0000",
    val initialTableCount: Int = 4
)

data class RemoteStaffLoginResult(
    val match: RemoteShopLoginMatch,
    val staff: RestaurantStaffAccount?
)

object RestaurantQrParser {
    private const val SCHEME_PREFIX = "konabpos://menu"

    fun buildTableQrUri(
        shopId: Long,
        shopName: String,
        tableName: String
    ): String {
        val cleanShop = shopName.trim().take(48)
        val cleanTable = tableName.trim().ifBlank { "Table 1" }.take(24)
        val encShop = URLEncoder.encode(cleanShop, "UTF-8")
        val encTable = URLEncoder.encode(cleanTable, "UTF-8")
        return "$SCHEME_PREFIX?shopId=$shopId&shop=$encShop&table=$encTable"
    }

    fun parseTableQrUri(raw: String?): ParsedTableQr? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null

        val lower = clean.lowercase(Locale.ROOT)
        val queryPart = when {
            lower.startsWith(SCHEME_PREFIX) -> {
                val idx = clean.indexOf('?')
                if (idx >= 0) clean.substring(idx + 1) else ""
            }
            (lower.contains("konab") || lower.contains("menu=1") || lower.contains("mode=menu")) &&
                (lower.contains("table=") || lower.contains("t=")) -> {
                val idx = clean.indexOf('?')
                if (idx >= 0) clean.substring(idx + 1) else clean
            }
            else -> return null
        }

        if (queryPart.isBlank()) return null
        val params = mutableMapOf<String, String>()
        queryPart.split("&").forEach { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val k = pair.substring(0, eqIdx).trim()
                val v = try {
                    URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(eqIdx + 1)
                }
                params[k.lowercase(Locale.ROOT)] = v.trim()
            }
        }

        val shopId = (params["shopid"] ?: params["s"])?.toLongOrNull() ?: 0L
        val shopName = (params["shop"] ?: params["n"]).orEmpty()
        val tableName = (params["table"] ?: params["t"]).orEmpty().ifBlank { "Table 1" }
        if (shopId <= 0L && shopName.isBlank()) return null
        return ParsedTableQr(
            shopId = shopId,
            shopName = shopName,
            tableName = tableName
        )
    }
}

object RestaurantStorage {
    private const val PREFS_NAME = "konab_restaurant_sync_prefs"
    private const val KEY_ACTIVE_STAFF_ID = "active_staff_id_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun shopKey(prefix: String, shopId: Long): String = "${prefix}_${shopId.coerceAtLeast(0L)}"

    // --- STAFF ---

    @Synchronized
    fun getStaffAccounts(context: Context, shopId: Long): List<RestaurantStaffAccount> {
        val raw = prefs(context).getString(shopKey("staff", shopId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { RestaurantStaffAccount.fromJson(arr.getJSONObject(it)) }
                .filter { it.name.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveStaffAccounts(context: Context, shopId: Long, list: List<RestaurantStaffAccount>) {
        val arr = JSONArray()
        list.distinctBy { it.id }.forEach { arr.put(it.copy(shopId = shopId).toJson()) }
        prefs(context).edit().putString(shopKey("staff", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun upsertStaffAccount(context: Context, shopId: Long, account: RestaurantStaffAccount): List<RestaurantStaffAccount> {
        val current = getStaffAccounts(context, shopId).toMutableList()
        val norm = account.copy(shopId = shopId, updatedAt = System.currentTimeMillis())
        val idx = current.indexOfFirst { it.id == norm.id }
        if (idx >= 0) current[idx] = norm else current.add(norm)
        saveStaffAccounts(context, shopId, current)
        return current
    }

    @Synchronized
    fun mergeStaffFromCloud(context: Context, shopId: Long, remoteStaff: List<RestaurantStaffAccount>): List<RestaurantStaffAccount> {
        val mergedById = LinkedHashMap<String, RestaurantStaffAccount>()
        getStaffAccounts(context, shopId).forEach { mergedById[it.id] = it }
        remoteStaff.forEach { remote ->
            val existing = mergedById[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                mergedById[remote.id] = remote.copy(shopId = shopId)
            }
        }
        val finalList = mergedById.values.toList()
        saveStaffAccounts(context, shopId, finalList)
        return finalList
    }

    @Synchronized
    fun deleteStaffAccount(context: Context, shopId: Long, staffId: String): List<RestaurantStaffAccount> {
        val updated = getStaffAccounts(context, shopId).filterNot { it.id == staffId }
        saveStaffAccounts(context, shopId, updated)
        return updated
    }

    @Synchronized
    fun ensureDefaultRestaurantTeam(
        context: Context,
        shopId: Long,
        adminName: String,
        adminPhone: String,
        adminPin: String,
        kitchenName: String = "Chef Cuisine",
        kitchenPhone: String = "",
        kitchenPin: String = "2222",
        waiterName: String = "Serveur Salle",
        waiterPhone: String = "",
        waiterPin: String = "1111",
        cashierName: String = "Caissier Salle",
        cashierPin: String = "0000"
    ): List<RestaurantStaffAccount> {
        val existing = getStaffAccounts(context, shopId)
        if (existing.isNotEmpty()) return existing
        val now = System.currentTimeMillis()
        val defaults = listOf(
            RestaurantStaffAccount(
                id = "STAFF-MGR-$shopId",
                shopId = shopId,
                name = adminName.ifBlank { "Gérant" },
                phone = adminPhone,
                role = RestaurantStaffRole.MANAGER,
                pin = adminPin.ifBlank { "1234" },
                isActive = true,
                updatedAt = now
            ),
            RestaurantStaffAccount(
                id = "STAFF-KIT-$shopId",
                shopId = shopId,
                name = kitchenName.ifBlank { "Chef Cuisine" },
                phone = kitchenPhone,
                role = RestaurantStaffRole.KITCHEN,
                pin = kitchenPin.ifBlank { "2222" },
                isActive = true,
                updatedAt = now
            ),
            RestaurantStaffAccount(
                id = "STAFF-WTR-$shopId",
                shopId = shopId,
                name = waiterName.ifBlank { "Serveur Salle" },
                phone = waiterPhone,
                role = RestaurantStaffRole.WAITER,
                pin = waiterPin.ifBlank { "1111" },
                isActive = true,
                updatedAt = now
            ),
            RestaurantStaffAccount(
                id = "STAFF-CSH-$shopId",
                shopId = shopId,
                name = cashierName.ifBlank { "Caissier Salle" },
                phone = adminPhone,
                role = RestaurantStaffRole.CASHIER,
                pin = cashierPin.ifBlank { "0000" },
                isActive = true,
                updatedAt = now
            )
        )
        saveStaffAccounts(context, shopId, defaults)
        return defaults
    }

    @Synchronized
    fun setActiveStaff(context: Context, shopId: Long, staffId: String?) {
        val key = "$KEY_ACTIVE_STAFF_ID$shopId"
        if (staffId.isNullOrBlank()) {
            prefs(context).edit().remove(key).apply()
        } else {
            prefs(context).edit().putString(key, staffId).apply()
        }
    }

    @Synchronized
    fun clearActiveStaff(context: Context, shopId: Long) {
        setActiveStaff(context, shopId, null)
    }

    @Synchronized
    fun getActiveStaff(context: Context, shopId: Long): RestaurantStaffAccount? {
        val id = prefs(context).getString("$KEY_ACTIVE_STAFF_ID$shopId", null)
        val all = getStaffAccounts(context, shopId)
        return all.find { it.id == id && it.isActive } ?: all.firstOrNull { it.role == RestaurantStaffRole.MANAGER && it.isActive }
    }

    @Synchronized
    fun findStaffByPin(context: Context, shopId: Long, pin: String): RestaurantStaffAccount? {
        val clean = pin.trim()
        if (clean.isBlank()) return null
        return getStaffAccounts(context, shopId).firstOrNull { it.isActive && it.pin == clean }
    }

    // --- TABLES ---

    @Synchronized
    fun getTables(context: Context, shopId: Long): List<RestaurantTableRecord> {
        val raw = prefs(context).getString(shopKey("tables", shopId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { RestaurantTableRecord.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveTables(context: Context, shopId: Long, tables: List<RestaurantTableRecord>) {
        val arr = JSONArray()
        tables.distinctBy { it.id }.forEach { arr.put(it.copy(shopId = shopId).toJson()) }
        prefs(context).edit().putString(shopKey("tables", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun upsertTable(context: Context, shopId: Long, table: RestaurantTableRecord): List<RestaurantTableRecord> {
        val current = getTables(context, shopId).toMutableList()
        val norm = table.copy(shopId = shopId, updatedAt = System.currentTimeMillis())
        val idx = current.indexOfFirst {
            it.id == norm.id || it.tableNumber.equals(norm.tableNumber, ignoreCase = true)
        }
        if (idx >= 0) current[idx] = norm.copy(id = current[idx].id) else current.add(norm)
        saveTables(context, shopId, current)
        return current
    }

    @Synchronized
    fun mergeTablesFromCloud(context: Context, shopId: Long, remoteTables: List<RestaurantTableRecord>): List<RestaurantTableRecord> {
        val mergedById = LinkedHashMap<String, RestaurantTableRecord>()
        getTables(context, shopId).forEach { mergedById[it.id] = it }
        remoteTables.forEach { remote ->
            val existing = mergedById[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                mergedById[remote.id] = remote.copy(shopId = shopId)
            }
        }
        val finalList = mergedById.values.toList()
        saveTables(context, shopId, finalList)
        return finalList
    }

    @Synchronized
    fun deleteTable(context: Context, shopId: Long, tableId: String): List<RestaurantTableRecord> {
        val updated = getTables(context, shopId).filterNot { it.id == tableId }
        saveTables(context, shopId, updated)
        return updated
    }

    @Synchronized
    fun ensureDefaultTables(context: Context, shopId: Long, count: Int = 4): List<RestaurantTableRecord> {
        val existing = getTables(context, shopId)
        if (existing.isNotEmpty()) return existing
        val now = System.currentTimeMillis()
        val safeCount = count.coerceIn(1, 24)
        val defaults = (1..safeCount).map { num ->
            RestaurantTableRecord(
                id = "TBL-$shopId-$num",
                shopId = shopId,
                tableNumber = "Table $num",
                zone = if (num <= (safeCount / 2).coerceAtLeast(2)) "Salle Principale" else "Terrasse",
                seats = 4,
                isActive = true,
                updatedAt = now
            )
        }
        saveTables(context, shopId, defaults)
        return defaults
    }

    @Synchronized
    fun generateBatchTables(context: Context, shopId: Long, countToAdd: Int = 4, zone: String = "Salle Principale"): List<RestaurantTableRecord> {
        val current = getTables(context, shopId).toMutableList()
        val now = System.currentTimeMillis()
        var nextNum = current.size + 1
        repeat(countToAdd.coerceIn(1, 20)) {
            while (current.any { it.tableNumber.equals("Table $nextNum", ignoreCase = true) }) {
                nextNum++
            }
            current.add(
                RestaurantTableRecord(
                    id = "TBL-$shopId-$nextNum-$now",
                    shopId = shopId,
                    tableNumber = "Table $nextNum",
                    zone = zone,
                    seats = 4,
                    isActive = true,
                    updatedAt = now
                )
            )
            nextNum++
        }
        saveTables(context, shopId, current)
        return current
    }

    // --- ORDERS ---

    @Synchronized
    fun getOrders(context: Context, shopId: Long): List<RestaurantOrderRecord> {
        val raw = prefs(context).getString(shopKey("orders", shopId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { RestaurantOrderRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveOrders(context: Context, shopId: Long, orders: List<RestaurantOrderRecord>) {
        val arr = JSONArray()
        orders.sortedByDescending { it.createdAt }.take(200).forEach {
            arr.put(it.copy(shopId = shopId).toJson())
        }
        prefs(context).edit().putString(shopKey("orders", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun upsertOrder(context: Context, shopId: Long, order: RestaurantOrderRecord): List<RestaurantOrderRecord> {
        val current = getOrders(context, shopId).toMutableList()
        val norm = order.copy(shopId = shopId)
        val idx = current.indexOfFirst { it.id == norm.id }
        if (idx >= 0) {
            if (norm.updatedAt >= current[idx].updatedAt) {
                current[idx] = norm
            }
        } else {
            current.add(0, norm)
        }
        saveOrders(context, shopId, current)
        return current.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun mergeOrdersFromCloud(context: Context, shopId: Long, remoteOrders: List<RestaurantOrderRecord>): List<RestaurantOrderRecord> {
        val mergedById = LinkedHashMap<String, RestaurantOrderRecord>()
        getOrders(context, shopId).forEach { mergedById[it.id] = it }
        remoteOrders.forEach { remote ->
            val existing = mergedById[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                mergedById[remote.id] = remote.copy(shopId = shopId)
            }
        }
        val finalList = mergedById.values.sortedByDescending { it.createdAt }
        saveOrders(context, shopId, finalList)
        return finalList
    }

    @Synchronized
    fun nextOrderNumber(context: Context, shopId: Long): String {
        val count = getOrders(context, shopId).size + 1
        return "CMD-${count.toString().padStart(3, '0')}"
    }

    // --- MENU DU JOUR (PRODUITS MIS EN AVANT) ---

    @Synchronized
    fun getMenuOfDayProductIds(context: Context, shopId: Long): Set<Long> {
        val raw = prefs(context).getString(shopKey("menu_day_ids", shopId), null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optLong(it, 0L).takeIf { id -> id > 0L } }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    @Synchronized
    fun setMenuOfDayProductIds(context: Context, shopId: Long, ids: Set<Long>) {
        val arr = JSONArray()
        ids.filter { it > 0L }.forEach { arr.put(it) }
        prefs(context).edit().putString(shopKey("menu_day_ids", shopId), arr.toString()).apply()
    }

    @Synchronized
    fun toggleMenuOfDayProduct(context: Context, shopId: Long, productId: Long): Set<Long> {
        val current = getMenuOfDayProductIds(context, shopId).toMutableSet()
        if (productId in current) current.remove(productId) else current.add(productId)
        setMenuOfDayProductIds(context, shopId, current)
        return current
    }
}

object RestaurantKitchenHelper {
    private val KITCHEN_ACTIVE_STATUSES = setOf(
        RestaurantOrderStatus.PENDING_MANAGER,
        RestaurantOrderStatus.SENT_TO_KITCHEN,
        RestaurantOrderStatus.IN_PREPARATION
    )

    fun filterActiveQrOrders(orders: List<RestaurantOrderRecord>): List<RestaurantOrderRecord> {
        return orders
            .filter { it.source.equals("QR_TABLE", ignoreCase = true) && it.status in KITCHEN_ACTIVE_STATUSES }
            .sortedBy { it.createdAt }
    }

    fun filterAllActiveKitchenOrders(orders: List<RestaurantOrderRecord>): List<RestaurantOrderRecord> {
        return orders
            .filter { it.status in KITCHEN_ACTIVE_STATUSES }
            .sortedBy { it.createdAt }
    }

    fun filterReadyToServeOrders(orders: List<RestaurantOrderRecord>): List<RestaurantOrderRecord> {
        return orders
            .filter { it.status == RestaurantOrderStatus.READY }
            .sortedByDescending { it.updatedAt }
    }

    fun formatItemsSummary(order: RestaurantOrderRecord): String {
        if (order.items.isEmpty()) return "Commande vide"
        return order.items.joinToString(", ") { "${it.quantity}x ${it.productName}" }.take(90)
    }

    fun formatElapsedLabel(createdAt: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffMin = ((nowMillis - createdAt).coerceAtLeast(0L) / 60_000L)
        return when {
            diffMin < 1L -> "À l'instant"
            diffMin < 60L -> "${diffMin} min"
            else -> "${diffMin / 60L}h ${(diffMin % 60L).toString().padStart(2, '0')}"
        }
    }
}

data class PublicRestaurantListing(
    val shopId: Long,
    val name: String,
    val slogan: String = "",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val currency: String = "FCFA",
    val logoUri: String? = null,
    val businessType: String = "Restaurant"
) {
    fun toWebSlug(): String {
        val decomposed = Normalizer.normalize(name.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        val slug = decomposed.replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        return slug.ifBlank { "restaurant" }
    }
}

object RestaurantDirectoryHelper {
    private val RESTAURANT_KEYWORDS = listOf("restaurant", "maquis", "bistro", "grill", "café", "cafe", "snack", "fast food", "traiteur")

    fun isRestaurantShop(businessType: String?): Boolean {
        val clean = businessType?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (clean.isBlank()) return false
        return clean == "restaurant" || RESTAURANT_KEYWORDS.any { clean.contains(it) }
    }

    fun mergeAndFilterDirectory(
        remoteListings: List<PublicRestaurantListing>,
        cachedShops: List<ShopProfileEntity> = emptyList(),
        connectedShops: List<ConnectedShopSession> = emptyList(),
        localShop: ShopProfileEntity? = null,
        localShopCloudId: Long = 0L,
        query: String = ""
    ): List<PublicRestaurantListing> {
        val byKey = LinkedHashMap<String, PublicRestaurantListing>()

        fun putIfValid(item: PublicRestaurantListing) {
            if (item.name.isBlank() || !isRestaurantShop(item.businessType)) return
            val normName = AgentDeploymentStorage.normalizeShopName(item.name)
            val existing = byKey[normName]
            if (existing == null || (existing.shopId <= 1L && item.shopId > 1L) || (existing.logoUri.isNullOrBlank() && !item.logoUri.isNullOrBlank())) {
                byKey[normName] = item
            }
        }

        remoteListings.forEach { putIfValid(it) }

        connectedShops.forEach { s ->
            putIfValid(
                PublicRestaurantListing(
                    shopId = s.cloudShopId,
                    name = s.shopName,
                    slogan = s.slogan,
                    city = s.city,
                    address = s.address,
                    phone = s.phone,
                    currency = s.currency.ifBlank { "FCFA" },
                    logoUri = s.logoUri,
                    businessType = s.businessType
                )
            )
        }

        cachedShops.forEach { s ->
            putIfValid(
                PublicRestaurantListing(
                    shopId = s.id.toLong().takeIf { it > 1L } ?: 0L,
                    name = s.name,
                    slogan = s.slogan,
                    city = s.city,
                    address = s.address,
                    phone = s.phone,
                    currency = s.currency.ifBlank { "FCFA" },
                    logoUri = s.logoUri,
                    businessType = s.businessType
                )
            )
        }

        if (localShop != null && localShop.isConfigured) {
            putIfValid(
                PublicRestaurantListing(
                    shopId = localShopCloudId.takeIf { it > 1L } ?: localShop.id.toLong(),
                    name = localShop.name,
                    slogan = localShop.slogan,
                    city = localShop.city,
                    address = localShop.address,
                    phone = localShop.phone,
                    currency = localShop.currency.ifBlank { "FCFA" },
                    logoUri = localShop.logoUri,
                    businessType = localShop.businessType
                )
            )
        }

        val cleanQuery = query.trim()
        return byKey.values
            .filter { item ->
                cleanQuery.isBlank() ||
                    item.name.contains(cleanQuery, ignoreCase = true) ||
                    item.city.contains(cleanQuery, ignoreCase = true) ||
                    item.slogan.contains(cleanQuery, ignoreCase = true) ||
                    item.address.contains(cleanQuery, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}
