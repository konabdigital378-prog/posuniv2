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

data class TontineBusinessProfile(
    val id: String,
    val businessName: String,
    val organizerName: String = "Organisatrice",
    val phone: String = "",
    val city: String = "Conakry",
    val pin: String = "1234",
    val deployerId: String = "",
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_name", businessName)
        put("organizer_name", organizerName)
        put("phone", phone)
        put("city", city)
        put("pin", pin)
        put("deployer_id", deployerId)
        put("photo_uri", photoUri)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): TontineBusinessProfile {
            return TontineBusinessProfile(
                id = o.optString("id", "TONT-BIZ-${System.currentTimeMillis()}"),
                businessName = o.optString("business_name", o.optString("businessName", "Tontine Solidarité")),
                organizerName = o.optString("organizer_name", o.optString("organizerName", "Organisatrice")),
                phone = o.optString("phone", ""),
                city = o.optString("city", "Conakry"),
                pin = o.optString("pin", "1234"),
                deployerId = o.optString("deployer_id", o.optString("deployerId", "")),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class TontineGroup(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessPhone: String = "",
    val title: String,
    val dailyAmount: Long = 1000L,
    val totalDays: Int = 30,
    val frequencyLabel: String = "Journalier • Carte 30 jours",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalTargetAmount: Long get() = dailyAmount * totalDays

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("business_name", businessName)
        put("business_phone", businessPhone)
        put("title", title)
        put("daily_amount", dailyAmount)
        put("total_days", totalDays)
        put("frequency_label", frequencyLabel)
        put("is_active", isActive)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): TontineGroup {
            return TontineGroup(
                id = o.optString("id", "TONT-GRP-${System.currentTimeMillis()}"),
                businessId = o.optString("business_id", o.optString("businessId", "")),
                businessName = o.optString("business_name", o.optString("businessName", "Business Tontine")),
                businessPhone = o.optString("business_phone", o.optString("businessPhone", "")),
                title = o.optString("title", "Tontine 30 jours"),
                dailyAmount = o.optLong("daily_amount", o.optLong("dailyAmount", 1000L)),
                totalDays = o.optInt("total_days", o.optInt("totalDays", 30)).coerceIn(1, 31),
                frequencyLabel = o.optString("frequency_label", o.optString("frequencyLabel", "Journalier • Carte 30 jours")),
                isActive = o.optBoolean("is_active", o.optBoolean("isActive", true)),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class TontineCheckMark(
    val dayNumber: Int,
    val checkedAt: Long = System.currentTimeMillis(),
    val amount: Long = 0L,
    val checkedBy: String = "Organisatrice"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("dayNumber", dayNumber)
        put("checkedAt", checkedAt)
        put("amount", amount)
        put("checkedBy", checkedBy)
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
        return sdf.format(Date(checkedAt))
    }

    companion object {
        fun fromJson(o: JSONObject): TontineCheckMark {
            return TontineCheckMark(
                dayNumber = o.optInt("dayNumber", 1),
                checkedAt = o.optLong("checkedAt", System.currentTimeMillis()),
                amount = o.optLong("amount", 0L),
                checkedBy = o.optString("checkedBy", "Organisatrice")
            )
        }
    }
}

enum class TontineCardStatus(val label: String) {
    EN_COURS("En cours"),
    SOLDEE_A_PAYER("Soldée • Paiement à recevoir"),
    PAYEE("Payée & Clôturée");

    companion object {
        fun fromRaw(raw: String?): TontineCardStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: EN_COURS
        }
    }
}

data class TontineMemberCard(
    val id: String,
    val businessId: String,
    val businessName: String,
    val businessPhone: String = "",
    val groupId: String,
    val groupTitle: String,
    val memberName: String,
    val memberPhone: String,
    val dailyAmount: Long = 1000L,
    val totalDays: Int = 30,
    val payoutTurnNumber: Int = 1,
    val checkMarks: List<TontineCheckMark> = emptyList(),
    val status: TontineCardStatus = TontineCardStatus.EN_COURS,
    val validatedAt: Long = 0L,
    val validationMessage: String = "",
    val paidAt: Long = 0L,
    val photoUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val checkedCount: Int get() = checkMarks.size
    val remainingDays: Int get() = (totalDays - checkedCount).coerceAtLeast(0)
    val totalCollected: Long get() = checkMarks.sumOf { it.amount }.takeIf { it > 0L } ?: (checkedCount * dailyAmount)
    val expectedTotal: Long get() = dailyAmount * totalDays
    val isFullCard: Boolean get() = totalDays > 0 && checkedCount >= totalDays

    fun isDayChecked(day: Int): Boolean = checkMarks.any { it.dayNumber == day }
    fun markForDay(day: Int): TontineCheckMark? = checkMarks.firstOrNull { it.dayNumber == day }

    fun nextUncheckedDay(): Int? = (1..totalDays).firstOrNull { d -> checkMarks.none { it.dayNumber == d } }

    fun buildQrUri(): String {
        val b = Uri.encode(businessId)
        val c = Uri.encode(id)
        return "konabpos://tontine?bizId=$b&cardId=$c"
    }

    fun buildSettledMessage(): String {
        val amountText = CurrencyFormatter.formatFCFA(totalCollected.coerceAtLeast(expectedTotal))
        return "Félicitations $memberName ! Votre carte de tontine « $groupTitle » chez $businessName est SOLDÉE ($checkedCount/$totalDays jours validés = $amountText). Vous devez recevoir votre paiement !"
    }

    fun buildWhatsAppSettledMessage(): String {
        val amountText = CurrencyFormatter.formatFCFA(totalCollected.coerceAtLeast(expectedTotal))
        return buildString {
            appendLine("Bonjour *$memberName* 🌸,")
            appendLine("Excellente nouvelle de *$businessName* !")
            appendLine()
            appendLine("✅ Votre carte numérique de tontine *« $groupTitle »* est *SOLDÉE & VALIDÉE* ($checkedCount / $totalDays jours pointés).")
            appendLine("💰 Montant à recevoir : *$amountText*.")
            appendLine()
            appendLine("Vous pouvez consulter votre carte et le détail de vos $checkedCount pointages en temps réel sur Konab POS -> Tontine -> Mes Tontines (Code carte : $id).")
            append("Merci pour votre régularité !")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("business_id", businessId)
        put("business_name", businessName)
        put("business_phone", businessPhone)
        put("group_id", groupId)
        put("group_title", groupTitle)
        put("member_name", memberName)
        put("member_phone", memberPhone)
        put("daily_amount", dailyAmount)
        put("total_days", totalDays)
        put("payout_turn_number", payoutTurnNumber)
        put("check_marks_json", JSONArray().apply { checkMarks.forEach { put(it.toJson()) } }.toString())
        put("status", status.name)
        put("validated_at", validatedAt)
        put("validation_message", validationMessage)
        put("paid_at", paidAt)
        put("photo_uri", photoUri)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): TontineMemberCard {
            val marks = mutableListOf<TontineCheckMark>()
            val rawMarks = o.optString("check_marks_json", "")
            if (rawMarks.isNotBlank()) {
                try {
                    val arr = JSONArray(rawMarks)
                    for (i in 0 until arr.length()) marks.add(TontineCheckMark.fromJson(arr.getJSONObject(i)))
                } catch (_: Exception) {}
            } else {
                o.optJSONArray("checkMarks")?.let { arr ->
                    for (i in 0 until arr.length()) marks.add(TontineCheckMark.fromJson(arr.getJSONObject(i)))
                }
            }
            return TontineMemberCard(
                id = o.optString("id", "CARD-${System.currentTimeMillis()}"),
                businessId = o.optString("business_id", o.optString("businessId", "")),
                businessName = o.optString("business_name", o.optString("businessName", "Tontine")),
                businessPhone = o.optString("business_phone", o.optString("businessPhone", "")),
                groupId = o.optString("group_id", o.optString("groupId", "")),
                groupTitle = o.optString("group_title", o.optString("groupTitle", "Tontine 30 jours")),
                memberName = o.optString("member_name", o.optString("memberName", "Membre")),
                memberPhone = o.optString("member_phone", o.optString("memberPhone", "")),
                dailyAmount = o.optLong("daily_amount", o.optLong("dailyAmount", 1000L)),
                totalDays = o.optInt("total_days", o.optInt("totalDays", 30)).coerceIn(1, 31),
                payoutTurnNumber = o.optInt("payout_turn_number", o.optInt("payoutTurnNumber", 1)).coerceAtLeast(1),
                checkMarks = marks.distinctBy { it.dayNumber }.sortedBy { it.dayNumber },
                status = TontineCardStatus.fromRaw(o.optString("status", TontineCardStatus.EN_COURS.name)),
                validatedAt = o.optLong("validated_at", o.optLong("validatedAt", 0L)),
                validationMessage = o.optString("validation_message", o.optString("validationMessage", "")),
                paidAt = o.optLong("paid_at", o.optLong("paidAt", 0L)),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

data class ParsedTontineQr(
    val businessId: String,
    val cardId: String
)

object TontineQrParser {
    fun parseTontineQrUri(raw: String?): ParsedTontineQr? {
        val clean = raw?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (!clean.contains("konabpos://tontine", ignoreCase = true) &&
            !clean.contains("tontine?", ignoreCase = true)
        ) {
            return null
        }
        return try {
            val uri = Uri.parse(clean)
            val bizId = uri.getQueryParameter("bizId")?.trim().orEmpty()
            val cardId = uri.getQueryParameter("cardId")?.trim().orEmpty()
            if (bizId.isNotBlank() || cardId.isNotBlank()) {
                ParsedTontineQr(businessId = bizId, cardId = cardId)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

object TontineStorage {
    private const val PREFS = "konab_tontine_prefs"
    private const val KEY_BUSINESSES = "tontine_businesses_json"
    private const val KEY_ACTIVE_BIZ_ID = "tontine_active_biz_id"
    private const val KEY_GROUPS = "tontine_groups_json"
    private const val KEY_CARDS = "tontine_cards_json"
    private const val KEY_MEMBER_PHONE = "tontine_member_query_phone"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizePhone(phone: String?): String {
        val digits = phone.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    @Synchronized
    fun getBusinesses(context: Context): List<TontineBusinessProfile> {
        val raw = prefs(context).getString(KEY_BUSINESSES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { TontineBusinessProfile.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveBusinesses(context: Context, list: List<TontineBusinessProfile>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_BUSINESSES, arr.toString()).apply()
    }

    @Synchronized
    fun upsertBusiness(context: Context, profile: TontineBusinessProfile): TontineBusinessProfile {
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
    fun mergeBusinessesFromCloud(context: Context, remote: List<TontineBusinessProfile>): List<TontineBusinessProfile> {
        val byId = LinkedHashMap<String, TontineBusinessProfile>()
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
    fun getGroups(context: Context): List<TontineGroup> {
        val raw = prefs(context).getString(KEY_GROUPS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { TontineGroup.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveGroups(context: Context, list: List<TontineGroup>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    @Synchronized
    fun upsertGroup(context: Context, group: TontineGroup): TontineGroup {
        val list = getGroups(context).toMutableList()
        val idx = list.indexOfFirst { it.id == group.id }
        val updated = group.copy(updatedAt = maxOf(group.updatedAt, System.currentTimeMillis()))
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        saveGroups(context, list)
        return updated
    }

    @Synchronized
    fun mergeGroupsFromCloud(context: Context, remote: List<TontineGroup>): List<TontineGroup> {
        val byId = LinkedHashMap<String, TontineGroup>()
        getGroups(context).forEach { byId[it.id] = it }
        remote.forEach { r ->
            val cur = byId[r.id]
            if (cur == null || r.updatedAt >= cur.updatedAt) byId[r.id] = r
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveGroups(context, merged)
        return merged
    }

    @Synchronized
    fun getCards(context: Context): List<TontineMemberCard> {
        val raw = prefs(context).getString(KEY_CARDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { TontineMemberCard.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveCards(context: Context, list: List<TontineMemberCard>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_CARDS, arr.toString()).apply()
    }

    @Synchronized
    fun upsertCard(context: Context, card: TontineMemberCard): TontineMemberCard {
        val list = getCards(context).toMutableList()
        val idx = list.indexOfFirst { it.id == card.id }
        val updated = card.copy(updatedAt = maxOf(card.updatedAt, System.currentTimeMillis()))
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        saveCards(context, list)
        return updated
    }

    @Synchronized
    fun deleteCard(context: Context, cardId: String) {
        saveCards(context, getCards(context).filterNot { it.id == cardId })
    }

    @Synchronized
    fun mergeCardsFromCloud(context: Context, remote: List<TontineMemberCard>): List<TontineMemberCard> {
        val byId = LinkedHashMap<String, TontineMemberCard>()
        getCards(context).forEach { byId[it.id] = it }
        remote.forEach { r ->
            val cur = byId[r.id]
            if (cur == null || r.updatedAt >= cur.updatedAt) byId[r.id] = r
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveCards(context, merged)
        return merged
    }

    fun getSavedMemberPhone(context: Context): String =
        prefs(context).getString(KEY_MEMBER_PHONE, "") ?: ""

    fun saveMemberPhone(context: Context, phoneOrCode: String) {
        prefs(context).edit().putString(KEY_MEMBER_PHONE, phoneOrCode.trim()).apply()
    }

    fun findCardsForMember(cards: List<TontineMemberCard>, query: String): List<TontineMemberCard> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val qNorm = normalizePhone(q)
        return cards.filter { c ->
            val mNorm = normalizePhone(c.memberPhone)
            c.id.equals(q, ignoreCase = true) ||
                (qNorm.length >= 6 && mNorm == qNorm) ||
                c.memberName.contains(q, ignoreCase = true)
        }
    }
}
