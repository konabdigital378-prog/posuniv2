package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
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

    val widthClass = LocalKonabWidthClass.current

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = if (isSaleCompleted) "Reçu & Confirmation" else "Paiement",
                subtitle = if (isSaleCompleted) "Encaissement enregistré" else "Finalisation de la vente",
                onBack = if (!isSaleCompleted) onBack else null
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ResponsiveMaxWidthBox(
                maxWidth = 720.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = isSaleCompleted && lastCompletedSale != null,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.96f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "payment_state_transition"
                ) { completed ->
                    if (completed && lastCompletedSale != null) {
                        // Success View
                        val (sale, items) = lastCompletedSale
                        val checkScale = remember { Animatable(0.4f) }
                        LaunchedEffect(sale.id) {
                            checkScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }

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
                                    .size(84.dp)
                                    .graphicsLayer {
                                        scaleX = checkScale.value
                                        scaleY = checkScale.value
                                    }
                                    .clip(CircleShape)
                                    .background(KonabGreen)
                                    .konabHeroShimmer(),
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
                                    fontFamily = OutfitFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    .konabPressScale()
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
                                    .konabPressScale()
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .konabHeroShimmer(shimmerColor = KonabGreen, maxAlpha = 0.06f)
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
                                            fontFamily = OutfitFontFamily,
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

                            // Payment Methods Grid (2 cols on Compact, 3 cols on Medium/Expanded)
                            val methods = listOf(
                                Pair("Espèces", Icons.Default.Payments),
                                Pair("Orange Money", Icons.Default.PhoneAndroid),
                                Pair("Moov Money", Icons.AutoMirrored.Filled.SendToMobile),
                                Pair("Wave", Icons.Default.Waves),
                                Pair("Carte bancaire", Icons.Default.CreditCard),
                                Pair("À crédit", Icons.Default.Receipt)
                            )

                            val methodCols = if (widthClass == KonabWidthClass.COMPACT) 2 else 3
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (i in methods.indices step methodCols) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        for (col in 0 until methodCols) {
                                            val idx = i + col
                                            if (idx < methods.size) {
                                                val (m, icon) = methods[idx]
                                                PaymentMethodCard(
                                                    name = m,
                                                    icon = icon,
                                                    isSelected = paymentMethod == m,
                                                    modifier = Modifier.weight(1f),
                                                    onSelect = { paymentMethod = m }
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
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
                                    text = "Transaction caisse locale ($paymentMethod). Validez la vente après confirmation avec votre client.",
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
                            .konabPressScale()
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
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) KonabGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        animationSpec = tween(180),
        label = "pay_method_border"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) KonabGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "pay_method_bg"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier
            .height(76.dp)
            .konabPressScale(pressedScale = 0.96f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
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
                color = if (isSelected) KonabGreen else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
