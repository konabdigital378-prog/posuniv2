package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.screens.AgentUser
import org.json.JSONArray
import org.json.JSONObject

data class AgentDeploymentRecord(
    val id: String,
    val agentId: String,
    val agentName: String,
    val agentPhone: String,
    val shopName: String,
    val shopPhone: String = "",
    val shopCity: String = "",
    val tier: String,
    val durationDays: Int,
    val licenseKey: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("agent_id", agentId)
        put("agent_name", agentName)
        put("agent_phone", agentPhone)
        put("shop_name", shopName)
        put("shop_phone", shopPhone)
        put("shop_city", shopCity)
        put("tier", tier)
        put("duration_days", durationDays)
        put("license_key", licenseKey)
        put("notes", notes)
        put("timestamp", timestamp)
        put("is_synced", isSynced)
    }

    companion object {
        fun fromJson(obj: JSONObject): AgentDeploymentRecord {
            return AgentDeploymentRecord(
                id = obj.optString("id", System.currentTimeMillis().toString()),
                agentId = obj.optString("agent_id", "AGT-101"),
                agentName = obj.optString("agent_name", "Agent Konab"),
                agentPhone = obj.optString("agent_phone", ""),
                shopName = obj.optString("shop_name", "Boutique"),
                shopPhone = obj.optString("shop_phone", ""),
                shopCity = obj.optString("shop_city", ""),
                tier = obj.optString("tier", "Professionnel"),
                durationDays = obj.optInt("duration_days", 365),
                licenseKey = obj.optString("license_key", ""),
                notes = obj.optString("notes", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                isSynced = obj.optBoolean("is_synced", false)
            )
        }
    }
}

object AgentDeploymentStorage {
    private const val PREFS_NAME = "konab_agent_deployments_prefs"
    private const val SESSION_PREFS_NAME = "konab_agent_session_prefs"
    private const val KEY_DEPLOYMENTS = "saved_deployments_json"
    private const val KEY_CLOUD_AGENTS = "saved_cloud_agents_json"
    private const val KEY_AGENT_PHOTOS = "saved_agent_photos_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getSessionPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- AGENT PHOTO PERSISTENCE (Keyed by Agent ID) ---

