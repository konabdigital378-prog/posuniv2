package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.components.KonabCardShape
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabSearchField
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabCardBorder
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterHelper
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateFilterPeriod(val label: String) {
    ALL("Toutes"),
    TODAY("Aujourd'hui"),
    YESTERDAY("Hier"),
    THIS_WEEK("Cette semaine"),
    THIS_MONTH("Ce mois-ci")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    sales: List<SaleEntity>,
    saleItems: List<SaleItemEntity>,
    shopProfile: ShopProfileEntity?,
    onViewSaleReceipt: (SaleEntity) -> Unit,
    onCancelSale: (saleId: Long, reason: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf(DateFilterPeriod.ALL) }
    var selectedPaymentMethod by remember { mutableStateOf("Tous") }
    var selectedStatus by remember { mutableStateOf("Tous") } // Tous, Payées, Crédit, Annulées

    // Cancellation dialog state
    var saleToCancel by remember { mutableStateOf<SaleEntity?>(null) }
    var cancellationReasonText by remember { mutableStateOf("") }
    var isCancelling by remember { mutableStateOf(false) }

    // Map sales to their items for fast lookups
    val itemsBySaleId = remember(saleItems) {
        saleItems.groupBy { it.saleId }
    }

    // Filter sales based on period, payment method, status, and search query
    val filteredSales = remember(sales, searchQuery, selectedPeriod, selectedPaymentMethod, selectedStatus) {
        val nowCal = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterdayStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val weekStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        sales.filter { sale ->
            // Date filter
            val dateMatches = when (selectedPeriod) {
                DateFilterPeriod.ALL -> true
                DateFilterPeriod.TODAY -> sale.timestamp >= todayStart
                DateFilterPeriod.YESTERDAY -> sale.timestamp in yesterdayStart until todayStart
                DateFilterPeriod.THIS_WEEK -> sale.timestamp >= weekStart
                DateFilterPeriod.THIS_MONTH -> sale.timestamp >= monthStart
            }

            // Payment method filter
            val methodMatches = if (selectedPaymentMethod == "Tous") {
                true
            } else {
                sale.paymentMethod.equals(selectedPaymentMethod, ignoreCase = true)
            }

            // Status filter
            val statusMatches = when (selectedStatus) {
                "Tous" -> true
                "Payées" -> !sale.isCancelled && sale.paymentStatus == "PAYÉ"
                "Crédit" -> !sale.isCancelled && sale.paymentStatus == "CRÉDIT"
                "Annulées" -> sale.isCancelled
                else -> true
            }

            // Search query filter
            val query = searchQuery.trim().lowercase()
            val queryMatches = if (query.isEmpty()) {
                true
            } else {
                sale.transactionNumber.lowercase().contains(query) ||
                    sale.customerName.lowercase().contains(query) ||
                    sale.cashierName.lowercase().contains(query) ||
                    sale.notes.lowercase().contains(query)
            }

            dateMatches && methodMatches && statusMatches && queryMatches
        }
    }

    // Key financial metrics of filtered sales
    val activeSales = remember(filteredSales) { filteredSales.filter { !it.isCancelled } }
    val cancelledSales = remember(filteredSales) { filteredSales.filter { it.isCancelled } }
    val totalRevenue = remember(activeSales) { activeSales.sumOf { it.totalAmount } }
    val totalCancelledAmount = remember(cancelledSales) { cancelledSales.sumOf { it.totalAmount } }
    val averageTicket = remember(activeSales, totalRevenue) {
        if (activeSales.isNotEmpty()) totalRevenue / activeSales.size else 0L
    }

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Historique des transactions",
                subtitle = "${filteredSales.size} vente(s) trouvée(s)",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        val widthClass = LocalKonabWidthClass.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
            // Search Input
            KonabSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "N° ticket, nom du client, caissier...",
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("input_search_transactions")
            )

            // Date Filters Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DateFilterPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label, fontSize = 12.sp) },
                        leadingIcon = if (selectedPeriod == period) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KonabGreenLight,
                            selectedLabelColor = KonabGreenDark
                        )
                    )
                }
            }

            // Payment Method & Status Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status Filter Chips
                listOf("Tous", "Payées", "Crédit", "Annulées").forEach { status ->
                    val isSelected = selectedStatus == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedStatus = status },
                        label = { Text(status, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (status == "Annulées") KonabRedContainer else KonabGreenLight,
                            selectedLabelColor = if (status == "Annulées") KonabRed else KonabGreenDark
                        )
                    )
                }

                VerticalDivider(modifier = Modifier.height(28.dp).padding(vertical = 4.dp))

                // Payment Method Chips
                listOf("Tous", "Espèces", "Orange Money", "Moov Money", "Wave", "Carte bancaire").forEach { method ->
                    val isSelected = selectedPaymentMethod == method
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPaymentMethod = method },
                        label = { Text(method, fontSize = 12.sp) }
                    )
                }
            }

            // Financial Metrics Summary Card
            Card(
                shape = KonabCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = konabCardBorder(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Chiffre Net Validé", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.formatFCFA(totalRevenue),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${activeSales.size} vente(s) active(s)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(34.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Panier Moyen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.formatFCFA(averageTicket),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    if (cancelledSales.isNotEmpty()) {
                        VerticalDivider(modifier = Modifier.height(34.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Annulations", fontSize = 11.sp, color = KonabRed)
                            Text(
                                "-${CurrencyFormatter.formatFCFA(totalCancelledAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = KonabRed
                            )
                            Text(
                                "${cancelledSales.size} vente(s)",
                                fontSize = 11.sp,
                                color = KonabRed
                            )
                        }
                    }
                }
            }

            // Transactions List / Adaptive Grid
            if (filteredSales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KonabEmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Aucune transaction trouvée",
                        subtitle = "Modifiez vos critères de filtres ou la période sélectionnée.",
                        accentColor = KonabGreen,
                        actionLabel = if (searchQuery.isNotBlank() || selectedPeriod != DateFilterPeriod.ALL || selectedStatus != "Tous" || selectedPaymentMethod != "Tous") "Réinitialiser les filtres" else null,
                        onAction = {
                            searchQuery = ""
                            selectedPeriod = DateFilterPeriod.ALL
                            selectedStatus = "Tous"
                            selectedPaymentMethod = "Tous"
                        }
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = if (widthClass == KonabWidthClass.COMPACT) {
                        GridCells.Fixed(1)
                    } else {
                        GridCells.Adaptive(minSize = 320.dp)
                    },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredSales, key = { _, s -> s.id }) { idx, sale ->
                        val items = itemsBySaleId[sale.id] ?: emptyList()
                        KonabStaggeredReveal(index = idx) {
                            TransactionCard(
                                sale = sale,
                                items = items,
                                shopProfile = shopProfile,
                                onViewReceipt = { onViewSaleReceipt(sale) },
                                onRequestCancel = {
                                    saleToCancel = sale
                                    cancellationReasonText = ""
                                },
                                onPrintBluetooth = {
                                    if (shopProfile != null) {
                                        coroutineScope.launch {
                                            val printer = BluetoothPrinterHelper.getSavedPrinter(context)
                                            if (printer == null) {
                                                Toast.makeText(context, "Aucune imprimante connectée (Paramètres -> Imprimante)", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            val bytes = BluetoothPrinterHelper.buildSaleReceiptBytes(
                                                shop = shopProfile,
                                                sale = sale,
                                                items = items,
                                                paperWidth = BluetoothPrinterHelper.getSavedPaperWidth(context)
                                            )
                                            val res = BluetoothPrinterHelper.printBytes(context, printer.address, bytes)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Ticket imprimé !", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Erreur impression : ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }

    // Cancellation Confirmation & Reason Dialog
    if (saleToCancel != null) {
        val targetSale = saleToCancel!!
        val quickReasons = listOf(
            "Erreur de saisie d'articles",
            "Retour marchandise / insatisfaction",
            "Double encaissement par erreur",
            "Client a renoncé à l'achat",
            "Erreur de mode de paiement"
        )

        AlertDialog(
            onDismissRequest = {
                if (!isCancelling) saleToCancel = null
            },
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = KonabRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Annuler la vente N° ${targetSale.transactionNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Warning Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = KonabRedContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Action irréversible :",
                                fontWeight = FontWeight.Bold,
                                color = KonabRed,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Les articles seront réintégrés automatiquement au stock.\n• Si payé en espèces, une sortie de caisse sera comptabilisée.\n• Si vente à crédit, la dette du client sera déduite.",
                                fontSize = 11.sp,
                                color = KonabRed
                            )
                        }
                    }

                    // Sale Summary Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Montant :", fontSize = 13.sp, color = KonabTextSecondary)
                        Text(
                            CurrencyFormatter.formatFCFA(targetSale.totalAmount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Client :", fontSize = 13.sp, color = KonabTextSecondary)
                        Text(targetSale.customerName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    HorizontalDivider()

                    // Quick Reason Selection Chips
                    Text(
                        text = "Motif de l'annulation (obligatoire) :",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickReasons.forEach { reason ->
                            SuggestionChip(
                                onClick = { cancellationReasonText = reason },
                                label = { Text(reason, fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (cancellationReasonText == reason) KonabGreenLight else MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = cancellationReasonText,
                        onValueChange = { cancellationReasonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cancellation_reason"),
                        placeholder = { Text("Précisez la raison détaillée de l'annulation...", fontSize = 12.sp) },
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reason = cancellationReasonText.trim().ifBlank { "Annulation sans motif spécifié" }
                        isCancelling = true
                        onCancelSale(targetSale.id, reason) { success, message ->
                            isCancelling = false
                            saleToCancel = null
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = cancellationReasonText.isNotBlank() && !isCancelling,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabRed),
                    modifier = Modifier.testTag("btn_confirm_cancel_sale")
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirmer l'annulation")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { saleToCancel = null },
                    enabled = !isCancelling
                ) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
fun TransactionCard(
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    shopProfile: ShopProfileEntity?,
    onViewReceipt: () -> Unit,
    onRequestCancel: () -> Unit,
    onPrintBluetooth: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusBadgeColor = when {
        sale.isCancelled -> KonabRed
        sale.paymentStatus == "CRÉDIT" -> KonabGoldDark
        else -> KonabGreen
    }

    val statusBgColor = when {
        sale.isCancelled -> KonabRedContainer
        sale.paymentStatus == "CRÉDIT" -> KonabGoldContainer
        else -> KonabGreenLight
    }

    val statusLabel = when {
        sale.isCancelled -> "ANNULÉ"
        sale.paymentStatus == "CRÉDIT" -> "CRÉDIT"
        else -> "PAYÉ"
    }

    Card(
        shape = KonabCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (sale.isCancelled) KonabRed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .konabPressScale(pressedScale = 0.99f)
            .testTag("transaction_card_${sale.transactionNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Transaction #, Date, Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (sale.isCancelled) Icons.Default.Close else Icons.Default.Receipt,
                            contentDescription = null,
                            tint = statusBadgeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "N° ${sale.transactionNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textDecoration = if (sale.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Text(
                            text = CurrencyFormatter.formatDateTime(sale.timestamp),
                            fontSize = 11.sp,
                            color = KonabTextMuted
                        )
                    }
                }

                // Status Badge
                Surface(
                    color = statusBgColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusBadgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Customer, Cashier & Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = KonabTextSecondary)
                    Text(
                        text = sale.customerName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Payment Method Badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when (sale.paymentMethod) {
                                "Espèces" -> Icons.Default.AccountBalanceWallet
                                "Carte bancaire" -> Icons.Default.CreditCard
                                else -> Icons.Default.PhoneIphone
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sale.paymentMethod,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Total Amount Row & Expand button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = if (items.isNotEmpty()) "${items.size} article(s)" else "Détails",
                        fontSize = 12.sp,
                        color = KonabGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = KonabGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = CurrencyFormatter.formatFCFA(sale.totalAmount),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = if (sale.isCancelled) KonabRed else KonabGreenDark,
                    textDecoration = if (sale.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            // Expandable Items breakdown
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Détail des articles vendus :", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    if (items.isEmpty()) {
                        Text("Aucun article répertorié.", fontSize = 11.sp, color = KonabTextMuted)
                    } else {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.productName}",
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyFormatter.formatFCFA(item.totalPrice),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (sale.discountAmount > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Remise déduite :", fontSize = 11.sp, color = KonabRed)
                            Text("-${CurrencyFormatter.formatFCFA(sale.discountAmount)}", fontSize = 11.sp, color = KonabRed, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (sale.notes.isNotBlank()) {
                        Text(
                            text = "Note : ${sale.notes}",
                            fontSize = 11.sp,
                            color = KonabTextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Cancellation Audit Notice
            if (sale.isCancelled) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = KonabRedContainer),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = KonabRed, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Vente annulée ${sale.cancelledAt?.let { "le " + CurrencyFormatter.formatDateTime(it) } ?: ""}",
                                fontWeight = FontWeight.Bold,
                                color = KonabRed,
                                fontSize = 11.sp
                            )
                        }
                        if (!sale.cancellationReason.isNullOrBlank()) {
                            Text(
                                text = "Motif : ${sale.cancellationReason}",
                                fontSize = 11.sp,
                                color = KonabRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (!sale.cancelledBy.isNullOrBlank()) {
                            Text(
                                text = "Responsable : ${sale.cancelledBy} • Stocks réintégrés",
                                fontSize = 10.sp,
                                color = KonabRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = KonabBorder)
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons Row: View Receipt, Bluetooth Print, Cancel Sale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onViewReceipt,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_view_receipt_${sale.id}")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = KonabGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reçu", fontSize = 12.sp, color = KonabGreen, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onPrintBluetooth,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_print_bluetooth_${sale.id}")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp), tint = KonabTextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Imprimer", fontSize = 12.sp, color = KonabTextSecondary)
                    }
                }

                // Cancel Button (only for active sales)
                if (!sale.isCancelled) {
                    OutlinedButton(
                        onClick = onRequestCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabRed),
                        border = BorderStroke(1.dp, KonabRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_cancel_sale_${sale.id}")
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp), tint = KonabRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Annuler", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
