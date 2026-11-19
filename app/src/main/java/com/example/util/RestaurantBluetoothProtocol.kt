package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.data.remote.supabase.RestaurantTableRecord
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class RestaurantBluetoothMode(val label: String, val shortBadge: String) {
    STANDALONE("Hors réseau Bluetooth", "SOLO"),
    HUB("Poste Principal (Hub Bluetooth)", "HUB"),
    CLIENT("Poste Connecté au Hub", "CLIENT");

    companion object {
        fun fromString(raw: String?): RestaurantBluetoothMode {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STANDALONE
        }
    }
}

data class RestaurantTeamJoinQr(
    val shopId: Long,
    val shopName: String,
    val role: RestaurantStaffRole,
    val pin: String,
    val staffId: String,
    val staffName: String,
    val hubBtMac: String = "",
    val hubBtName: String = "",
    val syncKey: String = "",
    val phone: String = "",
    val city: String = ""
)

data class BtPeerCandidate(
    val name: String,
    val address: String,
    val isBonded: Boolean = true,
    val isSuggestedHub: Boolean = false
)

data class ConnectedBtPeer(
    val peerId: String,
    val deviceName: String,
    val deviceAddress: String,
    val staffName: String,
    val role: RestaurantStaffRole,
    val connectedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

data class BtSyncStatus(
    val mode: RestaurantBluetoothMode = RestaurantBluetoothMode.STANDALONE,
    val isRunning: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val localBtName: String = "",
    val hubDeviceName: String = "",
    val hubDeviceAddress: String = "",
    val connectedPeers: List<ConnectedBtPeer> = emptyList(),
    val statusMessage: String = "Bluetooth local inactif",
    val lastSyncAt: Long = 0L,
    val pendingOutboxCount: Int = 0
)

data class RestaurantFullSyncBundle(
    val shopId: Long,
    val shopName: String,
    val slogan: String,
    val city: String,
    val address: String,
    val phone: String,
    val currency: String,
    val businessType: String,
    val adminPin: String,
    val cashierPin: String,
    val logoUri: String?,
    val products: List<ProductEntity>,
    val menuDayIds: Set<Long>,
    val tables: List<RestaurantTableRecord>,
    val staff: List<RestaurantStaffAccount>,
    val orders: List<RestaurantOrderRecord>,
    val generatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shop_id", shopId)
        put("shop_name", shopName)
        put("slogan", slogan)
        put("city", city)
        put("address", address)
        put("phone", phone)
        put("currency", currency.ifBlank { "FCFA" })
        put("business_type", businessType.ifBlank { "Restaurant" })
        put("admin_pin", adminPin)
        put("cashier_pin", cashierPin)
        // Only carry lightweight non-giant-base64 logo URI over BT (URL/drawable/compact data URI)
        val safeLogo = logoUri?.takeIf { it.length <= 18_000 }
        if (!safeLogo.isNullOrBlank()) put("logo_uri", safeLogo)

        val prodArr = JSONArray()
        products.take(250).forEach { p ->
            prodArr.put(JSONObject().apply {
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
                if (p.imageUri.isNotBlank() && p.imageUri.length <= 2048) {
                    put("image_uri", p.imageUri)
                }
            })
        }
        put("products", prodArr)

        val menuArr = JSONArray()
        menuDayIds.forEach { menuArr.put(it) }
        put("menu_day_ids", menuArr)

        val tableArr = JSONArray()
        tables.forEach { tableArr.put(it.toJson()) }
        put("tables", tableArr)

        val staffArr = JSONArray()
        staff.forEach { staffArr.put(it.toJson()) }
        put("staff", staffArr)

        val orderArr = JSONArray()
        orders.take(100).forEach { orderArr.put(it.toJson()) }
        put("orders", orderArr)

        put("generated_at", generatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): RestaurantFullSyncBundle {
            val prods = mutableListOf<ProductEntity>()
            val prodArr = o.optJSONArray("products") ?: JSONArray()
            for (i in 0 until prodArr.length()) {
                val po = prodArr.optJSONObject(i) ?: continue
                val name = po.optString("name", "").trim()
                if (name.isBlank()) continue
                prods.add(
                    ProductEntity(
                        id = po.optLong("id", 0L),
                        name = name,
                        category = po.optString("category", "Plats"),
                        barcode = po.optString("barcode", ""),
                        buyPrice = po.optLong("buy_price", 0L),
                        sellPrice = po.optLong("sell_price", 0L),
                        stockQuantity = po.optInt("stock_quantity", 0),
                        minStockAlert = po.optInt("min_stock_alert", 5),
                        unit = po.optString("unit", "plat"),
                        iconName = po.optString("icon_name", "restaurant"),
                        isAvailable = po.optBoolean("is_available", true),
                        imageUri = po.optString("image_uri", "")
                    )
                )
            }

            val menuIds = linkedSetOf<Long>()
            val menuArr = o.optJSONArray("menu_day_ids") ?: JSONArray()
            for (i in 0 until menuArr.length()) {
                val id = menuArr.optLong(i, 0L)
                if (id > 0L) menuIds.add(id)
            }

            val tables = mutableListOf<RestaurantTableRecord>()
            val tableArr = o.optJSONArray("tables") ?: JSONArray()
            for (i in 0 until tableArr.length()) {
                tableArr.optJSONObject(i)?.let { tables.add(RestaurantTableRecord.fromJson(it)) }
            }

            val staff = mutableListOf<RestaurantStaffAccount>()
            val staffArr = o.optJSONArray("staff") ?: JSONArray()
            for (i in 0 until staffArr.length()) {
                staffArr.optJSONObject(i)?.let { staff.add(RestaurantStaffAccount.fromJson(it)) }
            }

            val orders = mutableListOf<RestaurantOrderRecord>()
            val orderArr = o.optJSONArray("orders") ?: JSONArray()
            for (i in 0 until orderArr.length()) {
                orderArr.optJSONObject(i)?.let { orders.add(RestaurantOrderRecord.fromJson(it)) }
            }

            return RestaurantFullSyncBundle(
                shopId = o.optLong("shop_id", 0L),
                shopName = o.optString("shop_name", "Mon Restaurant").trim(),
                slogan = o.optString("slogan", "").trim(),
                city = o.optString("city", "").trim(),
                address = o.optString("address", "").trim(),
                phone = o.optString("phone", "").trim(),
                currency = o.optString("currency", "FCFA").trim().ifBlank { "FCFA" },
                businessType = o.optString("business_type", "Restaurant").trim().ifBlank { "Restaurant" },
                adminPin = o.optString("admin_pin", "1234").trim().ifBlank { "1234" },
                cashierPin = o.optString("cashier_pin", "0000").trim().ifBlank { "0000" },
                logoUri = o.optString("logo_uri", "").trim().takeIf { it.isNotBlank() },
                products = prods,
                menuDayIds = menuIds,
                tables = tables,
                staff = staff,
                orders = orders,
                generatedAt = o.optLong("generated_at", System.currentTimeMillis())
            )
        }
    }
}

