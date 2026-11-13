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
        val configRes = syncCompanyConfig(repository)
        try {
            syncProductsOnly(repository)
        } catch (_: Exception) {}
        try {
            syncAgentDeployments()
        } catch (_: Exception) {}
        return configRes
    }

    suspend fun syncProductsOnly(repository: KonabRepository): Result<Int> {
        if (!SupabaseConfig.isConfigured) return Result.success(0)
        val products = repository.getAllProductsOnce()
        if (products.isEmpty()) return Result.success(0)
        return apiClient.pushProducts(products)
    }

    suspend fun deleteProductFromCloud(productId: Long): Result<Unit> {
        if (!SupabaseConfig.isConfigured) return Result.success(Unit)
        return apiClient.deleteProduct(productId)
    }

    /**
     * Vérifie qu'un nom de boutique est unique (localement + sur Supabase Cloud).
     */
    suspend fun verifyShopNameAvailable(
        shopName: String,
        excludeShopId: Long? = null
    ): Result<Unit> {
        val cleanName = shopName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Le nom de la boutique est obligatoire."))
        }
        if (excludeShopId == null && AgentDeploymentStorage.hasLocalDeploymentForShopName(context, cleanName)) {
            return Result.failure(
                IllegalStateException("La boutique « $cleanName » existe déjà. Chaque boutique doit avoir un nom unique.")
            )
        }
        if (SupabaseConfig.isConfigured) {
            val remoteCheck = apiClient.checkShopNameExists(cleanName, excludeShopId)
            if (remoteCheck.isSuccess && remoteCheck.getOrDefault(false)) {
                return Result.failure(
                    IllegalStateException("Une boutique nommée « $cleanName » existe déjà dans la base de données. Choisissez un nom unique.")
                )
            }
        }
        return Result.success(Unit)
    }

    suspend fun fetchCloudShopsForAgent(agentId: String): Result<List<ShopProfileEntity>> {
        return apiClient.fetchShopsForAgent(agentId)
    }

    /**
     * Synchronise les informations de configuration de l'entreprise (ShopProfile)
     * avec Supabase Cloud.
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
     * Exige d'abord un compte Agent Déployeur créé et vérifie l'unicité du nom de la boutique.
     */
    suspend fun pushShopProfileDirectly(profile: ShopProfileEntity, isNewShop: Boolean = false): Result<Unit> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Supabase non configuré"))
        }
        val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
        val effectiveDeployerId = profile.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: activeAgent?.id?.trim()?.uppercase()
        if (effectiveDeployerId.isNullOrBlank()) {
            val msg = "Veuillez d'abord créer ou connecter un compte Agent Déployeur avant de créer une boutique."
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = msg)
            return Result.failure(IllegalStateException(msg))
        }

        // 1. S'assurer d'abord que le compte agent existe dans Supabase (`agents`)
        if (activeAgent != null && activeAgent.id.trim().equals(effectiveDeployerId, ignoreCase = true)) {
            try {
                apiClient.pushCloudAgent(activeAgent)
            } catch (_: Exception) {}
        }

        val cloudId = if (isNewShop) resetCloudShopId() else getOrCreateCloudShopId()
        val profileWithAgent = profile.copy(deployerId = effectiveDeployerId)

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Envoi de la boutique \"${profileWithAgent.name}\" (Agent $effectiveDeployerId)..."
        )
        val pushRes = apiClient.pushShopProfile(
            profile = profileWithAgent,
            cloudShopId = cloudId,
            isNewShop = isNewShop
        )
        val now = System.currentTimeMillis()
        if (pushRes.isSuccess) {
            AgentDeploymentStorage.markCreatedShopOnDevice(context, profileWithAgent.name, effectiveDeployerId)
            saveLastSyncTimestamp(now)
            val msg = "Boutique \"${profileWithAgent.name}\" synchronisée (Agent: $effectiveDeployerId)"
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
            statusMessage = "Synchronisation de la configuration boutique..."
        )

        try {
            var count = 0

            val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
            val shop = repository.getShopProfileOnce()
            if (shop != null && shop.isConfigured && shop.name.isNotBlank()) {
                val deployerId = shop.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                    ?: activeAgent?.id?.trim()?.uppercase()
                if (!deployerId.isNullOrBlank()) {
                    val cloudId = getOrCreateCloudShopId()
                    val shopWithDeployer = shop.copy(deployerId = deployerId)
                    val pushRes = apiClient.pushShopProfile(shopWithDeployer, cloudId, isNewShop = false)
                    if (pushRes.isSuccess) {
                        count++
                    } else {
                        val ex = pushRes.exceptionOrNull()
                        if (ex != null) throw ex
                    }
                }
            } else if (activeAgent != null) {
                // Récupérer uniquement la boutique liée à l'agent connecté (spécificité par agent)
                val remoteShop = apiClient.fetchShopsForAgent(activeAgent.id).getOrNull()?.firstOrNull()
                if (remoteShop != null && remoteShop.name.isNotBlank()) {
                    repository.saveShopProfile(remoteShop)
                    count++
                }
            }

            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)

            val companyName = shop?.name ?: "Boutique"
            val successMsg = "Configuration boutique synchronisée ($companyName)"
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
     * Synchronise toutes les données persistantes de la boutique (configuration, produits, clients, ventes, fournisseurs, caisse)
     * vers Supabase Cloud de façon permanente.
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
            statusMessage = "Synchronisation complète vers Supabase..."
        )

        try {
            var count = 0

            // 1. Profil entreprise (uniquement si un agent déployeur est associé)
            val shop = repository.getShopProfileOnce()
            val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
            val deployerId = shop?.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                ?: activeAgent?.id?.trim()?.uppercase()
            if (shop != null && shop.isConfigured && !deployerId.isNullOrBlank()) {
                val cloudId = getOrCreateCloudShopId()
                val res = apiClient.pushShopProfile(shop.copy(deployerId = deployerId), cloudId, isNewShop = false)
                if (res.isSuccess) count++
            }

            // 2. Déploiements d'agent
            val depRes = syncAgentDeployments()
            count += depRes.getOrDefault(0)

            // 3. Produits & Stock (stockage permanent local + Cloud)
            val prods = repository.getAllProductsOnce()
            if (prods.isNotEmpty()) {
                val res = apiClient.pushProducts(prods)
                count += res.getOrDefault(0)
            }

            // 4. Clients
            val custs = repository.getAllCustomersOnce()
            if (custs.isNotEmpty()) {
                val res = apiClient.pushCustomers(custs)
                count += res.getOrDefault(0)
            }

            // 5. Ventes & Lignes de vente
            val sales = repository.getAllSalesOnce()
            val saleItems = repository.getAllSaleItemsOnce()
            if (sales.isNotEmpty()) {
                val res = apiClient.pushSales(sales, saleItems)
                count += res.getOrDefault(0)
            }

            // 6. Fournisseurs
            val supps = repository.getDao().getAllSuppliersOnce()
            if (supps.isNotEmpty()) {
                val res = apiClient.pushSuppliers(supps)
                count += res.getOrDefault(0)
            }

            // 7. Caisse
            val movs = repository.getDao().getAllCashMovementsOnce()
            if (movs.isNotEmpty()) {
                val res = apiClient.pushCashMovements(movs)
                count += res.getOrDefault(0)
            }

            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)

            val successMsg = "Synchronisation complète réussie ($count éléments sauvegardés dans Supabase)"
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

    suspend fun refreshDeploymentsForAgent(agentId: String): Result<List<AgentDeploymentRecord>> {
        val cleanId = agentId.trim().uppercase()
        if (SupabaseConfig.isConfigured) {
            val res = apiClient.fetchAgentDeployments(cleanId)
            if (res.isSuccess) {
                val list = res.getOrDefault(emptyList())
                AgentDeploymentStorage.saveCloudDeployments(context, list)
                return Result.success(list)
            }
        }
        return Result.success(AgentDeploymentStorage.getDeploymentsForAgent(context, cleanId))
    }

    suspend fun registerNewAgentWithAutoMatricule(
        name: String,
        phone: String,
        zone: String,
        pin: String,
        photoUri: String
    ): Result<com.example.ui.screens.AgentUser> {
        val cloudAgents = if (SupabaseConfig.isConfigured) {
            try {
                val res = apiClient.fetchCloudAgents()
                if (res.isSuccess) {
                    val list = res.getOrDefault(emptyList())
                    if (list.isNotEmpty()) {
                        AgentDeploymentStorage.saveCloudAgents(context, list)
                    }
                    list
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val assignedMatricule = AgentDeploymentStorage.generateUniqueAgentMatricule(context, cloudAgents)
        val cleanPhoto = photoUri.trim()
        if (cleanPhoto.isNotBlank()) {
            AgentDeploymentStorage.saveAgentPhotoUri(context, assignedMatricule, cleanPhoto)
        }
        val newAgent = com.example.ui.screens.AgentUser(
            id = assignedMatricule,
            name = name.trim(),
            phone = phone.trim(),
            zone = zone.trim().ifBlank { "National" },
            pin = pin.trim(),
            role = "Agent Déployeur",
            photoUri = cleanPhoto
        )

        AgentDeploymentStorage.registerLocalAgent(context, newAgent)
        AgentDeploymentStorage.saveAgentSession(context, newAgent)
        if (SupabaseConfig.isConfigured) {
            try {
                apiClient.registerCloudAgent(newAgent)
            } catch (_: Exception) {}
        }
        return Result.success(newAgent)
    }

    suspend fun registerAgentOnline(agent: com.example.ui.screens.AgentUser): Result<com.example.ui.screens.AgentUser> {
        // Save locally first for instant offline readiness
        AgentDeploymentStorage.registerLocalAgent(context, agent)
        AgentDeploymentStorage.saveAgentSession(context, agent)
        if (SupabaseConfig.isConfigured) {
            val cloudRes = apiClient.registerCloudAgent(agent)
            if (cloudRes.isSuccess) {
                return Result.success(agent)
            }
        }
        return Result.success(agent)
    }

    suspend fun loginAgentOnline(idOrPhone: String, pin: String): Result<com.example.ui.screens.AgentUser?> {
        val cleanInput = idOrPhone.trim().uppercase()
        val cleanPin = pin.trim()

        // 1. First check remote Supabase for cross-device authenticity
        if (SupabaseConfig.isConfigured) {
            val cloudRes = apiClient.authenticateCloudAgent(cleanInput, cleanPin)
            if (cloudRes.isSuccess && cloudRes.getOrNull() != null) {
                val matchedAgent = cloudRes.getOrNull()!!
                AgentDeploymentStorage.saveAgentSession(context, matchedAgent)
                refreshDeploymentsForAgent(matchedAgent.id)
                return Result.success(matchedAgent)
            }
        }

        // 2. Offline fallback: Check local cached agents & presets
        val allKnownAgents = AgentDeploymentStorage.getCloudAgents(context).toMutableList()
        val preset = com.example.ui.screens.AgentDirectory.findAgent(cleanInput, cleanPin)
        if (preset != null) {
            AgentDeploymentStorage.saveAgentSession(context, preset)
            return Result.success(preset)
        }

        val cachedMatch = allKnownAgents.find {
            (it.id.equals(cleanInput, ignoreCase = true) || it.phone.replace(" ", "") == cleanInput.replace(" ", "")) &&
            it.pin == cleanPin
        }
        if (cachedMatch != null) {
            AgentDeploymentStorage.saveAgentSession(context, cachedMatch)
            return Result.success(cachedMatch)
        }

        return Result.success(null)
    }
}
