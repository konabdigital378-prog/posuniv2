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
import com.example.data.remote.supabase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File

data class CartItem(
    val product: ProductEntity,
    val quantity: Int,
    val variantNote: String = "",
    val itemDiscount: Long = 0L
) {
    val unitFinalPrice: Long get() = (product.sellPrice - itemDiscount).coerceAtLeast(0L)
    val totalPrice: Long get() = unitFinalPrice * quantity
    val totalCost: Long get() = product.buyPrice * quantity
}

class KonabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: KonabRepository = KonabRepository(
        KonabDatabase.getDatabase(application).konabDao(),
        com.example.data.local.datastore.KonabDataStore(application)
    )
    private val supabaseSyncManager: com.example.data.remote.supabase.SupabaseSyncManager =
        com.example.data.remote.supabase.SupabaseSyncManager(application)
    val supabaseSyncState: StateFlow<com.example.data.remote.supabase.SupabaseSyncState> =
        supabaseSyncManager.syncState

    private var periodicSyncJob: kotlinx.coroutines.Job? = null

    // Registre Multiboutique
    private val _connectedShops = MutableStateFlow(
        com.example.data.remote.supabase.MultiShopStorage.getConnectedShops(application)
    )
    val connectedShops: StateFlow<List<com.example.data.remote.supabase.ConnectedShopSession>> =
        _connectedShops.asStateFlow()

    private val _activeCloudShopId = MutableStateFlow(
        com.example.data.remote.supabase.MultiShopStorage.getActiveCloudShopId(application)
    )
    val activeCloudShopId: StateFlow<Long> = _activeCloudShopId.asStateFlow()

    private fun reloadConnectedShopsState() {
        val app = getApplication<Application>()
        _connectedShops.value = MultiShopStorage.getConnectedShops(app)
        _activeCloudShopId.value = MultiShopStorage.getActiveCloudShopId(app)
    }

    // --- ÉTAT RESTAURANT MULTI-UTILISATEURS & QR TABLES ---
    private val _restaurantStaff = MutableStateFlow<List<RestaurantStaffAccount>>(emptyList())
    val restaurantStaff: StateFlow<List<RestaurantStaffAccount>> = _restaurantStaff.asStateFlow()

    private val _activeStaff = MutableStateFlow<RestaurantStaffAccount?>(null)
    val activeStaff: StateFlow<RestaurantStaffAccount?> = _activeStaff.asStateFlow()

    private val _restaurantTables = MutableStateFlow<List<RestaurantTableRecord>>(emptyList())
    val restaurantTables: StateFlow<List<RestaurantTableRecord>> = _restaurantTables.asStateFlow()

    private val _restaurantOrders = MutableStateFlow<List<RestaurantOrderRecord>>(emptyList())
    val restaurantOrders: StateFlow<List<RestaurantOrderRecord>> = _restaurantOrders.asStateFlow()

    private val _menuOfDayProductIds = MutableStateFlow<Set<Long>>(emptySet())
    val menuOfDayProductIds: StateFlow<Set<Long>> = _menuOfDayProductIds.asStateFlow()

    private val _isSyncingRestaurant = MutableStateFlow(false)
    val isSyncingRestaurant: StateFlow<Boolean> = _isSyncingRestaurant.asStateFlow()

    private val _pendingQrMenuTarget = MutableStateFlow<ParsedTableQr?>(null)
    val pendingQrMenuTarget: StateFlow<ParsedTableQr?> = _pendingQrMenuTarget.asStateFlow()

    private val _publicQrMenuBundle = MutableStateFlow<SupabaseApiClient.PublicRestaurantMenuBundle?>(null)
    val publicQrMenuBundle: StateFlow<SupabaseApiClient.PublicRestaurantMenuBundle?> = _publicQrMenuBundle.asStateFlow()

    private val _isLoadingQrMenu = MutableStateFlow(false)
    val isLoadingQrMenu: StateFlow<Boolean> = _isLoadingQrMenu.asStateFlow()

    private val _qrMenuError = MutableStateFlow<String?>(null)
    val qrMenuError: StateFlow<String?> = _qrMenuError.asStateFlow()

    private val _lastRestaurantSyncAt = MutableStateFlow(System.currentTimeMillis())
    val lastRestaurantSyncAt: StateFlow<Long> = _lastRestaurantSyncAt.asStateFlow()

    private val _publicRestaurants = MutableStateFlow<List<PublicRestaurantListing>>(emptyList())
    val publicRestaurants: StateFlow<List<PublicRestaurantListing>> = _publicRestaurants.asStateFlow()

    private val _isLoadingPublicRestaurants = MutableStateFlow(false)
    val isLoadingPublicRestaurants: StateFlow<Boolean> = _isLoadingPublicRestaurants.asStateFlow()

    private val _publicRestaurantsError = MutableStateFlow<String?>(null)
    val publicRestaurantsError: StateFlow<String?> = _publicRestaurantsError.asStateFlow()

    private val notifiedRestaurantEventKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun reloadRestaurantLocalState() {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shop = shopProfile.value
        if (shop != null && shop.businessType.equals("Restaurant", ignoreCase = true)) {
            val staff = RestaurantStorage.ensureDefaultRestaurantTeam(
                context = app,
                shopId = shopId,
                adminName = adminAccount.value.adminName.ifBlank { "Gérant" },
                adminPhone = adminAccount.value.adminPhone.ifBlank { shop.phone },
                adminPin = shop.adminPin.ifBlank { "1234" }
            )
            val tables = RestaurantStorage.ensureDefaultTables(app, shopId)
            _restaurantStaff.value = staff
            _restaurantTables.value = tables
        } else {
            _restaurantStaff.value = RestaurantStorage.getStaffAccounts(app, shopId)
            _restaurantTables.value = RestaurantStorage.getTables(app, shopId)
        }
        _activeStaff.value = RestaurantStorage.getActiveStaff(app, shopId)
        _restaurantOrders.value = RestaurantStorage.getOrders(app, shopId)
        _menuOfDayProductIds.value = RestaurantStorage.getMenuOfDayProductIds(app, shopId)
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

    // Navigation State (démarre sur le menu principal: Entrer dans ma boutique / Espace Agent)
    private val _screenStack = MutableStateFlow<List<KonabScreen>>(listOf(KonabScreen.ONBOARDING))
    val currentScreen: StateFlow<KonabScreen> = _screenStack.map { it.lastOrNull() ?: KonabScreen.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KonabScreen.ONBOARDING)

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

    // Role & PIN / Biometric Launch Lock State
    private val _currentRole = MutableStateFlow("ADMIN") // ADMIN or CASHIER
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    private val _isLocked = MutableStateFlow(
        com.example.util.BiometricSecurityHelper.shouldLockOnLaunch(application)
    )
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val subtotal: Long get() = _cart.value.sumOf { it.totalPrice }
    val totalAmount: Long get() = maxOf(0L, subtotal - _discountAmount.value)

    val supabaseAuthState: StateFlow<com.example.data.remote.supabase.SupabaseAuthManager.AuthState> =
        com.example.data.remote.supabase.SupabaseAuthManager.authState

    // --- ÉTAT ATELIER COUTURE, QR CATALOGUE & SUIVI CLIENT ---
    private val _coutureModels = MutableStateFlow<List<CoutureModelRecord>>(emptyList())
    val coutureModels: StateFlow<List<CoutureModelRecord>> = _coutureModels.asStateFlow()

    private val _coutureOrders = MutableStateFlow<List<CoutureOrderRecord>>(emptyList())
    val coutureOrders: StateFlow<List<CoutureOrderRecord>> = _coutureOrders.asStateFlow()

    private val _isSyncingCouture = MutableStateFlow(false)
    val isSyncingCouture: StateFlow<Boolean> = _isSyncingCouture.asStateFlow()

    private val _lastCoutureSyncAt = MutableStateFlow(System.currentTimeMillis())
    val lastCoutureSyncAt: StateFlow<Long> = _lastCoutureSyncAt.asStateFlow()

    private val _pendingCoutureQrTarget = MutableStateFlow<ParsedCoutureQr?>(null)
    val pendingCoutureQrTarget: StateFlow<ParsedCoutureQr?> = _pendingCoutureQrTarget.asStateFlow()

    private val _publicCoutureBundle = MutableStateFlow<SupabaseApiClient.PublicCoutureCatalogBundle?>(null)
    val publicCoutureBundle: StateFlow<SupabaseApiClient.PublicCoutureCatalogBundle?> = _publicCoutureBundle.asStateFlow()

    private val _isLoadingCoutureCatalog = MutableStateFlow(false)
    val isLoadingCoutureCatalog: StateFlow<Boolean> = _isLoadingCoutureCatalog.asStateFlow()

    private val _coutureCatalogError = MutableStateFlow<String?>(null)
    val coutureCatalogError: StateFlow<String?> = _coutureCatalogError.asStateFlow()

    private val _publicCouturiers = MutableStateFlow<List<PublicCouturierListing>>(emptyList())
    val publicCouturiers: StateFlow<List<PublicCouturierListing>> = _publicCouturiers.asStateFlow()

    private val _publicCoutureModelsFeed = MutableStateFlow<List<CoutureModelRecord>>(emptyList())
    val publicCoutureModelsFeed: StateFlow<List<CoutureModelRecord>> = _publicCoutureModelsFeed.asStateFlow()

    private val _isLoadingCoutureDirectory = MutableStateFlow(false)
    val isLoadingCoutureDirectory: StateFlow<Boolean> = _isLoadingCoutureDirectory.asStateFlow()

    private val _activeCoutureClient = MutableStateFlow(CoutureStorage.getClientAccount(application))
    val activeCoutureClient: StateFlow<CoutureClientAccount?> = _activeCoutureClient.asStateFlow()

    private val _clientCoutureOrders = MutableStateFlow<List<CoutureOrderRecord>>(emptyList())
    val clientCoutureOrders: StateFlow<List<CoutureOrderRecord>> = _clientCoutureOrders.asStateFlow()

    private val _isLoadingClientCoutureOrders = MutableStateFlow(false)
    val isLoadingClientCoutureOrders: StateFlow<Boolean> = _isLoadingClientCoutureOrders.asStateFlow()

    fun reloadCoutureLocalState() {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shop = shopProfile.value
        if (shop != null && CoutureDirectoryHelper.isCoutureShop(shop.businessType)) {
            _coutureModels.value = CoutureStorage.ensureDefaultModelsForAtelier(
                context = app,
                shopId = shopId,
                shopName = shop.name
            )
        } else {
            _coutureModels.value = CoutureStorage.getModels(app, shopId)
        }
        _coutureOrders.value = CoutureStorage.getOrders(app, shopId)
        _activeCoutureClient.value = CoutureStorage.getClientAccount(app)
    }

    init {
        com.example.util.NotificationHelper.createNotificationChannels(application)

        // Vérification non-bloquante au lancement (toujours démarrer sur le menu principal)
        viewModelScope.launch {
            val profile = repository.getShopProfileOnce()
            if (profile == null || !profile.isConfigured) {
                com.example.util.BiometricSecurityHelper.setShopConfiguredCache(application, false)
                _isLocked.value = false
                _screenStack.value = listOf(KonabScreen.ONBOARDING)
            } else {
                com.example.util.BiometricSecurityHelper.setShopConfiguredCache(application, true)
                if (com.example.util.BiometricSecurityHelper.isLaunchLockEnabled(application)) {
                    _isLocked.value = true
                }
                // S'assurer que la boutique locale active figure dans le registre multiboutique
                val shopId = supabaseSyncManager.getOrCreateCloudShopId()
                repository.exportMultiShopSnapshot(shopId)?.let { snap ->
                    MultiShopStorage.saveConnectedShop(application, snap.session)
                    MultiShopStorage.saveLocalSnapshotCache(application, snap)
                }
                reloadConnectedShopsState()
                reloadRestaurantLocalState()
                reloadCoutureLocalState()
            }
        }

        // Démarrage de la synchronisation automatique continue (après initialisation de tous les StateFlows)
        startPeriodicSync()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(2000L)
            try {
                if (supabaseSyncManager.isAutoSyncEnabled() && SupabaseConfig.isConfigured) {
                    supabaseSyncManager.syncAll(repository)
                    reloadConnectedShopsState()
                    reloadRestaurantLocalState()
                    reloadCoutureLocalState()
                }
            } catch (_: Exception) {}
        }
    }

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

    fun addToCartWithVariant(product: ProductEntity, variantNote: String = "", discount: Long = 0L) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == product.id && it.variantNote == variantNote }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(CartItem(product = product, quantity = 1, variantNote = variantNote, itemDiscount = discount))
        }
        _cart.value = current
    }

    fun addCustomAmountToCart(
        title: String,
        unitPrice: Long,
        quantity: Int = 1,
        unit: String = "forfait",
        variantNote: String = ""
    ) {
        if (unitPrice <= 0L || quantity <= 0) return
        val syntheticId = -System.currentTimeMillis()
        val syntheticProduct = ProductEntity(
            id = syntheticId,
            name = title.ifBlank { "Prestation / Vente libre" },
            category = "Vente Libre",
            buyPrice = 0L,
            sellPrice = unitPrice,
            stockQuantity = 999,
            minStockAlert = 0,
            unit = unit.ifBlank { "forfait" }
        )
        _cart.value = _cart.value + CartItem(
            product = syntheticProduct,
            quantity = quantity,
            variantNote = variantNote
        )
    }

    fun updateCartItemNote(productId: Long, note: String) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            current[index] = current[index].copy(variantNote = note)
            _cart.value = current
        }
    }

    fun updateCartItemDiscount(productId: Long, discount: Long) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.product.id == productId }
        if (index >= 0) {
            current[index] = current[index].copy(itemDiscount = discount)
            _cart.value = current
        }
    }

    fun switchShopSector(newSector: String, reloadPresetProducts: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val profile = repository.getShopProfileOnce()
            if (profile != null) {
                val updated = profile.copy(businessType = newSector)
                repository.saveShopProfile(updated)
            }
            if (reloadPresetProducts) {
                val preset = com.example.util.BusinessTypePresets.getPreset(newSector)
                preset.defaultProducts.forEach { prod ->
                    repository.insertProduct(prod)
                }
            }
            reloadRestaurantLocalState()
            reloadCoutureLocalState()
            onComplete()
        }
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
            val displayName = if (item.variantNote.isNotBlank()) {
                "${item.product.name} [${item.variantNote}]"
            } else {
                item.product.name
            }
            SaleItemEntity(
                saleId = 0L,
                productId = item.product.id,
                productName = displayName,
                quantity = item.quantity,
                unitPrice = item.unitFinalPrice,
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

            // Trigger stock alerts if any real catalog item reached minimum threshold
            currentCart.forEach { item ->
                if (item.product.id > 0L) {
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
            }

            if (isCredit && customer != null) {
                com.example.util.NotificationHelper.sendDebtAlert(
                    app,
                    customer.name,
                    totalAmount,
                    curr
                )
            }

            triggerAutoSync()
            onSuccess(sale)
        }
    }

    fun openCashSession(openingBalance: Long) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.openCashSession(openingBalance, operator)
            triggerAutoSync()
        }
    }

    fun closeCashSession(actualCountedBalance: Long, notes: String) {
        val active = activeCashSession.value ?: return
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.closeCashSession(active, actualCountedBalance, operator, notes)
            triggerAutoSync()
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
            triggerAutoSync()
        }
    }

    fun saveProduct(product: ProductEntity) {
        viewModelScope.launch {
            if (product.id == 0L) {
                repository.insertProduct(product)
            } else {
                repository.updateProduct(product)
            }
            if (supabaseSyncManager.isAutoSyncEnabled() && com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        supabaseSyncManager.syncProductsOnly(repository)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            if (supabaseSyncManager.isAutoSyncEnabled() && com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        supabaseSyncManager.deleteProductFromCloud(productId)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun saveCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            if (customer.id == 0L) {
                repository.insertCustomer(customer)
            } else {
                repository.updateCustomer(customer)
            }
            triggerAutoSync()
        }
    }

    fun recordCustomerDebtPayment(customer: CustomerEntity, amount: Long, method: String) {
        viewModelScope.launch {
            val operator = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
            repository.recordCustomerDebtPayment(customer, amount, method, operator)
            triggerAutoSync()
        }
    }

    fun saveSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            if (supplier.id == 0L) {
                repository.insertSupplier(supplier)
            } else {
                repository.updateSupplier(supplier)
            }
            triggerAutoSync()
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
            triggerAutoSync()
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
            val app = getApplication<android.app.Application>()
            val existing = repository.getShopProfileOnce()
            if (existing != null && existing.isConfigured && existing.name.isNotBlank()) {
                reloadConnectedShopsState()
                reloadRestaurantLocalState()
                resetTo(KonabScreen.DASHBOARD)
                return@launch
            }

            val currentAgent = com.example.data.remote.supabase.AgentDeploymentStorage.getSavedAgentSession(app)
                ?: return@launch

            val creationResult = withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSyncManager.createShopExclusive(
                    repository = repository,
                    shopName = shopName,
                    slogan = slogan,
                    phone = phone,
                    address = address,
                    city = city,
                    currency = "FCFA",
                    logoIcon = "storefront",
                    logoColorHex = 0xFF087A45,
                    businessType = businessType,
                    adminName = adminName,
                    adminPhone = adminPhone,
                    adminPin = adminPin,
                    preloadDemoData = preloadDemoData,
                    subscriptionTier = subscriptionTier,
                    activeAgent = currentAgent
                )
            }
            if (creationResult.isSuccess) {
                reloadConnectedShopsState()
                reloadRestaurantLocalState()
                reloadCoutureLocalState()
                resetTo(KonabScreen.DASHBOARD)
            } else {
                android.util.Log.e(
                    "KonabPOS",
                    "Échec completeOnboarding: ${creationResult.exceptionOrNull()?.message}"
                )
            }
        }
    }

    // --- MULTIBOUTIQUE : CONNEXION GÉRANT & ÉQUIPE & BASCULE ---

    fun loginToShopWithManagerCredentials(
        identifier: String,
        pin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSyncManager.loginAndHydrateShopRemote(identifier, pin, repository)
            }
            if (res.isSuccess) {
                val session = res.getOrThrow()
                _cart.value = emptyList()
                _selectedCustomer.value = null
                _discountAmount.value = 0L
                _lastCompletedSale.value = null
                reloadConnectedShopsState()
                reloadRestaurantLocalState()
                reloadCoutureLocalState()
                val staff = _activeStaff.value
                _currentRole.value = staff?.role?.name ?: "ADMIN"
                _isLocked.value = false
                if (staff != null && (staff.role == RestaurantStaffRole.KITCHEN || staff.role == RestaurantStaffRole.WAITER)) {
                    resetTo(KonabScreen.RESTAURANT)
                } else {
                    resetTo(KonabScreen.DASHBOARD)
                }
                val suffix = staff?.let { " • ${it.role.badgeLabel} (${it.name})" } ?: ""
                onResult(true, "Connecté à « ${session.shopName} »$suffix")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Identifiants gérant ou équipe incorrects.")
            }
        }
    }

    fun switchConnectedShop(
        session: ConnectedShopSession,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSyncManager.switchToConnectedShop(session, repository)
            }
            if (res.isSuccess) {
                val loaded = res.getOrThrow()
                _cart.value = emptyList()
                _selectedCustomer.value = null
                _discountAmount.value = 0L
                _lastCompletedSale.value = null
                reloadConnectedShopsState()
                reloadRestaurantLocalState()
                reloadCoutureLocalState()
                _currentRole.value = _activeStaff.value?.role?.name ?: "ADMIN"
                _isLocked.value = false
                resetTo(KonabScreen.DASHBOARD)
                onResult(true, "Boutique active : « ${loaded.shopName} »")
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Échec du changement de boutique.")
            }
        }
    }

    fun refreshActiveShopFromCloud(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSyncManager.refreshActiveShopFromCloud(repository)
            }
            reloadConnectedShopsState()
            reloadRestaurantLocalState()
            reloadCoutureLocalState()
            if (res.isSuccess) {
                val updates = res.getOrDefault(0)
                onResult(
                    true,
                    if (updates > 0) "$updates mise(s) à jour chargée(s) depuis Supabase." else "Produits et ventes déjà à jour."
                )
            } else {
                onResult(false, res.exceptionOrNull()?.localizedMessage ?: "Impossible d'actualiser depuis le Cloud.")
            }
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
                val app = getApplication<Application>()
                com.example.util.BiometricSecurityHelper.setShopConfiguredCache(app, false)
                _isLocked.value = false
                com.example.data.remote.supabase.AgentDeploymentStorage.clearCreatedShopLock(app)
                supabaseSyncManager.resetCloudShopId()
                resetTo(KonabScreen.ONBOARDING)
            }
            onComplete()
        }
    }

    fun updateShopProfile(updated: ShopProfileEntity) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val currentAgent = com.example.data.remote.supabase.AgentDeploymentStorage.getSavedAgentSession(app)
            val resolvedDeployer = updated.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
                ?: currentAgent?.id?.trim()?.uppercase()
            val finalUpdated = updated.copy(deployerId = resolvedDeployer)
            repository.saveShopProfile(finalUpdated)
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    supabaseSyncManager.pushShopProfileDirectly(finalUpdated, isNewShop = false)
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
        val app = getApplication<Application>()
        val cleanPin = enteredPin.trim()
        val profile = shopProfile.value ?: kotlinx.coroutines.runBlocking {
            repository.getShopProfileOnce()
        } ?: return cleanPin == "1234"
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        if (cleanPin == profile.adminPin.trim()) {
            val mgr = RestaurantStorage.getStaffAccounts(app, shopId)
                .firstOrNull { it.role == RestaurantStaffRole.MANAGER && it.isActive }
            RestaurantStorage.setActiveStaff(app, shopId, mgr?.id)
            _currentRole.value = "ADMIN"
            _isLocked.value = false
            reloadRestaurantLocalState()
            return true
        }
        val staffMatch = RestaurantStorage.findStaffByPin(app, shopId, cleanPin)
        if (staffMatch != null) {
            RestaurantStorage.setActiveStaff(app, shopId, staffMatch.id)
            _currentRole.value = staffMatch.role.name
            _isLocked.value = false
            reloadRestaurantLocalState()
            return true
        }
        return false
    }

    fun unlockWithBiometric() {
        _currentRole.value = "ADMIN"
        _isLocked.value = false
        reloadRestaurantLocalState()
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

    fun activateSubscriptionWithSasPay(
        tier: String,
        durationDays: Int,
        paymentId: String,
        reference: String,
        amountFcfa: Long,
        channel: String
    ): Boolean {
        val profile = shopProfile.value ?: return false
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val effectiveDays = if (tier.equals("Premium", ignoreCase = true)) 3650 else durationDays.coerceAtLeast(30)
        val baseTime = maxOf(now, profile.subscriptionExpiresAt)
        val newExpiry = baseTime + (effectiveDays.toLong() * 86_400_000L)

        val updated = profile.copy(
            subscriptionTier = tier,
            subscriptionExpiresAt = newExpiry
        )
        updateShopProfile(updated)

        try {
            val durationCode = when (effectiveDays) {
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
                note = "Paiement SasPay $channel • ID: $paymentId • Réf: $reference ($amountFcfa FCFA)"
            )
            com.example.util.LicenseHelper.saveGeneratedLicense(app, keyData)
        } catch (_: Exception) {}

        viewModelScope.launch {
            try {
                repository.insertNotification(
                    NotificationEntity(
                        title = "Réabonnement SasPay activé ($tier)",
                        message = "Paiement vérifié de $amountFcfa FCFA via $channel (Réf: $reference). Formule $tier prolongée de $effectiveDays jours.",
                        type = "SUBSCRIPTION_RENEWED",
                        priority = "HIGH",
                        actionRoute = "subscription"
                    )
                )
            } catch (_: Exception) {}
        }
        return true
    }

    fun activateSubscriptionByAgent(
        tier: String,
        durationDays: Int,
        agentName: String,
        agentPhone: String,
        notes: String
    ): Boolean {
        val app = getApplication<Application>()
        val currentAgent = com.example.data.remote.supabase.AgentDeploymentStorage.getSavedAgentSession(app)
            ?: return false
        val profile = shopProfile.value ?: return false
        val canonicalAgentId = currentAgent.id.trim().uppercase()
        val effectiveAgentName = currentAgent.name.ifBlank { agentName }
        val effectiveAgentPhone = currentAgent.phone.ifBlank { agentPhone }

        val expiry = System.currentTimeMillis() + (durationDays.toLong() * 24 * 60 * 60 * 1000)
        val updated = profile.copy(
            subscriptionTier = tier,
            subscriptionExpiresAt = expiry,
            deployerId = profile.deployerId?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: canonicalAgentId
        )
        updateShopProfile(updated)

        // Save generated license key & single deployment record tied strictly to currentAgent.id
        try {
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
                note = "Activé par agent $effectiveAgentName ($canonicalAgentId)"
            )
            com.example.util.LicenseHelper.saveGeneratedLicense(app, keyData)

            val deploymentId = "DEP-${System.currentTimeMillis()}"
            val record = com.example.data.remote.supabase.AgentDeploymentRecord(
                id = deploymentId,
                agentId = canonicalAgentId,
                agentName = effectiveAgentName,
                agentPhone = effectiveAgentPhone,
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
                if (supabaseSyncManager.isAutoSyncEnabled() && com.example.data.remote.supabase.SupabaseConfig.isConfigured) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            supabaseSyncManager.syncProductsOnly(repository)
                        } catch (_: Exception) {}
                    }
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
                triggerAutoSync()
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
                val isResto = shopProfile.value?.businessType.equals("Restaurant", ignoreCase = true)
                val isCouture = CoutureDirectoryHelper.isCoutureShop(shopProfile.value?.businessType)
                kotlinx.coroutines.delay(if (isResto || isCouture) 15_000L else 45_000L)
                if (supabaseSyncManager.isAutoSyncEnabled() && SupabaseConfig.isConfigured) {
                    try {
                        val previousById = _restaurantOrders.value.associateBy { it.id }
                        supabaseSyncManager.syncAll(repository)
                        reloadRestaurantLocalState()
                        reloadCoutureLocalState()
                        _lastRestaurantSyncAt.value = System.currentTimeMillis()
                        _lastCoutureSyncAt.value = System.currentTimeMillis()
                        if (isResto) {
                            detectAndNotifyRemoteOrderUpdates(previousById, _restaurantOrders.value)
                        }
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

    fun runSupabaseDiagnostic(onResult: (SupabaseAuthManager.DiagnosticResult) -> Unit) {
        viewModelScope.launch {
            val diag = SupabaseAuthManager.runSelfTest()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(diag)
            }
        }
    }

    // --- GESTION RESTAURANT SYNCHRONISÉE (ÉQUIPE, TABLES QR, MENU DU JOUR, CUISINE, ENCAISSEMENT) ---

    fun switchActiveStaffByPin(pin: String): RestaurantStaffAccount? {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val profile = shopProfile.value
        val cleanPin = pin.trim()
        val found = RestaurantStorage.findStaffByPin(app, shopId, cleanPin) ?: if (profile != null && cleanPin == profile.adminPin.trim()) {
            RestaurantStaffAccount(
                id = "STAFF-MGR-$shopId",
                shopId = shopId,
                name = adminAccount.value.adminName.ifBlank { "Gérant" },
                phone = adminAccount.value.adminPhone.ifBlank { profile.phone },
                role = RestaurantStaffRole.MANAGER,
                pin = profile.adminPin
            )
        } else null

        if (found != null) {
            RestaurantStorage.setActiveStaff(app, shopId, found.id)
            _activeStaff.value = found
            _currentRole.value = if (found.role == RestaurantStaffRole.MANAGER) "ADMIN" else found.role.name
            reloadRestaurantLocalState()
        }
        return found
    }

    fun switchActiveStaffDirect(staff: RestaurantStaffAccount) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        RestaurantStorage.setActiveStaff(app, shopId, staff.id)
        _activeStaff.value = staff
        _currentRole.value = if (staff.role == RestaurantStaffRole.MANAGER) "ADMIN" else staff.role.name
    }

    fun saveStaffAccount(account: RestaurantStaffAccount) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val norm = if (account.id.isBlank()) {
            account.copy(id = "STAFF-${account.role.name.take(3)}-${System.currentTimeMillis()}", shopId = shopId)
        } else {
            account.copy(shopId = shopId)
        }
        _restaurantStaff.value = RestaurantStorage.upsertStaffAccount(app, shopId, norm)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun deleteStaffAccount(staffId: String) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _restaurantStaff.value = RestaurantStorage.deleteStaffAccount(app, shopId, staffId)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.deleteRestaurantStaffCloud(staffId)
            } catch (_: Exception) {}
        }
    }

    fun saveRestaurantTable(table: RestaurantTableRecord) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val norm = if (table.id.isBlank()) {
            table.copy(id = "TBL-$shopId-${System.currentTimeMillis()}", shopId = shopId)
        } else {
            table.copy(shopId = shopId)
        }
        _restaurantTables.value = RestaurantStorage.upsertTable(app, shopId, norm)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun generateBatchTables(count: Int = 4, zone: String = "Salle Principale") {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _restaurantTables.value = RestaurantStorage.generateBatchTables(app, shopId, countToAdd = count, zone = zone)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun deleteRestaurantTable(tableId: String) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _restaurantTables.value = RestaurantStorage.deleteTable(app, shopId, tableId)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.deleteRestaurantTableCloud(tableId)
            } catch (_: Exception) {}
        }
    }

    fun toggleMenuOfDayProduct(productId: Long) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _menuOfDayProductIds.value = RestaurantStorage.toggleMenuOfDayProduct(app, shopId, productId)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun selectAllProductsForMenuOfDay() {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val ids = products.value.filter { it.isAvailable }.map { it.id }.toSet()
        RestaurantStorage.setMenuOfDayProductIds(app, shopId, ids)
        _menuOfDayProductIds.value = ids
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun clearMenuOfDay() {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        RestaurantStorage.setMenuOfDayProductIds(app, shopId, emptySet())
        _menuOfDayProductIds.value = emptySet()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantConfigNow(shopId)
            } catch (_: Exception) {}
        }
    }

    fun submitRestaurantOrder(
        tableName: String,
        customerName: String = "Client Table",
        customerPhone: String = "",
        items: List<RestaurantOrderItem>,
        notes: String = "",
        source: String = "POS",
        initialStatus: RestaurantOrderStatus = RestaurantOrderStatus.PENDING_MANAGER,
        onComplete: (RestaurantOrderRecord) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val now = System.currentTimeMillis()
        val activeOp = _activeStaff.value
        val order = RestaurantOrderRecord(
            id = "ORD-$shopId-$now-${(10..99).random()}",
            shopId = shopId,
            orderNumber = RestaurantStorage.nextOrderNumber(app, shopId),
            tableName = tableName.trim().ifBlank { "Table 1" },
            customerName = customerName.trim().ifBlank { "Client Table" },
            customerPhone = customerPhone.trim(),
            source = source,
            status = initialStatus,
            items = items,
            totalAmount = items.sumOf { it.totalPrice },
            notes = notes.trim(),
            createdByRole = activeOp?.role?.name ?: source,
            createdByName = activeOp?.name ?: customerName.ifBlank { "Client" },
            assignedToKitchenBy = if (initialStatus == RestaurantOrderStatus.SENT_TO_KITCHEN) {
                activeOp?.name ?: "Gérant"
            } else "",
            createdAt = now,
            updatedAt = now
        )
        _restaurantOrders.value = RestaurantStorage.upsertOrder(app, shopId, order)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.insertNotification(
                    NotificationEntity(
                        title = "Nouvelle commande ${order.orderNumber} (${order.tableName})",
                        message = "${order.items.sumOf { it.quantity }} plat(s) • ${order.totalAmount} ${shopProfile.value?.currency ?: "FCFA"} (${order.status.label})",
                        type = "RESTAURANT_ORDER",
                        priority = "HIGH",
                        actionRoute = "restaurant"
                    )
                )
            } catch (_: Exception) {}
            try {
                supabaseSyncManager.syncRestaurantOrderNow(shopId, order)
            } catch (_: Exception) {}
        }
        onComplete(order)
    }

    fun advanceRestaurantOrderStatus(
        order: RestaurantOrderRecord,
        newStatus: RestaurantOrderStatus
    ) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val activeOp = _activeStaff.value
        val kitchenBy = if (newStatus == RestaurantOrderStatus.SENT_TO_KITCHEN && order.assignedToKitchenBy.isBlank()) {
            activeOp?.name ?: adminAccount.value.adminName.ifBlank { "Gérant" }
        } else {
            order.assignedToKitchenBy
        }
        val updated = order.copy(
            status = newStatus,
            assignedToKitchenBy = kitchenBy,
            updatedAt = System.currentTimeMillis()
        )
        notifiedRestaurantEventKeys.add("${updated.id}:${updated.status.name}:${updated.updatedAt}")
        _restaurantOrders.value = RestaurantStorage.upsertOrder(app, shopId, updated)
        if (newStatus == RestaurantOrderStatus.READY) {
            val itemsSummary = RestaurantKitchenHelper.formatItemsSummary(updated)
            com.example.util.NotificationHelper.sendRestaurantReadyAlert(
                context = app,
                orderNumber = updated.orderNumber,
                tableName = updated.tableName,
                customerName = updated.customerName,
                itemsSummary = itemsSummary
            )
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.syncRestaurantOrderNow(shopId, updated)
            } catch (_: Exception) {}
            if (newStatus == RestaurantOrderStatus.READY) {
                try {
                    val itemsSummary = RestaurantKitchenHelper.formatItemsSummary(updated)
                    repository.insertNotification(
                        NotificationEntity(
                            title = "🔔 Prêt à servir : ${updated.tableName} (${updated.orderNumber})",
                            message = "Serveur notifié (${updated.customerName}) • $itemsSummary. À récupérer en cuisine.",
                            type = "RESTAURANT_READY",
                            priority = "HIGH",
                            actionRoute = "restaurant"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun notifyWaiterOrderReady(order: RestaurantOrderRecord) {
        val app = getApplication<Application>()
        val itemsSummary = RestaurantKitchenHelper.formatItemsSummary(order)
        com.example.util.NotificationHelper.sendRestaurantReadyAlert(
            context = app,
            orderNumber = order.orderNumber,
            tableName = order.tableName,
            customerName = order.customerName,
            itemsSummary = itemsSummary
        )
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.insertNotification(
                    NotificationEntity(
                        title = "🔔 Rappel Serveur : ${order.tableName} (${order.orderNumber})",
                        message = "Plat prêt à servir pour ${order.customerName} ($itemsSummary).",
                        type = "RESTAURANT_READY",
                        priority = "HIGH",
                        actionRoute = "restaurant"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private suspend fun detectAndNotifyRemoteOrderUpdates(
        previousById: Map<String, RestaurantOrderRecord>,
        currentOrders: List<RestaurantOrderRecord>
    ) {
        val app = getApplication<Application>()
        val curr = shopProfile.value?.currency ?: "FCFA"
        currentOrders.forEach { order ->
            val prev = previousById[order.id]
            val eventKey = "${order.id}:${order.status.name}:${order.updatedAt}"
            if (eventKey in notifiedRestaurantEventKeys) return@forEach

            if (prev == null && order.source.equals("QR_TABLE", ignoreCase = true) && order.status.isOpenOrder) {
                notifiedRestaurantEventKeys.add(eventKey)
                com.example.util.NotificationHelper.sendRestaurantQrOrderAlert(
                    context = app,
                    orderNumber = order.orderNumber,
                    tableName = order.tableName,
                    customerName = order.customerName,
                    totalAmount = order.totalAmount,
                    currency = curr
                )
                try {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "📲 Commande QR reçue : ${order.tableName} (${order.orderNumber})",
                            message = "${order.customerName} • ${RestaurantKitchenHelper.formatItemsSummary(order)}",
                            type = "RESTAURANT_QR_ORDER",
                            priority = "HIGH",
                            actionRoute = "restaurant"
                        )
                    )
                } catch (_: Exception) {}
            } else if (order.status == RestaurantOrderStatus.READY && prev != null && prev.status != RestaurantOrderStatus.READY) {
                notifiedRestaurantEventKeys.add(eventKey)
                val itemsSummary = RestaurantKitchenHelper.formatItemsSummary(order)
                com.example.util.NotificationHelper.sendRestaurantReadyAlert(
                    context = app,
                    orderNumber = order.orderNumber,
                    tableName = order.tableName,
                    customerName = order.customerName,
                    itemsSummary = itemsSummary
                )
                try {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "🔔 Prêt à servir : ${order.tableName} (${order.orderNumber})",
                            message = "Serveur notifié (${order.customerName}) • $itemsSummary.",
                            type = "RESTAURANT_READY",
                            priority = "HIGH",
                            actionRoute = "restaurant"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun pullRestaurantOrdersLive() {
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val previousById = _restaurantOrders.value.associateBy { it.id }
                if (SupabaseConfig.isConfigured && shopId > 1L) {
                    supabaseSyncManager.pullRestaurantStateNow(shopId)
                }
                reloadRestaurantLocalState()
                _lastRestaurantSyncAt.value = System.currentTimeMillis()
                detectAndNotifyRemoteOrderUpdates(previousById, _restaurantOrders.value)
            } catch (_: Exception) {}
        }
    }

    fun collectRestaurantOrderPayment(
        order: RestaurantOrderRecord,
        paymentMethod: String,
        isCredit: Boolean = false,
        notes: String = "",
        onSuccess: (SaleEntity) -> Unit
    ) {
        if (order.items.isEmpty()) return
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val localProducts = products.value
        val saleItems = order.items.map { item ->
            val matchedProduct = localProducts.firstOrNull { p ->
                p.id == item.productId ||
                    MultiShopStorage.scopedCloudId(shopId, p.id) == item.productId ||
                    p.name.equals(item.productName, ignoreCase = true)
            }
            val displayName = if (item.note.isNotBlank()) "${item.productName} [${item.note}]" else item.productName
            SaleItemEntity(
                saleId = 0L,
                productId = matchedProduct?.id ?: -kotlin.math.abs(item.productId.takeIf { it > 0L } ?: System.currentTimeMillis()),
                productName = displayName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                unitBuyPrice = matchedProduct?.buyPrice ?: 0L,
                totalPrice = item.totalPrice
            )
        }
        val saleTotal = order.items.sumOf { it.totalPrice }
        val activeOp = _activeStaff.value
        val cashier = activeOp?.name?.ifBlank { null } ?: if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"
        val combinedNotes = listOfNotNull(
            "Table: ${order.tableName} • ${order.orderNumber}",
            notes.trim().takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        viewModelScope.launch {
            val sale = repository.recordSale(
                customerId = null,
                customerName = "${order.customerName.ifBlank { "Client" }} (${order.tableName})",
                items = saleItems,
                subtotal = saleTotal,
                discountAmount = 0L,
                taxAmount = 0L,
                totalAmount = saleTotal,
                paymentMethod = paymentMethod,
                isCredit = isCredit,
                cashierName = cashier,
                notes = combinedNotes
            )
            _lastCompletedSale.value = Pair(sale, saleItems)

            val paidOrder = order.copy(
                status = RestaurantOrderStatus.PAID,
                saleId = sale.id,
                updatedAt = System.currentTimeMillis()
            )
            _restaurantOrders.value = RestaurantStorage.upsertOrder(app, shopId, paidOrder)

            val curr = shopProfile.value?.currency ?: "FCFA"
            com.example.util.NotificationHelper.sendSaleSuccess(app, sale.transactionNumber, saleTotal, curr)

            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    supabaseSyncManager.syncRestaurantOrderNow(shopId, paidOrder)
                    supabaseSyncManager.syncAll(repository)
                } catch (_: Exception) {}
            }
            onSuccess(sale)
        }
    }

    fun refreshRestaurantNow(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _isSyncingRestaurant.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val previousById = _restaurantOrders.value.associateBy { it.id }
            val pushRes = supabaseSyncManager.syncRestaurantConfigNow(shopId)
            val pullRes = supabaseSyncManager.pullRestaurantStateNow(shopId)
            reloadRestaurantLocalState()
            _lastRestaurantSyncAt.value = System.currentTimeMillis()
            detectAndNotifyRemoteOrderUpdates(previousById, _restaurantOrders.value)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isSyncingRestaurant.value = false
                if (pullRes.isSuccess) {
                    val updates = pullRes.getOrDefault(0) + pushRes.getOrDefault(0)
                    onResult(true, if (updates > 0) "Restaurant synchronisé ($updates éléments)." else "Restaurant déjà à jour.")
                } else {
                    onResult(false, pullRes.exceptionOrNull()?.localizedMessage ?: "Échec de synchronisation.")
                }
            }
        }
    }

    fun loadPublicRestaurantsDirectory() {
        _publicRestaurantsError.value = null
        val localInitial = supabaseSyncManager.getLocalPublicRestaurantsDirectory(shopProfile.value)
        if (localInitial.isNotEmpty()) {
            _publicRestaurants.value = localInitial
        }
        _isLoadingPublicRestaurants.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.fetchPublicRestaurantsDirectory(shopProfile.value)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isLoadingPublicRestaurants.value = false
                if (res.isSuccess) {
                    _publicRestaurants.value = res.getOrDefault(emptyList())
                } else if (_publicRestaurants.value.isEmpty()) {
                    _publicRestaurantsError.value = res.exceptionOrNull()?.localizedMessage
                        ?: "Impossible de charger la liste des restaurants."
                }
            }
        }
    }

    fun openRestaurantMenuDirectly(
        restaurant: PublicRestaurantListing,
        tableName: String = "Table 1"
    ) {
        val uri = RestaurantQrParser.buildTableQrUri(
            shopId = restaurant.shopId,
            shopName = restaurant.name,
            tableName = tableName
        )
        openQrMenuFromScan(uri, expectedRestaurant = restaurant)
    }

    fun openQrMenuFromScan(
        rawQr: String,
        expectedRestaurant: PublicRestaurantListing? = null
    ): Boolean {
        val directParsed = RestaurantQrParser.parseTableQrUri(rawQr)
        val parsed = when {
            directParsed != null -> directParsed
            expectedRestaurant != null -> {
                val cleanRaw = rawQr.trim()
                val detectedTable = Regex("table\\s*[#:_-]?\\s*([a-z0-9\\s-]+)", RegexOption.IGNORE_CASE)
                    .find(cleanRaw)
                    ?.value
                    ?.trim()
                    ?.take(24)
                    ?: "Table 1"
                ParsedTableQr(
                    shopId = expectedRestaurant.shopId,
                    shopName = expectedRestaurant.name,
                    tableName = detectedTable
                )
            }
            else -> return false
        }

        _pendingQrMenuTarget.value = parsed
        _qrMenuError.value = null
        _publicQrMenuBundle.value = null
        _isLoadingQrMenu.value = true

        val localShop = shopProfile.value
        val activeId = supabaseSyncManager.getOrCreateCloudShopId()
        val matchesLocalShop = localShop != null && localShop.isConfigured && (
            (parsed.shopId > 1L && parsed.shopId == activeId) ||
                (parsed.shopName.isNotBlank() && localShop.name.equals(parsed.shopName, ignoreCase = true))
            )

        if (matchesLocalShop && localShop != null && products.value.isNotEmpty()) {
            val allProds = products.value.filter { it.isAvailable }
            val menuIds = _menuOfDayProductIds.value
            val menuProds = if (menuIds.isNotEmpty()) {
                allProds.filter { it.id in menuIds }.ifEmpty { allProds }
            } else {
                allProds
            }
            _publicQrMenuBundle.value = SupabaseApiClient.PublicRestaurantMenuBundle(
                shopId = activeId,
                shopName = localShop.name,
                city = localShop.city,
                phone = localShop.phone,
                currency = localShop.currency.ifBlank { "FCFA" },
                products = menuProds,
                menuDayIds = menuIds,
                slogan = localShop.slogan,
                address = localShop.address,
                logoUri = localShop.logoUri,
                tables = _restaurantTables.value
            )
            _isLoadingQrMenu.value = false
            return true
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.fetchPublicMenuForQr(parsed)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isLoadingQrMenu.value = false
                if (res.isSuccess) {
                    val fetched = res.getOrThrow()
                    _publicQrMenuBundle.value = if (expectedRestaurant != null) {
                        fetched.copy(
                            shopName = fetched.shopName.ifBlank { expectedRestaurant.name },
                            city = fetched.city.ifBlank { expectedRestaurant.city },
                            phone = fetched.phone.ifBlank { expectedRestaurant.phone },
                            slogan = fetched.slogan.ifBlank { expectedRestaurant.slogan },
                            address = fetched.address.ifBlank { expectedRestaurant.address },
                            logoUri = fetched.logoUri ?: expectedRestaurant.logoUri
                        )
                    } else {
                        fetched
                    }
                } else {
                    _qrMenuError.value = res.exceptionOrNull()?.localizedMessage
                        ?: "Impossible de charger le menu du jour pour ce restaurant."
                }
            }
        }
        return true
    }

    fun openLocalMenuPreview(tableName: String = "Table 1") {
        val localShop = shopProfile.value ?: return
        val activeId = supabaseSyncManager.getOrCreateCloudShopId()
        val parsed = ParsedTableQr(shopId = activeId, shopName = localShop.name, tableName = tableName)
        openQrMenuFromScan(RestaurantQrParser.buildTableQrUri(activeId, localShop.name, tableName))
        _pendingQrMenuTarget.value = parsed
    }

    fun submitPublicQrOrder(
        bundle: SupabaseApiClient.PublicRestaurantMenuBundle,
        tableName: String,
        customerName: String,
        customerPhone: String,
        items: List<RestaurantOrderItem>,
        notes: String,
        onResult: (Boolean, RestaurantOrderRecord?, String) -> Unit
    ) {
        if (items.isEmpty()) {
            onResult(false, null, "Sélectionnez au moins un plat du menu.")
            return
        }
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val targetShopId = bundle.shopId
        val activeLocalId = supabaseSyncManager.getOrCreateCloudShopId()
        val isSameActiveShop = targetShopId == activeLocalId ||
            (shopProfile.value?.name?.equals(bundle.shopName, ignoreCase = true) == true)

        val order = RestaurantOrderRecord(
            id = "ORD-$targetShopId-$now-${(10..99).random()}",
            shopId = targetShopId,
            orderNumber = if (isSameActiveShop) RestaurantStorage.nextOrderNumber(app, activeLocalId) else "CMD-${(now % 1000).toString().padStart(3, '0')}",
            tableName = tableName.trim().ifBlank { "Table 1" },
            customerName = customerName.trim().ifBlank { "Client Table" },
            customerPhone = customerPhone.trim(),
            source = "QR_TABLE",
            status = RestaurantOrderStatus.PENDING_MANAGER,
            items = items,
            totalAmount = items.sumOf { it.totalPrice },
            notes = notes.trim(),
            createdByRole = "CLIENT_QR",
            createdByName = customerName.trim().ifBlank { "Client ${tableName}" },
            createdAt = now,
            updatedAt = now
        )

        if (isSameActiveShop) {
            _restaurantOrders.value = RestaurantStorage.upsertOrder(app, activeLocalId, order)
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val remoteRes = if (SupabaseConfig.isConfigured && targetShopId > 1L) {
                supabaseSyncManager.submitPublicQrOrder(targetShopId, order)
            } else if (isSameActiveShop) {
                Result.success(1)
            } else {
                Result.failure(IllegalStateException("Connexion Supabase requise."))
            }

            if (isSameActiveShop) {
                notifiedRestaurantEventKeys.add("${order.id}:${order.status.name}:${order.updatedAt}")
                com.example.util.NotificationHelper.sendRestaurantQrOrderAlert(
                    context = app,
                    orderNumber = order.orderNumber,
                    tableName = order.tableName,
                    customerName = order.customerName,
                    totalAmount = order.totalAmount,
                    currency = bundle.currency
                )
                try {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Commande Table QR : ${order.orderNumber} (${order.tableName})",
                            message = "${order.customerName} a commandé ${order.items.sumOf { it.quantity }} plat(s) (${order.totalAmount} ${bundle.currency}). Visible en Cuisine.",
                            type = "RESTAURANT_QR_ORDER",
                            priority = "HIGH",
                            actionRoute = "restaurant"
                        )
                    )
                } catch (_: Exception) {}
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (remoteRes.isSuccess) {
                    onResult(true, order, "Commande ${order.orderNumber} envoyée au gérant !")
                } else {
                    if (isSameActiveShop) {
                        onResult(true, order, "Commande ${order.orderNumber} enregistrée localement pour le gérant !")
                    } else {
                        onResult(false, null, remoteRes.exceptionOrNull()?.localizedMessage ?: "Échec d'envoi de la commande.")
                    }
                }
            }
        }
    }

    fun dismissQrMenu() {
        _pendingQrMenuTarget.value = null
        _publicQrMenuBundle.value = null
        _isLoadingQrMenu.value = false
        _qrMenuError.value = null
    }

    // --- SECTEUR COUTURE : MODÈLES, MESURES, PHOTOS TISSUS, QR CATALOGUE & SUIVI CLIENT ---

    fun saveCoutureModel(model: CoutureModelRecord, onComplete: () -> Unit = {}) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shopName = shopProfile.value?.name.orEmpty()
        val now = System.currentTimeMillis()
        val norm = if (model.id.isBlank()) {
            model.copy(
                id = "MOD-$shopId-$now",
                shopId = shopId,
                shopName = model.shopName.ifBlank { shopName },
                updatedAt = now
            )
        } else {
            model.copy(
                shopId = shopId,
                shopName = model.shopName.ifBlank { shopName },
                updatedAt = now
            )
        }
        _coutureModels.value = CoutureStorage.upsertModel(app, shopId, norm)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val synced = supabaseSyncManager.syncCoutureModelNow(shopId, norm, shopName)
                synced.getOrNull()?.let { updated ->
                    _coutureModels.value = CoutureStorage.upsertModel(app, shopId, updated)
                }
            } catch (_: Exception) {}
        }
        onComplete()
    }

    fun deleteCoutureModel(modelId: String) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        _coutureModels.value = CoutureStorage.deleteModel(app, shopId, modelId)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                supabaseSyncManager.deleteCoutureModelCloud(modelId)
            } catch (_: Exception) {}
        }
    }

    fun saveCoutureOrder(order: CoutureOrderRecord, onComplete: (CoutureOrderRecord) -> Unit = {}) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shopName = shopProfile.value?.name.orEmpty()
        val now = System.currentTimeMillis()
        val norm = if (order.id.isBlank()) {
            order.copy(
                id = "CTRO-$shopId-$now-${(10..99).random()}",
                shopId = shopId,
                shopName = order.shopName.ifBlank { shopName },
                orderNumber = order.orderNumber.ifBlank { CoutureStorage.nextOrderNumber(app, shopId) },
                createdAt = now,
                updatedAt = now
            )
        } else {
            order.copy(
                shopId = shopId,
                shopName = order.shopName.ifBlank { shopName },
                orderNumber = order.orderNumber.ifBlank { CoutureStorage.nextOrderNumber(app, shopId) },
                updatedAt = now
            )
        }
        _coutureOrders.value = CoutureStorage.upsertOrder(app, shopId, norm)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Auto-enregistrer le client dans le répertoire clients Konab s'il est nouveau
            try {
                val cleanName = norm.fullCustomerName.trim()
                val cleanPhone = CoutureDirectoryHelper.normalizePhone(norm.customerPhone)
                if (cleanName.isNotBlank() && cleanName != "Client Atelier") {
                    val existing = repository.getAllCustomersOnce().firstOrNull { cust ->
                        (cleanPhone.isNotBlank() && CoutureDirectoryHelper.normalizePhone(cust.phone) == cleanPhone) ||
                            cust.name.equals(cleanName, ignoreCase = true)
                    }
                    if (existing == null) {
                        repository.insertCustomer(
                            CustomerEntity(
                                name = cleanName,
                                phone = norm.customerPhone.trim(),
                                notes = "Client Atelier Couture (${norm.orderNumber})"
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            try {
                val synced = supabaseSyncManager.syncCoutureOrderNow(shopId, norm, shopName)
                synced.getOrNull()?.let { updated ->
                    _coutureOrders.value = CoutureStorage.upsertOrder(app, shopId, updated)
                }
            } catch (_: Exception) {}
        }
        onComplete(norm)
    }

    fun advanceCoutureOrderStage(order: CoutureOrderRecord, targetStage: CoutureStage? = null) {
        val next = targetStage ?: order.status.nextStage() ?: return
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shopName = shopProfile.value?.name.orEmpty()
        val updated = order.copy(
            status = next,
            updatedAt = System.currentTimeMillis()
        )
        _coutureOrders.value = CoutureStorage.upsertOrder(app, shopId, updated)

        if (next == CoutureStage.ESSAYAGE_PRET) {
            com.example.util.NotificationHelper.sendCustomNotification(
                context = app,
                title = "✂️ Tenue prête : ${updated.fullCustomerName}",
                message = "${updated.modelTitle} (${updated.orderNumber}) est prête pour l'essayage / récupération."
            )
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (next == CoutureStage.ESSAYAGE_PRET) {
                try {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "Tenue prête : ${updated.fullCustomerName} (${updated.orderNumber})",
                            message = "${updated.modelTitle} terminée. Solde à encaisser : ${updated.remainingBalance} ${shopProfile.value?.currency ?: "FCFA"}.",
                            type = "COUTURE_READY",
                            priority = "HIGH",
                            actionRoute = "couture"
                        )
                    )
                } catch (_: Exception) {}
            }
            try {
                val synced = supabaseSyncManager.syncCoutureOrderNow(shopId, updated, shopName)
                synced.getOrNull()?.let { cloudOrder ->
                    _coutureOrders.value = CoutureStorage.upsertOrder(app, shopId, cloudOrder)
                }
            } catch (_: Exception) {}
        }
    }

    fun collectCoutureOrderPayment(
        order: CoutureOrderRecord,
        paymentMethod: String,
        notes: String = "",
        onSuccess: (SaleEntity) -> Unit
    ) {
        val app = getApplication<Application>()
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shopName = shopProfile.value?.name.orEmpty()
        val amountToCollect = order.remainingBalance.takeIf { it > 0L } ?: order.totalPrice.coerceAtLeast(1L)
        val curr = shopProfile.value?.currency ?: "FCFA"
        val cashier = if (_currentRole.value == "ADMIN") "Gérant" else "Caissier"

        val saleItem = SaleItemEntity(
            saleId = 0L,
            productId = -kotlin.math.abs(order.id.hashCode().toLong()).coerceAtMost(-1L),
            productName = "Couture : ${order.modelTitle} (${order.orderNumber})",
            quantity = 1,
            unitPrice = amountToCollect,
            unitBuyPrice = 0L,
            totalPrice = amountToCollect
        )
        val combinedNotes = listOfNotNull(
            "Atelier Couture • ${order.orderNumber} • Client: ${order.fullCustomerName}",
            if (order.advancePaid > 0L) "Avance déjà versée: ${order.advancePaid} $curr" else null,
            notes.trim().takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        viewModelScope.launch {
            val matchedCustomer = customers.value.firstOrNull { c ->
                CoutureDirectoryHelper.normalizePhone(c.phone).isNotBlank() &&
                    CoutureDirectoryHelper.normalizePhone(c.phone) == CoutureDirectoryHelper.normalizePhone(order.customerPhone)
            }
            val sale = repository.recordSale(
                customerId = matchedCustomer?.id,
                customerName = order.fullCustomerName,
                items = listOf(saleItem),
                subtotal = amountToCollect,
                discountAmount = 0L,
                taxAmount = 0L,
                totalAmount = amountToCollect,
                paymentMethod = paymentMethod,
                isCredit = false,
                cashierName = cashier,
                notes = combinedNotes
            )
            _lastCompletedSale.value = Pair(sale, listOf(saleItem))

            val settledOrder = order.copy(
                status = CoutureStage.LIVRE_ENCAISSE,
                advancePaid = maxOf(order.totalPrice, order.advancePaid + amountToCollect),
                saleId = sale.id,
                updatedAt = System.currentTimeMillis()
            )
            _coutureOrders.value = CoutureStorage.upsertOrder(app, shopId, settledOrder)
            com.example.util.NotificationHelper.sendSaleSuccess(app, sale.transactionNumber, amountToCollect, curr)

            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    supabaseSyncManager.syncCoutureOrderNow(shopId, settledOrder, shopName)
                    supabaseSyncManager.syncAll(repository)
                } catch (_: Exception) {}
            }
            onSuccess(sale)
        }
    }

    fun refreshCoutureNow(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        val shopName = shopProfile.value?.name.orEmpty()
        _isSyncingCouture.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pushRes = supabaseSyncManager.syncAllCoutureConfigNow(shopId, shopName)
            val pullRes = supabaseSyncManager.pullCoutureStateNow(shopId)
            reloadCoutureLocalState()
            _lastCoutureSyncAt.value = System.currentTimeMillis()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isSyncingCouture.value = false
                if (pullRes.isSuccess) {
                    val total = pullRes.getOrDefault(0) + pushRes.getOrDefault(0)
                    onResult(true, if (total > 0) "Atelier Couture synchronisé ($total éléments)." else "Atelier Couture à jour.")
                } else {
                    onResult(false, pullRes.exceptionOrNull()?.localizedMessage ?: "Échec de synchronisation.")
                }
            }
        }
    }

    fun pullCoutureOrdersLive() {
        val shopId = supabaseSyncManager.getOrCreateCloudShopId()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (SupabaseConfig.isConfigured && shopId > 1L) {
                    supabaseSyncManager.pullCoutureStateNow(shopId)
                }
                reloadCoutureLocalState()
                _lastCoutureSyncAt.value = System.currentTimeMillis()
            } catch (_: Exception) {}
        }
    }

    // --- ROUTAGE QR UNIFIÉ (RESTAURANT OU COUTURE) & PORTAIL CLIENT COUTURE ---

    fun openAnyQrScan(rawQr: String): Boolean {
        if (CoutureQrParser.parseCoutureQrUri(rawQr) != null) {
            return openCoutureQrFromScan(rawQr)
        }
        if (RestaurantQrParser.parseTableQrUri(rawQr) != null) {
            return openQrMenuFromScan(rawQr)
        }
        return false
    }

    fun openCoutureCatalogDirectly(couturier: PublicCouturierListing, initialModelId: String? = null) {
        val uri = CoutureQrParser.buildCatalogQrUri(
            shopId = couturier.shopId,
            shopName = couturier.name,
            modelId = initialModelId
        )
        openCoutureQrFromScan(uri, expectedShop = couturier)
    }

    fun openLocalCoutureCatalogPreview() {
        val localShop = shopProfile.value ?: return
        val activeId = supabaseSyncManager.getOrCreateCloudShopId()
        openCoutureQrFromScan(
            CoutureQrParser.buildCatalogQrUri(
                shopId = activeId,
                shopName = localShop.name
            )
        )
    }

    fun openCoutureQrFromScan(
        rawQr: String,
        expectedShop: PublicCouturierListing? = null
    ): Boolean {
        val directParsed = CoutureQrParser.parseCoutureQrUri(rawQr)
        val parsed = when {
            directParsed != null -> directParsed
            expectedShop != null -> ParsedCoutureQr(
                shopId = expectedShop.shopId,
                shopName = expectedShop.name
            )
            else -> return false
        }

        _pendingCoutureQrTarget.value = parsed
        _coutureCatalogError.value = null
        _publicCoutureBundle.value = null
        _isLoadingCoutureCatalog.value = true

        val localShop = shopProfile.value
        val activeId = supabaseSyncManager.getOrCreateCloudShopId()
        val matchesLocal = localShop != null && localShop.isConfigured && (
            (parsed.shopId > 1L && parsed.shopId == activeId) ||
                (parsed.shopName.isNotBlank() && localShop.name.equals(parsed.shopName, ignoreCase = true))
            )

        if (matchesLocal && localShop != null) {
            val localModels = CoutureStorage.ensureDefaultModelsForAtelier(
                context = getApplication(),
                shopId = activeId,
                shopName = localShop.name
            ).filter { it.isAvailable }
            _publicCoutureBundle.value = SupabaseApiClient.PublicCoutureCatalogBundle(
                shopId = activeId,
                shopName = localShop.name,
                city = localShop.city,
                phone = localShop.phone,
                currency = localShop.currency.ifBlank { "FCFA" },
                models = localModels,
                slogan = localShop.slogan,
                address = localShop.address,
                logoUri = localShop.logoUri
            )
            _isLoadingCoutureCatalog.value = false
            return true
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.fetchPublicCoutureCatalogForQr(parsed)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isLoadingCoutureCatalog.value = false
                if (res.isSuccess) {
                    val fetched = res.getOrThrow()
                    val enriched = if (expectedShop != null) {
                        fetched.copy(
                            shopName = fetched.shopName.ifBlank { expectedShop.name },
                            city = fetched.city.ifBlank { expectedShop.city },
                            phone = fetched.phone.ifBlank { expectedShop.phone },
                            slogan = fetched.slogan.ifBlank { expectedShop.slogan },
                            address = fetched.address.ifBlank { expectedShop.address },
                            logoUri = fetched.logoUri ?: expectedShop.logoUri
                        )
                    } else fetched
                    _publicCoutureBundle.value = enriched
                } else {
                    _coutureCatalogError.value = res.exceptionOrNull()?.localizedMessage
                        ?: "Impossible de charger le catalogue de ce couturier."
                }
            }
        }
        return true
    }

    fun dismissCoutureCatalog() {
        _pendingCoutureQrTarget.value = null
        _publicCoutureBundle.value = null
        _isLoadingCoutureCatalog.value = false
        _coutureCatalogError.value = null
    }

    fun loadPublicCoutureDirectory() {
        val localShops = supabaseSyncManager.getLocalPublicCoutureDirectory(shopProfile.value)
        if (localShops.isNotEmpty()) {
            _publicCouturiers.value = localShops
        }
        val localModels = CoutureStorage.getAllLocalModels(getApplication())
        if (localModels.isNotEmpty()) {
            _publicCoutureModelsFeed.value = localModels
        }
        _isLoadingCoutureDirectory.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dirRes = supabaseSyncManager.fetchPublicCoutureDirectory(shopProfile.value)
            val modelsRes = supabaseSyncManager.fetchPublicCoutureModelsFeed()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isLoadingCoutureDirectory.value = false
                if (dirRes.isSuccess) {
                    _publicCouturiers.value = dirRes.getOrDefault(emptyList())
                }
                if (modelsRes.isSuccess) {
                    _publicCoutureModelsFeed.value = modelsRes.getOrDefault(emptyList())
                }
            }
        }
    }

    fun registerOrLoginCoutureClient(
        firstName: String,
        lastName: String,
        phone: String,
        city: String = "",
        onDone: (CoutureClientAccount) -> Unit = {}
    ) {
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanPhone = phone.trim()
        if (cleanFirst.isBlank() || cleanPhone.isBlank()) return
        val account = CoutureClientAccount(
            id = "CLI-${CoutureDirectoryHelper.normalizePhone(cleanPhone).ifBlank { System.currentTimeMillis().toString() }}",
            firstName = cleanFirst,
            lastName = cleanLast,
            phone = cleanPhone,
            city = city.trim()
        )
        CoutureStorage.saveClientAccount(getApplication(), account)
        _activeCoutureClient.value = account
        refreshClientCoutureProgress(overridePhone = cleanPhone, overrideName = account.fullName)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            supabaseSyncManager.registerCoutureClientAccount(account)
        }
        onDone(account)
    }

    fun logoutCoutureClient() {
        CoutureStorage.clearClientAccount(getApplication())
        _activeCoutureClient.value = null
        _clientCoutureOrders.value = emptyList()
    }

    fun refreshClientCoutureProgress(overridePhone: String? = null, overrideName: String? = null) {
        val client = _activeCoutureClient.value
        val phone = overridePhone?.trim()?.takeIf { it.isNotBlank() } ?: client?.phone.orEmpty()
        val name = overrideName?.trim()?.takeIf { it.isNotBlank() } ?: client?.fullName
        if (phone.isBlank() && name.isNullOrBlank()) return
        _isLoadingClientCoutureOrders.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = supabaseSyncManager.fetchClientCoutureProgress(phone, name)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                _isLoadingClientCoutureOrders.value = false
                if (res.isSuccess) {
                    _clientCoutureOrders.value = res.getOrDefault(emptyList())
                }
            }
        }
    }

    fun submitPublicCoutureRequest(
        bundle: SupabaseApiClient.PublicCoutureCatalogBundle,
        chosenModel: CoutureModelRecord?,
        isCustomModel: Boolean,
        customModelLabel: String,
        firstName: String,
        lastName: String,
        phone: String,
        clientNotes: String,
        onResult: (Boolean, CoutureOrderRecord?, String) -> Unit
    ) {
        val cleanFirst = firstName.trim()
        val cleanLast = lastName.trim()
        val cleanPhone = phone.trim()
        if (cleanFirst.isBlank() || cleanPhone.isBlank()) {
            onResult(false, null, "Renseignez votre prénom et votre numéro de téléphone.")
            return
        }
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val targetShopId = bundle.shopId
        val activeLocalId = supabaseSyncManager.getOrCreateCloudShopId()
        val isSameActiveShop = targetShopId == activeLocalId ||
            (shopProfile.value?.name?.equals(bundle.shopName, ignoreCase = true) == true)

        val modelTitle = if (isCustomModel || chosenModel == null) {
            customModelLabel.trim().ifBlank { "Modèle apporté par le client" }
        } else {
            chosenModel.title
        }

        val order = CoutureOrderRecord(
            id = "CTRO-$targetShopId-$now-${(10..99).random()}",
            shopId = targetShopId,
            shopName = bundle.shopName,
            orderNumber = if (isSameActiveShop) CoutureStorage.nextOrderNumber(app, activeLocalId) else "CTR-${(now % 1000).toString().padStart(3, '0')}",
            customerFirstName = cleanFirst,
            customerLastName = cleanLast,
            customerPhone = cleanPhone,
            modelId = chosenModel?.id.orEmpty(),
            modelTitle = modelTitle,
            modelImageUri = chosenModel?.imageUri ?: "img_prod_mode",
            isCustomClientModel = isCustomModel || chosenModel == null,
            fabricNotes = clientNotes.trim(),
            status = CoutureStage.DEMANDE_CLIENT,
            totalPrice = chosenModel?.price ?: 0L,
            tailorNotes = if (isCustomModel) "Le client viendra avec son propre modèle." else "",
            createdAt = now,
            updatedAt = now
        )

        // Sauvegarder automatiquement le compte client pour suivi immédiat
        registerOrLoginCoutureClient(cleanFirst, cleanLast, cleanPhone)

        if (isSameActiveShop) {
            _coutureOrders.value = CoutureStorage.upsertOrder(app, activeLocalId, order)
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val remoteRes = if (SupabaseConfig.isConfigured && targetShopId > 1L) {
                supabaseSyncManager.submitPublicCoutureOrderRequest(targetShopId, order)
            } else if (isSameActiveShop) {
                Result.success(1)
            } else {
                Result.failure(IllegalStateException("Connexion Supabase requise pour contacter l'atelier."))
            }

            if (isSameActiveShop) {
                try {
                    repository.insertNotification(
                        NotificationEntity(
                            title = "✂️ Modèle choisi : ${order.fullCustomerName}",
                            message = "${order.modelTitle} (${order.orderNumber}) • Tél: ${order.customerPhone}. Prêt pour prise de mesures.",
                            type = "COUTURE_REQUEST",
                            priority = "HIGH",
                            actionRoute = "couture"
                        )
                    )
                } catch (_: Exception) {}
            }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                refreshClientCoutureProgress(overridePhone = cleanPhone, overrideName = order.fullCustomerName)
                if (remoteRes.isSuccess) {
                    onResult(true, order, "Demande ${order.orderNumber} transmise à ${bundle.shopName} !")
                } else if (isSameActiveShop) {
                    onResult(true, order, "Demande ${order.orderNumber} enregistrée pour le couturier !")
                } else {
                    onResult(false, null, remoteRes.exceptionOrNull()?.localizedMessage ?: "Échec d'envoi au couturier.")
                }
            }
        }
    }
}
