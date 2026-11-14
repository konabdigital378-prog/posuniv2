package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashMovementEntity
import com.example.data.local.entity.CashSessionEntity
import com.example.ui.components.KonabEmptyState
import com.example.ui.components.KonabLivePulseDot
import com.example.ui.components.KonabScreenTopBar
import com.example.ui.components.KonabStaggeredReveal
import com.example.ui.components.KonabWidthClass
import com.example.ui.components.LocalKonabWidthClass
import com.example.ui.components.ResponsiveMaxWidthBox
import com.example.ui.components.konabHeroShimmer
import com.example.ui.components.konabPressScale
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(
    activeSession: CashSessionEntity?,
    cashMovements: List<CashMovementEntity>,
    onOpenSession: (openingBalance: Long) -> Unit,
    onCloseSession: (actualBalance: Long, notes: String) -> Unit,
    onAddMovement: (type: String, amount: Long, reason: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showOpenSessionDialog by remember { mutableStateOf(false) }
    var showCloseSessionDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showCashInDialog by remember { mutableStateOf(false) }
    var movementFilter by remember { mutableStateOf("Tous") } // Tous, Entrées, Sorties

    // Calculate live cash in drawer
    val currentCashBalance = remember(cashMovements) {
        cashMovements.sumOf { m ->
            when (m.type) {
                "SALE", "CASH_IN", "DEBT_PAYMENT" -> m.amount
                "EXPENSE", "CASH_OUT", "SUPPLIER_PAYMENT" -> -m.amount
                else -> 0L
            }
        }
    }

    val totalSalesCash = remember(cashMovements) {
        cashMovements.filter { it.type == "SALE" }.sumOf { it.amount }
    }

    val totalDebtCollected = remember(cashMovements) {
        cashMovements.filter { it.type == "DEBT_PAYMENT" }.sumOf { it.amount }
    }

    val totalExpenses = remember(cashMovements) {
        cashMovements.filter { it.type in listOf("EXPENSE", "SUPPLIER_PAYMENT", "CASH_OUT") }.sumOf { it.amount }
    }

    val filteredMovements = remember(cashMovements, movementFilter) {
        when (movementFilter) {
            "Entrées" -> cashMovements.filter { it.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT") }
            "Sorties" -> cashMovements.filter { it.type in listOf("EXPENSE", "CASH_OUT", "SUPPLIER_PAYMENT") }
            else -> cashMovements
        }
    }

    Scaffold(
        topBar = {
            KonabScreenTopBar(
                title = "Caisse & Dépenses",
                subtitle = if (activeSession != null) "Session ouverte" else "Caisse actuellement fermée",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            val zReport = buildString {
                                append("🧾 *RAPPORT Z DE CAISSE — KONAB POS*\n")
                                append("Statut : ${if (activeSession != null) "Ouverte (depuis ${CurrencyFormatter.formatTimeOnly(activeSession.openedAt)})" else "Fermée"}\n")
                                append("----------------------------\n")
                                append("• Ventes espèces : ${CurrencyFormatter.formatFCFA(totalSalesCash)}\n")
                                append("• Recouvrements dettes : ${CurrencyFormatter.formatFCFA(totalDebtCollected)}\n")
                                append("• Dépenses & sorties : -${CurrencyFormatter.formatFCFA(totalExpenses)}\n")
                                append("----------------------------\n")
                                append("💰 *Solde théorique caisse : ${CurrencyFormatter.formatFCFA(maxOf(0L, currentCashBalance))}*\n")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Rapport Z de Caisse Konab POS")
                                putExtra(Intent.EXTRA_TEXT, zReport)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Partager le Z de caisse"))
                        },
                        modifier = Modifier.testTag("btn_share_z_report")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Partager le Z de caisse", tint = KonabBlue)
                    }
                }
            )
        }
    ) { paddingValues ->
        val widthClass = LocalKonabWidthClass.current
        ResponsiveMaxWidthBox(
            maxWidth = 960.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (widthClass == KonabWidthClass.COMPACT) 16.dp else 24.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cash Drawer Status Banner
                item {
                    KonabStaggeredReveal(index = 0) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(KonabBlue, KonabBlueDark)
                                    ),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clip(RoundedCornerShape(22.dp))
                                .konabHeroShimmer(maxAlpha = 0.09f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (activeSession != null) {
                                            KonabLivePulseDot(color = Color(0xFF10B981), size = 9.dp)
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(Color(0xFFEF4444), CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (activeSession != null) "Caisse ouverte" else "Caisse fermée",
                                            color = Color.White.copy(alpha = 0.95f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (activeSession != null) {
                                        Text(
                                            text = "Depuis ${CurrencyFormatter.formatTimeOnly(activeSession.openedAt)}",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Solde théorique en caisse",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = CurrencyFormatter.formatFCFA(maxOf(0L, currentCashBalance)),
                                    color = Color.White,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (activeSession == null) {
                                        Button(
                                            onClick = { showOpenSessionDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabOrange, contentColor = Color.White),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .konabPressScale()
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ouvrir la caisse", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { showCloseSessionDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .konabPressScale()
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = KonabBlueDark)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Clôturer la caisse (Z de caisse)", color = KonabBlueDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick cash movement actions
                item {
                    KonabStaggeredReveal(index = 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showExpenseDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .konabPressScale()
                                    .testTag("btn_add_expense")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sortie / Dépense", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showCashInDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .konabPressScale()
                                    .testTag("btn_cash_in")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = KonabGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Entrée de caisse", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KonabGreen)
                            }
                        }
                    }
                }

                // Breakdown Summary Card
                item {
                    KonabStaggeredReveal(index = 2) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Récapitulatif des flux", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total encaissé espèces :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(CurrencyFormatter.formatFCFA(totalSalesCash), fontWeight = FontWeight.Bold, color = KonabGreen)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total dépenses & sorties :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("-${CurrencyFormatter.formatFCFA(totalExpenses)}", fontWeight = FontWeight.Bold, color = KonabRed)
                                }
                            }
                        }
                    }
                }

                // Movements History & Filters
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Historique des mouvements de caisse (${filteredMovements.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Tous", "Entrées", "Sorties").forEach { filterOption ->
                                FilterChip(
                                    selected = movementFilter == filterOption,
                                    onClick = { movementFilter = filterOption },
                                    label = { Text(filterOption, fontSize = 12.sp) },
                                    modifier = Modifier.konabPressScale(pressedScale = 0.96f)
                                )
                            }
                        }
                    }
                }

                if (filteredMovements.isEmpty()) {
                    item {
                        KonabEmptyState(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Aucun mouvement de caisse",
                            subtitle = "Aucune opération ne correspond au filtre « $movementFilter » pour l'instant.",
                            accentColor = KonabBlue
                        )
                    }
                } else {
                    itemsIndexed(filteredMovements, key = { index, m -> "${m.id}_$index" }) { idx, m ->
                        val isIncoming = m.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT")
                        KonabStaggeredReveal(index = idx) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (isIncoming) KonabGreenLight else KonabRedContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = if (isIncoming) KonabGreen else KonabRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(m.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(
                                                "${CurrencyFormatter.formatDateTime(m.timestamp)} • ${m.operatorName}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = (if (isIncoming) "+" else "-") + CurrencyFormatter.formatFCFA(m.amount),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncoming) KonabGreen else KonabRed,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Open Session Dialog
    if (showOpenSessionDialog) {
        var openingFloatStr by remember { mutableStateOf("25000") }
        AlertDialog(
            onDismissRequest = { showOpenSessionDialog = false },
            title = { Text("Ouverture de caisse") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Indiquez le montant du fond de caisse initial :", fontSize = 13.sp)
                    OutlinedTextField(
                        value = openingFloatStr,
                        onValueChange = { openingFloatStr = it },
                        label = { Text("Fond de caisse (FCFA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = openingFloatStr.toLongOrNull() ?: 0L
                        onOpenSession(amount)
                        showOpenSessionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Ouvrir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOpenSessionDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Close Session Dialog
    if (showCloseSessionDialog) {
        var countedStr by remember { mutableStateOf(currentCashBalance.toString()) }
        var notes by remember { mutableStateOf("") }
        val counted = countedStr.toLongOrNull() ?: 0L
        val diff = counted - currentCashBalance

        AlertDialog(
            onDismissRequest = { showCloseSessionDialog = false },
            title = { Text("Clôture de caisse (Z de caisse)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Solde théorique : ${CurrencyFormatter.formatFCFA(currentCashBalance)}", fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = countedStr,
                        onValueChange = { countedStr = it },
                        label = { Text("Montant réel compté en caisse (FCFA) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = if (diff == 0L) KonabGreenLight else KonabRedContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diff == 0L) "Caisse parfaitement équilibrée (+0 FCFA)"
                            else "Écart de caisse : ${if (diff > 0) "+" else ""}${CurrencyFormatter.formatFCFA(diff)}",
                            fontWeight = FontWeight.Bold,
                            color = if (diff == 0L) KonabGreenDark else KonabRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observations ou explications d'écart") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCloseSession(counted, notes)
                        showCloseSessionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Confirmer la clôture")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseSessionDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Expense Dialog
    if (showExpenseDialog) {
        var expenseAmountStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        val categories = listOf("Loyer", "Électricité SONABEL", "Transport", "Salaires", "Fournitures", "Autre")
        var selectedCat by remember { mutableStateOf(categories.first()) }

        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Enregistrer une dépense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = expenseAmountStr,
                        onValueChange = { expenseAmountStr = it },
                        label = { Text("Montant de la dépense (FCFA) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Catégorie de dépense :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCat == cat,
                                onClick = { selectedCat = cat },
                                label = { Text(cat, fontSize = 12.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Précision / Détail du motif") },
                        placeholder = { Text("Ex: Achat recharge compteur") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = expenseAmountStr.toLongOrNull() ?: 0L
                        if (amount > 0) {
                            val finalReason = if (reason.isNotBlank()) "$selectedCat - $reason" else selectedCat
                            onAddMovement("EXPENSE", amount, finalReason)
                            showExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabRed)
                ) {
                    Text("Valider la sortie")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Cash In Dialog
    if (showCashInDialog) {
        var cashInAmountStr by remember { mutableStateOf("") }
        var cashInReason by remember { mutableStateOf("Apport personnel du gérant") }

        AlertDialog(
            onDismissRequest = { showCashInDialog = false },
            title = { Text("Entrée d'argent exceptionnelle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cashInAmountStr,
                        onValueChange = { cashInAmountStr = it },
                        label = { Text("Montant apporté (FCFA) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = cashInReason,
                        onValueChange = { cashInReason = it },
                        label = { Text("Origine / Motif") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = cashInAmountStr.toLongOrNull() ?: 0L
                        if (amount > 0) {
                            onAddMovement("CASH_IN", amount, cashInReason)
                            showCashInDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Valider l'entrée")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashInDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