object RestaurantTeamQrParser {
    private const val SCHEME_JOIN = "konabpos://join"
    private const val SCHEME_LEGACY = "konabpos://team-join"
    private const val FAKE_ANDROID_MAC = "02:00:00:00:00:00"

    fun isValidBluetoothMac(mac: String?): Boolean {
        val clean = mac?.trim().orEmpty()
        if (clean.isBlank() || clean == FAKE_ANDROID_MAC) return false
        return Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(clean)
    }

    fun deriveSyncKey(shopId: Long, adminPin: String, shopName: String): String {
        val seed = "KONAB-BT-RESTO|$shopId|${adminPin.trim()}|${shopName.trim().lowercase(Locale.ROOT)}"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            digest.take(4).joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "KNB${(shopId % 99999L).toString().padStart(5, '0')}"
        }
    }

    /**
     * Builds a compact QR URI (guaranteed <= 220 UTF-8 bytes for ISO/IEC 18004 Level-L V1..V9 scanning).
     */
    fun buildJoinQrUri(qr: RestaurantTeamJoinQr): String {
        val cleanName = qr.shopName.trim().ifBlank { "Restaurant" }.take(24)
        val cleanStaff = qr.staffName.trim().ifBlank { qr.role.badgeLabel }.take(18)
        val cleanBtName = qr.hubBtName.trim().take(20)
        val cleanPhone = qr.phone.trim().take(14)
        val cleanCity = qr.city.trim().take(14)
        val validMac = qr.hubBtMac.trim().takeIf { isValidBluetoothMac(it) }.orEmpty()

        val sb = StringBuilder(SCHEME_JOIN)
        sb.append("?i=").append(qr.shopId)
        sb.append("&n=").append(URLEncoder.encode(cleanName, "UTF-8"))
        sb.append("&r=").append(qr.role.name)
        sb.append("&p=").append(URLEncoder.encode(qr.pin.trim().take(10), "UTF-8"))
        if (qr.staffId.isNotBlank()) {
            sb.append("&u=").append(URLEncoder.encode(qr.staffId.trim().take(24), "UTF-8"))
        }
        sb.append("&w=").append(URLEncoder.encode(cleanStaff, "UTF-8"))
        if (validMac.isNotBlank()) {
            sb.append("&m=").append(URLEncoder.encode(validMac, "UTF-8"))
        }
        if (cleanBtName.isNotBlank()) {
            sb.append("&b=").append(URLEncoder.encode(cleanBtName, "UTF-8"))
        }
        if (qr.syncKey.isNotBlank()) {
            sb.append("&k=").append(URLEncoder.encode(qr.syncKey.trim().take(12), "UTF-8"))
        }
        if (cleanPhone.isNotBlank()) {
            sb.append("&t=").append(URLEncoder.encode(cleanPhone, "UTF-8"))
        }
        if (cleanCity.isNotBlank()) {
            sb.append("&c=").append(URLEncoder.encode(cleanCity, "UTF-8"))
        }
        return sb.toString()
    }

    fun parseJoinQrUri(raw: String?): RestaurantTeamJoinQr? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null

        if (clean.startsWith("{") && clean.endsWith("}")) {
            return try {
                val o = JSONObject(clean)
                val type = o.optString("type", "")
                if (!type.equals("KONAB_TEAM_JOIN", ignoreCase = true) && !o.has("role")) return null
                val shopName = o.optString("shopName", o.optString("shop_name", "")).trim()
                val shopId = o.optLong("shopId", o.optLong("shop_id", 0L))
                if (shopName.isBlank() && shopId <= 0L) return null
                RestaurantTeamJoinQr(
                    shopId = shopId,
                    shopName = shopName.ifBlank { "Restaurant Konab" },
                    role = RestaurantStaffRole.fromString(o.optString("role", "WAITER")),
                    pin = o.optString("pin", "1111").trim().ifBlank { "1111" },
                    staffId = o.optString("staffId", o.optString("staff_id", "")).trim(),
                    staffName = o.optString("staffName", o.optString("staff_name", "")).trim(),
                    hubBtMac = o.optString("hubBtMac", o.optString("mac", "")).trim(),
                    hubBtName = o.optString("hubBtName", o.optString("btName", "")).trim(),
                    syncKey = o.optString("syncKey", o.optString("key", "")).trim(),
                    phone = o.optString("phone", "").trim(),
                    city = o.optString("city", "").trim()
                )
            } catch (_: Exception) {
                null
            }
        }

        val lower = clean.lowercase(Locale.ROOT)
        if (!lower.startsWith(SCHEME_JOIN) && !lower.startsWith(SCHEME_LEGACY)) {
            return null
        }
        val qIdx = clean.indexOf('?')
        if (qIdx < 0 || qIdx >= clean.length - 1) return null

        val params = mutableMapOf<String, String>()
        clean.substring(qIdx + 1).split("&").forEach { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val key = pair.substring(0, eqIdx).trim().lowercase(Locale.ROOT)
                val value = try {
                    URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8")
                } catch (_: Exception) {
                    pair.substring(eqIdx + 1)
                }
                params[key] = value.trim()
            }
        }

        val shopId = (params["i"] ?: params["shopid"] ?: params["s"])?.toLongOrNull() ?: 0L
        val shopName = (params["n"] ?: params["shop"]).orEmpty()
        if (shopId <= 0L && shopName.isBlank()) return null

        val role = RestaurantStaffRole.fromString(params["r"] ?: params["role"])
        val pin = (params["p"] ?: params["pin"]).orEmpty().ifBlank {
            when (role) {
                RestaurantStaffRole.KITCHEN -> "2222"
                RestaurantStaffRole.WAITER -> "1111"
                RestaurantStaffRole.CASHIER -> "0000"
                RestaurantStaffRole.MANAGER -> "1234"
            }
        }
        return RestaurantTeamJoinQr(
            shopId = shopId,
            shopName = shopName.ifBlank { "Restaurant Konab" },
            role = role,
            pin = pin,
            staffId = (params["u"] ?: params["sid"]).orEmpty().ifBlank { "STAFF-${role.name.take(3)}-$shopId" },
            staffName = (params["w"] ?: params["sname"]).orEmpty().ifBlank { role.displayName },
            hubBtMac = (params["m"] ?: params["mac"]).orEmpty(),
            hubBtName = (params["b"] ?: params["bt"]).orEmpty(),
            syncKey = (params["k"] ?: params["key"]).orEmpty(),
            phone = (params["t"] ?: params["phone"]).orEmpty(),
            city = (params["c"] ?: params["city"]).orEmpty()
        )
    }
}

object RestaurantBtWireProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    const val SERVICE_NAME = "KonabPosRestaurantHub"

    const val TYPE_HELLO = "HELLO"
    const val TYPE_HELLO_ACK = "HELLO_ACK"
    const val TYPE_FULL_SYNC_REQUEST = "FULL_SYNC_REQUEST"
    const val TYPE_FULL_SYNC_BUNDLE = "FULL_SYNC_BUNDLE"
    const val TYPE_ORDER_UPSERT = "ORDER_UPSERT"
    const val TYPE_CONFIG_UPSERT = "CONFIG_UPSERT"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"

    fun buildFrame(type: String, shopId: Long, syncKey: String, payload: JSONObject = JSONObject()): String {
        return JSONObject().apply {
            put("v", 1)
            put("type", type)
            put("event_id", UUID.randomUUID().toString())
            put("shop_id", shopId)
            put("sync_key", syncKey)
            put("ts", System.currentTimeMillis())
            put("payload", payload)
        }.toString()
    }
}

object RestaurantBluetoothStorage {
    private const val PREFS_NAME = "konab_bt_restaurant_prefs"
    private const val KEY_MODE = "bt_mode"
    private const val KEY_HUB_MAC = "hub_bt_mac"
    private const val KEY_HUB_NAME = "hub_bt_name"
    private const val KEY_SYNC_KEY = "bt_sync_key"
    private const val KEY_OUTBOX_PREFIX = "pending_orders_outbox_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMode(context: Context): RestaurantBluetoothMode {
        return RestaurantBluetoothMode.fromString(prefs(context).getString(KEY_MODE, RestaurantBluetoothMode.STANDALONE.name))
    }

    @Synchronized
    fun setMode(context: Context, mode: RestaurantBluetoothMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    @Synchronized
    fun saveHubTarget(context: Context, mac: String, name: String, syncKey: String) {
        prefs(context).edit()
            .putString(KEY_HUB_MAC, mac.trim())
            .putString(KEY_HUB_NAME, name.trim())
            .putString(KEY_SYNC_KEY, syncKey.trim())
            .apply()
    }

    @Synchronized
    fun getSavedHubMac(context: Context): String = prefs(context).getString(KEY_HUB_MAC, "").orEmpty()

    @Synchronized
    fun getSavedHubName(context: Context): String = prefs(context).getString(KEY_HUB_NAME, "").orEmpty()

    @Synchronized
    fun getSavedSyncKey(context: Context): String = prefs(context).getString(KEY_SYNC_KEY, "").orEmpty()

    @Synchronized
    fun resolveOrCreateSyncKey(context: Context, shopId: Long, profile: ShopProfileEntity?): String {
        val saved = getSavedSyncKey(context)
        if (saved.isNotBlank()) return saved
        val derived = RestaurantTeamQrParser.deriveSyncKey(
            shopId = shopId,
            adminPin = profile?.adminPin ?: "1234",
            shopName = profile?.name ?: "Restaurant"
        )
        prefs(context).edit().putString(KEY_SYNC_KEY, derived).apply()
        return derived
    }

    // --- PERSISTENT OFFLINE -> CLOUD HYBRID OUTBOX ---

    @Synchronized
    fun enqueuePendingOrder(context: Context, shopId: Long, order: RestaurantOrderRecord) {
        val list = getPendingOrders(context, shopId).toMutableList()
        val idx = list.indexOfFirst { it.id == order.id }
        if (idx >= 0) {
            if (order.updatedAt >= list[idx].updatedAt) list[idx] = order
        } else {
            list.add(order)
        }
        savePendingOrders(context, shopId, list)
    }

    @Synchronized
    fun getPendingOrders(context: Context, shopId: Long): List<RestaurantOrderRecord> {
        val raw = prefs(context).getString("$KEY_OUTBOX_PREFIX$shopId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { RestaurantOrderRecord.fromJson(it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun removePendingOrderIds(context: Context, shopId: Long, syncedIds: Set<String>) {
        if (syncedIds.isEmpty()) return
        val remaining = getPendingOrders(context, shopId).filterNot { it.id in syncedIds }
        savePendingOrders(context, shopId, remaining)
    }

    @Synchronized
    private fun savePendingOrders(context: Context, shopId: Long, orders: List<RestaurantOrderRecord>) {
        val arr = JSONArray()
        orders.takeLast(120).forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString("$KEY_OUTBOX_PREFIX$shopId", arr.toString()).apply()
    }
}
