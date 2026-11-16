package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.CoutureDirectoryHelper
import com.example.data.remote.supabase.CoutureQrParser
import com.example.data.remote.supabase.RestaurantOrderItem
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.TontineStorage
import com.example.data.remote.supabase.WasteCollectorStorage
import com.example.ui.components.CustomerCoutureCatalogModal
import com.example.ui.components.CustomerQrMenuModal
import com.example.ui.components.KonabBottomBar
import com.example.ui.components.KonabNavigationRail
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.PinLockDialog
import com.example.ui.components.PublicCouturePortalModal
import com.example.ui.components.PublicRestaurantDirectoryModal
import com.example.ui.components.PublicUserPortalModal
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.components.TontinePortalModal
import com.example.ui.components.WasteCollectorPortalModal
import com.example.ui.components.widthClassFromDp
import com.example.ui.navigation.KonabScreen
import com.example.ui.screens.*
import com.example.ui.theme.KonabPOSTheme
import com.example.ui.viewmodel.KonabViewModel
import com.example.util.BusinessTypePresets

class MainActivity : FragmentActivity() {
    private val incomingQrUriState = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingQrUriState.value = intent.dataString
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingQrUriState.value = intent?.dataString
        enableEdgeToEdge()
        setContent {
            KonabPOSTheme {
                val context = LocalContext.current
                val viewModel: KonabViewModel = viewModel()

                val shopProfile by viewModel.shopProfile.collectAsStateWithLifecycle()
                val products by viewModel.products.collectAsStateWithLifecycle()
                val customers by viewModel.customers.collectAsStateWithLifecycle()
                val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
                val recentSales by viewModel.recentSales.collectAsStateWithLifecycle()
                val allSales by viewModel.allSales.collectAsStateWithLifecycle()
                val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
                val cashMovements by viewModel.cashMovements.collectAsStateWithLifecycle()
                val activeCashSession by viewModel.activeCashSession.collectAsStateWithLifecycle()
                val allPurchases by viewModel.allPurchases.collectAsStateWithLifecycle()
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val cart by viewModel.cart.collectAsStateWithLifecycle()
                val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
                val discountAmount by viewModel.discountAmount.collectAsStateWithLifecycle()
                val lastCompletedSale by viewModel.lastCompletedSale.collectAsStateWithLifecycle()
                val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
                val supabaseSyncState by viewModel.supabaseSyncState.collectAsStateWithLifecycle()
                val supabaseAuthState by viewModel.supabaseAuthState.collectAsStateWithLifecycle()
                val notifications by viewModel.notifications.collectAsStateWithLifecycle()
                val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
                val connectedShops by viewModel.connectedShops.collectAsStateWithLifecycle()
                val activeCloudShopId by viewModel.activeCloudShopId.collectAsStateWithLifecycle()
                val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()

                // Synchronized Restaurant State
                val restaurantStaff by viewModel.restaurantStaff.collectAsStateWithLifecycle()
                val activeStaff by viewModel.activeStaff.collectAsStateWithLifecycle()
                val activeOperatorLabel = remember(activeStaff, currentRole) {
                    activeStaff?.name?.ifBlank { null }
                        ?: if (currentRole.equals("ADMIN", ignoreCase = true) ||
                            currentRole.equals("MANAGER", ignoreCase = true)
                        ) "Gérant" else "Caissier"
                }
                val restaurantTables by viewModel.restaurantTables.collectAsStateWithLifecycle()
                val restaurantOrders by viewModel.restaurantOrders.collectAsStateWithLifecycle()
                val menuOfDayProductIds by viewModel.menuOfDayProductIds.collectAsStateWithLifecycle()
                val isSyncingRestaurant by viewModel.isSyncingRestaurant.collectAsStateWithLifecycle()
                val pendingQrMenuTarget by viewModel.pendingQrMenuTarget.collectAsStateWithLifecycle()
                val publicQrMenuBundle by viewModel.publicQrMenuBundle.collectAsStateWithLifecycle()
                val isLoadingQrMenu by viewModel.isLoadingQrMenu.collectAsStateWithLifecycle()
                val qrMenuError by viewModel.qrMenuError.collectAsStateWithLifecycle()
                val publicRestaurants by viewModel.publicRestaurants.collectAsStateWithLifecycle()
                val isLoadingPublicRestaurants by viewModel.isLoadingPublicRestaurants.collectAsStateWithLifecycle()
                val publicRestaurantsError by viewModel.publicRestaurantsError.collectAsStateWithLifecycle()

                // Synchronized Couture State
                val coutureModels by viewModel.coutureModels.collectAsStateWithLifecycle()
                val coutureOrders by viewModel.coutureOrders.collectAsStateWithLifecycle()
                val isSyncingCouture by viewModel.isSyncingCouture.collectAsStateWithLifecycle()
                val lastCoutureSyncAt by viewModel.lastCoutureSyncAt.collectAsStateWithLifecycle()
                val pendingCoutureQrTarget by viewModel.pendingCoutureQrTarget.collectAsStateWithLifecycle()
                val publicCoutureBundle by viewModel.publicCoutureBundle.collectAsStateWithLifecycle()
                val isLoadingCoutureCatalog by viewModel.isLoadingCoutureCatalog.collectAsStateWithLifecycle()
                val coutureCatalogError by viewModel.coutureCatalogError.collectAsStateWithLifecycle()
                val publicCouturiers by viewModel.publicCouturiers.collectAsStateWithLifecycle()
                val publicCoutureModelsFeed by viewModel.publicCoutureModelsFeed.collectAsStateWithLifecycle()
                val isLoadingCoutureDirectory by viewModel.isLoadingCoutureDirectory.collectAsStateWithLifecycle()
                val activeCoutureClient by viewModel.activeCoutureClient.collectAsStateWithLifecycle()
                val clientCoutureOrders by viewModel.clientCoutureOrders.collectAsStateWithLifecycle()
                val isLoadingClientCoutureOrders by viewModel.isLoadingClientCoutureOrders.collectAsStateWithLifecycle()

                // Synchronized Waste Collector Portal State
                val wasteBusinesses by viewModel.wasteBusinesses.collectAsStateWithLifecycle()
                val wasteClients by viewModel.wasteClients.collectAsStateWithLifecycle()
                val activeWasteBusinessId by viewModel.activeWasteBusinessId.collectAsStateWithLifecycle()
                val isSyncingWaste by viewModel.isSyncingWaste.collectAsStateWithLifecycle()
                val pendingWasteQrTarget by viewModel.pendingWasteQrTarget.collectAsStateWithLifecycle()
                val wasteFamilyQuery by viewModel.wasteFamilyQuery.collectAsStateWithLifecycle()

                // Synchronized Tontine 30-Day Portal State
                val tontineBusinesses by viewModel.tontineBusinesses.collectAsStateWithLifecycle()
                val tontineGroups by viewModel.tontineGroups.collectAsStateWithLifecycle()
                val tontineCards by viewModel.tontineCards.collectAsStateWithLifecycle()
                val activeTontineBusinessId by viewModel.activeTontineBusinessId.collectAsStateWithLifecycle()
                val isSyncingTontine by viewModel.isSyncingTontine.collectAsStateWithLifecycle()
                val pendingTontineQrTarget by viewModel.pendingTontineQrTarget.collectAsStateWithLifecycle()
                val tontineMemberQuery by viewModel.tontineMemberQuery.collectAsStateWithLifecycle()

                // Public User Identification State
                val currentPublicUser by viewModel.currentPublicUser.collectAsStateWithLifecycle()
                val isSyncingPublicUser by viewModel.isSyncingPublicUser.collectAsStateWithLifecycle()

                var showPublicRestaurantDirectory by remember { mutableStateOf(false) }
                var showPublicCouturePortal by remember { mutableStateOf(false) }
                var showWastePortal by remember { mutableStateOf(false) }
                var showTontinePortal by remember { mutableStateOf(false) }
                var showUserPortal by remember { mutableStateOf(false) }
                var showGlobalQrScanner by remember { mutableStateOf(false) }
                var agentLoginTrigger by remember { mutableIntStateOf(0) }

                val activeDeployerAgent = remember(currentScreen, agentLoginTrigger, showWastePortal, showTontinePortal) {
                    AgentDeploymentStorage.getSavedAgentSession(context)
                }

                LaunchedEffect(pendingWasteQrTarget) {
                    if (pendingWasteQrTarget != null) {
                        showWastePortal = true
                    }
                }
                LaunchedEffect(pendingTontineQrTarget) {
                    if (pendingTontineQrTarget != null) {
                        showTontinePortal = true
                    }
                }

                val isRestaurantMode = remember(shopProfile?.businessType) {
                    val bType = shopProfile?.businessType.orEmpty()
                    bType.equals("Restaurant", ignoreCase = true) ||
                        BusinessTypePresets.getPreset(bType).sectorFeatures.hasTables
                }
                val isCoutureMode = remember(shopProfile?.businessType) {
                    CoutureDirectoryHelper.isCoutureShop(shopProfile?.businessType)
                }
                val openRestaurantOrdersCount = remember(restaurantOrders) {
                    restaurantOrders.count { it.status.isOpenOrder }
                }
                val openCoutureOrdersCount = remember(coutureOrders) {
                    coutureOrders.count { it.status.isOpenWork }
                }

                val pendingIncomingUri by incomingQrUriState
                LaunchedEffect(pendingIncomingUri) {
                    val uri = pendingIncomingUri
                    if (!uri.isNullOrBlank()) {
                        viewModel.openAnyQrScan(uri)
                        incomingQrUriState.value = null
                    }
                }

                // Request POST_NOTIFICATIONS on Android 13+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { /* granted */ }

                    LaunchedEffect(Unit) {
                        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val totalAmount = viewModel.totalAmount
                val subtotal = viewModel.subtotal

                // Handle Hardware and Gesture Back Button
                BackHandler(enabled = currentScreen != KonabScreen.DASHBOARD && currentScreen != KonabScreen.ONBOARDING) {
                    viewModel.navigateBack()
                }

                val showChromeNav = currentScreen !in listOf(
                    KonabScreen.ONBOARDING,
                    KonabScreen.PAYMENT,
                    KonabScreen.RECEIPT,
                    KonabScreen.AGENT_PORTAL
                )

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val widthClass = widthClassFromDp(maxWidth)
                    val useNavigationRail = showChromeNav && widthClass != KonabWidthClass.COMPACT
                    val showBottomBar = showChromeNav && widthClass == KonabWidthClass.COMPACT

                    CompositionLocalProvider(LocalKonabWidthClass provides widthClass) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                contentWindowInsets = WindowInsets.safeDrawing,
                                bottomBar = {
                                    if (showBottomBar) {
                                        KonabBottomBar(
                                            currentScreen = currentScreen,
                                            onNavigate = { viewModel.navigateTo(it) },
                                            cartItemCount = cart.sumOf { it.quantity },
                                            isRestaurantMode = isRestaurantMode,
                                            openRestaurantOrdersCount = openRestaurantOrdersCount,
                                            isCoutureMode = isCoutureMode,
                                            openCoutureOrdersCount = openCoutureOrdersCount,
                                            currentRole = currentRole
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    if (useNavigationRail) {
                                        KonabNavigationRail(
                                            currentScreen = currentScreen,
                                            onNavigate = { viewModel.navigateTo(it) },
                                            cartItemCount = cart.sumOf { it.quantity },
                                            isRestaurantMode = isRestaurantMode,
                                            openRestaurantOrdersCount = openRestaurantOrdersCount,
                                            isCoutureMode = isCoutureMode,
                                            openCoutureOrdersCount = openCoutureOrdersCount,
                                            currentRole = currentRole
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        AnimatedContent(
                                            targetState = currentScreen,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = tween(220)) +
                                                    scaleIn(initialScale = 0.985f, animationSpec = tween(220)) +
                                                    slideInHorizontally(animationSpec = tween(220)) { width -> (width * 0.04f).toInt() })
                                                    .togetherWith(fadeOut(animationSpec = tween(150)))
                                            },
                                            label = "konab_screen_transition"
                                        ) { screen ->
                                            when (screen) {
                                KonabScreen.ONBOARDING -> {
                                    SetupWizard(
                                        shopProfile = shopProfile,
                                        connectedShops = connectedShops,
                                        activeCloudShopId = activeCloudShopId,
                                        currentPublicUser = currentPublicUser,
                                        openAgentLoginTrigger = agentLoginTrigger,
                                        onConnectMultiShopRemote = { id, pin, cb ->
                                            viewModel.loginToShopWithManagerCredentials(id, pin, cb)
                                        },
                                        onSwitchConnectedShop = { session, cb ->
                                            viewModel.switchConnectedShop(session, cb)
                                        },
                                        onScanTableQr = { rawQr ->
                                            if (!viewModel.openAnyQrScan(rawQr)) {
                                                Toast.makeText(
                                                    context,
                                                    "QR code non reconnu (Restaurant, Couture, Poubelles ou Tontine Konab).",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        onOpenUserPortal = {
                                            showUserPortal = true
                                        },
                                        onOpenRestaurantDirectory = {
                                            viewModel.loadPublicRestaurantsDirectory()
                                            showPublicRestaurantDirectory = true
                                        },
                                        onOpenCouturePortal = {
                                            viewModel.loadPublicCoutureDirectory()
                                            viewModel.refreshClientCoutureProgress()
                                            showPublicCouturePortal = true
                                        },
                                        onOpenWastePortal = {
                                            viewModel.refreshWastePortalFromCloud()
                                            showWastePortal = true
                                        },
                                        onOpenTontinePortal = {
                                            viewModel.refreshTontinePortalFromCloud()
                                            showTontinePortal = true
                                        },
                                        wasteClientsCount = wasteClients.size,
                                        tontineCardsCount = tontineCards.size,
                                        onEnterShop = {
                                            viewModel.reloadRestaurantLocalState()
                                            viewModel.reloadCoutureLocalState()
                                            viewModel.syncRoleFromActiveStaff()
                                            val staff = viewModel.activeStaff.value
                                            val isResto = isRestaurantMode
                                            if (staff != null && (staff.role == com.example.data.remote.supabase.RestaurantStaffRole.KITCHEN ||
                                                    staff.role == com.example.data.remote.supabase.RestaurantStaffRole.WAITER ||
                                                    (isResto && staff.role == com.example.data.remote.supabase.RestaurantStaffRole.CASHIER))
                                            ) {
                                                viewModel.resetTo(KonabScreen.RESTAURANT)
                                            } else {
                                                viewModel.resetTo(KonabScreen.DASHBOARD)
                                            }
                                            viewModel.refreshActiveShopFromCloud()
                                        },
                                        onComplete = { name, slogan, phone, address, city, currency, logoIcon, type, adminName, adminPhone, pin, preloadDemo, subscriptionTier ->
                                            viewModel.completeOnboarding(
                                                shopName = name,
                                                slogan = slogan,
                                                phone = phone,
                                                address = address,
                                                city = city,
                                                businessType = type,
                                                adminName = adminName,
                                                adminPhone = adminPhone,
                                                adminPin = pin,
                                                preloadDemoData = preloadDemo,
                                                subscriptionTier = subscriptionTier
                                            )
                                        }
                                    )
                                }

                                KonabScreen.DASHBOARD -> {
                                    DashboardScreen(
                                        shopProfile = shopProfile,
                                        sales = allSales,
                                        products = products,
                                        cashMovements = cashMovements,
                                        onNavigate = { viewModel.navigateTo(it) },
                                        onViewSaleReceipt = { viewModel.viewSaleReceipt(it) },
                                        onLockApp = { viewModel.lockApp() },
                                        onAddToCart = { viewModel.addToCart(it) },
                                        activeCashSession = activeCashSession,
                                        customers = customers,
                                        onReturnToWelcome = { viewModel.resetTo(KonabScreen.ONBOARDING) },
                                        notifications = notifications,
                                        unreadNotificationsCount = unreadNotificationsCount,
                                        onMarkNotificationAsRead = { viewModel.markNotificationAsRead(it) },
                                        onMarkAllNotificationsAsRead = { viewModel.markAllNotificationsAsRead() },
                                        onDeleteNotification = { viewModel.deleteNotification(it) },
                                        onClearAllNotifications = { viewModel.deleteAllNotifications() },
                                        onTriggerTestNotification = { viewModel.triggerTestNotification(it) },
                                        connectedShops = connectedShops,
                                        activeCloudShopId = activeCloudShopId,
                                        onConnectMultiShopRemote = { id, pin, cb ->
                                            viewModel.loginToShopWithManagerCredentials(id, pin, cb)
                                        },
                                        onSwitchConnectedShop = { session, cb ->
                                            viewModel.switchConnectedShop(session, cb)
                                        },
                                        onRefreshActiveShopFromCloud = { cb ->
                                            viewModel.refreshActiveShopFromCloud(cb)
                                        },
                                        restaurantOrders = restaurantOrders,
                                        activeStaff = activeStaff,
                                        onTableQrScanned = { rawQr -> viewModel.openAnyQrScan(rawQr) },
                                        coutureOrders = coutureOrders,
                                        coutureModelsCount = coutureModels.size
                                    )
                                }

                                KonabScreen.POS -> {
                                    PosScreen(
                                        products = products,
                                        customers = customers,
                                        cart = cart,
                                        selectedCustomer = selectedCustomer,
                                        discountAmount = discountAmount,
                                        subtotal = subtotal,
                                        totalAmount = totalAmount,
                                        onAddToCart = { viewModel.addToCart(it) },
                                        onAddToCartWithVariant = { prod, note -> viewModel.addToCartWithVariant(prod, note) },
                                        onAddCustomAmount = { title, price, qty, unit, note ->
                                            viewModel.addCustomAmountToCart(title, price, qty, unit, note)
                                        },
                                        onQuickSaveProduct = { viewModel.saveProduct(it) },
                                        onUpdateQuantity = { id, delta -> viewModel.updateCartQuantity(id, delta) },
                                        onUpdateItemNote = { id, note -> viewModel.updateCartItemNote(id, note) },
                                        onUpdateItemDiscount = { id, disc -> viewModel.updateCartItemDiscount(id, disc) },
                                        onRemoveFromCart = { viewModel.removeFromCart(it) },
                                        onClearCart = { viewModel.clearCart() },
                                        onSelectCustomer = { viewModel.selectCustomer(it) },
                                        onSetDiscount = { viewModel.setDiscount(it) },
                                        onProceedToPayment = { viewModel.navigateTo(KonabScreen.PAYMENT) },
                                        onBack = { viewModel.navigateBack() },
                                        shopProfile = shopProfile,
                                        restaurantTables = restaurantTables,
                                        activeStaff = activeStaff,
                                        openRestaurantOrdersCount = openRestaurantOrdersCount,
                                        onSubmitRestaurantOrder = { tableName, serverName, notes, sendDirectlyToKitchen ->
                                            val orderItems = cart.map { item ->
                                                RestaurantOrderItem(
                                                    productId = item.product.id,
                                                    productName = item.product.name,
                                                    unitPrice = item.unitFinalPrice,
                                                    quantity = item.quantity,
                                                    note = item.variantNote
                                                )
                                            }
                                            viewModel.submitRestaurantOrder(
                                                tableName = tableName,
                                                customerName = selectedCustomer?.name ?: "Client $tableName",
                                                customerPhone = selectedCustomer?.phone.orEmpty(),
                                                items = orderItems,
                                                notes = listOfNotNull(
                                                    notes.takeIf { it.isNotBlank() },
                                                    serverName.takeIf { it.isNotBlank() }?.let { "Serveur: $it" }
                                                ).joinToString(" • "),
                                                source = "POS",
                                                initialStatus = if (sendDirectlyToKitchen) {
                                                    RestaurantOrderStatus.SENT_TO_KITCHEN
                                                } else {
                                                    RestaurantOrderStatus.PENDING_MANAGER
                                                },
                                                onComplete = {
                                                    viewModel.clearCart()
                                                    viewModel.navigateTo(KonabScreen.RESTAURANT)
                                                }
                                            )
                                        },
                                        onOpenRestaurantScreen = { viewModel.navigateTo(KonabScreen.RESTAURANT) }
                                    )
                                }

                                KonabScreen.PAYMENT -> {
                                    PaymentScreen(
                                        shopProfile = shopProfile,
                                        totalAmount = totalAmount,
                                        selectedCustomer = selectedCustomer,
                                        lastCompletedSale = lastCompletedSale,
                                        onConfirmPayment = { method, isCredit, notes ->
                                            viewModel.processSale(
                                                paymentMethod = method,
                                                isCredit = isCredit,
                                                notes = notes,
                                                onSuccess = { /* sale processed */ }
                                            )
                                        },
                                        onViewReceipt = { viewModel.navigateTo(KonabScreen.RECEIPT) },
                                        onNewSale = { viewModel.resetTo(KonabScreen.POS) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.RECEIPT -> {
                                    ReceiptViewScreen(
                                        shopProfile = shopProfile,
                                        sale = lastCompletedSale?.first,
                                        items = lastCompletedSale?.second ?: emptyList(),
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.STOCK -> {
                                    StockScreen(
                                        products = products,
                                        shopProfile = shopProfile,
                                        onSaveProduct = { viewModel.saveProduct(it) },
                                        onDeleteProduct = { viewModel.deleteProduct(it) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.CUSTOMERS -> {
                                    CustomersScreen(
                                        customers = customers,
                                        onSaveCustomer = { viewModel.saveCustomer(it) },
                                        onPayDebt = { customer, amount, method ->
                                            viewModel.recordCustomerDebtPayment(customer, amount, method)
                                        },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.SUPPLIERS -> {
                                    SuppliersScreen(
                                        suppliers = suppliers,
                                        purchases = allPurchases,
                                        products = products,
                                        onSaveSupplier = { viewModel.saveSupplier(it) },
                                        onRecordPurchase = { supplier, product, quantity, unitCost, isPaidFromCash, notes ->
                                            viewModel.recordPurchase(supplier, product, quantity, unitCost, isPaidFromCash, notes)
                                        },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.CASH -> {
                                    CashScreen(
                                        activeSession = activeCashSession,
                                        cashMovements = cashMovements,
                                        onOpenSession = { viewModel.openCashSession(it) },
                                        onCloseSession = { actual, notes -> viewModel.closeCashSession(actual, notes) },
                                        onAddMovement = { type, amount, reason -> viewModel.addCashMovement(type, amount, reason) },
                                        onBack = { viewModel.navigateBack() },
                                        currentRole = currentRole,
                                        activeOperatorLabel = activeOperatorLabel,
                                        onRefreshCash = { viewModel.refreshLedgerNow() }
                                    )
                                }

                                KonabScreen.REPORTS -> {
                                    ReportsScreen(
                                        sales = allSales,
                                        saleItems = allSaleItems,
                                        products = products,
                                        cashMovements = cashMovements,
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.TRANSACTIONS -> {
                                    TransactionsScreen(
                                        sales = allSales,
                                        saleItems = allSaleItems,
                                        shopProfile = shopProfile,
                                        onViewSaleReceipt = { viewModel.viewSaleReceipt(it) },
                                        onCancelSale = { saleId, reason, onResult ->
                                            viewModel.cancelSale(saleId, reason, onResult)
                                        },
                                        onBack = { viewModel.navigateBack() },
                                        currentRole = currentRole,
                                        activeOperatorLabel = activeOperatorLabel,
                                        onRefreshTransactions = { viewModel.refreshLedgerNow() }
                                    )
                                }

                                KonabScreen.SETTINGS -> {
                                    SettingsScreen(
                                        shopProfile = shopProfile,
                                        onUpdateProfile = { viewModel.updateShopProfile(it) },
                                        onNavigate = { viewModel.navigateTo(it) },
                                        onLockApp = { viewModel.lockApp() },
                                        onBackup = { ctx, onRes -> viewModel.backupDatabase(ctx, onRes) },
                                        onRestore = { json, onRes -> viewModel.restoreDatabase(json, onRes) },
                                        onClearDemoData = { keepProfile, onResult ->
                                            viewModel.clearAllDemoData(keepProfile, onResult)
                                        },
                                        supabaseSyncState = supabaseSyncState,
                                        onSyncWithSupabase = { viewModel.syncWithSupabase() },
                                        onSyncFullStore = { viewModel.syncFullStoreWithSupabase() },
                                        onToggleAutoSync = { viewModel.setSupabaseAutoSyncEnabled(it) },
                                        onTestSupabase = { url, key -> viewModel.testSupabaseConnection(url, key) { _, _ -> } },
                                        onSaveSupabaseCredentials = { url, key -> viewModel.saveSupabaseCredentials(url, key) },
                                        supabaseAuthState = supabaseAuthState,
                                        onSignInSupabase = { email, pass -> viewModel.signInSupabase(email, pass) { _, _ -> } },
                                        onSignUpSupabase = { email, pass, name, phone, role, shopName ->
                                            viewModel.signUpSupabase(email, pass, name, phone, role, shopName) { _, _ -> }
                                        },
                                        onSignOutSupabase = { viewModel.signOutSupabase {} },
                                        onRecoverSupabasePassword = { email -> viewModel.recoverSupabasePassword(email) { _, _ -> } },
                                        onRefreshSupabaseSession = { viewModel.refreshSupabaseSession { _, _ -> } },
                                        onSwitchSector = { newType, reload -> viewModel.switchShopSector(newType, reload) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.SUBSCRIPTION -> {
                                    SubscriptionScreen(
                                        shopProfile = shopProfile,
                                        onActivate = { code, tier -> viewModel.activateSubscription(code, tier) },
                                        onActivateWithSasPay = { tier, days, paymentId, reference, amountFcfa, channel ->
                                            viewModel.activateSubscriptionWithSasPay(tier, days, paymentId, reference, amountFcfa, channel)
                                        },
                                        onNavigateToAgentPortal = { viewModel.navigateTo(KonabScreen.AGENT_PORTAL) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.AGENT_PORTAL -> {
                                    AgentPortalScreen(
                                        shopProfile = shopProfile,
                                        onUpdateProfile = { viewModel.updateShopProfile(it) },
                                        onActivateByAgent = { tier, days, name, phone, notes ->
                                            viewModel.activateSubscriptionByAgent(tier, days, name, phone, notes)
                                        },
                                        onPreloadCatalogue = { type, onComp ->
                                            viewModel.preloadCatalogueForAgent(type, onComp)
                                        },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.RESTAURANT -> {
                                    val lastRestaurantSyncAt by viewModel.lastRestaurantSyncAt.collectAsStateWithLifecycle()
                                    RestaurantScreen(
                                        shopProfile = shopProfile,
                                        cloudShopId = activeCloudShopId,
                                        products = products,
                                        staffList = restaurantStaff,
                                        activeStaff = activeStaff,
                                        tables = restaurantTables,
                                        orders = restaurantOrders,
                                        menuOfDayProductIds = menuOfDayProductIds,
                                        isSyncingRestaurant = isSyncingRestaurant,
                                        lastRestaurantSyncAt = lastRestaurantSyncAt,
                                        onSwitchActiveStaffByPin = { pin -> viewModel.switchActiveStaffByPin(pin) },
                                        onSwitchActiveStaffDirect = { staff -> viewModel.switchActiveStaffDirect(staff) },
                                        onSaveStaffAccount = { account -> viewModel.saveStaffAccount(account) },
                                        onDeleteStaffAccount = { staffId -> viewModel.deleteStaffAccount(staffId) },
                                        onSaveTable = { table -> viewModel.saveRestaurantTable(table) },
                                        onGenerateBatchTables = { count, zone -> viewModel.generateBatchTables(count, zone) },
                                        onDeleteTable = { tableId -> viewModel.deleteRestaurantTable(tableId) },
                                        onToggleMenuOfDayProduct = { id -> viewModel.toggleMenuOfDayProduct(id) },
                                        onSelectAllMenuOfDay = { viewModel.selectAllProductsForMenuOfDay() },
                                        onClearMenuOfDay = { viewModel.clearMenuOfDay() },
                                        onAdvanceOrderStatus = { order, newStatus ->
                                            viewModel.advanceRestaurantOrderStatus(order, newStatus)
                                        },
                                        onNotifyWaiterAgain = { order ->
                                            viewModel.notifyWaiterOrderReady(order)
                                        },
                                        onCollectOrderPayment = { order, method, isCredit, notes, onSuccess ->
                                            viewModel.collectRestaurantOrderPayment(order, method, isCredit, notes, onSuccess)
                                        },
                                        onViewSaleReceiptById = { saleId, order ->
                                            viewModel.viewSaleReceiptById(saleId, order)
                                        },
                                        onRefreshRestaurant = { cb -> viewModel.refreshRestaurantNow(cb) },
                                        onLivePollOrders = { viewModel.pullRestaurantOrdersLive() },
                                        onOpenQrScanResult = { rawQr -> viewModel.openQrMenuFromScan(rawQr) },
                                        onOpenLocalMenuPreview = { tableName -> viewModel.openLocalMenuPreview(tableName) },
                                        onOpenPosForOrder = { viewModel.navigateTo(KonabScreen.POS) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }

                                KonabScreen.COUTURE -> {
                                    CoutureScreen(
                                        shopProfile = shopProfile,
                                        cloudShopId = activeCloudShopId,
                                        models = coutureModels,
                                        orders = coutureOrders,
                                        products = products,
                                        customers = customers,
                                        isSyncingCouture = isSyncingCouture,
                                        lastCoutureSyncAt = lastCoutureSyncAt,
                                        onSaveModel = { model -> viewModel.saveCoutureModel(model) },
                                        onDeleteModel = { modelId -> viewModel.deleteCoutureModel(modelId) },
                                        onSaveOrder = { order -> viewModel.saveCoutureOrder(order) },
                                        onAdvanceOrderStage = { order, target ->
                                            viewModel.advanceCoutureOrderStage(order, target)
                                        },
                                        onCollectOrderPayment = { order, method, notes, onSuccess ->
                                            viewModel.collectCoutureOrderPayment(order, method, notes, onSuccess)
                                        },
                                        onRefreshCouture = { cb -> viewModel.refreshCoutureNow(cb) },
                                        onLivePollCouture = { viewModel.pullCoutureOrdersLive() },
                                        onOpenQrScanResult = { rawQr -> viewModel.openAnyQrScan(rawQr) },
                                        onOpenLocalCatalogPreview = { viewModel.openLocalCoutureCatalogPreview() },
                                        onOpenStockScreen = { viewModel.navigateTo(KonabScreen.STOCK) },
                                        onBack = { viewModel.navigateBack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Security Manager PIN & Biometric Lock Overlay
            if (isLocked) {
                PinLockDialog(
                    shopName = shopProfile?.name.orEmpty(),
                    expectedPinLength = (shopProfile?.adminPin?.trim()?.length ?: 4).coerceIn(4, 8),
                    onUnlock = { enteredPin ->
                        viewModel.unlockWithPin(enteredPin)
                    },
                    onBiometricUnlock = {
                        viewModel.unlockWithBiometric()
                    }
                )
            }

            // Public Registered Restaurants Directory Overlay (accessible from Welcome screen)
            if (showPublicRestaurantDirectory) {
                PublicRestaurantDirectoryModal(
                    restaurants = publicRestaurants,
                    isLoading = isLoadingPublicRestaurants,
                    errorMessage = publicRestaurantsError,
                    onRefresh = { viewModel.loadPublicRestaurantsDirectory() },
                    onScanAnyQr = { rawQr ->
                        if (viewModel.openQrMenuFromScan(rawQr)) {
                            showPublicRestaurantDirectory = false
                        } else {
                            Toast.makeText(
                                context,
                                "Ce QR code n'est pas un Menu Restaurant Konab.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onScanTableQrForRestaurant = { rawQr, restaurant ->
                        if (viewModel.openQrMenuFromScan(rawQr, expectedRestaurant = restaurant)) {
                            showPublicRestaurantDirectory = false
                        } else {
                            Toast.makeText(
                                context,
                                "Impossible de lire ce QR code pour ${restaurant.name}.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onOpenRestaurantMenu = { restaurant ->
                        viewModel.openRestaurantMenuDirectly(restaurant)
                        showPublicRestaurantDirectory = false
                    },
                    onDismiss = { showPublicRestaurantDirectory = false }
                )
            }

            // Customer Table QR Menu du Jour Overlay (opened from QR scan, directory or deep link)
            pendingQrMenuTarget?.let { qrTarget ->
                CustomerQrMenuModal(
                    target = qrTarget,
                    bundle = publicQrMenuBundle,
                    isLoading = isLoadingQrMenu,
                    errorMessage = qrMenuError,
                    defaultCustomerName = currentPublicUser?.fullName.orEmpty(),
                    defaultCustomerPhone = currentPublicUser?.phone.orEmpty(),
                    onRetry = {
                        viewModel.openQrMenuFromScan(
                            com.example.data.remote.supabase.RestaurantQrParser.buildTableQrUri(
                                shopId = qrTarget.shopId,
                                shopName = qrTarget.shopName,
                                tableName = qrTarget.tableName
                            )
                        )
                    },
                    onSubmitOrder = { bundle, tableName, custName, custPhone, items, notes, onResult ->
                        viewModel.submitPublicQrOrder(
                            bundle = bundle,
                            tableName = tableName,
                            customerName = custName,
                            customerPhone = custPhone,
                            items = items,
                            notes = notes,
                            onResult = onResult
                        )
                    },
                    onDismiss = { viewModel.dismissQrMenu() }
                )
            }

            // Public Couture Directory, User Account & Client Progress Tracker Overlay
            if (showPublicCouturePortal) {
                PublicCouturePortalModal(
                    couturiers = publicCouturiers,
                    modelsFeed = publicCoutureModelsFeed,
                    isLoadingDirectory = isLoadingCoutureDirectory,
                    activeClient = activeCoutureClient,
                    clientOrders = clientCoutureOrders,
                    isLoadingClientOrders = isLoadingClientCoutureOrders,
                    onRefreshDirectory = { viewModel.loadPublicCoutureDirectory() },
                    onRegisterOrLoginClient = { first, last, phone, city ->
                        viewModel.registerOrLoginCoutureClient(first, last, phone, city)
                    },
                    onLogoutClient = { viewModel.logoutCoutureClient() },
                    onRefreshClientProgress = { overridePhone ->
                        viewModel.refreshClientCoutureProgress(overridePhone = overridePhone)
                    },
                    onOpenCouturierCatalog = { couturier, modelId ->
                        viewModel.openCoutureCatalogDirectly(couturier, initialModelId = modelId)
                    },
                    onScanAnyQr = { rawQr ->
                        if (!viewModel.openAnyQrScan(rawQr)) {
                            Toast.makeText(
                                context,
                                "QR non reconnu pour le catalogue couture.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onDismiss = { showPublicCouturePortal = false }
                )
            }

            // Customer Couture QR Model Catalog Overlay
            pendingCoutureQrTarget?.let { qrTarget ->
                CustomerCoutureCatalogModal(
                    target = qrTarget,
                    bundle = publicCoutureBundle,
                    isLoading = isLoadingCoutureCatalog,
                    errorMessage = coutureCatalogError,
                    activeClient = activeCoutureClient,
                    currentPublicUser = currentPublicUser,
                    onRetry = {
                        viewModel.openCoutureQrFromScan(
                            CoutureQrParser.buildCatalogQrUri(
                                shopId = qrTarget.shopId,
                                shopName = qrTarget.shopName,
                                modelId = qrTarget.modelId
                            )
                        )
                    },
                    onSubmitRequest = { bundle, chosenModel, isCustom, customLabel, first, last, phone, notes, onResult ->
                        viewModel.submitPublicCoutureRequest(
                            bundle = bundle,
                            chosenModel = chosenModel,
                            isCustomModel = isCustom,
                            customModelLabel = customLabel,
                            firstName = first,
                            lastName = last,
                            phone = phone,
                            clientNotes = notes,
                            onResult = onResult
                        )
                    },
                    onOpenMyProgressTracker = {
                        viewModel.loadPublicCoutureDirectory()
                        viewModel.refreshClientCoutureProgress()
                        showPublicCouturePortal = true
                    },
                    onDismiss = { viewModel.dismissCoutureCatalog() }
                )
            }

            // Waste Collector & Family Portal Overlay (Gestion Poubelles)
            if (showWastePortal) {
                WasteCollectorPortalModal(
                    businesses = wasteBusinesses,
                    clients = wasteClients,
                    activeBusinessId = activeWasteBusinessId,
                    isSyncing = isSyncingWaste,
                    familyQuery = wasteFamilyQuery,
                    pendingQrTarget = pendingWasteQrTarget,
                    activeAgent = activeDeployerAgent,
                    currentPublicUser = currentPublicUser,
                    onRequestAgentLogin = {
                        showWastePortal = false
                        agentLoginTrigger++
                    },
                    onRefreshCloud = { viewModel.refreshWastePortalFromCloud() },
                    onSelectBusiness = { viewModel.selectWasteBusiness(it) },
                    onSaveBusiness = { name, manager, phone, city, pin, fee, passages, existingId, photoUri, onDone ->
                        viewModel.saveWasteBusiness(
                            businessName = name,
                            managerName = manager,
                            phone = phone,
                            city = city,
                            pin = pin,
                            defaultFee = fee,
                            defaultPassages = passages,
                            existingId = existingId,
                            photoUri = photoUri,
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() },
                            onResult = onDone
                        )
                    },
                    onSaveClient = { biz, famName, contact, phone, hood, status, days, fee, targetPassages, existing, photoUri, onDone ->
                        viewModel.saveWasteClient(
                            business = biz,
                            familyName = famName,
                            contactName = contact,
                            phone = phone,
                            neighborhood = hood,
                            status = status,
                            pickupDaysLabel = days,
                            monthlyFee = fee,
                            targetPassages = targetPassages,
                            existingClient = existing,
                            photoUri = photoUri,
                            onResult = onDone
                        )
                    },
                    onUpdateClientPhoto = { client, newPhoto ->
                        viewModel.updateWasteClientPhoto(client, newPhoto)
                    },
                    onCheckPickup = { client, collector, note ->
                        viewModel.checkWastePickup(client, collector, note)
                    },
                    onUncheckLastPickup = { client ->
                        viewModel.uncheckLastWastePickup(client)
                    },
                    onMarkBillingNotified = { client ->
                        viewModel.markWasteBillingNotified(client)
                    },
                    onRecordPayment = { client, amount, note ->
                        viewModel.recordWasteCyclePayment(client, amount, note)
                    },
                    onDeleteClient = { id ->
                        viewModel.deleteWasteClient(id)
                    },
                    onUpdateFamilyQuery = { q ->
                        viewModel.setWasteFamilyQuery(q)
                    },
                    onScanWasteQr = { rawQr ->
                        viewModel.openWasteQrFromScan(rawQr)
                    },
                    onDismissQrTarget = { viewModel.dismissWasteQrTarget() },
                    onDismiss = {
                        viewModel.dismissWasteQrTarget()
                        showWastePortal = false
                    }
                )
            }

            // Digital 30-Day Tontine Portal Overlay
            if (showTontinePortal) {
                TontinePortalModal(
                    businesses = tontineBusinesses,
                    groups = tontineGroups,
                    cards = tontineCards,
                    activeBusinessId = activeTontineBusinessId,
                    isSyncing = isSyncingTontine,
                    memberQuery = tontineMemberQuery,
                    pendingQrTarget = pendingTontineQrTarget,
                    activeAgent = activeDeployerAgent,
                    currentPublicUser = currentPublicUser,
                    onRequestAgentLogin = {
                        showTontinePortal = false
                        agentLoginTrigger++
                    },
                    onRefreshCloud = { viewModel.refreshTontinePortalFromCloud() },
                    onSelectBusiness = { viewModel.selectTontineBusiness(it) },
                    onSaveBusiness = { bizName, organizer, phone, city, pin, existingId, photoUri, onDone ->
                        viewModel.saveTontineBusiness(
                            businessName = bizName,
                            organizerName = organizer,
                            phone = phone,
                            city = city,
                            pin = pin,
                            existingId = existingId,
                            photoUri = photoUri,
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() },
                            onResult = onDone
                        )
                    },
                    onSaveGroup = { biz, title, dailyAmt, days, freq, existingId, onDone ->
                        viewModel.saveTontineGroup(biz, title, dailyAmt, days, freq, existingId, onDone)
                    },
                    onSaveMemberCard = { grp, mName, mPhone, turn, existing, photoUri, onDone ->
                        viewModel.saveTontineMemberCard(
                            group = grp,
                            memberName = mName,
                            memberPhone = mPhone,
                            payoutTurnNumber = turn,
                            existingCard = existing,
                            photoUri = photoUri,
                            onResult = onDone
                        )
                    },
                    onUpdateCardPhoto = { card, newPhoto ->
                        viewModel.updateTontineCardPhoto(card, newPhoto)
                    },
                    onToggleDayCheck = { card, day, checkedBy ->
                        viewModel.toggleTontineDayCheck(card, day, checkedBy)
                    },
                    onCheckNextDay = { card, checkedBy ->
                        viewModel.checkNextTontineDay(card, checkedBy)
                    },
                    onValidateSettled = { card, msg ->
                        viewModel.validateTontineCardSettled(card, msg)
                    },
                    onMarkPaid = { card ->
                        viewModel.markTontineCardPaid(card)
                    },
                    onRestartNewCycle = { card ->
                        viewModel.restartNewTontineCycle(card)
                    },
                    onDeleteCard = { id ->
                        viewModel.deleteTontineCard(id)
                    },
                    onUpdateMemberQuery = { q ->
                        viewModel.setTontineMemberQuery(q)
                    },
                    onScanTontineQr = { rawQr ->
                        viewModel.openTontineQrFromScan(rawQr)
                    },
                    onDismissQrTarget = { viewModel.dismissTontineQrTarget() },
                    onDismiss = {
                        viewModel.dismissTontineQrTarget()
                        showTontinePortal = false
                    }
                )
            }

            // Public User Identification, Supabase Account & Activity Card Overlay
            if (showUserPortal) {
                val userWasteMatches = remember(wasteClients, currentPublicUser?.phone) {
                    val q = currentPublicUser?.phone.orEmpty()
                    if (q.isBlank()) 0 else WasteCollectorStorage.findClientsForFamily(wasteClients, q).size
                }
                val userTontineMatches = remember(tontineCards, currentPublicUser?.phone) {
                    val q = currentPublicUser?.phone.orEmpty()
                    if (q.isBlank()) 0 else TontineStorage.findCardsForMember(tontineCards, q).size
                }
                PublicUserPortalModal(
                    activeUser = currentPublicUser,
                    isSyncing = isSyncingPublicUser,
                    wasteCardsCount = userWasteMatches,
                    tontineCardsCount = userTontineMatches,
                    onRegisterUser = { fullName, phone, city, hood, pin, photoUri, activity, onResult ->
                        viewModel.registerPublicUser(fullName, phone, city, hood, pin, photoUri, activity, onResult)
                    },
                    onLoginUser = { phoneOrId, pin, onResult ->
                        viewModel.loginPublicUser(phoneOrId, pin, onResult)
                    },
                    onUpdatePhoto = { photo ->
                        viewModel.updatePublicUserPhoto(photo)
                    },
                    onUpdatePreferredTheme = { theme ->
                        viewModel.updatePublicUserPreferredActivity(theme.name)
                    },
                    onLogoutUser = {
                        viewModel.logoutPublicUser()
                    },
                    onOpenRestaurants = {
                        showUserPortal = false
                        viewModel.loadPublicRestaurantsDirectory()
                        showPublicRestaurantDirectory = true
                    },
                    onOpenCouture = {
                        showUserPortal = false
                        viewModel.loadPublicCoutureDirectory()
                        viewModel.refreshClientCoutureProgress()
                        showPublicCouturePortal = true
                    },
                    onOpenWastePortal = {
                        showUserPortal = false
                        viewModel.refreshWastePortalFromCloud()
                        showWastePortal = true
                    },
                    onOpenTontinePortal = {
                        showUserPortal = false
                        viewModel.refreshTontinePortalFromCloud()
                        showTontinePortal = true
                    },
                    onOpenScanner = {
                        showUserPortal = false
                        showGlobalQrScanner = true
                    },
                    onDismiss = { showUserPortal = false }
                )
            }

            if (showGlobalQrScanner) {
                RealBarcodeScannerModal(
                    title = "Scanner QR Universel Konab",
                    subtitle = "Cantine, Restaurant, Couture, Carte Poubelle ou Tontine 30j",
                    onDismiss = { showGlobalQrScanner = false },
                    onBarcodeScanned = { scanned ->
                        showGlobalQrScanner = false
                        if (!viewModel.openAnyQrScan(scanned)) {
                            Toast.makeText(
                                context,
                                "QR code non reconnu sur Konab POS.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
                        }
                    }
                }
            }
        }
    }
}
