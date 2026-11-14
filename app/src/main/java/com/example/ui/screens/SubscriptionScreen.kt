package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.components.KonabCardShape
import com.example.ui.components.KonabLivePulseDot
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabCardBorder
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    shopProfile: ShopProfileEntity?,
    onActivate: (code: String, tier: String) -> Boolean,
    onActivateWithSasPay: (tier: String, durationDays: Int, paymentId: String, reference: String, amountFcfa: Long, channel: String) -> Boolean = { _, _, _, _, _, _ -> false },
    onNavigateToAgentPortal: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTier by remember { mutableStateOf(shopProfile?.subscriptionTier ?: "Premium") }
    var activationCodeInput by remember { mutableStateOf("") }
    var showActivationDialog by remember { mutableStateOf(false) }
    var showSasPayDialog by remember { mutableStateOf(false) }

    val businessType = shopProfile?.businessType ?: "Boutique"
    val sectorPreset = remember(businessType) {
        com.example.util.BusinessTypePresets.getPreset(businessType)
    }
    val pricingProfile = remember(businessType) {
        com.example.util.SubscriptionPricingHelper.getPricingProfile(businessType)
    }
    val isReducedTariff = pricingProfile.isReducedTariff

    val tiers = remember(pricingProfile) {
        listOf(
            Triple("Gratuit", "0 FCFA", "10 produits max, fonctions de base caisse (essai 30 jours)"),
            Triple(
                "Standard",
                com.example.util.SubscriptionPricingHelper.formatCompactPrice(pricingProfile.standardMonthly, "/ mois"),
                "Produits illimités, reçus WhatsApp, gestion caisse"
            ),
            Triple(
                "Professionnel",
                "${com.example.util.SubscriptionPricingHelper.formatCompactPrice(pricingProfile.proMonthly, "/ mois")} • ou ${com.example.util.SubscriptionPricingHelper.formatCompactPrice(pricingProfile.proAnnual, "/ an")}",
                "Rapports avancés, dettes clients, gestion stocks et alertes"
            ),
            Triple(
                "Entreprise",
                com.example.util.SubscriptionPricingHelper.formatCompactPrice(pricingProfile.entrepriseMonthly, "/ mois"),
                "Multi-utilisateurs, sauvegarde automatique, support prioritaire 24/7"
            ),
            Triple(
                "Premium",
                com.example.util.SubscriptionPricingHelper.formatCompactPrice(pricingProfile.premiumLifetime, "(À vie)"),
                "Licence permanente 10 ans, toutes options Cloud & multi-postes"
            )
        )
    }

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Abonnements Konab",
                subtitle = "Tarifs adaptés à votre secteur d'activité",
                onBack = onBack,
                actions = {
                    if (onNavigateToAgentPortal != null) {
                        IconButton(onClick = onNavigateToAgentPortal) {
                            Icon(Icons.Default.Badge, contentDescription = "Portail Agent", tint = KonabOrange)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val widthClass = LocalKonabWidthClass.current
        val isWide = widthClass != KonabWidthClass.COMPACT
        val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

        ResponsiveMaxWidthBox(
            maxWidth = 960.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isWide) 24.dp else 16.dp, vertical = 16.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Activity-Adjusted Tariff Card
                KonabStaggeredReveal(index = 0) {
                    val accent = Color(pricingProfile.category.accentColorHex)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
                        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().testTag("card_activity_tariff_info")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sectorPreset.emoji} ${sectorPreset.title}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = accent,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = pricingProfile.category.badgeLabel,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = pricingProfile.category.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isReducedTariff) {
                                    "Votre activité bénéficie du tarif social allégé (dès 1 500 FCFA/mois), distinct des boutiques et grandes entreprises."
                                } else {
                                    pricingProfile.category.description + " (Note : les vendeurs ambulants et courtiers disposent d'un tarif réduit dédié.)"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Official Konab Deposit Notice
                KonabStaggeredReveal(index = 0) {
                    Card(
                        shape = KonabCardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = konabCardBorder(KonabOrange.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .konabHeroShimmer(maxAlpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(KonabOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Numéro officiel de dépôt Konab :", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("65413799", fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = KonabOrange)
                                Text("Pour toute activation ou renouvellement de licence.", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (onNavigateToAgentPortal != null) {
                    KonabStaggeredReveal(index = 1) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF06245E)),
                            border = BorderStroke(1.5.dp, Color(0xFFFFA43A).copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .konabPressScale()
                                .clickable { onNavigateToAgentPortal() }
                                .testTag("card_agent_portal_shortcut")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF06245E),
                                                Color(0xFF073B9A)
                                            )
                                        )
                                    )
                                    .konabHeroShimmer(maxAlpha = 0.08f)
                                    .padding(15.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(42.dp),
                                            shape = CircleShape,
                                            color = Color(0xFFFF7900),
                                            border = BorderStroke(2.dp, Color(0xFFFFA43A))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                KonabLivePulseDot(color = Color(0xFF34D399), size = 7.dp)
                                                Text("Espace Agent Déployeur", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                                                Surface(
                                                    color = Color(0xFFFF7900),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "OFFICIEL",
                                                        color = Color.White,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "Portail réservé aux techniciens et commerciaux agréés",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFFE2E8F0),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFFFFA43A), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Security Policy Card
                KonabStaggeredReveal(index = 2) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = KonabRedContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = KonabRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Règle de sécurité : Ne jamais considérer une capture d'écran comme une preuve automatique de paiement validé.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabRed
                            )
                        }
                    }
                }

                // Tiers List (2 columns on wide screens, stacked on phones)
                Text("Formules disponibles :", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                if (isWide) {
                    tiers.chunked(2).forEachIndexed { rowIdx, pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEachIndexed { colIdx, (name, price, desc) ->
                                val isCurrent = (shopProfile?.subscriptionTier ?: "Premium") == name
                                val isSelected = selectedTier == name
                                val cardBg by animateColorAsState(
                                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    animationSpec = tween(220),
                                    label = "tier_bg_$name"
                                )
                                val borderColor by animateColorAsState(
                                    targetValue = if (isSelected) KonabBlue else outlineColor,
                                    animationSpec = tween(220),
                                    label = "tier_border_$name"
                                )
                                KonabStaggeredReveal(
                                    index = rowIdx * 2 + colIdx,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Card(
                                        shape = KonabCardShape,
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = borderColor,
                                                shape = KonabCardShape
                                            )
                                            .konabPressScale()
                                            .clickable { selectedTier = name }
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    name,
                                                    fontFamily = OutfitFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isCurrent) {
                                                    Surface(color = KonabBlue, shape = RoundedCornerShape(6.dp)) {
                                                        Text("Actif", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(price, fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                desc,
                                                fontSize = 12.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    tiers.forEachIndexed { idx, (name, price, desc) ->
                        val isCurrent = (shopProfile?.subscriptionTier ?: "Premium") == name
                        val isSelected = selectedTier == name
                        val cardBg by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            animationSpec = tween(220),
                            label = "tier_bg_$name"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) KonabBlue else outlineColor,
                            animationSpec = tween(220),
                            label = "tier_border_$name"
                        )

                        KonabStaggeredReveal(index = idx) {
                            Card(
                                shape = KonabCardShape,
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = KonabCardShape
                                    )
                                    .konabPressScale()
                                    .clickable { selectedTier = name }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )

                                        if (isCurrent) {
                                            Surface(
                                                color = KonabBlue,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("Actif sur cet appareil", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(price, fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        desc,
                                        fontSize = 12.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Renewal via SasPay & Super Admin Activation Section
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, outlineColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Réabonnement en ligne (SasPay)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Réglez votre formule ($selectedTier) en temps réel par Mobile Money ou Carte bancaire via SasPay, ou utilisez une clé fournie après dépôt au 65413799.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showSasPayDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .konabPressScale()
                                .testTag("btn_open_saspay_renewal")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Se réabonner via SasPay", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showActivationDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KonabGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .konabPressScale()
                                .testTag("btn_super_admin_activate")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = KonabGreenDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Activer avec une clé de licence", fontWeight = FontWeight.Bold, color = KonabGreenDark)
                        }
                    }
                }
            }
        }
    }

    // License Activation Dialog
    if (showActivationDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = { Text("Validation Super Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formule à activer : $selectedTier", fontWeight = FontWeight.Bold)
                    Text("Saisissez la clé d'activation fournie par Konab après contrôle au 65413799 :", fontSize = 12.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = { Text("Ex: KONAB-2026-PRO ou 65413799") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onActivate(code, selectedTier)
                        if (success) {
                            Toast.makeText(context, "Formule $selectedTier activée avec succès !", Toast.LENGTH_LONG).show()
                            showActivationDialog = false
                        } else {
                            Toast.makeText(context, "Clé de licence invalide", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Activer la formule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    if (showSasPayDialog) {
        com.example.ui.components.SasPayPaymentDialog(
            shopProfile = shopProfile,
            initialTier = selectedTier,
            isPosCollectionMode = false,
            onDismiss = { showSasPayDialog = false },
            onPaymentVerified = { tier, durationDays, paymentId, reference, amountFcfa, channel ->
                val ok = onActivateWithSasPay(tier, durationDays, paymentId, reference, amountFcfa, channel)
                if (ok) {
                    selectedTier = tier
                    Toast.makeText(context, "Réabonnement SasPay activé : $tier ($durationDays j) !", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
