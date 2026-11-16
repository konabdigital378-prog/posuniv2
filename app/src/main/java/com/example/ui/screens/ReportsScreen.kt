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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.local.entity.CashMovementEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class WeeklyBarPoint(
    val shortDay: String,
    val dayNumber: String,
    val fullDate: String,
    val amount: Long,
    val count: Int,
    val isToday: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity>,
    products: List<ProductEntity>,
    cashMovements: List<CashMovementEntity>,
    onBack: () -> Unit,
    onRefreshReports: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Ventes, 1: Stock, 2: Dépenses
    var selectedPeriod by remember { mutableStateOf("Cette semaine") }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showCashPeriodMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshReports()
    }

    val periods = listOf("Aujourd'hui", "Cette semaine", "Ce mois", "Cette année", "Tout l'historique")

    // Normalized period start timestamp (zeroed MILLISECOND and Monday-aligned week start)
    val periodStartTime = remember(selectedPeriod, sales.size, cashMovements.size) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (selectedPeriod) {
            "Aujourd'hui" -> cal.timeInMillis
            "Cette semaine" -> {
                val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                cal.timeInMillis
            }
            "Ce mois" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            "Cette année" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            else -> 0L
        }
    }

    // Filter sales by selected period
    val filteredSales = remember(sales, periodStartTime) {
        sales.filter { !it.isCancelled && it.timestamp >= periodStartTime }
    }

    val filteredSaleIds = remember(filteredSales) {
        filteredSales.mapTo(HashSet()) { it.id }
    }

    val saleItemsBySaleId = remember(saleItems) {
        saleItems.groupBy { it.saleId }
    }

    val totalRevenue = remember(filteredSales) { filteredSales.sumOf { it.totalAmount } }
    val totalCost = remember(filteredSales, saleItemsBySaleId) {
        filteredSales.sumOf { sale ->
            if (sale.totalCostPrice > 0L) {
                sale.totalCostPrice
            } else {
                saleItemsBySaleId[sale.id]?.sumOf { it.unitBuyPrice * it.quantity } ?: 0L
            }
        }
    }
    val estimatedProfit = maxOf(0L, totalRevenue - totalCost)
    val averageBasket = remember(filteredSales, totalRevenue) {
        if (filteredSales.isNotEmpty()) totalRevenue / filteredSales.size else 0L
    }
    val profitMarginPct = remember(totalRevenue, estimatedProfit) {
        if (totalRevenue > 0L) ((estimatedProfit.toDouble() / totalRevenue.toDouble()) * 100).toInt() else 0
    }

    // Best selling products strictly matching filtered non-cancelled sales
    val topProducts = remember(saleItems, filteredSaleIds) {
        val periodItems = saleItems.filter { it.saleId in filteredSaleIds }
        periodItems.groupBy { it.productName }
            .map { (name, items) ->
                Triple(name, items.sumOf { it.quantity }, items.sumOf { it.totalPrice })
            }
            .sortedByDescending { it.second }
    }

    // Real last 7 days aggregation
    val weeklyChartData = remember(sales) {
        val shortFmt = SimpleDateFormat("EEE", Locale.FRENCH)
        val numFmt = SimpleDateFormat("dd", Locale.getDefault())
        val fullFmt = SimpleDateFormat("EEEE d MMM", Locale.FRENCH)
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = cal.timeInMillis
            val endMs = startMs + 86_400_000L - 1L
            val daySales = sales.filter { !it.isCancelled && it.timestamp in startMs..endMs }
            WeeklyBarPoint(
                shortDay = shortFmt.format(cal.time).replace(".", "").take(3).replaceFirstChar { it.uppercase() },
                dayNumber = numFmt.format(cal.time),
                fullDate = fullFmt.format(cal.time).replaceFirstChar { it.uppercase() },
                amount = daySales.sumOf { it.totalAmount },
                count = daySales.size,
                isToday = (offset == 0)
            )
        }
    }

    // Payment methods breakdown
    val paymentBreakdown = remember(filteredSales) {
        filteredSales.groupBy { it.paymentMethod }
            .map { (method, list) ->
                Triple(method, list.size, list.sumOf { it.totalAmount })
            }
            .sortedByDescending { it.third }
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

    // Cash movement analytics filtered by period
    val filteredCashMovements = remember(cashMovements, periodStartTime) {
        cashMovements.filter { it.timestamp >= periodStartTime }
    }
    val totalCashIn = remember(filteredCashMovements) {
        filteredCashMovements.filter { it.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT", "IN") }.sumOf { it.amount }
    }
    val totalCashOut = remember(filteredCashMovements) {
        filteredCashMovements.filter { it.type in listOf("EXPENSE", "CASH_OUT", "SUPPLIER_PAYMENT", "OUT") }.sumOf { it.amount }
    }
    val netCashMovement = totalCashIn - totalCashOut

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Rapports & Statistiques",
                subtitle = "Analyses de performance financière",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            onRefreshReports()
                            android.widget.Toast.makeText(
                                context,
                                "Actualisation des statistiques...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("btn_refresh_reports")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Actualiser les statistiques", tint = KonabBlue)
                    }
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
                }
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
                                                    listOf(KonabBlue, KonabBlueDark)
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
                                                fontSize = if (CurrencyFormatter.formatFCFA(totalRevenue).length > 14) 26.sp else 32.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(18.dp))

                                            // Responsive financial breakdown pills inside hero
                                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                                val compactHero = maxWidth < 330.dp
                                                val pillAmountSize = if (compactHero) 12.sp else 14.sp
                                                FlowRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    maxItemsInEachRow = if (compactHero) 2 else 3
                                                ) {
                                                    Surface(
                                                        color = Color.White.copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(
                                                                "Bénéfices estimés",
                                                                color = Color.White.copy(alpha = 0.8f),
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                CurrencyFormatter.formatFCFA(estimatedProfit),
                                                                color = Color(0xFF6EE7B7),
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = OutfitFontFamily,
                                                                fontSize = pillAmountSize,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        color = Color.White.copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(
                                                                "Panier moyen",
                                                                color = Color.White.copy(alpha = 0.8f),
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                CurrencyFormatter.formatFCFA(averageBasket),
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = OutfitFontFamily,
                                                                fontSize = pillAmountSize,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    Surface(
                                                        color = Color.White.copy(alpha = 0.14f),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = if (compactHero) Modifier.fillMaxWidth() else Modifier.weight(0.75f)
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            Text(
                                                                "Nb. Ventes",
                                                                color = Color.White.copy(alpha = 0.8f),
                                                                fontSize = 10.sp,
                                                                maxLines = 1
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                "${filteredSales.size}",
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = OutfitFontFamily,
                                                                fontSize = 15.sp,
                                                                maxLines = 1
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

                        // Real Interactive 7-Day Performance Bar Chart
                        item {
                            KonabStaggeredReveal(index = 1) {
                                var selectedBarIndex by remember { mutableIntStateOf(6) }
                                val chartGrow = remember { Animatable(0f) }
                                LaunchedEffect(selectedTab, weeklyChartData) {
                                    chartGrow.snapTo(0f)
                                    chartGrow.animateTo(
                                        1f,
                                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                                    )
                                }
                                val maxDayAmount = remember(weeklyChartData) {
                                    weeklyChartData.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
                                }
                                val hasAnyWeeklySale = remember(weeklyChartData) {
                                    weeklyChartData.any { it.amount > 0L }
                                }
                                val activeBar = weeklyChartData.getOrNull(selectedBarIndex) ?: weeklyChartData.lastOrNull()

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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Évolution des 7 derniers jours", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                activeBar?.let { pt ->
                                                    Text(
                                                        text = "${pt.fullDate} : ${CurrencyFormatter.formatFCFA(pt.amount)} (${pt.count} vente(s))",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = KonabBlue,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = KonabBlueContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Réel",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KonabBlue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (!hasAnyWeeklySale) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Aucune vente enregistrée sur les 7 derniers jours.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(14.dp)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(132.dp)
                                            ) {
                                                Canvas(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .pointerInput(weeklyChartData) {
                                                            detectTapGestures { offset ->
                                                                if (weeklyChartData.isNotEmpty()) {
                                                                    val slotW = size.width / weeklyChartData.size
                                                                    selectedBarIndex = (offset.x / slotW).toInt().coerceIn(0, weeklyChartData.lastIndex)
                                                                }
                                                            }
                                                        }
                                                ) {
                                                    val slotWidth = size.width / weeklyChartData.size.coerceAtLeast(1)
                                                    val barWidth = (slotWidth * 0.54f).coerceAtMost(28.dp.toPx())
                                                    val maxHeight = size.height - 10.dp.toPx()

                                                    weeklyChartData.forEachIndexed { i, point ->
                                                        val centerX = (i * slotWidth) + (slotWidth / 2f)
                                                        val x = centerX - (barWidth / 2f)
                                                        val ratio = (point.amount.toFloat() / maxDayAmount.toFloat()).coerceIn(0f, 1f)
                                                        val barHeight = if (point.amount > 0L) {
                                                            (maxHeight * ratio * chartGrow.value).coerceAtLeast(8.dp.toPx())
                                                        } else {
                                                            4.dp.toPx()
                                                        }
                                                        val y = size.height - barHeight

                                                        val barColor = when {
                                                            i == selectedBarIndex -> KonabBlue
                                                            point.isToday -> KonabGreen
                                                            point.amount > 0L -> Color(0xFF93C5FD)
                                                            else -> Color(0xFFE2E8F0)
                                                        }

                                                        drawRoundRect(
                                                            color = barColor,
                                                            topLeft = Offset(x, y),
                                                            size = Size(barWidth, barHeight),
                                                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                weeklyChartData.forEachIndexed { index, point ->
                                                    val isSelected = (index == selectedBarIndex)
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { selectedBarIndex = index }
                                                            .padding(vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = point.shortDay,
                                                            fontSize = 11.sp,
                                                            color = if (isSelected) KonabBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1
                                                        )
                                                        Text(
                                                            text = point.dayNumber,
                                                            fontSize = 9.5.sp,
                                                            color = if (isSelected) KonabBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                    }
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
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Valeur marchande", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                CurrencyFormatter.formatFCFA(totalStockSell),
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                color = KonabBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text("Coût d'acquisition", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                CurrencyFormatter.formatFCFA(totalStockCost),
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
                        // Period Selector Bar for Cash Flow
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    OutlinedButton(
                                        onClick = { showCashPeriodMenu = true },
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
                                        expanded = showCashPeriodMenu,
                                        onDismissRequest = { showCashPeriodMenu = false }
                                    ) {
                                        periods.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p, fontWeight = if (p == selectedPeriod) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    selectedPeriod = p
                                                    showCashPeriodMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = KonabBlueContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${filteredCashMovements.size} op.",
                                        color = KonabBlue,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

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
                                    Text("Bilan des flux de trésorerie ($selectedPeriod)", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Apports / Entrées", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "+${CurrencyFormatter.formatFCFA(totalCashIn)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = KonabSuccess,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text("Dépenses / Sorties", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "-${CurrencyFormatter.formatFCFA(totalCashOut)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = KonabRed,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
                                            fontSize = 17.sp,
                                            color = if (netCashMovement >= 0) KonabSuccess else KonabRed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                Text("${filteredCashMovements.size} opérations", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (filteredCashMovements.isEmpty()) {
                            item {
                                KonabEmptyState(
                                    icon = Icons.Default.AccountBalanceWallet,
                                    title = "Aucun mouvement enregistré",
                                    subtitle = "Vos apports et dépenses pour cette période apparaîtront ici.",
                                    accentColor = KonabBlue
                                )
                            }
                        } else {
                            itemsIndexed(filteredCashMovements.take(40), key = { index, m -> "${m.id}_$index" }) { idx, movement ->
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
                                                    Text(movement.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        "$typeLabel • ${CurrencyFormatter.formatDateTime(movement.timestamp)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "${if (isEntry) "+" else "-"}${CurrencyFormatter.formatFCFA(movement.amount)}",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = if (isEntry) KonabSuccess else KonabRed,
                                                maxLines = 1
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
