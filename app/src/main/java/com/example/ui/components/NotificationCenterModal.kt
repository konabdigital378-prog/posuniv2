package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.NotificationEntity
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabGreenDark
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationCenterModal(
    notifications: List<NotificationEntity>,
    unreadCount: Int,
    onDismiss: () -> Unit,
    onMarkAsRead: (Long) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onDeleteNotification: (Long) -> Unit,
    onClearAll: () -> Unit,
    onTriggerTestNotification: (String) -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "UNREAD" -> notifications.filter { !it.isRead }
            "STOCK" -> notifications.filter { it.type == "STOCK_ALERT" }
            "SALE" -> notifications.filter { it.type == "SALE_COMPLETED" }
            "DEBT" -> notifications.filter { it.type == "DEBT_ALERT" }
            "RESTO" -> notifications.filter { it.type.startsWith("RESTAURANT_") }
            "SYNC" -> notifications.filter { it.type == "SUPABASE_SYNC" }
            else -> notifications
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("notification_center_modal"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KonabGreen)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
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
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Notifications",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = if (unreadCount > 0) "$unreadCount non lue(s)" else "Toutes vos alertes sont à jour",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("btn_close_notifications")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Action Bar (Mark all read & Clear all)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllAsRead,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_mark_all_read")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tout marquer comme lu", fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (notifications.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_clear_all_notifications")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tout effacer", fontSize = 13.sp)
                        }
                    }
                }

                // Filter Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to "Toutes (${notifications.size})",
                        "UNREAD" to "Non lues ($unreadCount)",
                        "STOCK" to "Stock (${notifications.count { it.type == "STOCK_ALERT" }})",
                        "RESTO" to "Cuisine (${notifications.count { it.type.startsWith("RESTAURANT_") }})",
                        "SALE" to "Ventes (${notifications.count { it.type == "SALE_COMPLETED" }})",
                        "DEBT" to "Dettes (${notifications.count { it.type == "DEBT_ALERT" }})",
                        "SYNC" to "Cloud (${notifications.count { it.type == "SUPABASE_SYNC" }})"
                    )

                    items(filters) { (id, label) ->
                        FilterChip(
                            selected = selectedFilter == id,
                            onClick = { selectedFilter = id },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (selectedFilter == id) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KonabGreen.copy(alpha = 0.15f),
                                selectedLabelColor = KonabGreenDark
                            )
                        )
                    }
                }

                // Quick Test Notification Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = KonabGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tester une notification en direct :",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onTriggerTestNotification("STOCK") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_stock_notif")
                            ) {
                                Text("⚠️ Stock", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onTriggerTestNotification("SALE") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_sale_notif")
                            ) {
                                Text("💰 Vente", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onTriggerTestNotification("DEBT") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_debt_notif")
                            ) {
                                Text("💳 Dette", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onTriggerTestNotification("SYNC") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_sync_notif")
                            ) {
                                Text("☁️ Cloud", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Notifications List or Empty State
                if (filteredNotifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Aucune notification",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Vous recevrez des alertes instantanées lors des ventes, stocks faibles, dettes et synchronisations cloud.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredNotifications, key = { it.id }) { notif ->
                            NotificationCardItem(
                                notification = notif,
                                onMarkAsRead = { onMarkAsRead(notif.id) },
                                onDelete = { onDeleteNotification(notif.id) },
                                onAction = { route ->
                                    onDismiss()
                                    onNavigateToRoute(route)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    notification: NotificationEntity,
    onMarkAsRead: () -> Unit,
    onDelete: () -> Unit,
    onAction: (String) -> Unit
) {
    val (icon, iconColor, bgTint) = when (notification.type) {
        "STOCK_ALERT" -> Triple(Icons.Default.WarningAmber, Color(0xFFD97706), Color(0xFFFEF3C7))
        "SALE_COMPLETED" -> Triple(Icons.Default.CheckCircle, KonabGreen, Color(0xFFD1FAE5))
        "DEBT_ALERT" -> Triple(Icons.Default.CreditCard, Color(0xFFEA580C), Color(0xFFFFEDD5))
        "RESTAURANT_READY" -> Triple(Icons.Default.NotificationsActive, KonabGreen, Color(0xFFD1FAE5))
        "RESTAURANT_QR_ORDER", "RESTAURANT_ORDER" -> Triple(Icons.Default.SoupKitchen, Color(0xFFEA580C), Color(0xFFFFEDD5))
        "SUPABASE_SYNC" -> Triple(Icons.Default.CloudDone, Color(0xFF0284C7), Color(0xFFE0F2FE))
        else -> Triple(Icons.Default.Info, Color(0xFF6B7280), Color(0xFFF3F4F6))
    }

    val formattedTime = remember(notification.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val now = System.currentTimeMillis()
        val diff = now - notification.timestamp
        when {
            diff < 60_000 -> "À l'instant"
            diff < 3600_000 -> "Il y a ${diff / 60_000} min"
            diff < 86400_000 -> "Aujourd'hui à ${sdf.format(Date(notification.timestamp))}"
            else -> dateSdf.format(Date(notification.timestamp))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (!notification.isRead) onMarkAsRead()
                notification.actionRoute?.let { onAction(it) }
            }
            .testTag("notification_card_${notification.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (!notification.isRead) {
            androidx.compose.foundation.BorderStroke(1.5.dp, iconColor.copy(alpha = 0.4f))
        } else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(bgTint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Body
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom actions for this card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (notification.actionRoute != null) {
                        val actionLabel = when (notification.actionRoute) {
                            "stock" -> "Voir le stock"
                            "customers" -> "Gérer le client"
                            "reports" -> "Voir la vente"
                            "settings" -> "Paramètres Cloud"
                            "restaurant" -> "Ouvrir Cuisine / Service"
                            else -> "Ouvrir"
                        }
                        Button(
                            onClick = { onAction(notification.actionRoute) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = iconColor.copy(alpha = 0.15f),
                                contentColor = iconColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!notification.isRead) {
                            IconButton(
                                onClick = onMarkAsRead,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = "Marquer lu",
                                    tint = KonabGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
