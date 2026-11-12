package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductEntity
import com.example.ui.theme.*
import com.example.util.BusinessTypePresets
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemVariantCustomizerDialog(
    product: ProductEntity,
    initialNote: String = "",
    businessType: String = "Boutique",
    onDismiss: () -> Unit,
    onConfirm: (customNote: String) -> Unit
) {
    val sectorPreset = remember(businessType) {
        BusinessTypePresets.getPreset(businessType)
    }
    val features = sectorPreset.sectorFeatures

    var selectedSize by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("") }
    var selectedPosology by remember { mutableStateOf("") }
    var selectedKitchenNote by remember { mutableStateOf("") }
    var selectedCraftService by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf("") }
    var selectedNomadFormat by remember { mutableStateOf("") }
    var freeTextNote by remember { mutableStateOf(initialNote) }

    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "Sur-mesure")
    val colors = listOf("Noir", "Blanc", "Bleu", "Rouge", "Vert", "Jaune", "Faso Dan Fani", "Koko Dunda", "Motif")
    val posologies = listOf("1 matin / 1 soir", "3x par jour", "Avant les repas", "Après les repas", "Si douleur / fièvre", "Pendant 5 jours")
    val kitchenNotes = listOf("Sans piment", "Bien pimenté", "Piment à part", "Bien cuit", "Bien frais", "Sans glaçons", "À emporter")
    val craftServices = listOf("Fourniture + Pose", "Main d'œuvre seule", "Pièce seule", "Dépannage urgent", "Garantie 6 mois", "Chantier complet")
    val locations = listOf("Chantier client", "Atelier", "Sur site + Livraison", "Devis préalable")
    val nomadFormats = listOf("À l'unité", "Sachet de 5", "Lot de 10", "Bien glacé / Frais", "À emporter rapide")

    val buildFinalNote: () -> String = {
        val parts = mutableListOf<String>()
        if (selectedSize.isNotBlank()) parts.add("Taille: $selectedSize")
        if (selectedColor.isNotBlank()) parts.add("Couleur: $selectedColor")
        if (selectedPosology.isNotBlank()) parts.add("Posologie: $selectedPosology")
        if (selectedKitchenNote.isNotBlank()) parts.add("Cuisson: $selectedKitchenNote")
        if (selectedCraftService.isNotBlank()) parts.add("Prestation: $selectedCraftService")
        if (selectedLocation.isNotBlank()) parts.add("Lieu: $selectedLocation")
        if (selectedNomadFormat.isNotBlank()) parts.add("Format: $selectedNomadFormat")
        if (freeTextNote.isNotBlank()) parts.add(freeTextNote.trim())
        parts.joinToString(" • ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1
                )
                Text(
                    text = "${sectorPreset.emoji} Spécifications ${sectorPreset.label} • ${CurrencyFormatter.formatFCFA(product.sellPrice)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fashion / Clothing Size & Color
                if (features.hasVariations) {
                    Text(
                        text = "Taille du vêtement :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sizes) { size ->
                            val isSel = selectedSize == size
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedSize = if (isSel) "" else size },
                                label = { Text(size, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        text = "Couleur ou Tissu :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(colors) { col ->
                            val isSel = selectedColor == col
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedColor = if (isSel) "" else col },
                                label = { Text(col, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabGreenDark,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Pharmacy / Health Posology
                if (features.hasPrescriptionExpiry) {
                    Text(
                        text = "Conseil de prise & Posologie :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(posologies) { pos ->
                            val isSel = selectedPosology == pos
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedPosology = if (isSel) "" else pos },
                                label = { Text(pos, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Restaurant & Bar Cooking Notes
                if (features.hasTables) {
                    Text(
                        text = "Préférence de préparation :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(kitchenNotes) { kn ->
                            val isSel = selectedKitchenNote == kn
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedKitchenNote = if (isSel) "" else kn },
                                label = { Text(kn, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEA580C),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Artisan / SME & Craftsmen Services & Job Site
                if (features.hasJobSiteQuotes) {
                    Text(
                        text = "Type de prestation / Service :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(craftServices) { srv ->
                            val isSel = selectedCraftService == srv
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCraftService = if (isSel) "" else srv },
                                label = { Text(srv, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        text = "Lieu d'intervention / Modalité :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(locations) { loc ->
                            val isSel = selectedLocation == loc
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedLocation = if (isSel) "" else loc },
                                label = { Text(loc, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF92400E),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Vendeur Ambulant & Nomade Formats
                if (features.hasNomadMode) {
                    Text(
                        text = "Conditionnement / Format nomade :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(nomadFormats) { fmt ->
                            val isSel = selectedNomadFormat == fmt
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedNomadFormat = if (isSel) "" else fmt },
                                label = { Text(fmt, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE11D48),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Free text note
                OutlinedTextField(
                    value = freeTextNote,
                    onValueChange = { freeTextNote = it },
                    label = { Text("Note libre / Détail supplémentaire") },
                    placeholder = { Text("Ex: Retouche demandée, n° ordonnance, etc.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_custom_variant_note")
                )

                // Preview of final note tag
                val currentPreview = buildFinalNote()
                if (currentPreview.isNotBlank()) {
                    Surface(
                        color = KonabGreenLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aperçu : $currentPreview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KonabGreenDark,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(buildFinalNote())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                modifier = Modifier.testTag("btn_confirm_variant_modal")
            ) {
                Text("Valider la spécification", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
