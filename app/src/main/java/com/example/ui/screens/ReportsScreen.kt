package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashMovementEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
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

    val periods = listOf("Aujourd'hui", "Cette semaine", "Ce mois", "Cette année")

    // Filter sales by selected period
    val filteredSales = remember(sales, selectedPeriod) {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
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
                cal.timeInMillis
            }
            "Ce mois" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.timeInMillis
            }
            else -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
        }
        sales.filter { it.timestamp >= startTime }
    }

    val totalRevenue = remember(filteredSales) { filteredSales.sumOf { it.totalAmount } }
    val totalCost = remember(filteredSales) { filteredSales.sumOf { it.totalCostPrice } }
    val estimatedProfit = maxOf(0L, totalRevenue - totalCost)

    // Best selling products calculation
    val topProducts = remember(saleItems) {
        val grouped = saleItems.groupBy { it.productName }
        grouped.map { (name, items) ->
            Triple(name, items.sumOf { it.quantity }, items.sumOf { it.totalPrice })
        }.sortedByDescending { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapports & Statistiques", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val reportText = StringBuilder()
                        reportText.append("=== RAPPORT D'ACTIVITÉ KONAB POS ===\n")
                        reportText.append("Période : $selectedPeriod\n")
                        reportText.append("Chiffre d'affaires : ${CurrencyFormatter.formatFCFA(totalRevenue)}\n")
                        reportText.append("Bénéfices estimés : ${CurrencyFormatter.formatFCFA(estimatedProfit)}\n")
                        reportText.append("Nombre de ventes : ${filteredSales.size}\n")
                        reportText.append("------------------------------------\n")
                        reportText.append("Meilleures ventes :\n")
                        topProducts.take(5).forEachIndexed { i, p ->
                            reportText.append("${i + 1}. ${p.first} (${p.second} vendus) : ${CurrencyFormatter.formatFCFA(p.third)}\n")
                        }
                        reportText.append("====================================")

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reportText.toString())
                            putExtra(Intent.EXTRA_SUBJECT, "Rapport commercial $selectedPeriod")
                        }
                        context.startActivity(Intent.createChooser(intent, "Exporter le rapport"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exporter le rapport", tint = KonabGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ventes", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Stock", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Dépenses", fontWeight = FontWeight.Bold) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Dropdown Selector
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(
                                onClick = { showPeriodMenu = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = KonabGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(selectedPeriod, fontWeight = FontWeight.SemiBold, color = KonabTextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = showPeriodMenu,
                                onDismissRequest = { showPeriodMenu = false }
                            ) {
                                periods.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = {
                                            selectedPeriod = p
                                            showPeriodMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Surface(
                            color = KonabGreenLight,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+12%", color = KonabGreenDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Revenue Headline Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Chiffre d'affaires",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatFCFA(totalRevenue),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp,
                                color = KonabGreenDark
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Bénéfices estimés", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        CurrencyFormatter.formatFCFA(estimatedProfit),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = KonabGreen
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Nombre de ventes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${filteredSales.size} ventes",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bar Chart Visual for Weekly Performance
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Évolution des ventes (Semaine)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom Bar Chart Canvas
                            val days = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
                            val sampleHeights = listOf(0.4f, 0.65f, 0.5f, 0.85f, 0.7f, 1.0f, 0.8f)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val barWidth = 22.dp.toPx()
                                    val totalWidth = size.width
                                    val space = (totalWidth - (barWidth * days.size)) / (days.size + 1)
                                    val maxHeight = size.height - 25.dp.toPx()

                                    sampleHeights.forEachIndexed { i, ratio ->
                                        val x = space + i * (barWidth + space)
                                        val barHeight = maxHeight * ratio
                                        val y = maxHeight - barHeight

                                        drawRoundRect(
                                            color = if (i == 5) androidx.compose.ui.graphics.Color(0xFF087A45) else androidx.compose.ui.graphics.Color(0xFF66BB6A),
                                            topLeft = Offset(x, y),
                                            size = Size(barWidth, barHeight),
                                            cornerRadius = CornerRadius(8f, 8f)
                                        )
                                    }
                                }

                                // Day Labels
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    days.forEach { day ->
                                        Text(
                                            text = day,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Best Sellers Header
                item {
                    Text(
                        text = "Meilleures ventes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (topProducts.isEmpty()) {
                    item {
                        Text(
                            text = "Effectuez des ventes pour afficher le classement de vos articles phares.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(topProducts.take(6)) { (name, qty, total) ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(KonabGoldContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = KonabGoldDark, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(CurrencyFormatter.formatFCFA(total), fontSize = 12.sp, color = KonabGreen)
                                    }
                                }

                                Surface(
                                    color = KonabGreenLight,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$qty vendus",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = KonabGreenDark,
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
}
