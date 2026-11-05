package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashMovementEntity
import com.example.data.local.entity.CashSessionEntity
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
    var showOpenSessionDialog by remember { mutableStateOf(false) }
    var showCloseSessionDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showCashInDialog by remember { mutableStateOf(false) }

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

    val totalExpenses = remember(cashMovements) {
        cashMovements.filter { it.type in listOf("EXPENSE", "SUPPLIER_PAYMENT") }.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caisse & Dépenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cash Drawer Status Banner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = KonabGreen),
                    modifier = Modifier.fillMaxWidth()
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
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (activeSession != null) Color(0xFF69F0AE) else Color(0xFFFF8A80), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activeSession != null) "Caisse ouverte" else "Caisse fermée",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Solde théorique en caisse",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )

                        Text(
                            text = CurrencyFormatter.formatFCFA(maxOf(0L, currentCashBalance)),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (activeSession == null) {
                                Button(
                                    onClick = { showOpenSessionDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGold),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ouvrir la caisse", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { showCloseSessionDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = KonabGreenDark)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clôturer la caisse (Z de caisse)", color = KonabGreenDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Quick cash movement actions
            item {
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
                            .height(48.dp)
                            .testTag("btn_add_expense")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sortie / Dépense", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showCashInDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cash_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Entrée de caisse", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KonabGreen)
                    }
                }
            }

            // Breakdown Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Récapitulatif des flux", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            // Movements History
            item {
                Text(
                    text = "Historique des mouvements de caisse",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (cashMovements.isEmpty()) {
                item {
                    Text(
                        text = "Aucun mouvement de caisse enregistré.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(cashMovements) { m ->
                    val isIncoming = m.type in listOf("SALE", "CASH_IN", "DEBT_PAYMENT")
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    Text(m.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

                    Text("Motif :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    categories.take(3).forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCat = cat }
                        ) {
                            RadioButton(selected = selectedCat == cat, onClick = { selectedCat = cat })
                            Text(cat, fontSize = 13.sp)
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
