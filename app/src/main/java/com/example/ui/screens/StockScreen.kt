package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import org.json.JSONArray
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.local.entity.ProductEntity
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ProductImageHelper
import com.example.util.ProductThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    products: List<ProductEntity>,
    onSaveProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (Long) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Tous") } // Tous, En stock, Faible, Rupture
    var showProductDialog by remember { mutableStateOf(false) }
    var showStockScanDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var zoomingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val filters = listOf("Tous", "En stock", "Faible", "Rupture")

    val filteredProducts = remember(products, searchQuery, selectedFilter) {
        products.filter { p ->
            val matchQuery = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.category.contains(searchQuery, ignoreCase = true) ||
                    p.barcode.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (selectedFilter) {
                "En stock" -> p.stockQuantity > p.minStockAlert
                "Faible" -> p.stockQuantity in 1..p.minStockAlert
                "Rupture" -> p.stockQuantity <= 0
                else -> true
            }
            matchQuery && matchFilter
        }
    }

    // Valuation calculations
    val totalStockCost = remember(products) {
        products.sumOf { it.buyPrice * it.stockQuantity }
    }
    val totalStockValue = remember(products) {
        products.sumOf { it.sellPrice * it.stockQuantity }
    }
    val potentialProfit = maxOf(0L, totalStockValue - totalStockCost)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock & Produits", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProduct = null
                    showProductDialog = true
                },
                containerColor = KonabGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_product")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un produit")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un nom, catégorie ou code...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                        IconButton(
                            onClick = { showStockScanDialog = true },
                            modifier = Modifier.testTag("btn_stock_search_barcode_scan")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner code-barres produit",
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
                    .testTag("input_search_stock")
            )

            // Stock Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filter == "Rupture" || filter == "Faible") KonabRed else KonabGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Valuation Cards
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Valeur d'achat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.formatFCFA(totalStockCost),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(30.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Bénéfice potentiel", fontSize = 11.sp, color = KonabGreen)
                        Text(
                            CurrencyFormatter.formatFCFA(potentialProfit),
                            fontWeight = FontWeight.ExtraBold,
                            color = KonabGreen,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Product List
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredProducts) { product ->
                    ProductStockItem(
                        product = product,
                        onEdit = {
                            editingProduct = product
                            showProductDialog = true
                        },
                        onDelete = { onDeleteProduct(product.id) },
                        onZoom = { zoomingProduct = product },
                        onPrintPriceTag = {
                            coroutineScope.launch {
                                val res = com.example.util.BluetoothPrinterHelper.printPriceTag(
                                    context = context,
                                    shopName = "Boutique",
                                    productName = product.name,
                                    barcode = product.barcode,
                                    price = product.sellPrice
                                )
                                if (res.isSuccess) {
                                    android.widget.Toast.makeText(context, "Étiquette imprimée pour ${product.name} !", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Imprimante non connectée (Voir Paramètres -> Imprimante)", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Product Dialog
    if (showProductDialog) {
        ProductFormDialog(
            initial = editingProduct,
            onDismiss = { showProductDialog = false },
            onSave = {
                onSaveProduct(it)
                showProductDialog = false
            }
        )
    }

    // Zoom Product Gallery Dialog
    zoomingProduct?.let { prod ->
        ProductImageZoomDialog(
            images = prod.getGalleryList(),
            initialIndex = 0,
            productName = prod.name,
            onDismiss = { zoomingProduct = null }
        )
    }

    // Real Barcode Scanner for Stock search
    if (showStockScanDialog) {
        RealBarcodeScannerModal(
            title = "Recherche par code-barres",
            subtitle = "Pointez vers le code-barres pour localiser l'article en stock",
            onBarcodeScanned = { scannedCode ->
                searchQuery = scannedCode.trim()
                showStockScanDialog = false
            },
            onDismiss = { showStockScanDialog = false }
        )
    }
}

@Composable
private fun ProductStockItem(
    product: ProductEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onZoom: () -> Unit = {},
    onPrintPriceTag: () -> Unit
) {
    val isLow = product.stockQuantity <= product.minStockAlert
    val isOut = product.stockQuantity <= 0
    val margin = product.sellPrice - product.buyPrice

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onZoom() }
                ) {
                    ProductThumbnail(
                        product = product,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    val galleryCount = product.getGalleryList().size
                    if (galleryCount > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp),
                            color = KonabNavy,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "+${galleryCount - 1}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isOut || isLow) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(13.dp)
                                .background(if (isOut) KonabRed else KonabGoldDark, CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${product.category} • Vente : ${CurrencyFormatter.formatFCFA(product.sellPrice)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Achat : ${CurrencyFormatter.formatFCFA(product.buyPrice)} (Marge : +${CurrencyFormatter.formatFCFA(margin)})",
                        fontSize = 11.sp,
                        color = KonabGreen
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onPrintPriceTag,
                    modifier = Modifier.size(32.dp).testTag("btn_print_tag_${product.name}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Imprimer étiquette",
                        tint = KonabGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (isOut) KonabRedContainer else if (isLow) KonabGoldContainer else KonabGreenLight,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Stock : ${product.stockQuantity} ${product.unit}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isOut) KonabRed else if (isLow) KonabGoldDark else KonabGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Seuil : ${product.minStockAlert}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductFormDialog(
    initial: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Général") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "") }
    var buyPriceStr by remember { mutableStateOf(initial?.buyPrice?.toString() ?: "") }
    var sellPriceStr by remember { mutableStateOf(initial?.sellPrice?.toString() ?: "") }
    var stockStr by remember { mutableStateOf(initial?.stockQuantity?.toString() ?: "10") }
    var minStockStr by remember { mutableStateOf(initial?.minStockAlert?.toString() ?: "5") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pièce") }
    var imageUri by remember { mutableStateOf(initial?.imageUri ?: "") }
    var galleryImages by remember {
        mutableStateOf(
            initial?.getGalleryList() ?: if (imageUri.isNotBlank()) listOf(imageUri) else emptyList()
        )
    }
    var showUrlInput by remember { mutableStateOf(false) }
    var showBarcodeScannerInForm by remember { mutableStateOf(false) }
    var zoomImageIndex by remember { mutableStateOf<Int?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val uriStr = uri.toString()
            imageUri = uriStr
            if (!galleryImages.contains(uriStr)) {
                galleryImages = listOf(uriStr) + galleryImages
            }
        }
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val list = uris.map { it.toString() }
            galleryImages = (galleryImages + list).distinct()
            if (imageUri.isBlank() && galleryImages.isNotEmpty()) {
                imageUri = galleryImages.first()
            }
        }
    }

    val previewProduct = remember(name, category, imageUri) {
        ProductEntity(
            name = name.ifBlank { "Produit" },
            category = category.ifBlank { "Général" },
            imageUri = imageUri
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ajouter un produit" else "Modifier le produit") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // -------------------------------------------------------------
                // CHAMP UPLOAD IMAGE (TÉLÉVERSEMENT IMAGE PRODUIT)
                // -------------------------------------------------------------
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo Principale (Couverture)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { showUrlInput = !showUrlInput },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = KonabGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (showUrlInput) "Masquer URL" else "Lien Web URL",
                                fontSize = 11.sp,
                                color = KonabGreen
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.5.dp,
                            color = if (imageUri.isNotBlank()) KonabGreen else KonabBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_image_dropzone")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            if (imageUri.isNotBlank()) {
                                // Image Uploaded / Selected State with Zoom click
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clickable { zoomImageIndex = 0 }
                                            .testTag("btn_zoom_product_preview")
                                    ) {
                                        ProductThumbnail(
                                            product = previewProduct,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        // Zoom indicator badge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.ZoomIn,
                                                contentDescription = "Zoomer",
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = KonabGreenLight,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = KonabGreenDark,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Image chargée (Toucher pour zoomer)",
                                                    color = KonabGreenDark,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (imageUri.startsWith("content://")) "Photo depuis votre appareil"
                                            else if (imageUri.startsWith("http")) "Image URL distante"
                                            else "Thème catalogue (${imageUri.removePrefix("img_prod_")})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp).testTag("btn_change_uploaded_photo")
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Changer", fontSize = 11.sp)
                                            }

                                            TextButton(
                                                onClick = {
                                                    imageUri = ""
                                                    if (galleryImages.isNotEmpty()) {
                                                        galleryImages = galleryImages.drop(1)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp).testTag("btn_delete_uploaded_photo")
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = KonabRed, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Effacer", fontSize = 11.sp, color = KonabRed)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Empty state: Big Upload Button / Dropzone
                                Surface(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, KonabBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_upload_image_area")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(KonabGreenLight, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = "Upload",
                                                tint = KonabGreenDark,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Téléverser une image (Upload photo)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = KonabGreenDark
                                        )
                                        Text(
                                            text = "Appuyez pour ouvrir la galerie ou prendre une photo",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (showUrlInput) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = imageUri,
                                    onValueChange = {
                                        imageUri = it
                                        if (it.isNotBlank() && !galleryImages.contains(it)) {
                                            galleryImages = listOf(it) + galleryImages
                                        }
                                    },
                                    label = { Text("URL de l'image (https://...)") },
                                    placeholder = { Text("https://example.com/produit.jpg") },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (imageUri.isNotBlank()) {
                                            IconButton(onClick = { imageUri = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_custom_image_url")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ou modèles d'illustrations rapides :",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(ProductImageHelper.PRESET_IMAGES) { preset ->
                                    val isSelected = imageUri == preset.key
                                    Surface(
                                        onClick = {
                                            imageUri = preset.key
                                            if (!galleryImages.contains(preset.key)) {
                                                galleryImages = listOf(preset.key) + galleryImages
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) KonabGreen else KonabBorder
                                        ),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.width(90.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(6.dp)
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(id = preset.drawableRes),
                                                contentDescription = preset.label,
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = preset.label,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) KonabGreen else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // -------------------------------------------------------------
                // CHAMP GALERIE : MULTI-IMAGES DU PRODUIT
                // -------------------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .border(1.dp, KonabBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Galerie Photos (${galleryImages.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (galleryImages.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    galleryImages = emptyList()
                                    imageUri = ""
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Vider", fontSize = 11.sp, color = KonabRed)
                            }
                        }
                    }

                    Text(
                        text = "Chargez ou capturez plusieurs photos pour présenter votre produit sous tous ses angles",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                multiplePhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("btn_add_gallery_photos")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ajouter photos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_capture_gallery_photo")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1 photo", fontSize = 11.sp)
                        }
                    }

                    if (galleryImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(galleryImages) { idx, uri ->
                                val isCover = (idx == 0 || uri == imageUri)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = if (isCover) 2.dp else 1.dp,
                                        color = if (isCover) KonabGreen else KonabBorder
                                    ),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .width(80.dp)
                                        .testTag("gallery_thumb_$idx")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clickable { zoomImageIndex = idx }
                                        ) {
                                            ProductThumbnail(
                                                product = ProductEntity(name = name, category = category, imageUri = uri),
                                                modifier = Modifier.fillMaxSize(),
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            // Delete button
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(20.dp)
                                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                                    .clickable {
                                                        val updated = galleryImages.toMutableList().apply { removeAt(idx) }
                                                        galleryImages = updated
                                                        if (uri == imageUri) {
                                                            imageUri = updated.firstOrNull() ?: ""
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }

                                            // Zoom indicator
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(18.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoomer", tint = Color.White, modifier = Modifier.size(11.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        if (isCover) {
                                            Surface(
                                                color = KonabGreenLight,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "★ Principale",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KonabGreenDark,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    imageUri = uri
                                                    val list = galleryImages.toMutableList()
                                                    list.removeAt(idx)
                                                    galleryImages = listOf(uri) + list
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(18.dp)
                                            ) {
                                                Text("Choisir", fontSize = 8.sp, color = KonabGreen)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du produit *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_product_name")
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Catégorie *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Code-barres (facultatif)") },
                    placeholder = { Text("Scannez ou saisissez") },
                    trailingIcon = {
                        IconButton(
                            onClick = { showBarcodeScannerInForm = true },
                            modifier = Modifier.testTag("btn_scan_barcode_for_product")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scanner avec l'appareil photo",
                                tint = KonabGreen
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_product_barcode")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buyPriceStr,
                        onValueChange = { buyPriceStr = it },
                        label = { Text("Prix d'achat FCFA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sellPriceStr,
                        onValueChange = { sellPriceStr = it },
                        label = { Text("Prix de vente FCFA *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_product_sell_price")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Quantité en stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minStockStr,
                        onValueChange = { minStockStr = it },
                        label = { Text("Seuil d'alerte") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unité (pièce, kg, litre, portion, etc.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && sellPriceStr.isNotBlank()) {
                        val finalGallery = if (galleryImages.isNotEmpty()) {
                            galleryImages
                        } else if (imageUri.isNotBlank()) {
                            listOf(imageUri)
                        } else {
                            emptyList()
                        }
                        val finalImageUri = imageUri.ifBlank { finalGallery.firstOrNull() ?: "" }
                        val product = ProductEntity(
                            id = initial?.id ?: 0L,
                            name = name.trim(),
                            category = category.trim(),
                            barcode = barcode.trim(),
                            buyPrice = buyPriceStr.toLongOrNull() ?: 0L,
                            sellPrice = sellPriceStr.toLongOrNull() ?: 0L,
                            stockQuantity = stockStr.toIntOrNull() ?: 0,
                            minStockAlert = minStockStr.toIntOrNull() ?: 5,
                            unit = unit.trim().ifBlank { "pièce" },
                            imageUri = finalImageUri.trim(),
                            galleryImagesJson = JSONArray(finalGallery).toString()
                        )
                        onSave(product)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                modifier = Modifier.testTag("btn_save_product")
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

    if (zoomImageIndex != null) {
        val zoomList = if (galleryImages.isNotEmpty()) galleryImages else if (imageUri.isNotBlank()) listOf(imageUri) else emptyList()
        ProductImageZoomDialog(
            images = zoomList,
            initialIndex = zoomImageIndex ?: 0,
            productName = name.ifBlank { "Produit" },
            onDismiss = { zoomImageIndex = null }
        )
    }

    if (showBarcodeScannerInForm) {
        RealBarcodeScannerModal(
            title = "Enregistrer code-barres",
            subtitle = "Pointez vers le code-barres pour l'assigner au produit",
            onBarcodeScanned = { scanned ->
                barcode = scanned.trim()
                showBarcodeScannerInForm = false
            },
            onDismiss = { showBarcodeScannerInForm = false }
        )
    }
}

@Composable
fun ProductImageZoomDialog(
    images: List<String>,
    initialIndex: Int = 0,
    productName: String,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) {
        onDismiss()
        return
    }
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, images.size - 1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", fontWeight = FontWeight.Bold, color = KonabGreen)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (images.size > 1) {
                    Surface(
                        shape = CircleShape,
                        color = KonabGreenLight
                    ) {
                        Text(
                            text = "${currentIndex + 1} / ${images.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KonabGreenDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val currentUri = images[currentIndex]
                    ProductThumbnail(
                        product = ProductEntity(name = productName, category = "Général", imageUri = currentUri),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (currentIndex > 0) {
                        IconButton(
                            onClick = { currentIndex-- },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent", tint = Color.White)
                        }
                    }

                    if (currentIndex < images.size - 1) {
                        IconButton(
                            onClick = { currentIndex++ },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant", tint = Color.White)
                        }
                    }
                }

                if (images.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(images) { index, uri ->
                            Surface(
                                onClick = { currentIndex = index },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (index == currentIndex) 2.dp else 1.dp,
                                    color = if (index == currentIndex) KonabGreen else KonabBorder
                                ),
                                modifier = Modifier.size(52.dp)
                            ) {
                                ProductThumbnail(
                                    product = ProductEntity(name = productName, category = "Général", imageUri = uri),
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
