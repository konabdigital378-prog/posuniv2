package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.saspay.SasPayApiClient
import com.example.data.remote.saspay.SasPayCountryOption
import com.example.data.remote.saspay.SasPayPaymentResult
import com.example.data.remote.saspay.SasPayTransactionRecord
import com.example.data.remote.saspay.SasPayTransactionStore
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.SubscriptionPricingHelper
import kotlinx.coroutines.launch

/**
 * Dialogue complet de paiement & réabonnement via SasPay (https://api.saspay.me/api/v1) :
 * - Mode REABONNEMENT : calcul automatique du tarif selon l'activité (Vendeur ambulant/Courtier vs Boutique)
 *   et renouvellement de la licence après vérification serveur SasPay.
 * - Mode ENCAISSEMENT_POS : encaissement client Mobile Money / Carte en caisse.
 */
@Composable
fun SasPayPaymentDialog(
    shopProfile: ShopProfileEntity?,
    initialTier: String = "Professionnel",
    initialDurationDays: Int = 365,
    isPosCollectionMode: Boolean = false,
    fixedPosAmountFcfa: Long = 0L,
    customerNameOverride: String = "",
    customerPhoneOverride: String = "",
    onDismiss: () -> Unit,
    onPaymentVerified: (
        tier: String,
        durationDays: Int,
        paymentId: String,
        reference: String,
        amountFcfa: Long,
        channel: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { SasPayApiClient() }

    val businessType = shopProfile?.businessType ?: "Boutique"
    val pricingProfile = remember(businessType) {
        SubscriptionPricingHelper.getPricingProfile(businessType)
    }

    var selectedTier by remember {
        mutableStateOf(
            if (initialTier.equals("Gratuit", ignoreCase = true) || initialTier.equals("Starter", ignoreCase = true)) {
                "Professionnel"
            } else initialTier
        )
    }
    var selectedDurationDays by remember { mutableIntStateOf(initialDurationDays.coerceAtLeast(30)) }

    // 0 = Softpay (Direct Mobile Money), 1 = Checkout Session (Carte / Wave / Orange)
    var paymentMode by remember { mutableIntStateOf(0) }

    var countries by remember { mutableStateOf(SasPayApiClient.defaultWestAfricaCountries()) }
    var selectedCountry by remember { mutableStateOf(countries.first()) }
    var selectedNetwork by remember { mutableStateOf(countries.first().networks.first()) }

    var phoneInput by remember {
        mutableStateOf(customerPhoneOverride.ifBlank { shopProfile?.phone ?: "" })
    }
    var emailInput by remember { mutableStateOf("merchant@konabpos.app") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activePaymentResult by remember { mutableStateOf<SasPayPaymentResult?>(null) }
    var recentTxList by remember { mutableStateOf(SasPayTransactionStore.getTransactions(context)) }

    LaunchedEffect(Unit) {
        val loaded = apiClient.fetchCountries().getOrNull()
        if (!loaded.isNullOrEmpty()) {
            countries = loaded
            val preferred = loaded.firstOrNull { it.code == "BF" } ?: loaded.first()
            selectedCountry = preferred
            if (preferred.networks.isNotEmpty()) {
                selectedNetwork = preferred.networks.first()
            }
        }
    }

    val computedAmountFcfa = remember(isPosCollectionMode, fixedPosAmountFcfa, businessType, selectedTier, selectedDurationDays) {
        if (isPosCollectionMode) {
            fixedPosAmountFcfa
        } else {
            val effectiveDays = if (selectedTier.equals("Premium", ignoreCase = true)) 3650 else selectedDurationDays
            SubscriptionPricingHelper.getPriceForDuration(businessType, selectedTier, effectiveDays)
        }
    }

    val tierOptions = listOf("Standard", "Professionnel", "Entreprise", "Premium")
    val durationOptions = listOf(
        30 to "1 Mois",
        90 to "3 Mois",
        180 to "6 Mois",
        365 to "1 An",
        3650 to "À vie"
    )

    fun openCheckoutUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir le lien SasPay.", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerVerification(paymentId: String, tierToApply: String, daysToApply: Int, channelLabel: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            val res = apiClient.verifyPayment(paymentId)
            isLoading = false
            res.onSuccess { verified ->
                activePaymentResult = verified
                SasPayTransactionStore.updateStatus(context, verified.paymentId, verified.status, verified.isPaid)
                recentTxList = SasPayTransactionStore.getTransactions(context)
                if (verified.isPaid) {
                    Toast.makeText(context, "Paiement SasPay confirmé (${verified.reference}) !", Toast.LENGTH_LONG).show()
                    onPaymentVerified(
                        tierToApply,
                        daysToApply,
                        verified.paymentId,
                        verified.reference,
                        verified.amountFcfa.takeIf { it > 0L } ?: computedAmountFcfa,
                        channelLabel
                    )
                    onDismiss()
                } else {
                    errorMessage = "Statut SasPay actuel : ${verified.status}. Finalisez sur votre téléphone ou la page SasPay puis vérifiez à nouveau."
                }
            }.onFailure { err ->
                errorMessage = err.message ?: "Erreur lors de la vérification SasPay."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KonabGreen,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text(
                        text = if (isPosCollectionMode) "Encaissement SasPay" else "Réabonnement SasPay",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Mobile Money & Carte bancaire en ligne",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!apiClient.isConfigured) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KonabRedContainer,
                        border = BorderStroke(1.dp, KonabRed.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = KonabRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Configurez SASPAY_SECRET_KEY dans le panneau Secrets d'AI Studio pour activer les paiements SasPay.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KonabRed
                            )
                        }
                    }
                }

                // Montant & Palier d'activité
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(pricingProfile.category.accentColorHex).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(pricingProfile.category.accentColorHex).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!isPosCollectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pricingProfile.businessType} • ${pricingProfile.category.title}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(pricingProfile.category.accentColorHex)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(pricingProfile.category.accentColorHex)
                                ) {
                                    Text(
                                        text = pricingProfile.category.badgeLabel,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Encaissement vente client",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabGreenDark
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatFCFA(computedAmountFcfa),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = OutfitFontFamily,
                            color = KonabGreen
                        )
                    }
                }

                // Choix de formule & durée pour réabonnement
                if (!isPosCollectionMode) {
                    Text("Formule d'abonnement :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tierOptions) { tier ->
                            val isSel = selectedTier.equals(tier, ignoreCase = true)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedTier = tier
                                    if (tier.equals("Premium", ignoreCase = true)) {
                                        selectedDurationDays = 3650
                                    } else if (selectedDurationDays >= 3650) {
                                        selectedDurationDays = 365
                                    }
                                },
                                label = { Text(tier, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    if (!selectedTier.equals("Premium", ignoreCase = true)) {
                        Text("Durée du réabonnement :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(durationOptions.filter { it.first < 3650 }) { (days, label) ->
                                val isSel = selectedDurationDays == days
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedDurationDays = days },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KonabBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Mode de paiement SasPay (Softpay Direct vs Hosted Checkout)
                Text("Mode SasPay :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0 to "📱 Mobile Money Direct",
                        1 to "💳 Lien / Carte SasPay"
                    ).forEach { (mode, label) ->
                        val isSel = paymentMode == mode
                        Surface(
                            onClick = { paymentMode = mode },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) KonabGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isSel) KonabGreen else KonabBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_saspay_mode_$mode")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (paymentMode == 0) {
                    Text("Pays :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(countries) { country: SasPayCountryOption ->
                            val isSel = selectedCountry.code == country.code
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedCountry = country
                                    if (country.networks.isNotEmpty()) {
                                        selectedNetwork = country.networks.first()
                                    }
                                },
                                label = { Text("${country.name} (${country.dialCode})", fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Réseau Mobile Money :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(selectedCountry.networks) { net ->
                            val isSel = selectedNetwork.code == net.code
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedNetwork = net },
                                label = { Text(net.name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Numéro Mobile Money (${selectedCountry.dialCode}) *") },
                        placeholder = { Text("Ex: 65413799") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saspay_phone")
                    )
                } else {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("E-mail reçu SasPay") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_saspay_email")
                    )
                }

                // Résultat / Vérification d'un paiement initié
                activePaymentResult?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (res.isPaid) KonabGreenLight else Color(0xFFE0F2FE),
                        border = BorderStroke(1.dp, if (res.isPaid) KonabGreen else KonabBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Transaction SasPay : ${res.reference.ifBlank { res.paymentId }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Statut : ${res.status}${if (res.message.isNotBlank()) " • ${res.message}" else ""}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!res.checkoutUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { openCheckoutUrl(res.checkoutUrl) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ouvrir la page de paiement SasPay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            val effectiveDays = if (selectedTier.equals("Premium", ignoreCase = true)) 3650 else selectedDurationDays
                            Button(
                                onClick = {
                                    triggerVerification(
                                        paymentId = res.paymentId.ifBlank { res.reference },
                                        tierToApply = selectedTier,
                                        daysToApply = effectiveDays,
                                        channelLabel = if (paymentMode == 0) "SasPay (${selectedNetwork.name})" else "SasPay Checkout"
                                    )
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("btn_saspay_verify")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vérifier le paiement SasPay", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KonabRedContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = KonabRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Historique récent des paiements en attente (permet de re-vérifier)
                val pendingList = recentTxList.filter { !SasPayApiClient.isStatusPaid(it.status) }.take(2)
                if (pendingList.isNotEmpty() && activePaymentResult == null) {
                    HorizontalDivider()
                    Text(
                        text = "Paiements SasPay en attente :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pendingList.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${tx.tier} • ${CurrencyFormatter.formatFCFA(tx.amountFcfa)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Réf: ${tx.reference} (${tx.status})",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    triggerVerification(
                                        paymentId = tx.paymentId.ifBlank { tx.reference },
                                        tierToApply = tx.tier,
                                        daysToApply = tx.durationDays,
                                        channelLabel = tx.channel
                                    )
                                }
                            ) {
                                Text("Vérifier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val effectiveDays = if (selectedTier.equals("Premium", ignoreCase = true)) 3650 else selectedDurationDays
                    val customerName = customerNameOverride.ifBlank { shopProfile?.name ?: "Commerçant Konab" }
                    val desc = if (isPosCollectionMode) {
                        "Encaissement POS ${shopProfile?.name ?: "Konab"} (${CurrencyFormatter.formatFCFA(computedAmountFcfa)})"
                    } else {
                        "Réabonnement Konab POS - $selectedTier ($effectiveDays j) - ${shopProfile?.name ?: "Boutique"}"
                    }

                    if (paymentMode == 0 && phoneInput.isBlank()) {
                        errorMessage = "Veuillez saisir le numéro Mobile Money du client."
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val callResult = if (paymentMode == 0) {
                            apiClient.initiateSoftpay(
                                amountFcfa = computedAmountFcfa,
                                countryCode = selectedCountry.code,
                                dialCode = selectedCountry.dialCode,
                                networkCode = selectedNetwork.code,
                                customerPhone = phoneInput,
                                customerName = customerName,
                                customerEmail = emailInput,
                                description = desc
                            )
                        } else {
                            apiClient.createCheckoutSession(
                                amountFcfa = computedAmountFcfa,
                                customerName = customerName,
                                customerEmail = emailInput,
                                description = desc
                            )
                        }
                        isLoading = false

                        callResult.onSuccess { res ->
                            activePaymentResult = res
                            val channelLabel = if (paymentMode == 0) "SasPay (${selectedNetwork.name})" else "SasPay Checkout"
                            SasPayTransactionStore.saveTransaction(
                                context,
                                SasPayTransactionRecord(
                                    id = res.paymentId.ifBlank { "SASPAY-${System.currentTimeMillis()}" },
                                    paymentId = res.paymentId,
                                    reference = res.reference,
                                    purpose = if (isPosCollectionMode) "ENCAISSEMENT_POS" else "REABONNEMENT",
                                    tier = selectedTier,
                                    durationDays = effectiveDays,
                                    amountFcfa = computedAmountFcfa,
                                    channel = channelLabel,
                                    phone = phoneInput,
                                    checkoutUrl = res.checkoutUrl,
                                    status = res.status,
                                    verifiedAt = if (res.isPaid) System.currentTimeMillis() else null
                                )
                            )
                            recentTxList = SasPayTransactionStore.getTransactions(context)

                            if (res.isPaid) {
                                Toast.makeText(context, "Paiement SasPay validé avec succès !", Toast.LENGTH_LONG).show()
                                onPaymentVerified(
                                    selectedTier,
                                    effectiveDays,
                                    res.paymentId,
                                    res.reference,
                                    computedAmountFcfa,
                                    channelLabel
                                )
                                onDismiss()
                            } else if (!res.checkoutUrl.isNullOrBlank()) {
                                openCheckoutUrl(res.checkoutUrl)
                            }
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Échec de l'initiation du paiement SasPay."
                        }
                    }
                },
                enabled = !isLoading && computedAmountFcfa > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                modifier = Modifier.testTag("btn_saspay_pay")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Traitement...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Payer (${CurrencyFormatter.formatFCFA(computedAmountFcfa)})", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isLoading) onDismiss() }) {
                Text("Fermer")
            }
        }
    )
}
