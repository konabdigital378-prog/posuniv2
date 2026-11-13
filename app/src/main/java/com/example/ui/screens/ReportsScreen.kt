package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashMovementEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity>,
    products: List<ProductEntity>,
    cashMovements: List<CashMovementEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Ventes, 1: Stock, 2: Dépenses
    var selectedPeriod by remember { mutableStateOf("Cette semaine") }
    var showPeriodMenu by remember { mutableStateOf(false) }

    val periods = listOf("Aujourd'hui", "Cette semaine", "Ce mois", "Cette année", "Tout l'historique")

    // Filter sales by selected period
    val filteredSales = remember(sales, selectedPeriod) {
        val cal = Calendar.getInstance()
        val startTime = when (selectedPeriod) {
            "Aujourd'hui" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            "Cette semaine" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            "Ce mois" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            "Cette année" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            else -> 0L
        }
        sales.filter { !it.isCancelled && it.timestamp >= startTime }
    }

    val totalRevenue = remember(filteredSales) { filteredSales.sumOf { it.totalAmount } }
    val totalCost = remember(filteredSales) { filteredSales.sumOf { it.totalCostPrice } }
    val estimatedProfit = maxOf(0L, totalRevenue - totalCost)
    val averageBasket = remember(filteredSales, totalRevenue) {
        if (filteredSales.isNotEmpty()) totalRevenue / filteredSales.size else 0L
    }
    val profitMarginPct = remember(totalRevenue, estimatedProfit) {
        if (totalRevenue > 0) ((estimatedProfit.toDouble() / totalRevenue.toDouble()) * 100).toInt() else 0
    }

    // Best selling products calculation
    val topProducts = remember(saleItems) {
        val grouped = saleItems.groupBy { it.productName }
        grouped.map { (name, items) ->
            Triple(name, items.sumOf { it.quantity }, items.sumOf { it.totalPrice })
        }.sortedByDescending { it.second }
    }

    // Payment methods breakdown
    val paymentBreakdown = remember(filteredSales) {
        val grouped = filteredSales.groupBy { it.paymentMethod }
        grouped.map { (method, list) ->
            val count = list.size
            val amount = list.sumOf { it.totalAmount }
            Triple(method, count, amount)
        }.sortedByDescending { it.third }
    }

    // Stock analytics
    val totalStockCost = remember(products) { products.sumOf { it.buyPrice * it.stockQuantity } }
    val totalStockSell = remember(products) { products.sumOf { it.sellPrice * it.stockQuantity } }
    val potentialStockProfit = maxOf(0L, totalStockSell - totalStockCost)
    val lowStockCount = remember(products) { products.count { it.stockQuantity in 1..it.minStockAlert } }
    val outOfStockCount = remember(products) { products.count { it.stockQuantity <= 0 } }
    val healthyStockCount = remember(products) { products.count { it.stockQuantity > it.minStockAlert } }
    val urgentRestockProducts = remember(products) {
        products.filter { it.stockQuantity <= it.minStockAlert }.sortedBy { it.stockQuantity }
    }

    // Cash movement analytics (supports actual repository types: SALE, CASH_IN, DEBT_PAYMENT, EXPENSE, CASH_OUT, SUPPLIER_PAYMENT)
    val totalCashIn = remember(cashMovements) {
        cashMovements.filter { it.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT", "IN") }.sumOf { it.amount }
    }
    val totalCashOut = remember(cashMovements) {
        cashMovements.filter { it.type in listOf("EXPENSE", "CASH_OUT", "SUPPLIER_PAYMENT", "OUT") }.sumOf { it.amount }
    }
    val netCashMovement = totalCashIn - totalCashOut

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rapports & Statistiques", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Analyses de performance financière", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val reportText = StringBuilder()
                        reportText.append("=== RAPPORT D'ACTIVITÉ KONAB POS ===\n")
                        reportText.append("Période : $selectedPeriod\n\n")
                        reportText.append("CHIFFRES CLÉS :\n")
                        reportText.append("• Chiffre d'affaires : ${CurrencyFormatter.formatFCFA(totalRevenue)}\n")
                        reportText.append("• Bénéfices estimés : ${CurrencyFormatter.formatFCFA(estimatedProfit)} ($profitMarginPct% de marge)\n")
                        reportText.append("• Nombre de ventes : ${filteredSales.size}\n")
                        reportText.append("• Panier moyen : ${CurrencyFormatter.formatFCFA(averageBasket)}\n\n")
                        reportText.append("VALEUR DU STOCK :\n")
                        reportText.append("• Valeur marchande : ${CurrencyFormatter.formatFCFA(totalStockSell)}\n")
                        reportText.append("• Coût d'achat : ${CurrencyFormatter.formatFCFA(totalStockCost)}\n")
                        reportText.append("• Articles en rupture : $outOfStockCount\n")
                        reportText.append("• Articles stock faible : $lowStockCount\n\n")
                        reportText.append("TOP 5 PRODUITS :\n")
                        topProducts.take(5).forEachIndexed { i, p ->
                            reportText.append("${i + 1}. ${p.first} : ${p.second} vendus (${CurrencyFormatter.formatFCFA(p.third)})\n")
                        }
                        reportText.append("\n====================================")

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText.toString())
                            putExtra(Intent.EXTRA_SUBJECT, "Rapport commercial - $selectedPeriod")
                        }
                        context.startActivity(Intent.createChooser(intent, "Exporter le rapport commercial"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exporter le rapport", tint = KonabBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        val widthClass = LocalKonabWidthClass.current
        val horizontalPad = if (widthClass == KonabWidthClass.COMPACT) 16.dp else 24.dp
        val cardOutlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Modern Segmented Navigation Tabs
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                ResponsiveMaxWidthBox(maxWidth = 1040.dp) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = KonabBlue
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ventes", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stock", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dépenses", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        )
                    }
                }
            }

            ResponsiveMaxWidthBox(
                maxWidth = 1040.dp,
                modifier = Modifier.weight(1f)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                    },
                    label = "reports_tab_transition"
                ) { currentTab ->
                    when (currentTab) {
                        0 -> {
                            // TAB 0 : VENTES & REVENUS
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizontalPad),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Period Selector Bar
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            OutlinedButton(
                                                onClick = { showPeriodMenu = true },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, cardOutlineColor),
                                                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .konabPressScale()
                                            ) {
                                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp), tint = KonabBlue)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(selectedPeriod, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            DropdownMenu(
                                                expanded = showPeriodMenu,
                                                onDismissRequest = { showPeriodMenu = false }
                                            ) {
                                        periods.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p, fontWeight = if (p == selectedPeriod) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    selectedPeriod = p
                                                    showPeriodMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = KonabSuccessContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = KonabSuccess, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$profitMarginPct% marge",
                                            color = KonabSuccess,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Sapphire Gradient Hero Card
                        item {
                            KonabStaggeredReveal(index = 0) {
                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF0B4BD9), Color(0xFF071E54))
                                                )
                                            )
                                            .konabHeroShimmer(maxAlpha = 0.09f)
                                            .padding(22.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Chiffre d'affaires total",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = selectedPeriod,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = CurrencyFormatter.formatFCFA(totalRevenue),
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 32.sp,
                                                color = Color.White
                                            )

                                            Spacer(modifier = Modifier.height(18.dp))

                                            // Financial breakdown pills inside hero
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text("Bénéfices estimés", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            CurrencyFormatter.formatFCFA(estimatedProfit),
                                                            color = Color(0xFF6EE7B7),
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = OutfitFontFamily,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text("Panier moyen", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            CurrencyFormatter.formatFCFA(averageBasket),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = OutfitFontFamily,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = Color.White.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(0.8f)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Text("Nb. Ventes", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            "${filteredSales.size}",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = OutfitFontFamily,
                                                            fontSize = 15.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bar Chart Visual for Weekly Performance
                        item {
                            KonabStaggeredReveal(index = 1) {
                                val chartGrow = remember { Animatable(0f) }
                                LaunchedEffect(selectedTab, selectedPeriod) {
                                    chartGrow.snapTo(0f)
                                    chartGrow.animateTo(
                                        1f,
                                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                                    )
                                }

                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, cardOutlineColor),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Évolution hebdomadaire", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("7 derniers jours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        val days = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
                                        val sampleHeights = listOf(0.4f, 0.65f, 0.5f, 0.85f, 0.7f, 1.0f, 0.82f)

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val barWidth = 24.dp.toPx()
                                                val totalWidth = size.width
                                                val space = (totalWidth - (barWidth * days.size)) / (days.size + 1)
                                                val maxHeight = size.height - 24.dp.toPx()

                                                sampleHeights.forEachIndexed { i, ratio ->
                                                    val x = space + i * (barWidth + space)
                                                    val barHeight = (maxHeight * ratio * chartGrow.value).coerceAtLeast(6.dp.toPx())
                                                    val y = maxHeight - barHeight

                                                    val barColor = if (i == 5) Color(0xFF0B4BD9) else Color(0xFF93C5FD)

                                                    drawRoundRect(
                                                        color = barColor,
                                                        topLeft = Offset(x, y),
                                                        size = Size(barWidth, barHeight),
                                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                days.forEachIndexed { index, day ->
                                                    Text(
                                                        text = day,
                                                        fontSize = 11.sp,
                                                        color = if (index == 5) KonabBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (index == 5) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Payment Methods Distribution
                        item {
                            KonabStaggeredReveal(index = 2) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, cardOutlineColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text("Répartition par mode de règlement", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (paymentBreakdown.isEmpty()) {
                                            Text(
                                                "Aucune transaction enregistrée pour cette période.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                paymentBreakdown.forEach { (method, _, amount) ->
                                                    val percentage = if (totalRevenue > 0) ((amount.toDouble() / totalRevenue.toDouble()) * 100).toInt() else 0
                                                    val animProgress by animateFloatAsState(
                                                        targetValue = (percentage / 100f).coerceIn(0f, 1f),
                                                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
                                                        label = "pay_bar_$method"
                                                    )
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(method, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                            Text(
                                                                "${CurrencyFormatter.formatFCFA(amount)} ($percentage%)",
                                                                fontFamily = OutfitFontFamily,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = KonabBlue
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        LinearProgressIndicator(
                                                            progress = { animProgress },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(7.dp)
                                                                .clip(CircleShape),
                                                            color = KonabBlue,
                                                            trackColor = cardOutlineColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Top 5 Best Sellers
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Articles phares les plus vendus", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${topProducts.size} réf.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (topProducts.isEmpty()) {
                            item {
                                KonabEmptyState(
                                    icon = Icons.Default.Sell,
                                    title = "Aucune vente enregistrée",
                                    subtitle = "Les produits vendus apparaîtront ici avec leur classement.",
                                    accentColor = KonabBlue
                                )
                            }
                        } else {
                            itemsIndexed(topProducts.take(5)) { idx, (name, qty, total) ->
                                KonabStaggeredReveal(index = idx) {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, cardOutlineColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFEF3C7)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(
                                                        CurrencyFormatter.formatFCFA(total),
                                                        fontFamily = OutfitFontFamily,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = KonabBlue
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = KonabBlueContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "$qty vendus",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = KonabBlue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1 : RAPPORT DE STOCK & INVENTAIRE
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPad),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Stock Valuation Card
                        item {
                            KonabStaggeredReveal(index = 0) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, cardOutlineColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Valorisation globale du stock", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Valeur marchande", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                CurrencyFormatter.formatFCFA(totalStockSell),
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 20.sp,
                                                color = KonabBlue
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Coût d'acquisition", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                CurrencyFormatter.formatFCFA(totalStockCost),
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = cardOutlineColor)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Plus-value potentielle", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            "+${CurrencyFormatter.formatFCFA(potentialStockProfit)}",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = KonabSuccess
                                        )
                                    }
                                }
                            }
                            }
                        }

                        // Stock Health Indicators
                        item {
                            KonabStaggeredReveal(index = 1) {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, cardOutlineColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text("Santé globale de l'inventaire", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            color = KonabSuccessContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Sain", fontSize = 11.sp, color = KonabSuccess, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("$healthyStockCount", fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KonabSuccess)
                                                Text("articles", fontSize = 10.sp, color = KonabSuccess)
                                            }
                                        }

                                        Surface(
                                            color = Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Faible", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("$lowStockCount", fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFD97706))
                                                Text("à réassortir", fontSize = 10.sp, color = Color(0xFFD97706))
                                            }
                                        }

                                        Surface(
                                            color = KonabRedContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Rupture", fontSize = 11.sp, color = KonabRed, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("$outOfStockCount", fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KonabRed)
                                                Text("épuisés", fontSize = 10.sp, color = KonabRed)
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }

                        // Urgent Restock Items
                        item {
                            Text("Articles prioritaires à réapprovisionner", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        if (urgentRestockProducts.isEmpty()) {
                            item {
                                KonabEmptyState(
                                    icon = Icons.Default.CheckCircle,
                                    title = "Tous vos stocks sont au-dessus du seuil",
                                    subtitle = "Aucune rupture ou alerte critique détectée.",
                                    accentColor = KonabSuccess
                                )
                            }
                        } else {
                            itemsIndexed(urgentRestockProducts, key = { index, p -> "${p.id}_$index" }) { idx, product ->
                                val isOut = product.stockQuantity <= 0
                                KonabStaggeredReveal(index = idx) {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, if (isOut) KonabRed.copy(alpha = 0.5f) else cardOutlineColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Seuil d'alerte configuré : ${product.minStockAlert} ${product.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            Surface(
                                                color = if (isOut) KonabRedContainer else Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (isOut) "Épuisé (0)" else "${product.stockQuantity} restants",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (isOut) KonabRed else Color(0xFFD97706),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2 : DÉPENSES & MOUVEMENTS DE CAISSE
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPad),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Cash Movement Summary Card
                        item {
                            KonabStaggeredReveal(index = 0) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, cardOutlineColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Bilan des flux de trésorerie", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Apports / Entrées", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "+${CurrencyFormatter.formatFCFA(totalCashIn)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = KonabSuccess
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Dépenses / Sorties", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "-${CurrencyFormatter.formatFCFA(totalCashOut)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = KonabRed
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = cardOutlineColor)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Solde net des mouvements", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text(
                                            CurrencyFormatter.formatFCFA(netCashMovement),
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = if (netCashMovement >= 0) KonabSuccess else KonabRed
                                        )
                                    }
                                }
                            }
                            }
                        }

                        // Recent Cash Movements List
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Historique des mouvements", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${cashMovements.size} opérations", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (cashMovements.isEmpty()) {
                            item {
                                KonabEmptyState(
                                    icon = Icons.Default.AccountBalanceWallet,
                                    title = "Aucun mouvement enregistré",
                                    subtitle = "Vos apports et dépenses de caisse apparaîtront ici.",
                                    accentColor = KonabBlue
                                )
                            }
                        } else {
                            itemsIndexed(cashMovements.take(30), key = { index, m -> "${m.id}_$index" }) { idx, movement ->
                                val isEntry = movement.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT", "IN")
                                val typeLabel = when (movement.type) {
                                    "SALE" -> "Vente espèces"
                                    "CASH_IN", "IN" -> "Entrée de caisse"
                                    "DEBT_PAYMENT" -> "Remboursement dette"
                                    "SUPPLIER_PAYMENT" -> "Paiement fournisseur"
                                    "EXPENSE" -> "Dépense"
                                    "CASH_OUT", "OUT" -> "Sortie de caisse"
                                    else -> "Mouvement"
                                }
                                KonabStaggeredReveal(index = idx) {
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, cardOutlineColor),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isEntry) KonabSuccessContainer else KonabRedContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        if (isEntry) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                        contentDescription = null,
                                                        tint = if (isEntry) KonabSuccess else KonabRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(movement.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                    Text(
                                                        "$typeLabel • ${CurrencyFormatter.formatDateTime(movement.timestamp)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${if (isEntry) "+" else "-"}${CurrencyFormatter.formatFCFA(movement.amount)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (isEntry) KonabSuccess else KonabRed
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
    }
}
