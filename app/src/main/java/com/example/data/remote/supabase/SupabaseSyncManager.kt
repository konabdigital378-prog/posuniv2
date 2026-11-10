package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.repository.KonabRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SupabaseSyncState(
    val isConfigured: Boolean = false,
    val isSyncing: Boolean = false,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncTimestamp: Long? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val syncedRecordsCount: Int = 0
)

class SupabaseSyncManager(
    private val context: Context,
    private val apiClient: SupabaseApiClient = SupabaseApiClient()
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("konab_supabase_prefs", Context.MODE_PRIVATE)

    init {
        SupabaseConfig.init(context)
    }

    fun isAutoSyncEnabled(): Boolean {
        return prefs.getBoolean("auto_sync_enabled", true)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync_enabled", enabled).apply()
        _syncState.value = _syncState.value.copy(
            isAutoSyncEnabled = enabled,
            statusMessage = if (enabled) "Synchronisation automatique activée" else "Synchronisation automatique suspendue"
        )
    }

    private val _syncState = MutableStateFlow(
        SupabaseSyncState(
            isConfigured = SupabaseConfig.isConfigured,
            isAutoSyncEnabled = prefs.getBoolean("auto_sync_enabled", true),
            lastSyncTimestamp = getSavedLastSyncTimestamp(),
            statusMessage = if (SupabaseConfig.isConfigured) {
                if (prefs.getBoolean("auto_sync_enabled", true)) "Synchronisation auto en direct active" else "Prêt pour la synchronisation"
            } else "Supabase non configuré"
        )
    )
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    private fun getSavedLastSyncTimestamp(): Long? {
        val ts = prefs.getLong("last_sync_timestamp", 0L)
        return if (ts > 0L) ts else null
    }

    private fun saveLastSyncTimestamp(ts: Long) {
        prefs.edit().putLong("last_sync_timestamp", ts).apply()
    }

    fun saveCredentials(url: String, key: String) {
        SupabaseConfig.saveCustomConfig(context, url, key)
        _syncState.value = _syncState.value.copy(
            isConfigured = SupabaseConfig.isConfigured,
            statusMessage = if (SupabaseConfig.isConfigured) "Identifiants Supabase enregistrés" else "Configuration incomplète"
        )
    }

    fun clearCredentials() {
        SupabaseConfig.clearCustomConfig(context)
        _syncState.value = _syncState.value.copy(
            isConfigured = false,
            statusMessage = "Configuration Supabase effacée"
        )
    }

    suspend fun testConnection(customUrl: String? = null, customKey: String? = null): Result<String> {
        val testingUrl = customUrl?.takeIf { it.isNotBlank() } ?: SupabaseConfig.rawUrl
        val testingKey = customKey?.takeIf { it.isNotBlank() } ?: SupabaseConfig.rawAnonKey

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Test de connexion vers Supabase..."
        )
        val result = apiClient.testConnection(testingUrl, testingKey)
        val isSuccess = result.isSuccess
        val message = result.getOrNull() ?: (result.exceptionOrNull()?.message ?: "Échec de connexion")

        if (isSuccess && !customUrl.isNullOrBlank() && !customKey.isNullOrBlank()) {
            saveCredentials(customUrl, customKey)
        }

        _syncState.value = _syncState.value.copy(
            isConfigured = SupabaseConfig.isConfigured,
            isSyncing = false,
            isError = !isSuccess,
            statusMessage = message
        )
        return result
    }

    suspend fun syncAll(repository: KonabRepository): Result<Int> {
        return syncFullStore(repository)
    }

    /**
     * Synchronise exclusivement les informations de configuration de l'entreprise (ShopProfile)
     * avec Supabase Cloud. Toutes les autres données (ventes, stocks, clients, caisse)
     * demeurent strictement locales et privées sur l'appareil.
     */
    fun getOrCreateCloudShopId(): Long {
        var id = prefs.getLong("cloud_shop_id", 0L)
        if (id <= 1L) {
            val now = System.currentTimeMillis()
            val rnd = (100L..999L).random()
            id = now * 1000L + rnd
            prefs.edit().putLong("cloud_shop_id", id).apply()
        }
        return id
    }

    fun resetCloudShopId(): Long {
        val now = System.currentTimeMillis()
        val rnd = (100L..999L).random()
        val id = now * 1000L + rnd
        prefs.edit().putLong("cloud_shop_id", id).apply()
        return id
    }

    /**
     * Pousse immédiatement et directement un profil de boutique vers Supabase.
     * Chaque personne/appareil dispose d'un identifiant unique (cloudShopId)
     * pour que chaque boutique créée soit visible dans la base de données sans écraser les autres.
     */
    suspend fun pushShopProfileDirectly(profile: ShopProfileEntity, isNewShop: Boolean = false): Result<Unit> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Supabase non configuré"))
        }
        val cloudId = if (isNewShop) resetCloudShopId() else getOrCreateCloudShopId()
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Envoi de la boutique \"${profile.name}\" vers Supabase..."
        )
        val pushRes = apiClient.pushShopProfile(profile, cloudId)
        val now = System.currentTimeMillis()
        if (pushRes.isSuccess) {
            saveLastSyncTimestamp(now)
            val msg = "Boutique \"${profile.name}\" synchronisée avec succès dans Supabase (ID: $cloudId)"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = 1,
                statusMessage = msg
            )
            com.example.util.NotificationHelper.sendCloudSyncSuccess(context, msg)
        } else {
            val err = pushRes.exceptionOrNull()?.localizedMessage ?: "Échec de l'envoi vers Supabase"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = true,
                statusMessage = err
            )
        }
        return pushRes
    }

    suspend fun syncCompanyConfig(repository: KonabRepository): Result<Int> {
        if (!SupabaseConfig.isConfigured) {
            val msg = "Supabase non configuré. Renseignez SUPABASE_URL et SUPABASE_ANON_KEY dans les Secrets."
            _syncState.value = _syncState.value.copy(
                isConfigured = false,
                isError = true,
                statusMessage = msg
            )
            return Result.failure(IllegalStateException(msg))
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isError = false,
            statusMessage = "Synchronisation automatique de la configuration d'entreprise..."
        )

        try {
            var count = 0

            // Synchronisation EXCLUSIVE du profil & configuration de l'entreprise
            val shop = repository.getShopProfileOnce()
            if (shop != null && shop.isConfigured && shop.name.isNotBlank()) {
                val cloudId = getOrCreateCloudShopId()
                val pushRes = apiClient.pushShopProfile(shop, cloudId)
                if (pushRes.isSuccess) {
                    count++
                } else {
                    val ex = pushRes.exceptionOrNull()
                    if (ex != null) throw ex
                }
            } else {
                // Si non configuré en local ou nom vide, récupération de la configuration distante sur Supabase
                val remoteShop = apiClient.fetchShopProfile().getOrNull()
                if (remoteShop != null && remoteShop.name.isNotBlank()) {
                    repository.saveShopProfile(remoteShop)
                    count++
                }
            }

            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)

            val companyName = shop?.name ?: "Boutique"
            val successMsg = "Configuration d'entreprise synchronisée avec succès ($companyName sur Supabase)"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = count,
                statusMessage = successMsg
            )

            // Notification de confirmation discrète
            com.example.util.NotificationHelper.sendCloudSyncSuccess(context, successMsg)
            repository.insertNotification(
                com.example.data.local.entity.NotificationEntity(
                    title = "Configuration Entreprise Synchronisée",
                    message = "Informations de l'entreprise synchronisées avec Supabase ($companyName). Les ventes et produits restent sécurisés localement.",
                    type = "SUPABASE_SYNC",
                    priority = "NORMAL",
                    actionRoute = "settings"
                )
            )

            return Result.success(count)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Erreur de synchronisation avec Supabase"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = true,
                statusMessage = errMsg
            )
            return Result.failure(e)
        }
    }

    /**
     * Synchronise l'ensemble des données du magasin (Configuration, Produits, Clients, Ventes, Caisse)
     * vers Supabase Cloud à la demande de l'utilisateur.
     */
    suspend fun syncFullStore(repository: KonabRepository): Result<Int> {
        if (!SupabaseConfig.isConfigured) {
            val msg = "Supabase non configuré."
            _syncState.value = _syncState.value.copy(isConfigured = false, isError = true, statusMessage = msg)
            return Result.failure(IllegalStateException(msg))
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isError = false,
            statusMessage = "Synchronisation complète de toutes les tables vers Supabase..."
        )

        try {
            var count = 0

            // 1. Profil entreprise
            val shop = repository.getShopProfileOnce()
            if (shop != null) {
                apiClient.pushShopProfile(shop)
                count++
            }

            // 2. Produits
            val prods = repository.getAllProductsOnce()
            if (prods.isNotEmpty()) {
                val res = apiClient.pushProducts(prods)
                count += res.getOrDefault(0)
            }

            // 3. Clients
            val custs = repository.getAllCustomersOnce()
            if (custs.isNotEmpty()) {
                val res = apiClient.pushCustomers(custs)
                count += res.getOrDefault(0)
            }

            // 4. Ventes & Lignes de vente
            val sales = repository.getAllSalesOnce()
            val saleItems = repository.getAllSaleItemsOnce()
            if (sales.isNotEmpty()) {
                val res = apiClient.pushSales(sales, saleItems)
                count += res.getOrDefault(0)
            }

            // 5. Fournisseurs
            val supps = repository.getDao().getAllSuppliersOnce()
            if (supps.isNotEmpty()) {
                val res = apiClient.pushSuppliers(supps)
                count += res.getOrDefault(0)
            }

            // 6. Caisse
            val movs = repository.getDao().getAllCashMovementsOnce()
            if (movs.isNotEmpty()) {
                val res = apiClient.pushCashMovements(movs)
                count += res.getOrDefault(0)
            }

            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)

            val successMsg = "Synchronisation complète réussie ($count données sur Supabase Cloud)"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = count,
                statusMessage = successMsg
            )

            com.example.util.NotificationHelper.sendCloudSyncSuccess(context, successMsg)
            return Result.success(count)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Erreur de synchronisation complète"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = true,
                statusMessage = errMsg
            )
            return Result.failure(e)
        }
    }

    // --- AGENT DEPLOYMENTS CLOUD SYNC ---

    suspend fun syncAgentDeployments(): Result<Int> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Supabase non configuré"))
        }
        val all = AgentDeploymentStorage.getAllDeployments(context)
        val pending = all.filter { !it.isSynced }
        if (pending.isEmpty()) {
            return Result.success(0)
        }
        val res = apiClient.pushAgentDeployments(pending)
        if (res.isSuccess) {
            pending.forEach { d ->
                AgentDeploymentStorage.markAsSynced(context, d.id)
            }
        }
        return res
    }

    suspend fun refreshAgentsFromCloud(): Result<List<com.example.ui.screens.AgentUser>> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Supabase non configuré"))
        }
        val res = apiClient.fetchCloudAgents()
        if (res.isSuccess) {
            val list = res.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                AgentDeploymentStorage.saveCloudAgents(context, list)
            }
        }
        return res
    }
}
