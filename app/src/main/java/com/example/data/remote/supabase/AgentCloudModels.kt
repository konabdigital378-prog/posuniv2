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
    private const val KEY_DEPLOYMENTS = "saved_deployments_json"
    private const val KEY_CLOUD_AGENTS = "saved_cloud_agents_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun saveDeployment(context: Context, deployment: AgentDeploymentRecord) {
        val current = getAllDeployments(context).toMutableList()
        val index = current.indexOfFirst { it.id == deployment.id }
        if (index >= 0) {
            current[index] = deployment
        } else {
            current.add(0, deployment)
        }
        val array = JSONArray()
        current.take(100).forEach { array.put(it.toJson()) }
        getPrefs(context).edit().putString(KEY_DEPLOYMENTS, array.toString()).apply()
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

    @Synchronized
    fun saveCloudAgents(context: Context, agents: List<AgentUser>) {
        val array = JSONArray()
        agents.forEach { a ->
            val obj = JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("phone", a.phone)
                put("zone", a.zone)
                put("pin", a.pin)
                put("role", a.role)
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
                list.add(
                    AgentUser(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        phone = o.optString("phone", ""),
                        zone = o.optString("zone", ""),
                        pin = o.optString("pin", "1234"),
                        role = o.optString("role", "Agent Déployeur")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
