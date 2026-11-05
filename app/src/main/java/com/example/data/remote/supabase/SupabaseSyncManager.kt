package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
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
            statusMessage = "Synchronisation en cours avec Supabase Cloud..."
        )

        try {
            var count = 0

            // 1. Sync Shop Profile
            val shop = repository.getShopProfileOnce()
            if (shop != null) {
                apiClient.pushShopProfile(shop)
                count++
            } else {
                val remoteShop = apiClient.fetchShopProfile().getOrNull()
                if (remoteShop != null) {
                    repository.saveShopProfile(remoteShop)
                }
            }

            // 2. Sync Products
            val localProducts = repository.getAllProductsOnce()
            if (localProducts.isNotEmpty()) {
                val pushProdRes = apiClient.pushProducts(localProducts)
                count += pushProdRes.getOrDefault(0)
            }

            // Pull any products that may have been created in Supabase
            val remoteProdsRes = apiClient.fetchProducts()
            remoteProdsRes.getOrNull()?.let { remoteList ->
                if (remoteList.isNotEmpty()) {
                    repository.insertProducts(remoteList)
                }
            }

            // 3. Sync Customers
            val localCustomers = repository.getAllCustomersOnce()
            if (localCustomers.isNotEmpty()) {
                val pushCustRes = apiClient.pushCustomers(localCustomers)
                count += pushCustRes.getOrDefault(0)
            }

            // Pull any customers created on Supabase
            val remoteCustRes = apiClient.fetchCustomers()
            remoteCustRes.getOrNull()?.let { remoteList ->
                if (remoteList.isNotEmpty()) {
                    repository.getDao().insertCustomers(remoteList)
                }
            }

            // 4. Sync Sales & Sale Items
            val localSales = repository.getAllSalesOnce()
            val localSaleItems = repository.getAllSaleItemsOnce()
            if (localSales.isNotEmpty()) {
                val pushSalesRes = apiClient.pushSales(localSales, localSaleItems)
                count += pushSalesRes.getOrDefault(0)
            }

            // 5. Sync Suppliers
            val localSuppliers = repository.getDao().getAllSuppliersOnce()
            if (localSuppliers.isNotEmpty()) {
                val pushSuppRes = apiClient.pushSuppliers(localSuppliers)
                count += pushSuppRes.getOrDefault(0)
            }

            // 6. Sync Purchases
            val localPurchases = repository.getDao().getAllPurchasesOnce()
            if (localPurchases.isNotEmpty()) {
                val pushPurchRes = apiClient.pushPurchases(localPurchases)
                count += pushPurchRes.getOrDefault(0)
            }

            // 7. Sync Cash Movements
            val localMovements = repository.getDao().getAllCashMovementsOnce()
            if (localMovements.isNotEmpty()) {
                val pushMoveRes = apiClient.pushCashMovements(localMovements)
                count += pushMoveRes.getOrDefault(0)
            }

            // 8. Sync Cash Sessions
            val localSessions = repository.getDao().getAllCashSessionsOnce()
            if (localSessions.isNotEmpty()) {
                val pushSessRes = apiClient.pushCashSessions(localSessions)
                count += pushSessRes.getOrDefault(0)
            }

            // 9. Sync Notifications
            val localNotifs = repository.getAllNotificationsOnce()
            if (localNotifs.isNotEmpty()) {
                val pushNotifRes = apiClient.pushNotifications(localNotifs)
                count += pushNotifRes.getOrDefault(0)
            }

            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)

            val successMsg = "Synchronisation terminée avec succès ($count enregistrements sauvegardés sur Supabase)"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = count,
                statusMessage = successMsg
            )

            // Notify via Android status bar & in-app
            com.example.util.NotificationHelper.sendCloudSyncSuccess(context, successMsg)
            repository.insertNotification(
                com.example.data.local.entity.NotificationEntity(
                    title = "Cloud Supabase à jour",
                    message = "$count enregistrements synchronisés (Produits, Ventes, Caisse, Fournisseurs).",
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
}
