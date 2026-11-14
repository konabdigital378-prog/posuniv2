package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ProductEntity
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

private data class HairStylePreset(
    val name: String,
    val suggestedPrice: Long
)

/**
 * Dialogue dédié au secteur Coiffure :
 * - Choisir entre Coiffure Homme et Coiffure Femme
 * - Sélectionner ou saisir le nom de la coiffure
 * - Définir librement le montant de la coiffure (FCFA)
 * - Assigner le coiffeur / la coiffeuse et optionnellement enregistrer le tarif dans le catalogue
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoiffureServiceDialog(
    initialStylist: String = "Fatou",
    onDismiss: () -> Unit,
    onConfirmCoiffure: (title: String, amount: Long, note: String, saveToCatalogAs: ProductEntity?) -> Unit
) {
    var selectedGender by remember { mutableStateOf("Homme") } // "Homme" ou "Femme"
    var serviceName by remember { mutableStateOf("Dégradé / Taper Homme") }
    var amountStr by remember { mutableStateOf("1500") }
    var selectedStylist by remember { mutableStateOf(initialStylist) }
    var extraNote by remember { mutableStateOf("") }
    var saveToCatalog by remember { mutableStateOf(false) }

    val hommePresets = remember {
        listOf(
            HairStylePreset("Coupe simple Homme", 1000L),
            HairStylePreset("Dégradé / Taper Homme", 1500L),
            HairStylePreset("Coupe + Barbe + Contours", 2000L),
            HairStylePreset("Traçage / Contours", 500L),
            HairStylePreset("Locks / Teinture Homme", 3500L)
        )
    }

    val femmePresets = remember {
        listOf(
            HairStylePreset("Tresses / Nattes Femme", 3500L),
            HairStylePreset("Pose Perruque / Lace", 5000L),
            HairStylePreset("Brushing + Soin Capillaire", 3000L),
            HairStylePreset("Rasta / Crochet Braids", 7000L),
            HairStylePreset("Lissage / Défrisage", 4000L),
            HairStylePreset("Chignon Mariage / Cérémonie", 10000L)
        )
    }

    val activePresets = if (selectedGender == "Homme") hommePresets else femmePresets
    val quickAmounts = if (selectedGender == "Homme") {
        listOf(500L, 1000L, 1500L, 2000L, 3000L, 5000L)
    } else {
        listOf(2000L, 3500L, 5000L, 7500L, 10000L, 15000L)
    }
    val stylists = listOf("Fatou", "Moussa", "Sarah", "Ibrahim")
    val parsedAmount = amountStr.trim().toLongOrNull() ?: 0L
    val canConfirm = serviceName.isNotBlank() && parsedAmount > 0L

    val accentColor = if (selectedGender == "Homme") KonabBlue else Color(0xFFDB2777)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = null, tint = accentColor)
                Column {
                    Text("Faire une coiffure", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "Choisissez Homme ou Femme et définissez le montant",
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
                // Sélecteur Homme / Femme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Homme" to "👨 Coiffure Homme", "Femme" to "👩 Coiffure Femme").forEach { (gender, label) ->
                        val isSel = selectedGender == gender
                        val btnColor = if (gender == "Homme") KonabBlue else Color(0xFFDB2777)
                        Surface(
                            onClick = {
                                selectedGender = gender
                                val first = if (gender == "Homme") hommePresets.first() else femmePresets.first()
                                serviceName = first.name
                                amountStr = first.suggestedPrice.toString()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) btnColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isSel) btnColor else KonabBorder),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_coiffure_gender_${gender.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Modèles de coiffures selon Homme / Femme
                Text(
                    text = "Prestations ${selectedGender} fréquentes :",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(activePresets) { preset ->
                        val isSel = serviceName.equals(preset.name, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                serviceName = preset.name
                                amountStr = preset.suggestedPrice.toString()
                            },
                            label = {
                                Text(
                                    "${preset.name} (${preset.suggestedPrice} F)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Nom de la coiffure ($selectedGender) *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_coiffure_name")
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Montant de la coiffure ($selectedGender) en FCFA *") },
                    suffix = { Text("FCFA", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_coiffure_amount")
                )

                // Montants rapides
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickAmounts) { amt ->
                        val isSel = parsedAmount == amt
                        SuggestionChip(
                            onClick = { amountStr = amt.toString() },
                            label = {
                                Text(
                                    CurrencyFormatter.formatFCFA(amt),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSel) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // Praticien(ne)
                Text(
                    text = "Coiffeur / Coiffeuse assigné(e) :",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(stylists) { st ->
                        val isSel = selectedStylist == st
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedStylist = st },
                            label = { Text(st, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF9333EA),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = extraNote,
                    onValueChange = { extraNote = it },
                    label = { Text("Note / Style (facultatif)") },
                    placeholder = { Text("Ex: Rasage de près, mèches fournies par client...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enregistrer aussi ce tarif au catalogue",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ajoute cette coiffure dans ${if (selectedGender == "Homme") "Coupes Hommes" else "Prestations Femmes"}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = saveToCatalog,
                        onCheckedChange = { saveToCatalog = it },
                        modifier = Modifier.testTag("switch_coiffure_save_catalog")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = serviceName.trim()
                    val noteParts = mutableListOf(
                        "Genre: $selectedGender",
                        "Coiffeur: $selectedStylist",
                        "Unité: service"
                    )
                    if (extraNote.isNotBlank()) noteParts.add(extraNote.trim())

                    val catalogProduct = if (saveToCatalog) {
                        ProductEntity(
                            name = cleanTitle,
                            category = if (selectedGender == "Homme") "Coupes Hommes" else "Prestations Femmes",
                            buyPrice = 0L,
                            sellPrice = parsedAmount,
                            stockQuantity = 999,
                            minStockAlert = 5,
                            unit = "service",
                            imageUri = "img_prod_cosmetique"
                        )
                    } else null

                    onConfirmCoiffure(cleanTitle, parsedAmount, noteParts.joinToString(" • "), catalogProduct)
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.testTag("btn_confirm_coiffure_service")
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ajouter (${CurrencyFormatter.formatFCFA(parsedAmount)})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
