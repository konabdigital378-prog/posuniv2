package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.ui.components.ItemVariantCustomizerDialog
import com.example.ui.components.KitchenOrderModal
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.CartItem
import com.example.util.BusinessTypePresets
import com.example.util.CurrencyFormatter
import com.example.util.ProductThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    cart: List<CartItem>,
    selectedCustomer: CustomerEntity?,
    discountAmount: Long,
    subtotal: Long,
    totalAmount: Long,
    onAddToCart: (ProductEntity) -> Unit,
    onAddToCartWithVariant: (ProductEntity, String) -> Unit = { p, _ -> onAddToCart(p) },
    onUpdateQuantity: (productId: Long, delta: Int) -> Unit,
    onUpdateItemNote: (productId: Long, String) -> Unit = { _, _ -> },
    onUpdateItemDiscount: (productId: Long, Long) -> Unit = { _, _ -> },
    onRemoveFromCart: (productId: Long) -> Unit,
    onClearCart: () -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onSetDiscount: (Long) -> Unit,
    onProceedToPayment: () -> Unit,
    onBack: () -> Unit,
    shopProfile: com.example.data.local.entity.ShopProfileEntity? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tous") }
    var showCartDetailsSheet by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var showProformaModal by remember { mutableStateOf(false) }

    val sectorPreset = remember(shopProfile?.businessType) {
        BusinessTypePresets.getPreset(shopProfile?.businessType ?: "Boutique")
    }
    val sectorFeatures = sectorPreset.sectorFeatures

    var selectedTable by remember { mutableStateOf("Table 1") }
    var selectedStylist by remember { mutableStateOf("Fatou") }
    var selectedNomadSpot by remember { mutableStateOf("Carrefour") }
    var selectedCraftMode by remember { mutableStateOf("Fourniture + Pose") }
    var showKitchenModal by remember { mutableStateOf(false) }
    var productForCustomization by remember { mutableStateOf<ProductEntity?>(null) }
    var itemForCartNoteEdit by remember { mutableStateOf<CartItem?>(null) }
    var speedCashTender by remember { mutableStateOf<Long?>(null) }

    // Categories extracted dynamically from products
    val categories = remember(products) {
        listOf("Tous") + products.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    // Filtered products
    val filteredProducts = remember(products, searchQuery, selectedCategory) {
        products.filter { p ->
            val matchQuery = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.barcode.contains(searchQuery, ignoreCase = true)
            val matchCategory = selectedCategory == "Tous" || p.category.equals(selectedCategory, ignoreCase = true)
            matchQuery && matchCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vente", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showScanDialog = true },
                        modifier = Modifier.testTag("btn_barcode_scan")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner code-barres", tint = KonabBlue)
                    }
                    if (cart.isNotEmpty()) {
                        if (sectorFeatures.hasTables) {
                            IconButton(
                                onClick = { showKitchenModal = true },
                                modifier = Modifier.testTag("btn_top_kitchen_order")
                            ) {
                                Icon(Icons.Default.RestaurantMenu, contentDescription = "Bon cuisine", tint = Color(0xFFEA580C))
                            }
                        }
                        IconButton(
                            onClick = { showProformaModal = true },
                            modifier = Modifier.testTag("btn_top_proforma")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "Établir devis", tint = KonabBlue)
                        }
                        IconButton(onClick = onClearCart) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Vider panier", tint = KonabRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (cart.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showCartDetailsSheet = true }
                            ) {
                                Surface(
                                    color = KonabBlueContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${cart.sumOf { it.quantity }} art.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = KonabBlue,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Panier en cours",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Détails", tint = KonabBlue)
                            }

                            TextButton(onClick = { showCartDetailsSheet = true }) {
                                Text("Détails", color = KonabBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Preview of items
                        Text(
                            text = cart.joinToString(", ") { "${it.quantity}x ${it.product.name}" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Speed Cash Shortcuts for Kiosks, Fast-Food, and Vendeurs Ambulants
                        if (sectorFeatures.hasSpeedCashShortcuts) {
                            val fastBills = if (sectorFeatures.hasNomadMode) {
                                listOf(100L, 200L, 500L, 1000L, 2000L, 5000L)
                            } else {
                                listOf(500L, 1000L, 2000L, 5000L, 10000L)
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ Monnaie express :",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (speedCashTender != null) {
                                        val change = maxOf(0L, speedCashTender!! - totalAmount)
                                        Text(
                                            text = "Reçu: ${CurrencyFormatter.formatFCFA(speedCashTender!!)} • Rendre: ${CurrencyFormatter.formatFCFA(change)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (change >= 0) KonabGreenDark else KonabRed
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(fastBills) { bill ->
                                        val isSel = speedCashTender == bill
                                        SuggestionChip(
                                            onClick = { speedCashTender = if (isSel) null else bill },
                                            label = { Text("${bill} F", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSel) KonabGreenLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total TTC", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = CurrencyFormatter.formatFCFA(totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 19.sp,
                                    color = KonabBlue
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (sectorFeatures.hasTables) {
                                    OutlinedButton(
                                        onClick = { showKitchenModal = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("btn_cart_kitchen_order"),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEA580C))
                                    ) {
                                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Cuisine", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showProformaModal = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_cart_proforma"),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = KonabBlue)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Devis", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KonabBlue)
                                }

                                Button(
                                    onClick = onProceedToPayment,
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_proceed_to_payment")
                                ) {
                                    Text("Payer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sector Tools & Context Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = KonabGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${sectorPreset.emoji} ${sectorPreset.label}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabGreenDark,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (sectorFeatures.hasNomadMode) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf("🚶 Carrefour", "🏪 Marché", "🚌 Arrêt", "🏠 Tournée", "🎪 Événement")) { spot ->
                                    val isSel = selectedNomadSpot == spot
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedNomadSpot = spot },
                                        label = { Text(spot, fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFE11D48),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        } else if (sectorFeatures.hasTables) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf("T-1", "T-2", "T-3", "T-4", "Terrasse", "Emporter")) { tbl ->
                                    val isSel = selectedTable == tbl
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedTable = tbl },
                                        label = { Text(tbl, fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFEA580C),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        } else if (sectorFeatures.hasJobSiteQuotes) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf("Fourniture + Pose", "Main d'œuvre", "Dépannage", "Atelier")) { mode ->
                                    val isSel = selectedCraftMode == mode
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedCraftMode = mode },
                                        label = { Text(mode, fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0369A1),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        } else if (sectorFeatures.hasOperatorStylist) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(listOf("Fatou", "Moussa", "Sarah", "Ibrahim")) { stylist ->
                                    val isSel = selectedStylist == stylist
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedStylist = stylist },
                                        label = { Text(stylist, fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF9333EA),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        } else if (sectorFeatures.hasPrescriptionExpiry) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "💊 Mode Ordonnance & DCI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0284C7)
                                )
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE0F2FE)) {
                                    Text("Posologie active", fontSize = 10.sp, color = Color(0xFF0369A1), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        } else if (sectorFeatures.hasVariations) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "👗 Rayon Prêt-à-Porter",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = KonabBlue
                                )
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEDE9FE)) {
                                    Text("Tailles & Couleurs", fontSize = 10.sp, color = Color(0xFF6D28D9), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        } else if (sectorFeatures.hasFractionalUnits) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "🔨 Vente au détail & découpe",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309)
                                )
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF3C7)) {
                                    Text("Mètre, Kg, Sac", fontSize = 10.sp, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    if (sectorFeatures.hasNomadMode && cart.isNotEmpty()) {
                        Button(
                            onClick = onProceedToPayment,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_nomad_cash_flash")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Encaisser Flash", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (sectorFeatures.hasTables && cart.isNotEmpty()) {
                        Button(
                            onClick = { showKitchenModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_kitchen_order")
                        ) {
                            Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Bon Cuisine", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (sectorFeatures.hasJobSiteQuotes && cart.isNotEmpty()) {
                        Button(
                            onClick = { showProformaModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_jobsite_quote")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Devis Chantier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un produit ou code...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                        IconButton(
                            onClick = { showScanDialog = true },
                            modifier = Modifier.testTag("btn_search_barcode_scan")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner code-barres",
                                tint = KonabBlue
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("input_search_product")
            )

            // Category Filter Chips with dynamic counts
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    val catCount = if (cat == "Tous") products.size else products.count { it.category.equals(cat, ignoreCase = true) }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = if (cat == "Tous") "Tous ($catCount)" else "$cat ($catCount)",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KonabBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Products Grid
            if (filteredProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = KonabTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Aucun produit trouvé", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Vérifiez le filtre ou ajoutez de nouveaux articles dans l'onglet Stock", fontSize = 12.sp, color = KonabTextMuted)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredProducts) { product ->
                        val countInCart = cart.find { it.product.id == product.id }?.quantity ?: 0
                        val needsCustomization = sectorFeatures.hasVariations || sectorFeatures.hasPrescriptionExpiry || sectorFeatures.hasTables || sectorFeatures.hasJobSiteQuotes || sectorFeatures.hasNomadMode
                        ProductGridItem(
                            product = product,
                            countInCart = countInCart,
                            onAdd = { onAddToCart(product) },
                            onCustomize = if (needsCustomization) { { productForCustomization = product } } else null
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Cart Management
    if (showCartDetailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartDetailsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Détail du panier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onClearCart(); showCartDetailsSheet = false }) {
                        Text("Vider", color = KonabRed)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Cart Item Rows
                cart.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProductThumbnail(
                            product = item.product,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (item.variantNote.isNotBlank()) {
                                Surface(
                                    color = KonabGreenLight,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = item.variantNote,
                                        fontSize = 11.sp,
                                        color = KonabGreenDark,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "${CurrencyFormatter.formatFCFA(item.unitFinalPrice)} / ${item.product.unit}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { itemForCartNoteEdit = item },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("btn_cart_note_${item.product.name}")
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = "Spécification", tint = KonabBlue, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, -1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "-1", tint = KonabRed)
                            }
                            Text(
                                "${item.quantity}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "+1", tint = KonabGreen)
                            }

                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                CurrencyFormatter.formatFCFA(item.totalPrice),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = KonabGreen
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Customer Selection row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomerPicker = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Client", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                selectedCustomer?.name ?: "Client de passage (Anonyme)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Discount button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiscountDialog = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = KonabGoldDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appliquer une réduction", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        if (discountAmount > 0) "-${CurrencyFormatter.formatFCFA(discountAmount)}" else "0 FCFA",
                        fontWeight = FontWeight.Bold,
                        color = if (discountAmount > 0) KonabRed else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subtotal & Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sous-total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(CurrencyFormatter.formatFCFA(subtotal), fontWeight = FontWeight.SemiBold)
                }
                if (discountAmount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Réduction", color = KonabRed)
                        Text("-${CurrencyFormatter.formatFCFA(discountAmount)}", color = KonabRed, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL NET", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(CurrencyFormatter.formatFCFA(totalAmount), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = KonabGreen)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showCartDetailsSheet = false
                        onProceedToPayment()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Procéder au paiement (${CurrencyFormatter.formatFCFA(totalAmount)})", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Customer Picker Dialog
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("Sélectionner un client") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            onSelectCustomer(null)
                            showCustomerPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Client de passage (Anonyme)", fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    customers.forEach { customer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCustomer(customer)
                                    showCustomerPicker = false
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(customer.name, fontWeight = FontWeight.Bold)
                                Text(customer.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (customer.debtAmount > 0) {
                                Surface(
                                    color = KonabRedContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "Dette: ${CurrencyFormatter.formatFCFA(customer.debtAmount)}",
                                        color = KonabRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Discount Dialog
    if (showDiscountDialog) {
        var discountInput by remember { mutableStateOf(if (discountAmount > 0) discountAmount.toString() else "") }
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("Montant de la réduction") },
            text = {
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it },
                    label = { Text("Réduction en FCFA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = discountInput.toLongOrNull() ?: 0L
                        onSetDiscount(amount)
                        showDiscountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onSetDiscount(0L)
                    showDiscountDialog = false
                }) {
                    Text("Annuler la réduction")
                }
            }
        )
    }

    // Real Barcode Scanner Modal with CameraX and ML Kit
    if (showScanDialog) {
        var lastScannedInfo by remember { mutableStateOf<String?>(null) }

        RealBarcodeScannerModal(
            title = "Scanner de caisse",
            subtitle = "Pointez la caméra vers le code-barres pour ajouter l'article",
            continuousScan = true,
            lastScannedBarcode = lastScannedInfo,
            onBarcodeScanned = { scannedCode ->
                val trimmed = scannedCode.trim()
                val found = products.find { 
                    it.barcode.equals(trimmed, ignoreCase = true) ||
                    (it.barcode.isNotBlank() && trimmed.contains(it.barcode))
                }
                if (found != null) {
                    onAddToCart(found)
                    lastScannedInfo = "${found.name} (+1)"
                } else {
                    lastScannedInfo = "Inconnu : $trimmed"
                    searchQuery = trimmed
                }
            },
            onDismiss = {
                showScanDialog = false
            }
        )
    }

    if (showProformaModal) {
        com.example.ui.components.ProformaQuoteModal(
            shopProfile = shopProfile,
            cart = cart,
            customer = selectedCustomer,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            onDismiss = { showProformaModal = false }
        )
    }

    if (showKitchenModal) {
        KitchenOrderModal(
            cart = cart,
            shopName = shopProfile?.name ?: "Mon Établissement",
            initialTable = selectedTable,
            onUpdateItemNote = onUpdateItemNote,
            onDismiss = { showKitchenModal = false }
        )
    }

    productForCustomization?.let { prod ->
        ItemVariantCustomizerDialog(
            product = prod,
            businessType = shopProfile?.businessType ?: "Boutique",
            onDismiss = { productForCustomization = null },
            onConfirm = { customNote ->
                onAddToCartWithVariant(prod, customNote)
                productForCustomization = null
            }
        )
    }

    itemForCartNoteEdit?.let { cartItem ->
        ItemVariantCustomizerDialog(
            product = cartItem.product,
            initialNote = cartItem.variantNote,
            businessType = shopProfile?.businessType ?: "Boutique",
            onDismiss = { itemForCartNoteEdit = null },
            onConfirm = { customNote ->
                onUpdateItemNote(cartItem.product.id, customNote)
                itemForCartNoteEdit = null
            }
        )
    }
}

@Composable
private fun ProductGridItem(
    product: ProductEntity,
    countInCart: Int,
    onAdd: () -> Unit,
    onCustomize: (() -> Unit)? = null
) {
    val isOut = product.stockQuantity <= 0
    val isLow = product.stockQuantity in 1..product.minStockAlert

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .border(
                width = if (countInCart > 0) 2.dp else 1.dp,
                color = if (countInCart > 0) KonabBlue else KonabBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("product_card_${product.name}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Product image with cart badge overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                ProductThumbnail(
                    product = product,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category tag on top-left of image
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = product.category,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }

                // Gallery count badge on bottom-right of image
                val gallerySize = product.getGalleryList().size
                if (gallerySize > 1) {
                    Surface(
                        color = KonabNavy.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "$gallerySize",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Cart badge if items already selected
                if (countInCart > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .background(KonabBlue, CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$countInCart",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Stock Pill Badge
            Surface(
                color = if (isOut) KonabRedContainer else if (isLow) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (isOut) "Épuisé" else if (isLow) "Faible (${product.stockQuantity})" else "Stock : ${product.stockQuantity} ${product.unit}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOut) KonabRed else if (isLow) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = CurrencyFormatter.formatFCFA(product.sellPrice),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = OutfitFontFamily,
                    color = KonabBlue,
                    fontSize = 15.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onCustomize != null) {
                        IconButton(
                            onClick = onCustomize,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("btn_customize_${product.name}")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Spécifier variantes/notes",
                                tint = KonabBlue,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(KonabBlue, CircleShape)
                            .clickable { onAdd() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Ajouter au panier",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
