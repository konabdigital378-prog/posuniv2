package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compte Utilisateur Public KONAB POS • Conakry Web.
 * Permet à n'importe quel citoyen/client de créer son compte ou se connecter,
 * obtenir sa carte numérique personnalisée et accéder aux services (Cantines, Restaurants,
 * Couture, Salubrité Poubelles, Tontines 30j) avec pré-remplissage automatique.
 */
data class PublicUserAccount(
    val id: String,
    val fullName: String,
    val phone: String,
    val city: String = "Conakry",
    val neighborhood: String = "",
    val pin: String = "0000",
    val photoUri: String = "",
    val preferredActivity: String = "UTILISATEUR",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun buildQrUri(): String {
        val uId = Uri.encode(id)
        val uPhone = Uri.encode(phone)
        return "konabpos://user?id=$uId&phone=$uPhone"
    }

    fun toJson(): JSONObject = JSONObject().apply {
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

    companion object {
        fun fromJson(o: JSONObject): PublicUserAccount {
            return PublicUserAccount(
                id = o.optString("id", "USR-${System.currentTimeMillis()}"),
                fullName = o.optString("full_name", o.optString("fullName", "Utilisateur")),
                phone = o.optString("phone", ""),
                city = o.optString("city", "Conakry"),
                neighborhood = o.optString("neighborhood", ""),
                pin = o.optString("pin", "0000"),
                photoUri = o.optString("photo_uri", o.optString("photoUri", "")),
                preferredActivity = o.optString("preferred_activity", o.optString("preferredActivity", "UTILISATEUR")),
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
                byId[remote.id] = remote
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
