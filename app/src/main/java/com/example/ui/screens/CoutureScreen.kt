package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.CoutureModelRecord
import com.example.data.remote.supabase.CoutureOrderRecord
import com.example.data.remote.supabase.CoutureStage
import com.example.ui.components.CoutureMeasurementsModal
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.theme.KonabBlue
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabOrange
import com.example.ui.theme.OutfitFontFamily
import com.example.util.CouturePhotoCaptureControl
import com.example.util.CouturePhotoImage
import com.example.util.CoutureQrCardHelper
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AtelierViolet = Color(0xFF5B21B6)
private val AtelierVioletLight = Color(0xFFF5F3FF)
private val AtelierGold = Color(0xFFD97706)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoutureScreen(
    shopProfile: ShopProfileEntity?,
    cloudShopId: Long,
    models: List<CoutureModelRecord>,
    orders: List<CoutureOrderRecord>,
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    isSyncingCouture: Boolean,
    lastCoutureSyncAt: Long,
    onSaveModel: (CoutureModelRecord) -> Unit,
    onDeleteModel: (String) -> Unit,
    onSaveOrder: (CoutureOrderRecord) -> Unit,
    onAdvanceOrderStage: (CoutureOrderRecord, CoutureStage?) -> Unit,
    onCollectOrderPayment: (CoutureOrderRecord, method: String, notes: String, onSuccess: (SaleEntity) -> Unit) -> Unit,
    onRefreshCouture: ((Boolean, String) -> Unit) -> Unit,
    onLivePollCouture: () -> Unit,
    onOpenQrScanResult: (String) -> Boolean,
    onOpenLocalCatalogPreview: () -> Unit,
    onOpenStockScreen: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currency = shopProfile?.currency?.ifBlank { "FCFA" } ?: "FCFA"
    val shopName = shopProfile?.name?.ifBlank { "Atelier Couture" } ?: "Atelier Couture"

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mesures & Commandes, 1: Modèles & Tissus, 2: Catalogue QR
    var stageFilter by remember { mutableStateOf("ACTIVE") } // ACTIVE, READY, ALL
    var searchQuery by remember { mutableStateOf("") }

    var editingOrder by remember { mutableStateOf<CoutureOrderRecord?>(null) }
    var showMeasurementsModal by remember { mutableStateOf(false) }

    var editingModel by remember { mutableStateOf<CoutureModelRecord?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }

    var paymentOrderTarget by remember { mutableStateOf<CoutureOrderRecord?>(null) }
    var showScannerModal by remember { mutableStateOf(false) }

    LaunchedEffect(cloudShopId) {
        while (isActive) {
            onLivePollCouture()
            delay(10_000L)
        }
    }

    val openOrdersCount = remember(orders) { orders.count { it.status.isOpenWork } }
    val readyOrdersCount = remember(orders) { orders.count { it.status == CoutureStage.ESSAYAGE_PRET } }
    val clientRequestsCount = remember(orders) { orders.count { it.status == CoutureStage.DEMANDE_CLIENT } }

    val filteredOrders = remember(orders, stageFilter, searchQuery) {
        val base = when (stageFilter) {
            "READY" -> orders.filter { it.status == CoutureStage.ESSAYAGE_PRET }
            "ALL" -> orders
            else -> orders.filter { it.status.isOpenWork }
        }
        val cleanQ = searchQuery.trim()
        if (cleanQ.isBlank()) base else {
            base.filter {
                it.fullCustomerName.contains(cleanQ, ignoreCase = true) ||
                    it.customerPhone.contains(cleanQ, ignoreCase = true) ||
                    it.orderNumber.contains(cleanQ, ignoreCase = true) ||
                    it.modelTitle.contains(cleanQ, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("couture_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Atelier Couture • $shopName",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${models.size} modèle(s) • $openOrdersCount tenue(s) en atelier",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showScannerModal = true },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_couture_scan_qr")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Tester scan QR", tint = AtelierViolet)
                    }
                    IconButton(
                        onClick = {
                            onRefreshCouture { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncingCouture,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_couture_sync")
                    ) {
                        if (isSyncingCouture) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AtelierViolet)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Synchroniser", tint = AtelierViolet)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingOrder = null
                        showMeasurementsModal = true
                    },
                    containerColor = AtelierViolet,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_new_couture_measurement")
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Prendre Mesures", fontWeight = FontWeight.ExtraBold)
                }
            } else if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingModel = null
                        showModelDialog = true
                    },
                    containerColor = AtelierViolet,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_couture_model")
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ajouter un modèle", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Sync Header & KPI Strip
            Surface(
                color = AtelierVioletLight,
                border = BorderStroke(1.dp, AtelierViolet.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Demandes QR: $clientRequestsCount • Prêtes: $readyOrdersCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AtelierViolet
                        )
                        val syncTime = remember(lastCoutureSyncAt) {
                            SimpleDateFormat("HH:mm:ss", Locale.FRENCH).format(Date(lastCoutureSyncAt))
                        }
                        Text(
                            text = "Dernière synchro : $syncTime",
                            fontSize = 10.5.sp,
                            color = Color(0xFF4C1D95)
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenLocalCatalogPreview,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AtelierViolet,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.heightIn(min = 40.dp).testTag("btn_couture_preview_catalog")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vue Client", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Mesures (${openOrdersCount})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Modèles & Tissus", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Checkroom, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("QR Catalogue", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> CoutureOrdersAndMeasurementsTab(
                    orders = filteredOrders,
                    currency = currency,
                    stageFilter = stageFilter,
                    onStageFilterChange = { stageFilter = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onNewMeasurement = {
                        editingOrder = null
                        showMeasurementsModal = true
                    },
                    onEditOrder = { order ->
                        editingOrder = order
                        showMeasurementsModal = true
                    },
                    onAdvanceStage = { order ->
                        onAdvanceOrderStage(order, null)
                    },
                    onCollectPayment = { order ->
                        paymentOrderTarget = order
                    }
                )
                1 -> CoutureModelsAndFabricsTab(
                    models = models,
                    products = products,
                    currency = currency,
                    onAddModel = {
                        editingModel = null
                        showModelDialog = true
                    },
                    onEditModel = { m ->
                        editingModel = m
                        showModelDialog = true
                    },
                    onDeleteModel = onDeleteModel,
                    onOpenStockScreen = onOpenStockScreen
                )
                2 -> CoutureQrCatalogTab(
                    shopProfile = shopProfile,
                    cloudShopId = cloudShopId,
                    modelsCount = models.size,
                    onOpenLocalPreview = onOpenLocalCatalogPreview
                )
            }
        }
    }

    if (showMeasurementsModal) {
        CoutureMeasurementsModal(
            initialOrder = editingOrder,
            models = models,
            customers = customers,
            currency = currency,
            shopId = cloudShopId,
            shopName = shopName,
            onSave = { savedOrder ->
                onSaveOrder(savedOrder)
                showMeasurementsModal = false
                editingOrder = null
                Toast.makeText(context, "Fiche couture & mesures enregistrées !", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showMeasurementsModal = false
                editingOrder = null
            }
        )
    }

    if (showModelDialog) {
        CoutureModelEditDialog(
            initialModel = editingModel,
            currency = currency,
            shopId = cloudShopId,
            shopName = shopName,
            onSave = { model ->
                onSaveModel(model)
                showModelDialog = false
                editingModel = null
                Toast.makeText(context, "Modèle enregistré au catalogue QR", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showModelDialog = false
                editingModel = null
            }
        )
    }

    paymentOrderTarget?.let { order ->
        CouturePaymentDialog(
            order = order,
            currency = currency,
            onConfirm = { method, notes ->
                onCollectOrderPayment(order, method, notes) { sale ->
                    paymentOrderTarget = null
                    Toast.makeText(
                        context,
                        "Encaissement validé (${sale.transactionNumber}) !",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { paymentOrderTarget = null }
        )
    }

    if (showScannerModal) {
        RealBarcodeScannerModal(
            onDismiss = { showScannerModal = false },
            onBarcodeScanned = { scanned ->
                showScannerModal = false
                if (!onOpenQrScanResult(scanned)) {
                    Toast.makeText(context, "QR non reconnu pour le catalogue couture.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun CoutureOrdersAndMeasurementsTab(
    orders: List<CoutureOrderRecord>,
    currency: String,
    stageFilter: String,
    onStageFilterChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewMeasurement: () -> Unit,
    onEditOrder: (CoutureOrderRecord) -> Unit,
    onAdvanceStage: (CoutureOrderRecord) -> Unit,
    onCollectPayment: (CoutureOrderRecord) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.FRENCH) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Rechercher client, tél ou modèle...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = stageFilter == "ACTIVE",
                    onClick = { onStageFilterChange("ACTIVE") },
                    label = { Text("En cours", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = stageFilter == "READY",
                    onClick = { onStageFilterChange("READY") },
                    label = { Text("Prêts à livrer", fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = stageFilter == "ALL",
                    onClick = { onStageFilterChange("ALL") },
                    label = { Text("Tout l'atelier", fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (orders.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Straighten, contentDescription = null, tint = AtelierViolet, modifier = Modifier.size(40.dp))
                        Text("Aucune tenue dans cette vue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "Prenez les mesures d'un client, capturez la photo de son tissu et fixez son rendez-vous.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNewMeasurement,
                            colors = ButtonDefaults.buttonColors(containerColor = AtelierViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Nouvelle prise de mesures", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(orders, key = { it.id }) { order ->
            val stageColor = when (order.status) {
                CoutureStage.DEMANDE_CLIENT -> KonabOrange
                CoutureStage.ESSAYAGE_PRET -> KonabGreen
                CoutureStage.LIVRE_ENCAISSE -> KonabBlue
                else -> AtelierViolet
            }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, stageColor.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CouturePhotoImage(
                            uriOrKey = order.fabricPhotoUri.ifBlank { order.modelImageUri },
                            contentDescription = order.modelTitle,
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = order.fullCustomerName,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = stageColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${order.status.progressPercent}% ${order.status.shortLabel}",
                                        color = stageColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${order.orderNumber} • ${order.customerPhone}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (order.isCustomClientModel) "Modèle client : ${order.modelTitle}" else order.modelTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = AtelierViolet,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { order.status.progressPercent / 100f },
                        color = stageColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Text(
                        text = "Mesures (${order.measurements.bodyProfile}) : ${order.measurements.compactSummary()}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val apptStr = if (order.appointmentAt > 0L) "RDV: ${dateFmt.format(Date(order.appointmentAt))}" else "RDV à fixer"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = apptStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = CurrencyFormatter.formatAmount(order.totalPrice, currency),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                            if (order.advancePaid > 0L && order.remainingBalance > 0L) {
                                Text(
                                    text = "Reste : ${CurrencyFormatter.formatAmount(order.remainingBalance, currency)}",
                                    fontSize = 10.5.sp,
                                    color = KonabOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEditOrder(order) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (order.status == CoutureStage.DEMANDE_CLIENT) "Prendre mesures" else "Mesures / Photo",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val nextStage = order.status.nextStage()
                        if (nextStage != null && nextStage != CoutureStage.LIVRE_ENCAISSE) {
                            Button(
                                onClick = { onAdvanceStage(order) },
                                colors = ButtonDefaults.buttonColors(containerColor = AtelierViolet),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Étape : ${nextStage.shortLabel}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (order.status != CoutureStage.LIVRE_ENCAISSE) {
                            Button(
                                onClick = { onCollectPayment(order) },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Encaisser", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoutureModelsAndFabricsTab(
    models: List<CoutureModelRecord>,
    products: List<ProductEntity>,
    currency: String,
    onAddModel: () -> Unit,
    onEditModel: (CoutureModelRecord) -> Unit,
    onDeleteModel: (String) -> Unit,
    onOpenStockScreen: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Catalogue des Modèles (${models.size})",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                    Text(
                        text = "Visibles lorsqu'un client scanne le QR Code de votre atelier",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onAddModel) {
                    Text("+ Modèle", fontWeight = FontWeight.Bold, color = AtelierViolet)
                }
            }
        }

        items(models, key = { it.id }) { model ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CouturePhotoImage(
                        uriOrKey = model.imageUri,
                        contentDescription = model.title,
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2)
                        Text(
                            text = "${model.category} • ~${model.estimatedDays} jour(s)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatAmount(model.price, currency),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = AtelierViolet
                        )
                    }
                    Column {
                        IconButton(onClick = { onEditModel(model) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier modèle", tint = KonabBlue)
                        }
                        IconButton(onClick = { onDeleteModel(model.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer modèle", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stock Tissus & Mercerie (${products.size})",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onOpenStockScreen) {
                    Text("Gérer le stock", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (products.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aucun tissu ou accessoire en stock. Utilisez Stock pour inventorier vos rouleaux de pagne, bazin, fils et fermetures.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(products.take(10), key = { "stock_${it.id}" }) { prod ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${prod.category} • ${CurrencyFormatter.formatAmount(prod.sellPrice, currency)}/${prod.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (prod.stockQuantity <= prod.minStockAlert) Color(0xFFFEE2E2) else AtelierVioletLight
                        ) {
                            Text(
                                text = "${prod.stockQuantity} ${prod.unit}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (prod.stockQuantity <= prod.minStockAlert) Color(0xFFB91C1C) else AtelierViolet,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoutureQrCatalogTab(
    shopProfile: ShopProfileEntity?,
    cloudShopId: Long,
    modelsCount: Int,
    onOpenLocalPreview: () -> Unit
) {
    val context = LocalContext.current
    val shopName = shopProfile?.name?.ifBlank { "Atelier Couture" } ?: "Atelier Couture"
    val posterBitmap: Bitmap = remember(shopName, cloudShopId, modelsCount, shopProfile?.city, shopProfile?.phone) {
        CoutureQrCardHelper.buildCatalogQrCardBitmap(
            shopName = shopName,
            shopId = cloudShopId,
            city = shopProfile?.city.orEmpty(),
            phone = shopProfile?.phone.orEmpty(),
            modelsCount = modelsCount
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Affiche QR du Catalogue Modèles",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
        Text(
            text = "Affichez ou imprimez ce QR code dans votre atelier : vos clients le scannent pour voir vos modèles avec photos et tarifs puis choisir leur tenue.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = BorderStroke(1.dp, AtelierViolet.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Image(
                bitmap = posterBitmap.asImageBitmap(),
                contentDescription = "QR Code Catalogue $shopName",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(900f / 1240f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val res = CoutureQrCardHelper.saveCatalogQrPngToDevice(context, posterBitmap, shopName)
                    Toast.makeText(
                        context,
                        res.getOrNull() ?: res.exceptionOrNull()?.message ?: "Erreur sauvegarde PNG",
                        Toast.LENGTH_LONG
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AtelierViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_save_couture_qr_png")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Télécharger PNG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    CoutureQrCardHelper.printCatalogQrCard(context, posterBitmap, shopName)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Imprimer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    CoutureQrCardHelper.shareCatalogQrPng(context, posterBitmap, shopName)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Partager", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            FilledTonalButton(
                onClick = onOpenLocalPreview,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tester le catalogue", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CoutureModelEditDialog(
    initialModel: CoutureModelRecord?,
    currency: String,
    shopId: Long,
    shopName: String,
    onSave: (CoutureModelRecord) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialModel?.title ?: "") }
    var category by remember { mutableStateOf(initialModel?.category ?: "Confections Dames") }
    var priceText by remember { mutableStateOf(initialModel?.price?.takeIf { it > 0L }?.toString() ?: "15000") }
    var daysText by remember { mutableStateOf((initialModel?.estimatedDays ?: 5).toString()) }
    var description by remember { mutableStateOf(initialModel?.description ?: "") }
    var imageUri by remember { mutableStateOf(initialModel?.imageUri ?: "img_prod_mode") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initialModel == null) "Nouveau Modèle Catalogue" else "Modifier Modèle",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                CouturePhotoCaptureControl(
                    title = "Photo du modèle",
                    subtitle = "Prenez une photo ou choisissez depuis la galerie",
                    currentPhotoUri = imageUri,
                    onPhotoSelected = { imageUri = it },
                    filePrefix = "modele"
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nom du modèle *") },
                    placeholder = { Text("Ex: Boubou Bazin Brodé Luxe") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Catégorie") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                        label = { Text("Tarif ($currency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { c -> c.isDigit() } },
                        label = { Text("Délai (jours)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Détails / tissu recommandé") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) return@Button
                            onSave(
                                CoutureModelRecord(
                                    id = initialModel?.id.orEmpty(),
                                    shopId = shopId,
                                    shopName = shopName,
                                    title = title.trim(),
                                    category = category.trim().ifBlank { "Tenue sur-mesure" },
                                    description = description.trim(),
                                    price = priceText.toLongOrNull() ?: 0L,
                                    estimatedDays = (daysText.toIntOrNull() ?: 5).coerceIn(1, 90),
                                    imageUri = imageUri.ifBlank { "img_prod_mode" },
                                    isFeatured = true,
                                    isAvailable = true
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AtelierViolet),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CouturePaymentDialog(
    order: CoutureOrderRecord,
    currency: String,
    onConfirm: (method: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val due = order.remainingBalance.takeIf { it > 0L } ?: order.totalPrice
    var method by remember { mutableStateOf("Espèces") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encaisser ${order.orderNumber}", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${order.fullCustomerName} • ${order.modelTitle}", fontWeight = FontWeight.Bold)
                Text(
                    text = "Solde à encaisser : ${CurrencyFormatter.formatAmount(due, currency)}",
                    color = KonabGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Espèces", "Orange Money", "Moov Money", "Wave").forEach { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Remarque encaissement") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(method, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
            ) {
                Text("Valider l'encaissement", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
