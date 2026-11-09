package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.WasteConfig
import com.example.util.WasteManagementHelper
import com.example.util.WastePaymentRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteManagementModal(
    shopName: String,
    onAddCashExpense: (amount: Long, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(WasteManagementHelper.getConfig(context)) }
    var payments by remember { mutableStateOf(WasteManagementHelper.getPayments(context)) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Planning & Statut, 1: Payer Redevance, 2: Historique, 3: Config

    val isUpToDate = remember(config) { WasteManagementHelper.isPaymentUpToDate(config) }
    val nextPickupHint = remember(config) { WasteManagementHelper.getNextPickupHint(config) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = KonabBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Ramassage des Poubelles",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = KonabTextPrimary
                            )
                            Text(
                                text = "Salubrité & Déchets de la boutique",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_waste_modal")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (isUpToDate) Color(0xFFBBF7D0) else Color(0xFFFED7AA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isUpToDate) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isUpToDate) "Cotisation à jour" else "Cotisation à renouveler",
                                        color = if (isUpToDate) Color(0xFF15803D) else Color(0xFFB45309),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = config.collectorName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prochain passage :",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = nextPickupHint,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = KonabBlue
                            )
                        }

                        // Quick Call / WhatsApp buttons for collector
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (config.collectorPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${config.collectorPhone}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFE0E7FF), CircleShape)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Appeler éboueur", tint = Color(0xFF4338CA), modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val message = "Bonjour ${config.collectorName}, concernant le ramassage des poubelles pour la boutique '$shopName' :"
                                        val url = "https://api.whatsapp.com/send?phone=${config.collectorPhone}&text=${Uri.encode(message)}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFDCFCE7), CircleShape)
                                ) {
                                    Icon(Icons.Default.Chat, contentDescription = "WhatsApp éboueur", tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = KonabBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Planning", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Payer", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Historique (${payments.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Prestataire", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Body content based on active tab
                when (selectedTab) {
                    0 -> {
                        // TAB 0 : PLANNING & CONSIGNES
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, KonabBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Jours réguliers de ramassage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val allDays = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
                                            val fullDays = mapOf(
                                                "Lun" to "Lundi",
                                                "Mar" to "Mardi",
                                                "Mer" to "Mercredi",
                                                "Jeu" to "Jeudi",
                                                "Ven" to "Vendredi",
                                                "Sam" to "Samedi",
                                                "Dim" to "Dimanche"
                                            )
                                            allDays.forEach { shortDay ->
                                                val fullName = fullDays[shortDay] ?: shortDay
                                                val isActive = config.pickupDays.any { it.equals(fullName, ignoreCase = true) }
                                                Surface(
                                                    color = if (isActive) KonabBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = shortDay,
                                                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Plage horaire : ${config.pickupTime}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, KonabBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Assignment, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Consignes de salubrité du magasin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "• ${config.notes}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Plier et attacher les cartons vides d'emballage séparément.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Ne pas encombrer le trottoir ou la voie publique en dehors des heures de passage.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item {
                                Button(
                                    onClick = { selectedTab = 1 },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Payment, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enregistrer un paiement de redevance", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1 : FORMULAIRE DE PAIEMENT
                        var amountInput by remember { mutableStateOf(config.monthlyFee.toString()) }
                        var periodInput by remember { mutableStateOf("Novembre 2026") }
                        var paymentMethod by remember { mutableStateOf("Espèces") }
                        var deductFromCash by remember { mutableStateOf(true) }
                        var notesInput by remember { mutableStateOf("Cotisation ramassage ordures") }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                OutlinedTextField(
                                    value = amountInput,
                                    onValueChange = { amountInput = it },
                                    label = { Text("Montant de la cotisation (FCFA)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                OutlinedTextField(
                                    value = periodInput,
                                    onValueChange = { periodInput = it },
                                    label = { Text("Période couverte (ex: Novembre 2026)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                Text("Mode de paiement", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Espèces", "Orange Money", "Moov", "Wave").forEach { mode ->
                                        val isSel = paymentMethod == mode
                                        Surface(
                                            color = if (isSel) KonabBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { paymentMethod = mode }
                                        ) {
                                            Text(
                                                text = mode,
                                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = deductFromCash,
                                            onCheckedChange = { deductFromCash = it }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Déduire automatiquement de la caisse", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Crée une sortie de caisse comptable pour le ramassage des ordures.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            item {
                                Button(
                                    onClick = {
                                        val amt = amountInput.toLongOrNull() ?: config.monthlyFee
                                        val newRecord = WastePaymentRecord(
                                            id = "POUB-${System.currentTimeMillis() % 100000}",
                                            timestamp = System.currentTimeMillis(),
                                            amount = amt,
                                            period = periodInput.ifBlank { "Mois en cours" },
                                            paymentMethod = paymentMethod,
                                            collectorName = config.collectorName,
                                            notes = notesInput
                                        )
                                        WasteManagementHelper.addPayment(context, newRecord)
                                        payments = WasteManagementHelper.getPayments(context)
                                        config = WasteManagementHelper.getConfig(context)

                                        if (deductFromCash) {
                                            onAddCashExpense(
                                                amt,
                                                "Ramassage poubelles - $periodInput (${config.collectorName})"
                                            )
                                        }

                                        Toast.makeText(context, "Paiement de salubrité enregistré !", Toast.LENGTH_SHORT).show()
                                        selectedTab = 2
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_confirm_waste_payment")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Valider & Émettre le reçu de paiement", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2 : HISTORIQUE DES COTISATIONS
                        if (payments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = KonabTextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Aucun paiement enregistré", fontWeight = FontWeight.Bold)
                                    Text("Vos cotisations d'éboueurs apparaîtront ici.", fontSize = 12.sp, color = KonabTextMuted)
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(payments) { p ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, KonabBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = Color(0xFFDCFCE7),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = p.period,
                                                            color = Color(0xFF15803D),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(p.paymentMethod, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("N° : ${p.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = CurrencyFormatter.formatDateTime(p.timestamp),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = CurrencyFormatter.formatFCFA(p.amount),
                                                    fontFamily = OutfitFontFamily,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 15.sp,
                                                    color = Color(0xFF15803D)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        val receiptText = WasteManagementHelper.generateWastePaymentReceiptText(shopName, p)
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, receiptText)
                                                            putExtra(Intent.EXTRA_SUBJECT, "Reçu de cotisation salubrité - ${p.period}")
                                                        }
                                                        context.startActivity(Intent.createChooser(intent, "Partager le reçu"))
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = "Partager reçu", tint = KonabBlue, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // TAB 3 : CONFIGURATION PRESTATAIRE
                        var colName by remember { mutableStateOf(config.collectorName) }
                        var colPhone by remember { mutableStateOf(config.collectorPhone) }
                        var colFee by remember { mutableStateOf(config.monthlyFee.toString()) }
                        var colTime by remember { mutableStateOf(config.pickupTime) }
                        var colNotes by remember { mutableStateOf(config.notes) }
                        var selectedDays by remember { mutableStateOf(config.pickupDays.toSet()) }

                        val allDays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                OutlinedTextField(
                                    value = colName,
                                    onValueChange = { colName = it },
                                    label = { Text("Nom du collecteur ou GIE") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                OutlinedTextField(
                                    value = colPhone,
                                    onValueChange = { colPhone = it },
                                    label = { Text("Numéro de téléphone de l'éboueur") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                OutlinedTextField(
                                    value = colFee,
                                    onValueChange = { colFee = it },
                                    label = { Text("Tarif mensuel de base (FCFA)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                OutlinedTextField(
                                    value = colTime,
                                    onValueChange = { colTime = it },
                                    label = { Text("Heure habituelle de passage") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                Text("Jours de ramassage hebdomadaire", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    allDays.forEach { day ->
                                        val isSelected = selectedDays.contains(day)
                                        Surface(
                                            color = if (isSelected) KonabBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    selectedDays = if (isSelected) {
                                                        selectedDays - day
                                                    } else {
                                                        selectedDays + day
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = day.take(3),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = colNotes,
                                    onValueChange = { colNotes = it },
                                    label = { Text("Consignes particulières de dépôt") },
                                    maxLines = 2,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                Button(
                                    onClick = {
                                        val updated = config.copy(
                                            collectorName = colName.ifBlank { "GIE Salubrité" },
                                            collectorPhone = colPhone,
                                            monthlyFee = colFee.toLongOrNull() ?: 2500L,
                                            pickupTime = colTime.ifBlank { "08h00 - 10h00" },
                                            pickupDays = if (selectedDays.isNotEmpty()) selectedDays.toList() else listOf("Mardi", "Vendredi"),
                                            notes = colNotes
                                        )
                                        WasteManagementHelper.saveConfig(context, updated)
                                        config = updated
                                        Toast.makeText(context, "Paramètres de ramassage mis à jour !", Toast.LENGTH_SHORT).show()
                                        selectedTab = 0
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text("Enregistrer les coordonnées", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
