package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compte Utilisateur Public KONAB POS.
 * Permet à n'importe quel citoyen/client de créer son compte ou se connecter,
 * modifier librement ses informations (nom, téléphone, ville, accompagnement, boutique affiliée),
 * obtenir sa carte numérique personnalisée et accéder aux services.
 */
data class PublicUserAccount(
    val id: String,
    val fullName: String,
    val phone: String,
    val city: String = "",
    val neighborhood: String = "",
    val pin: String = "",
    val photoUri: String = "",
    val preferredActivity: String = "UTILISATEUR",
    val isSeekingAccompaniment: Boolean = false,
    val accompanimentDetails: String = "",
    val isShopMember: Boolean = false,
    val memberShopName: String = "",
    val memberShopRole: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun buildQrUri(): String {
        val uId = Uri.encode(id)
        val uPhone = Uri.encode(phone)
        return "konabpos://user?id=$uId&phone=$uPhone"
    }

    fun toBaseJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("full_name", fullName)
        put("phone", phone)
        put("city", city)
        put("neighborhood", neighborhood)
        put("pin", pin)
        put("photo_uri", photoUri)
        put("preferred_activity", preferredActivity)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    fun toJson(): JSONObject = toBaseJson().apply {
        put("is_seeking_accompaniment", isSeekingAccompaniment)
        put("accompaniment_details", accompanimentDetails)
        put("is_shop_member", isShopMember)
        put("member_shop_name", memberShopName)
        put("member_shop_role", memberShopRole)
    }

    companion object {
        private fun JSONObject.optCleanString(vararg keys: String): String {
            for (k in keys) {
                if (has(k) && !isNull(k)) {
                    val v = optString(k, "").trim()
                    if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) {
                        return v
                    }
                }
            }
            return ""
        }

        fun fromJson(o: JSONObject): PublicUserAccount {
            return PublicUserAccount(
                id = o.optCleanString("id").ifBlank { "USR-${System.currentTimeMillis()}" },
                fullName = o.optCleanString("full_name", "fullName"),
                phone = o.optCleanString("phone"),
                city = o.optCleanString("city"),
                neighborhood = o.optCleanString("neighborhood"),
                pin = o.optCleanString("pin"),
                photoUri = o.optCleanString("photo_uri", "photoUri"),
                preferredActivity = o.optCleanString("preferred_activity", "preferredActivity").ifBlank { "UTILISATEUR" },
                isSeekingAccompaniment = o.optBoolean("is_seeking_accompaniment", o.optBoolean("isSeekingAccompaniment", false)),
                accompanimentDetails = o.optCleanString("accompaniment_details", "accompanimentDetails"),
                isShopMember = o.optBoolean("is_shop_member", o.optBoolean("isShopMember", false)),
                memberShopName = o.optCleanString("member_shop_name", "memberShopName"),
                memberShopRole = o.optCleanString("member_shop_role", "memberShopRole"),
                createdAt = o.optLong("created_at", o.optLong("createdAt", System.currentTimeMillis())),
                updatedAt = o.optLong("updated_at", o.optLong("updatedAt", System.currentTimeMillis()))
            )
        }
    }
}

object PublicUserStorage {
    private const val PREFS = "konab_public_user_prefs"
    private const val KEY_ACTIVE_USER = "active_public_user_json"
    private const val KEY_ALL_USERS = "registered_public_users_json"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun normalizePhone(phone: String?): String {
        val digits = phone.orEmpty().filter { it.isDigit() }
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    @Synchronized
    fun getActiveUser(context: Context): PublicUserAccount? {
        val raw = prefs(context).getString(KEY_ACTIVE_USER, null)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            PublicUserAccount.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun setActiveUser(context: Context, user: PublicUserAccount?) {
        val edit = prefs(context).edit()
        if (user == null) {
            edit.remove(KEY_ACTIVE_USER)
        } else {
            edit.putString(KEY_ACTIVE_USER, user.toJson().toString())
        }
        edit.apply()
    }

    @Synchronized
    fun getAllUsers(context: Context): List<PublicUserAccount> {
        val raw = prefs(context).getString(KEY_ALL_USERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { PublicUserAccount.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveAllUsers(context: Context, users: List<PublicUserAccount>) {
        val arr = JSONArray().apply { users.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY_ALL_USERS, arr.toString()).apply()
    }

    @Synchronized
    fun upsertUser(context: Context, user: PublicUserAccount, makeActive: Boolean = true): PublicUserAccount {
        val normPhone = normalizePhone(user.phone)
        val existingList = getAllUsers(context).toMutableList()
        val idx = existingList.indexOfFirst {
            it.id == user.id || (normPhone.length >= 6 && normalizePhone(it.phone) == normPhone)
        }
        val now = maxOf(user.updatedAt, System.currentTimeMillis())
        val updated = user.copy(updatedAt = now)
        if (idx >= 0) {
            existingList[idx] = updated
        } else {
            existingList.add(0, updated)
        }
        saveAllUsers(context, existingList)
        if (makeActive) {
            setActiveUser(context, updated)
        }
        return updated
    }

    @Synchronized
    fun mergeUsersFromCloud(context: Context, remoteUsers: List<PublicUserAccount>): List<PublicUserAccount> {
        val byId = LinkedHashMap<String, PublicUserAccount>()
        getAllUsers(context).forEach { byId[it.id] = it }
        remoteUsers.forEach { remote ->
            val current = byId[remote.id]
            if (current == null || remote.updatedAt >= current.updatedAt) {
                val mergedRow = if (current != null) {
                    remote.copy(
                        city = remote.city.ifBlank { current.city },
                        neighborhood = remote.neighborhood.ifBlank { current.neighborhood },
                        isSeekingAccompaniment = remote.isSeekingAccompaniment || current.isSeekingAccompaniment,
                        accompanimentDetails = remote.accompanimentDetails.ifBlank { current.accompanimentDetails },
                        isShopMember = remote.isShopMember || current.isShopMember,
                        memberShopName = remote.memberShopName.ifBlank { current.memberShopName },
                        memberShopRole = remote.memberShopRole.ifBlank { current.memberShopRole }
                    )
                } else {
                    remote
                }
                byId[remote.id] = mergedRow
            }
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        saveAllUsers(context, merged)

        val currentActive = getActiveUser(context)
        if (currentActive != null) {
            val fresh = merged.firstOrNull {
                it.id == currentActive.id ||
                    (normalizePhone(it.phone).length >= 6 && normalizePhone(it.phone) == normalizePhone(currentActive.phone))
            }
            if (fresh != null && fresh.updatedAt >= currentActive.updatedAt) {
                setActiveUser(context, fresh)
            }
        }
        return merged
    }

    fun findByPhoneOrId(users: List<PublicUserAccount>, query: String): PublicUserAccount? {
        val clean = query.trim()
        if (clean.isBlank()) return null
        val qPhone = normalizePhone(clean)
        return users.firstOrNull { u ->
            u.id.equals(clean, ignoreCase = true) ||
                (qPhone.length >= 6 && normalizePhone(u.phone) == qPhone)
        }
    }

    fun clearSession(context: Context) {
        setActiveUser(context, null)
    }
}
