package com.example.data.remote.pocketbase

import android.content.Context
import com.example.BuildConfig

enum class CloudBackendProvider(val displayName: String) {
    SUPABASE("Supabase"),
    POCKETBASE("PocketBase")
}

/**
 * Configuration persistante pour le backend PocketBase (v0.23+).
 * Permet de basculer sans perte entre Supabase et PocketBase depuis les Paramètres
 * ou automatiquement via la variable BuildConfig.POCKETBASE_URL.
 */
object PocketBaseConfig {

    private const val PREFS_NAME = "konab_pocketbase_prefs"
    private const val KEY_PROVIDER = "cloud_backend_provider"
    private const val KEY_CUSTOM_URL = "custom_pocketbase_url"

    @Volatile
    private var activeProvider: CloudBackendProvider = CloudBackendProvider.SUPABASE

    @Volatile
    private var customUrlOverride: String? = null

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_CUSTOM_URL, null)?.trim()
            customUrlOverride = if (!savedUrl.isNullOrBlank() && isValidHttpUrl(savedUrl)) {
                savedUrl.trimEnd('/')
            } else {
                null
            }

            val savedProvider = prefs.getString(KEY_PROVIDER, null)?.trim()
            activeProvider = when {
                savedProvider.equals(CloudBackendProvider.POCKETBASE.name, ignoreCase = true) ->
                    CloudBackendProvider.POCKETBASE
                savedProvider.equals(CloudBackendProvider.SUPABASE.name, ignoreCase = true) ->
                    CloudBackendProvider.SUPABASE
                isValidHttpUrl(buildConfigUrl) ->
                    CloudBackendProvider.POCKETBASE
                else ->
                    CloudBackendProvider.SUPABASE
            }
        } catch (_: Exception) {}
    }

    fun setProvider(context: Context, provider: CloudBackendProvider) {
        activeProvider = provider
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROVIDER, provider.name)
                .apply()
        } catch (_: Exception) {}
    }

    fun saveConfig(context: Context, url: String, activatePocketBase: Boolean = true) {
        val cleanUrl = url.trim().trimEnd('/')
        customUrlOverride = if (isValidHttpUrl(cleanUrl)) cleanUrl else null
        if (activatePocketBase) {
            activeProvider = CloudBackendProvider.POCKETBASE
        }
        try {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (customUrlOverride != null) {
                editor.putString(KEY_CUSTOM_URL, customUrlOverride)
            } else {
                editor.remove(KEY_CUSTOM_URL)
            }
            if (activatePocketBase) {
                editor.putString(KEY_PROVIDER, CloudBackendProvider.POCKETBASE.name)
            }
            editor.apply()
        } catch (_: Exception) {}
    }

    fun clearConfig(context: Context) {
        customUrlOverride = null
        activeProvider = CloudBackendProvider.SUPABASE
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CUSTOM_URL)
                .putString(KEY_PROVIDER, CloudBackendProvider.SUPABASE.name)
                .apply()
        } catch (_: Exception) {}
    }

    private val buildConfigUrl: String
        get() = try {
            BuildConfig.POCKETBASE_URL.trim().trimEnd('/')
        } catch (_: Exception) {
            ""
        }

    val provider: CloudBackendProvider
        get() = activeProvider

    val isPocketBaseActive: Boolean
        get() = activeProvider == CloudBackendProvider.POCKETBASE

    val rawUrl: String
        get() {
            val override = customUrlOverride
            if (!override.isNullOrBlank() && isValidHttpUrl(override)) {
                return override
            }
            val envVal = buildConfigUrl
            if (isValidHttpUrl(envVal)) {
                return envVal
            }
            return ""
        }

    val apiBaseUrl: String
        get() {
            val base = rawUrl.trimEnd('/')
            return if (base.isBlank()) "" else "$base/api"
        }

    val isConfigured: Boolean
        get() = isValidHttpUrl(rawUrl)

    fun isValidHttpUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        return (clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)) &&
            clean.length > 10 &&
            !clean.contains("your-pocketbase", ignoreCase = true)
    }
}
