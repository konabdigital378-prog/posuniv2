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

enum class WasteClientStatus(val label: String) {
    CLIENT_ACTIF("Famille active"),
    PROSPECT("Potentiel client"),
    SUSPENDU("Suspendu");

    companion object {
        fun fromRaw(raw: String?): WasteClientStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: CLIENT_ACTIF
        }
    }
}

data class WasteBusinessProfile(
    val id: String,
    val businessName: String,
    val managerName: String = "Responsable",
    val phone: String = "",
    val city: String = "Conakry",
    val pin: String = "1234",
    val defaultMonthlyFee: Long = 2500L,
    val defaultPassagesPerCycle: Int = 8,
    val deployerId: String = "",
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_name", businessName)
        put("manager_name", managerName)
        put("phone", phone)
        put("city", city)
        put("pin", pin)
        put("default_monthly_fee", defaultMonthlyFee)
        put("default_passages_per_cycle", defaultPassagesPerCycle)
        put("deployer_id", deployerId)
        put("photo_uri", photoUri)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): WasteBusinessProfile {
            return WasteBusinessProfile(
                id = o.optString("id", "WASTE-BIZ-${System.currentTimeMillis()}"),
                businessName = o.optString("business_name", o.optString("businessName", "Équipe Salubrité")),
                managerName = o.optString("manager_name", o.optString("managerName", "Responsable")),
                phone = o.optString("phone", ""),
                city = o.optString("city", "Conakry"),
                pin = o.optString("pin", "1234"),
                defaultMonthlyFee = o.optLong("default_monthly_fee", o.optLong("defaultMonthlyFee", 2500L)),
                defaultPassagesPerCycle = o.optInt("default_passages_per_cycle", o.optInt("defaultPassagesPerCycle", 8)).coerceIn(1, 31),
                deployerId = o.optString("deployer_id", o.optString("deployerId", "")),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class WastePickupLog(
    val id: String = "PK-${System.currentTimeMillis()}",
    val timestamp: Long = System.currentTimeMillis(),
    val collectorName: String = "Équipe collecte",
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("collectorName", collectorName)
        put("note", note)
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
        return sdf.format(Date(timestamp))
    }

    fun formattedShortDate(): String {
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
        return sdf.format(Date(timestamp))
    }

    companion object {
        fun fromJson(o: JSONObject): WastePickupLog {
            return WastePickupLog(
                id = o.optString("id", "PK-${System.currentTimeMillis()}"),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                collectorName = o.optString("collectorName", "Équipe collecte"),
                note = o.optString("note", "")
            )
        }
    }
}

data class WasteCyclePayment(
    val id: String = "PAY-${System.currentTimeMillis()}",
    val amount: Long = 0L,
    val paidAt: Long = System.currentTimeMillis(),
    val passagesCompleted: Int = 0,
    val cycleTarget: Int = 8,
    val pickupTimestamps: List<Long> = emptyList(),
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amount", amount)
        put("paidAt", paidAt)
        put("passagesCompleted", passagesCompleted)
        put("cycleTarget", cycleTarget)
        put("pickupTimestamps", JSONArray().apply { pickupTimestamps.forEach { put(it) } })
        put("note", note)
    }

    fun formattedPaidDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
        return sdf.format(Date(paidAt))
    }

    companion object {
        fun fromJson(o: JSONObject): WasteCyclePayment {
            val tsArr = o.optJSONArray("pickupTimestamps")
            val tsList = mutableListOf<Long>()
            if (tsArr != null) {
                for (i in 0 until tsArr.length()) {
                    tsList.add(tsArr.optLong(i))
                }
            }
            return WasteCyclePayment(
                id = o.optString("id", "PAY-${System.currentTimeMillis()}"),
                amount = o.optLong("amount", 0L),
                paidAt = o.optLong("paidAt", System.currentTimeMillis()),
                passagesCompleted = o.optInt("passagesCompleted", 0),
                cycleTarget = o.optInt("cycleTarget", 8),
                pickupTimestamps = tsList,
                note = o.optString("note", "")
            )
        }
    }
}

