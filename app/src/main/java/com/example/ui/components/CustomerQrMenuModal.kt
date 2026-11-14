package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ProductEntity
import com.example.data.remote.supabase.ParsedTableQr
import com.example.data.remote.supabase.PublicRestaurantListing
import com.example.data.remote.supabase.RestaurantOrderItem
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ProductThumbnail
import com.example.util.ShopLogoAvatar

/**
 * Mini-site Web & Catalogue Menu du Jour client ouvert après sélection d'un restaurant
 * ou scan d'un QR Code de table (`konabpos://menu`).
 * Affiche le restaurant comme une vitrine web (adresse konab.menu/..., bannière, catalogue visuel
 * en grille avec photos des plats configurées par le restaurant, fiche détail multi-photos et panier).
 */
@Composable
fun CustomerQrMenuModal(
    target: ParsedTableQr,
    bundle: SupabaseApiClient.PublicRestaurantMenuBundle?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSubmitOrder: (
        bundle: SupabaseApiClient.PublicRestaurantMenuBundle,
        tableName: String,
        customerName: String,
        customerPhone: String,
        items: List<RestaurantOrderItem>,
        notes: String,
        onResult: (Boolean, RestaurantOrderRecord?, String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTableName by remember(target.shopId, target.tableName) {
        mutableStateOf(target.tableName.ifBlank { "Table 1" })
    }
    var showTablePicker by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tous") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var generalNotes by remember { mutableStateOf("") }
    var selectedQty by remember(target, bundle?.shopId) { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var itemNotes by remember(target, bundle?.shopId) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var detailDish by remember { mutableStateOf<ProductEntity?>(null) }
    var editingDishForNote by remember { mutableStateOf<ProductEntity?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submittedOrder by remember { mutableStateOf<RestaurantOrderRecord?>(null) }
    var submitStatusMessage by remember { mutableStateOf("") }

    val resolvedShopName = bundle?.shopName?.ifBlank { null }
        ?: target.shopName.ifBlank { "Restaurant Konab" }
    val webSlug = remember(resolvedShopName) {
        PublicRestaurantListing(shopId = target.shopId, name = resolvedShopName).toWebSlug()
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.93f)
                .widthIn(max = 680.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_customer_qr_menu"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barre supérieure style mini-site web (https://konab.menu/nom-du-resto)
                Surface(
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Site menu vérifié",
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "konab.menu/$webSlug",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Badge table cliquable pour changer de table si souhaité
                        Surface(
                            color = Color(0xFFEA580C),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = submittedOrder == null && !isSubmitting) {
                                    showTablePicker = true
                                }
                                .testTag("btn_qr_menu_change_table")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableRestaurant,
                                    contentDescription = "Table sélectionnée",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = selectedTableName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { if (!isSubmitting) onDismiss() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_close_qr_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer le menu",
                                tint = Color.White
                            )
                        }
                    }
                }

                when {
                    isLoading -> {
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
                                CircularProgressIndicator(color = Color(0xFFEA580C))
                                Text(
                                    text = "Ouverture du Menu Web...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "$resolvedShopName • $selectedTableName",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    errorMessage != null && bundle == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KonabEmptyState(
                                icon = Icons.Default.WifiOff,
                                title = "Menu indisponible",
                                subtitle = errorMessage,
                                accentColor = KonabRed,
                                actionLabel = "Réessayer",
                                onAction = onRetry
                            )
                        }
                    }

                    submittedOrder != null -> {
                        val confirmed = submittedOrder!!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.5.dp, KonabGreen.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_qr_order_confirmed")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(CircleShape)
                                            .background(KonabGreenLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Commande envoyée",
                                            tint = KonabGreen,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Text(
                                        text = "Commande envoyée au Gérant !",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 19.sp,
                                        color = KonabGreenDark,
                                        textAlign = TextAlign.Center
                                    )
                                    Surface(
                                        color = Color(0xFFFFF7ED),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${confirmed.orderNumber} • ${confirmed.tableName}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Color(0xFF9A3412),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                    Text(
                                        text = submitStatusMessage.ifBlank {
                                            "Le gérant vient de recevoir votre sélection et va la transmettre directement à la cuisine."
                                        },
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            confirmed.items.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "${item.quantity}x ${item.productName}",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = CurrencyFormatter.formatFCFA(item.totalPrice),
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = KonabBlue
                                                    )
                                                }
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Total à régler", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(
                                                    text = CurrencyFormatter.formatFCFA(confirmed.totalAmount),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 16.sp,
                                                    color = KonabGreen
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = onDismiss,
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("btn_qr_order_done")
                                    ) {
                                        Text("Terminer", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    bundle != null -> {
                        val allDishes = bundle.products
                        val categories = remember(allDishes) {
                            listOf("Tous") + allDishes.map { it.category }.distinct().filter { it.isNotBlank() }
                        }
                        val filteredDishes = remember(allDishes, searchQuery, selectedCategory) {
                            allDishes.filter { dish ->
                                val matchesCat = selectedCategory == "Tous" || dish.category.equals(selectedCategory, ignoreCase = true)
                                val matchesSearch = searchQuery.isBlank() ||
                                    dish.name.contains(searchQuery, ignoreCase = true) ||
                                    dish.category.contains(searchQuery, ignoreCase = true)
                                matchesCat && matchesSearch
                            }
                        }

                        val dishRows = remember(filteredDishes) { filteredDishes.chunked(2) }

                        val selectedItems = remember(allDishes, selectedQty, itemNotes) {
                            allDishes.mapNotNull { prod ->
                                val qty = selectedQty[prod.id] ?: 0
                                if (qty > 0) {
                                    RestaurantOrderItem(
                                        productId = prod.id,
                                        productName = prod.name,
                                        unitPrice = prod.sellPrice,
                                        quantity = qty,
                                        note = itemNotes[prod.id].orEmpty()
                                    )
                                } else null
                            }
                        }
                        val orderTotal = remember(selectedItems) { selectedItems.sumOf { it.totalPrice } }
                        val totalDishesCount = remember(selectedItems) { selectedItems.sumOf { it.quantity } }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Vitrine Hero du site web du restaurant
                            item {
                                RestaurantWebHeroCard(
                                    bundle = bundle,
                                    selectedTableName = selectedTableName,
                                    dishesCount = allDishes.size,
                                    onTableClick = { showTablePicker = true }
                                )
                            }

                            item {
                                KonabSearchField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = "Rechercher un plat, boisson, grillade...",
                                    modifier = Modifier.testTag("input_qr_menu_search")
                                )
                            }

                            if (categories.size > 1) {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(categories) { cat ->
                                            val isSel = selectedCategory == cat
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { selectedCategory = cat },
                                                label = {
                                                    Text(
                                                        text = cat,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFEA580C),
                                                    selectedLabelColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredDishes.isEmpty()) {
                                item {
                                    KonabEmptyState(
                                        icon = Icons.Default.Restaurant,
                                        title = "Aucun plat disponible",
                                        subtitle = "Aucun plat ne correspond à votre filtre dans le Menu du Jour.",
                                        accentColor = Color(0xFFEA580C)
                                    )
                                }
                            } else {
                                items(dishRows, key = { row -> row.joinToString("_") { it.id.toString() } }) { rowDishes ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowDishes.forEach { dish ->
                                            val qty = selectedQty[dish.id] ?: 0
                                            val dishNote = itemNotes[dish.id].orEmpty()
                                            WebDishCatalogCard(
                                                dish = dish,
                                                qty = qty,
                                                note = dishNote,
                                                onOpenDetail = { detailDish = dish },
                                                onIncrement = {
                                                    selectedQty = selectedQty + (dish.id to (qty + 1))
                                                },
                                                onDecrement = {
                                                    val next = (qty - 1).coerceAtLeast(0)
                                                    selectedQty = if (next == 0) selectedQty - dish.id else selectedQty + (dish.id to next)
                                                },
                                                onEditNote = { editingDishForNote = dish },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowDishes.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            // Identification client & instructions générales
                            if (selectedItems.isNotEmpty()) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Vos informations (optionnel)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = customerName,
                                                    onValueChange = { customerName = it },
                                                    label = { Text("Votre nom") },
                                                    placeholder = { Text("Ex: Moussa") },
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("input_qr_customer_name")
                                                )
                                                OutlinedTextField(
                                                    value = customerPhone,
                                                    onValueChange = { customerPhone = it },
                                                    label = { Text("Téléphone") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                    singleLine = true,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("input_qr_customer_phone")
                                                )
                                            }
                                            OutlinedTextField(
                                                value = generalNotes,
                                                onValueChange = { generalNotes = it },
                                                label = { Text("Note au gérant / cuisine") },
                                                placeholder = { Text("Ex: Peu épicé, servir les boissons d'abord") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("input_qr_order_notes")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Barre sticky Commander / Envoyer au Gérant
                        Surface(
                            tonalElevation = 8.dp,
                            shadowElevation = 10.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (submitError != null) {
                                    Text(
                                        text = submitError!!,
                                        color = KonabRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (totalDishesCount == 0) {
                                                "Touchez un plat pour voir sa fiche"
                                            } else {
                                                "$totalDishesCount plat(s) • $selectedTableName"
                                            },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatFCFA(orderTotal),
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 19.sp,
                                            color = KonabGreen
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            submitError = null
                                            isSubmitting = true
                                            onSubmitOrder(
                                                bundle,
                                                selectedTableName,
                                                customerName.ifBlank { "Client $selectedTableName" },
                                                customerPhone,
                                                selectedItems,
                                                generalNotes
                                            ) { ok, order, msg ->
                                                isSubmitting = false
                                                if (ok && order != null) {
                                                    submittedOrder = order
                                                    submitStatusMessage = msg
                                                } else {
                                                    submitError = msg
                                                }
                                            }
                                        },
                                        enabled = selectedItems.isNotEmpty() && !isSubmitting,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .height(50.dp)
                                            .testTag("btn_qr_submit_order")
                                    ) {
                                        if (isSubmitting) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Envoyer au Gérant",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp
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

    // Fiche Web détaillée du plat (multi-photos + préparation + ajout au panier)
    detailDish?.let { dish ->
        val currentQty = selectedQty[dish.id] ?: 0
        val currentNote = itemNotes[dish.id].orEmpty()
        DishWebDetailDialog(
            dish = dish,
            initialQty = if (currentQty > 0) currentQty else 1,
            initialNote = currentNote,
            onDismiss = { detailDish = null },
            onConfirm = { newQty, newNote ->
                selectedQty = if (newQty <= 0) selectedQty - dish.id else selectedQty + (dish.id to newQty)
                itemNotes = if (newNote.isBlank()) itemNotes - dish.id else itemNotes + (dish.id to newNote.trim())
                detailDish = null
            }
        )
    }

    // Précision rapide
    editingDishForNote?.let { dish ->
        var tempNote by remember(dish.id) { mutableStateOf(itemNotes[dish.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingDishForNote = null },
            title = { Text("Précision • ${dish.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tempNote,
                        onValueChange = { tempNote = it },
                        placeholder = { Text("Ex: Bien cuit, piment à part, sans glaçons...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Bien frais", "Sans glaçons", "Piment à part", "Bien cuit", "À emporter")) { s ->
                            SuggestionChip(
                                onClick = { tempNote = if (tempNote.isBlank()) s else "$tempNote, $s" },
                                label = { Text(s, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemNotes = if (tempNote.isBlank()) itemNotes - dish.id else itemNotes + (dish.id to tempNote.trim())
                        editingDishForNote = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDishForNote = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Sélecteur de table (pratique si le client a ouvert le menu web directement depuis la liste des restos)
    if (showTablePicker && bundle != null) {
        val availableTables = remember(bundle.tables) {
            if (bundle.tables.isNotEmpty()) {
                bundle.tables.filter { it.isActive }.map { it.tableNumber }.ifEmpty {
                    (1..8).map { "Table $it" }
                }
            } else {
                (1..8).map { "Table $it" } + "À emporter"
            }
        }
        AlertDialog(
            onDismissRequest = { showTablePicker = false },
            title = { Text("Choisir votre table", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableTables) { tbl ->
                        val isSel = tbl.equals(selectedTableName, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) Color(0xFFFFEDD5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (isSel) BorderStroke(1.2.dp, Color(0xFFEA580C)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedTableName = tbl
                                    showTablePicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tbl,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSel) Color(0xFF9A3412) else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTablePicker = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun RestaurantWebHeroCard(
    bundle: SupabaseApiClient.PublicRestaurantMenuBundle,
    selectedTableName: String,
    dishesCount: Int,
    onTableClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF7C2D12), Color(0xFFEA580C), KonabOrange)
                    )
                )
                .konabHeroShimmer()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ShopLogoAvatar(
                        logoUri = bundle.logoUri ?: "drawable:img_prod_resto",
                        size = 58.dp,
                        shape = RoundedCornerShape(16.dp),
                        contentDescription = "Logo ${bundle.shopName}"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bundle.shopName,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (bundle.slogan.isNotBlank()) {
                            Text(
                                text = bundle.slogan,
                                fontSize = 12.sp,
                                color = Color(0xFFFFEDD5),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val meta = listOfNotNull(
                            bundle.city.takeIf { it.isNotBlank() },
                            bundle.address.takeIf { it.isNotBlank() },
                            bundle.phone.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Menu du Jour • $dishesCount plat(s)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onTableClick)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableRestaurant,
                                contentDescription = null,
                                tint = Color(0xFF9A3412),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = selectedTableName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDishCatalogCard(
    dish: ProductEntity,
    qty: Int,
    note: String,
    onOpenDetail: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val galleryCount = remember(dish.galleryImagesJson, dish.imageUri) {
        dish.getGalleryList().size
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (qty > 0) 1.6.dp else 1.dp,
            color = if (qty > 0) Color(0xFFEA580C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (qty > 0) 4.dp else 2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenDetail)
            .testTag("qr_menu_dish_${dish.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo haute résolution du plat + badges prix / panier / galerie
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                ProductThumbnail(
                    product = dish,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                if (galleryCount > 1) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "$galleryCount",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (qty > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA580C))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$qty",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = CurrencyFormatter.formatFCFA(dish.sellPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = KonabGreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = dish.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                    lineHeight = 17.sp
                )
                Text(
                    text = dish.category.ifBlank { "Menu du jour" },
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (note.isNotBlank()) {
                    Text(
                        text = "Note: $note",
                        fontSize = 10.5.sp,
                        color = Color(0xFF9A3412),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (qty == 0) {
                    Button(
                        onClick = onIncrement,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFF7ED),
                            contentColor = Color(0xFF9A3412)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_qr_plus_${dish.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Ajouter ${dish.name}",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ajouter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDecrement,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("btn_qr_minus_${dish.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RemoveCircleOutline,
                                contentDescription = "Retirer ${dish.name}",
                                tint = KonabRed
                            )
                        }
                        TextButton(
                            onClick = onEditNote,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "$qty • Note",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = KonabBlue
                            )
                        }
                        FilledIconButton(
                            onClick = onIncrement,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFEA580C),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_qr_plus_${dish.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Ajouter encore ${dish.name}",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishWebDetailDialog(
    dish: ProductEntity,
    initialQty: Int,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (qty: Int, note: String) -> Unit
) {
    val gallery = remember(dish.id, dish.imageUri, dish.galleryImagesJson) {
        dish.getGalleryList()
    }
    var activePhotoUri by remember(dish.id) {
        mutableStateOf(gallery.firstOrNull() ?: dish.imageUri)
    }
    var qty by remember(dish.id) { mutableIntStateOf(initialQty.coerceAtLeast(1)) }
    var note by remember(dish.id) { mutableStateOf(initialNote) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .testTag("dialog_dish_web_detail")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    ProductThumbnail(
                        product = dish.copy(imageUri = activePhotoUri),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer la fiche")
                    }
                    Surface(
                        color = Color(0xFFEA580C),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = CurrencyFormatter.formatFCFA(dish.sellPrice),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (gallery.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gallery) { uri ->
                            val isSelected = uri == activePhotoUri
                            ProductThumbnail(
                                product = dish.copy(imageUri = uri),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFEA580C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { activePhotoUri = uri },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = dish.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Catégorie : ${dish.category.ifBlank { "Menu du jour" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Précision pour la cuisine (optionnel)") },
                        placeholder = { Text("Ex: Bien cuit, piment à part, sans glaçons...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("Bien frais", "Sans glaçons", "Piment à part", "Bien cuit", "À emporter")) { s ->
                            SuggestionChip(
                                onClick = { note = if (note.isBlank()) s else "$note, $s" },
                                label = { Text(s, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (qty > 1) qty-- },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Diminuer", tint = KonabRed)
                            }
                            Text(
                                text = "$qty",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { qty++ },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Augmenter", tint = Color(0xFFEA580C))
                            }
                        }

                        Button(
                            onClick = { onConfirm(qty, note) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_dish_detail_confirm")
                        ) {
                            Text(
                                text = "Ajouter • ${CurrencyFormatter.formatFCFA(dish.sellPrice * qty)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
