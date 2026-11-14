package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabSearchField
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ProductThumbnail

@Composable
fun RestaurantMenuOfDayTab(
    products: List<ProductEntity>,
    menuOfDayIds: Set<Long>,
    onToggleProduct: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onPreviewCustomerMenu: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(products, search) {
        products.filter { p ->
            search.isBlank() || p.name.contains(search, ignoreCase = true) || p.category.contains(search, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("restaurant_menu_day_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Configuration du Menu du Jour (${if (menuOfDayIds.isEmpty()) products.size else menuOfDayIds.size} plats)",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Sélectionnez les plats et boissons affichés lorsque vos clients scannent le QR Code d'une table. Si aucun n'est coché, toute la carte disponible est affichée.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSelectAll,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_menu_select_all")
                        ) {
                            Text("Tout activer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (menuOfDayIds.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearAll,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabRed),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp)
                            ) {
                                Text("Réinitialiser", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }
                        Button(
                            onClick = onPreviewCustomerMenu,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(46.dp)
                                .testTag("btn_preview_customer_menu")
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Aperçu Client", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            KonabSearchField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Filtrer les plats ou boissons...",
                modifier = Modifier.testTag("input_menu_day_search")
            )
        }

        if (filtered.isEmpty()) {
            item {
                KonabEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "Aucun plat dans le catalogue",
                    subtitle = "Ajoutez vos plats et boissons dans l'onglet Stock pour les inclure dans votre Menu du Jour.",
                    accentColor = KonabBlue
                )
            }
        } else {
            items(filtered, key = { it.id }) { prod ->
                val isIncluded = menuOfDayIds.contains(prod.id)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isIncluded) Color(0xFFEA580C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProductThumbnail(
                            product = prod,
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "${prod.category} • ${CurrencyFormatter.formatFCFA(prod.sellPrice)}",
                                fontSize = 12.sp,
                                color = KonabGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = isIncluded,
                            onCheckedChange = { onToggleProduct(prod.id) },
                            modifier = Modifier.testTag("switch_menu_day_${prod.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantTeamSyncTab(
    shopName: String,
    shopPhone: String,
    cloudShopId: Long,
    staffList: List<RestaurantStaffAccount>,
    activeStaff: RestaurantStaffAccount?,
    onSwitchActiveStaff: (RestaurantStaffAccount) -> Unit,
    onSaveStaff: (RestaurantStaffAccount) -> Unit,
    onDeleteStaff: (String) -> Unit
) {
    var editingStaff by remember { mutableStateOf<RestaurantStaffAccount?>(null) }
    var showCreateStaffDialog by remember { mutableStateOf(false) }
    var revealedPinIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("restaurant_team_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connexion Multi-Utilisateurs Synchronisée",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Chaque membre de votre restaurant (Gérant, Cuisine, Serveur, Caissier) peut se connecter sur son propre téléphone via « Multiboutique » avec : « $shopName » ${if (shopPhone.isNotBlank()) "ou $shopPhone" else ""} + son code PIN personnel ci-dessous (ID Cloud #$cloudShopId).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = { showCreateStaffDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_add_restaurant_staff")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Ajouter un membre d'équipe", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(staffList, key = { it.id }) { member ->
            val isCurrent = activeStaff?.id == member.id
            val isPinShown = member.id in revealedPinIds
            val roleColor = when (member.role) {
                RestaurantStaffRole.KITCHEN -> Color(0xFFEA580C)
                RestaurantStaffRole.WAITER -> KonabGreen
                RestaurantStaffRole.CASHIER -> KonabPurple
                else -> KonabBlue
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (isCurrent) roleColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_staff_${member.role.name.lowercase()}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(member.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Surface(color = roleColor, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = member.role.badgeLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = member.role.description,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "PIN : ${if (isPinShown) member.pin else "••••"} ${if (member.phone.isNotBlank()) "• Tél : ${member.phone}" else ""}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KonabBlue
                            )
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    revealedPinIds = if (isPinShown) revealedPinIds - member.id else revealedPinIds + member.id
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (isPinShown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPinShown) "Masquer le PIN" else "Afficher le PIN"
                                )
                            }
                            IconButton(
                                onClick = { editingStaff = member },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier ${member.name}", tint = KonabBlue)
                            }
                            if (member.role != RestaurantStaffRole.MANAGER) {
                                IconButton(
                                    onClick = { onDeleteStaff(member.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer ${member.name}", tint = KonabRed)
                                }
                            }
                        }
                    }

                    if (!isCurrent) {
                        OutlinedButton(
                            onClick = { onSwitchActiveStaff(member) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text("Passer sur ce profil (${member.role.badgeLabel})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Surface(
                            color = KonabGreenLight,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Profil actuellement utilisé sur cet appareil",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabGreenDark,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateStaffDialog || editingStaff != null) {
        val initial = editingStaff
        StaffEditorDialog(
            initial = initial,
            onSave = { account ->
                onSaveStaff(account)
                showCreateStaffDialog = false
                editingStaff = null
            },
            onDismiss = {
                showCreateStaffDialog = false
                editingStaff = null
            }
        )
    }
}

@Composable
private fun StaffEditorDialog(
    initial: RestaurantStaffAccount?,
    onSave: (RestaurantStaffAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var pin by remember { mutableStateOf(initial?.pin ?: "3333") }
    var role by remember { mutableStateOf(initial?.role ?: RestaurantStaffRole.WAITER) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "Nouveau Compte Équipe" else "Modifier ${initial.name}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Rôle :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RestaurantStaffRole.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pair.forEach { r ->
                                FilterChip(
                                    selected = role == r,
                                    onClick = { role = r },
                                    label = { Text(r.badgeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = "" },
                    label = { Text("Nom complet *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(8); errorMsg = "" },
                    label = { Text("Code PIN (min. 4 chiffres) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = KonabRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanPin = pin.trim()
                    if (cleanName.isBlank() || cleanPin.length < 4) {
                        errorMsg = "Nom et PIN de 4+ chiffres requis."
                    } else {
                        onSave(
                            RestaurantStaffAccount(
                                id = initial?.id.orEmpty(),
                                shopId = initial?.shopId ?: 0L,
                                name = cleanName,
                                phone = phone.trim(),
                                role = role,
                                pin = cleanPin,
                                isActive = true
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KonabBlue)
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
