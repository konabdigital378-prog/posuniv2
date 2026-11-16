package com.example.data.remote.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.datastore.KonabDataStore
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.repository.KonabRepository
import com.example.ui.screens.AgentUser
import com.example.util.BiometricSecurityHelper
import com.example.util.CouturePhotoHelper
import com.example.util.LicenseHelper
import com.example.util.NotificationHelper
import com.example.util.ShopLogoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class SupabaseSyncState(
    val isConfigured: Boolean = false,
    val isSyncing: Boolean = false,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncTimestamp: Long? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val syncedRecordsCount: Int = 0
)

data class ShopCreationResult(
    val cloudShopId: Long,
    val licenseKey: String,
    val isCloudSyncSuccess: Boolean,
    val cloudSyncMessage: String
)

class SupabaseSyncManager(
    private val context: Context,
    private val apiClient: SupabaseApiClient = SupabaseApiClient()
) {

    companion object {
        val shopSyncMutex = Mutex()
    }

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

    private fun isRestaurantBusiness(businessType: String?): Boolean {
        val bType = businessType.orEmpty()
        return RestaurantDirectoryHelper.isRestaurantShop(bType) ||
            com.example.util.BusinessTypePresets.getPreset(bType).sectorFeatures.hasTables
    }

    /**
     * Converts a local file:/// or content:// (or offline Base64 Data URI) shop logo into a
     * permanent public Supabase Storage HTTPS URL (`shop-logos` bucket, with `shop-ads` fallback),
     * seeds the persistent offline disk cache, or keeps a portable Base64 Data URI fallback.
     */
    suspend fun resolvePortableLogoUri(logoUri: String?, shopId: Long): String? {
        val clean = ShopLogoHelper.normalizeLogoUri(logoUri) ?: return null
        if (clean.startsWith("data:image/", ignoreCase = true)) {
            if (SupabaseConfig.isConfigured) {
                val bmp = ShopLogoHelper.decodeDataUriBitmap(clean)
                if (bmp != null) {
                    val tmpFile = File(context.cacheDir, "tmp_logo_promote_${System.currentTimeMillis()}.jpg")
                    try {
                        java.io.FileOutputStream(tmpFile).use { out ->
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, out)
                        }
                        val upRes = apiClient.uploadShopLogoMedia(tmpFile, shopId, "image/jpeg")
                        if (upRes.isSuccess) {
                            val url = upRes.getOrThrow()
                            if (url.isNotBlank()) {
                                ShopLogoHelper.seedCachedLogoFromFile(context, url, tmpFile)
                                return url
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        bmp.recycle()
                        tmpFile.delete()
                    }
                }
            }
            return clean
        }
        if (!ShopLogoHelper.isLocalUnpublishedLogoUri(clean)) {
            return clean
        }
        val file = ShopLogoHelper.getUploadableLogoFile(context, clean)
        if (file != null && SupabaseConfig.isConfigured) {
            val upRes = apiClient.uploadShopLogoMedia(file, shopId, "image/jpeg")
            if (upRes.isSuccess) {
                val url = upRes.getOrThrow()
                if (url.isNotBlank()) {
                    ShopLogoHelper.seedCachedLogoFromFile(context, url, file)
                    return url
                }
            }
        }
        return ShopLogoHelper.encodeLogoToPortableDataUri(context, clean) ?: clean
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

    suspend fun syncAll(repository: KonabRepository): Result<Int> = shopSyncMutex.withLock {
        val shop = repository.getShopProfileOnce()
        val fullRes = if (shop != null && shop.isConfigured && shop.name.isNotBlank()) {
            val pushed = syncFullStoreInternal(repository)
            try {
                pullAndMergeRemoteInternal(repository)
            } catch (_: Exception) {}
            pushed
        } else {
            syncCompanyConfigInternal(repository)
        }
        try {
            syncAgentDeploymentsInternal()
        } catch (_: Exception) {}
        fullRes
    }

    suspend fun syncProductsOnly(repository: KonabRepository): Result<Int> {
        if (!SupabaseConfig.isConfigured) return Result.success(0)
        val products = repository.getAllProductsOnce()
        if (products.isEmpty()) return Result.success(0)
        val shopId = getOrCreateCloudShopId()
        return apiClient.pushProducts(products, shopId = shopId)
    }

    suspend fun deleteProductFromCloud(productId: Long): Result<Unit> {
        if (!SupabaseConfig.isConfigured) return Result.success(Unit)
        val shopId = getOrCreateCloudShopId()
        return apiClient.deleteProduct(productId, shopId = shopId)
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

    private fun generateCloudShopId(): Long {
        val now = System.currentTimeMillis()
        val rnd = (100L..999L).random()
        return now * 1000L + rnd
    }

    /**
     * Synchronise les informations de configuration de l'entreprise (ShopProfile)
     * avec Supabase Cloud.
     */
    fun getOrCreateCloudShopId(): Long {
        var id = prefs.getLong("cloud_shop_id", 0L)
        if (id <= 1L) {
            id = generateCloudShopId()
            prefs.edit().putLong("cloud_shop_id", id).apply()
        }
        return id
    }

    fun resetCloudShopId(): Long {
        val id = generateCloudShopId()
        prefs.edit()
            .putLong("cloud_shop_id", id)
            .remove("cloud_shop_name")
            .apply()
        return id
    }

    fun prepareCloudShopIdForNewShop(shopName: String): Long {
        val norm = AgentDeploymentStorage.normalizeShopName(shopName)
        val existingId = prefs.getLong("cloud_shop_id", 0L)
        val existingName = prefs.getString("cloud_shop_name", null)?.let {
            AgentDeploymentStorage.normalizeShopName(it)
        }
        if (existingId > 1L && existingName != null && existingName == norm) {
            return existingId
        }
        val id = generateCloudShopId()
        prefs.edit()
            .putLong("cloud_shop_id", id)
            .putString("cloud_shop_name", norm)
            .apply()
        return id
    }

    fun saveCloudShopId(id: Long, shopName: String) {
        if (id <= 1L) return
        val norm = AgentDeploymentStorage.normalizeShopName(shopName)
        prefs.edit()
            .putLong("cloud_shop_id", id)
            .putString("cloud_shop_name", norm)
            .apply()
        MultiShopStorage.setActiveCloudShopId(context, id)
    }

    /**
     * Crée la boutique de manière atomique et sérialisée :
     * 1. Vérifie que la boutique n'est pas déjà configurée dans Room et que le nom est unique (local + Supabase).
     * 2. Pousse d'abord `shop_profile` vers Supabase (si configuré) pour bloquer tout doublon avant toute écriture locale.
     * 3. Enregistre ensuite Room + DataStore + 1 seul AgentDeploymentRecord, puis synchronise `agent_deployments`.
     */
    suspend fun createShopExclusive(
        repository: KonabRepository,
        shopName: String,
        slogan: String,
        phone: String,
        address: String,
        city: String,
        currency: String,
        logoIcon: String,
        logoColorHex: Long,
        businessType: String,
        adminName: String,
        adminPhone: String,
        adminPin: String,
        preloadDemoData: Boolean,
        subscriptionTier: String,
        activeAgent: AgentUser,
        notes: String = "Activité: $businessType",
        restaurantTeam: RestaurantTeamConfig? = null
    ): Result<ShopCreationResult> = shopSyncMutex.withLock {
        val cleanName = shopName.trim()
        if (cleanName.isBlank()) {
            return@withLock Result.failure(IllegalArgumentException("Le nom du commerce est obligatoire."))
        }

        // Guard 1 : Empêcher toute recréation si une boutique est déjà configurée sur cet appareil
        val existing = repository.getShopProfileOnce()
        if (existing != null && existing.isConfigured && existing.name.isNotBlank()) {
            return@withLock Result.failure(
                IllegalStateException("La boutique « ${existing.name} » est déjà créée et ne peut plus être recréée.")
            )
        }

        // Guard 2 : Unicité absolue du nom avant toute écriture (aucun déploiement local n'existe encore ici)
        val uniqueness = verifyShopNameAvailable(cleanName)
        if (uniqueness.isFailure) {
            val err = uniqueness.exceptionOrNull()
                ?: IllegalStateException("Une boutique portant ce nom existe déjà dans la base de données.")
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = err.message)
            return@withLock Result.failure(err)
        }

        val deployerId = activeAgent.id.trim().uppercase()
        val effectiveAdminPhone = adminPhone.trim().ifBlank { phone.trim() }
        val effectiveTeamCfg = if (isRestaurantBusiness(businessType)) {
            restaurantTeam ?: RestaurantTeamConfig()
        } else null
        val effectiveCashierPin = effectiveTeamCfg?.cashierPin?.trim()?.ifBlank { "0000" } ?: "0000"
        var cloudId = prepareCloudShopIdForNewShop(cleanName)
        val profile = ShopProfileEntity(
            id = 1,
            name = cleanName,
            slogan = slogan.trim(),
            phone = phone.trim(),
            address = address.trim(),
            city = city.trim(),
            currency = currency,
            businessType = businessType,
            adminPin = adminPin,
            cashierPin = effectiveCashierPin,
            subscriptionTier = subscriptionTier,
            isConfigured = true,
            receiptFooter = "Merci pour votre confiance ! À très bientôt !",
            deployerId = deployerId
        )

        var cloudSuccess = false
        var cloudMsg = "Boutique enregistrée localement (mode hors-ligne)"

        // Guard 3 : Pousser vers Supabase AVANT d'écrire Room/Deployment pour rejeter tout conflit distant sans trace locale
        if (SupabaseConfig.isConfigured) {
            _syncState.value = _syncState.value.copy(
                isSyncing = true,
                isError = false,
                statusMessage = "Création de la boutique « $cleanName » (Agent $deployerId)..."
            )
            try {
                apiClient.pushCloudAgent(activeAgent)
            } catch (_: Exception) {}

            val pushRes = apiClient.pushShopProfile(
                profile = profile,
                cloudShopId = cloudId,
                isNewShop = true,
                adminName = adminName.trim().ifBlank { "Gérant" },
                adminPhone = effectiveAdminPhone
            )
            if (pushRes.isSuccess) {
                cloudId = pushRes.getOrDefault(cloudId)
                saveCloudShopId(cloudId, cleanName)
                cloudSuccess = true
                cloudMsg = "Boutique « $cleanName » créée et liée à l'agent ${activeAgent.name} ($deployerId)."
            } else {
                val ex = pushRes.exceptionOrNull()
                // En cas de doublon / validation refusée par la base, abandonner proprement sans rien enregistrer localement
                if (ex is IllegalStateException || ex is IllegalArgumentException) {
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        isError = true,
                        statusMessage = ex.message
                    )
                    return@withLock Result.failure(ex)
                }
                // Erreur réseau / hors-ligne : poursuivre la création locale avec l'ID préparé
                cloudSuccess = false
                cloudMsg = ex?.message ?: "Enregistrée localement (sync Cloud en attente)"
            }
        }

        // Enregistrement local atomique (Room + DataStore)
        repository.initializeStore(
            name = cleanName,
            slogan = slogan.trim(),
            phone = phone.trim(),
            address = address.trim(),
            city = city.trim(),
            businessType = businessType,
            adminName = adminName.trim(),
            adminPhone = effectiveAdminPhone,
            adminPin = adminPin,
            preloadDemoData = preloadDemoData,
            subscriptionTier = subscriptionTier,
            deployerId = deployerId,
            currency = currency,
            logoIcon = logoIcon,
            logoColor = logoColorHex
        )
        if (effectiveCashierPin != "0000") {
            try {
                repository.getShopProfileOnce()?.let { currentProf ->
                    repository.saveShopProfile(currentProf.copy(cashierPin = effectiveCashierPin))
                }
            } catch (_: Exception) {}
        }
        BiometricSecurityHelper.setShopConfiguredCache(context, true)
        AgentDeploymentStorage.markCreatedShopOnDevice(context, cleanName, deployerId)

        // Générer une seule licence & enregistrer un unique AgentDeploymentRecord
        val durationCode = when (subscriptionTier) {
            "Premium" -> "VIE"
            "Standard", "Starter" -> "1M"
            else -> "1A"
        }
        val durationDays = when (subscriptionTier) {
            "Premium" -> 3650
            "Standard", "Starter" -> 30
            else -> 365
        }
        val keyData = LicenseHelper.generateKey(
            tier = subscriptionTier,
            durationCode = durationCode,
            beneficiary = cleanName,
            note = "Déployé par ${activeAgent.name} ($deployerId)"
        )
        LicenseHelper.saveGeneratedLicense(context, keyData)

        val deployment = AgentDeploymentRecord(
            id = "DEP-${System.currentTimeMillis()}",
            agentId = deployerId,
            agentName = activeAgent.name,
            agentPhone = activeAgent.phone,
            shopName = cleanName,
            shopPhone = phone.trim(),
            shopCity = city.trim(),
            tier = subscriptionTier,
            durationDays = durationDays,
            licenseKey = keyData.key,
            notes = notes,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )
        AgentDeploymentStorage.saveDeployment(context, deployment)

        // Enregistrer la session multiboutique localement pour accès instantané & bascule multiboutique
        try {
            val session = ConnectedShopSession(
                cloudShopId = cloudId,
                shopName = cleanName,
                businessType = businessType,
                city = city.trim(),
                address = address.trim(),
                phone = phone.trim(),
                slogan = slogan.trim(),
                currency = currency,
                receiptFooter = profile.receiptFooter,
                logoUri = profile.logoUri,
                adminName = adminName.trim().ifBlank { "Gérant" },
                adminPhone = effectiveAdminPhone,
                adminPin = adminPin,
                cashierPin = effectiveCashierPin,
                subscriptionTier = subscriptionTier,
                deployerId = deployerId,
                lastSyncedAt = System.currentTimeMillis()
            )
            MultiShopStorage.saveConnectedShop(context, session)
            repository.exportMultiShopSnapshot(cloudId)?.let { snap ->
                MultiShopStorage.saveLocalSnapshotCache(context, snap)
            }
        } catch (_: Exception) {}

        if (isRestaurantBusiness(businessType)) {
            val teamCfg = effectiveTeamCfg ?: RestaurantTeamConfig()
            val createdStaff = RestaurantStorage.ensureDefaultRestaurantTeam(
                context = context,
                shopId = cloudId,
                adminName = adminName.trim().ifBlank { "Gérant" },
                adminPhone = effectiveAdminPhone,
                adminPin = adminPin,
                kitchenName = teamCfg.kitchenName,
                kitchenPhone = teamCfg.kitchenPhone,
                kitchenPin = teamCfg.kitchenPin,
                waiterName = teamCfg.waiterName,
                waiterPhone = teamCfg.waiterPhone,
                waiterPin = teamCfg.waiterPin,
                cashierName = teamCfg.cashierName,
                cashierPin = teamCfg.cashierPin
            )
            createdStaff.firstOrNull { it.role == RestaurantStaffRole.MANAGER }?.let { mgr ->
                RestaurantStorage.setActiveStaff(context, cloudId, mgr.id)
            }
            val createdTables = RestaurantStorage.ensureDefaultTables(
                context = context,
                shopId = cloudId,
                count = teamCfg.initialTableCount
            )
            val prodsNow = repository.getAllProductsOnce()
            if (prodsNow.isNotEmpty() && RestaurantStorage.getMenuOfDayProductIds(context, cloudId).isEmpty()) {
                val ids = prodsNow.map { it.id }.toSet()
                RestaurantStorage.setMenuOfDayProductIds(context, cloudId, ids)
            }
            if (cloudSuccess) {
                try {
                    apiClient.pushRestaurantStaff(createdStaff, cloudId)
                    apiClient.pushRestaurantTables(createdTables, cloudId)
                    val menuIds = RestaurantStorage.getMenuOfDayProductIds(context, cloudId)
                    if (menuIds.isNotEmpty()) {
                        apiClient.pushMenuOfDay(cloudId, menuIds)
                    }
                } catch (_: Exception) {}
            }
        }

        if (CoutureDirectoryHelper.isCoutureShop(businessType)) {
            val defaultModels = CoutureStorage.ensureDefaultModelsForAtelier(
                context = context,
                shopId = cloudId,
                shopName = cleanName
            )
            if (cloudSuccess && defaultModels.isNotEmpty()) {
                try {
                    apiClient.pushCoutureModels(defaultModels, cloudId)
                } catch (_: Exception) {}
            }
        }

        if (cloudSuccess) {
            if (preloadDemoData) {
                try {
                    syncProductsOnly(repository)
                } catch (_: Exception) {}
            }
            try {
                syncAgentDeploymentsInternal()
            } catch (_: Exception) {}
            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = 1,
                statusMessage = cloudMsg
            )
            NotificationHelper.sendCloudSyncSuccess(context, cloudMsg)
        } else {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                statusMessage = cloudMsg
            )
        }

        Result.success(
            ShopCreationResult(
                cloudShopId = cloudId,
                licenseKey = keyData.key,
                isCloudSyncSuccess = cloudSuccess,
                cloudSyncMessage = cloudMsg
            )
        )
    }

    /**
     * Pousse immédiatement et directement un profil de boutique vers Supabase.
     * Exige d'abord un compte Agent Déployeur créé et vérifie l'unicité du nom de la boutique.
     */
    suspend fun pushShopProfileDirectly(
        profile: ShopProfileEntity,
        isNewShop: Boolean = false,
        repository: KonabRepository? = null
    ): Result<Unit> =
        shopSyncMutex.withLock {
            pushShopProfileInternal(profile, isNewShop, repository).map { }
        }

    private suspend fun pushShopProfileInternal(
        profile: ShopProfileEntity,
        isNewShop: Boolean = false,
        repository: KonabRepository? = null
    ): Result<Long> {
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Supabase non configuré"))
        }
        val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
        val effectiveDeployerId = profile.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: activeAgent?.id?.trim()?.uppercase()
        if (isNewShop && effectiveDeployerId.isNullOrBlank()) {
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

        val cloudId = if (isNewShop) {
            prepareCloudShopIdForNewShop(profile.name)
        } else {
            getOrCreateCloudShopId()
        }
        val resolvedLogo = resolvePortableLogoUri(profile.logoUri, cloudId)
        val profileWithAgent = profile.copy(
            deployerId = effectiveDeployerId ?: profile.deployerId,
            logoUri = resolvedLogo
        )
        if (repository != null && resolvedLogo != profile.logoUri) {
            try {
                repository.saveShopProfile(profileWithAgent)
            } catch (_: Exception) {}
        }

        val adminAcc = try {
            KonabDataStore(context).adminAccountFlow.firstOrNull()
        } catch (_: Exception) {
            null
        }
        val effectiveAdminName = adminAcc?.adminName?.ifBlank { "Gérant" } ?: "Gérant"
        val effectiveAdminPhone = adminAcc?.adminPhone?.ifBlank { profileWithAgent.phone } ?: profileWithAgent.phone

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            statusMessage = "Envoi de la boutique \"${profileWithAgent.name}\" (Agent $effectiveDeployerId)..."
        )
        val pushRes = apiClient.pushShopProfile(
            profile = profileWithAgent,
            cloudShopId = cloudId,
            isNewShop = isNewShop,
            adminName = effectiveAdminName,
            adminPhone = effectiveAdminPhone
        )
        val now = System.currentTimeMillis()
        if (pushRes.isSuccess) {
            val canonicalId = pushRes.getOrDefault(cloudId)
            saveCloudShopId(canonicalId, profileWithAgent.name)
            if (!effectiveDeployerId.isNullOrBlank()) {
                AgentDeploymentStorage.markCreatedShopOnDevice(context, profileWithAgent.name, effectiveDeployerId)
            }
            MultiShopStorage.saveConnectedShop(
                context,
                ConnectedShopSession(
                    cloudShopId = canonicalId,
                    shopName = profileWithAgent.name,
                    businessType = profileWithAgent.businessType,
                    city = profileWithAgent.city,
                    address = profileWithAgent.address,
                    phone = profileWithAgent.phone,
                    slogan = profileWithAgent.slogan,
                    currency = profileWithAgent.currency,
                    receiptFooter = profileWithAgent.receiptFooter,
                    logoUri = profileWithAgent.logoUri,
                    adminName = effectiveAdminName,
                    adminPhone = effectiveAdminPhone,
                    adminPin = profileWithAgent.adminPin,
                    cashierPin = profileWithAgent.cashierPin,
                    subscriptionTier = profileWithAgent.subscriptionTier,
                    deployerId = effectiveDeployerId,
                    lastSyncedAt = now
                )
            )
            saveLastSyncTimestamp(now)
            val msg = "Boutique \"${profileWithAgent.name}\" synchronisée (Agent: $effectiveDeployerId)"
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                syncedRecordsCount = 1,
                statusMessage = msg
            )
            NotificationHelper.sendCloudSyncSuccess(context, msg)
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

    suspend fun syncCompanyConfig(repository: KonabRepository): Result<Int> = shopSyncMutex.withLock {
        syncCompanyConfigInternal(repository)
    }

    private suspend fun syncCompanyConfigInternal(repository: KonabRepository): Result<Int> {
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
            val admin = repository.getAdminAccountOnce()
            if (shop != null && shop.isConfigured && shop.name.isNotBlank()) {
                val deployerId = shop.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                    ?: activeAgent?.id?.trim()?.uppercase()
                if (!deployerId.isNullOrBlank()) {
                    val cloudId = getOrCreateCloudShopId()
                    val resolvedLogo = resolvePortableLogoUri(shop.logoUri, cloudId)
                    val shopWithDeployer = shop.copy(deployerId = deployerId, logoUri = resolvedLogo)
                    if (resolvedLogo != shop.logoUri) {
                        try { repository.saveShopProfile(shopWithDeployer) } catch (_: Exception) {}
                    }
                    val pushRes = apiClient.pushShopProfile(
                        profile = shopWithDeployer,
                        cloudShopId = cloudId,
                        isNewShop = false,
                        adminName = admin.adminName,
                        adminPhone = admin.adminPhone.ifBlank { shop.phone }
                    )
                    if (pushRes.isSuccess) {
                        saveCloudShopId(pushRes.getOrDefault(cloudId), shop.name)
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
                    if (remoteShop.id > 1L) {
                        saveCloudShopId(remoteShop.id.toLong(), remoteShop.name)
                    }
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

            NotificationHelper.sendCloudSyncSuccess(context, successMsg)
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
     * Synchronise toutes les données persistantes de la boutique (configuration, produits, clients, ventes, fournisseurs, achats, caisse)
     * vers Supabase Cloud de façon permanente, isolées par `shop_id`.
     */
    suspend fun syncFullStore(repository: KonabRepository): Result<Int> = shopSyncMutex.withLock {
        val res = syncFullStoreInternal(repository)
        try {
            pullAndMergeRemoteInternal(repository)
        } catch (_: Exception) {}
        res
    }

    private suspend fun syncFullStoreInternal(repository: KonabRepository): Result<Int> {
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

            // 1. Profil entreprise + Identifiants gérant
            val shop = repository.getShopProfileOnce()
            val admin = repository.getAdminAccountOnce()
            val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
            val deployerId = shop?.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                ?: activeAgent?.id?.trim()?.uppercase()
            var activeCloudShopId = getOrCreateCloudShopId()

            if (shop != null && shop.isConfigured && shop.name.isNotBlank()) {
                val resolvedLogo = resolvePortableLogoUri(shop.logoUri, activeCloudShopId)
                val preparedShop = shop.copy(
                    deployerId = deployerId ?: shop.deployerId,
                    logoUri = resolvedLogo
                )
                if (resolvedLogo != shop.logoUri || (deployerId != null && deployerId != shop.deployerId)) {
                    try { repository.saveShopProfile(preparedShop) } catch (_: Exception) {}
                }
                val res = apiClient.pushShopProfile(
                    profile = preparedShop,
                    cloudShopId = activeCloudShopId,
                    isNewShop = false,
                    adminName = admin.adminName,
                    adminPhone = admin.adminPhone.ifBlank { shop.phone }
                )
                if (res.isSuccess) {
                    activeCloudShopId = res.getOrDefault(activeCloudShopId)
                    saveCloudShopId(activeCloudShopId, shop.name)
                    count++
                }
            }

            // 2. Déploiements d'agent
            val depRes = syncAgentDeploymentsInternal()
            count += depRes.getOrDefault(0)

            // 3. Produits & Stock (isolés par activeCloudShopId)
            val prods = repository.getAllProductsOnce()
            if (prods.isNotEmpty()) {
                val res = apiClient.pushProducts(prods, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }

            // 4. Clients
            val custs = repository.getAllCustomersOnce()
            if (custs.isNotEmpty()) {
                val res = apiClient.pushCustomers(custs, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }

            // 5. Ventes & Lignes de vente
            val sales = repository.getAllSalesOnce()
            val saleItems = repository.getAllSaleItemsOnce()
            if (sales.isNotEmpty()) {
                val res = apiClient.pushSales(sales, saleItems, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }

            // 6. Fournisseurs & Achats
            val supps = repository.getDao().getAllSuppliersOnce()
            if (supps.isNotEmpty()) {
                val res = apiClient.pushSuppliers(supps, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }
            val purchases = repository.getDao().getAllPurchasesOnce()
            if (purchases.isNotEmpty()) {
                val res = apiClient.pushPurchases(purchases, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }

            // 7. Sessions & Mouvements de Caisse
            val sessions = repository.getDao().getAllCashSessionsOnce()
            if (sessions.isNotEmpty()) {
                val res = apiClient.pushCashSessions(sessions, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }
            val movs = repository.getDao().getAllCashMovementsOnce()
            if (movs.isNotEmpty()) {
                val res = apiClient.pushCashMovements(movs, shopId = activeCloudShopId)
                count += res.getOrDefault(0)
            }

            // 8. Données Restaurant synchronisées (Équipe, Tables QR, Commandes Cuisine, Menu du jour)
            if (isRestaurantBusiness(shop?.businessType) ||
                RestaurantStorage.getOrders(context, activeCloudShopId).isNotEmpty() ||
                RestaurantStorage.getTables(context, activeCloudShopId).isNotEmpty()
            ) {
                val staff = RestaurantStorage.getStaffAccounts(context, activeCloudShopId)
                if (staff.isNotEmpty()) {
                    count += apiClient.pushRestaurantStaff(staff, activeCloudShopId).getOrDefault(0)
                }
                val tables = RestaurantStorage.getTables(context, activeCloudShopId)
                if (tables.isNotEmpty()) {
                    count += apiClient.pushRestaurantTables(tables, activeCloudShopId).getOrDefault(0)
                }
                val orders = RestaurantStorage.getOrders(context, activeCloudShopId)
                if (orders.isNotEmpty()) {
                    count += apiClient.pushRestaurantOrders(orders, activeCloudShopId).getOrDefault(0)
                }
                val menuIds = RestaurantStorage.getMenuOfDayProductIds(context, activeCloudShopId)
                if (menuIds.isNotEmpty() || isRestaurantBusiness(shop?.businessType)) {
                    apiClient.pushMenuOfDay(activeCloudShopId, menuIds)
                }
            }

            // 9. Données Atelier Couture (Modèles & Commandes/Mesures/Photos tissus)
            if (CoutureDirectoryHelper.isCoutureShop(shop?.businessType) ||
                CoutureStorage.getModels(context, activeCloudShopId).isNotEmpty() ||
                CoutureStorage.getOrders(context, activeCloudShopId).isNotEmpty()
            ) {
                count += pushCoutureStateInternal(activeCloudShopId, shop?.name.orEmpty())
            }

            // Sauvegarder aussi le cache local multiboutique
            repository.exportMultiShopSnapshot(activeCloudShopId)?.let { snap ->
                MultiShopStorage.saveConnectedShop(context, snap.session)
                MultiShopStorage.saveLocalSnapshotCache(context, snap)
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

            NotificationHelper.sendCloudSyncSuccess(context, successMsg)
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

    // --- MULTIBOUTIQUE : CONNEXION GÉRANT À DISTANCE, HYDRATATION & BASCULE ---

    private suspend fun pullRemoteSnapshotForShop(match: RemoteShopLoginMatch): Result<MultiShopSnapshot> {
        val shopId = match.cloudShopId
        val shopName = match.session.shopName
        val productsRes = apiClient.fetchProducts(shopId, shopName = shopName)
        val salesRes = apiClient.fetchSales(shopId, shopName = shopName)
        if (productsRes.isFailure || salesRes.isFailure) {
            return Result.failure(
                productsRes.exceptionOrNull()
                    ?: salesRes.exceptionOrNull()
                    ?: IllegalStateException("Impossible de charger les données distantes de la boutique.")
            )
        }
        val products = productsRes.getOrDefault(emptyList())
        val sales = salesRes.getOrDefault(emptyList())
        val saleIds = sales.map { it.id }.toSet()
        val saleItems = apiClient.fetchSaleItems(shopId, validSaleIds = saleIds.takeIf { it.isNotEmpty() }, shopName = shopName).getOrDefault(emptyList())
        val customers = apiClient.fetchCustomers(shopId, shopName = shopName).getOrDefault(emptyList())
        val suppliers = apiClient.fetchSuppliers(shopId, shopName = shopName).getOrDefault(emptyList())
        val purchases = apiClient.fetchPurchases(shopId, shopName = shopName).getOrDefault(emptyList())
        val cashMovements = apiClient.fetchCashMovements(shopId, shopName = shopName).getOrDefault(emptyList())
        val cashSessions = apiClient.fetchCashSessions(shopId, shopName = shopName).getOrDefault(emptyList())

        val latestRemoteProfile = try {
            apiClient.fetchShopProfileById(shopId).getOrNull() ?: match.profile
        } catch (_: Exception) {
            match.profile
        }
        val normalizedLogo = ShopLogoHelper.normalizeLogoUri(latestRemoteProfile.logoUri)
            ?.takeUnless { ShopLogoHelper.isLocalUnpublishedLogoUri(it) && ShopLogoHelper.getLocalFileIfExists(it) == null }
        if (normalizedLogo != null && ShopLogoHelper.isRemoteHttpUrl(normalizedLogo)) {
            try { ShopLogoHelper.cacheRemoteLogoBlocking(context, normalizedLogo, apiClient.httpClient) } catch (_: Exception) {}
        }
        val hydratedProfile = latestRemoteProfile.copy(logoUri = normalizedLogo)
        val hydratedSession = match.session.copy(
            logoUri = normalizedLogo,
            lastSyncedAt = System.currentTimeMillis()
        )

        return Result.success(
            MultiShopSnapshot(
                session = hydratedSession,
                shopProfile = hydratedProfile,
                products = products,
                customers = customers,
                sales = sales,
                saleItems = saleItems,
                suppliers = suppliers,
                purchases = purchases,
                cashMovements = cashMovements,
                cashSessions = cashSessions
            )
        )
    }

    private suspend fun snapshotCurrentLocalWorkspaceBestEffort(repository: KonabRepository) {
        val currentShop = repository.getShopProfileOnce()
        if (currentShop != null && currentShop.isConfigured && currentShop.name.isNotBlank()) {
            val currentCloudId = getOrCreateCloudShopId()
            repository.exportMultiShopSnapshot(currentCloudId)?.let { currentSnap ->
                MultiShopStorage.saveConnectedShop(context, currentSnap.session)
                MultiShopStorage.saveLocalSnapshotCache(context, currentSnap)
            }
            if (SupabaseConfig.isConfigured) {
                try {
                    syncFullStoreInternal(repository)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Authentifie un gérant partout où il se trouve via ses identifiants (Nom de boutique ou Téléphone gérant + Code PIN)
     * et charge l'intégralité du catalogue produits et des anciennes ventes dans la session active.
     */
    suspend fun loginAndHydrateShopRemote(
        identifier: String,
        pin: String,
        repository: KonabRepository
    ): Result<ConnectedShopSession> = shopSyncMutex.withLock {
        val cleanId = identifier.trim()
        val cleanPin = pin.trim()
        if (cleanId.isBlank() || cleanPin.isBlank()) {
            return@withLock Result.failure(IllegalArgumentException("Veuillez saisir l'identifiant (boutique ou téléphone) et le code PIN gérant."))
        }

        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isError = false,
            statusMessage = "Connexion à la boutique « $cleanId »..."
        )

        try {
            // 1. Sauvegarder la boutique actuellement ouverte sur cet appareil avant de basculer
            snapshotCurrentLocalWorkspaceBestEffort(repository)

            // 2. Authentification distante via Supabase (Gérant OU membre équipe Cuisine/Serveur/Caissier)
            if (SupabaseConfig.isConfigured) {
                val authRes = apiClient.authenticateShopTeamMemberRemote(cleanId, cleanPin)
                if (authRes.isSuccess) {
                    val staffLogin = authRes.getOrNull()
                    if (staffLogin != null) {
                        val match = staffLogin.match
                        val snapRes = pullRemoteSnapshotForShop(match)
                        if (snapRes.isSuccess) {
                            val snapshot = snapRes.getOrThrow()
                            repository.replaceLocalWorkspaceFromCloud(snapshot)
                            saveCloudShopId(match.cloudShopId, match.session.shopName)
                            MultiShopStorage.saveConnectedShop(context, snapshot.session)
                            MultiShopStorage.saveLocalSnapshotCache(context, snapshot)
                            BiometricSecurityHelper.setShopConfiguredCache(context, true)

                            // Hydrater l'état restaurant (équipe, tables, commandes, menu du jour)
                            pullRestaurantStateInternal(
                                shopId = match.cloudShopId,
                                shopName = snapshot.session.shopName,
                                localProducts = snapshot.products
                            )
                            if (CoutureDirectoryHelper.isCoutureShop(snapshot.session.businessType)) {
                                pullCoutureStateInternal(match.cloudShopId, snapshot.session.shopName)
                            }
                            if (staffLogin.staff != null) {
                                RestaurantStorage.setActiveStaff(context, match.cloudShopId, staffLogin.staff.id)
                            } else {
                                val mgr = RestaurantStorage.getStaffAccounts(context, match.cloudShopId)
                                    .firstOrNull { it.role == RestaurantStaffRole.MANAGER && it.isActive }
                                RestaurantStorage.setActiveStaff(context, match.cloudShopId, mgr?.id)
                            }

                            val now = System.currentTimeMillis()
                            saveLastSyncTimestamp(now)
                            val roleLabel = staffLogin.staff?.let { " [${it.role.badgeLabel}: ${it.name}]" } ?: ""
                            val msg = "Connecté à « ${snapshot.session.shopName} »$roleLabel (${snapshot.products.size} produits, ${snapshot.sales.size} ventes)."
                            _syncState.value = _syncState.value.copy(
                                isSyncing = false,
                                isError = false,
                                lastSyncTimestamp = now,
                                syncedRecordsCount = snapshot.products.size + snapshot.sales.size,
                                statusMessage = msg
                            )
                            NotificationHelper.sendCloudSyncSuccess(context, msg)
                            return@withLock Result.success(snapshot.session)
                        } else {
                            val cachedSnap = MultiShopStorage.loadLocalSnapshotCache(context, match.cloudShopId)
                            if (cachedSnap != null && cachedSnap.products.isNotEmpty()) {
                                repository.replaceLocalWorkspaceFromCloud(cachedSnap)
                                saveCloudShopId(match.cloudShopId, match.session.shopName)
                                MultiShopStorage.saveConnectedShop(context, cachedSnap.session)
                                BiometricSecurityHelper.setShopConfiguredCache(context, true)
                                pullRestaurantStateInternal(
                                    shopId = match.cloudShopId,
                                    shopName = cachedSnap.session.shopName,
                                    localProducts = cachedSnap.products
                                )
                                if (staffLogin.staff != null) {
                                    RestaurantStorage.setActiveStaff(context, match.cloudShopId, staffLogin.staff.id)
                                }
                                val msg = "Connecté à « ${cachedSnap.session.shopName} » (cache local, sync Cloud incomplète)"
                                _syncState.value = _syncState.value.copy(isSyncing = false, isError = false, statusMessage = msg)
                                return@withLock Result.success(cachedSnap.session)
                            }
                            val errMsg = snapRes.exceptionOrNull()?.localizedMessage
                                ?: "Authentification réussie pour « ${match.session.shopName} », mais impossible de charger les données depuis Supabase."
                            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = errMsg)
                            return@withLock Result.failure(IllegalStateException(errMsg))
                        }
                    } else {
                        // Aucun match distant : vérifier s'il s'agit d'une boutique locale pas encore poussée
                        val offlineSession = MultiShopStorage.findConnectedShopByCredentials(context, cleanId, cleanPin)
                        if (offlineSession == null) {
                            val errMsg = "Aucune boutique trouvée avec « $cleanId ». Vérifiez le nom de la boutique ou le téléphone du gérant."
                            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = errMsg)
                            return@withLock Result.failure(IllegalStateException(errMsg))
                        }
                    }
                } else {
                    val ex = authRes.exceptionOrNull()
                    if (ex is IllegalArgumentException) {
                        _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = ex.message)
                        return@withLock Result.failure(ex)
                    }
                    // Si erreur réseau, poursuivre vers le fallback cache multiboutique local ci-dessous
                }
            }

            // 3. Fallback hors-ligne : restaurer depuis le cache multiboutique local si déjà connecté précédemment
            val cachedSession = MultiShopStorage.findConnectedShopByCredentials(context, cleanId, cleanPin)
            if (cachedSession != null) {
                val cachedSnapshot = MultiShopStorage.loadLocalSnapshotCache(context, cachedSession.cloudShopId)
                if (cachedSnapshot != null) {
                    repository.replaceLocalWorkspaceFromCloud(cachedSnapshot)
                } else {
                    repository.replaceLocalWorkspaceFromCloud(
                        MultiShopSnapshot(
                            session = cachedSession,
                            shopProfile = cachedSession.toShopProfileEntity()
                        )
                    )
                }
                saveCloudShopId(cachedSession.cloudShopId, cachedSession.shopName)
                MultiShopStorage.setActiveCloudShopId(context, cachedSession.cloudShopId)
                BiometricSecurityHelper.setShopConfiguredCache(context, true)
                val cachedStaff = MultiShopStorage.findConnectedStaffForCredentials(context, cachedSession, cleanPin)
                if (cachedStaff != null) {
                    RestaurantStorage.setActiveStaff(context, cachedSession.cloudShopId, cachedStaff.id)
                } else {
                    val mgr = RestaurantStorage.getStaffAccounts(context, cachedSession.cloudShopId)
                        .firstOrNull { it.role == RestaurantStaffRole.MANAGER && it.isActive }
                    RestaurantStorage.setActiveStaff(context, cachedSession.cloudShopId, mgr?.id)
                }
                val msg = "Connecté hors-ligne à « ${cachedSession.shopName} »"
                _syncState.value = _syncState.value.copy(isSyncing = false, isError = false, statusMessage = msg)
                return@withLock Result.success(cachedSession)
            }

            val err = "Connexion impossible : vérifiez votre connexion Internet ou vos identifiants gérant."
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = err)
            return@withLock Result.failure(IllegalStateException(err))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Échec de connexion à la boutique"
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = errMsg)
            return@withLock Result.failure(e)
        }
    }

    /**
     * Bascule rapidement vers une boutique déjà enregistrée sur l'appareil (et rafraîchit depuis Supabase si en ligne).
     */
    suspend fun switchToConnectedShop(
        target: ConnectedShopSession,
        repository: KonabRepository
    ): Result<ConnectedShopSession> = shopSyncMutex.withLock {
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isError = false,
            statusMessage = "Bascule vers « ${target.shopName} »..."
        )
        try {
            snapshotCurrentLocalWorkspaceBestEffort(repository)

            if (SupabaseConfig.isConfigured && target.cloudShopId > 1L) {
                val remoteSnapRes = pullRemoteSnapshotForShop(
                    RemoteShopLoginMatch(
                        cloudShopId = target.cloudShopId,
                        session = target,
                        profile = target.toShopProfileEntity()
                    )
                )
                if (remoteSnapRes.isSuccess) {
                    val snapshot = remoteSnapRes.getOrThrow()
                    repository.replaceLocalWorkspaceFromCloud(snapshot)
                    saveCloudShopId(target.cloudShopId, target.shopName)
                    MultiShopStorage.saveConnectedShop(context, snapshot.session)
                    MultiShopStorage.saveLocalSnapshotCache(context, snapshot)
                    BiometricSecurityHelper.setShopConfiguredCache(context, true)
                    pullRestaurantStateInternal(
                        shopId = target.cloudShopId,
                        shopName = snapshot.session.shopName,
                        localProducts = snapshot.products
                    )
                    if (CoutureDirectoryHelper.isCoutureShop(target.businessType)) {
                        pullCoutureStateInternal(target.cloudShopId, snapshot.session.shopName)
                    }
                    val now = System.currentTimeMillis()
                    saveLastSyncTimestamp(now)
                    val msg = "Boutique active : « ${target.shopName} » (${snapshot.products.size} produits, ${snapshot.sales.size} ventes)."
                    _syncState.value = _syncState.value.copy(isSyncing = false, isError = false, lastSyncTimestamp = now, statusMessage = msg)
                    return@withLock Result.success(snapshot.session)
                }
            }

            val cached = MultiShopStorage.loadLocalSnapshotCache(context, target.cloudShopId)
            val snapshotToLoad = cached ?: MultiShopSnapshot(session = target, shopProfile = target.toShopProfileEntity())
            repository.replaceLocalWorkspaceFromCloud(snapshotToLoad)
            saveCloudShopId(target.cloudShopId, target.shopName)
            MultiShopStorage.setActiveCloudShopId(context, target.cloudShopId)
            BiometricSecurityHelper.setShopConfiguredCache(context, true)
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = false, statusMessage = "Boutique active : « ${target.shopName} »")
            Result.success(target)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = e.message)
            Result.failure(e)
        }
    }

    /**
     * Récupère et fusionne les derniers produits et ventes de la boutique active depuis Supabase (vue à distance & sur place).
     */
    suspend fun refreshActiveShopFromCloud(repository: KonabRepository): Result<Int> = shopSyncMutex.withLock {
        if (!SupabaseConfig.isConfigured) {
            return@withLock Result.failure(IllegalStateException("Supabase non configuré."))
        }
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isError = false,
            statusMessage = "Actualisation des produits et ventes..."
        )
        try {
            syncFullStoreInternal(repository)
            val merged = pullAndMergeRemoteInternal(repository)
            val now = System.currentTimeMillis()
            saveLastSyncTimestamp(now)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isError = false,
                lastSyncTimestamp = now,
                statusMessage = if (merged > 0) "$merged mise(s) à jour récupérée(s) depuis le Cloud" else "Catalogue et ventes déjà à jour"
            )
            Result.success(merged)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(isSyncing = false, isError = true, statusMessage = e.message)
            Result.failure(e)
        }
    }

    private suspend fun pullAndMergeRemoteInternal(repository: KonabRepository): Int {
        if (!SupabaseConfig.isConfigured) return 0
        val shop = repository.getShopProfileOnce() ?: return 0
        if (!shop.isConfigured || shop.name.isBlank()) return 0
        val shopId = getOrCreateCloudShopId()
        if (shopId <= 1L) return 0

        var profileUpdates = 0
        try {
            val remoteProfile = apiClient.fetchShopProfileById(shopId).getOrNull()
            if (remoteProfile != null && remoteProfile.name.isNotBlank()) {
                val remoteLogo = ShopLogoHelper.normalizeLogoUri(remoteProfile.logoUri)
                val localIsUnpublished = ShopLogoHelper.isLocalUnpublishedLogoUri(shop.logoUri) &&
                    ShopLogoHelper.getLocalFileIfExists(shop.logoUri) != null
                val remoteIsUsable = remoteLogo != null && !ShopLogoHelper.isLocalUnpublishedLogoUri(remoteLogo)
                if (remoteIsUsable && ShopLogoHelper.isRemoteHttpUrl(remoteLogo)) {
                    try { ShopLogoHelper.cacheRemoteLogoBlocking(context, remoteLogo!!, apiClient.httpClient) } catch (_: Exception) {}
                }
                if (!localIsUnpublished && remoteIsUsable && remoteLogo != shop.logoUri) {
                    repository.saveShopProfile(shop.copy(logoUri = remoteLogo))
                    profileUpdates++
                } else if (!localIsUnpublished && remoteLogo == null && !shop.logoUri.isNullOrBlank()) {
                    repository.saveShopProfile(shop.copy(logoUri = null))
                    profileUpdates++
                }
            }
        } catch (_: Exception) {}

        val remoteProducts = apiClient.fetchProducts(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remoteSales = apiClient.fetchSales(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remoteSaleIds = remoteSales.map { it.id }.toSet()
        val remoteSaleItems = if (remoteSales.isNotEmpty()) {
            apiClient.fetchSaleItems(shopId, validSaleIds = remoteSaleIds, shopName = shop.name).getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val remoteCustomers = apiClient.fetchCustomers(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remoteCashSessions = apiClient.fetchCashSessions(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remoteCashMovements = apiClient.fetchCashMovements(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remoteSuppliers = apiClient.fetchSuppliers(shopId, shopName = shop.name).getOrDefault(emptyList())
        val remotePurchases = apiClient.fetchPurchases(shopId, shopName = shop.name).getOrDefault(emptyList())

        val merged = repository.mergeRemoteUpdates(
            remoteProducts = remoteProducts,
            remoteSales = remoteSales,
            remoteSaleItems = remoteSaleItems,
            remoteCustomers = remoteCustomers,
            remoteCashSessions = remoteCashSessions,
            remoteCashMovements = remoteCashMovements,
            remoteSuppliers = remoteSuppliers,
            remotePurchases = remotePurchases,
            shopId = shopId
        )
        val restaurantMerged = if (isRestaurantBusiness(shop.businessType) ||
            RestaurantStorage.getOrders(context, shopId).isNotEmpty() ||
            RestaurantStorage.getTables(context, shopId).isNotEmpty()
        ) {
            pullRestaurantStateInternal(
                shopId = shopId,
                shopName = shop.name,
                localProducts = repository.getAllProductsOnce()
            )
        } else 0
        val coutureMerged = if (CoutureDirectoryHelper.isCoutureShop(shop.businessType) ||
            CoutureStorage.getOrders(context, shopId).isNotEmpty() ||
            CoutureStorage.getModels(context, shopId).isNotEmpty()
        ) {
            pullCoutureStateInternal(shopId, shop.name)
        } else 0
        repository.exportMultiShopSnapshot(shopId)?.let { snap ->
            MultiShopStorage.saveConnectedShop(context, snap.session)
            MultiShopStorage.saveLocalSnapshotCache(context, snap)
        }
        return merged + restaurantMerged + coutureMerged + profileUpdates
    }

    /**
     * Tire uniquement les mises à jour distantes (caisse, reçus, produits, restaurant) sans bloquer les écritures locales.
     */
    suspend fun pullLedgerAndRestaurantNow(repository: KonabRepository): Result<Int> = shopSyncMutex.withLock {
        if (!SupabaseConfig.isConfigured) return@withLock Result.success(0)
        return@withLock try {
            Result.success(pullAndMergeRemoteInternal(repository))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- SYNCHRONISATION TEMPS RÉEL RESTAURANT ---

    private suspend fun pullRestaurantStateInternal(
        shopId: Long,
        shopName: String? = null,
        localProducts: List<com.example.data.local.entity.ProductEntity> = emptyList()
    ): Int {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return 0
        var updates = 0
        try {
            val remoteStaff = apiClient.fetchRestaurantStaff(shopId, shopName).getOrDefault(emptyList())
            if (remoteStaff.isNotEmpty()) {
                RestaurantStorage.mergeStaffFromCloud(context, shopId, remoteStaff)
                updates += remoteStaff.size
            }
        } catch (_: Exception) {}

        try {
            val remoteTables = apiClient.fetchRestaurantTables(shopId, shopName).getOrDefault(emptyList())
            if (remoteTables.isNotEmpty()) {
                RestaurantStorage.mergeTablesFromCloud(context, shopId, remoteTables)
                updates += remoteTables.size
            }
        } catch (_: Exception) {}

        try {
            val remoteOrders = apiClient.fetchRestaurantOrders(shopId, shopName).getOrDefault(emptyList())
            if (remoteOrders.isNotEmpty()) {
                RestaurantStorage.mergeOrdersFromCloud(context, shopId, remoteOrders)
                updates += remoteOrders.size
            }
        } catch (_: Exception) {}

        try {
            val remoteMenu = apiClient.fetchMenuOfDay(shopId, shopName).getOrDefault(emptySet())
            if (remoteMenu.isNotEmpty()) {
                val normalizedMenu = if (localProducts.isNotEmpty()) {
                    val mapped = linkedSetOf<Long>()
                    for (mid in remoteMenu) {
                        val matchedProd = localProducts.firstOrNull { p ->
                            p.id == mid ||
                                MultiShopStorage.scopedCloudId(shopId, p.id) == mid ||
                                (mid >= 1_000_000_000_000L && MultiShopStorage.scopedCloudId(shopId, p.id) == mid) ||
                                (p.id in 1L..999_999L && mid == MultiShopStorage.scopedCloudId(shopId, p.id))
                        }
                        if (matchedProd != null) mapped.add(matchedProd.id)
                        else mapped.add(mid)
                    }
                    mapped
                } else {
                    remoteMenu
                }
                RestaurantStorage.setMenuOfDayProductIds(context, shopId, normalizedMenu)
                updates++
            }
        } catch (_: Exception) {}

        return updates
    }

    suspend fun pullRestaurantStateNow(shopId: Long, shopName: String? = null): Result<Int> {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return Result.success(0)
        return Result.success(pullRestaurantStateInternal(shopId, shopName))
    }

    suspend fun syncRestaurantOrderNow(shopId: Long, order: RestaurantOrderRecord): Result<Int> {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return Result.success(0)
        return apiClient.pushRestaurantOrders(listOf(order), shopId)
    }

    suspend fun syncRestaurantConfigNow(shopId: Long): Result<Int> {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return Result.success(0)
        var count = 0
        val staff = RestaurantStorage.getStaffAccounts(context, shopId)
        if (staff.isNotEmpty()) count += apiClient.pushRestaurantStaff(staff, shopId).getOrDefault(0)
        val tables = RestaurantStorage.getTables(context, shopId)
        if (tables.isNotEmpty()) count += apiClient.pushRestaurantTables(tables, shopId).getOrDefault(0)
        val menu = RestaurantStorage.getMenuOfDayProductIds(context, shopId)
        apiClient.pushMenuOfDay(shopId, menu)
        return Result.success(count)
    }

    suspend fun deleteRestaurantStaffCloud(staffId: String, shopId: Long = 0L): Result<Unit> {
        return apiClient.deleteRestaurantStaff(staffId, shopId)
    }

    suspend fun deleteRestaurantTableCloud(tableId: String, shopId: Long = 0L): Result<Unit> {
        return apiClient.deleteRestaurantTable(tableId, shopId)
    }

    suspend fun fetchPublicMenuForQr(parsedQr: ParsedTableQr): Result<SupabaseApiClient.PublicRestaurantMenuBundle> {
        return apiClient.fetchPublicRestaurantMenuBundle(
            shopId = parsedQr.shopId,
            fallbackShopName = parsedQr.shopName
        )
    }

    fun getLocalPublicRestaurantsDirectory(localShop: ShopProfileEntity? = null): List<PublicRestaurantListing> {
        val cachedShops = ShopAdStorage.getCachedShops(context)
        val connectedShops = MultiShopStorage.getConnectedShops(context)
        val activeLocalId = getOrCreateCloudShopId()
        return RestaurantDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = emptyList(),
            cachedShops = cachedShops,
            connectedShops = connectedShops,
            localShop = localShop,
            localShopCloudId = activeLocalId
        )
    }

    suspend fun fetchPublicRestaurantsDirectory(localShop: ShopProfileEntity? = null): Result<List<PublicRestaurantListing>> {
        val cachedShops = ShopAdStorage.getCachedShops(context)
        val connectedShops = MultiShopStorage.getConnectedShops(context)
        val activeLocalId = getOrCreateCloudShopId()

        val remoteRes = if (SupabaseConfig.isConfigured) {
            apiClient.fetchPublicRestaurantsDirectory()
        } else {
            Result.success(emptyList())
        }
        val remoteList = remoteRes.getOrDefault(emptyList())
        val merged = RestaurantDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = remoteList,
            cachedShops = cachedShops,
            connectedShops = connectedShops,
            localShop = localShop,
            localShopCloudId = activeLocalId
        )
        return if (remoteRes.isFailure && merged.isEmpty()) {
            Result.failure(remoteRes.exceptionOrNull() ?: IllegalStateException("Impossible de charger les restaurants."))
        } else {
            Result.success(merged)
        }
    }

    suspend fun submitPublicQrOrder(shopId: Long, order: RestaurantOrderRecord): Result<Int> {
        if (shopId <= 1L) return Result.failure(IllegalArgumentException("Identifiant restaurant manquant."))
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Connexion Supabase requise pour transmettre la commande à distance."))
        }
        return apiClient.pushRestaurantOrders(listOf(order.copy(shopId = shopId)), shopId)
    }

    // --- AGENT DEPLOYMENTS CLOUD SYNC ---

    suspend fun syncAgentDeployments(): Result<Int> = shopSyncMutex.withLock {
        syncAgentDeploymentsInternal()
    }

    private suspend fun syncAgentDeploymentsInternal(): Result<Int> {
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

    // --- SHOP ADS & SHOWCASE FEED ---

    fun getLocalShowcaseFeed(localShop: ShopProfileEntity? = null): ShowcaseFeedResult {
        val localAds = ShopAdStorage.getAllAds(context)
        val cachedShops = ShopAdStorage.getCachedShops(context)
        val localDeployments = AgentDeploymentStorage.getAllDeployments(context)
        val settings = ShopAdStorage.getCarouselSettings(context)
        val merged = ShopAdStorage.mergeShopsAndAds(
            registeredShops = cachedShops,
            ads = localAds,
            localShop = localShop,
            localDeployments = localDeployments,
            settings = settings
        )
        return ShowcaseFeedResult(
            showcaseAds = merged,
            registeredShops = cachedShops,
            fromCloud = false
        )
    }

    suspend fun fetchShowcaseFeed(localShop: ShopProfileEntity? = null): ShowcaseFeedResult {
        val localDeployments = AgentDeploymentStorage.getAllDeployments(context)
        var shops = ShopAdStorage.getCachedShops(context)
        var cloudSuccess = false

        if (SupabaseConfig.isConfigured) {
            try {
                val remoteShopsRes = apiClient.fetchAllRegisteredShops()
                if (remoteShopsRes.isSuccess) {
                    val fetchedShops = remoteShopsRes.getOrDefault(emptyList())
                    if (fetchedShops.isNotEmpty()) {
                        shops = fetchedShops
                        ShopAdStorage.saveCachedShops(context, fetchedShops)
                    }
                    cloudSuccess = true
                }
            } catch (_: Exception) {}

            try {
                val remoteAdsRes = apiClient.fetchShopAds()
                if (remoteAdsRes.isSuccess) {
                    val fetchedAds = remoteAdsRes.getOrDefault(emptyList())
                    ShopAdStorage.saveCloudAds(context, fetchedAds)
                    cloudSuccess = true
                }
            } catch (_: Exception) {}

            // Pousser les éventuelles publicités locales non encore synchronisées
            try {
                val pendingAds = ShopAdStorage.getAllAds(context).filter { !it.isSynced && !it.isFallbackShopOnly }
                for (pending in pendingAds) {
                    val pushRes = apiClient.pushShopAd(pending)
                    if (pushRes.isSuccess) {
                        ShopAdStorage.saveAd(context, pending.copy(isSynced = true))
                    }
                }
            } catch (_: Exception) {}
        }

        val allAds = ShopAdStorage.getAllAds(context)
        val settings = ShopAdStorage.getCarouselSettings(context)
        val merged = ShopAdStorage.mergeShopsAndAds(
            registeredShops = shops,
            ads = allAds,
            localShop = localShop,
            localDeployments = localDeployments,
            settings = settings
        )
        return ShowcaseFeedResult(
            showcaseAds = merged,
            registeredShops = shops,
            fromCloud = cloudSuccess
        )
    }

    suspend fun publishShopAd(
        ad: ShopAdRecord,
        localLogoFile: File? = null,
        localVideoFile: File? = null
    ): Result<ShopAdRecord> {
        val safeDuration = ShopAdStorage.clampVideoDurationSec(ad.videoDurationSec, ad.videoUri.isNotBlank())
        var resolvedLogoUri = ad.logoUri.trim()
        var resolvedVideoUri = ad.videoUri.trim()
        var cloudSynced = false

        if (SupabaseConfig.isConfigured) {
            if (localLogoFile != null && localLogoFile.exists() && localLogoFile.length() > 0L) {
                val ext = localLogoFile.extension.ifBlank { "jpg" }
                val remotePath = "logos/${ad.id}_${System.currentTimeMillis()}.$ext"
                val upLogo = apiClient.uploadAdMedia(localLogoFile, remotePath, "image/jpeg")
                if (upLogo.isSuccess) {
                    resolvedLogoUri = upLogo.getOrThrow()
                }
            }

            if (localVideoFile != null && localVideoFile.exists() && localVideoFile.length() > 0L) {
                val ext = localVideoFile.extension.ifBlank { "mp4" }
                val remotePath = "videos/${ad.id}_${System.currentTimeMillis()}.$ext"
                val upVideo = apiClient.uploadAdMedia(localVideoFile, remotePath, "video/mp4")
                if (upVideo.isSuccess) {
                    resolvedVideoUri = upVideo.getOrThrow()
                }
            }
        }

        val preparedAd = ad.copy(
            logoUri = resolvedLogoUri,
            videoUri = resolvedVideoUri,
            videoDurationSec = safeDuration,
            updatedAt = System.currentTimeMillis(),
            isSynced = false,
            isFallbackShopOnly = false
        )

        // Sauvegarde locale immédiate
        ShopAdStorage.saveAd(context, preparedAd)

        if (SupabaseConfig.isConfigured) {
            val pushRes = apiClient.pushShopAd(preparedAd)
            if (pushRes.isSuccess) {
                cloudSynced = true
                val syncedAd = preparedAd.copy(isSynced = true)
                ShopAdStorage.saveAd(context, syncedAd)
                return Result.success(syncedAd)
            }
        }

        return Result.success(preparedAd.copy(isSynced = cloudSynced))
    }

    suspend fun deleteShopAd(adId: String): Result<Unit> {
        ShopAdStorage.deleteAd(context, adId)
        if (SupabaseConfig.isConfigured) {
            apiClient.deleteShopAd(adId)
        }
        return Result.success(Unit)
    }

    // --- SECTEUR COUTURE : MODÈLES, MESURES, PHOTOS TISSUS, QR CATALOGUE & SUIVI CLIENT ---

    private suspend fun uploadCoutureLocalPhotoIfNeeded(uri: String, subfolder: String): String {
        val clean = uri.trim()
        if (clean.isBlank() || clean.startsWith("http://") || clean.startsWith("https://")) {
            return clean
        }
        if (!SupabaseConfig.isConfigured) return clean
        val file = CouturePhotoHelper.getUploadableFileForUri(context, clean) ?: return clean
        val remotePath = "couture/$subfolder/${file.name}"
        val upRes = apiClient.uploadAdMedia(file, remotePath, "image/jpeg")
        return if (upRes.isSuccess) upRes.getOrThrow() else clean
    }

    private suspend fun pushCoutureStateInternal(shopId: Long, shopName: String): Int {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return 0
        var count = 0
        val localModels = CoutureStorage.getModels(context, shopId)
        if (localModels.isNotEmpty()) {
            val preparedModels = localModels.map { model ->
                val resolvedImg = uploadCoutureLocalPhotoIfNeeded(model.imageUri, "models")
                if (resolvedImg != model.imageUri || model.shopName.isBlank()) {
                    val updated = model.copy(
                        imageUri = resolvedImg,
                        shopName = model.shopName.ifBlank { shopName }
                    )
                    CoutureStorage.upsertModel(context, shopId, updated)
                    updated
                } else {
                    model
                }
            }
            count += apiClient.pushCoutureModels(preparedModels, shopId).getOrDefault(0)
        }

        val localOrders = CoutureStorage.getOrders(context, shopId)
        if (localOrders.isNotEmpty()) {
            val preparedOrders = localOrders.map { order ->
                val resolvedFabric = uploadCoutureLocalPhotoIfNeeded(order.fabricPhotoUri, "fabrics")
                val resolvedModelImg = uploadCoutureLocalPhotoIfNeeded(order.modelImageUri, "models")
                if (resolvedFabric != order.fabricPhotoUri || resolvedModelImg != order.modelImageUri || order.shopName.isBlank()) {
                    val updated = order.copy(
                        fabricPhotoUri = resolvedFabric,
                        modelImageUri = resolvedModelImg,
                        shopName = order.shopName.ifBlank { shopName }
                    )
                    CoutureStorage.upsertOrder(context, shopId, updated)
                    updated
                } else {
                    order
                }
            }
            count += apiClient.pushCoutureOrders(preparedOrders, shopId).getOrDefault(0)
        }
        return count
    }

    private suspend fun pullCoutureStateInternal(shopId: Long, shopName: String? = null): Int {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return 0
        var updates = 0
        try {
            val remoteModels = apiClient.fetchCoutureModels(shopId, shopName).getOrDefault(emptyList())
            if (remoteModels.isNotEmpty()) {
                CoutureStorage.mergeModelsFromCloud(context, shopId, remoteModels)
                updates += remoteModels.size
            }
        } catch (_: Exception) {}

        try {
            val remoteOrders = apiClient.fetchCoutureOrders(shopId, shopName).getOrDefault(emptyList())
            if (remoteOrders.isNotEmpty()) {
                CoutureStorage.mergeOrdersFromCloud(context, shopId, remoteOrders)
                updates += remoteOrders.size
            }
        } catch (_: Exception) {}

        return updates
    }

    suspend fun pullCoutureStateNow(shopId: Long, shopName: String? = null): Result<Int> {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return Result.success(0)
        return Result.success(pullCoutureStateInternal(shopId, shopName))
    }

    suspend fun syncCoutureModelNow(shopId: Long, model: CoutureModelRecord, shopName: String): Result<CoutureModelRecord> {
        val resolvedUri = uploadCoutureLocalPhotoIfNeeded(model.imageUri, "models")
        val finalModel = model.copy(
            shopId = shopId,
            shopName = model.shopName.ifBlank { shopName },
            imageUri = resolvedUri,
            updatedAt = maxOf(model.updatedAt, System.currentTimeMillis())
        )
        CoutureStorage.upsertModel(context, shopId, finalModel)
        if (SupabaseConfig.isConfigured && shopId > 1L) {
            val pushRes = apiClient.pushCoutureModels(listOf(finalModel), shopId)
            if (pushRes.isFailure) {
                return Result.success(finalModel)
            }
        }
        return Result.success(finalModel)
    }

    suspend fun syncCoutureOrderNow(shopId: Long, order: CoutureOrderRecord, shopName: String): Result<CoutureOrderRecord> {
        val resolvedFabricUri = uploadCoutureLocalPhotoIfNeeded(order.fabricPhotoUri, "fabrics")
        val resolvedModelUri = uploadCoutureLocalPhotoIfNeeded(order.modelImageUri, "models")
        val finalOrder = order.copy(
            shopId = shopId,
            shopName = order.shopName.ifBlank { shopName },
            fabricPhotoUri = resolvedFabricUri,
            modelImageUri = resolvedModelUri,
            updatedAt = maxOf(order.updatedAt, System.currentTimeMillis())
        )
        CoutureStorage.upsertOrder(context, shopId, finalOrder)
        if (SupabaseConfig.isConfigured && shopId > 1L) {
            apiClient.pushCoutureOrders(listOf(finalOrder), shopId)
        }
        return Result.success(finalOrder)
    }

    suspend fun syncAllCoutureConfigNow(shopId: Long, shopName: String): Result<Int> {
        if (!SupabaseConfig.isConfigured || shopId <= 1L) return Result.success(0)
        return Result.success(pushCoutureStateInternal(shopId, shopName))
    }

    suspend fun deleteCoutureModelCloud(modelId: String): Result<Unit> {
        return apiClient.deleteCoutureModel(modelId)
    }

    fun getLocalPublicCoutureDirectory(localShop: ShopProfileEntity? = null): List<PublicCouturierListing> {
        val cachedShops = ShopAdStorage.getCachedShops(context)
        val connectedShops = MultiShopStorage.getConnectedShops(context)
        val activeLocalId = getOrCreateCloudShopId()
        val localModels = CoutureStorage.getAllLocalModels(context)
        val counts = localModels.groupingBy { it.shopId }.eachCount()
        return CoutureDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = emptyList(),
            cachedShops = cachedShops,
            connectedShops = connectedShops,
            localShop = localShop,
            localShopCloudId = activeLocalId,
            modelCountsByShopId = counts
        )
    }

    suspend fun fetchPublicCoutureDirectory(localShop: ShopProfileEntity? = null): Result<List<PublicCouturierListing>> {
        val cachedShops = ShopAdStorage.getCachedShops(context)
        val connectedShops = MultiShopStorage.getConnectedShops(context)
        val activeLocalId = getOrCreateCloudShopId()

        val remoteRes = if (SupabaseConfig.isConfigured) {
            apiClient.fetchPublicCouturiersDirectory()
        } else {
            Result.success(emptyList())
        }
        val remoteModels = if (SupabaseConfig.isConfigured) {
            apiClient.fetchPublicCoutureModelsFeed().getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val allKnownModels = (remoteModels + CoutureStorage.getAllLocalModels(context)).distinctBy { it.id }
        val counts = allKnownModels.groupingBy { it.shopId }.eachCount()

        val merged = CoutureDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = remoteRes.getOrDefault(emptyList()),
            cachedShops = cachedShops,
            connectedShops = connectedShops,
            localShop = localShop,
            localShopCloudId = activeLocalId,
            modelCountsByShopId = counts
        )
        return if (remoteRes.isFailure && merged.isEmpty()) {
            Result.failure(remoteRes.exceptionOrNull() ?: IllegalStateException("Impossible de charger les couturiers."))
        } else {
            Result.success(merged)
        }
    }

    suspend fun fetchPublicCoutureModelsFeed(): Result<List<CoutureModelRecord>> {
        val localModels = CoutureStorage.getAllLocalModels(context)
        if (!SupabaseConfig.isConfigured) {
            return Result.success(localModels)
        }
        val remoteRes = apiClient.fetchPublicCoutureModelsFeed()
        val remoteList = remoteRes.getOrDefault(emptyList())
        val byId = LinkedHashMap<String, CoutureModelRecord>()
        remoteList.forEach { byId[it.id] = it }
        localModels.forEach { local ->
            val existing = byId[local.id]
            if (existing == null || local.updatedAt >= existing.updatedAt) {
                byId[local.id] = local
            }
        }
        return Result.success(byId.values.filter { it.isAvailable }.sortedByDescending { it.updatedAt })
    }

    suspend fun fetchPublicCoutureCatalogForQr(parsedQr: ParsedCoutureQr): Result<SupabaseApiClient.PublicCoutureCatalogBundle> {
        return apiClient.fetchPublicCoutureCatalogBundle(
            shopId = parsedQr.shopId,
            fallbackShopName = parsedQr.shopName
        )
    }

    suspend fun registerCoutureClientAccount(account: CoutureClientAccount): Result<CoutureClientAccount> {
        val norm = account.copy(
            id = account.id.ifBlank { "CLI-${CoutureDirectoryHelper.normalizePhone(account.phone).ifBlank { System.currentTimeMillis().toString() }}" },
            firstName = account.firstName.trim(),
            lastName = account.lastName.trim(),
            phone = account.phone.trim(),
            city = account.city.trim(),
            updatedAt = System.currentTimeMillis()
        )
        CoutureStorage.saveClientAccount(context, norm)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushCoutureClientAccount(norm)
        }
        return Result.success(norm)
    }

    suspend fun fetchClientCoutureProgress(clientPhone: String, clientName: String? = null): Result<List<CoutureOrderRecord>> {
        val localMatches = CoutureStorage.getAllLocalOrders(context).filter {
            CoutureDirectoryHelper.matchesClientOrder(it, clientPhone, clientName)
        }
        if (!SupabaseConfig.isConfigured) {
            return Result.success(localMatches)
        }
        val remoteRes = apiClient.fetchClientCoutureOrdersByPhone(clientPhone)
        val remoteMatches = remoteRes.getOrDefault(emptyList())
        val byId = LinkedHashMap<String, CoutureOrderRecord>()
        localMatches.forEach { byId[it.id] = it }
        remoteMatches.forEach { remote ->
            val existing = byId[remote.id]
            if (existing == null || remote.updatedAt >= existing.updatedAt) {
                byId[remote.id] = remote
            }
        }
        val merged = byId.values.sortedByDescending { it.updatedAt }
        return if (remoteRes.isFailure && merged.isEmpty()) {
            Result.failure(remoteRes.exceptionOrNull() ?: IllegalStateException("Impossible de charger votre suivi couture."))
        } else {
            Result.success(merged)
        }
    }

    suspend fun submitPublicCoutureOrderRequest(shopId: Long, order: CoutureOrderRecord): Result<Int> {
        if (shopId <= 1L) return Result.failure(IllegalArgumentException("Identifiant atelier couturier manquant."))
        if (!SupabaseConfig.isConfigured) {
            return Result.failure(IllegalStateException("Connexion Supabase requise pour envoyer la demande au couturier."))
        }
        return apiClient.pushCoutureOrders(listOf(order.copy(shopId = shopId)), shopId)
    }

    // --- PORTAIL GESTION POUBELLES / SALUBRITÉ ---

    suspend fun fetchAllWastePortalState(): Result<Pair<List<WasteBusinessProfile>, List<WasteHouseholdClient>>> {
        val localBusinesses = WasteCollectorStorage.getBusinesses(context)
        val localClients = WasteCollectorStorage.getClients(context)
        if (!SupabaseConfig.isConfigured) {
            return Result.success(localBusinesses to localClients)
        }
        return try {
            val remoteBizRes = apiClient.fetchWasteBusinesses()
            val remoteCliRes = apiClient.fetchWasteClients()
            val mergedBiz = if (remoteBizRes.isSuccess) {
                WasteCollectorStorage.mergeBusinessesFromCloud(context, remoteBizRes.getOrDefault(emptyList()))
            } else {
                localBusinesses
            }
            val mergedCli = if (remoteCliRes.isSuccess) {
                WasteCollectorStorage.mergeClientsFromCloud(context, remoteCliRes.getOrDefault(emptyList()))
            } else {
                localClients
            }
            Result.success(mergedBiz to mergedCli)
        } catch (e: Exception) {
            Result.success(localBusinesses to localClients)
        }
    }

    private suspend fun uploadActivityCardPhotoIfNeeded(uri: String, subfolder: String): String {
        val clean = uri.trim()
        if (clean.isBlank() || clean.startsWith("http://") || clean.startsWith("https://")) {
            return clean
        }
        if (!SupabaseConfig.isConfigured) return clean
        val directFile = java.io.File(clean.removePrefix("file://")).takeIf { it.exists() && it.length() > 0L }
            ?: CouturePhotoHelper.getUploadableFileForUri(context, clean)
            ?: return clean
        val remotePath = "cards/$subfolder/${directFile.name}"
        val upRes = apiClient.uploadAdMedia(directFile, remotePath, "image/jpeg")
        return if (upRes.isSuccess) upRes.getOrThrow() else clean
    }

    suspend fun syncWasteBusinessNow(biz: WasteBusinessProfile): Result<WasteBusinessProfile> {
        val resolvedPhoto = uploadActivityCardPhotoIfNeeded(biz.photoUri, "waste-biz")
        val prepared = if (resolvedPhoto != biz.photoUri) biz.copy(photoUri = resolvedPhoto) else biz
        val saved = WasteCollectorStorage.upsertBusiness(context, prepared)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushWasteBusinesses(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun syncWasteClientNow(client: WasteHouseholdClient): Result<WasteHouseholdClient> {
        val resolvedPhoto = uploadActivityCardPhotoIfNeeded(client.photoUri, "waste-clients")
        val prepared = if (resolvedPhoto != client.photoUri) client.copy(photoUri = resolvedPhoto) else client
        val saved = WasteCollectorStorage.upsertClient(context, prepared)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushWasteClients(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun deleteWasteClientCloud(clientId: String): Result<Unit> {
        WasteCollectorStorage.deleteClient(context, clientId)
        if (SupabaseConfig.isConfigured) {
            apiClient.deleteWasteClient(clientId)
        }
        return Result.success(Unit)
    }

    // --- PORTAIL TONTINE NUMÉRIQUE (CARTES 30 JOURS) ---

    suspend fun fetchAllTontinePortalState(): Result<Triple<List<TontineBusinessProfile>, List<TontineGroup>, List<TontineMemberCard>>> {
        val localBiz = TontineStorage.getBusinesses(context)
        val localGroups = TontineStorage.getGroups(context)
        val localCards = TontineStorage.getCards(context)
        if (!SupabaseConfig.isConfigured) {
            return Result.success(Triple(localBiz, localGroups, localCards))
        }
        return try {
            val remoteBizRes = apiClient.fetchTontineBusinesses()
            val remoteGroupsRes = apiClient.fetchTontineGroups()
            val remoteCardsRes = apiClient.fetchTontineCards()
            val mergedBiz = if (remoteBizRes.isSuccess) {
                TontineStorage.mergeBusinessesFromCloud(context, remoteBizRes.getOrDefault(emptyList()))
            } else {
                localBiz
            }
            val mergedGroups = if (remoteGroupsRes.isSuccess) {
                TontineStorage.mergeGroupsFromCloud(context, remoteGroupsRes.getOrDefault(emptyList()))
            } else {
                localGroups
            }
            val mergedCards = if (remoteCardsRes.isSuccess) {
                TontineStorage.mergeCardsFromCloud(context, remoteCardsRes.getOrDefault(emptyList()))
            } else {
                localCards
            }
            Result.success(Triple(mergedBiz, mergedGroups, mergedCards))
        } catch (e: Exception) {
            Result.success(Triple(localBiz, localGroups, localCards))
        }
    }

    suspend fun syncTontineBusinessNow(biz: TontineBusinessProfile): Result<TontineBusinessProfile> {
        val resolvedPhoto = uploadActivityCardPhotoIfNeeded(biz.photoUri, "tontine-biz")
        val prepared = if (resolvedPhoto != biz.photoUri) biz.copy(photoUri = resolvedPhoto) else biz
        val saved = TontineStorage.upsertBusiness(context, prepared)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushTontineBusinesses(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun syncTontineGroupNow(group: TontineGroup): Result<TontineGroup> {
        val saved = TontineStorage.upsertGroup(context, group)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushTontineGroups(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun syncTontineCardNow(card: TontineMemberCard): Result<TontineMemberCard> {
        val resolvedPhoto = uploadActivityCardPhotoIfNeeded(card.photoUri, "tontine-cards")
        val prepared = if (resolvedPhoto != card.photoUri) card.copy(photoUri = resolvedPhoto) else card
        val saved = TontineStorage.upsertCard(context, prepared)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushTontineCards(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun deleteTontineCardCloud(cardId: String): Result<Unit> {
        TontineStorage.deleteCard(context, cardId)
        if (SupabaseConfig.isConfigured) {
            apiClient.deleteTontineCard(cardId)
        }
        return Result.success(Unit)
    }

    // --- UTILISATEURS PUBLICS (COMPTE & CARTE CONAKRY WEB / KONAB POS) ---

    suspend fun syncPublicUserNow(user: PublicUserAccount): Result<PublicUserAccount> {
        val resolvedPhoto = uploadActivityCardPhotoIfNeeded(user.photoUri, "app-users")
        val prepared = if (resolvedPhoto != user.photoUri) user.copy(photoUri = resolvedPhoto) else user
        val saved = PublicUserStorage.upsertUser(context, prepared, makeActive = true)
        if (SupabaseConfig.isConfigured) {
            apiClient.pushAppUsers(listOf(saved))
        }
        return Result.success(saved)
    }

    suspend fun fetchPublicUsersFromCloud(): Result<List<PublicUserAccount>> {
        val local = PublicUserStorage.getAllUsers(context)
        if (!SupabaseConfig.isConfigured) return Result.success(local)
        return try {
            val remote = apiClient.fetchAppUsers()
            if (remote.isSuccess) {
                Result.success(PublicUserStorage.mergeUsersFromCloud(context, remote.getOrDefault(emptyList())))
            } else {
                Result.success(local)
            }
        } catch (_: Exception) {
            Result.success(local)
        }
    }
}
