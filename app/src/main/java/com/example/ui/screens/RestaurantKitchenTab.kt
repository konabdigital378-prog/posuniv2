package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.RestaurantKitchenHelper
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.ui.components.KonabEmptyState
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.delay

private enum class KitchenViewFilter(val label: String) {
    QR_LIVE("QR Tables (En direct)"),
    ALL_ACTIVE("Toutes actives"),
    READY_TO_SERVE("Prêtes (Serveur notifié)")
}

@Composable
fun RestaurantKitchenTab(
    orders: List<RestaurantOrderRecord>,
    isSyncing: Boolean,
    lastSyncAt: Long,
    onRefreshNow: () -> Unit,
    onStartPreparation: (RestaurantOrderRecord) -> Unit,
    onMarkReadyToServe: (RestaurantOrderRecord) -> Unit,
    onMarkServed: (RestaurantOrderRecord) -> Unit,
    onNotifyWaiterAgain: (RestaurantOrderRecord) -> Unit,
    onOpenTablesQr: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(KitchenViewFilter.QR_LIVE) }

    // Ticker for live elapsed preparation times every 15 seconds
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val qrLiveOrders = remember(orders) { RestaurantKitchenHelper.filterActiveQrOrders(orders) }
    val allActiveKitchenOrders = remember(orders) { RestaurantKitchenHelper.filterAllActiveKitchenOrders(orders) }
    val readyOrders = remember(orders) { RestaurantKitchenHelper.filterReadyToServeOrders(orders) }
    val inPrepCount = remember(allActiveKitchenOrders) {
        allActiveKitchenOrders.count { it.status == RestaurantOrderStatus.IN_PREPARATION }
    }

    val displayedOrders = remember(selectedFilter, qrLiveOrders, allActiveKitchenOrders, readyOrders) {
        when (selectedFilter) {
            KitchenViewFilter.QR_LIVE -> qrLiveOrders
            KitchenViewFilter.ALL_ACTIVE -> allActiveKitchenOrders
            KitchenViewFilter.READY_TO_SERVE -> readyOrders
        }
    }

    // Pulsing live indicator
    val infiniteTransition = rememberInfiniteTransition(label = "kitchen_live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("restaurant_kitchen_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real-Time Kitchen Header Banner
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1E293B), Color(0xFF7C2D12), Color(0xFFEA580C))
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .alpha(pulseAlpha)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80))
                                )
                                Text(
                                    text = "CUISINE EN TEMPS RÉEL",
                                    color = Color(0xFF86EFAC),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = "Commandes QR Tables en direct",
                                color = Color.White,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Actualisation auto (4s) • Dernière sync : ${CurrencyFormatter.formatTimeOnly(lastSyncAt)}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onRefreshNow,
                            enabled = !isSyncing,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_kitchen_live_refresh")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Actualiser commandes cuisine"
                                )
                            }
                        }
                    }
                }
            }
        }

        // KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KitchenKpiCard(
                    title = "QR en attente",
                    value = "${qrLiveOrders.size}",
                    accent = Color(0xFFEA580C),
                    modifier = Modifier.weight(1f)
                )
                KitchenKpiCard(
                    title = "En cuisson",
                    value = "$inPrepCount",
                    accent = Color(0xFF9333EA),
                    modifier = Modifier.weight(1f)
                )
                KitchenKpiCard(
                    title = "Prêtes à servir",
                    value = "${readyOrders.size}",
                    accent = KonabGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(KitchenViewFilter.entries) { filterOption ->
                    val isSelected = selectedFilter == filterOption
                    val count = when (filterOption) {
                        KitchenViewFilter.QR_LIVE -> qrLiveOrders.size
                        KitchenViewFilter.ALL_ACTIVE -> allActiveKitchenOrders.size
                        KitchenViewFilter.READY_TO_SERVE -> readyOrders.size
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filterOption },
                        label = {
                            Text(
                                text = "${filterOption.label} ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("chip_kitchen_${filterOption.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filterOption == KitchenViewFilter.READY_TO_SERVE) KonabGreen else Color(0xFFEA580C),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (displayedOrders.isEmpty()) {
            item {
                val emptyTitle = when (selectedFilter) {
                    KitchenViewFilter.QR_LIVE -> "Aucune commande QR table en attente"
                    KitchenViewFilter.ALL_ACTIVE -> "Aucune commande en cours en cuisine"
                    KitchenViewFilter.READY_TO_SERVE -> "Aucun plat en attente de service"
                }
                val emptySubtitle = when (selectedFilter) {
                    KitchenViewFilter.QR_LIVE ->
                        "Dès qu'un client scanne le QR code d'une table et valide ses plats, la commande s'affiche ici automatiquement en temps réel."
                    KitchenViewFilter.ALL_ACTIVE ->
                        "Toutes les commandes QR et caisse envoyées en cuisine sont à jour."
                    KitchenViewFilter.READY_TO_SERVE ->
                        "Appuyez sur « Prêt à servir » sur une commande en cuisine pour notifier le serveur immédiatement."
                }
                KonabEmptyState(
                    icon = Icons.Default.SoupKitchen,
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    accentColor = Color(0xFFEA580C),
                    actionLabel = "Voir QR Codes des Tables",
                    onAction = onOpenTablesQr
                )
            }
        } else {
            items(displayedOrders, key = { it.id }) { order ->
                KitchenTicketCard(
                    order = order,
                    nowMillis = nowMillis,
                    onStartPreparation = { onStartPreparation(order) },
                    onMarkReadyToServe = {
                        onMarkReadyToServe(order)
                        Toast.makeText(
                            context,
                            "🔔 Serveur notifié : ${order.tableName} (${order.orderNumber}) prêt à servir !",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onMarkServed = { onMarkServed(order) },
                    onNotifyWaiterAgain = {
                        onNotifyWaiterAgain(order)
                        Toast.makeText(
                            context,
                            "🔔 Rappel envoyé au serveur pour ${order.tableName} !",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun KitchenKpiCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = accent
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KitchenTicketCard(
    order: RestaurantOrderRecord,
    nowMillis: Long,
    onStartPreparation: () -> Unit,
    onMarkReadyToServe: () -> Unit,
    onMarkServed: () -> Unit,
    onNotifyWaiterAgain: () -> Unit
) {
    val isQrOrder = order.source.equals("QR_TABLE", ignoreCase = true)
    val isReady = order.status == RestaurantOrderStatus.READY
    val isInPrep = order.status == RestaurantOrderStatus.IN_PREPARATION

    val accentColor by animateColorAsState(
        targetValue = when (order.status) {
            RestaurantOrderStatus.PENDING_MANAGER -> Color(0xFFD97706)
            RestaurantOrderStatus.SENT_TO_KITCHEN -> Color(0xFFEA580C)
            RestaurantOrderStatus.IN_PREPARATION -> Color(0xFF9333EA)
            RestaurantOrderStatus.READY -> KonabGreen
            else -> KonabBlue
        },
        label = "kitchen_ticket_accent"
    )

    val elapsedText = remember(order.createdAt, nowMillis) {
        RestaurantKitchenHelper.formatElapsedLabel(order.createdAt, nowMillis)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_kitchen_ticket_${order.orderNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Table + Order Info + Elapsed Time & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accentColor,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = order.tableName,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp
                            )
                            if (isQrOrder) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "Commande QR Table",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "QR",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${order.orderNumber} • ${order.customerName}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${CurrencyFormatter.formatTimeOnly(order.createdAt)} ($elapsedText)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (order.status == RestaurantOrderStatus.PENDING_MANAGER && isQrOrder) {
                            "NOUVELLE QR"
                        } else {
                            order.status.shortBadge
                        },
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Dish Items Block (high-readability KDS style)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    order.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    color = accentColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${item.quantity}x",
                                        color = accentColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (item.note.isNotBlank()) {
                                        Text(
                                            text = "👉 ${item.note}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF9A3412),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = CurrencyFormatter.formatFCFA(item.totalPrice),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (order.notes.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                                contentDescription = null,
                                tint = Color(0xFF9A3412),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Note table : ${order.notes}",
                                fontSize = 12.sp,
                                color = Color(0xFF9A3412),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Action Buttons
            if (!isReady) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isInPrep) {
                        OutlinedButton(
                            onClick = onStartPreparation,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF9333EA)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF9333EA)),
                            modifier = Modifier
                                .weight(0.85f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_kitchen_prep_${order.orderNumber}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Préparer", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Button(
                        onClick = onMarkReadyToServe,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(if (isInPrep) 1f else 1.35f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_kitchen_ready_to_serve_${order.orderNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Notifier le serveur",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Prêt à servir",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onNotifyWaiterAgain,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KonabGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabGreenDark),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_kitchen_reping_waiter_${order.orderNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rappeler serveur", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = onMarkServed,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_kitchen_served_${order.orderNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RoomService,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Marquer Servi", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
