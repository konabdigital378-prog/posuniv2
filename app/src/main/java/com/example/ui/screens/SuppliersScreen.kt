package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.PurchaseEntity
import com.example.data.local.entity.SupplierEntity
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
            TopAppBar(
                title = { Text("Fournisseurs & Achats", fontWeight = FontWeight.Bold) },
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
                    if (selectedTab == 0) showAddSupplierDialog = true
                    else showRecordPurchaseDialog = true
                },
                containerColor = KonabGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_supplier_or_purchase")
            ) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.PersonAdd else Icons.Default.AddShoppingCart,
                    contentDescription = "Ajouter"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
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

            if (selectedTab == 0) {
                // Suppliers Tab
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(suppliers) { supplier ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
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
            } else {
                // Purchases Tab
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(purchases) { p ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
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
                                    color = KonabTextMuted
                                )
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
