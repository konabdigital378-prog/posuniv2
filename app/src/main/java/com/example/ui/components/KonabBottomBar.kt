package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*

@Composable
fun KonabBottomBar(
    currentScreen: KonabScreen,
    onNavigate: (KonabScreen) -> Unit,
    cartItemCount: Int = 0
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = KonabBlue,
        selectedTextColor = KonabBlue,
        indicatorColor = KonabBlueContainer,
        unselectedIconColor = KonabTextSecondary,
        unselectedTextColor = KonabTextSecondary
    )

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, KonabBorder),
        shadowElevation = 8.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets.navigationBars,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                modifier = Modifier.testTag("nav_dashboard"),
                selected = currentScreen == KonabScreen.DASHBOARD,
                onClick = { onNavigate(KonabScreen.DASHBOARD) },
                icon = {
                    Icon(
                        imageVector = if (currentScreen == KonabScreen.DASHBOARD) Icons.Default.Dashboard else Icons.Default.Home,
                        contentDescription = "Accueil"
                    )
                },
                label = {
                    Text(
                        text = "Accueil",
                        fontWeight = if (currentScreen == KonabScreen.DASHBOARD) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationBarItem(
                modifier = Modifier.testTag("nav_pos"),
                selected = currentScreen == KonabScreen.POS || currentScreen == KonabScreen.PAYMENT,
                onClick = { onNavigate(KonabScreen.POS) },
                icon = {
                    BadgedBox(badge = {
                        if (cartItemCount > 0) {
                            Badge(
                                containerColor = KonabOrange,
                                contentColor = Color.White
                            ) {
                                Text("$cartItemCount", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }) {
                        Icon(Icons.Default.PointOfSale, contentDescription = "Ventes")
                    }
                },
                label = {
                    Text(
                        text = "Caisse",
                        fontWeight = if (currentScreen == KonabScreen.POS || currentScreen == KonabScreen.PAYMENT) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationBarItem(
                modifier = Modifier.testTag("nav_stock"),
                selected = currentScreen == KonabScreen.STOCK,
                onClick = { onNavigate(KonabScreen.STOCK) },
                icon = { Icon(Icons.Default.Inventory2, contentDescription = "Produits") },
                label = {
                    Text(
                        text = "Stock",
                        fontWeight = if (currentScreen == KonabScreen.STOCK) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationBarItem(
                modifier = Modifier.testTag("nav_reports"),
                selected = currentScreen == KonabScreen.REPORTS,
                onClick = { onNavigate(KonabScreen.REPORTS) },
                icon = { Icon(Icons.Default.Assessment, contentDescription = "Rapports") },
                label = {
                    Text(
                        text = "Stats",
                        fontWeight = if (currentScreen == KonabScreen.REPORTS) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationBarItem(
                modifier = Modifier.testTag("nav_more"),
                colors = navItemColors,
                selected = showMoreMenu || currentScreen in listOf(
                    KonabScreen.CUSTOMERS,
                    KonabScreen.SUPPLIERS,
                    KonabScreen.CASH,
                    KonabScreen.TRANSACTIONS,
                    KonabScreen.SETTINGS,
                    KonabScreen.SUBSCRIPTION
                ),
                onClick = { showMoreMenu = true },
                icon = {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Plus")
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Historique des ventes", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.TRANSACTIONS)
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = KonabBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("Clients & Crédits", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.CUSTOMERS)
                            },
                            leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = KonabBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("Caisse & Dépenses", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.CASH)
                            },
                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KonabSuccess) }
                        )
                        DropdownMenuItem(
                            text = { Text("Fournisseurs & Achats", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.SUPPLIERS)
                            },
                            leadingIcon = { Icon(Icons.Default.LocalShipping, contentDescription = null, tint = KonabBlue) }
                        )
                        DropdownMenuItem(
                            text = { Text("Abonnement Konab", fontWeight = FontWeight.Medium) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.SUBSCRIPTION)
                            },
                            leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = KonabOrange) }
                        )
                        HorizontalDivider(color = KonabBorder)
                        DropdownMenuItem(
                            text = { Text("Paramètres", fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                showMoreMenu = false
                                onNavigate(KonabScreen.SETTINGS)
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = KonabTextPrimary) }
                        )
                    }
                },
                label = {
                    Text(
                        text = "Plus",
                        fontWeight = if (showMoreMenu || currentScreen in listOf(
                            KonabScreen.CUSTOMERS,
                            KonabScreen.SUPPLIERS,
                            KonabScreen.CASH,
                            KonabScreen.TRANSACTIONS,
                            KonabScreen.SETTINGS,
                            KonabScreen.SUBSCRIPTION
                        )) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            )
        }
    }
}
