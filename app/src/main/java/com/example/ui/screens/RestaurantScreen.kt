package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.RestaurantKitchenHelper
import com.example.data.remote.supabase.RestaurantOrderRecord
import com.example.data.remote.supabase.RestaurantOrderStatus
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.data.remote.supabase.RestaurantTableRecord
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.RealBarcodeScannerModal
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.delay

private enum class RestaurantTab(val label: String) {
    ORDERS("Commandes"),
    KITCHEN("Cuisine"),
    MENU_OF_DAY("Menu du Jour"),
    TABLES_QR("Tables & QR"),
    TEAM("Équipe")
}

@Composable
fun RestaurantScreen(
    shopProfile: ShopProfileEntity?,
    cloudShopId: Long,
    products: List<ProductEntity>,
    staffList: List<RestaurantStaffAccount>,
    activeStaff: RestaurantStaffAccount?,
    tables: List<RestaurantTableRecord>,
    orders: List<RestaurantOrderRecord>,
    menuOfDayProductIds: Set<Long>,
    isSyncingRestaurant: Boolean,
    lastRestaurantSyncAt: Long = System.currentTimeMillis(),
    initialTabIndex: Int = -1,
    onSwitchActiveStaffByPin: (String) -> RestaurantStaffAccount?,
    onSwitchActiveStaffDirect: (RestaurantStaffAccount) -> Unit,
    onSaveStaffAccount: (RestaurantStaffAccount) -> Unit,
    onDeleteStaffAccount: (String) -> Unit,
    onSaveTable: (RestaurantTableRecord) -> Unit,
    onGenerateBatchTables: (Int, String) -> Unit,
    onDeleteTable: (String) -> Unit,
    onToggleMenuOfDayProduct: (Long) -> Unit,
    onSelectAllMenuOfDay: () -> Unit,
    onClearMenuOfDay: () -> Unit,
    onAdvanceOrderStatus: (RestaurantOrderRecord, RestaurantOrderStatus) -> Unit,
    onNotifyWaiterAgain: (RestaurantOrderRecord) -> Unit = {},
    onCollectOrderPayment: (
        order: RestaurantOrderRecord,
        paymentMethod: String,
        isCredit: Boolean,
        notes: String,
        onSuccess: (SaleEntity) -> Unit
    ) -> Unit,
    onViewSaleReceiptById: (Long) -> Unit,
    onRefreshRestaurant: ((Boolean, String) -> Unit) -> Unit,
    onLivePollOrders: () -> Unit = {},
    onOpenQrScanResult: (String) -> Boolean,
    onOpenLocalMenuPreview: (String) -> Unit,
    onOpenPosForOrder: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTabIndex, activeStaff?.role) {
        val defaultTab = when {
            initialTabIndex in RestaurantTab.entries.indices -> RestaurantTab.entries[initialTabIndex]
            activeStaff?.role == RestaurantStaffRole.KITCHEN -> RestaurantTab.KITCHEN
            else -> RestaurantTab.ORDERS
        }
        mutableStateOf(defaultTab)
    }
    var showOperatorSwitcher by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var orderForCheckout by remember { mutableStateOf<RestaurantOrderRecord?>(null) }

    // Real-time auto-polling for incoming QR table orders and kitchen status updates
    LaunchedEffect(selectedTab, cloudShopId) {
        onLivePollOrders()
        val intervalMs = if (selectedTab == RestaurantTab.KITCHEN) 4_000L else 8_000L
        while (true) {
            delay(intervalMs)
            onLivePollOrders()
        }
    }

    val openOrdersCount = remember(orders) {
        orders.count { it.status.isOpenOrder }
    }
    val activeQrKitchenCount = remember(orders) {
        RestaurantKitchenHelper.filterActiveQrOrders(orders).size
    }

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Restaurant & Cuisine",
                subtitle = shopProfile?.name?.ifBlank { "Mode Restaurant Synchronisé" } ?: "Mode Restaurant",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showQrScanner = true },
                        modifier = Modifier.testTag("btn_restaurant_scan_qr")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scanner QR table",
                            tint = Color(0xFFEA580C)
                        )
                    }
                    IconButton(
                        onClick = {
                            onRefreshRestaurant { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSyncingRestaurant,
                        modifier = Modifier.testTag("btn_restaurant_sync")
                    ) {
                        if (isSyncingRestaurant) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = KonabBlue
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Synchroniser restaurant",
                                tint = KonabBlue
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Active Operator & Multi-User Sync Strip
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val roleColor = when (activeStaff?.role) {
                            RestaurantStaffRole.KITCHEN -> Color(0xFFEA580C)
                            RestaurantStaffRole.WAITER -> KonabGreen
                            RestaurantStaffRole.CASHIER -> KonabPurple
                            else -> KonabBlue
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(roleColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (activeStaff?.role) {
                                    RestaurantStaffRole.KITCHEN -> Icons.Default.SoupKitchen
                                    RestaurantStaffRole.WAITER -> Icons.Default.RoomService
                                    RestaurantStaffRole.CASHIER -> Icons.Default.PointOfSale
                                    else -> Icons.Default.ManageAccounts
                                },
                                contentDescription = null,
                                tint = roleColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = activeStaff?.name?.ifBlank { "Gérant" } ?: "Gérant",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = roleColor,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = activeStaff?.role?.badgeLabel ?: "GÉRANT",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Synchronisation multi-postes active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { showOperatorSwitcher = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("btn_switch_restaurant_operator")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Changer profil", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4-Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFFEA580C),
                edgePadding = 12.dp
            ) {
                RestaurantTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.testTag("tab_restaurant_${tab.name.lowercase()}"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tab.label,
                                    fontWeight = if (selectedTab == tab) FontWeight.ExtraBold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                if (tab == RestaurantTab.ORDERS && openOrdersCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFFEA580C),
                                        contentColor = Color.White
                                    ) {
                                        Text("$openOrdersCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else if (tab == RestaurantTab.KITCHEN && activeQrKitchenCount > 0) {
                                    Badge(
                                        containerColor = KonabGreen,
                                        contentColor = Color.White
                                    ) {
                                        Text("$activeQrKitchenCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    RestaurantTab.ORDERS -> {
                        RestaurantOrdersTab(
                            orders = orders,
                            activeRole = activeStaff?.role ?: RestaurantStaffRole.MANAGER,
                            onAdvanceStatus = onAdvanceOrderStatus,
                            onOpenCheckout = { order -> orderForCheckout = order },
                            onViewReceiptById = onViewSaleReceiptById,
                            onOpenPos = onOpenPosForOrder
                        )
                    }

                    RestaurantTab.KITCHEN -> {
                        RestaurantKitchenTab(
                            orders = orders,
                            isSyncing = isSyncingRestaurant,
                            lastSyncAt = lastRestaurantSyncAt,
                            onRefreshNow = {
                                onRefreshRestaurant { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStartPreparation = { order ->
                                onAdvanceOrderStatus(order, RestaurantOrderStatus.IN_PREPARATION)
                            },
                            onMarkReadyToServe = { order ->
                                onAdvanceOrderStatus(order, RestaurantOrderStatus.READY)
                            },
                            onMarkServed = { order ->
                                onAdvanceOrderStatus(order, RestaurantOrderStatus.SERVED)
                            },
                            onNotifyWaiterAgain = onNotifyWaiterAgain,
                            onOpenTablesQr = { selectedTab = RestaurantTab.TABLES_QR }
                        )
                    }

                    RestaurantTab.MENU_OF_DAY -> {
                        RestaurantMenuOfDayTab(
                            products = products,
                            menuOfDayIds = menuOfDayProductIds,
                            onToggleProduct = onToggleMenuOfDayProduct,
                            onSelectAll = onSelectAllMenuOfDay,
                            onClearAll = onClearMenuOfDay,
                            onPreviewCustomerMenu = {
                                val firstTable = tables.firstOrNull()?.tableNumber ?: "Table 1"
                                onOpenLocalMenuPreview(firstTable)
                            }
                        )
                    }

                    RestaurantTab.TABLES_QR -> {
                        RestaurantTablesQrTab(
                            shopName = shopProfile?.name ?: "Mon Restaurant",
                            city = shopProfile?.city.orEmpty(),
                            phone = shopProfile?.phone.orEmpty(),
                            tables = tables,
                            onSaveTable = onSaveTable,
                            onGenerateBatchTables = onGenerateBatchTables,
                            onDeleteTable = onDeleteTable,
                            onPreviewTableMenu = { tableName -> onOpenLocalMenuPreview(tableName) }
                        )
                    }

                    RestaurantTab.TEAM -> {
                        RestaurantTeamSyncTab(
                            shopName = shopProfile?.name ?: "Mon Restaurant",
                            shopPhone = shopProfile?.phone.orEmpty(),
                            cloudShopId = cloudShopId,
                            staffList = staffList,
                            activeStaff = activeStaff,
                            onSwitchActiveStaff = onSwitchActiveStaffDirect,
                            onSaveStaff = onSaveStaffAccount,
                            onDeleteStaff = onDeleteStaffAccount
                        )
                    }
                }
            }
        }
    }

    // Operator PIN Switcher Dialog
    if (showOperatorSwitcher) {
        OperatorSwitcherDialog(
            staffList = staffList,
            activeStaffId = activeStaff?.id,
            onSwitchByPin = { pin ->
                val found = onSwitchActiveStaffByPin(pin)
                if (found != null) {
                    Toast.makeText(
                        context,
                        "Profil actif : ${found.name} (${found.role.badgeLabel})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                found
            },
            onSwitchDirect = { staff ->
                onSwitchActiveStaffDirect(staff)
                showOperatorSwitcher = false
                Toast.makeText(context, "Profil actif : ${staff.name}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showOperatorSwitcher = false }
        )
    }

    // Direct Checkout Dialog for Restaurant Order ("Encaisser directement sur la plateforme")
    orderForCheckout?.let { order ->
        RestaurantDirectCheckoutDialog(
            order = order,
            onDismiss = { orderForCheckout = null },
            onConfirmPayment = { method, isCredit, notes ->
                onCollectOrderPayment(order, method, isCredit, notes) { sale ->
                    orderForCheckout = null
                    Toast.makeText(
                        context,
                        "Encaissement confirmé • Ticket #${sale.transactionNumber}",
                        Toast.LENGTH_LONG
                    ).show()
                    onViewSaleReceiptById(sale.id)
                }
            }
        )
    }

    if (showQrScanner) {
        RealBarcodeScannerModal(
            title = "Scanner QR Code Table",
            subtitle = "Scannez le QR code d'une table pour ouvrir son Menu du Jour",
            onBarcodeScanned = { raw ->
                showQrScanner = false
                if (!onOpenQrScanResult(raw)) {
                    Toast.makeText(
                        context,
                        "QR code non reconnu comme menu de table Konab Restaurant.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
private fun OperatorSwitcherDialog(
    staffList: List<RestaurantStaffAccount>,
    activeStaffId: String?,
    onSwitchByPin: (String) -> RestaurantStaffAccount?,
    onSwitchDirect: (RestaurantStaffAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dialog_operator_switcher")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Changer d'opérateur Restaurant",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Saisissez un code PIN ou choisissez un compte sur cet appareil :",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter { c -> c.isDigit() }.take(8)
                        pinError = ""
                    },
                    label = { Text("Code PIN rapide") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_operator_switch_pin")
                )
                if (pinError.isNotBlank()) {
                    Text(pinError, color = KonabRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val res = onSwitchByPin(pinInput)
                        if (res != null) {
                            onDismiss()
                        } else {
                            pinError = "Aucun profil actif trouvé avec ce code PIN."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_operator_pin_confirm")
                ) {
                    Text("Basculer avec le PIN", fontWeight = FontWeight.Bold)
                }

                if (staffList.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Comptes configurés :", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    staffList.filter { it.isActive }.forEach { member ->
                        val isCurrent = member.id == activeStaffId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) KonabBlueContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isCurrent) KonabBlue else Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSwitchDirect(member) }
                                .testTag("btn_direct_switch_${member.role.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(member.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    Text(member.role.displayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = if (isCurrent) KonabGreen else KonabBlue,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isCurrent) "ACTIF" else member.role.badgeLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fermer")
                }
            }
        }
    }
}

@Composable
private fun RestaurantDirectCheckoutDialog(
    order: RestaurantOrderRecord,
    onDismiss: () -> Unit,
    onConfirmPayment: (method: String, isCredit: Boolean, notes: String) -> Unit
) {
    val paymentMethods = listOf("Espèces", "Orange Money", "Moov Money", "Wave", "Carte bancaire", "Crédit client")
    var selectedMethod by remember { mutableStateOf("Espèces") }
    var receivedCashText by remember { mutableStateOf(order.totalAmount.toString()) }
    var extraNote by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val receivedCash = receivedCashText.toLongOrNull() ?: 0L
    val changeDue = if (selectedMethod == "Espèces") maxOf(0L, receivedCash - order.totalAmount) else 0L

    Dialog(onDismissRequest = { if (!isProcessing) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dialog_restaurant_direct_checkout")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Encaissement Direct",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${order.orderNumber} • ${order.tableName} • ${order.customerName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { if (!isProcessing) onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = KonabGreenLight,
                    border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Montant à encaisser", fontWeight = FontWeight.Bold, color = KonabGreenDark)
                        Text(
                            text = CurrencyFormatter.formatFCFA(order.totalAmount),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = KonabGreenDark
                        )
                    }
                }

                Text("Mode de paiement :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    paymentMethods.chunked(2).forEach { rowMethods ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMethods.forEach { method ->
                                val isSel = selectedMethod == method
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedMethod = method },
                                    label = {
                                        Text(
                                            method,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KonabBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedMethod == "Espèces") {
                    OutlinedTextField(
                        value = receivedCashText,
                        onValueChange = { receivedCashText = it.filter { c -> c.isDigit() } },
                        label = { Text("Montant remis (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (receivedCash > order.totalAmount) {
                        Text(
                            text = "Monnaie à rendre : ${CurrencyFormatter.formatFCFA(changeDue)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = KonabOrange
                        )
                    }
                }

                OutlinedTextField(
                    value = extraNote,
                    onValueChange = { extraNote = it },
                    label = { Text("Note ticket (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        isProcessing = true
                        onConfirmPayment(
                            selectedMethod,
                            selectedMethod == "Crédit client",
                            extraNote
                        )
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_restaurant_confirm_pay")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Encaisser & Générer Reçu", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