    @Synchronized
    fun saveAgentPhotoUri(context: Context, agentId: String, photoUri: String) {
        if (agentId.isBlank() || photoUri.isBlank()) return
        val cleanId = agentId.trim().uppercase()
        val currentMap = try {
            JSONObject(getPrefs(context).getString(KEY_AGENT_PHOTOS, "{}") ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        currentMap.put(cleanId, photoUri)
        getPrefs(context).edit().putString(KEY_AGENT_PHOTOS, currentMap.toString()).apply()
    }

    @Synchronized
    fun getAgentPhotoUri(context: Context, agentId: String): String? {
        if (agentId.isBlank()) return null
        val cleanId = agentId.trim().uppercase()
        return try {
            val map = JSONObject(getPrefs(context).getString(KEY_AGENT_PHOTOS, "{}") ?: "{}")
            val found = map.optString(cleanId, "")
            found.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun getEffectivePhotoUri(context: Context, agent: AgentUser): String? {
        return agent.photoUri.ifBlank { getAgentPhotoUri(context, agent.id) }
    }

    // --- AGENT SESSION PERSISTENCE ---

    @Synchronized
    fun saveAgentSession(context: Context, agent: AgentUser) {
        val effectivePhoto = agent.photoUri.ifBlank { getAgentPhotoUri(context, agent.id) ?: "" }
        if (effectivePhoto.isNotBlank()) {
            saveAgentPhotoUri(context, agent.id, effectivePhoto)
        }
        getSessionPrefs(context).edit()
            .putString("agent_id", agent.id)
            .putString("agent_name", agent.name)
            .putString("agent_phone", agent.phone)
            .putString("agent_zone", agent.zone)
            .putString("agent_pin", agent.pin)
            .putString("agent_role", agent.role)
            .putString("agent_photo_uri", effectivePhoto)
            .putLong("login_time", System.currentTimeMillis())
            .apply()
        // Also register in known local agents
        registerLocalAgent(context, agent.copy(photoUri = effectivePhoto))
    }

    @Synchronized
    fun getSavedAgentSession(context: Context): AgentUser? {
        val sp = getSessionPrefs(context)
        val id = sp.getString("agent_id", null) ?: return null
        val name = sp.getString("agent_name", null) ?: return null
        val phone = sp.getString("agent_phone", "") ?: ""
        val zone = sp.getString("agent_zone", "") ?: ""
        val pin = sp.getString("agent_pin", "") ?: ""
        val role = sp.getString("agent_role", "Agent Déployeur") ?: "Agent Déployeur"
        val photoUri = (sp.getString("agent_photo_uri", "") ?: "").ifBlank { getAgentPhotoUri(context, id) ?: "" }
        return AgentUser(id = id, name = name, phone = phone, zone = zone, pin = pin, role = role, photoUri = photoUri)
    }

    @Synchronized
    fun clearAgentSession(context: Context) {
        getSessionPrefs(context).edit().clear().apply()
    }

    // --- DEPLOYMENTS & ACTIVATED SHOPS ---

    @Synchronized
    fun saveDeployment(context: Context, deployment: AgentDeploymentRecord) {
        val targetName = normalizeShopName(deployment.shopName)
        val current = getAllDeployments(context).toMutableList()
        val existingById = current.firstOrNull { it.id == deployment.id }
        val existingByName = if (targetName.isNotBlank()) {
            current.firstOrNull { normalizeShopName(it.shopName) == targetName }
        } else {
            null
        }
        val canonicalId = existingById?.id ?: existingByName?.id ?: deployment.id
        val recordToSave = deployment.copy(id = canonicalId)

        current.removeAll {
            it.id == canonicalId || (targetName.isNotBlank() && normalizeShopName(it.shopName) == targetName)
        }
        current.add(0, recordToSave)

        val array = JSONArray()
        current.take(200).forEach { array.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_DEPLOYMENTS, array.toString()).apply()
    }

    @Synchronized
    fun saveCloudDeployments(context: Context, deployments: List<AgentDeploymentRecord>) {
        val merged = (getAllDeployments(context) + deployments.map { it.copy(isSynced = true) })
            .sortedWith(
                compareByDescending<AgentDeploymentRecord> { it.timestamp }
                    .thenByDescending { it.isSynced }
            )
        val deduped = mutableListOf<AgentDeploymentRecord>()
        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        for (d in merged) {
            val normName = normalizeShopName(d.shopName)
            if (d.id in seenIds) continue
            if (normName.isNotBlank() && normName in seenNames) continue
            seenIds.add(d.id)
            if (normName.isNotBlank()) seenNames.add(normName)
            deduped.add(d)
        }
        val array = JSONArray()
        deduped.take(300).forEach { array.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_DEPLOYMENTS, array.toString()).apply()
    }

    fun normalizeShopName(name: String): String {
        return name.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)
    }

    @Synchronized
    fun getDeploymentByShopName(context: Context, shopName: String): AgentDeploymentRecord? {
        val target = normalizeShopName(shopName)
        if (target.isBlank()) return null
        return getAllDeployments(context).firstOrNull { normalizeShopName(it.shopName) == target }
    }

    @Synchronized
    fun hasLocalDeploymentForShopName(context: Context, shopName: String): Boolean {
        val target = normalizeShopName(shopName)
        if (target.isBlank()) return false
        return getAllDeployments(context).any { normalizeShopName(it.shopName) == target }
    }

    @Synchronized
    fun markCreatedShopOnDevice(context: Context, shopName: String, agentId: String) {
        val cleanName = shopName.trim()
        val cleanAgent = agentId.trim().uppercase()
        if (cleanName.isBlank()) return
        getPrefs(context).edit()
            .putString("locked_created_shop_name", cleanName)
            .putString("locked_created_shop_agent_id", cleanAgent)
            .putLong("locked_created_shop_at", System.currentTimeMillis())
            .apply()
    }

    @Synchronized
    fun getLockedCreatedShopName(context: Context): String? {
        return getPrefs(context).getString("locked_created_shop_name", null)?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun clearCreatedShopLock(context: Context) {
        getPrefs(context).edit()
            .remove("locked_created_shop_name")
            .remove("locked_created_shop_agent_id")
            .remove("locked_created_shop_at")
            .apply()
    }

    @Synchronized
    fun getDeploymentsForAgent(context: Context, agentId: String): List<AgentDeploymentRecord> {
        val clean = agentId.trim().uppercase()
        if (clean.isBlank()) return emptyList()
        return getAllDeployments(context).filter {
            it.agentId.trim().equals(clean, ignoreCase = true)
        }
    }

    @Synchronized
    fun markAsSynced(context: Context, deploymentId: String) {
        val current = getAllDeployments(context).toMutableList()
        val index = current.indexOfFirst { it.id == deploymentId }
        if (index >= 0) {
            current[index] = current[index].copy(isSynced = true)
            val array = JSONArray()
            current.forEach { array.put(it.toJson()) }
            getPrefs(context).edit().putString(KEY_DEPLOYMENTS, array.toString()).apply()
        }
    }

    @Synchronized
    fun getAllDeployments(context: Context): List<AgentDeploymentRecord> {
        val json = getPrefs(context).getString(KEY_DEPLOYMENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<AgentDeploymentRecord>()
            for (i in 0 until array.length()) {
                list.add(AgentDeploymentRecord.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- AGENTS DIRECTORY ---

    @Synchronized
    fun generateUniqueAgentMatricule(
        context: Context,
        additionalAgents: List<AgentUser> = emptyList()
    ): String {
        val takenIds = mutableSetOf<String>()
        com.example.ui.screens.AgentDirectory.PRESET_AGENTS.forEach { takenIds.add(it.id.trim().uppercase()) }
        getCloudAgents(context).forEach { if (it.id.isNotBlank()) takenIds.add(it.id.trim().uppercase()) }
        additionalAgents.forEach { if (it.id.isNotBlank()) takenIds.add(it.id.trim().uppercase()) }
        getSavedAgentSession(context)?.let { if (it.id.isNotBlank()) takenIds.add(it.id.trim().uppercase()) }

        val yearSuffix = java.text.SimpleDateFormat("yy", java.util.Locale.US)
            .format(java.util.Date())

        var candidate: String
        var attempts = 0
        do {
            val numberPart = (1000..9999).random()
            candidate = "AGT-$yearSuffix-$numberPart"
            attempts++
        } while (takenIds.contains(candidate.uppercase()) && attempts < 200)

        if (takenIds.contains(candidate.uppercase())) {
            val fallbackTail = (System.currentTimeMillis() % 100000L).toString().padStart(5, '0')
            candidate = "AGT-$yearSuffix-$fallbackTail"
        }
        return candidate
    }

    @Synchronized
    fun registerLocalAgent(context: Context, agent: AgentUser) {
        val effectivePhoto = agent.photoUri.ifBlank { getAgentPhotoUri(context, agent.id) ?: "" }
        if (effectivePhoto.isNotBlank()) {
            saveAgentPhotoUri(context, agent.id, effectivePhoto)
        }
        val agentWithPhoto = agent.copy(photoUri = effectivePhoto)
        val current = getCloudAgents(context).toMutableList()
        val index = current.indexOfFirst { it.id.equals(agentWithPhoto.id, ignoreCase = true) }
        if (index >= 0) {
            current[index] = agentWithPhoto
        } else {
            current.add(agentWithPhoto)
        }
        saveCloudAgents(context, current)
    }

    @Synchronized
    fun saveCloudAgents(context: Context, agents: List<AgentUser>) {
        val array = JSONArray()
        agents.forEach { a ->
            val effectivePhoto = a.photoUri.ifBlank { getAgentPhotoUri(context, a.id) ?: "" }
            val obj = JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("phone", a.phone)
                put("zone", a.zone)
                put("pin", a.pin)
                put("role", a.role)
                put("photo_uri", effectivePhoto)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CLOUD_AGENTS, array.toString()).apply()
    }

    @Synchronized
    fun getCloudAgents(context: Context): List<AgentUser> {
        val json = getPrefs(context).getString(KEY_CLOUD_AGENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<AgentUser>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val id = o.optString("id", "")
                val photoUri = o.optString("photo_uri", "").ifBlank { getAgentPhotoUri(context, id) ?: "" }
                list.add(
                    AgentUser(
                        id = id,
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        zone = o.optString("zone", ""),
                        pin = o.optString("pin", "1234"),
                        role = o.optString("role", "Agent Déployeur"),
                        photoUri = photoUri
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
