package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.RestaurantTableRecord
import com.example.ui.theme.*
import com.example.ui.viewmodel.CartItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenOrderModal(
    cart: List<CartItem>,
    shopName: String,
    initialTable: String = "Table 1",
    tables: List<RestaurantTableRecord> = emptyList(),
    activeStaffName: String = "Serveur",
    onUpdateItemNote: (productId: Long, note: String) -> Unit,
    onSubmitOrder: ((tableName: String, serverName: String, notes: String, sendDirectlyToKitchen: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentTable by remember { mutableStateOf(initialTable) }
    var serverName by remember(activeStaffName) { mutableStateOf(activeStaffName.ifBlank { "Serveur" }) }
    var orderNotes by remember { mutableStateOf("") }
    var editingItemNoteId by remember { mutableStateOf<Long?>(null) }
    var tempItemNote by remember { mutableStateOf("") }

    val presetTables = remember(tables) {
        tables.filter { it.isActive }.map { it.tableNumber }.ifEmpty {
            listOf("Table 1", "Table 2", "Table 3", "Table 4", "Table 5", "Terrasse A", "Terrasse B", "À Emporter", "VIP")
        }
    }

    val currentTime = remember {
        SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.FRENCH).format(Date())
    }

    val generateTicketText: () -> String = {
        buildString {
            appendLine("═══════════════════════════════════")
            appendLine("   BON DE COMMANDE CUISINE / BAR   ")
            appendLine("═══════════════════════════════════")
            appendLine("Établissement : $shopName")
            appendLine("Emplacement   : $currentTable")
            appendLine("Serveur(se)   : $serverName")
            appendLine("Heure         : $currentTime")
            appendLine("───────────────────────────────────")
            appendLine("ARTICLES À PRÉPARER :")
            cart.forEachIndexed { idx, item ->
                appendLine("${idx + 1}. [x${item.quantity}] ${item.product.name}")
                if (item.variantNote.isNotBlank()) {
                    appendLine("   👉 Spécification : ${item.variantNote}")
                }
            }
            if (orderNotes.isNotBlank()) {
                appendLine("───────────────────────────────────")
                appendLine("Note générale : $orderNotes")
            }
            appendLine("═══════════════════════════════════")
            appendLine("   Konab POS • Service Restaurant  ")
            appendLine("═══════════════════════════════════")
        }
    }

    val shareViaWhatsApp: () -> Unit = {
        try {
            val text = generateTicketText()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(sendIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, generateTicketText())
            }
            context.startActivity(Intent.createChooser(fallback, "Transmettre le bon cuisine"))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFEA580C).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier
                                .size(36.dp)
                                .padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bon de Préparation Cuisine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Restaurant, Maquis & Bar • $currentTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Table Selection
            Text(
                text = "Emplacement / Table :",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presetTables) { table ->
                    val isSel = currentTable == table
                    FilterChip(
                        selected = isSel,
                        onClick = { currentTable = table },
                        label = { Text(table, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEA580C),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Server / Note fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text("Serveur(se)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currentTable,
                    onValueChange = { currentTable = it },
                    label = { Text("N° Table") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Order items list with custom notes
            Text(
                text = "Plats et boissons commandés (${cart.size}) :",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cart) { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KonabBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "x${item.quantity} ${item.product.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        editingItemNoteId = item.product.id
                                        tempItemNote = item.variantNote
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (item.variantNote.isBlank()) "+ Cuisson/Note" else "Modifier",
                                        fontSize = 12.sp,
                                        color = Color(0xFFEA580C)
                                    )
                                }
                            }

                            if (item.variantNote.isNotBlank()) {
                                Surface(
                                    color = Color(0xFFFFF7ED),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDBA74))
                                ) {
                                    Text(
                                        text = "👉 ${item.variantNote}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF9A3412),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = orderNotes,
                onValueChange = { orderNotes = it },
                label = { Text("Note générale cuisine (optionnel)") },
                placeholder = { Text("Ex: Client pressé, servir ensemble...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_kitchen_order_notes")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Actions
            if (onSubmitOrder != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSubmitOrder(currentTable, serverName, orderNotes, false)
                            Toast.makeText(context, "Commande envoyée au Gérant pour validation !", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_send_order_manager")
                    ) {
                        Text("Au Gérant", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = {
                            onSubmitOrder(currentTable, serverName, orderNotes, true)
                            Toast.makeText(context, "Commande transmise directement en cuisine !", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("btn_confirm_kitchen_order")
                    ) {
                        Icon(Icons.Default.SoupKitchen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Envoyer Cuisine", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    OutlinedIconButton(
                        onClick = shareViaWhatsApp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_share_kitchen_whatsapp")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Partager WhatsApp", tint = Color(0xFF25D366))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = shareViaWhatsApp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_share_kitchen_whatsapp"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "Bon de commande transmis en cuisine !", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("btn_confirm_kitchen_order")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Envoyer Cuisine", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal to edit cooking/drink note
    if (editingItemNoteId != null) {
        val targetProduct = cart.find { it.product.id == editingItemNoteId }?.product
        AlertDialog(
            onDismissRequest = { editingItemNoteId = null },
            title = { Text("Note pour : ${targetProduct?.name ?: ""}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Indiquez la cuisson, l'assaisonnement ou la boisson souhaitée :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempItemNote,
                        onValueChange = { tempItemNote = it },
                        placeholder = { Text("Ex: Bien pimenté, piment à part, bien fraîche, sans glaçons...") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick suggestion chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val suggestions = listOf("Bien frais", "Sans glaçons", "Piment à part", "Bien cuit", "Sans oignons", "À emporter")
                        items(suggestions) { sugg ->
                            SuggestionChip(
                                onClick = {
                                    tempItemNote = if (tempItemNote.isBlank()) sugg else "$tempItemNote, $sugg"
                                },
                                label = { Text(sugg, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingItemNoteId?.let { onUpdateItemNote(it, tempItemNote) }
                        editingItemNoteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemNoteId = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
