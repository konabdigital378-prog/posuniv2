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
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.CartItem
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
    onUpdateQuantity: (productId: Long, delta: Int) -> Unit,
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
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner code-barres", tint = KonabGreen)
                    }
                    if (cart.isNotEmpty()) {
                        IconButton(
                            onClick = { showProformaModal = true },
                            modifier = Modifier.testTag("btn_top_proforma")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "Établir devis", tint = Color(0xFF0284C7))
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
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
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
                                Text(
                                    text = "Panier (${cart.sumOf { it.quantity }})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Détails", tint = KonabGreen)
                            }

                            TextButton(onClick = { showCartDetailsSheet = true }) {
                                Text("Voir tout", color = KonabGreen, fontSize = 13.sp)
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

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = CurrencyFormatter.formatFCFA(totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = KonabGreen
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showProformaModal = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_cart_proforma"),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0284C7))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Devis", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0284C7))
                                }

                                Button(
                                    onClick = onProceedToPayment,
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("btn_proceed_to_payment")
                                ) {
                                    Text("Payer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
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
                                tint = KonabGreen
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

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KonabGreen,
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
                        ProductGridItem(
                            product = product,
                            countInCart = countInCart,
                            onAdd = { onAddToCart(product) }
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
                            Text(
                                "${CurrencyFormatter.formatFCFA(item.product.sellPrice)} / ${item.product.unit}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, -1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "-1", tint = KonabRed)
                            }
                            Text(
                                "${item.quantity}",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { onUpdateQuantity(item.product.id, 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "+1", tint = KonabGreen)
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                CurrencyFormatter.formatFCFA(item.totalPrice),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
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
}

@Composable
private fun ProductGridItem(
    product: ProductEntity,
    countInCart: Int,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .border(
                width = if (countInCart > 0) 2.dp else 1.dp,
                color = if (countInCart > 0) KonabGreen else KonabBorder,
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
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = product.category,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
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
                            .background(KonabGreen, CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countInCart",
                            color = Color.White,
                            fontSize = 12.sp,
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

            Text(
                text = "Stock : ${product.stockQuantity} ${product.unit}",
                fontSize = 11.sp,
                color = if (product.stockQuantity <= product.minStockAlert) KonabRed else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = CurrencyFormatter.formatFCFA(product.sellPrice),
                    fontWeight = FontWeight.ExtraBold,
                    color = KonabGreen,
                    fontSize = 13.sp
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(KonabGreenLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = KonabGreenDark, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
