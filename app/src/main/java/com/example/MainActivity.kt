package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.KonabBottomBar
import com.example.ui.components.PinLockDialog
import com.example.ui.navigation.KonabScreen
import com.example.ui.screens.*
import com.example.ui.theme.KonabPOSTheme
import com.example.ui.viewmodel.KonabViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KonabPOSTheme {
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

                val showBottomBar = currentScreen !in listOf(
                    KonabScreen.ONBOARDING,
                    KonabScreen.PAYMENT,
                    KonabScreen.RECEIPT,
                    KonabScreen.AGENT_PORTAL
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (showBottomBar) {
                                KonabBottomBar(
                                    currentScreen = currentScreen,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    cartItemCount = cart.sumOf { it.quantity }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                KonabScreen.ONBOARDING -> {
                                    SetupWizard(
                                        shopProfile = shopProfile,
                                        onEnterShop = {
                                            viewModel.resetTo(KonabScreen.DASHBOARD)
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
                                        notifications = notifications,
                                        unreadNotificationsCount = unreadNotificationsCount,
                                        onMarkNotificationAsRead = { viewModel.markNotificationAsRead(it) },
                                        onMarkAllNotificationsAsRead = { viewModel.markAllNotificationsAsRead() },
                                        onDeleteNotification = { viewModel.deleteNotification(it) },
                                        onClearAllNotifications = { viewModel.deleteAllNotifications() },
                                        onTriggerTestNotification = { viewModel.triggerTestNotification(it) }
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
                                        onUpdateQuantity = { id, delta -> viewModel.updateCartQuantity(id, delta) },
                                        onUpdateItemNote = { id, note -> viewModel.updateCartItemNote(id, note) },
                                        onUpdateItemDiscount = { id, disc -> viewModel.updateCartItemDiscount(id, disc) },
                                        onRemoveFromCart = { viewModel.removeFromCart(it) },
                                        onClearCart = { viewModel.clearCart() },
                                        onSelectCustomer = { viewModel.selectCustomer(it) },
                                        onSetDiscount = { viewModel.setDiscount(it) },
                                        onProceedToPayment = { viewModel.navigateTo(KonabScreen.PAYMENT) },
                                        onBack = { viewModel.navigateBack() },
                                        shopProfile = shopProfile
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
                                        onBack = { viewModel.navigateBack() }
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
                                        onBack = { viewModel.navigateBack() }
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
                            }
                        }
                    }

                    // Security PIN Lock Overlay
                    if (isLocked) {
                        PinLockDialog(
                            onUnlock = { enteredPin ->
                                viewModel.unlockWithPin(enteredPin)
                            }
                        )
                    }
                }
            }
        }
    }
}
