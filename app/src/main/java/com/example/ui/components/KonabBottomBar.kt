package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
        indicatorColor = KonabOrangeContainer,
        unselectedIconColor = KonabTextSecondary,
        unselectedTextColor = KonabTextSecondary
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        NavigationBarItem(
            modifier = Modifier.testTag("nav_dashboard"),
            selected = currentScreen == KonabScreen.DASHBOARD,
            onClick = { onNavigate(KonabScreen.DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
            label = { Text("Accueil") },
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
                        ) { Text("$cartItemCount") }
                    }
                }) {
                    Icon(Icons.Default.PointOfSale, contentDescription = "Ventes")
                }
            },
            label = { Text("Ventes") },
            colors = navItemColors
        )

        NavigationBarItem(
            modifier = Modifier.testTag("nav_stock"),
            selected = currentScreen == KonabScreen.STOCK,
            onClick = { onNavigate(KonabScreen.STOCK) },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Produits") },
            label = { Text("Produits") },
            colors = navItemColors
        )

        NavigationBarItem(
            modifier = Modifier.testTag("nav_reports"),
            selected = currentScreen == KonabScreen.REPORTS,
            onClick = { onNavigate(KonabScreen.REPORTS) },
            icon = { Icon(Icons.Default.Assessment, contentDescription = "Rapports") },
            label = { Text("Rapports") },
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
                        text = { Text("Historique des ventes") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.TRANSACTIONS)
                        },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Clients & Crédits") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.CUSTOMERS)
                        },
                        leadingIcon = { Icon(Icons.Default.People, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Caisse & Dépenses") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.CASH)
                        },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Fournisseurs & Achats") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.SUPPLIERS)
                        },
                        leadingIcon = { Icon(Icons.Default.LocalShipping, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Abonnement Konab") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.SUBSCRIPTION)
                        },
                        leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Paramètres") },
                        onClick = {
                            showMoreMenu = false
                            onNavigate(KonabScreen.SETTINGS)
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                }
            },
            label = { Text("Plus") }
        )
    }
}
