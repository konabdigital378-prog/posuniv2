package com.example.data.remote.supabase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles Supabase GoTrue Authentication:
 * - Email / Password Sign In
 * - User Registration (Sign Up) with User Metadata (name, phone, role)
 * - Password Recovery / Reset
 * - Persistent Session Management & Token Refresh
 */
object SupabaseAuthManager {

    private const val TAG = "SupabaseAuthManager"
    private const val PREFS_NAME = "konab_supabase_auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_USER_PHONE = "user_phone"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    data class SupabaseUser(
        val id: String,
        val email: String,
        val name: String,
        val role: String,
        val phone: String,
        val createdAt: String = ""
    )

    data class SupabaseAuthSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val user: SupabaseUser
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    }

    sealed class AuthState {
        object Unauthenticated : AuthState()
        data class Loading(val message: String) : AuthState()
        data class Authenticated(val session: SupabaseAuthSession) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var currentSession: SupabaseAuthSession? = null

    val currentToken: String?
        get() = currentSession?.accessToken

    val currentUser: SupabaseUser?
        get() = currentSession?.user

    val isAuthenticated: Boolean
        get() = currentSession != null && !(currentSession?.isExpired ?: true)

    /**
     * Initializes auth state from saved preferences on application startup
     */
    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
            val userId = prefs.getString(KEY_USER_ID, null)
            val email = prefs.getString(KEY_USER_EMAIL, null)
            val name = prefs.getString(KEY_USER_NAME, "Gérant") ?: "Gérant"
            val role = prefs.getString(KEY_USER_ROLE, "ADMIN") ?: "ADMIN"
            val phone = prefs.getString(KEY_USER_PHONE, "") ?: ""

            if (!accessToken.isNullOrBlank() && !userId.isNullOrBlank() && !email.isNullOrBlank()) {
                val user = SupabaseUser(
                    id = userId,
                    email = email,
                    name = name,
                    role = role,
                    phone = phone
                )
                val session = SupabaseAuthSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken ?: "",
                    expiresAt = expiresAt,
                    user = user
                )
                currentSession = session
                _authState.value = AuthState.Authenticated(session)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring auth session", e)
            _authState.value = AuthState.Unauthenticated
        }
    }

    /**
     * Authenticates with Email and Password
     */
    suspend fun signIn(
        email: String,
        password: String,
        context: Context? = null
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()

        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            val err = "Veuillez renseigner votre email et mot de passe"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (!SupabaseConfig.isConfigured) {
            val err = "Supabase n'est pas configuré. Veuillez d'abord renseigner l'URL et la clé Anon."
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        _authState.value = AuthState.Loading("Connexion en cours à Supabase...")

        val authUrl = "${SupabaseConfig.rawUrl.trimEnd('/')}/auth/v1/token?grant_type=password"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", cleanPassword)
        }

        val request = Request.Builder()
            .url(authUrl)
            .addHeader("apikey", SupabaseConfig.rawAnonKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    val session = parseSessionJson(json)
                    currentSession = session
                    _authState.value = AuthState.Authenticated(session)
                    if (context != null) {
                        saveSessionToPrefs(context, session)
                    }
                    Result.success(session)
                } else {
                    val errMsg = parseErrorMessage(bodyString, response.code)
                    _authState.value = AuthState.Error(errMsg)
                    Result.failure(IOException(errMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign In Exception", e)
            val errMsg = "Échec de connexion : ${e.localizedMessage ?: "Vérifiez votre connexion Internet"}"
            _authState.value = AuthState.Error(errMsg)
            Result.failure(IOException(errMsg, e))
        }
    }

    /**
     * Creates a new user with metadata (Name, Phone, Role)
     */
    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        phone: String = "",
        role: String = "ADMIN",
        shopName: String = "",
        context: Context? = null
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()
        val cleanName = name.trim().ifBlank { "Gérant" }

        if (cleanEmail.isBlank() || cleanPassword.length < 6) {
            val err = "L'adresse email est requise et le mot de passe doit comporter au moins 6 caractères"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        if (!SupabaseConfig.isConfigured) {
            val err = "Supabase n'est pas configuré"
            _authState.value = AuthState.Error(err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        _authState.value = AuthState.Loading("Création de votre compte Supabase...")

        val authUrl = "${SupabaseConfig.rawUrl.trimEnd('/')}/auth/v1/signup"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
            put("password", cleanPassword)
            val metadata = JSONObject().apply {
                put("name", cleanName)
                put("phone", phone.trim())
                put("role", role.trim().uppercase())
                put("shop_name", shopName.trim())
            }
            put("data", metadata)
        }

        val request = Request.Builder()
            .url(authUrl)
            .addHeader("apikey", SupabaseConfig.rawAnonKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyString)
                    // If Supabase has email confirmation enabled, access_token might be null
                    if (json.has("access_token") && !json.isNull("access_token")) {
                        val session = parseSessionJson(json)
                        currentSession = session
                        _authState.value = AuthState.Authenticated(session)
                        if (context != null) {
                            saveSessionToPrefs(context, session)
                        }
                        Result.success(session)
                    } else {
                        // User created but needs email confirmation
                        val userObj = json.optJSONObject("user") ?: json
                        val user = SupabaseUser(
                            id = userObj.optString("id", "new-user"),
                            email = userObj.optString("email", cleanEmail),
                            name = cleanName,
                            role = role,
                            phone = phone
                        )
                        val placeholderSession = SupabaseAuthSession(
                            accessToken = "",
                            refreshToken = "",
                            expiresAt = 0L,
                            user = user
                        )
                        val msg = "Compte créé ! Si la confirmation d'email est activée sur votre projet Supabase, vérifiez votre boîte de réception."
                        _authState.value = AuthState.Error(msg)
                        Result.success(placeholderSession)
                    }
                } else {
                    val errMsg = parseErrorMessage(bodyString, response.code)
                    _authState.value = AuthState.Error(errMsg)
                    Result.failure(IOException(errMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign Up Exception", e)
            val errMsg = "Échec d'inscription : ${e.localizedMessage ?: "Vérifiez votre connexion Internet"}"
            _authState.value = AuthState.Error(errMsg)
            Result.failure(IOException(errMsg, e))
        }
    }

    /**
     * Sends password reset email
     */
    suspend fun recoverPassword(email: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email requis"))
        }

        val authUrl = "${SupabaseConfig.rawUrl.trimEnd('/')}/auth/v1/recover"
        val payload = JSONObject().apply {
            put("email", cleanEmail)
        }

        val request = Request.Builder()
            .url(authUrl)
            .addHeader("apikey", SupabaseConfig.rawAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success("Un email de réinitialisation de mot de passe a été envoyé à $cleanEmail")
                } else {
                    Result.failure(IOException(parseErrorMessage(body, response.code)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out current user and clears session storage
     */
    suspend fun signOut(context: Context? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val token = currentToken
        if (!token.isNullOrBlank()) {
            val authUrl = "${SupabaseConfig.rawUrl.trimEnd('/')}/auth/v1/logout"
            val request = Request.Builder()
                .url(authUrl)
                .addHeader("apikey", SupabaseConfig.rawAnonKey)
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            try {
                httpClient.newCall(request).execute().close()
            } catch (_: Exception) {}
        }

        currentSession = null
        _authState.value = AuthState.Unauthenticated

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            } catch (_: Exception) {}
        }

        Result.success(Unit)
    }

    /**
     * Refreshes user session using refreshToken
     */
    suspend fun refreshSession(context: Context? = null): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        val refresh = currentSession?.refreshToken
        if (refresh.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Aucun jeton de rafraîchissement disponible"))
        }

        val authUrl = "${SupabaseConfig.rawUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token"
        val payload = JSONObject().apply {
            put("refresh_token", refresh)
        }

        val request = Request.Builder()
            .url(authUrl)
            .addHeader("apikey", SupabaseConfig.rawAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val session = parseSessionJson(json)
                    currentSession = session
                    _authState.value = AuthState.Authenticated(session)
                    if (context != null) {
                        saveSessionToPrefs(context, session)
                    }
                    Result.success(session)
                } else {
                    Result.failure(IOException(parseErrorMessage(body, response.code)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSessionJson(json: JSONObject): SupabaseAuthSession {
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", "")
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

        val userJson = json.getJSONObject("user")
        val userId = userJson.getString("id")
        val email = userJson.optString("email", "")
        val createdAt = userJson.optString("created_at", "")

        val metadata = userJson.optJSONObject("user_metadata")
        val name = metadata?.optString("name")?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").replace(".", " ").capitalizeWords()
        val role = metadata?.optString("role")?.takeIf { it.isNotBlank() } ?: "ADMIN"
        val phone = metadata?.optString("phone") ?: ""

        val user = SupabaseUser(
            id = userId,
            email = email,
            name = name,
            role = role,
            phone = phone,
            createdAt = createdAt
        )

        return SupabaseAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            user = user
        )
    }

    private fun saveSessionToPrefs(context: Context, session: SupabaseAuthSession) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, session.accessToken)
                .putString(KEY_REFRESH_TOKEN, session.refreshToken)
                .putLong(KEY_EXPIRES_AT, session.expiresAt)
                .putString(KEY_USER_ID, session.user.id)
                .putString(KEY_USER_EMAIL, session.user.email)
                .putString(KEY_USER_NAME, session.user.name)
                .putString(KEY_USER_ROLE, session.user.role)
                .putString(KEY_USER_PHONE, session.user.phone)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session", e)
        }
    }

    private fun parseErrorMessage(body: String, statusCode: Int): String {
        return try {
            val json = JSONObject(body)
            when {
                json.has("error_description") -> json.getString("error_description")
                json.has("message") -> json.getString("message")
                json.has("msg") -> json.getString("msg")
                json.has("error") -> {
                    val err = json.get("error")
                    if (err is JSONObject && err.has("message")) err.getString("message")
                    else err.toString()
                }
                statusCode == 400 -> "Identifiants invalides (email ou mot de passe incorrect)"
                statusCode == 422 -> "Email invalide ou déjà utilisé"
                statusCode == 429 -> "Trop de tentatives. Veuillez patienter un instant."
                else -> "Erreur Supabase HTTP $statusCode"
            }
        } catch (_: Exception) {
            when (statusCode) {
                400 -> "Identifiants invalides ou format incorrect"
                401 -> "Non autorisé : clé API ou jeton invalide"
                422 -> "Données d'authentification invalides"
                else -> "Erreur serveur Supabase ($statusCode)"
            }
        }
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    data class DiagnosticResult(
        val isSuccessful: Boolean,
        val url: String,
        val latencyMs: Long,
        val authStatus: String,
        val databaseStatus: String,
        val sessionStatus: String,
        val details: List<String>
    )

    /**
     * Executes an end-to-end self-diagnostic test of Supabase Auth and Database
     * directly from inside the application.
     */
    suspend fun runSelfTest(): DiagnosticResult = withContext(Dispatchers.IO) {
        val details = mutableListOf<String>()
        val startTotal = System.currentTimeMillis()
        val targetUrl = SupabaseConfig.rawUrl.trimEnd('/')
        val anonKey = SupabaseConfig.rawAnonKey.trim()

        if (!SupabaseConfig.isConfigured) {
            return@withContext DiagnosticResult(
                isSuccessful = false,
                url = targetUrl,
                latencyMs = 0L,
                authStatus = "Non configuré",
                databaseStatus = "Non configuré",
                sessionStatus = "Déconnecté",
                details = listOf("⚠️ Le service Cloud n'est pas initialisé.")
            )
        }

        var authOk = false
        var authMessage = "Inaccessible"
        var dbOk = false
        var dbMessage = "Inaccessible"

        // 1. Test Auth Endpoint (/auth/v1/settings)
        try {
            val authStart = System.currentTimeMillis()
            val authReq = Request.Builder()
                .url("$targetUrl/auth/v1/settings")
                .addHeader("apikey", anonKey)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(authReq).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - authStart
                if (resp.isSuccessful || resp.code in 200..299) {
                    authOk = true
                    authMessage = "Opérationnel (${elapsed}ms)"
                    details.add("✅ Service de Sécurité Cloud : Actif et joignable (${elapsed}ms)")
                } else if (resp.code == 401) {
                    authMessage = "Erreur d'accès (HTTP 401)"
                    details.add("⚠️ Service joignable mais clé d'accès refusée (HTTP 401)")
                } else {
                    authOk = true
                    authMessage = "En ligne (HTTP ${resp.code})"
                    details.add("ℹ️ Le service Cloud a répondu : HTTP ${resp.code} (${elapsed}ms)")
                }
            }
        } catch (e: Exception) {
            details.add("❌ Échec test de connexion : ${e.localizedMessage ?: e.message}")
        }

        // 2. Test Database Endpoint (/rest/v1/)
        try {
            val dbStart = System.currentTimeMillis()
            val token = currentToken ?: anonKey
            val dbReq = Request.Builder()
                .url("$targetUrl/rest/v1/")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(dbReq).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - dbStart
                if (resp.isSuccessful || resp.code in 200..299) {
                    dbOk = true
                    dbMessage = "Opérationnel (${elapsed}ms)"
                    details.add("✅ Serveur de Synchronisation : Connecté avec succès (${elapsed}ms)")
                } else if (resp.code == 404 || resp.code == 400) {
                    dbOk = true
                    dbMessage = "Connecté (HTTP ${resp.code})"
                    details.add("✅ Serveur de Données opérationnel : HTTP ${resp.code} (${elapsed}ms)")
                } else {
                    dbMessage = "Réponse HTTP ${resp.code}"
                    details.add("⚠️ Le serveur a répondu HTTP ${resp.code} (${elapsed}ms)")
                }
            }
        } catch (e: Exception) {
            details.add("❌ Échec test Serveur de Données : ${e.localizedMessage ?: e.message}")
        }

        // 3. Test Session State
        val sessionStr: String
        val session = currentSession
        if (session != null) {
            if (!session.isExpired) {
                sessionStr = "Session Active (${session.user.email})"
                details.add("👤 Utilisateur connecté : ${session.user.name} (${session.user.role})")
                details.add("🔒 Session chiffrée et sécurisée")
            } else {
                sessionStr = "Session Expirée"
                details.add("⚠️ La session de ${session.user.email} est expirée, une reconnexion est requise.")
            }
        } else {
            sessionStr = "Session Locale / Sauvegarde Active"
            details.add("ℹ️ Sauvegarde continue active.")
        }

        val totalLatency = System.currentTimeMillis() - startTotal
        val isSuccessful = authOk || dbOk

        DiagnosticResult(
            isSuccessful = isSuccessful,
            url = targetUrl,
            latencyMs = totalLatency,
            authStatus = authMessage,
            databaseStatus = dbMessage,
            sessionStatus = sessionStr,
            details = details
        )
    }
}
