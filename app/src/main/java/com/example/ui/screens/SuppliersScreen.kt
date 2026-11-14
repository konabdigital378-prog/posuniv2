package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.PurchaseEntity
import com.example.data.local.entity.SupplierEntity
import com.example.ui.components.KonabCardShape
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabCardBorder
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ProductThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    suppliers: List<SupplierEntity>,
    purchases: List<PurchaseEntity>,
    products: List<ProductEntity>,
    onSaveSupplier: (SupplierEntity) -> Unit,
    onRecordPurchase: (supplier: SupplierEntity, product: ProductEntity, quantity: Int, unitCost: Long, isPaidFromCash: Boolean, notes: String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Fournisseurs, 1: Achats
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showRecordPurchaseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Fournisseurs & Achats",
                subtitle = "${suppliers.size} fournisseur(s) • ${purchases.size} réapprovisionnement(s)",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddSupplierDialog = true
                    else showRecordPurchaseDialog = true
                },
                containerColor = KonabBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .konabPressScale()
                    .testTag("fab_add_supplier_or_purchase")
            ) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.PersonAdd else Icons.Default.AddShoppingCart,
                    contentDescription = "Ajouter"
                )
            }
        }
    ) { paddingValues ->
        val widthClass = LocalKonabWidthClass.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            ResponsiveMaxWidthBox(
                maxWidth = 1120.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = KonabBlue
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Fournisseurs (${suppliers.size})", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Achats & Réappro (${purchases.size})", fontWeight = FontWeight.Bold) }
                        )
                    }

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                        label = "suppliers_tab_transition"
                    ) { tab ->
                        val gridCols = if (widthClass == KonabWidthClass.COMPACT) {
                            GridCells.Fixed(1)
                        } else {
                            GridCells.Adaptive(minSize = 310.dp)
                        }

                        if (tab == 0) {
                            if (suppliers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KonabEmptyState(
                                        icon = Icons.Default.LocalShipping,
                                        title = "Aucun fournisseur enregistré",
                                        subtitle = "Ajoutez vos grossistes pour suivre vos réapprovisionnements et dettes fournisseurs.",
                                        accentColor = KonabGreen,
                                        actionLabel = "Ajouter un fournisseur",
                                        onAction = { showAddSupplierDialog = true }
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = gridCols,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(suppliers, key = { _, s -> s.id }) { idx, supplier ->
                                        KonabStaggeredReveal(index = idx) {
                                            Card(
                                                shape = KonabCardShape,
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = konabCardBorder(),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .konabPressScale(pressedScale = 0.985f)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(42.dp)
                                                                .clip(CircleShape)
                                                                .background(KonabGreenLight),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = KonabGreenDark)
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column {
                                                            Text(supplier.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            Text(supplier.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            if (supplier.address.isNotBlank()) {
                                                                Text(supplier.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                    }

                                                    if (supplier.debtAmount > 0) {
                                                        Surface(
                                                            color = KonabRedContainer,
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                "Dette : ${CurrencyFormatter.formatFCFA(supplier.debtAmount)}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = KonabRed,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (purchases.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KonabEmptyState(
                                        icon = Icons.Default.AddShoppingCart,
                                        title = "Aucun achat enregistré",
                                        subtitle = "Enregistrez vos entrées de marchandises pour mettre à jour votre stock automatiquement.",
                                        accentColor = KonabGreen,
                                        actionLabel = if (suppliers.isNotEmpty() && products.isNotEmpty()) "Nouvel achat" else null,
                                        onAction = { showRecordPurchaseDialog = true }
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = gridCols,
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 90.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(purchases, key = { _, p -> p.id }) { idx, p ->
                                        KonabStaggeredReveal(index = idx) {
                                            Card(
                                                shape = KonabCardShape,
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = konabCardBorder(),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .konabPressScale(pressedScale = 0.985f)
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(p.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text(
                                                            CurrencyFormatter.formatFCFA(p.totalCost),
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = KonabRed
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        "Qté reçue : +${p.quantity} • P.U Achat : ${CurrencyFormatter.formatFCFA(p.unitCost)}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "Fournisseur : ${p.supplierName} • ${CurrencyFormatter.formatDateTime(p.timestamp)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
        }
    }

    // Add Supplier Dialog
    if (showAddSupplierDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("+226 ") }
        var address by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = { Text("Nouveau fournisseur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom du fournisseur / grossiste *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Téléphone *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Adresse / Marché") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            onSaveSupplier(
                                SupplierEntity(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    address = address.trim()
                                )
                            )
                            showAddSupplierDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Record Purchase Dialog
    if (showRecordPurchaseDialog) {
        var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
        var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
        var quantityStr by remember { mutableStateOf("10") }
        var unitCostStr by remember { mutableStateOf(selectedProduct?.buyPrice?.toString() ?: "1000") }
        var isPaidFromCash by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showRecordPurchaseDialog = false },
            title = { Text("Entrée de stock / Achat") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Fournisseur :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    suppliers.take(3).forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSupplier = s }
                        ) {
                            RadioButton(selected = selectedSupplier?.id == s.id, onClick = { selectedSupplier = s })
                            Text(s.name, fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider()

                    Text("Produit réapprovisionné :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    products.take(6).forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProduct = p
                                    unitCostStr = p.buyPrice.toString()
                                }
                                .padding(vertical = 3.dp)
                        ) {
                            RadioButton(selected = selectedProduct?.id == p.id, onClick = {
                                selectedProduct = p
                                unitCostStr = p.buyPrice.toString()
                            })
                            ProductThumbnail(
                                product = p,
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${p.name} (Stock: ${p.stockQuantity} ${p.unit})", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it },
                        label = { Text("Quantité ajoutée au stock *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = unitCostStr,
                        onValueChange = { unitCostStr = it },
                        label = { Text("Coût unitaire d'achat FCFA *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPaidFromCash, onCheckedChange = { isPaidFromCash = it })
                        Text("Déduire de la caisse (Payé comptant)", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sup = selectedSupplier
                        val prod = selectedProduct
                        val qty = quantityStr.toIntOrNull() ?: 0
                        val cost = unitCostStr.toLongOrNull() ?: 0L
                        if (sup != null && prod != null && qty > 0 && cost > 0) {
                            onRecordPurchase(sup, prod, qty, cost, isPaidFromCash, "Réapprovisionnement stock")
                            showRecordPurchaseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Valider l'achat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordPurchaseDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
