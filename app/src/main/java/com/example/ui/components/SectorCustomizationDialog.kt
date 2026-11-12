package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KonabBlue
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabOrange
import com.example.util.BusinessTypePresets

@Composable
fun SectorCustomizationDialog(
    currentSector: String,
    onDismiss: () -> Unit,
    onConfirmSector: (newSector: String, reloadPresetProducts: Boolean) -> Unit
) {
    var selectedSector by remember { mutableStateOf(currentSector) }
    var reloadCatalog by remember { mutableStateOf(false) }

    val allSectors = BusinessTypePresets.getAllTypes()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = "Secteur",
                        tint = KonabBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Secteur d'Activité & Métier",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Adapte l'application à votre type de commerce",
                        fontSize = 11.sp,
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Choisissez votre secteur pour activer les outils spécialisés (notes de table, variantes tailles/couleurs, ordonnances, vente au mètre/kg, etc.) :",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                allSectors.forEach { sectorName ->
                    val info = BusinessTypePresets.getPreset(sectorName)
                    val isSelected = selectedSector.equals(sectorName, ignoreCase = true)

                    val sectorIcon: ImageVector = when (sectorName) {
                        "Boutique" -> Icons.Default.ShoppingBag
                        "Restaurant" -> Icons.Default.Restaurant
                        "Kiosque" -> Icons.Default.Storefront
                        "Vendeur ambulant" -> Icons.Default.DirectionsWalk
                        "Pharmacie" -> Icons.Default.LocalPharmacy
                        "Quincaillerie" -> Icons.Default.Build
                        "Coiffure" -> Icons.Default.ContentCut
                        "Commerce général" -> Icons.Default.Storefront
                        "Électricité" -> Icons.Default.Bolt
                        "Maçonnerie", "Menuiserie", "Soudure", "Plomberie", "Mécanique" -> Icons.Default.Build
                        else -> Icons.Default.Category
                    }

                    val accentColor = when (sectorName) {
                        "Restaurant" -> Color(0xFFEA580C)
                        "Pharmacie" -> Color(0xFF16A34A)
                        "Boutique" -> Color(0xFF7C3AED)
                        "Quincaillerie" -> Color(0xFFD97706)
                        "Coiffure" -> Color(0xFFDB2777)
                        "Kiosque" -> Color(0xFF0284C7)
                        "Vendeur ambulant" -> Color(0xFFE11D48)
                        "Commerce général" -> Color(0xFF0D9488)
                        "Maçonnerie" -> Color(0xFFC2410C)
                        "Menuiserie" -> Color(0xFF92400E)
                        "Soudure" -> Color(0xFF0369A1)
                        "Plomberie" -> Color(0xFF0891B2)
                        "Électricité" -> Color(0xFFCA8A04)
                        "Mécanique" -> Color(0xFFDC2626)
                        else -> KonabBlue
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSector = sectorName }
                            .testTag("sector_item_${sectorName.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = sectorIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = info.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (sectorName.equals(currentSector, ignoreCase = true)) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = KonabGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "ACTUEL",
                                                color = KonabGreen,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = info.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )

                                // Feature Badges
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (info.sectorFeatures.hasTables) {
                                        SectorTagChip("Tables & Bons Cuisine")
                                    }
                                    if (info.sectorFeatures.hasVariations) {
                                        SectorTagChip("Tailles & Couleurs")
                                    }
                                    if (info.sectorFeatures.hasPrescriptionExpiry) {
                                        SectorTagChip("Ordonnances & Péremptions")
                                    }
                                    if (info.sectorFeatures.hasFractionalUnits) {
                                        SectorTagChip("Vente Vrac (m, kg, L)")
                                    }
                                    if (info.sectorFeatures.hasOperatorStylist) {
                                        SectorTagChip("Praticiennes & Soins")
                                    }
                                    if (info.sectorFeatures.hasJobSiteQuotes) {
                                        SectorTagChip("Devis & Chantiers PME")
                                    }
                                    if (info.sectorFeatures.hasNomadMode) {
                                        SectorTagChip("Vente Nomade & Express")
                                    }
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedSector = sectorName },
                                colors = RadioButtonDefaults.colors(selectedColor = accentColor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Toggle import sample catalog
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Importer le catalogue type du secteur",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Ajoute les articles de démonstration standards pour ce métier sans effacer vos produits existants.",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reloadCatalog,
                            onCheckedChange = { reloadCatalog = it },
                            modifier = Modifier.testTag("switch_import_preset_catalog")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmSector(selectedSector, reloadCatalog)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                modifier = Modifier.testTag("btn_confirm_sector_change")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Appliquer ce Secteur", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun SectorTagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
