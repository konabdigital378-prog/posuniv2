package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.*
import com.example.ui.screens.AgentUser
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.QrCodeHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteCollectorPortalModal(
    businesses: List<WasteBusinessProfile>,
    clients: List<WasteHouseholdClient>,
    activeBusinessId: String?,
    isSyncing: Boolean,
    familyQuery: String,
    pendingQrTarget: ParsedWasteQr?,
    activeAgent: AgentUser? = null,
    currentPublicUser: PublicUserAccount? = null,
    onRequestAgentLogin: () -> Unit = {},
    onRefreshCloud: () -> Unit,
    onSelectBusiness: (String?) -> Unit,
    onSaveBusiness: (
        businessName: String,
        managerName: String,
        phone: String,
        city: String,
        pin: String,
        defaultFee: Long,
        defaultPassages: Int,
        existingId: String?,
        photoUri: String,
        onDone: (WasteBusinessProfile) -> Unit
    ) -> Unit,
    onSaveClient: (
        business: WasteBusinessProfile,
        familyName: String,
        contactName: String,
        phone: String,
        neighborhood: String,
        status: WasteClientStatus,
        pickupDaysLabel: String,
        monthlyFee: Long,
        targetPassages: Int,
        existingClient: WasteHouseholdClient?,
        photoUri: String,
        onDone: (WasteHouseholdClient) -> Unit
    ) -> Unit,
    onUpdateClientPhoto: (WasteHouseholdClient, String) -> Unit = { _, _ -> },
    onCheckPickup: (WasteHouseholdClient, collectorName: String, note: String) -> Unit,
    onUncheckLastPickup: (WasteHouseholdClient) -> Unit,
    onMarkBillingNotified: (WasteHouseholdClient) -> Unit,
    onRecordPayment: (WasteHouseholdClient, amountPaid: Long, note: String) -> Unit,
    onDeleteClient: (String) -> Unit,
    onUpdateFamilyQuery: (String) -> Unit,
    onScanWasteQr: (String) -> Boolean,
    onDismissQrTarget: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(if (pendingQrTarget != null) 0 else 1.takeIf { businesses.isNotEmpty() || activeAgent != null } ?: 0) }
    var searchFamilyInput by remember(familyQuery, currentPublicUser?.phone) {
        mutableStateOf(familyQuery.ifBlank { currentPublicUser?.phone.orEmpty() })
    }
    var unlockedBizIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateBusinessDialog by remember { mutableStateOf(false) }
    var showDeployerRequiredDialog by remember { mutableStateOf(false) }
    var editingBusiness by remember { mutableStateOf<WasteBusinessProfile?>(null) }
    var pinGateBusiness by remember { mutableStateOf<WasteBusinessProfile?>(null) }
    var showAddClientDialog by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<WasteHouseholdClient?>(null) }
    var selectedClientForCardModal by remember { mutableStateOf<WasteHouseholdClient?>(null) }
    var paymentTargetClient by remember { mutableStateOf<WasteHouseholdClient?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var providerStatusFilter by remember { mutableStateOf<String>("ALL") } // ALL, ACTIFS, PROSPECTS, REMPLIES
    var providerSearch by remember { mutableStateOf("") }

    val activeBiz = remember(businesses, activeBusinessId) {
        businesses.firstOrNull { it.id == activeBusinessId } ?: businesses.firstOrNull()
    }

    LaunchedEffect(pendingQrTarget, clients) {
        val target = pendingQrTarget ?: return@LaunchedEffect
        if (target.clientId.isNotBlank()) {
            searchFamilyInput = target.clientId
            onUpdateFamilyQuery(target.clientId)
            val matched = clients.firstOrNull { it.id.equals(target.clientId, ignoreCase = true) }
            if (matched != null) {
                selectedClientForCardModal = matched
                selectedTab = 0
                onDismissQrTarget()
            }
        }
    }

    LaunchedEffect(Unit) {
        onRefreshCloud()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF10B981))
                            )
                        )
                        .konabHeroShimmer(maxAlpha = 0.08f)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Gestion Poubelles",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Gestion Poubelles & Salubrité",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 17.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    KonabLivePulseDot(color = Color(0xFFA7F3D0), size = 5.5.dp)
                                }
                                Text(
                                    text = "${clients.size} foyer(s) • Passages, cartes famille & relance",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD1FAE5),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onRefreshCloud,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_waste_refresh_cloud")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Actualiser Cloud",
                                        tint = Color.White
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_waste_close")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                            }
                        }
                    }
                }

                // Role Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF047857)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_waste_family"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Espace Famille",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_waste_collector"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Éboueur Pro",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    // =====================================================
                    // TAB 0 : ESPACE FAMILLE (VOIR SA CARTE & PASSAGES)
                    // =====================================================
                    val familyMatches = remember(clients, familyQuery, searchFamilyInput) {
                        val q = searchFamilyInput.ifBlank { familyQuery }
                        WasteCollectorStorage.findClientsForFamily(clients, q)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = konabCardBorder(),
                                tonalElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Consulter ma carte de passage poubelles",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Entrez votre téléphone, nom de famille ou code carte (ex: FAM-...) ou scannez votre QR code.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = searchFamilyInput,
                                        onValueChange = { searchFamilyInput = it },
                                        label = { Text("Téléphone, Nom de famille ou Code FAM-...") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("tf_waste_family_query")
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onUpdateFamilyQuery(searchFamilyInput)
                                                onRefreshCloud()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_waste_search_family")
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                "Voir ma carte",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { showScanner = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_waste_family_scan_qr")
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                "Scanner QR",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (searchFamilyInput.isBlank() && familyQuery.isBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFECFDF5),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFF047857),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "Transparence totale pour votre foyer",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF065F46)
                                        )
                                        Text(
                                            text = "Après votre paiement, retrouvez exactement le nombre de coches et chaque date/heure où l'équipe est passée vider votre poubelle.",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            color = Color(0xFF064E3B)
                                        )
                                    }
                                }
                            }
                        } else if (familyMatches.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = konabCardBorder(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Aucune carte trouvée pour « ${searchFamilyInput.ifBlank { familyQuery }} »",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Vérifiez le numéro saisi ou appuyez sur Actualiser.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(familyMatches, key = { it.id }) { household ->
                                FamilyWasteCardView(
                                    client = household,
                                    onOpenFullDetails = { selectedClientForCardModal = household },
                                    onContactCollectorWhatsApp = {
                                        openWhatsAppForPhone(
                                            context = context,
                                            phone = household.businessPhone,
                                            message = "Bonjour ${household.businessName}, je suis de la famille ${household.familyName} (Code ${household.id}) concernant le ramassage des ordures."
                                        )
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // =====================================================
                    // TAB 1 : ESPACE ÉBOUEUR PRO (BUSINESS, CLIENTS & COCHES)
                    // =====================================================
                    val bizClients = remember(clients, activeBiz) {
                        if (activeBiz == null) emptyList() else clients.filter { it.businessId == activeBiz.id }
                    }
                    val activeCount = bizClients.count { it.status == WasteClientStatus.CLIENT_ACTIF }
                    val prospectCount = bizClients.count { it.status == WasteClientStatus.PROSPECT }
                    val fullCount = bizClients.count { it.isCardFull }

                    val filteredClients = remember(bizClients, providerStatusFilter, providerSearch) {
                        bizClients.filter { c ->
                            val matchesFilter = when (providerStatusFilter) {
                                "ACTIFS" -> c.status == WasteClientStatus.CLIENT_ACTIF
                                "PROSPECTS" -> c.status == WasteClientStatus.PROSPECT
                                "REMPLIES" -> c.isCardFull
                                else -> true
                            }
                            val q = providerSearch.trim()
                            val matchesQuery = q.isBlank() ||
                                c.familyName.contains(q, ignoreCase = true) ||
                                c.contactName.contains(q, ignoreCase = true) ||
                                c.phone.contains(q, ignoreCase = true) ||
                                c.neighborhood.contains(q, ignoreCase = true) ||
                                c.id.contains(q, ignoreCase = true)
                            matchesFilter && matchesQuery
                        }
                    }

                    val isActiveBizUnlocked = activeBiz == null ||
                        activeBiz.pin.isBlank() ||
                        unlockedBizIds.contains(activeBiz.id)

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Business selector & creation bar
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                                tonalElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = activeBiz?.businessName ?: "Créer votre service de ramassage",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.5.sp
                                            )
                                            Text(
                                                text = activeBiz?.let {
                                                    "${it.managerName} • ${it.city} • Défaut: ${CurrencyFormatter.formatFCFA(it.defaultMonthlyFee)} / ${it.defaultPassagesPerCycle} coches"
                                                } ?: "Configurez votre entreprise puis ajoutez vos familles, clients et potentiels clients.",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (activeBiz != null && isActiveBizUnlocked) {
                                                IconButton(
                                                    onClick = {
                                                        editingBusiness = activeBiz
                                                        showCreateBusinessDialog = true
                                                    },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Modifier business", tint = Color(0xFF047857))
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = {
                                                    if (activeAgent == null) {
                                                        showDeployerRequiredDialog = true
                                                    } else {
                                                        editingBusiness = null
                                                        showCreateBusinessDialog = true
                                                    }
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFF047857).copy(alpha = 0.14f),
                                                    contentColor = Color(0xFF047857)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .testTag("btn_waste_create_business")
                                            ) {
                                                Icon(
                                                    if (activeAgent == null) Icons.Default.AdminPanelSettings else Icons.Default.AddBusiness,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (activeAgent == null) "Via Déployeur" else "Nouveau",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    if (activeBiz == null && activeAgent == null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = KonabOrange.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Création réservée aux Agents Déployeurs",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = KonabOrangeDark
                                                )
                                                Text(
                                                    text = "Toute nouvelle organisation de ramassage doit être créée par un Agent Déployeur authentifié.",
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Button(
                                                    onClick = {
                                                        onDismiss()
                                                        onRequestAgentLogin()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 46.dp)
                                                        .testTag("btn_waste_agent_gate_login")
                                                ) {
                                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(17.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Passer par un Agent Déployeur", fontWeight = FontWeight.Bold, maxLines = 1)
                                                }
                                            }
                                        }
                                    }

                                    if (businesses.size > 1) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            businesses.forEach { biz ->
                                                val selected = biz.id == activeBiz?.id
                                                FilterChip(
                                                    selected = selected,
                                                    onClick = {
                                                        onSelectBusiness(biz.id)
                                                        if (!unlockedBizIds.contains(biz.id) && biz.pin.isNotBlank()) {
                                                            pinGateBusiness = biz
                                                        }
                                                    },
                                                    label = { Text(biz.businessName, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                    }

                                    if (activeBiz != null && !isActiveBizUnlocked) {
                                        Button(
                                            onClick = { pinGateBusiness = activeBiz },
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_waste_unlock_biz")
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Déverrouiller mon espace Éboueur (PIN)", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (activeBiz != null && isActiveBizUnlocked) {
                            // KPI Strip
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    WasteKpiChip(
                                        label = "Familles actives",
                                        value = "$activeCount",
                                        color = Color(0xFF047857),
                                        modifier = Modifier.weight(1f)
                                    )
                                    WasteKpiChip(
                                        label = "Potentiels",
                                        value = "$prospectCount",
                                        color = KonabBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    WasteKpiChip(
                                        label = "Cartes remplies",
                                        value = "$fullCount",
                                        color = KonabOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Add Client/Family/Prospect + Search
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            editingClient = null
                                            showAddClientDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_waste_add_client")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Ajouter Famille / Client",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedTextField(
                                        value = providerSearch,
                                        onValueChange = { providerSearch = it },
                                        label = { Text("Rechercher famille, quartier, téléphone...") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = providerStatusFilter == "ALL",
                                            onClick = { providerStatusFilter = "ALL" },
                                            label = { Text("Tous (${bizClients.size})") }
                                        )
                                        FilterChip(
                                            selected = providerStatusFilter == "ACTIFS",
                                            onClick = { providerStatusFilter = "ACTIFS" },
                                            label = { Text("Familles ($activeCount)") }
                                        )
                                        FilterChip(
                                            selected = providerStatusFilter == "PROSPECTS",
                                            onClick = { providerStatusFilter = "PROSPECTS" },
                                            label = { Text("Potentiels ($prospectCount)") }
                                        )
                                        FilterChip(
                                            selected = providerStatusFilter == "REMPLIES",
                                            onClick = { providerStatusFilter = "REMPLIES" },
                                            label = { Text("À facturer ($fullCount)") }
                                        )
                                    }
                                }
                            }

                            if (filteredClients.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = konabCardBorder(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Groups,
                                                contentDescription = null,
                                                tint = Color(0xFF047857),
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Text(
                                                text = "Aucune famille ou potentiel client dans cette vue",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Ajoutez une famille pour générer automatiquement son profil et sa carte de passage !",
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredClients, key = { it.id }) { client ->
                                    ProviderWasteClientCard(
                                        client = client,
                                        onCheckPickup = {
                                            onCheckPickup(
                                                client,
                                                activeBiz.managerName,
                                                "Ramassage validé sur place"
                                            )
                                            Toast.makeText(
                                                context,
                                                "Passage coché pour ${client.familyName} (${client.currentCheckedCount + 1}/${client.targetPassages})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onUncheckLast = { onUncheckLastPickup(client) },
                                        onSendWhatsAppBilling = {
                                            onMarkBillingNotified(client)
                                            openWhatsAppForPhone(
                                                context = context,
                                                phone = client.phone,
                                                message = client.buildBillingWhatsAppMessage()
                                            )
                                        },
                                        onRecordPayment = { paymentTargetClient = client },
                                        onViewProfileAndCard = { selectedClientForCardModal = client },
                                        onEditClient = {
                                            editingClient = client
                                            showAddClientDialog = true
                                        },
                                        onConvertProspectToClient = {
                                            onSaveClient(
                                                activeBiz,
                                                client.familyName,
                                                client.contactName,
                                                client.phone,
                                                client.neighborhood,
                                                WasteClientStatus.CLIENT_ACTIF,
                                                client.pickupDaysLabel,
                                                client.monthlyFee,
                                                client.targetPassages,
                                                client,
                                                client.photoUri
                                            ) {}
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeployerRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showDeployerRequiredDialog = false },
            icon = { Icon(Icons.Default.Badge, contentDescription = null, tint = KonabOrange) },
            title = { Text("Agent Déployeur Requis", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Selon les règles Konab POS, la création et la configuration de toute nouvelle organisation (Poubelles, Couture, Restaurant, Tontine) doivent passer par un Agent Déployeur connecté."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeployerRequiredDialog = false
                        onDismiss()
                        onRequestAgentLogin()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabOrange)
                ) {
                    Text("Connexion Agent Déployeur", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeployerRequiredDialog = false }) { Text("Fermer") }
            }
        )
    }

    // Create/Edit Waste Business Dialog
    if (showCreateBusinessDialog) {
        WasteBusinessFormDialog(
            initial = editingBusiness,
            onDismiss = {
                showCreateBusinessDialog = false
                editingBusiness = null
            },
            onSave = { name, manager, phone, city, pin, fee, passages, photoUri ->
                onSaveBusiness(name, manager, phone, city, pin, fee, passages, editingBusiness?.id, photoUri) { saved ->
                    unlockedBizIds = unlockedBizIds + saved.id
                    showCreateBusinessDialog = false
                    editingBusiness = null
                    Toast.makeText(context, "Business « ${saved.businessName} » enregistré !", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Business PIN Gate Dialog
    pinGateBusiness?.let { biz ->
        var pinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinGateBusiness = null },
            title = { Text("Accès Éboueur • ${biz.businessName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entrez le code PIN de l'entreprise :", fontSize = 13.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = "" },
                        label = { Text("Code PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError.isNotBlank()) {
                        Text(pinError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.trim() == biz.pin.trim()) {
                            unlockedBizIds = unlockedBizIds + biz.id
                            pinGateBusiness = null
                        } else {
                            pinError = "Code PIN incorrect"
                        }
                    }
                ) {
                    Text("Déverrouiller")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinGateBusiness = null }) { Text("Annuler") }
            }
        )
    }

    // Add / Edit Household / Client / Prospect Dialog
    if (showAddClientDialog && activeBiz != null) {
        WasteClientFormDialog(
            business = activeBiz,
            initial = editingClient,
            onDismiss = {
                showAddClientDialog = false
                editingClient = null
            },
            onSave = { famName, contact, phone, hood, status, days, fee, targetPassages, photoUri ->
                onSaveClient(
                    activeBiz,
                    famName,
                    contact,
                    phone,
                    hood,
                    status,
                    days,
                    fee,
                    targetPassages,
                    editingClient,
                    photoUri
                ) { created ->
                    showAddClientDialog = false
                    editingClient = null
                    selectedClientForCardModal = created
                    Toast.makeText(
                        context,
                        "Profil & Carte générés pour ${created.familyName} !",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // Detail Modal: Profile + Digital Passage Card Grid + Exact Timestamps + Activity Card + QR
    selectedClientForCardModal?.let { cardClient ->
        val liveClient = clients.firstOrNull { it.id == cardClient.id } ?: cardClient
        WasteClientDetailDialog(
            client = liveClient,
            isCollectorView = selectedTab == 1,
            onPhotoSelected = { newPhoto ->
                onUpdateClientPhoto(liveClient, newPhoto)
            },
            onCheckPickup = {
                onCheckPickup(liveClient, activeBiz?.managerName ?: "Équipe collecte", "Passage validé")
            },
            onSendWhatsAppBilling = {
                onMarkBillingNotified(liveClient)
                openWhatsAppForPhone(context, liveClient.phone, liveClient.buildBillingWhatsAppMessage())
            },
            onRecordPayment = {
                paymentTargetClient = liveClient
            },
            onDeleteClient = {
                onDeleteClient(liveClient.id)
                selectedClientForCardModal = null
            },
            onDismiss = { selectedClientForCardModal = null }
        )
    }

    // Payment Confirmation Dialog
    paymentTargetClient?.let { payClient ->
        var amountStr by remember(payClient.id) { mutableStateOf(payClient.monthlyFee.toString()) }
        var noteStr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentTargetClient = null },
            title = { Text("Valider Paiement • ${payClient.familyName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ce règlement enregistre les ${payClient.currentCheckedCount} passages actuels dans l'historique officiel et ouvre une nouvelle carte vierge de ${payClient.targetPassages} passages.",
                        fontSize = 12.5.sp
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { v -> amountStr = v.filter { it.isDigit() } },
                        label = { Text("Montant payé (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteStr,
                        onValueChange = { noteStr = it },
                        label = { Text("Note (Mobile Money, Espèces...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toLongOrNull() ?: payClient.monthlyFee
                        onRecordPayment(payClient, amt, noteStr)
                        paymentTargetClient = null
                        Toast.makeText(context, "Paiement enregistré & nouvelle carte activée !", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text("Confirmer Paiement")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentTargetClient = null }) { Text("Annuler") }
            }
        )
    }

    if (showScanner) {
        RealBarcodeScannerModal(
            title = "Scanner Carte Poubelles",
            subtitle = "Visez le QR code remis par votre service de collecte",
            onDismiss = { showScanner = false },
            onBarcodeScanned = { scanned ->
                showScanner = false
                if (!onScanWasteQr(scanned)) {
                    Toast.makeText(context, "QR non reconnu pour le portail Poubelles.", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun WasteKpiChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontFamily = OutfitFontFamily, fontWeight = FontWeight.Black, fontSize = 18.sp, color = color)
            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProviderWasteClientCard(
    client: WasteHouseholdClient,
    onCheckPickup: () -> Unit,
    onUncheckLast: () -> Unit,
    onSendWhatsAppBilling: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewProfileAndCard: () -> Unit,
    onEditClient: () -> Unit,
    onConvertProspectToClient: () -> Unit
) {
    val accent = when {
        client.isCardFull -> KonabOrange
        client.status == WasteClientStatus.PROSPECT -> KonabBlue
        else -> Color(0xFF047857)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (client.isCardFull) 1.6.dp else 1.dp, accent.copy(alpha = if (client.isCardFull) 0.7f else 0.3f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = client.familyName,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = if (client.isCardFull) "CARTE REMPLIE" else client.status.label,
                                color = accent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = listOfNotNull(
                            client.contactName.takeIf { it.isNotBlank() },
                            client.neighborhood.takeIf { it.isNotBlank() },
                            client.phone.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { "Code: ${client.id}" },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Jours: ${client.pickupDaysLabel} • ${CurrencyFormatter.formatFCFA(client.monthlyFee)} / cycle",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }

                IconButton(
                    onClick = onEditClient,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Visual check grid
            WastePassageMiniGrid(
                checkedCount = client.currentCheckedCount,
                targetPassages = client.targetPassages,
                accentColor = accent
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCheckPickup,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_waste_check_${client.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Cocher", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onViewProfileAndCard,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Carte & QR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                if (client.passagesLogs.isNotEmpty()) {
                    IconButton(
                        onClick = onUncheckLast,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Annuler dernier passage", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (client.status == WasteClientStatus.PROSPECT) {
                OutlinedButton(
                    onClick = onConvertProspectToClient,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Convertir en Famille Cliente", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }

            if (client.isCardFull) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendWhatsAppBilling,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_waste_whatsapp_${client.id}")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("WhatsApp", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = onRecordPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_waste_pay_${client.id}")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Encaisser", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    }
                }
            } else if (client.currentCheckedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onSendWhatsAppBilling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Message WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    TextButton(
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Encaisser cycle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KonabOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyWasteCardView(
    client: WasteHouseholdClient,
    onOpenFullDetails: () -> Unit,
    onContactCollectorWhatsApp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.4.dp, Color(0xFF047857).copy(alpha = 0.45f)),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFullDetails() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.businessName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = client.familyName,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Jours de passage : ${client.pickupDaysLabel} • Code : ${client.id}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (client.isCardFull) KonabOrange else Color(0xFF047857)
                ) {
                    Text(
                        text = "${client.currentCheckedCount} / ${client.targetPassages} coches",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            WastePassageMiniGrid(
                checkedCount = client.currentCheckedCount,
                targetPassages = client.targetPassages,
                accentColor = if (client.isCardFull) KonabOrange else Color(0xFF047857)
            )

            if (client.isCardFull) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KonabOrange.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = KonabOrange)
                        Text(
                            text = "Votre carte est remplie ! Mensualité due : ${CurrencyFormatter.formatFCFA(client.monthlyFee)}.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KonabOrangeDark
                        )
                    }
                }
            }

            if (client.passagesLogs.isNotEmpty()) {
                Text(
                    text = "Passages effectués (${client.passagesLogs.size}) :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    client.passagesLogs.takeLast(5).reversed().forEachIndexed { idx, log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✓ Passage #${client.passagesLogs.size - idx} • ${log.collectorName}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = log.formattedDateTime(),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenFullDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Historique complet", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                OutlinedButton(
                    onClick = onContactCollectorWhatsApp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF25D366))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
fun WastePassageMiniGrid(
    checkedCount: Int,
    targetPassages: Int,
    accentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Carte de passage",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$checkedCount / $targetPassages coches",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
        val rows = ((targetPassages + 7) / 8).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val start = r * 8 + 1
                    val end = minOf((r + 1) * 8, targetPassages)
                    for (slot in start..end) {
                        val isChecked = slot <= checkedCount
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isChecked) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isChecked) "✓" else "$slot",
                                color = if (isChecked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    val missing = 8 - (end - start + 1)
                    repeat(missing) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteBusinessFormDialog(
    initial: WasteBusinessProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Long, Int, String) -> Unit
) {
    var bizName by remember { mutableStateOf(initial?.businessName ?: "") }
    var managerName by remember { mutableStateOf(initial?.managerName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "Conakry") }
    var pin by remember { mutableStateOf(initial?.pin ?: "1234") }
    var feeStr by remember { mutableStateOf((initial?.defaultMonthlyFee ?: 2500L).toString()) }
    var passagesStr by remember { mutableStateOf((initial?.defaultPassagesPerCycle ?: 8).toString()) }
    var photoUri by remember { mutableStateOf(initial?.photoUri ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Créer mon Business Salubrité" else "Modifier Business", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityPhotoPickerRow(
                    title = "Photo / Logo de l'organisation",
                    subtitle = "Affiché sur les cartes de passage Salubrité",
                    holderName = bizName.ifBlank { "Salubrité" },
                    photoUri = photoUri,
                    keyHint = "waste_biz",
                    accentColor = Color(0xFF047857),
                    onPhotoSelected = { photoUri = it }
                )
                OutlinedTextField(
                    value = bizName,
                    onValueChange = { bizName = it },
                    label = { Text("Nom de l'entreprise *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Nom du responsable") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone / WhatsApp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Ville / Secteur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = feeStr,
                        onValueChange = { v -> feeStr = v.filter { it.isDigit() } },
                        label = { Text("Tarif (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = passagesStr,
                        onValueChange = { v -> passagesStr = v.filter { it.isDigit() } },
                        label = { Text("Passages/carte") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { p -> pin = p.filter { it.isDigit() }.take(6) },
                    label = { Text("Code PIN Éboueur (4 chiffres)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bizName.isNotBlank()) {
                        onSave(
                            bizName,
                            managerName,
                            phone,
                            city,
                            pin,
                            feeStr.toLongOrNull() ?: 2500L,
                            (passagesStr.toIntOrNull() ?: 8).coerceIn(1, 31),
                            photoUri
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun WasteClientFormDialog(
    business: WasteBusinessProfile,
    initial: WasteHouseholdClient?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, WasteClientStatus, String, Long, Int, String) -> Unit
) {
    var familyName by remember { mutableStateOf(initial?.familyName ?: "") }
    var contactName by remember { mutableStateOf(initial?.contactName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var neighborhood by remember { mutableStateOf(initial?.neighborhood ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: WasteClientStatus.CLIENT_ACTIF) }
    var daysLabel by remember { mutableStateOf(initial?.pickupDaysLabel ?: "Lun • Jeu") }
    var feeStr by remember { mutableStateOf((initial?.monthlyFee ?: business.defaultMonthlyFee).toString()) }
    var passagesStr by remember { mutableStateOf((initial?.targetPassages ?: business.defaultPassagesPerCycle).toString()) }
    var photoUri by remember { mutableStateOf(initial?.photoUri ?: "") }

    val quickDays = listOf("Lun • Jeu", "Mar • Ven", "Mer • Sam", "Lun • Mer • Ven", "Tous les jours")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) "Nouvelle Famille / Client" else "Modifier Profil Famille",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityPhotoPickerRow(
                    title = "Photo de la famille / domicile",
                    subtitle = "Apparaît sur la carte QR Salubrité",
                    holderName = familyName.ifBlank { "Famille" },
                    photoUri = photoUri,
                    keyHint = "waste_fam",
                    accentColor = Color(0xFF047857),
                    onPhotoSelected = { photoUri = it }
                )
                Text(
                    text = "Type d'enregistrement :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(WasteClientStatus.CLIENT_ACTIF, WasteClientStatus.PROSPECT).forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text("Nom de la famille ou client *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Chef de famille / Interlocuteur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone WhatsApp *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it },
                    label = { Text("Quartier / Rue / Repère") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = daysLabel,
                    onValueChange = { daysLabel = it },
                    label = { Text("Jours de passage") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickDays.forEach { preset ->
                        AssistChip(
                            onClick = { daysLabel = preset },
                            label = { Text(preset, fontSize = 11.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passagesStr,
                        onValueChange = { v -> passagesStr = v.filter { it.isDigit() } },
                        label = { Text("Passages/carte") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = feeStr,
                        onValueChange = { v -> feeStr = v.filter { it.isDigit() } },
                        label = { Text("Tarif (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (familyName.isNotBlank()) {
                        onSave(
                            familyName,
                            contactName,
                            phone,
                            neighborhood,
                            status,
                            daysLabel,
                            feeStr.toLongOrNull() ?: business.defaultMonthlyFee,
                            (passagesStr.toIntOrNull() ?: business.defaultPassagesPerCycle).coerceIn(1, 31),
                            photoUri
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
            ) {
                Text("Générer Profil & Carte")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun WasteClientDetailDialog(
    client: WasteHouseholdClient,
    isCollectorView: Boolean,
    onPhotoSelected: (String) -> Unit,
    onCheckPickup: () -> Unit,
    onSendWhatsAppBilling: () -> Unit,
    onRecordPayment: () -> Unit,
    onDeleteClient: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF065F46))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = client.familyName,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Code: ${client.id} • ${client.pickupDaysLabel}",
                            fontSize = 11.5.sp,
                            color = Color(0xFFD1FAE5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActivityDigitalCard(
                        theme = CardActivityTheme.SALUBRITE_POUBELLES,
                        holderName = client.familyName,
                        codeId = client.id,
                        organizationTitle = client.businessName,
                        phone = client.phone,
                        zoneOrAddress = listOfNotNull(
                            client.neighborhood.takeIf { it.isNotBlank() },
                            client.pickupDaysLabel.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        qrPayload = client.buildQrUri(),
                        photoUri = client.photoUri,
                        statusBadge = if (client.isCardFull) "Carte Remplie • À régler" else "Carte Salubrité Active",
                        statsLine = "${client.currentCheckedCount}/${client.targetPassages} coches • ${CurrencyFormatter.formatFCFA(client.monthlyFee)}",
                        onPhotoSelected = onPhotoSelected
                    )

                    ActivityCardExportBar(
                        theme = CardActivityTheme.SALUBRITE_POUBELLES,
                        holderName = client.familyName,
                        codeId = client.id,
                        organizationTitle = client.businessName,
                        phone = client.phone,
                        zoneOrAddress = client.neighborhood.ifBlank { client.pickupDaysLabel },
                        qrPayload = client.buildQrUri(),
                        photoUri = client.photoUri,
                        statusBadge = "Carte Salubrité Active",
                        statsLine = "${client.currentCheckedCount}/${client.targetPassages} coches"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = konabCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Carte Numérique (${client.currentCheckedCount} / ${client.targetPassages} coches)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            WastePassageMiniGrid(
                                checkedCount = client.currentCheckedCount,
                                targetPassages = client.targetPassages,
                                accentColor = if (client.isCardFull) KonabOrange else Color(0xFF047857)
                            )
                            Text(
                                text = "Mensualité : ${CurrencyFormatter.formatFCFA(client.monthlyFee)} • Total passages cumulés : ${client.totalHistoricalPickups}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Exact pickup timestamps
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = konabCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Historique exact des passages (${client.passagesLogs.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (client.passagesLogs.isEmpty()) {
                                Text(
                                    text = "Aucun passage coché sur ce cycle.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                client.passagesLogs.reversed().forEachIndexed { idx, log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFECFDF5))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Passage #${client.passagesLogs.size - idx} ✓",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF065F46)
                                            )
                                            Text(
                                                text = log.collectorName,
                                                fontSize = 11.sp,
                                                color = Color(0xFF047857)
                                            )
                                        }
                                        Text(
                                            text = log.formattedDateTime(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (client.paymentHistory.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = konabCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Paiements & Cycles précédents (${client.paymentHistory.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                client.paymentHistory.forEach { pay ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "${CurrencyFormatter.formatFCFA(pay.amount)} (${pay.passagesCompleted}/${pay.cycleTarget} passages)",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.5.sp
                                            )
                                            Text(
                                                text = pay.formattedPaidDate(),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (isCollectorView) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onCheckPickup,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cocher +1", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onSendWhatsAppBilling,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("WhatsApp Facture", fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRecordPayment,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Encaisser", fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = onDeleteClient,
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Text("Supprimer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun openWhatsAppForPhone(context: Context, phone: String, message: String) {
    val cleanDigits = phone.filter { it.isDigit() }
    val withCountry = when {
        cleanDigits.length == 8 -> "226$cleanDigits"
        cleanDigits.isNotBlank() -> cleanDigits
        else -> ""
    }
    try {
        val url = if (withCountry.isNotBlank()) {
            "https://wa.me/$withCountry?text=${Uri.encode(message)}"
        } else {
            "https://wa.me/?text=${Uri.encode(message)}"
        }
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(waIntent)
    } catch (_: Exception) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Envoyer le message"))
        } catch (_: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir WhatsApp ou Partage.", Toast.LENGTH_SHORT).show()
        }
    }
}
