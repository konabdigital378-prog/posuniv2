package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KonabDatabase
import com.example.data.local.entity.*
import com.example.data.repository.KonabRepository
import com.example.ui.navigation.KonabScreen
import com.example.util.BackupRestoreHelper
import com.example.util.BusinessTypePresets
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File

data class CartItem(
    val product: ProductEntity,
    val quantity: Int
) {
    val totalPrice: Long get() = product.sellPrice * quantity
    val totalCost: Long get() = product.buyPrice * quantity
}

class KonabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: KonabRepository
    private val supabaseSyncManager: com.example.data.remote.supabase.SupabaseSyncManager =
        com.example.data.remote.supabase.SupabaseSyncManager(application)
    val supabaseSyncState: StateFlow<com.example.data.remote.supabase.SupabaseSyncState> =
        supabaseSyncManager.syncState

    private var periodicSyncJob: kotlinx.coroutines.Job? = null

    init {
        val database = KonabDatabase.getDatabase(application)
        val dataStore = com.example.data.local.datastore.KonabDataStore(application)
        repository = KonabRepository(database.konabDao(), dataStore)
        com.example.util.NotificationHelper.createNotificationChannels(application)

        // Vérification non-bloquante du premier lancement
        viewModelScope.launch {
            val profile = repository.getShopProfileOnce()
            if (profile == null || !profile.isConfigured) {
                _screenStack.value = listOf(KonabScreen.ONBOARDING)
            }
        }

        // Démarrage de la synchronisation automatique continue
        startPeriodicSync()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(2000L)
            try {
                if (supabaseSyncManager.isAutoSyncEnabled() && com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                    supabaseSyncManager.syncAll(repository)
                }
            } catch (_: Exception) {}
        }
    }

    // DataStore Local Admin Account Stream
    val adminAccount: StateFlow<com.example.data.local.datastore.LocalAdminAccount> = repository.adminAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.local.datastore.LocalAdminAccount())

    val isSetupCompleted: StateFlow<Boolean> = repository.isSetupCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Streams
    val shopProfile: StateFlow<ShopProfileEntity?> = repository.shopProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtors: StateFlow<List<CustomerEntity>> = repository.debtorCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSales: StateFlow<List<SaleEntity>> = repository.recentSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSales: StateFlow<List<SaleEntity>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSaleItems: StateFlow<List<SaleItemEntity>> = repository.allSaleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashMovements: StateFlow<List<CashMovementEntity>> = repository.allCashMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCashSession: StateFlow<CashSessionEntity?> = repository.activeCashSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPurchases: StateFlow<List<PurchaseEntity>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = repository.unreadNotificationsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Navigation State
    private val _screenStack = MutableStateFlow<List<KonabScreen>>(listOf(KonabScreen.DASHBOARD))
    val currentScreen: StateFlow<KonabScreen> = _screenStack.map { it.lastOrNull() ?: KonabScreen.DASHBOARD }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KonabScreen.DASHBOARD)

    // Cart State
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _discountAmount = MutableStateFlow(0L)
    val discountAmount: StateFlow<Long> = _discountAmount.asStateFlow()

    // Sale Result State
    private val _lastCompletedSale = MutableStateFlow<Pair<SaleEntity, List<SaleItemEntity>>?>(null)
    val lastCompletedSale: StateFlow<Pair<SaleEntity, List<SaleItemEntity>>?> = _lastCompletedSale.asStateFlow()

    // Role & PIN lock State
    private val _currentRole = MutableStateFlow("ADMIN") // ADMIN or CASHIER
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val subtotal: Long get() = _cart.value.sumOf { it.totalPrice }
    val totalAmount: Long get() = maxOf(0L, subtotal - _discountAmount.value)

    // Navigation methods
    fun navigateTo(screen: KonabScreen) {
        val current = _screenStack.value
        if (current.lastOrNull() != screen) {
            _screenStack.value = current + screen
        }
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        if (current.size > 1) {
            _screenStack.value = current.dropLast(1)
            return true
        }
        return false
    }

    fun resetTo(screen: KonabScreen) {
        _screenStack.value = listOf(screen)
    }

    // Cart operations
    fun addToCart(product: ProductEntity) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(CartItem(product = product, quantity = 1))
        }
        _cart.value = current
    }

    fun updateCartQuantity(productId: Long, delta: Int) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            val existing = current[index]
            val newQty = existing.quantity + delta
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = existing.copy(quantity = newQty)
            }
            _cart.value = current
        }
    }

    fun removeFromCart(productId: Long) {
        _cart.value = _cart.value.filterNot { it.product.id == productId }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _discountAmount.value = 0L
        _selectedCustomer.value = null
    }

    fun setDiscount(amount: Long) {
        _discountAmount.value = maxOf(0L, amount)
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    // Process Sale
    fun processSale(
        paymentMethod: String,
        isCredit: Boolean,
        notes: String,
        onSuccess: (SaleEntity) -> Unit
    ) {
        val currentCart = _cart.value
        if (currentCart.isEmpty()) return

        val saleItems = currentCart.map { item ->
            SaleItemEntity(
                saleId = 0L,
                productId = item.product.id,
                productName = item.product.name,
                quantity = item.quantity,
                unitPrice = item.product.sellPrice,
                unitBuyPrice = item.product.buyPrice,
                totalPrice = item.totalPrice
            )
        }

        viewModelScope.launch {
            val customer = _selectedCustomer.value
            val cashier = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            val sale = repository.recordSale(
                customerId = customer?.id,
                customerName = customer?.name ?: "Client de passage",
                items = saleItems,
                subtotal = subtotal,
                discountAmount = _discountAmount.value,
                taxAmount = 0L,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                isCredit = isCredit,
                cashierName = cashier,
                notes = notes
            )
            _lastCompletedSale.value = Pair(sale, saleItems)
            clearCart()

            // Trigger Android system notification for sale completion
            val app = getApplication<Application>()
            val curr = shopProfile.value?.currency ?: "FCFA"
            com.example.util.NotificationHelper.sendSaleSuccess(app, sale.transactionNumber, totalAmount, curr)

            // Trigger stock alerts if any item reached minimum threshold
            currentCart.forEach { item ->
                val remainingStock = item.product.stockQuantity - item.quantity
                if (remainingStock <= item.product.minStockAlert) {
                    com.example.util.NotificationHelper.sendStockAlert(
                        app,
                        item.product.name,
                        remainingStock,
                        item.product.minStockAlert
                    )
                }
            }

            if (isCredit && customer != null) {
                com.example.util.NotificationHelper.sendDebtAlert(
                    app,
                    customer.name,
                    totalAmount,
                    curr
                )
            }

            onSuccess(sale)
        }
    }

    fun openCashSession(openingBalance: Long) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.openCashSession(openingBalance, operator)
        }
    }

    fun closeCashSession(actualCountedBalance: Long, notes: String) {
        val active = activeCashSession.value ?: return
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.closeCashSession(active, actualCountedBalance, operator, notes)
        }
    }

    fun addCashMovement(type: String, amount: Long, reason: String) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.addCashMovement(
                type = type,
                amount = amount,
                reason = reason,
                operatorName = operator
            )
        }
    }

    fun saveProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (product.id == 0L) {
                repository.insertProduct(product)
            } else {
                repository.updateProduct(product)
            }
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
        }
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            if (customer.id == 0L) {
                repository.insertCustomer(customer)
            } else {
                repository.updateCustomer(customer)
            }
        }
    }

    fun recordCustomerDebtPayment(customer: CustomerEntity, amount: Long, method: String) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.recordCustomerDebtPayment(customer, amount, method, operator)
        }
    }

    fun saveSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            if (supplier.id == 0L) {
                repository.insertSupplier(supplier)
            } else {
                repository.updateSupplier(supplier)
            }
        }
    }

    fun recordPurchase(
        supplier: SupplierEntity,
        product: ProductEntity,
        quantity: Int,
        unitCost: Long,
        isPaidFromCash: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.recordPurchase(
                supplier = supplier,
                product = product,
                quantity = quantity,
                unitCost = unitCost,
                isPaidFromCash = isPaidFromCash,
                operatorName = operator,
                notes = notes
            )
        }
    }

    fun completeOnboarding(
        shopName: String,
        slogan: String,
        phone: String,
        address: String,
        city: String,
        businessType: String,
        adminName: String,
        adminPhone: String,
        adminPin: String,
        preloadDemoData: Boolean = false,
        subscriptionTier: String = "Premium"
    ) {
        viewModelScope.launch {
            val profile = com.example.data.local.entity.ShopProfileEntity(
                id = 1,
                name = shopName.trim(),
                slogan = slogan.trim(),
                phone = phone.trim(),
                address = address.trim(),
                city = city.trim(),
                currency = "FCFA",
                businessType = businessType,
                adminPin = adminPin,
                cashierPin = "0000",
                subscriptionTier = subscriptionTier,
                isConfigured = true,
                receiptFooter = "Merci pour votre confiance ! À très bientôt !"
            )
            repository.initializeStore(
                name = shopName,
                slogan = slogan,
                phone = phone,
                address = address,
                city = city,
                businessType = businessType,
                adminName = adminName,
                adminPhone = adminPhone,
                adminPin = adminPin,
                preloadDemoData = preloadDemoData,
                subscriptionTier = subscriptionTier
            )
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    supabaseSyncManager.pushShopProfileDirectly(profile, isNewShop = true)
                    supabaseSyncManager.syncFullStore(repository)
                } catch (e: Exception) {
                    android.util.Log.e("KonabPOS", "Erreur envoi initial boutique vers Cloud: ${e.message}", e)
                }
            }
            resetTo(KonabScreen.DASHBOARD)
        }
    }

    /**
     * Clears all demo / test data from the application:
     * Products, Sales, Customers, Suppliers, Debts, Cash Movements, Sessions.
     * When keepShopProfile is true: shop identity & PIN are kept, database is reset to clean 0 items.
     * When keepShopProfile is false: full app reset redirecting to SetupWizard.
     */
    fun clearAllDemoData(keepShopProfile: Boolean = true, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAllDemoData(keepShopProfile = keepShopProfile)
            _cart.value = emptyList()
            _selectedCustomer.value = null
            _discountAmount.value = 0L
            _lastCompletedSale.value = null
            if (!keepShopProfile) {
                supabaseSyncManager.resetCloudShopId()
                resetTo(KonabScreen.ONBOARDING)
            }
            onComplete()
        }
    }

    fun updateShopProfile(updated: ShopProfileEntity) {
        viewModelScope.launch {
            repository.saveShopProfile(updated)
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    supabaseSyncManager.pushShopProfileDirectly(updated, isNewShop = false)
                    supabaseSyncManager.syncFullStore(repository)
                } catch (e: Exception) {
                    android.util.Log.e("KonabPOS", "Erreur pushShopProfileDirectly: ${e.message}", e)
                }
            }
        }
    }

    fun switchRole(newRole: String) {
        _currentRole.value = newRole
    }

    fun unlockWithPin(enteredPin: String): Boolean {
        val profile = shopProfile.value ?: return enteredPin == "1234"
        if (enteredPin == profile.adminPin) {
            _currentRole.value = "ADMIN"
            _isLocked.value = false
            return true
        } else if (enteredPin == profile.cashierPin) {
            _currentRole.value = "CASHIER"
            _isLocked.value = false
            return true
        }
        return false
    }

    fun lockApp() {
        _isLocked.value = true
    }

    fun activateSubscription(enteredCode: String, targetTier: String): Boolean {
        val validation = com.example.util.LicenseHelper.validateKey(enteredCode, targetTier)
        if (validation.isValid) {
            val profile = shopProfile.value ?: return false
            val days = if (validation.durationDays > 0) validation.durationDays else 365
            val expiry = System.currentTimeMillis() + (days.toLong() * 24 * 60 * 60 * 1000)
            val finalTier = if (validation.tier.isNotBlank()) validation.tier else targetTier
            val updated = profile.copy(
                subscriptionTier = finalTier,
                subscriptionExpiresAt = expiry
            )
            updateShopProfile(updated)
            return true
        }
        return false
    }

    fun activateSubscriptionByAgent(
        tier: String,
        durationDays: Int,
        agentName: String,
        agentPhone: String,
        notes: String
    ): Boolean {
        val profile = shopProfile.value ?: return false
        val expiry = System.currentTimeMillis() + (durationDays.toLong() * 24 * 60 * 60 * 1000)
        val updated = profile.copy(
            subscriptionTier = tier,
            subscriptionExpiresAt = expiry
        )
        updateShopProfile(updated)

        // Save generated license key to agent history
        try {
            val app = getApplication<Application>()
            val durationCode = when (durationDays) {
                30 -> "1M"
                90 -> "3M"
                180 -> "6M"
                3650 -> "VIE"
                else -> "1A"
            }
            val keyData = com.example.util.LicenseHelper.generateKey(
                tier = tier,
                durationCode = durationCode,
                beneficiary = profile.name,
                note = "Activé par agent $agentName ($agentPhone) • $notes"
            )
            com.example.util.LicenseHelper.saveGeneratedLicense(app, keyData)

            // Record deployment audit record
            val deploymentId = "DEP-${System.currentTimeMillis()}"
            val record = com.example.data.remote.supabase.AgentDeploymentRecord(
                id = deploymentId,
                agentId = agentName.take(12),
                agentName = agentName,
                agentPhone = agentPhone,
                shopName = profile.name,
                shopPhone = profile.phone,
                shopCity = profile.city,
                tier = tier,
                durationDays = durationDays,
                licenseKey = keyData.key,
                notes = notes,
                timestamp = System.currentTimeMillis(),
                isSynced = false
            )
            com.example.data.remote.supabase.AgentDeploymentStorage.saveDeployment(app, record)

            // Trigger background sync to Supabase if connected
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    if (com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                        supabaseSyncManager.syncAgentDeployments()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return true
    }

    fun syncAgentDeploymentsToSupabase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.syncAgentDeployments()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    onResult(true, if (count > 0) "$count activation(s) synchronisée(s) sur Supabase Cloud" else "Toutes les activations sont déjà à jour sur Supabase")
                } else {
                    onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Échec de synchronisation avec Supabase")
                }
            }
        }
    }

    fun refreshAgentsFromSupabase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.refreshAgentsFromCloud()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (res.isSuccess) {
                    val list = res.getOrDefault(emptyList())
                    onResult(true, "${list.size} agent(s) synchronisé(s) depuis Supabase")
                } else {
                    onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Échec du chargement des agents")
                }
            }
        }
    }

    fun preloadCatalogueForAgent(businessType: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val preset = com.example.util.BusinessTypePresets.getPreset(businessType)
                for (prod in preset.defaultProducts) {
                    repository.insertProduct(prod.copy(id = 0L))
                }
            } catch (_: Exception) {}
            onComplete()
        }
    }

    fun backupDatabase(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val file = BackupRestoreHelper.createBackup(context, repository.getDao())
                onResult(file)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    fun restoreDatabase(jsonString: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val ok = BackupRestoreHelper.restoreFromJson(jsonString, repository.getDao())
                onResult(ok)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun viewSaleReceipt(sale: SaleEntity) {
        viewModelScope.launch {
            val items = repository.getSaleItems(sale.id)
            _lastCompletedSale.value = Pair(sale, items)
            navigateTo(KonabScreen.RECEIPT)
        }
    }

    fun cancelSale(
        saleId: Long,
        reason: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            val result = repository.cancelSale(
                saleId = saleId,
                reason = reason,
                cancelledBy = operator
            )
            if (result.isSuccess) {
                val sale = result.getOrNull()
                if (sale != null) {
                    val app = getApplication<Application>()
                    val curr = shopProfile.value?.currency ?: "FCFA"
                    com.example.util.NotificationHelper.sendSaleCancelled(
                        app,
                        sale.transactionNumber,
                        sale.totalAmount,
                        reason,
                        curr
                    )
                }
                onResult(true, "Vente annulée avec succès. Les articles ont été remis en stock.")
            } else {
                val errMsg = result.exceptionOrNull()?.localizedMessage ?: "Échec de l'annulation"
                onResult(false, errMsg)
            }
        }
    }

    // Supabase Cloud Database Integration
    fun setSupabaseAutoSyncEnabled(enabled: Boolean) {
        supabaseSyncManager.setAutoSyncEnabled(enabled)
        if (enabled) {
            startPeriodicSync()
            triggerAutoSync()
        } else {
            periodicSyncJob?.cancel()
            periodicSyncJob = null
        }
    }

    fun triggerAutoSync() {
        if (!supabaseSyncManager.isAutoSyncEnabled() || !supabaseSyncState.value.isConfigured) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val res = supabaseSyncManager.syncAll(repository)
                if (res.isFailure) {
                    android.util.Log.e("KonabPOS", "Erreur syncAll: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("KonabPOS", "Exception triggerAutoSync", e)
            }
        }
    }

    private fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                kotlinx.coroutines.delay(45_000L) // Synchronisation automatique en arrière-plan toutes les 45 secondes
                if (supabaseSyncManager.isAutoSyncEnabled() && com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                    try {
                        supabaseSyncManager.syncAll(repository)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun syncWithSupabase() {
        triggerAutoSync()
    }

    fun syncFullStoreWithSupabase(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.syncFullStore(repository)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (res.isSuccess) {
                    onResult(true, "Synchronisation réussie (${res.getOrDefault(0)} éléments envoyés)")
                } else {
                    onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Échec de synchronisation")
                }
            }
        }
    }

    fun testSupabaseConnection(url: String?, key: String?, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.testConnection(url, key)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (res.isSuccess) {
                    onResult(true, res.getOrNull() ?: "Connexion réussie")
                } else {
                    onResult(false, res.exceptionOrNull()?.message ?: "Échec de connexion")
                }
            }
        }
    }

    fun saveSupabaseCredentials(url: String, key: String) {
        supabaseSyncManager.saveCredentials(url, key)
        triggerAutoSync()
    }

    fun clearSupabaseCredentials() {
        supabaseSyncManager.clearCredentials()
    }

    // --- NOTIFICATIONS MANAGEMENT ---

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun deleteAllNotifications() {
        viewModelScope.launch {
            repository.deleteAllNotifications()
        }
    }

    fun triggerTestNotification(type: String) {
        val app = getApplication<Application>()
        val curr = shopProfile.value?.currency ?: "FCFA"
        when (type) {
            "STOCK" -> {
                com.example.util.NotificationHelper.sendStockAlert(app, "Riz Parfumé 25kg", 2, 5)
                viewModelScope.launch {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Alerte Stock : Riz Parfumé 25kg",
                            message = "Plus que 2 sacs en réserve (seuil minimal : 5). Pensez à réapprovisionner auprès de votre fournisseur.",
                            type = "STOCK_ALERT",
                            priority = "HIGH",
                            actionRoute = "stock"
                        )
                    )
                }
            }
            "SALE" -> {
                val transNo = "20261031-0089"
                com.example.util.NotificationHelper.sendSaleSuccess(app, transNo, 45000L, curr)
                viewModelScope.launch {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Vente encaissée ($transNo)",
                            message = "Paiement de 45 000 $curr encaissé avec succès (Espèces - Client de passage).",
                            type = "SALE_COMPLETED",
                            priority = "NORMAL",
                            actionRoute = "reports"
                        )
                    )
                }
            }
            "DEBT" -> {
                com.example.util.NotificationHelper.sendDebtAlert(app, "Fatou Diop", 15000L, curr)
                viewModelScope.launch {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Relance Crédit Client",
                            message = "Le client Fatou Diop a une dette en cours de 15 000 $curr depuis 7 jours.",
                            type = "DEBT_ALERT",
                            priority = "HIGH",
                            actionRoute = "customers"
                        )
                    )
                }
            }
            "SYNC" -> {
                com.example.util.NotificationHelper.sendCloudSyncSuccess(app, "Sauvegarde Supabase effectuée avec succès (11 tables synchronisées)")
                viewModelScope.launch {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Cloud Supabase à jour",
                            message = "Toutes vos données (produits, ventes, caisse, clients, fournisseurs) sont sauvegardées sur Supabase.",
                            type = "SUPABASE_SYNC",
                            priority = "LOW",
                            actionRoute = "settings"
                        )
                    )
                }
            }
        }
    }

    // --- SUPABASE AUTHENTICATION INTEGRATION ---

    val supabaseAuthState: StateFlow<com.example.data.remote.supabase.SupabaseAuthManager.AuthState> =
        com.example.data.remote.supabase.SupabaseAuthManager.authState

    fun signInSupabase(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = com.example.data.remote.supabase.SupabaseAuthManager.signIn(email, pass, getApplication())
            result.onSuccess { session ->
                if (session.user.role.isNotBlank()) {
                    _currentRole.value = session.user.role
                }
                syncFullStoreWithSupabase()
                onResult(true, "Connexion réussie ! Bienvenue ${session.user.name}")
            }.onFailure { err ->
                onResult(false, err.message ?: "Échec de connexion")
            }
        }
    }

    fun signUpSupabase(
        email: String,
        pass: String,
        name: String,
        phone: String,
        role: String,
        shopName: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = com.example.data.remote.supabase.SupabaseAuthManager.signUp(
                email = email,
                password = pass,
                name = name,
                phone = phone,
                role = role,
                shopName = shopName,
                context = getApplication()
            )
            result.onSuccess { session ->
                if (session.user.role.isNotBlank()) {
                    _currentRole.value = session.user.role
                }
                syncFullStoreWithSupabase()
                onResult(true, "Compte Supabase créé avec succès !")
            }.onFailure { err ->
                onResult(false, err.message ?: "Échec de l'inscription")
            }
        }
    }

    fun signOutSupabase(onResult: () -> Unit) {
        viewModelScope.launch {
            com.example.data.remote.supabase.SupabaseAuthManager.signOut(getApplication())
            onResult()
        }
    }

    fun recoverSupabasePassword(email: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = com.example.data.remote.supabase.SupabaseAuthManager.recoverPassword(email)
            result.onSuccess { msg ->
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.message ?: "Échec de la récupération")
            }
        }
    }

    fun refreshSupabaseSession(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = com.example.data.remote.supabase.SupabaseAuthManager.refreshSession(getApplication())
            result.onSuccess {
                onResult(true, "Session actualisée avec succès")
            }.onFailure { err ->
                onResult(false, err.message ?: "Échec de l'actualisation")
            }
        }
    }

    fun runSupabaseDiagnostic(onResult: (com.example.data.remote.supabase.SupabaseAuthManager.DiagnosticResult) -> Unit) {
        viewModelScope.launch {
            val diag = com.example.data.remote.supabase.SupabaseAuthManager.runSelfTest()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(diag)
            }
        }
    }
}
