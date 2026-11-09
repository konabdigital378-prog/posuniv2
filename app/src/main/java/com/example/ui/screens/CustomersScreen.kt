package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomerEntity
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    onSaveCustomer: (CustomerEntity) -> Unit,
    onPayDebt: (CustomerEntity, amount: Long, method: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetails by remember { mutableStateOf<CustomerEntity?>(null) }
    var showDebtPaymentDialog by remember { mutableStateOf(false) }

    val totalDebtReceivable = remember(customers) {
        customers.sumOf { it.debtAmount }
    }

    val filteredCustomers = remember(customers, searchQuery) {
        customers.filter { c ->
            searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clients", fontWeight = FontWeight.Bold) },
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
                onClick = { showAddDialog = true },
                containerColor = KonabGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_add_customer")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau client")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un client...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("input_search_customer")
            )

            // Debt Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (totalDebtReceivable > 0) KonabRedContainer else KonabGreenLight),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (totalDebtReceivable > 0) KonabRed.copy(alpha = 0.2f) else KonabGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = null,
                                tint = if (totalDebtReceivable > 0) KonabRed else KonabGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Total des crédits en cours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = CurrencyFormatter.formatFCFA(totalDebtReceivable),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (totalDebtReceivable > 0) KonabRed else KonabGreenDark
                            )
                        }
                    }

                    Text(
                        text = "${customers.count { it.debtAmount > 0 }} client(s) débiteur(s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Customer List
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCustomers) { customer ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCustomerForDetails = customer }
                            .testTag("customer_card_${customer.name}")
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
                                // Initials circle
                                val initials = customer.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(KonabGoldContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials.ifBlank { "C" },
                                        fontWeight = FontWeight.Bold,
                                        color = KonabGoldDark,
                                        fontSize = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = customer.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = customer.phone,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (customer.debtAmount > 0) {
                                        Text(
                                            text = "Dette : ${CurrencyFormatter.formatFCFA(customer.debtAmount)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KonabRed
                                        )
                                    }
                                }
                            }

                            // Customer Status Badge
                            Surface(
                                color = when (customer.status) {
                                    "Client fidèle" -> KonabGreenLight
                                    "Régulier" -> KonabGoldContainer
                                    "Débiteur" -> KonabRedContainer
                                    else -> Color(0xFFE0F2FE)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (customer.debtAmount > 0) "Débiteur" else customer.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        customer.debtAmount > 0 -> KonabRed
                                        customer.status == "Client fidèle" -> KonabGreenDark
                                        customer.status == "Régulier" -> KonabGoldDark
                                        else -> Color(0xFF0369A1)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Customer Details Dialog
    selectedCustomerForDetails?.let { customer ->
        AlertDialog(
            onDismissRequest = { selectedCustomerForDetails = null },
            title = {
                Text(customer.name, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Téléphone : ${customer.phone}", fontSize = 13.sp)
                    if (customer.address.isNotBlank()) Text("Adresse : ${customer.address}", fontSize = 13.sp)
                    Text("Total dépensé : ${CurrencyFormatter.formatFCFA(customer.totalSpent)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Nombre d'achats : ${customer.totalSalesCount}", fontSize = 13.sp)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (customer.debtAmount > 0) {
                        Surface(
                            color = KonabRedContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Crédit / Dette en cours :", fontSize = 11.sp, color = KonabRed)
                                Text(
                                    CurrencyFormatter.formatFCFA(customer.debtAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KonabRed,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Button(
                            onClick = { showDebtPaymentDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_pay_debt")
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enregistrer un remboursement", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val cleanPhone = customer.phone.replace(" ", "").replace("-", "")
                                val intlPhone = if (cleanPhone.length == 8) "226$cleanPhone" else cleanPhone.trimStart('+')
                                val message = "Bonjour ${customer.name},\nSauf erreur de notre part, votre solde débiteur auprès de notre boutique est de ${CurrencyFormatter.formatFCFA(customer.debtAmount)}.\nMerci de bien vouloir passer régulariser la situation à votre convenance.\nCordialement."
                                try {
                                    val encoded = java.net.URLEncoder.encode(message, "UTF-8")
                                    val uri = Uri.parse("https://wa.me/$intlPhone?text=$encoded")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Relance", message))
                                    android.widget.Toast.makeText(context, "Message de relance copié !", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_whatsapp_reminder")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Relancer sur WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_call_customer")
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Appeler le client", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCustomerForDetails = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Debt Payment Dialog
    if (showDebtPaymentDialog && selectedCustomerForDetails != null) {
        val customer = selectedCustomerForDetails!!
        var payAmountStr by remember { mutableStateOf(customer.debtAmount.toString()) }
        var payMethod by remember { mutableStateOf("Espèces") }

        AlertDialog(
            onDismissRequest = { showDebtPaymentDialog = false },
            title = { Text("Remboursement de dette") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Client : ${customer.name}", fontWeight = FontWeight.Bold)
                    Text("Dette actuelle : ${CurrencyFormatter.formatFCFA(customer.debtAmount)}", color = KonabRed)

                    OutlinedTextField(
                        value = payAmountStr,
                        onValueChange = { payAmountStr = it },
                        label = { Text("Montant remboursé (FCFA) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Mode de règlement :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Espèces", "Orange Money", "Moov Money").forEach { m ->
                            FilterChip(
                                selected = payMethod == m,
                                onClick = { payMethod = m },
                                label = { Text(m, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = payAmountStr.toLongOrNull() ?: 0L
                        if (amount > 0) {
                            onPayDebt(customer, amount, payMethod)
                            showDebtPaymentDialog = false
                            selectedCustomerForDetails = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Valider le reçu de remboursement")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDebtPaymentDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Add Customer Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("+226 ") }
        var address by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nouveau client") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom complet du client *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_customer_name")
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
                        label = { Text("Adresse / Quartier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            onSaveCustomer(
                                CustomerEntity(
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    status = "Nouveau"
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier.testTag("btn_save_customer")
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
