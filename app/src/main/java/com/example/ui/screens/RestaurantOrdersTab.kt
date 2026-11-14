package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.ui.components.KonabEmptyState
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

private enum class OrderFilter(val label: String) {
    ALL_OPEN("En cours"),
    PENDING_MANAGER("À valider"),
    KITCHEN("Cuisine"),
    READY_SERVED("Prêt / Servi"),
    PAID("Encaissées")
}

@Composable
fun RestaurantOrdersTab(
    orders: List<RestaurantOrderRecord>,
    activeRole: RestaurantStaffRole,
    onAdvanceStatus: (RestaurantOrderRecord, RestaurantOrderStatus) -> Unit,
    onOpenCheckout: (RestaurantOrderRecord) -> Unit,
    onViewReceiptById: (Long) -> Unit,
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
        // KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrderKpiMiniCard("À valider", "$pendingManagerCount", Color(0xFFD97706), Modifier.weight(1f))
                OrderKpiMiniCard("Cuisine", "$kitchenCount", Color(0xFFEA580C), Modifier.weight(1f))
                OrderKpiMiniCard("Prêt/Servi", "$readyServedCount", KonabBlue, Modifier.weight(1f))
                OrderKpiMiniCard("Encaissées", "$paidCount", KonabGreen, Modifier.weight(1f))
            }
        }

        // Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OrderFilter.entries) { f ->
                    val isSel = filter == f
                    FilterChip(
                        selected = isSel,
                        onClick = { filter = f },
                        label = {
                            Text(
                                text = f.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("chip_filter_${f.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEA580C),
                            selectedLabelColor = Color.White
                        )
                    )
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
                    onViewReceiptById = onViewReceiptById
                )
            }
        }
    }
}

@Composable
private fun OrderKpiMiniCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = accent
            )
            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
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
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_restaurant_order_${order.orderNumber}")
    ) {
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
                            Icon(Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transmettre Cuisine", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                            Text("Encaisser directement sur la plateforme", fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                        }
                    }

                    RestaurantOrderStatus.PAID -> {
                        val linkedSaleId = order.saleId
                        if (linkedSaleId != null && linkedSaleId > 0L) {
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
                    }

                    RestaurantOrderStatus.CANCELLED -> Unit
                }
            }
        }
    }
}
