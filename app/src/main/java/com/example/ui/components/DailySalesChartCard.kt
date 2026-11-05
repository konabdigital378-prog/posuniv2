package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SaleEntity
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DailyChartPoint(
    val label: String,
    val subLabel: String,
    val fullDateLabel: String,
    val amount: Long,
    val count: Int,
    val isCurrentPeriod: Boolean
)

enum class ChartPeriod {
    LAST_7_DAYS,
    TODAY_HOURS
}

@Composable
fun DailySalesChartCard(
    sales: List<SaleEntity>,
    onViewReports: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.LAST_7_DAYS) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // Prepare 7-day data
    val last7DaysData = remember(sales) {
        val calendar = Calendar.getInstance()
        val points = mutableListOf<DailyChartPoint>()
        val dayFormat = SimpleDateFormat("EEE", Locale.FRENCH)
        val numFormat = SimpleDateFormat("dd", Locale.getDefault())
        val fullFormat = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH)

        // Generate past 6 days + today (7 points total)
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = c.timeInMillis
            val endMs = startMs + (24 * 60 * 60 * 1000) - 1

            val daySales = sales.filter { it.timestamp in startMs..endMs }
            val amount = daySales.sumOf { it.totalAmount }
            val count = daySales.size

            val rawDay = dayFormat.format(c.time).replace(".", "").replaceFirstChar { it.uppercase() }
            val dayNum = numFormat.format(c.time)
            val fullDate = fullFormat.format(c.time).replaceFirstChar { it.uppercase() }

            points.add(
                DailyChartPoint(
                    label = rawDay,
                    subLabel = dayNum,
                    fullDateLabel = fullDate,
                    amount = amount,
                    count = count,
                    isCurrentPeriod = (i == 0)
                )
            )
        }
        points
    }

    // Prepare Today's Hourly breakdown (8h-20h buckets)
    val todayHourlyData = remember(sales) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val buckets = listOf(
            8 to 10,
            10 to 12,
            12 to 14,
            14 to 16,
            16 to 18,
            18 to 20,
            20 to 22
        )

        buckets.map { (startH, endH) ->
            val bStart = startOfToday + (startH * 3600 * 1000L)
            val bEnd = startOfToday + (endH * 3600 * 1000L) - 1
            val bucketSales = sales.filter { it.timestamp in bStart..bEnd }
            val amount = bucketSales.sumOf { it.totalAmount }
            val count = bucketSales.size
            val isCurrent = currentHour in startH until endH

            DailyChartPoint(
                label = "${startH}h",
                subLabel = "${endH}h",
                fullDateLabel = "Créneau $startH:00 - $endH:00",
                amount = amount,
                count = count,
                isCurrentPeriod = isCurrent
            )
        }
    }

    val currentData = if (selectedPeriod == ChartPeriod.LAST_7_DAYS) last7DaysData else todayHourlyData

    // Auto-select the last point (today / current period) on switch
    LaunchedEffect(selectedPeriod, currentData) {
        selectedIndex = currentData.indexOfLast { it.isCurrentPeriod }.coerceAtLeast(currentData.lastIndex)
    }

    val selectedPoint = currentData.getOrNull(selectedIndex) ?: currentData.lastOrNull()
    val totalPeriodAmount = currentData.sumOf { it.amount }
    val totalPeriodCount = currentData.sumOf { it.count }
    val maxAmount = currentData.maxOfOrNull { it.amount }?.coerceAtLeast(10000L) ?: 10000L

    // Best day / peak point
    val peakPoint = currentData.maxByOrNull { it.amount }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, KonabBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_daily_sales_chart")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title, Total & Period Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(KonabGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = KonabGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ventes journalières",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Chiffre d'affaires en FCFA",
                            style = MaterialTheme.typography.bodySmall,
                            color = KonabTextSecondary
                        )
                    }
                }

                // Period Switch Tabs
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KonabBackground,
                    border = BorderStroke(1.dp, KonabBorder)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        PeriodTabButton(
                            text = "7 jours",
                            isSelected = selectedPeriod == ChartPeriod.LAST_7_DAYS,
                            onClick = { selectedPeriod = ChartPeriod.LAST_7_DAYS }
                        )
                        PeriodTabButton(
                            text = "Aujourd'hui",
                            isSelected = selectedPeriod == ChartPeriod.TODAY_HOURS,
                            onClick = { selectedPeriod = ChartPeriod.TODAY_HOURS }
                        )
                    }
                }
            }

            // Highlight Tooltip / Banner for the active/selected bar
            selectedPoint?.let { pt ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = KonabGreenLight.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pt.fullDateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KonabGreenDark
                                )
                                if (pt.isCurrentPeriod) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = KonabGreen,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "En cours",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${pt.count} vente(s) enregistrée(s)",
                                fontSize = 11.sp,
                                color = KonabTextSecondary
                            )
                        }

                        Text(
                            text = CurrencyFormatter.formatFCFA(pt.amount),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KonabGreen
                        )
                    }
                }
            }

            // Native Jetpack Compose Canvas Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                // Animated progress for bar heights
                val animProgress by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 650),
                    label = "bar_anim"
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentData) {
                            detectTapGestures { offset ->
                                val count = currentData.size
                                if (count > 0) {
                                    val slotWidth = size.width / count
                                    val index = (offset.x / slotWidth).toInt().coerceIn(0, count - 1)
                                    selectedIndex = index
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 4.dp.toPx()
                    val usableHeight = height - bottomPadding - 12.dp.toPx()

                    // Draw 3 horizontal grid reference lines
                    val lineCount = 3
                    for (i in 0..lineCount) {
                        val y = height - bottomPadding - (usableHeight * (i.toFloat() / lineCount))
                        drawLine(
                            color = KonabBorder.copy(alpha = 0.7f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    val count = currentData.size
                    if (count == 0) return@Canvas

                    val slotWidth = width / count
                    val barWidth = (slotWidth * 0.52f).coerceAtMost(32.dp.toPx())

                    // Draw Bars
                    currentData.forEachIndexed { index, point ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val barLeft = centerX - (barWidth / 2f)

                        val ratio = (point.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
                        val barHeight = ((usableHeight * ratio) * animProgress).coerceAtLeast(6.dp.toPx())
                        val barTop = height - bottomPadding - barHeight

                        val isSelected = (index == selectedIndex)

                        // Bar Gradient Brush
                        val barBrush = when {
                            isSelected -> Brush.verticalGradient(
                                colors = listOf(KonabGold, KonabGoldDark),
                                startY = barTop,
                                endY = height - bottomPadding
                            )
                            point.isCurrentPeriod -> Brush.verticalGradient(
                                colors = listOf(KonabGreen, KonabGreenDark),
                                startY = barTop,
                                endY = height - bottomPadding
                            )
                            point.amount > 0 -> Brush.verticalGradient(
                                colors = listOf(KonabGreen.copy(alpha = 0.75f), KonabGreenDark.copy(alpha = 0.9f)),
                                startY = barTop,
                                endY = height - bottomPadding
                            )
                            else -> Brush.verticalGradient(
                                colors = listOf(KonabSurfaceVariant, KonabBorder),
                                startY = barTop,
                                endY = height - bottomPadding
                            )
                        }

                        // Draw Rounded Pill Bar
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        // If selected, draw an active border & top indicator dot
                        if (isSelected) {
                            drawRoundRect(
                                color = KonabGoldDark,
                                topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                size = Size(barWidth + 2.dp.toPx(), barHeight + 2.dp.toPx()),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Indicator glow pin above the bar
                            drawCircle(
                                color = KonabGold,
                                radius = 3.5.dp.toPx(),
                                center = Offset(centerX, barTop - 6.dp.toPx())
                            )
                        }
                    }

                    // Optional smooth trend spline across non-zero points
                    if (currentData.any { it.amount > 0 }) {
                        val trendPath = Path()
                        var first = true

                        currentData.forEachIndexed { index, point ->
                            val centerX = (index * slotWidth) + (slotWidth / 2f)
                            val ratio = (point.amount.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
                            val barHeight = ((usableHeight * ratio) * animProgress).coerceAtLeast(6.dp.toPx())
                            val barTop = height - bottomPadding - barHeight

                            if (first) {
                                trendPath.moveTo(centerX, barTop)
                                first = false
                            } else {
                                trendPath.lineTo(centerX, barTop)
                            }
                        }

                        drawPath(
                            path = trendPath,
                            color = KonabGreen.copy(alpha = 0.4f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // X-Axis Labels Row below Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                currentData.forEachIndexed { index, point ->
                    val isSelected = (index == selectedIndex)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedIndex = index }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = point.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) KonabGreenDark else KonabTextSecondary
                        )
                        Text(
                            text = point.subLabel,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) KonabGreenDark else KonabTextMuted
                        )
                    }
                }
            }

            // Bottom Summary & Quick Metrics Footer
            HorizontalDivider(color = KonabBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = KonabGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Total période : ${CurrencyFormatter.formatFCFA(totalPeriodAmount)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (onViewReports != null) {
                    TextButton(
                        onClick = onViewReports,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Rapports détaillés",
                            color = KonabGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) KonabGreen else Color.Transparent
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else KonabTextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
