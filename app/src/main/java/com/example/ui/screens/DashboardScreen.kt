package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.ui.components.DailySalesChartCard
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.components.StatCard
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
    notifications: List<com.example.data.local.entity.NotificationEntity> = emptyList(),
    unreadNotificationsCount: Int = 0,
    onMarkNotificationAsRead: (Long) -> Unit = {},
    onMarkAllNotificationsAsRead: () -> Unit = {},
    onDeleteNotification: (Long) -> Unit = {},
    onClearAllNotifications: () -> Unit = {},
    onTriggerTestNotification: (String) -> Unit = {}
) {
    // Barcode scanner modal state
    var showScannerModal by remember { mutableStateOf(false) }
    var showNotificationModal by remember { mutableStateOf(false) }
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
        sales.filter { it.timestamp >= startOfToday }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(KonabBackground)
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Emerald Brand Gradient and Floating Store Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Background Emerald Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    KonabGreen,
                                    KonabGreenDark
                                )
                            ),
                            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                        )
                )

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
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_konab_logo),
                                    contentDescription = "Logo KONAB POS",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .fillMaxSize()
                                )
                            }
                            Column {
                                Text(
                                    text = shopProfile?.name?.ifBlank { "KONAB POS" } ?: "KONAB POS",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
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
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Floating Merchant Identity Card (Screen 3 Board Style)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(1.dp, KonabBorder),
                        modifier = Modifier
                            .fillMaxWidth()
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
                                // Store Avatar
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(KonabGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = shopProfile?.name?.ifBlank { "Boutique Kaba" } ?: "Boutique Kaba",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(KonabGreen)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ouvert • Hors ligne",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = KonabTextSecondary
                                        )
                                    }
                                }
                            }

                            // Subscription Tier Badge (Clickable)
                            Surface(
                                color = KonabGoldContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, KonabGoldDark.copy(alpha = 0.3f)),
                                modifier = Modifier
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
                        }
                    }
                }
            }
        }

        // Quick POS Launcher Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, KonabBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main POS Action Button
                        Button(
                            onClick = { onNavigate(KonabScreen.POS) },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_quick_sale")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AddShoppingCart,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Nouvelle Vente (POS)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Encaisser en espèces ou mobile money",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.85f)
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
                                badgeColor = KonabGoldDark,
                                containerColor = KonabGoldContainer,
                                onClick = { onNavigate(KonabScreen.CASH) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.Inventory2,
                                label = "Stock",
                                badgeColor = KonabGreen,
                                containerColor = KonabGreenLight,
                                onClick = { onNavigate(KonabScreen.STOCK) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.People,
                                label = "Clients",
                                badgeColor = Color(0xFF1E88E5),
                                containerColor = Color(0xFFE3F2FD),
                                onClick = { onNavigate(KonabScreen.CUSTOMERS) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DashboardShortcutPill(
                                icon = Icons.Default.BarChart,
                                label = "Rapports",
                                badgeColor = Color(0xFF8E24AA),
                                containerColor = Color(0xFFF3E5F5),
                                onClick = { onNavigate(KonabScreen.REPORTS) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 4 KPI Stat Cards (2x2 Grid) - Faithful Reproduction of Screen 3 on UX Board
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Chiffre d'affaires & Ventes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Chiffre d'affaires",
                        value = CurrencyFormatter.formatFCFA(todayRevenue),
                        subtitle = "Aujourd'hui",
                        icon = Icons.Default.TrendingUp,
                        iconColor = KonabGreen,
                        containerColor = KonabGreenLight,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_revenue"),
                        onClick = { onNavigate(KonabScreen.REPORTS) }
                    )

                    StatCard(
                        title = "Ventes",
                        value = "${todaySales.size}",
                        subtitle = "Aujourd'hui",
                        icon = Icons.Default.ReceiptLong,
                        iconColor = Color(0xFF1E88E5),
                        containerColor = Color(0xFFE3F2FD),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_sales"),
                        onClick = { onNavigate(KonabScreen.REPORTS) }
                    )
                }

                // Row 2: Caisse & Dépenses
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        title = "Caisse",
                        value = CurrencyFormatter.formatFCFA(maxOf(0L, totalCashBalance)),
                        subtitle = "En caisse",
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = KonabGoldDark,
                        containerColor = KonabGoldContainer,
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
                        containerColor = KonabRedContainer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("kpi_expenses"),
                        onClick = { onNavigate(KonabScreen.CASH) }
                    )
                }
            }
        }

        // Daily Sales Native Canvas Chart
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                DailySalesChartCard(
                    sales = sales,
                    onViewReports = { onNavigate(KonabScreen.REPORTS) }
                )
            }
        }

        // Section "Produits en alerte" with "Voir tout" link (UX Board Screen 3)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

        // Smart Business Tip Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, KonabBorder),
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
                                text = "Conseil Konab POS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val tipText = when {
                                lowStockProducts.isNotEmpty() -> "Réapprovisionnez vos ${lowStockProducts.size} articles en alerte pour ne rater aucune vente aujourd'hui."
                                todaySales.isEmpty() -> "Commencez votre journée en ouvrant votre caisse et en enregistrant vos premières ventes."
                                todayProfit > 0 -> "Bénéfice estimé aujourd'hui : ${CurrencyFormatter.formatFCFA(todayProfit)}. Excellente gestion !"
                                else -> "Pensez à vérifier le solde de caisse avant la fin de journée."
                            }
                            Text(
                                text = tipText,
                                fontSize = 12.sp,
                                color = KonabTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Section "Dernières ventes" Header (UX Board Screen 3)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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

        // Recent Sales List
        if (sales.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, KonabBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(KonabBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = KonabTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "Aucune vente enregistrée",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Appuyez sur 'Nouvelle Vente' pour enregistrer votre première transaction",
                            fontSize = 12.sp,
                            color = KonabTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(sales.take(8)) { sale ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, KonabBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                            // Payment Method Thumbnail / Icon
                            val iconColor = when (sale.paymentMethod) {
                                "Espèces" -> KonabGreen
                                "Carte bancaire" -> Color(0xFF1E88E5)
                                "Crédit client" -> KonabRed
                                else -> KonabGoldDark // Orange Money, Wave, Moov
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
                                        color = KonabTextSecondary
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 12.sp,
                                        color = KonabTextMuted
                                    )
                                    Text(
                                        text = sale.paymentMethod,
                                        fontSize = 12.sp,
                                        color = KonabTextSecondary
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
                Icon(Icons.Default.HelpOutline, contentDescription = null, tint = KonabGoldDark)
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
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.15f)),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
