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
    var selectedBakeryFlavor by remember { mutableStateOf("") }
    var selectedCoutureDetail by remember { mutableStateOf("") }
    var selectedPressingTreatment by remember { mutableStateOf("") }
    var selectedMeatCut by remember { mutableStateOf("") }
    var selectedTechDiagnostic by remember { mutableStateOf("") }
    var selectedJewelryMetal by remember { mutableStateOf("") }
    var selectedWashFinish by remember { mutableStateOf("") }
    var selectedUnitChoice by remember { mutableStateOf("") }
    var selectedBrokerService by remember { mutableStateOf("") }
    var selectedCoiffureGender by remember { mutableStateOf("") }
    var selectedStylist by remember { mutableStateOf("") }
    var freeTextNote by remember { mutableStateOf(initialNote) }

    val sizes = listOf("XS", "S", "M", "L", "XL", "XXL", "Sur-mesure")
    val colors = listOf("Noir", "Blanc", "Bleu", "Rouge", "Vert", "Jaune", "Faso Dan Fani", "Koko Dunda", "Motif")
    val posologies = listOf("1 matin / 1 soir", "3x par jour", "Avant les repas", "Après les repas", "Si douleur / fièvre", "Pendant 5 jours")
    val kitchenNotes = listOf("Sans piment", "Bien pimenté", "Piment à part", "Bien cuit", "Bien frais", "Sans glaçons", "À emporter")
    val craftServices = listOf("Fourniture + Pose", "Main d'œuvre seule", "Pièce seule", "Dépannage urgent", "Garantie 6 mois", "Chantier complet")
    val locations = listOf("Chantier client", "Atelier", "Sur site + Livraison", "Devis préalable")
    val nomadFormats = listOf("À l'unité", "Sachet de 5", "Lot de 10", "Bien glacé / Frais", "À emporter rapide")
    val bakeryFlavors = listOf("Chocolat noir", "Vanille bourbon", "Fraise", "Caramel au beurre", "Coco rapé", "Café", "Praliné", "Nature")
    val coutureDetails = listOf("Sur-mesure complet", "Col brodé", "Doublure soyeuse", "Ourlet invisible", "Urgent 24h", "Cérémonie / Fête")
    val pressingTreatments = listOf("Lavage à sec", "Repassage vapeur", "Détachage ciblé", "Sur cintre", "Sous film plastique", "Lavage délicat")
    val meatCuts = listOf("En tranches fines", "En gros morceaux", "Désossé / Sans os", "Entier vidé", "Pour grillade", "Assaisonné")
    val techDiagnostics = listOf("Écran fissuré", "Batterie HS", "Connecteur de charge", "Système bloqué / Déblocage", "Sauvegarde données", "Garantie 3 mois")
    val jewelryMetals = listOf("Or jaune 18k", "Or blanc 18k", "Argent massif 925", "Plaqué or 24k", "Perles de culture", "Gravure offerte")
    val washFinishes = listOf("Mousse active karcher", "Lustrage carrosserie", "Brillant pneus", "Sent-bon vanille", "Shampooing sièges")
    val brokerServices = listOf("Frais de visite", "Commission courtage", "Frais de dossier", "Contrat / Bail", "Acompte réservation")
    val coiffureGenders = listOf("Homme", "Femme")
    val stylists = listOf("Fatou", "Moussa", "Sarah", "Ibrahim")

    val buildFinalNote: () -> String = {
        val parts = mutableListOf<String>()
        if (selectedUnitChoice.isNotBlank()) parts.add("Unité: $selectedUnitChoice")
        if (selectedCoiffureGender.isNotBlank()) parts.add("Client: $selectedCoiffureGender")
        if (selectedStylist.isNotBlank()) parts.add("Coiffeur: $selectedStylist")
        if (selectedBrokerService.isNotBlank()) parts.add("Acte: $selectedBrokerService")
        if (selectedSize.isNotBlank()) parts.add("Taille: $selectedSize")
        if (selectedColor.isNotBlank()) parts.add("Couleur: $selectedColor")
        if (selectedPosology.isNotBlank()) parts.add("Posologie: $selectedPosology")
        if (selectedKitchenNote.isNotBlank()) parts.add("Cuisson: $selectedKitchenNote")
        if (selectedCraftService.isNotBlank()) parts.add("Prestation: $selectedCraftService")
        if (selectedLocation.isNotBlank()) parts.add("Lieu: $selectedLocation")
        if (selectedNomadFormat.isNotBlank()) parts.add("Format: $selectedNomadFormat")
        if (selectedBakeryFlavor.isNotBlank()) parts.add("Arôme: $selectedBakeryFlavor")
        if (selectedCoutureDetail.isNotBlank()) parts.add("Finition: $selectedCoutureDetail")
        if (selectedPressingTreatment.isNotBlank()) parts.add("Traitement: $selectedPressingTreatment")
        if (selectedMeatCut.isNotBlank()) parts.add("Découpe: $selectedMeatCut")
        if (selectedTechDiagnostic.isNotBlank()) parts.add("Diagnostic: $selectedTechDiagnostic")
        if (selectedJewelryMetal.isNotBlank()) parts.add("Métal: $selectedJewelryMetal")
        if (selectedWashFinish.isNotBlank()) parts.add("Lavage: $selectedWashFinish")
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
                // Unités du secteur
                if (sectorPreset.quickUnits.size > 1) {
                    Text(
                        text = "Unité / Conditionnement (${product.unit}) :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sectorPreset.quickUnits) { u ->
                            val isSel = selectedUnitChoice == u
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedUnitChoice = if (isSel) "" else u },
                                label = { Text(u, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(sectorPreset.accentColorHex),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Coiffure (Homme / Femme + Coiffeur)
                if (businessType.equals("Coiffure", ignoreCase = true) || features.hasOperatorStylist) {
                    Text(
                        text = "Clientèle Coiffure (Homme / Femme) :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(coiffureGenders) { gender ->
                            val isSel = selectedCoiffureGender == gender
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCoiffureGender = if (isSel) "" else gender },
                                label = { Text(if (gender == "Homme") "👨 Homme" else "👩 Femme", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDB2777),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Text(
                        text = "Praticien / Coiffeur :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(stylists) { st ->
                            val isSel = selectedStylist == st
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStylist = if (isSel) "" else st },
                                label = { Text(st, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF9333EA),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Courtier / Démarcheur
                if (businessType.contains("Courtier", ignoreCase = true)) {
                    Text(
                        text = "Type d'acte / Courtage :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(brokerServices) { act ->
                            val isSel = selectedBrokerService == act
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedBrokerService = if (isSel) "" else act },
                                label = { Text(act, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0F766E),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

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

                // Boulangerie & Pâtisserie Flavors
                if (businessType.contains("Boulang", ignoreCase = true) || product.category.contains("Gâteau", ignoreCase = true) || product.category.contains("Pâtisserie", ignoreCase = true)) {
                    Text(
                        text = "Arôme ou Parfum (Boulangerie/Pâtisserie) :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(bakeryFlavors) { flavor ->
                            val isSel = selectedBakeryFlavor == flavor
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedBakeryFlavor = if (isSel) "" else flavor },
                                label = { Text(flavor, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD97706),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Couture, Stylisme & Finition
                if (businessType.contains("Couture", ignoreCase = true) || product.category.contains("Confection", ignoreCase = true)) {
                    Text(
                        text = "Finition & Type de confection (Couture) :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(coutureDetails) { det ->
                            val isSel = selectedCoutureDetail == det
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCoutureDetail = if (isSel) "" else det },
                                label = { Text(det, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF8B5CF6),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Pressing & Blanchisserie
                if (businessType.contains("Pressing", ignoreCase = true) || product.category.contains("Linge", ignoreCase = true)) {
                    Text(
                        text = "Traitement & Présentation (Pressing) :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(pressingTreatments) { trt ->
                            val isSel = selectedPressingTreatment == trt
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedPressingTreatment = if (isSel) "" else trt },
                                label = { Text(trt, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Boucherie & Poissonnerie
                if (businessType.contains("Boucherie", ignoreCase = true) || businessType.contains("Poisson", ignoreCase = true) || product.category.contains("Viande", ignoreCase = true) || product.category.contains("Poisson", ignoreCase = true)) {
                    Text(
                        text = "Mode de découpe & Préparation :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(meatCuts) { cut ->
                            val isSel = selectedMeatCut == cut
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedMeatCut = if (isSel) "" else cut },
                                label = { Text(cut, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDC2626),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Informatique & Réparation
                if (businessType.contains("Informatique", ignoreCase = true) || product.category.contains("Réparation", ignoreCase = true)) {
                    Text(
                        text = "Diagnostic & État technique :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(techDiagnostics) { diag ->
                            val isSel = selectedTechDiagnostic == diag
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedTechDiagnostic = if (isSel) "" else diag },
                                label = { Text(diag, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2563EB),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Bijouterie & Joaillerie
                if (businessType.contains("Bijou", ignoreCase = true) || product.category.contains("Bijoux", ignoreCase = true)) {
                    Text(
                        text = "Alliage, Métal & Poinçon :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(jewelryMetals) { mtl ->
                            val isSel = selectedJewelryMetal == mtl
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedJewelryMetal = if (isSel) "" else mtl },
                                label = { Text(mtl, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEAB308),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Lavage Auto-Moto
                if (businessType.contains("Lavage", ignoreCase = true) || product.category.contains("Lavage", ignoreCase = true)) {
                    Text(
                        text = "Finition & Traitement véhicule :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(washFinishes) { wsh ->
                            val isSel = selectedWashFinish == wsh
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedWashFinish = if (isSel) "" else wsh },
                                label = { Text(wsh, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0D9488),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
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
