package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

private enum class OrderFilter(val label: String) {
    ALL_OPEN("En cours"),
    PENDING_MANAGER("À valider"),
    KITCHEN("Cuisine"),
    READY_SERVED("Prêts"),
    PAID("Payées")
}

@Composable
fun RestaurantOrdersTab(
    orders: List<RestaurantOrderRecord>,
    activeRole: RestaurantStaffRole,
    onAdvanceStatus: (RestaurantOrderRecord, RestaurantOrderStatus) -> Unit,
    onOpenCheckout: (RestaurantOrderRecord) -> Unit,
    onViewReceiptById: (Long, RestaurantOrderRecord?) -> Unit,
    onOpenPos: () -> Unit
) {
    var filter by remember(activeRole) {
        mutableStateOf(
            if (activeRole == RestaurantStaffRole.KITCHEN) OrderFilter.KITCHEN else OrderFilter.ALL_OPEN
        )
    }

    val pendingManagerCount = remember(orders) { orders.count { it.status == RestaurantOrderStatus.PENDING_MANAGER } }
    val kitchenCount = remember(orders) {
        orders.count { it.status == RestaurantOrderStatus.SENT_TO_KITCHEN || it.status == RestaurantOrderStatus.IN_PREPARATION }
    }
    val readyServedCount = remember(orders) {
        orders.count { it.status == RestaurantOrderStatus.READY || it.status == RestaurantOrderStatus.SERVED }
    }
    val paidCount = remember(orders) { orders.count { it.status == RestaurantOrderStatus.PAID } }

    val filteredOrders = remember(orders, filter) {
        when (filter) {
            OrderFilter.ALL_OPEN -> orders.filter { it.status.isOpenOrder }
            OrderFilter.PENDING_MANAGER -> orders.filter { it.status == RestaurantOrderStatus.PENDING_MANAGER }
            OrderFilter.KITCHEN -> orders.filter {
                it.status == RestaurantOrderStatus.SENT_TO_KITCHEN || it.status == RestaurantOrderStatus.IN_PREPARATION
            }
            OrderFilter.READY_SERVED -> orders.filter {
                it.status == RestaurantOrderStatus.READY || it.status == RestaurantOrderStatus.SERVED
            }
            OrderFilter.PAID -> orders.filter { it.status == RestaurantOrderStatus.PAID }
        }
    }
    val readyNow = remember(orders) { orders.filter { it.status == RestaurantOrderStatus.READY } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("restaurant_orders_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Interactive Restaurant KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrderKpiMiniCard(
                    title = "À valider",
                    value = "$pendingManagerCount",
                    icon = Icons.Default.HourglassTop,
                    accent = Color(0xFFD97706),
                    isSelected = filter == OrderFilter.PENDING_MANAGER,
                    onClick = { filter = OrderFilter.PENDING_MANAGER },
                    modifier = Modifier.weight(1f)
                )
                OrderKpiMiniCard(
                    title = "Cuisine",
                    value = "$kitchenCount",
                    icon = Icons.Default.SoupKitchen,
                    accent = Color(0xFFEA580C),
                    isSelected = filter == OrderFilter.KITCHEN,
                    onClick = { filter = OrderFilter.KITCHEN },
                    modifier = Modifier.weight(1f)
                )
                OrderKpiMiniCard(
                    title = "Prêt/Servi",
                    value = "$readyServedCount",
                    icon = Icons.Default.RoomService,
                    accent = KonabBlue,
                    isSelected = filter == OrderFilter.READY_SERVED,
                    onClick = { filter = OrderFilter.READY_SERVED },
                    modifier = Modifier.weight(1f)
                )
                OrderKpiMiniCard(
                    title = "Payées",
                    value = "$paidCount",
                    icon = Icons.Default.CheckCircle,
                    accent = KonabGreen,
                    isSelected = filter == OrderFilter.PAID,
                    onClick = { filter = OrderFilter.PAID },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Compact Filter Pills
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(OrderFilter.entries) { f ->
                    val isSel = filter == f
                    val count = when (f) {
                        OrderFilter.ALL_OPEN -> orders.count { it.status.isOpenOrder }
                        OrderFilter.PENDING_MANAGER -> pendingManagerCount
                        OrderFilter.KITCHEN -> kitchenCount
                        OrderFilter.READY_SERVED -> readyServedCount
                        OrderFilter.PAID -> paidCount
                    }
                    Surface(
                        onClick = { filter = f },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) Color(0xFFEA580C) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSel) Color(0xFFEA580C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .konabPressScale(pressedScale = 0.96f)
                            .testTag("chip_filter_${f.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = f.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (count > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSel) Color.White.copy(alpha = 0.25f) else Color(0xFFEA580C).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSel) Color.White else Color(0xFFEA580C),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ready-to-serve Waiter Alert Banner
        if (readyNow.isNotEmpty() && filter != OrderFilter.PAID) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = KonabGreenLight),
                    border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_waiter_ready_orders")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Serveur notifié",
                            tint = KonabGreenDark,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${readyNow.size} commande(s) prête(s) à servir !",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = KonabGreenDark
                            )
                            Text(
                                text = "Tables : ${readyNow.joinToString(", ") { it.tableName }} • Serveur notifié",
                                fontSize = 11.5.sp,
                                color = KonabGreenDark.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        if (filteredOrders.isEmpty()) {
            item {
                KonabEmptyState(
                    icon = Icons.Default.RestaurantMenu,
                    title = "Aucune commande dans cet onglet",
                    subtitle = "Les commandes envoyées depuis les QR Codes de tables ou depuis la caisse apparaissent ici en temps réel.",
                    accentColor = Color(0xFFEA580C),
                    actionLabel = "Prendre une commande (Caisse)",
                    onAction = onOpenPos
                )
            }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                RestaurantOrderCard(
                    order = order,
                    onAdvanceStatus = onAdvanceStatus,
                    onOpenCheckout = onOpenCheckout,
                    onViewReceiptById = { saleId -> onViewReceiptById(saleId, order) }
                )
            }
        }
    }
}

@Composable
private fun OrderKpiMiniCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, accent.copy(alpha = if (isSelected) 0.7f else 0.28f)),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = modifier.konabPressScale(pressedScale = 0.96f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = value,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = accent,
                maxLines = 1
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RestaurantOrderCard(
    order: RestaurantOrderRecord,
    onAdvanceStatus: (RestaurantOrderRecord, RestaurantOrderStatus) -> Unit,
    onOpenCheckout: (RestaurantOrderRecord) -> Unit,
    onViewReceiptById: (Long) -> Unit
) {
    val statusColor = when (order.status) {
        RestaurantOrderStatus.PENDING_MANAGER -> Color(0xFFD97706)
        RestaurantOrderStatus.SENT_TO_KITCHEN -> Color(0xFFEA580C)
        RestaurantOrderStatus.IN_PREPARATION -> Color(0xFF9333EA)
        RestaurantOrderStatus.READY -> KonabGreen
        RestaurantOrderStatus.SERVED -> KonabBlue
        RestaurantOrderStatus.PAID -> KonabGreenDark
        RestaurantOrderStatus.CANCELLED -> KonabRed
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, statusColor.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_restaurant_order_${order.orderNumber}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(statusColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = order.tableName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "${order.orderNumber} • ${order.customerName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        val sourceLabel = when (order.source) {
                            "QR_TABLE" -> "Scanné via QR Table"
                            "WAITER" -> "Serveur : ${order.createdByName}"
                            else -> "Caisse : ${order.createdByName}"
                        }
                        Text(
                            text = "$sourceLabel • ${CurrencyFormatter.formatTimeOnly(order.createdAt)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = order.status.shortBadge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Dish Lines
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    order.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.quantity}x ${item.productName}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        text = "👉 ${item.note}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9A3412),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Text(
                                text = CurrencyFormatter.formatFCFA(item.totalPrice),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                    if (order.notes.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Note client/salle : ${order.notes}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF9A3412),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Total & Attribution
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (order.assignedToKitchenBy.isNotBlank()) {
                        "Transmis cuisine par : ${order.assignedToKitchenBy}"
                    } else {
                        "${order.items.sumOf { it.quantity }} plat(s)"
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatFCFA(order.totalAmount),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = KonabGreen
                )
            }

            // Workflow Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (order.status) {
                    RestaurantOrderStatus.PENDING_MANAGER -> {
                        OutlinedButton(
                            onClick = { onAdvanceStatus(order, RestaurantOrderStatus.CANCELLED) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabRed),
                            modifier = Modifier
                                .weight(0.75f)
                                .height(48.dp)
                                .testTag("btn_cancel_${order.orderNumber}")
                        ) {
                            Text("Annuler", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onAdvanceStatus(order, RestaurantOrderStatus.SENT_TO_KITCHEN) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("btn_transmit_kitchen_${order.orderNumber}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Valider & Envoyer Reçu", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    RestaurantOrderStatus.SENT_TO_KITCHEN -> {
                        Button(
                            onClick = { onAdvanceStatus(order, RestaurantOrderStatus.IN_PREPARATION) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_start_prep_${order.orderNumber}")
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Démarrer en Cuisine", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    RestaurantOrderStatus.IN_PREPARATION -> {
                        Button(
                            onClick = { onAdvanceStatus(order, RestaurantOrderStatus.READY) },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_mark_ready_${order.orderNumber}")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prêt à servir", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    RestaurantOrderStatus.READY -> {
                        OutlinedButton(
                            onClick = { onAdvanceStatus(order, RestaurantOrderStatus.SERVED) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_mark_served_${order.orderNumber}")
                        ) {
                            Text("Marquer Servi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onOpenCheckout(order) },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_collect_ready_${order.orderNumber}")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Encaisser", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }

                    RestaurantOrderStatus.SERVED -> {
                        Button(
                            onClick = { onOpenCheckout(order) },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_collect_served_${order.orderNumber}")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Encaisser la table", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }

                    RestaurantOrderStatus.PAID -> {
                        val linkedSaleId = order.saleId ?: 0L
                        OutlinedButton(
                            onClick = { onViewReceiptById(linkedSaleId) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_view_order_receipt_${order.orderNumber}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = KonabBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voir le Reçu de Caisse", fontWeight = FontWeight.Bold, color = KonabBlue)
                        }
                    }

                    RestaurantOrderStatus.CANCELLED -> Unit
                }
            }
            }
        }
    }
}
