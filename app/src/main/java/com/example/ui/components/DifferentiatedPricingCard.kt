package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.PricingCategory
import com.example.util.SubscriptionPricingHelper

/**
 * Panneau comparatif interactif de tarification différenciée pour les Paramètres.
 * Met en évidence les frais réduits pour Marchands Ambulants & Courtiers par rapport aux
 * Artisans/Services de proximité et aux Boutiques établies.
 */
@Composable
fun DifferentiatedPricingComparisonBlock(
    currentBusinessType: String?,
    currentTier: String?,
    onOpenSubscription: () -> Unit,
    onChangeSector: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val activeProfile = remember(currentBusinessType) {
        SubscriptionPricingHelper.getPricingProfile(currentBusinessType)
    }

    val tierLevels = remember {
        listOf(
            Triple(
                PricingCategory.NOMAD_BROKER,
                "Marchands Ambulants & Courtiers",
                "Vendeur ambulant, Courtier / Démarcheur"
            ),
            Triple(
                PricingCategory.ARTISAN_MICRO,
                "Artisans & Services de proximité",
                "Coiffure, Couture, Kiosque, Librairie, Lavage Auto-Moto"
            ),
            Triple(
                PricingCategory.BOUTIQUE_ENTREPRISE,
                "Boutiques établies & Entreprises",
                "Boutique, Restaurant, Pharmacie, Quincaillerie, BTP, PME"
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("card_settings_differentiated_pricing"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active category summary banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(activeProfile.category.accentColorHex).copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color(activeProfile.category.accentColorHex).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(activeProfile.category.accentColorHex)
                        ) {
                            Text(
                                text = activeProfile.category.badgeLabel,
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = activeProfile.businessType,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeProfile.category.title} • Standard ${
                            SubscriptionPricingHelper.formatCompactPrice(activeProfile.standardMonthly, "/mois")
                        } • Pro ${
                            SubscriptionPricingHelper.formatCompactPrice(activeProfile.proAnnual, "/an")
                        }",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(activeProfile.category.accentColorHex)
                    )
                    Text(
                        text = "Formule actuelle : ${currentTier ?: "Gratuit"} • Tarifs adaptés automatiquement à votre activité",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Réduire la grille" else "Afficher la grille",
                    tint = Color(activeProfile.category.accentColorHex)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Grille des frais d'abonnement par catégorie d'activité :",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                tierLevels.forEach { (category, label, examples) ->
                    val sampleType = when (category) {
                        PricingCategory.NOMAD_BROKER -> "Vendeur ambulant"
                        PricingCategory.ARTISAN_MICRO -> "Coiffure"
                        PricingCategory.BOUTIQUE_ENTREPRISE -> "Boutique"
                    }
                    val profile = SubscriptionPricingHelper.getPricingProfile(sampleType)
                    val isCurrent = activeProfile.category == category
                    val accent = Color(category.accentColorHex)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pricing_level_${category.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = examples,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCurrent) accent else accent.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (isCurrent) "VOTRE TARIF" else category.badgeLabel,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCurrent) Color.White else accent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PricingChipColumn(
                                    title = "Standard",
                                    value = SubscriptionPricingHelper.formatCompactPrice(profile.standardMonthly, "/m"),
                                    accent = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                PricingChipColumn(
                                    title = "Pro (1 an)",
                                    value = SubscriptionPricingHelper.formatCompactPrice(profile.proAnnual, "/an"),
                                    accent = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                PricingChipColumn(
                                    title = "Entreprise",
                                    value = SubscriptionPricingHelper.formatCompactPrice(profile.entrepriseMonthly, "/m"),
                                    accent = accent,
                                    modifier = Modifier.weight(1f)
                                )
                                PricingChipColumn(
                                    title = "À vie",
                                    value = SubscriptionPricingHelper.formatCompactPrice(profile.premiumLifetime),
                                    accent = accent,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onChangeSector,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_settings_adjust_sector")
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Changer secteur", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onOpenSubscription,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(activeProfile.category.accentColorHex)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_settings_open_subscription")
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Souscrire", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PricingChipColumn(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
