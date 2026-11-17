package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.util.CurrencyFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeliveryStatus(val label: String, val accentHex: Long) {
    EN_COURS("En cours", 0xFF0284C7),
    VALIDEE("Validée par QR", 0xFF10B981),
    ANNULEE("Annulée", 0xFFEF4444);

    val isOngoing: Boolean
        get() = this == EN_COURS

    companion object {
        fun fromRaw(raw: String?): DeliveryStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EN_COURS
        }
    }
}

data class DeliveryDriverProfile(
    val id: String,
    val userId: String = "",
    val fullName: String,
    val phone: String,
    val city: String = "Ouagadougou",
    val neighborhood: String = "",
    val vehicleType: String = "Moto",
    val zone: String = "",
    val photoUri: String = "",
    val planPaid: Boolean = false,
    val planAmountFcfa: Long = 1000L,
    val planPaymentRef: String = "",
    val planPaidAt: Long = 0L,
    val planExpiresAt: Long = 0L,
    val isAvailable: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isPlanActive: Boolean
        get() = planPaid && (planExpiresAt <= 0L || planExpiresAt > System.currentTimeMillis())

    val displayLocation: String
        get() = listOf(zone, neighborhood, city)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" • ")

    fun buildQrUri(): String = DeliveryQrParser.buildDriverQrUri(id)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("user_id", userId)
        put("full_name", fullName)
        put("phone", phone)
        put("city", city)
        put("neighborhood", neighborhood)
        put("vehicle_type", vehicleType)
        put("zone", zone)
        put("photo_uri", photoUri)
        put("plan_paid", planPaid)
        put("plan_amount_fcfa", planAmountFcfa)
        put("plan_payment_ref", planPaymentRef)
        put("plan_paid_at", planPaidAt)
        put("plan_expires_at", planExpiresAt)
        put("is_available", isAvailable)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DeliveryDriverProfile {
            return DeliveryDriverProfile(
                id = o.optString("id", "LIV-${System.currentTimeMillis()}"),
                userId = o.optString("user_id", o.optString("userId", "")),
                fullName = o.optString("full_name", o.optString("fullName", "Livreur Konab")),
                phone = o.optString("phone", ""),
                city = o.optString("city", "Ouagadougou"),
                neighborhood = o.optString("neighborhood", ""),
                vehicleType = o.optString("vehicle_type", o.optString("vehicleType", "Moto")),
                zone = o.optString("zone", ""),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                planPaid = o.optBoolean("plan_paid", o.optBoolean("planPaid", false)),
                planAmountFcfa = o.optLong("plan_amount_fcfa", o.optLong("planAmountFcfa", 1000L)),
                planPaymentRef = o.optString("plan_payment_ref", o.optString("planPaymentRef", "")),
                planPaidAt = o.optLong("plan_paid_at", o.optLong("planPaidAt", 0L)),
                planExpiresAt = o.optLong("plan_expires_at", o.optLong("planExpiresAt", 0L)),
                isAvailable = o.optBoolean("is_available", o.optBoolean("isAvailable", true)),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class DeliveryOrderRecord(
    val id: String,
    val driverId: String,
    val driverName: String,
    val driverPhone: String = "",
    val clientUserId: String = "",
    val clientName: String,
    val clientPhone: String = "",
    val clientCity: String = "",
    val clientNeighborhood: String = "",
    val title: String = "Colis / Commande",
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val priceFcfa: Long = 0L,
    val status: DeliveryStatus = DeliveryStatus.EN_COURS,
    val receiptNumber: String = "",
    val receiptSentAt: Long = 0L,
    val validatedAt: Long = 0L,
    val validatedQrRaw: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isValidated: Boolean get() = status == DeliveryStatus.VALIDEE
    val effectiveReceiptNumber: String
        get() = receiptNumber.ifBlank { "REC-${id.removePrefix("DLV-")}" }

    fun formattedPrice(): String = if (priceFcfa > 0L) CurrencyFormatter.formatFCFA(priceFcfa) else "À définir"

    fun clientCoordinatesSummary(): String {
        val place = listOf(clientNeighborhood.trim(), clientCity.trim())
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return listOfNotNull(
            clientName.takeIf { it.isNotBlank() },
            clientPhone.takeIf { it.isNotBlank() },
            place.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }

    fun formattedDateTime(ts: Long = createdAt): String {
        if (ts <= 0L) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
        return sdf.format(Date(ts))
    }

    fun buildReceiptText(driver: DeliveryDriverProfile? = null): String {
        val effectiveDriverPhone = driver?.phone?.ifBlank { driverPhone } ?: driverPhone
        val place = listOf(clientNeighborhood.trim(), clientCity.trim()).filter { it.isNotBlank() }.joinToString(", ")
        return buildString {
            appendLine("📦 *REÇU DE LIVRAISON — KONAB POS*")
            appendLine("Reçu N° : *${effectiveReceiptNumber}*")
            appendLine("Statut : *${status.label}*")
            appendLine("════════════════════")
            appendLine("🚚 *Livreur* : $driverName")
            if (effectiveDriverPhone.isNotBlank()) appendLine("📞 Tél livreur : $effectiveDriverPhone")
            appendLine("👤 *Client* : $clientName")
            if (clientPhone.isNotBlank()) appendLine("📱 Tél client : $clientPhone")
            if (place.isNotBlank()) appendLine("📍 Localité client : $place")
            appendLine("────────────────────")
            appendLine("📦 Objet : $title")
            if (pickupAddress.isNotBlank()) appendLine("🛫 Départ : $pickupAddress")
            if (dropoffAddress.isNotBlank()) appendLine("🏁 Destination : $dropoffAddress")
            appendLine("💰 *PRIX LIVRAISON : ${formattedPrice()}*")
            if (validatedAt > 0L) {
                appendLine("✅ Validée par QR le ${formattedDateTime(validatedAt)}")
            } else {
                appendLine("🕒 Créée le ${formattedDateTime(createdAt)}")
            }
            if (notes.isNotBlank()) {
                appendLine("📝 Note : $notes")
            }
            appendLine("════════════════════")
            appendLine("🔒 Reçu généré via *KONAB POS*")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("driver_id", driverId)
        put("driver_name", driverName)
        put("driver_phone", driverPhone)
        put("client_user_id", clientUserId)
        put("client_name", clientName)
        put("client_phone", clientPhone)
        put("client_city", clientCity)
        put("client_neighborhood", clientNeighborhood)
        put("title", title)
        put("pickup_address", pickupAddress)
        put("dropoff_address", dropoffAddress)
        put("price_fcfa", priceFcfa)
        put("status", status.name)
        put("receipt_number", receiptNumber)
        put("receipt_sent_at", receiptSentAt)
        put("validated_at", validatedAt)
        put("validated_qr_raw", validatedQrRaw)
        put("notes", notes)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DeliveryOrderRecord {
            return DeliveryOrderRecord(
                id = o.optString("id", "DLV-${System.currentTimeMillis()}"),
                driverId = o.optString("driver_id", o.optString("driverId", "")),
                driverName = o.optString("driver_name", o.optString("driverName", "Livreur")),
                driverPhone = o.optString("driver_phone", o.optString("driverPhone", "")),
                clientUserId = o.optString("client_user_id", o.optString("clientUserId", "")),
                clientName = o.optString("client_name", o.optString("clientName", "Client")),
                clientPhone = o.optString("client_phone", o.optString("clientPhone", "")),
                clientCity = o.optString("client_city", o.optString("clientCity", "")),
                clientNeighborhood = o.optString("client_neighborhood", o.optString("clientNeighborhood", "")),
                title = o.optString("title", "Colis / Commande"),
                pickupAddress = o.optString("pickup_address", o.optString("pickupAddress", "")),
                dropoffAddress = o.optString("dropoff_address", o.optString("dropoffAddress", "")),
                priceFcfa = o.optLong("price_fcfa", o.optLong("priceFcfa", 0L)),
                status = DeliveryStatus.fromRaw(o.optString("status", DeliveryStatus.EN_COURS.name)),
                receiptNumber = o.optString("receipt_number", o.optString("receiptNumber", "")),
                receiptSentAt = o.optLong("receipt_sent_at", o.optLong("receiptSentAt", 0L)),
                validatedAt = o.optLong("validated_at", o.optLong("validatedAt", 0L)),
                validatedQrRaw = o.optString("validated_qr_raw", o.optString("validatedQrRaw", "")),
                notes = o.optString("notes", ""),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class ParsedDeliveryQr(
    val driverId: String,
    val deliveryId: String = ""
)

object DeliveryQrParser {
    fun buildDriverQrUri(driverId: String): String {
        val clean = driverId.trim()
        return "konabpos://delivery?driverId=${Uri.encode(clean)}"
    }

    fun parseDeliveryQrUri(raw: String): ParsedDeliveryQr? {
        val clean = raw.trim()
        if (clean.isBlank()) return null

        if (clean.startsWith("LIV-", ignoreCase = true) && !clean.contains(" ")) {
            return ParsedDeliveryQr(driverId = clean)
        }

        val normalized = if (clean.startsWith("konabpos://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)
        ) clean else return null

        return try {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
            val isDelivery = host == "delivery" ||
                host == "livraison" ||
                path.contains("/delivery") ||
                path.contains("/livraison")
            if (!isDelivery) return null
            val driverId = uri.getQueryParameter("driverId")?.trim()
                ?: uri.getQueryParameter("driver")?.trim()
                ?: uri.getQueryParameter("livreurId")?.trim()
                ?: ""
            val deliveryId = uri.getQueryParameter("deliveryId")?.trim().orEmpty()
            if (driverId.isBlank() && deliveryId.isBlank()) null
            else ParsedDeliveryQr(driverId = driverId, deliveryId = deliveryId)
        } catch (_: Exception) {
            // Fallback string extraction if Uri fails in non-Android unit tests
            val lower = normalized.lowercase(Locale.ROOT)
            if (!lower.contains("delivery") && !lower.contains("livraison")) return null
            val match = Regex("[?&](?:driverId|driver|livreurId)=([^&#]+)", RegexOption.IGNORE_CASE).find(normalized)
            val driverId = match?.groupValues?.getOrNull(1)?.let { Uri.decode(it) }?.trim().orEmpty()
            if (driverId.isBlank()) null else ParsedDeliveryQr(driverId = driverId)
        }
    }
}

object DeliveryStorage {
    private const val PREFS_NAME = "konab_delivery_portal_prefs"
    private const val KEY_DRIVERS = "drivers_json"
    private const val KEY_DELIVERIES = "deliveries_json"
    private const val KEY_ACTIVE_DRIVER_ID = "active_driver_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDrivers(context: Context): List<DeliveryDriverProfile> {
        val raw = prefs(context).getString(KEY_DRIVERS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { DeliveryDriverProfile.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveDrivers(context: Context, list: List<DeliveryDriverProfile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_DRIVERS, arr.toString()).apply()
    }

    fun upsertDriver(context: Context, driver: DeliveryDriverProfile): DeliveryDriverProfile {
        val list = getDrivers(context).filterNot { it.id == driver.id }
        val updated = listOf(driver) + list
        saveDrivers(context, updated)
        return driver
    }

    fun mergeDriversFromCloud(context: Context, remote: List<DeliveryDriverProfile>): List<DeliveryDriverProfile> {
        val byId = getDrivers(context).associateBy { it.id }.toMutableMap()
        remote.forEach { r ->
            val local = byId[r.id]
            if (local == null || r.updatedAt >= local.updatedAt) {
                val mergedPhoto = r.photoUri.ifBlank { local?.photoUri.orEmpty() }
                byId[r.id] = r.copy(photoUri = mergedPhoto)
            }
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveDrivers(context, merged)
        return merged
    }

    fun getActiveDriverId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_DRIVER_ID, null)?.takeIf { it.isNotBlank() }

    fun setActiveDriverId(context: Context, driverId: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_DRIVER_ID, driverId ?: "").apply()
    }

    fun getDeliveries(context: Context): List<DeliveryOrderRecord> {
        val raw = prefs(context).getString(KEY_DELIVERIES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { DeliveryOrderRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveDeliveries(context: Context, list: List<DeliveryOrderRecord>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_DELIVERIES, arr.toString()).apply()
    }

    fun upsertDelivery(context: Context, order: DeliveryOrderRecord): DeliveryOrderRecord {
        val list = getDeliveries(context).filterNot { it.id == order.id }
        val updated = listOf(order) + list
        saveDeliveries(context, updated)
        return order
    }

    fun mergeDeliveriesFromCloud(context: Context, remote: List<DeliveryOrderRecord>): List<DeliveryOrderRecord> {
        val byId = getDeliveries(context).associateBy { it.id }.toMutableMap()
        remote.forEach { r ->
            val local = byId[r.id]
            if (local == null || r.updatedAt >= local.updatedAt) {
                byId[r.id] = r
            }
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveDeliveries(context, merged)
        return merged
    }

    fun deleteDelivery(context: Context, deliveryId: String) {
        val updated = getDeliveries(context).filterNot { it.id == deliveryId }
        saveDeliveries(context, updated)
    }

    fun findDeliveriesForClient(
        deliveries: List<DeliveryOrderRecord>,
        user: PublicUserAccount?
    ): List<DeliveryOrderRecord> {
        if (user == null) return emptyList()
        val normPhone = PublicUserStorage.normalizePhone(user.phone)
        return deliveries.filter { order ->
            (order.clientUserId.isNotBlank() && order.clientUserId.equals(user.id, ignoreCase = true)) ||
                (normPhone.length >= 6 && PublicUserStorage.normalizePhone(order.clientPhone) == normPhone)
        }.sortedWith(
            compareBy<DeliveryOrderRecord> { if (it.status == DeliveryStatus.EN_COURS) 0 else 1 }
                .thenByDescending { it.updatedAt }
        )
    }

    fun findDeliveriesForDriver(
        deliveries: List<DeliveryOrderRecord>,
        driverId: String?
    ): List<DeliveryOrderRecord> {
        val clean = driverId?.trim().orEmpty()
        if (clean.isBlank()) return emptyList()
        return deliveries.filter { it.driverId.equals(clean, ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
    }
}
