package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Straighten
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
import com.example.ui.theme.KonabGreen
import com.example.util.BusinessTypeInfo
import com.example.util.CurrencyFormatter

/**
 * Permet d'ajouter rapidement une vente ou prestation personnalisée avec choix d'unité
 * adaptée au secteur d'activité (commission, visite, kg, mètre, sac, course, portion, etc.).
 */
@Composable
fun CustomUnitSaleDialog(
    sectorPreset: BusinessTypeInfo,
    onDismiss: () -> Unit,
    onConfirm: (title: String, unitPrice: Long, quantity: Int, unit: String, note: String) -> Unit
) {
    val defaultUnit = sectorPreset.quickUnits.firstOrNull() ?: "pièce"
    var title by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(defaultUnit) }
    var unitPriceStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }

    val parsedPrice = unitPriceStr.trim().toLongOrNull() ?: 0L
    val parsedQty = (quantityStr.trim().toIntOrNull() ?: 1).coerceAtLeast(1)
    val total = parsedPrice * parsedQty
    val canConfirm = title.isNotBlank() && parsedPrice > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Straighten, contentDescription = null, tint = Color(sectorPreset.accentColorHex))
                Column {
                    Text("Vente / Unité ${sectorPreset.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = sectorPreset.unitGuidance,
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
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Désignation / Prestation *") },
                    placeholder = { Text("Ex: ${sectorPreset.quickActionHint}") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_custom_unit_title")
                )

                Text(
                    text = "Unité du secteur :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sectorPreset.quickUnits) { u ->
                        val isSel = unit.equals(u, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { unit = u },
                            label = { Text(u, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(sectorPreset.accentColorHex),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitPriceStr,
                        onValueChange = { unitPriceStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Prix / $unit (FCFA) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("input_custom_unit_price")
                    )

                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Qté ($unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("input_custom_unit_qty")
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Précision (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalNote = listOfNotNull(
                        "Unité: $unit",
                        note.trim().takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    onConfirm(title.trim(), parsedPrice, parsedQty, unit.trim().ifBlank { "pièce" }, finalNote)
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                modifier = Modifier.testTag("btn_confirm_custom_unit_sale")
            ) {
                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ajouter (${CurrencyFormatter.formatFCFA(total)})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