data class WasteHouseholdClient(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessPhone: String = "",
    val familyName: String,
    val contactName: String = "",
    val phone: String = "",
    val neighborhood: String = "",
    val status: WasteClientStatus = WasteClientStatus.CLIENT_ACTIF,
    val pickupDaysLabel: String = "Lun • Jeu",
    val monthlyFee: Long = 2500L,
    val targetPassages: Int = 8,
    val passagesLogs: List<WastePickupLog> = emptyList(),
    val paymentHistory: List<WasteCyclePayment> = emptyList(),
    val lastBillingNotifiedAt: Long = 0L,
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val currentCheckedCount: Int get() = passagesLogs.size
    val isCardFull: Boolean get() = targetPassages > 0 && passagesLogs.size >= targetPassages
    val totalHistoricalPickups: Int
        get() = passagesLogs.size + paymentHistory.sumOf { it.passagesCompleted }

    fun buildQrUri(): String {
        val b = Uri.encode(businessId)
        val c = Uri.encode(id)
        return "konabpos://waste?bizId=$b&clientId=$c"
    }

    fun buildBillingWhatsAppMessage(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val datesSummary = if (passagesLogs.isNotEmpty()) {
            passagesLogs.takeLast(12).joinToString("\n") { "• ${sdf.format(Date(it.timestamp))}" }
        } else {
            "• Cycle en cours"
        }
        val feeText = CurrencyFormatter.formatFCFA(monthlyFee)
        val greetingTarget = contactName.ifBlank { familyName }
        return buildString {
            appendLine("Bonjour $greetingTarget ($familyName),")
            appendLine("C'est l'équipe *$businessName* (Ramassage des ordures).")
            appendLine()
            appendLine("✅ Votre carte de passage est désormais *REMPLIE* : *$currentCheckedCount / $targetPassages passages effectués*.")
            appendLine("📅 Dates exactes des passages :")
            appendLine(datesSummary)
            appendLine()
            appendLine("💳 Montant de la mensualité à régler : *$feeText*.")
            appendLine("Vous pouvez ouvrir Konab POS -> Portail Poubelles (code client : $id) pour consulter votre carte et l'historique exact de nos passages.")
            append("Merci pour votre confiance !")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("business_name", businessName)
        put("business_phone", businessPhone)
        put("family_name", familyName)
        put("contact_name", contactName)
        put("phone", phone)
        put("neighborhood", neighborhood)
        put("status", status.name)
        put("pickup_days_label", pickupDaysLabel)
        put("monthly_fee", monthlyFee)
        put("target_passages", targetPassages)
        put("passages_logs_json", JSONArray().apply { passagesLogs.forEach { put(it.toJson()) } }.toString())
        put("payment_history_json", JSONArray().apply { paymentHistory.forEach { put(it.toJson()) } }.toString())
        put("last_billing_notified_at", lastBillingNotifiedAt)
        put("photo_uri", photoUri)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): WasteHouseholdClient {
            val logs = mutableListOf<WastePickupLog>()
            val logsRaw = o.optString("passages_logs_json", "")
            if (logsRaw.isNotBlank()) {
                try {
                    val arr = JSONArray(logsRaw)
                    for (i in 0 until arr.length()) {
                        logs.add(WastePickupLog.fromJson(arr.getJSONObject(i)))
                    }
                } catch (_: Exception) {}
            } else {
                o.optJSONArray("passagesLogs")?.let { arr ->
                    for (i in 0 until arr.length()) logs.add(WastePickupLog.fromJson(arr.getJSONObject(i)))
                }
            }

            val payments = mutableListOf<WasteCyclePayment>()
            val paysRaw = o.optString("payment_history_json", "")
            if (paysRaw.isNotBlank()) {
                try {
                    val arr = JSONArray(paysRaw)
                    for (i in 0 until arr.length()) {
                        payments.add(WasteCyclePayment.fromJson(arr.getJSONObject(i)))
                    }
                } catch (_: Exception) {}
            } else {
                o.optJSONArray("paymentHistory")?.let { arr ->
                    for (i in 0 until arr.length()) payments.add(WasteCyclePayment.fromJson(arr.getJSONObject(i)))
                }
            }

            return WasteHouseholdClient(
                id = o.optString("id", "FAM-${System.currentTimeMillis()}"),
                businessId = o.optString("business_id", o.optString("businessId", "")),
                businessName = o.optString("business_name", o.optString("businessName", "Service Salubrité")),
                businessPhone = o.optString("business_phone", o.optString("businessPhone", "")),
                familyName = o.optString("family_name", o.optString("familyName", "Famille")),
                contactName = o.optString("contact_name", o.optString("contactName", "")),
                phone = o.optString("phone", ""),
                neighborhood = o.optString("neighborhood", ""),
                status = WasteClientStatus.fromRaw(o.optString("status", WasteClientStatus.CLIENT_ACTIF.name)),
                pickupDaysLabel = o.optString("pickup_days_label", o.optString("pickupDaysLabel", "Lun • Jeu")),
                monthlyFee = o.optLong("monthly_fee", o.optLong("monthlyFee", 2500L)),
                targetPassages = o.optInt("target_passages", o.optInt("targetPassages", 8)).coerceIn(1, 31),
                passagesLogs = logs.sortedBy { it.timestamp },
                paymentHistory = payments.sortedByDescending { it.paidAt },
                lastBillingNotifiedAt = o.optLong("last_billing_notified_at", o.optLong("lastBillingNotifiedAt", 0L)),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class ParsedWasteQr(
    val businessId: String,
    val clientId: String
)

object WasteQrParser {
    fun parseWasteQrUri(raw: String?): ParsedWasteQr? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (!clean.contains("konabpos://waste", ignoreCase = true) &&
            !clean.contains("waste?", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val uri = Uri.parse(clean)
            val bizId = uri.getQueryParameter("bizId")?.trim().orEmpty()
            val clientId = uri.getQueryParameter("clientId")?.trim().orEmpty()
            if (bizId.isNotBlank() || clientId.isNotBlank()) {
                ParsedWasteQr(businessId = bizId, clientId = clientId)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

object WasteCollectorStorage {
    private const val PREFS = "konab_waste_collector_prefs"
    private const val KEY_BUSINESSES = "waste_businesses_json"
    private const val KEY_ACTIVE_BIZ_ID = "waste_active_biz_id"
    private const val KEY_CLIENTS = "waste_clients_json"
    private const val KEY_FAMILY_PHONE = "waste_family_query_phone"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizePhone(phone: String?): String {
        val digits = phone.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    @Synchronized
    fun getBusinesses(context: Context): List<WasteBusinessProfile> {
        val raw = prefs(context).getString(KEY_BUSINESSES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { WasteBusinessProfile.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveBusinesses(context: Context, list: List<WasteBusinessProfile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_BUSINESSES, arr.toString()).apply()
    }

    @Synchronized
    fun upsertBusiness(context: Context, profile: WasteBusinessProfile): WasteBusinessProfile {
        val list = getBusinesses(context).toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        val updated = profile.copy(updatedAt = maxOf(profile.updatedAt, System.currentTimeMillis()))
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        saveBusinesses(context, list)
        setActiveBusinessId(context, updated.id)
        return updated
    }

    @Synchronized
    fun getActiveBusinessId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_BIZ_ID, null)?.ifBlank { null }

    @Synchronized
    fun setActiveBusinessId(context: Context, bizId: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_BIZ_ID, bizId.orEmpty()).apply()
    }

    @Synchronized
    fun mergeBusinessesFromCloud(context: Context, remote: List<WasteBusinessProfile>): List<WasteBusinessProfile> {
        val byId = LinkedHashMap<String, WasteBusinessProfile>()
        getBusinesses(context).forEach { byId[it.id] = it }
        remote.forEach { r ->
            val cur = byId[r.id]
            if (cur == null || r.updatedAt >= cur.updatedAt) byId[r.id] = r
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveBusinesses(context, merged)
        return merged
    }

    @Synchronized
    fun getClients(context: Context): List<WasteHouseholdClient> {
        val raw = prefs(context).getString(KEY_CLIENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { WasteHouseholdClient.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveClients(context: Context, list: List<WasteHouseholdClient>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_CLIENTS, arr.toString()).apply()
    }

    @Synchronized
    fun upsertClient(context: Context, client: WasteHouseholdClient): WasteHouseholdClient {
        val list = getClients(context).toMutableList()
        val idx = list.indexOfFirst { it.id == client.id }
        val updated = client.copy(updatedAt = maxOf(client.updatedAt, System.currentTimeMillis()))
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        saveClients(context, list)
        return updated
    }

    @Synchronized
    fun deleteClient(context: Context, clientId: String) {
        saveClients(context, getClients(context).filterNot { it.id == clientId })
    }

    @Synchronized
    fun mergeClientsFromCloud(context: Context, remote: List<WasteHouseholdClient>): List<WasteHouseholdClient> {
        val byId = LinkedHashMap<String, WasteHouseholdClient>()
        getClients(context).forEach { byId[it.id] = it }
        remote.forEach { r ->
            val cur = byId[r.id]
            if (cur == null || r.updatedAt >= cur.updatedAt) byId[r.id] = r
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveClients(context, merged)
        return merged
    }

    fun getSavedFamilyQuery(context: Context): String =
        prefs(context).getString(KEY_FAMILY_PHONE, "") ?: ""

    fun saveFamilyQuery(context: Context, query: String) {
        prefs(context).edit().putString(KEY_FAMILY_PHONE, query.trim()).apply()
    }

    fun findClientsForFamily(clients: List<WasteHouseholdClient>, query: String): List<WasteHouseholdClient> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val qNormPhone = normalizePhone(q)
        return clients.filter { client ->
            val cPhone = normalizePhone(client.phone)
            client.id.equals(q, ignoreCase = true) ||
                (qNormPhone.length >= 6 && cPhone == qNormPhone) ||
                client.familyName.contains(q, ignoreCase = true)
        }
    }
}
