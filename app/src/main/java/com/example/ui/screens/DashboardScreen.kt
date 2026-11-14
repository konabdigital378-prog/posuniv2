package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.ConnectedShopSession
import com.example.ui.components.DailySalesChartCard
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabLivePulseDot
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.MultiShopManagerModal
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.StatCard
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import java.util.Calendar

@Composable
fun DashboardScreen(
    shopProfile: ShopProfileEntity?,
    sales: List<SaleEntity>,
    products: List<ProductEntity>,
    cashMovements: List<com.example.data.local.entity.CashMovementEntity>,
    onNavigate: (KonabScreen) -> Unit,
    onViewSaleReceipt: (SaleEntity) -> Unit,
    onLockApp: () -> Unit,
    onAddToCart: ((ProductEntity) -> Unit)? = null,
    activeCashSession: com.example.data.local.entity.CashSessionEntity? = null,
    customers: List<com.example.data.local.entity.CustomerEntity> = emptyList(),
    onReturnToWelcome: (() -> Unit)? = null,
    notifications: List<com.example.data.local.entity.NotificationEntity> = emptyList(),
    unreadNotificationsCount: Int = 0,
    onMarkNotificationAsRead: (Long) -> Unit = {},
    onMarkAllNotificationsAsRead: () -> Unit = {},
    onDeleteNotification: (Long) -> Unit = {},
    onClearAllNotifications: () -> Unit = {},
    onTriggerTestNotification: (String) -> Unit = {},
    connectedShops: List<ConnectedShopSession> = emptyList(),
    activeCloudShopId: Long = 0L,
    onConnectMultiShopRemote: (identifier: String, pin: String, onDone: (Boolean, String) -> Unit) -> Unit = { _, _, cb -> cb(false, "Indisponible") },
    onSwitchConnectedShop: (ConnectedShopSession, onDone: (Boolean, String) -> Unit) -> Unit = { _, cb -> cb(false, "Indisponible") },
    onRefreshActiveShopFromCloud: ((onDone: (Boolean, String) -> Unit) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Barcode scanner modal state
    var showScannerModal by remember { mutableStateOf(false) }
    var showNotificationModal by remember { mutableStateOf(false) }
    var showMultiShopModal by remember { mutableStateOf(false) }
    var isRefreshingCloud by remember { mutableStateOf(false) }
    var scannedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var unknownScannedBarcode by remember { mutableStateOf<String?>(null) }

    // Calculate today's metrics
    val startOfToday = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todaySales = remember(sales, startOfToday) {
        sales.filter { !it.isCancelled && it.timestamp >= startOfToday }
    }

    val todayRevenue = remember(todaySales) {
        todaySales.sumOf { it.totalAmount }
    }

    val todayProfit = remember(todaySales) {
        todaySales.sumOf { it.totalAmount - it.totalCostPrice }
    }

    val todayExpenses = remember(cashMovements, startOfToday) {
        cashMovements.filter { it.timestamp >= startOfToday && it.type in listOf("EXPENSE", "SUPPLIER_PAYMENT") }
            .sumOf { it.amount }
    }

    val todayNetResult = todayProfit - todayExpenses

    val totalCustomerReceivables = remember(customers) {
        customers.filter { it.debtAmount > 0L }.sumOf { it.debtAmount }
    }

    val subscriptionDaysLeft = remember(shopProfile?.subscriptionExpiresAt) {
        val exp = shopProfile?.subscriptionExpiresAt ?: 0L
        if (exp <= 0L) null else ((exp - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
    }

    // Cash drawer theoretical balance
    val totalCashBalance = remember(cashMovements) {
        cashMovements.sumOf { movement ->
            when (movement.type) {
                "SALE", "CASH_IN", "DEBT_PAYMENT" -> movement.amount
                "EXPENSE", "CASH_OUT", "SUPPLIER_PAYMENT" -> -movement.amount
                else -> 0L
            }
        }
    }

    val lowStockProducts = remember(products) {
        products.filter { it.stockQuantity <= it.minStockAlert }
    }

    val businessType = shopProfile?.businessType ?: "Boutique"
    val sectorPreset = remember(businessType) {
        com.example.util.BusinessTypePresets.getPreset(businessType)
    }
    val sectorAccent = remember(businessType) {
        com.example.util.SectorUiHelper.getAccentColor(businessType)
    }
    val widthClass = LocalKonabWidthClass.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Blue Brand Gradient and Floating Store Card
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Full-width Background Blue Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(KonabBlue, KonabBlueDark)
                            )
                        )
                        .konabHeroShimmer()
                )

                ResponsiveMaxWidthBox(maxWidth = 1120.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp)
                    ) {
                    // Top Bar: Greeting & Top Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.5.dp, KonabOrange.copy(alpha = 0.85f)),
                                shadowElevation = 4.dp
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_konab_logo),
                                    contentDescription = "KONAB POS",
                                    modifier = Modifier
                                        .padding(3.dp)
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Column {
                                Text(
                                    text = "KONAB POS",
                                    fontFamily = OutfitFontFamily,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.6.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = shopProfile?.slogan?.ifBlank { "Plus proche de vous" } ?: "Plus proche de vous",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KonabOrangeLight,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Top Action Icons (Scanner, Notification Center, & Security Lock)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Direct Barcode Scanner Icon
                            FilledIconButton(
                                onClick = { showScannerModal = true },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_dashboard_scanner")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scanner un article",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Notification Center Icon with Badge
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                text = if (unreadNotificationsCount > 9) "9+" else "$unreadNotificationsCount",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                FilledIconButton(
                                    onClick = { showNotificationModal = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("btn_dashboard_notifications")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Security Lock Button
                            FilledIconButton(
                                onClick = onLockApp,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_lock_screen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Verrouiller l'application",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (onReturnToWelcome != null) {
                                FilledIconButton(
                                    onClick = onReturnToWelcome,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("btn_return_welcome_menu")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Menu d'accueil & Agent",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Floating Merchant Identity Card
                    KonabStaggeredReveal(index = 0) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .konabPressScale(pressedScale = 0.985f)
                                .clickable { onNavigate(KonabScreen.SETTINGS) }
                                .testTag("card_merchant_profile")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    com.example.util.ShopLogoAvatar(
                                        logoUri = shopProfile?.logoUri,
                                        size = 46.dp,
                                        shape = CircleShape,
                                        contentDescription = "Logo de la boutique",
                                        onClick = { onNavigate(com.example.ui.navigation.KonabScreen.SETTINGS) }
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = shopProfile?.name?.ifBlank { "Ma Boutique" } ?: "Ma Boutique",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Surface(
                                            color = sectorAccent.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${sectorPreset.emoji} ${sectorPreset.label}",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = sectorAccent,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        val isCloudAuth = com.example.data.remote.supabase.SupabaseAuthManager.isAuthenticated
                                        val isConfigured = com.example.data.remote.supabase.SupabaseConfig.isConfigured
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { onNavigate(KonabScreen.SETTINGS) }
                                        ) {
                                            KonabLivePulseDot(
                                                color = if (isCloudAuth) Color(0xFF10B981) else if (isConfigured) Color(0xFF0284C7) else KonabBlue,
                                                size = 6.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCloudAuth) "Ouvert • Cloud Connecté" else if (isConfigured) "Ouvert • Sauvegarde Active" else "Ouvert • Local",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isCloudAuth) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Right-side Badges: Multiboutique Switcher + Subscription Tier
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = KonabGoldContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, KonabGoldDark.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .konabPressScale(pressedScale = 0.95f)
                                            .clickable { onNavigate(KonabScreen.SUBSCRIPTION) }
                                            .testTag("badge_subscription")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = KonabGoldDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = shopProfile?.subscriptionTier?.uppercase() ?: "PREMIUM",
                                                color = KonabGoldDark,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (onRefreshActiveShopFromCloud != null) {
                                            Surface(
                                                color = KonabGreen.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.35f)),
                                                modifier = Modifier
                                                    .konabPressScale(pressedScale = 0.95f)
                                                    .clickable(enabled = !isRefreshingCloud) {
                                                        isRefreshingCloud = true
                                                        onRefreshActiveShopFromCloud { _, msg ->
                                                            isRefreshingCloud = false
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .testTag("btn_dashboard_cloud_refresh")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Sync,
                                                        contentDescription = "Actualiser produits et ventes",
                                                        tint = KonabGreen,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = if (isRefreshingCloud) "..." else "Synchro",
                                                        color = KonabGreen,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }

                                        Surface(
                                            color = KonabBlue.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .konabPressScale(pressedScale = 0.95f)
                                                .clickable { showMultiShopModal = true }
                                                .testTag("btn_dashboard_multishop")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Domain,
                                                    contentDescription = "Multiboutique",
                                                    tint = KonabBlue,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = if (connectedShops.size > 1) "Boutiques (${connectedShops.size})" else "Multiboutique",
                                                    color = KonabBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // Quick POS Launcher Hero Banner
        item {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                KonabStaggeredReveal(index = 1) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Main POS Action Button (Personalized by Sector)
                            Button(
                                onClick = { onNavigate(KonabScreen.POS) },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .konabPressScale()
                                    .konabHeroShimmer()
                                    .testTag("btn_quick_sale")
                            ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            com.example.util.SectorUiHelper.getIcon(businessType),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = sectorPreset.quickActionLabel,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sectorPreset.quickActionHint,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.88f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 4 Quick Module Shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DashboardShortcutPill(
                                icon = Icons.Default.AccountBalanceWallet,
                                label = "Caisse",
                                badgeColor = KonabOrange,
                                containerColor = KonabOrange.copy(alpha = 0.14f),
                                onClick = { onNavigate(KonabScreen.CASH) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.Inventory2,
                                label = if (sectorPreset.isServiceSector) "Catalogue" else "Stock",
                                badgeColor = KonabBlue,
                                containerColor = KonabBlue.copy(alpha = 0.14f),
                                onClick = { onNavigate(KonabScreen.STOCK) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.People,
                                label = "Clients",
                                badgeColor = Color(0xFF0284C7),
                                containerColor = Color(0xFF0284C7).copy(alpha = 0.14f),
                                onClick = { onNavigate(KonabScreen.CUSTOMERS) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.BarChart,
                                label = "Rapports",
                                badgeColor = KonabPurple,
                                containerColor = KonabPurple.copy(alpha = 0.14f),
                                onClick = { onNavigate(KonabScreen.REPORTS) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        }
                    }
                }
            }
        }

        // 4 KPI Stat Cards (2x2 on Compact, 4x1 Row on Medium/Expanded)
        item {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                KonabStaggeredReveal(index = 2) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (widthClass == KonabWidthClass.COMPACT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    title = "Chiffre d'affaires",
                                    value = CurrencyFormatter.formatFCFA(todayRevenue),
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                                    iconColor = KonabBlue,
                                    containerColor = KonabBlue.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_revenue"),
                                    onClick = { onNavigate(KonabScreen.REPORTS) }
                                )

                                StatCard(
                                    title = "Ventes",
                                    value = "${todaySales.size}",
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    iconColor = Color(0xFF0284C7),
                                    containerColor = Color(0xFF0284C7).copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_sales"),
                                    onClick = { onNavigate(KonabScreen.REPORTS) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    title = "Caisse",
                                    value = CurrencyFormatter.formatFCFA(maxOf(0L, totalCashBalance)),
                                    subtitle = if (activeCashSession != null) "Session ouverte" else "Caisse fermée",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconColor = KonabOrange,
                                    containerColor = KonabOrange.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_cash"),
                                    onClick = { onNavigate(KonabScreen.CASH) }
                                )

                                StatCard(
                                    title = "Dépenses",
                                    value = CurrencyFormatter.formatFCFA(todayExpenses),
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.Default.Payment,
                                    iconColor = KonabRed,
                                    containerColor = KonabRed.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_expenses"),
                                    onClick = { onNavigate(KonabScreen.CASH) }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = "Chiffre d'affaires",
                                    value = CurrencyFormatter.formatFCFA(todayRevenue),
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                                    iconColor = KonabBlue,
                                    containerColor = KonabBlue.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_revenue"),
                                    onClick = { onNavigate(KonabScreen.REPORTS) }
                                )

                                StatCard(
                                    title = "Ventes",
                                    value = "${todaySales.size}",
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    iconColor = Color(0xFF0284C7),
                                    containerColor = Color(0xFF0284C7).copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_sales"),
                                    onClick = { onNavigate(KonabScreen.REPORTS) }
                                )

                                StatCard(
                                    title = "Caisse",
                                    value = CurrencyFormatter.formatFCFA(maxOf(0L, totalCashBalance)),
                                    subtitle = if (activeCashSession != null) "Session ouverte" else "Caisse fermée",
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconColor = KonabOrange,
                                    containerColor = KonabOrange.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_cash"),
                                    onClick = { onNavigate(KonabScreen.CASH) }
                                )

                                StatCard(
                                    title = "Dépenses",
                                    value = CurrencyFormatter.formatFCFA(todayExpenses),
                                    subtitle = "Aujourd'hui",
                                    icon = Icons.Default.Payment,
                                    iconColor = KonabRed,
                                    containerColor = KonabRed.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("kpi_expenses"),
                                    onClick = { onNavigate(KonabScreen.CASH) }
                                )
                            }
                        }

                        // Row 3: Live Financial Pulse
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onNavigate(KonabScreen.REPORTS) }
                                ) {
                                    Text("Bénéfice net du jour", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = CurrencyFormatter.formatFCFA(todayNetResult),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (todayNetResult >= 0L) KonabGreen else KonabRed
                                    )
                                }

                                VerticalDivider(modifier = Modifier.height(30.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                        .clickable { onNavigate(KonabScreen.CUSTOMERS) }
                                ) {
                                    Text("Crédits à recouvrer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = CurrencyFormatter.formatFCFA(totalCustomerReceivables),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (totalCustomerReceivables > 0L) KonabOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (activeCashSession != null) KonabGreenLight else KonabRedContainer,
                                    modifier = Modifier
                                        .konabPressScale(pressedScale = 0.96f)
                                        .clickable { onNavigate(KonabScreen.CASH) }
                                        .testTag("badge_cash_session_status")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (activeCashSession != null) {
                                            KonabLivePulseDot(color = KonabGreen, size = 6.dp)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(KonabRed)
                                            )
                                        }
                                        Text(
                                            text = if (activeCashSession != null) "Ouverte" else "Fermée",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeCashSession != null) KonabGreenDark else KonabRed
                                        )
                                    }
                                }
                            }
                        }

                        // Subscription Expiration Warning if <= 7 days
                        if (subscriptionDaysLeft != null && subscriptionDaysLeft <= 7) {
                            val isExpired = subscriptionDaysLeft <= 0
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpired) KonabRedContainer else Color(0xFFFEF3C7)
                                ),
                                border = BorderStroke(1.dp, if (isExpired) KonabRed else Color(0xFFD97706)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .konabPressScale(pressedScale = 0.985f)
                                    .clickable { onNavigate(KonabScreen.SUBSCRIPTION) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = if (isExpired) KonabRed else Color(0xFFD97706)
                                        )
                                        Column {
                                            Text(
                                                text = if (isExpired) "Licence expirée" else "Licence expire dans $subscriptionDaysLeft jour(s)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isExpired) KonabRed else Color(0xFF92400E)
                                            )
                                            Text(
                                                text = "Touchez pour contacter un agent ou renouveler votre forfait.",
                                                fontSize = 11.sp,
                                                color = if (isExpired) KonabRed.copy(alpha = 0.85f) else Color(0xFF92400E).copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isExpired) KonabRed else Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Analytics Chart + Stock Alert + Sector Tip (Side-by-side on Expanded, Stacked on Compact/Medium)
        item {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                val stockAlertBlock: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Produits en alerte",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            TextButton(
                                onClick = { onNavigate(KonabScreen.STOCK) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Voir tout",
                                    color = KonabGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (lowStockProducts.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = KonabRedLight),
                                border = BorderStroke(1.dp, KonabRed.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .konabPressScale(pressedScale = 0.985f)
                                    .clickable { onNavigate(KonabScreen.STOCK) }
                                    .testTag("card_low_stock_alert")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(KonabRed),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = "${lowStockProducts.size} produit(s) en stock faible",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF991B1B),
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Risque d'indisponibilité pour vos ventes",
                                                fontSize = 12.sp,
                                                color = Color(0xFF991B1B).copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = KonabRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = KonabGreenLight),
                                border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(KonabGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = "Stock optimal • Aucune alerte",
                                            fontWeight = FontWeight.Bold,
                                            color = KonabGreenDark,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Tous vos articles sont bien approvisionnés",
                                            fontSize = 12.sp,
                                            color = KonabGreenDark.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val businessTipBlock: @Composable () -> Unit = {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(KonabGoldContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = KonabGoldDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Astuce Métier • ${sectorPreset.label}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = sectorAccent
                                )
                                val tipText = when {
                                    lowStockProducts.isNotEmpty() -> "Réapprovisionnez vos ${lowStockProducts.size} articles en alerte pour ne rater aucune vente aujourd'hui."
                                    todayProfit > 0 -> "Bénéfice estimé aujourd'hui : ${CurrencyFormatter.formatFCFA(todayProfit)}. ${sectorPreset.dailyTip}"
                                    else -> sectorPreset.dailyTip
                                }
                                Text(
                                    text = tipText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                KonabStaggeredReveal(index = 3) {
                    if (widthClass == KonabWidthClass.EXPANDED) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            DailySalesChartCard(
                                sales = sales,
                                onViewReports = { onNavigate(KonabScreen.REPORTS) },
                                modifier = Modifier.weight(1.45f)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                stockAlertBlock()
                                businessTipBlock()
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            DailySalesChartCard(
                                sales = sales,
                                onViewReports = { onNavigate(KonabScreen.REPORTS) }
                            )
                            stockAlertBlock()
                            businessTipBlock()
                        }
                    }
                }
            }
        }

        // Section "Dernières ventes" Header
        item {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dernières ventes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    TextButton(
                        onClick = { onNavigate(KonabScreen.TRANSACTIONS) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Historique",
                            color = KonabGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Recent Sales List
        if (sales.isEmpty()) {
            item {
                ResponsiveMaxWidthBox(
                    maxWidth = 1120.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    KonabEmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Aucune vente enregistrée",
                        subtitle = "Appuyez sur '${sectorPreset.quickActionLabel}' pour enregistrer votre première transaction.",
                        accentColor = KonabBlue,
                        actionLabel = "Ouvrir la caisse",
                        onAction = { onNavigate(KonabScreen.POS) }
                    )
                }
            }
        } else {
            itemsIndexed(sales.take(8)) { idx, sale ->
                ResponsiveMaxWidthBox(
                    maxWidth = 1120.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    KonabStaggeredReveal(index = idx) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .konabPressScale(pressedScale = 0.985f)
                                .clickable { onViewSaleReceipt(sale) }
                                .testTag("sale_item_${sale.transactionNumber}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val iconColor = when (sale.paymentMethod) {
                                        "Espèces" -> KonabGreen
                                        "Carte bancaire" -> Color(0xFF1E88E5)
                                        "Crédit client" -> KonabRed
                                        else -> KonabGoldDark
                                    }
                                    val containerColor = when (sale.paymentMethod) {
                                        "Espèces" -> KonabGreenLight
                                        "Carte bancaire" -> Color(0xFFE3F2FD)
                                        "Crédit client" -> KonabRedLight
                                        else -> KonabGoldContainer
                                    }
                                    val iconVector = when (sale.paymentMethod) {
                                        "Espèces" -> Icons.Default.Payments
                                        "Carte bancaire" -> Icons.Default.CreditCard
                                        "Crédit client" -> Icons.Default.Receipt
                                        else -> Icons.Default.PhoneAndroid
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(containerColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = sale.customerName.ifBlank { "Client comptoir" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = CurrencyFormatter.formatTimeOnly(sale.timestamp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = sale.paymentMethod,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyFormatter.formatFCFA(sale.totalAmount),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (sale.paymentStatus == "PAYÉ") KonabGreen else KonabRed,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Surface(
                                        color = if (sale.paymentStatus == "PAYÉ") KonabGreenLight else KonabRedLight,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (sale.paymentStatus == "PAYÉ") KonabGreen.copy(0.3f) else KonabRed.copy(0.3f)
                                        )
                                    ) {
                                        Text(
                                            text = sale.paymentStatus,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sale.paymentStatus == "PAYÉ") KonabGreenDark else KonabRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Direct Barcode Scanner Hardware Modal
    if (showScannerModal) {
        RealBarcodeScannerModal(
            title = "Scanner un article",
            subtitle = "Visez le code-barres pour vérifier le prix ou ajouter au panier",
            onBarcodeScanned = { barcode ->
                showScannerModal = false
                val match = products.find { it.barcode.equals(barcode.trim(), ignoreCase = true) }
                if (match != null) {
                    scannedProduct = match
                } else {
                    unknownScannedBarcode = barcode
                }
            },
            onDismiss = { showScannerModal = false }
        )
    }

    // Dialog when product is found by scanner on Dashboard
    scannedProduct?.let { product ->
        Dialog(onDismissRequest = { scannedProduct = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(KonabGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = KonabGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Catégorie : ${product.category}",
                            fontSize = 12.sp,
                            color = KonabTextSecondary
                        )
                        if (product.barcode.isNotBlank()) {
                            Text(
                                text = "Code-barres : ${product.barcode}",
                                fontSize = 11.sp,
                                color = KonabTextMuted
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KonabBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Prix de vente", fontSize = 11.sp, color = KonabTextSecondary)
                                Text(
                                    CurrencyFormatter.formatFCFA(product.sellPrice),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = KonabGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("En stock", fontSize = 11.sp, color = KonabTextSecondary)
                                Text(
                                    "${product.stockQuantity} ${product.unit}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (product.stockQuantity <= product.minStockAlert) KonabRed else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scannedProduct = null
                                onNavigate(KonabScreen.STOCK)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Voir Stock", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val p = product
                                scannedProduct = null
                                onAddToCart?.invoke(p)
                                onNavigate(KonabScreen.POS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Vendre", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog when scanned barcode is not recognized
    unknownScannedBarcode?.let { barcode ->
        AlertDialog(
            onDismissRequest = { unknownScannedBarcode = null },
            icon = {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = KonabGoldDark)
            },
            title = {
                Text("Code-barres non répertorié", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Le code '$barcode' n'est associé à aucun produit existant dans votre inventaire.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        unknownScannedBarcode = null
                        onNavigate(KonabScreen.STOCK)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Créer dans le Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { unknownScannedBarcode = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    if (showNotificationModal) {
        com.example.ui.components.NotificationCenterModal(
            notifications = notifications,
            unreadCount = unreadNotificationsCount,
            onDismiss = { showNotificationModal = false },
            onMarkAsRead = onMarkNotificationAsRead,
            onMarkAllAsRead = onMarkAllNotificationsAsRead,
            onDeleteNotification = onDeleteNotification,
            onClearAll = onClearAllNotifications,
            onTriggerTestNotification = onTriggerTestNotification,
            onNavigateToRoute = { route ->
                showNotificationModal = false
                when (route) {
                    "stock" -> onNavigate(KonabScreen.STOCK)
                    "pos" -> onNavigate(KonabScreen.POS)
                    "customers" -> onNavigate(KonabScreen.CUSTOMERS)
                    "cash" -> onNavigate(KonabScreen.CASH)
                    "reports" -> onNavigate(KonabScreen.REPORTS)
                    "settings" -> onNavigate(KonabScreen.SETTINGS)
                }
            }
        )
    }

    if (showMultiShopModal) {
        MultiShopManagerModal(
            connectedShops = connectedShops,
            activeCloudShopId = activeCloudShopId,
            isCurrentShopConfigured = shopProfile?.isConfigured == true,
            onConnectWithCredentials = onConnectMultiShopRemote,
            onSwitchShop = onSwitchConnectedShop,
            onRefreshActiveShop = onRefreshActiveShopFromCloud,
            onDismiss = { showMultiShopModal = false }
        )
    }
}

@Composable
private fun DashboardShortcutPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeColor: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .height(72.dp)
            .konabPressScale(pressedScale = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(containerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
