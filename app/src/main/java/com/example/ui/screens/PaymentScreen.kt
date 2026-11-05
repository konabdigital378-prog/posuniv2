package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ReceiptPrinterHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    shopProfile: ShopProfileEntity?,
    totalAmount: Long,
    selectedCustomer: CustomerEntity?,
    lastCompletedSale: Pair<SaleEntity, List<SaleItemEntity>>?,
    onConfirmPayment: (paymentMethod: String, isCredit: Boolean, notes: String) -> Unit,
    onViewReceipt: () -> Unit,
    onNewSale: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var paymentMethod by remember { mutableStateOf("Espèces") }
    var amountTenderedInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var isSaleCompleted by remember { mutableStateOf(false) }

    val amountTendered = amountTenderedInput.toLongOrNull() ?: totalAmount
    val changeToReturn = maxOf(0L, amountTendered - totalAmount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSaleCompleted) "Reçu & Confirmation" else "Paiement", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isSaleCompleted) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isSaleCompleted && lastCompletedSale != null) {
                // Success View
                val (sale, items) = lastCompletedSale
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(KonabGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Succès",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Paiement réussi !",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = KonabGreen
                        )
                    )

                    Text(
                        text = "Le reçu N° ${sale.transactionNumber} a été généré",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = CurrencyFormatter.formatFCFA(sale.totalAmount),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = KonabTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    Button(
                        onClick = {
                            if (shopProfile != null) {
                                ReceiptPrinterHelper.shareReceiptToWhatsApp(
                                    context = context,
                                    shop = shopProfile,
                                    sale = sale,
                                    items = items
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_share_whatsapp")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Partager WhatsApp", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (shopProfile != null) {
                                val file = ReceiptPrinterHelper.generateReceiptPdf(
                                    context = context,
                                    shop = shopProfile,
                                    sale = sale,
                                    items = items
                                )
                                Toast.makeText(context, "Reçu PDF exporté: ${file.name}", Toast.LENGTH_SHORT).show()
                                onViewReceipt()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_print_receipt")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Imprimer / Aperçu reçu", fontWeight = FontWeight.Bold, color = KonabGreen, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onNewSale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_new_sale")
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nouvelle vente", fontWeight = FontWeight.Bold, color = KonabGreen, fontSize = 15.sp)
                    }
                }
            } else {
                // Payment Form View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Total Banner
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Total à payer",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatFCFA(totalAmount),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = KonabGreen
                                )
                            )
                            if (selectedCustomer != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Client : ${selectedCustomer.name}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Moyen de paiement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Payment Methods Grid
                    val methods = listOf(
                        Pair("Espèces", Icons.Default.Payments),
                        Pair("Orange Money", Icons.Default.PhoneAndroid),
                        Pair("Moov Money", Icons.Default.SendToMobile),
                        Pair("Wave", Icons.Default.Waves),
                        Pair("Carte bancaire", Icons.Default.CreditCard),
                        Pair("À crédit", Icons.Default.Receipt)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in methods.indices step 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val (m1, icon1) = methods[i]
                                PaymentMethodCard(
                                    name = m1,
                                    icon = icon1,
                                    isSelected = paymentMethod == m1,
                                    modifier = Modifier.weight(1f),
                                    onSelect = { paymentMethod = m1 }
                                )

                                if (i + 1 < methods.size) {
                                    val (m2, icon2) = methods[i + 1]
                                    PaymentMethodCard(
                                        name = m2,
                                        icon = icon2,
                                        isSelected = paymentMethod == m2,
                                        modifier = Modifier.weight(1f),
                                        onSelect = { paymentMethod = m2 }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Cash Change Calculator
                    if (paymentMethod == "Espèces") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = KonabGreenLight)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Calcul de la monnaie (Espèces)",
                                    fontWeight = FontWeight.Bold,
                                    color = KonabGreenDark,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick Banknote Denomination Chips
                                Text(
                                    text = "Billets courants :",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val quickAmounts = listOf(
                                        totalAmount to "Exact",
                                        2000L to "2 000",
                                        5000L to "5 000",
                                        10000L to "10 000"
                                    )
                                    quickAmounts.forEach { (amt, label) ->
                                        Surface(
                                            onClick = { amountTenderedInput = amt.toString() },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (amountTenderedInput == amt.toString()) KonabGreen else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, KonabGreen.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (amountTenderedInput == amt.toString()) Color.White else KonabGreenDark
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = amountTenderedInput,
                                    onValueChange = { amountTenderedInput = it },
                                    label = { Text("Montant remis par le client") },
                                    placeholder = { Text("${totalAmount} FCFA") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_amount_tendered")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Monnaie à rendre :", fontWeight = FontWeight.Bold, color = KonabGreenDark)
                                    Text(
                                        CurrencyFormatter.formatFCFA(changeToReturn),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (changeToReturn > 0) KonabGreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp
                                    )
                                }

                                if (changeToReturn > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Decompose change into bills and coins
                                    val breakdown = remember(changeToReturn) {
                                        var rem = changeToReturn
                                        val list = mutableListOf<String>()
                                        val denominations = listOf(10000L, 5000L, 2000L, 1000L, 500L, 250L, 200L, 100L, 50L, 25L)
                                        for (d in denominations) {
                                            val count = rem / d
                                            if (count > 0) {
                                                list.add("${count}x ${d}")
                                                rem %= d
                                            }
                                        }
                                        list.joinToString(", ")
                                    }
                                    Surface(
                                        color = KonabGreenLight,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Détail coupures : $breakdown",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KonabGreenDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Mobile Money Official Notice
                    if (paymentMethod in listOf("Orange Money", "Moov Money", "Wave")) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = KonabGoldContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = KonabGoldDark)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Paiement direct $paymentMethod sans simulation. Confirmez après notification USSD / SMS client.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (paymentMethod == "À crédit" && selectedCustomer == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Attention : Vous devez sélectionner un client dans le panier pour enregistrer une vente à crédit.",
                            color = KonabRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val isCredit = paymentMethod == "À crédit"
                            onConfirmPayment(paymentMethod, isCredit, notesInput)
                            isSaleCompleted = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_confirm_payment")
                    ) {
                        Text("Valider le paiement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) KonabGreenLight else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .height(76.dp)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) KonabGreen else KonabBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() }
            .testTag("pay_method_$name")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) KonabGreen else KonabGoldContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (isSelected) Color.White else KonabGoldDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (isSelected) KonabGreenDark else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
