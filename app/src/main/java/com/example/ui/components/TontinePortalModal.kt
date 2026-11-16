package com.example.ui.components

import android.graphics.Bitmap
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
fun TontinePortalModal(
    businesses: List<TontineBusinessProfile>,
    groups: List<TontineGroup>,
    cards: List<TontineMemberCard>,
    activeBusinessId: String?,
    isSyncing: Boolean,
    memberQuery: String,
    pendingQrTarget: ParsedTontineQr?,
    activeAgent: AgentUser? = null,
    currentPublicUser: PublicUserAccount? = null,
    onRequestAgentLogin: () -> Unit = {},
    onRefreshCloud: () -> Unit,
    onSelectBusiness: (String?) -> Unit,
    onSaveBusiness: (
        businessName: String,
        organizerName: String,
        phone: String,
        city: String,
        pin: String,
        existingId: String?,
        photoUri: String,
        onDone: (TontineBusinessProfile) -> Unit
    ) -> Unit,
    onSaveGroup: (
        business: TontineBusinessProfile,
        title: String,
        dailyAmount: Long,
        totalDays: Int,
        frequencyLabel: String,
        existingId: String?,
        onDone: (TontineGroup) -> Unit
    ) -> Unit,
    onSaveMemberCard: (
        group: TontineGroup,
        memberName: String,
        memberPhone: String,
        payoutTurnNumber: Int,
        existingCard: TontineMemberCard?,
        photoUri: String,
        onDone: (TontineMemberCard) -> Unit
    ) -> Unit,
    onUpdateCardPhoto: (TontineMemberCard, String) -> Unit = { _, _ -> },
    onToggleDayCheck: (TontineMemberCard, dayNumber: Int, checkedBy: String) -> Unit,
    onCheckNextDay: (TontineMemberCard, checkedBy: String) -> Unit,
    onValidateSettled: (TontineMemberCard, customMessage: String) -> Unit,
    onMarkPaid: (TontineMemberCard) -> Unit,
    onRestartNewCycle: (TontineMemberCard) -> Unit,
    onDeleteCard: (String) -> Unit,
    onUpdateMemberQuery: (String) -> Unit,
    onScanTontineQr: (String) -> Boolean,
    onDismissQrTarget: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Mes Tontines (Membre), 1: Business Tontine
    var memberQueryInput by remember(memberQuery, currentPublicUser?.phone) {
        mutableStateOf(memberQuery.ifBlank { currentPublicUser?.phone.orEmpty() })
    }
    var unlockedBizIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }

    var showCreateBusinessDialog by remember { mutableStateOf(false) }
    var showDeployerRequiredDialog by remember { mutableStateOf(false) }
    var editingBusiness by remember { mutableStateOf<TontineBusinessProfile?>(null) }
    var pinGateBusiness by remember { mutableStateOf<TontineBusinessProfile?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var selectedCardDetail by remember { mutableStateOf<TontineMemberCard?>(null) }
    var validateSettledTarget by remember { mutableStateOf<TontineMemberCard?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val activeBiz = remember(businesses, activeBusinessId) {
        businesses.firstOrNull { it.id == activeBusinessId } ?: businesses.firstOrNull()
    }
    val bizGroups = remember(groups, activeBiz) {
        if (activeBiz == null) emptyList() else groups.filter { it.businessId == activeBiz.id }
    }
    val activeGroup = remember(bizGroups, selectedGroupId) {
        bizGroups.firstOrNull { it.id == selectedGroupId } ?: bizGroups.firstOrNull()
    }

    LaunchedEffect(pendingQrTarget, cards) {
        val target = pendingQrTarget ?: return@LaunchedEffect
        if (target.cardId.isNotBlank()) {
            memberQueryInput = target.cardId
            onUpdateMemberQuery(target.cardId)
            val matched = cards.firstOrNull { it.id.equals(target.cardId, ignoreCase = true) }
            if (matched != null) {
                selectedCardDetail = matched
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
                                listOf(Color(0xFF92400E), Color(0xFFD97706), KonabOrange)
                            )
                        )
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
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = "Tontine Numérique",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Tontine Numérique • Carte 30 Jours",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Remplace le carnet papier • Pointage en temps réel & solde",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFEF3C7),
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
                                    .testTag("btn_tontine_refresh")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Actualiser", tint = Color.White)
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_tontine_close")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                            }
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFFD97706)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_tontine_member"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Mes Tontines",
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
                        modifier = Modifier.testTag("tab_tontine_business"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Business Tontine",
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
                    // TAB 0 : MES TONTINES (MEMBRE - TEMPS RÉEL)
                    // =====================================================
                    val memberCards = remember(cards, memberQuery, memberQueryInput) {
                        val q = memberQueryInput.ifBlank { memberQuery }
                        TontineStorage.findCardsForMember(cards, q)
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
                                        text = "Mes Tontines & Cartes 30 jours en temps réel",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Saisissez votre numéro de téléphone, nom ou code carte (TC-...) pour voir votre entreprise de tontine et vos pointages en direct.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = memberQueryInput,
                                        onValueChange = { memberQueryInput = it },
                                        label = { Text("Mon téléphone, nom ou Code carte TC-...") },
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("tf_tontine_member_query")
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onUpdateMemberQuery(memberQueryInput)
                                                onRefreshCloud()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_tontine_lookup_member")
                                        ) {
                                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                "Mes tontines",
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
                                                .testTag("btn_tontine_scan_qr")
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

                        if (memberCards.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = if (memberQueryInput.isBlank() && memberQuery.isBlank()) {
                                                "Zéro carnet papier, 100% transparent"
                                            } else {
                                                "Aucune tontine trouvée pour « ${memberQueryInput.ifBlank { memberQuery }} »"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Chaque case cochée par l'organisatrice apparaît ici en direct avec l'heure exacte et votre total épargné.",
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            color = Color(0xFF78350F)
                                        )
                                    }
                                }
                            }
                        } else {
                            items(memberCards, key = { it.id }) { card ->
                                MemberTontineSummaryCard(
                                    card = card,
                                    onOpenCard = { selectedCardDetail = card }
                                )
                            }
                        }
                    }
                } else {
                    // =====================================================
                    // TAB 1 : MON BUSINESS TONTINE (ORGANISATRICE)
                    // =====================================================
                    val isUnlocked = activeBiz == null ||
                        activeBiz.pin.isBlank() ||
                        unlockedBizIds.contains(activeBiz.id)
                    val groupCards = remember(cards, activeGroup) {
                        if (activeGroup == null) emptyList() else cards.filter { it.groupId == activeGroup.id }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Business Profile Header Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f)),
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
                                                text = activeBiz?.businessName ?: "Créer mon Business Tontine",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.5.sp
                                            )
                                            Text(
                                                text = activeBiz?.let {
                                                    "Organisatrice : ${it.organizerName} • ${it.city} • ${it.phone}"
                                                } ?: "Créez d'abord votre entreprise de tontine puis vos différents groupes 30 jours.",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (activeBiz != null && isUnlocked) {
                                                IconButton(
                                                    onClick = {
                                                        editingBusiness = activeBiz
                                                        showCreateBusinessDialog = true
                                                    },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "Modifier business", tint = Color(0xFFD97706))
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
                                                    containerColor = Color(0xFFD97706).copy(alpha = 0.14f),
                                                    contentColor = Color(0xFFB45309)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .testTag("btn_tontine_create_business")
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
                                                    text = "Toute nouvelle organisation Tontine doit être configurée via un Agent Déployeur authentifié.",
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
                                                        .testTag("btn_tontine_agent_gate_login")
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
                                                FilterChip(
                                                    selected = biz.id == activeBiz?.id,
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

                                    if (activeBiz != null && !isUnlocked) {
                                        Button(
                                            onClick = { pinGateBusiness = activeBiz },
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Déverrouiller mon espace Tontine (PIN)", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (activeBiz != null && isUnlocked) {
                            // Tontine Groups Selector + Add Group
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = konabCardBorder()
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
                                            Text(
                                                text = "Mes Tontines (${bizGroups.size})",
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp
                                            )
                                            Button(
                                                onClick = { showCreateGroupDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .testTag("btn_tontine_create_group")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Créer Tontine", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        if (bizGroups.isEmpty()) {
                                            Text(
                                                text = "Aucune tontine créée pour le moment. Créez votre première tontine (ex: 1 000 FCFA / jour sur 30 jours).",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                bizGroups.forEach { grp ->
                                                    val selected = grp.id == activeGroup?.id
                                                    FilterChip(
                                                        selected = selected,
                                                        onClick = { selectedGroupId = grp.id },
                                                        label = {
                                                            Text(
                                                                "${grp.title} (${CurrencyFormatter.formatFCFA(grp.dailyAmount)}/j)",
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (activeGroup != null) {
                                item {
                                    Button(
                                        onClick = { showAddMemberDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 50.dp)
                                            .testTag("btn_tontine_add_member")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Ajouter une participante (+ Carte 30 jours)",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                }

                                if (groupCards.isEmpty()) {
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
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("Aucune participante inscrite", fontWeight = FontWeight.Bold)
                                                Text(
                                                    "Ajoutez un membre pour lui générer automatiquement sa carte numérique de 30 jours.",
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(groupCards, key = { it.id }) { card ->
                                        OrganizerTontineMemberCard(
                                            card = card,
                                            onPointNextDay = {
                                                onCheckNextDay(card, activeBiz.organizerName)
                                                Toast.makeText(
                                                    context,
                                                    "Pointage enregistré pour ${card.memberName} !",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onOpen30DayGrid = { selectedCardDetail = card },
                                            onValidateAndNotify = { validateSettledTarget = card },
                                            onMarkPaid = {
                                                onMarkPaid(card)
                                                Toast.makeText(context, "Paiement marqué remis pour ${card.memberName}", Toast.LENGTH_SHORT).show()
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

    // Create/Edit Business Dialog
    if (showCreateBusinessDialog) {
        TontineBusinessFormDialog(
            initial = editingBusiness,
            onDismiss = {
                showCreateBusinessDialog = false
                editingBusiness = null
            },
            onSave = { bizName, organizer, phone, city, pin, photoUri ->
                onSaveBusiness(bizName, organizer, phone, city, pin, editingBusiness?.id, photoUri) { saved ->
                    unlockedBizIds = unlockedBizIds + saved.id
                    showCreateBusinessDialog = false
                    editingBusiness = null
                    Toast.makeText(context, "Business Tontine « ${saved.businessName} » enregistré !", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // PIN Gate
    pinGateBusiness?.let { biz ->
        var pinInput by remember { mutableStateOf("") }
        var pinErr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinGateBusiness = null },
            title = { Text("Accès Organisatrice • ${biz.businessName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Code PIN :", fontSize = 13.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinErr = "" },
                        label = { Text("Code PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinErr.isNotBlank()) Text(pinErr, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput.trim() == biz.pin.trim()) {
                        unlockedBizIds = unlockedBizIds + biz.id
                        pinGateBusiness = null
                    } else {
                        pinErr = "Code PIN incorrect"
                    }
                }) { Text("Déverrouiller") }
            },
            dismissButton = { TextButton(onClick = { pinGateBusiness = null }) { Text("Annuler") } }
        )
    }

    // Create Tontine Group Dialog
    if (showCreateGroupDialog && activeBiz != null) {
        TontineGroupFormDialog(
            business = activeBiz,
            onDismiss = { showCreateGroupDialog = false },
            onSave = { title, dailyAmt, days, freq ->
                onSaveGroup(activeBiz, title, dailyAmt, days, freq, null) { grp ->
                    selectedGroupId = grp.id
                    showCreateGroupDialog = false
                    Toast.makeText(context, "Tontine « ${grp.title} » créée (${grp.totalDays} jours) !", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Add Member -> Generates 30-day card
    if (showAddMemberDialog && activeGroup != null) {
        val nextTurn = cards.count { it.groupId == activeGroup.id } + 1
        TontineAddMemberDialog(
            group = activeGroup,
            defaultTurn = nextTurn,
            onDismiss = { showAddMemberDialog = false },
            onSave = { memberName, memberPhone, turn, photoUri ->
                onSaveMemberCard(activeGroup, memberName, memberPhone, turn, null, photoUri) { createdCard ->
                    showAddMemberDialog = false
                    selectedCardDetail = createdCard
                    Toast.makeText(context, "Carte 30 jours générée pour ${createdCard.memberName} !", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 30-Day Digital Carnet Card Detail Modal
    selectedCardDetail?.let { detail ->
        val liveCard = cards.firstOrNull { it.id == detail.id } ?: detail
        val isOrganizerUnlocked = selectedTab == 1 && (activeBiz == null || unlockedBizIds.contains(activeBiz.id) || activeBiz.pin.isBlank())
        Tontine30DayCardModal(
            card = liveCard,
            isOrganizerMode = isOrganizerUnlocked,
            onPhotoSelected = { newPhoto ->
                onUpdateCardPhoto(liveCard, newPhoto)
            },
            onToggleDay = { day ->
                onToggleDayCheck(liveCard, day, activeBiz?.organizerName ?: "Organisatrice")
            },
            onPointNext = {
                onCheckNextDay(liveCard, activeBiz?.organizerName ?: "Organisatrice")
            },
            onValidateSettled = { validateSettledTarget = liveCard },
            onMarkPaid = { onMarkPaid(liveCard) },
            onRestartCycle = { onRestartNewCycle(liveCard) },
            onDeleteCard = {
                onDeleteCard(liveCard.id)
                selectedCardDetail = null
            },
            onDismiss = { selectedCardDetail = null }
        )
    }

    // Validate Settled & Send Notification Message Dialog
    validateSettledTarget?.let { target ->
        var msgInput by remember(target.id) { mutableStateOf(target.buildSettledMessage()) }
        AlertDialog(
            onDismissRequest = { validateSettledTarget = null },
            title = { Text("Valider Soldée & Avertir", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ce message sera enregistré en direct dans « Mes Tontines » du membre ET envoyé par WhatsApp :",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = msgInput,
                        onValueChange = { msgInput = it },
                        label = { Text("Message au membre") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onValidateSettled(target, msgInput)
                        validateSettledTarget = null
                        openWhatsAppForPhone(context, target.memberPhone, target.buildWhatsAppSettledMessage())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("Valider & Envoyer WhatsApp")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onValidateSettled(target, msgInput)
                        validateSettledTarget = null
                        Toast.makeText(context, "Carte validée soldée dans l'application !", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Valider seulement") }
            }
        )
    }

    if (showScanner) {
        RealBarcodeScannerModal(
            title = "Scanner Carte Tontine 30j",
            subtitle = "Visez le QR code de votre carte de tontine",
            onDismiss = { showScanner = false },
            onBarcodeScanned = { scanned ->
                showScanner = false
                if (!onScanTontineQr(scanned)) {
                    Toast.makeText(context, "QR non reconnu pour le portail Tontine.", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun MemberTontineSummaryCard(
    card: TontineMemberCard,
    onOpenCard: () -> Unit
) {
    val accent = when (card.status) {
        TontineCardStatus.SOLDEE_A_PAYER -> KonabGreen
        TontineCardStatus.PAYEE -> KonabBlue
        TontineCardStatus.EN_COURS -> Color(0xFFD97706)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.4.dp, accent.copy(alpha = 0.55f)),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.businessName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    Text(
                        text = card.groupTitle,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "${card.memberName} • Mise ${CurrencyFormatter.formatFCFA(card.dailyAmount)}/jour • Tour #${card.payoutTurnNumber}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent
                ) {
                    Text(
                        text = "${card.checkedCount} / ${card.totalDays} j",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (card.checkedCount.toFloat() / card.totalDays.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.16f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pointé : ${CurrencyFormatter.formatFCFA(card.totalCollected)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Text(
                    text = "Objectif : ${CurrencyFormatter.formatFCFA(card.expectedTotal)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (card.status == TontineCardStatus.SOLDEE_A_PAYER) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KonabGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Celebration, contentDescription = null, tint = KonabGreen)
                        Text(
                            text = card.validationMessage.ifBlank { card.buildSettledMessage() },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            } else if (card.status == TontineCardStatus.PAYEE) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = KonabBlue.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Paiement remis (${card.status.label})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = KonabBlue,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = onOpenCard,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ouvrir ma Carte 30 jours en direct", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OrganizerTontineMemberCard(
    card: TontineMemberCard,
    onPointNextDay: () -> Unit,
    onOpen30DayGrid: () -> Unit,
    onValidateAndNotify: () -> Unit,
    onMarkPaid: () -> Unit
) {
    val accent = when {
        card.status == TontineCardStatus.SOLDEE_A_PAYER -> KonabGreen
        card.status == TontineCardStatus.PAYEE -> KonabBlue
        card.isFullCard -> KonabOrange
        else -> Color(0xFFD97706)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.4f)),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = card.memberName,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = card.status.label,
                                color = accent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${card.memberPhone} • Code: ${card.id} • Tour #${card.payoutTurnNumber}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${card.checkedCount} / ${card.totalDays} jours • Total: ${CurrencyFormatter.formatFCFA(card.totalCollected)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (card.checkedCount.toFloat() / card.totalDays.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (card.nextUncheckedDay() != null) {
                    Button(
                        onClick = onPointNextDay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_tontine_point_${card.id}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Jour ${card.nextUncheckedDay()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpen30DayGrid,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Carte 30j",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (card.isFullCard || card.status == TontineCardStatus.SOLDEE_A_PAYER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onValidateAndNotify,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_tontine_validate_${card.id}")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Soldée & Notifier",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (card.status != TontineCardStatus.PAYEE) {
                        Button(
                            onClick = onMarkPaid,
                            colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(0.95f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                "Payée",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tontine30DayCardModal(
    card: TontineMemberCard,
    isOrganizerMode: Boolean,
    onPhotoSelected: (String) -> Unit,
    onToggleDay: (Int) -> Unit,
    onPointNext: () -> Unit,
    onValidateSettled: () -> Unit,
    onMarkPaid: () -> Unit,
    onRestartCycle: () -> Unit,
    onDeleteCard: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF92400E))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${card.businessName} • ${card.groupTitle}",
                            fontSize = 11.5.sp,
                            color = Color(0xFFFEF3C7),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Carte 30j • ${card.memberName}",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
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
                    // Notification banner when settled
                    if (card.status != TontineCardStatus.EN_COURS && card.validationMessage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = KonabGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.2.dp, KonabGreen)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = KonabGreen)
                                Text(
                                    text = card.validationMessage,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    }

                    // 30-day Digital Carnet Grid (6 columns x 5 rows)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, Color(0xFFD97706).copy(alpha = 0.45f))
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
                                Text(
                                    text = "Carnet Numérique (${card.checkedCount}/${card.totalDays} jours)",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = CurrencyFormatter.formatFCFA(card.totalCollected),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFD97706),
                                    fontSize = 14.sp
                                )
                            }

                            val cols = 6
                            val rows = (card.totalDays + cols - 1) / cols
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (r in 0 until rows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (c in 0 until cols) {
                                            val day = r * cols + c + 1
                                            if (day <= card.totalDays) {
                                                val mark = card.markForDay(day)
                                                val checked = mark != null
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(46.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (checked) KonabGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (checked) KonabGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable(enabled = isOrganizerMode) {
                                                            onToggleDay(day)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = "J$day",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (checked) Color.White else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (checked) "✓" else "○",
                                                            fontSize = 11.sp,
                                                            color = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            if (isOrganizerMode && card.nextUncheckedDay() != null) {
                                Button(
                                    onClick = onPointNext,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pointer Jour ${card.nextUncheckedDay()} maintenant", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Carte d'activité officielle Conakry Web • Tontine 30 Jours (Photo + QR + Fond thématique + Export PNG)
                    val cardStatusBadge = "${card.status.label} • Tour #${card.payoutTurnNumber}"
                    val cardStatsLine = "${card.checkedCount}/${card.totalDays}j • ${CurrencyFormatter.formatFCFA(card.totalCollected)}"
                    ActivityDigitalCard(
                        theme = CardActivityTheme.TONTINE,
                        holderName = card.memberName,
                        codeId = card.id,
                        organizationTitle = "${card.businessName} • ${card.groupTitle}",
                        phone = card.memberPhone,
                        zoneOrAddress = "Mise ${CurrencyFormatter.formatFCFA(card.dailyAmount)}/jour",
                        qrPayload = card.buildQrUri(),
                        photoUri = card.photoUri,
                        statusBadge = cardStatusBadge,
                        statsLine = cardStatsLine,
                        onPhotoSelected = { onPhotoSelected(it) }
                    )
                    ActivityCardExportBar(
                        theme = CardActivityTheme.TONTINE,
                        holderName = card.memberName,
                        codeId = card.id,
                        organizationTitle = "${card.businessName} • ${card.groupTitle}",
                        phone = card.memberPhone,
                        zoneOrAddress = "Mise ${CurrencyFormatter.formatFCFA(card.dailyAmount)}/jour",
                        qrPayload = card.buildQrUri(),
                        photoUri = card.photoUri,
                        statusBadge = cardStatusBadge,
                        statsLine = cardStatsLine
                    )

                    // Live pointages journal
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
                                text = "Journal des pointages (${card.checkMarks.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (card.checkMarks.isEmpty()) {
                                Text("Aucun pointage enregistré pour le moment.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                card.checkMarks.reversed().forEach { mk ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFFFBEB))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Jour ${mk.dayNumber} ✓ • ${CurrencyFormatter.formatFCFA(mk.amount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF92400E)
                                        )
                                        Text(
                                            text = mk.formattedDateTime(),
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF78350F)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isOrganizerMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onValidateSettled,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    "Valider Soldée",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (card.status != TontineCardStatus.PAYEE) {
                                Button(
                                    onClick = onMarkPaid,
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        "Marquer Payée",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onRestartCycle,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Nouveau Cycle", fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            TextButton(
                                onClick = onDeleteCard,
                                modifier = Modifier
                                    .weight(0.7f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Supprimer", color = MaterialTheme.colorScheme.error, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TontineBusinessFormDialog(
    initial: TontineBusinessProfile?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var bizName by remember { mutableStateOf(initial?.businessName ?: "") }
    var organizer by remember { mutableStateOf(initial?.organizerName ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "Conakry") }
    var pin by remember { mutableStateOf(initial?.pin ?: "1234") }
    var photoUri by remember { mutableStateOf(initial?.photoUri ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Créer mon Business Tontine" else "Modifier Business Tontine", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityPhotoPickerRow(
                    title = "Logo / Photo du Business Tontine",
                    subtitle = "Apparaîtra sur vos cartes membres",
                    holderName = bizName.ifBlank { "Tontine" },
                    photoUri = photoUri,
                    keyHint = "tbiz_${initial?.id ?: "new"}",
                    accentColor = Color(0xFFD97706),
                    onPhotoSelected = { photoUri = it }
                )
                OutlinedTextField(
                    value = bizName,
                    onValueChange = { bizName = it },
                    label = { Text("Nom de votre Tontine / Business *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = organizer,
                    onValueChange = { organizer = it },
                    label = { Text("Nom de l'organisatrice / gérant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone WhatsApp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Ville / Quartier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { p -> pin = p.filter { it.isDigit() }.take(6) },
                    label = { Text("Code PIN gestion (4 chiffres)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (bizName.isNotBlank()) onSave(bizName, organizer, phone, city, pin, photoUri)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) { Text(if (initial == null) "Créer mon Business" else "Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun TontineGroupFormDialog(
    business: TontineBusinessProfile,
    onDismiss: () -> Unit,
    onSave: (String, Long, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dailyAmountStr by remember { mutableStateOf("1000") }
    var totalDaysStr by remember { mutableStateOf("30") }
    var freqLabel by remember { mutableStateOf("Journalier • Carte 30 jours") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle Tontine • ${business.businessName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nom de la tontine (ex: Tontine 1 000 FCFA) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyAmountStr,
                        onValueChange = { v -> dailyAmountStr = v.filter { it.isDigit() } },
                        label = { Text("Mise/case (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = totalDaysStr,
                        onValueChange = { v -> totalDaysStr = v.filter { it.isDigit() } },
                        label = { Text("Nb jours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.75f)
                    )
                }
                OutlinedTextField(
                    value = freqLabel,
                    onValueChange = { freqLabel = it },
                    label = { Text("Fréquence / Libellé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = dailyAmountStr.toLongOrNull() ?: 1000L
                    val days = (totalDaysStr.toIntOrNull() ?: 30).coerceIn(1, 31)
                    val finalTitle = title.ifBlank { "Tontine ${CurrencyFormatter.formatFCFA(amt)}" }
                    onSave(finalTitle, amt, days, freqLabel)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
            ) { Text("Créer Tontine") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun TontineAddMemberDialog(
    group: TontineGroup,
    defaultTurn: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String) -> Unit
) {
    var memberName by remember { mutableStateOf("") }
    var memberPhone by remember { mutableStateOf("") }
    var turnStr by remember { mutableStateOf(defaultTurn.toString()) }
    var photoUri by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter Membre • ${group.title}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Une carte numérique de ${group.totalDays} jours (${CurrencyFormatter.formatFCFA(group.dailyAmount)}/case) avec photo & QR sera générée.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActivityPhotoPickerRow(
                    title = "Photo de la participante",
                    subtitle = "Pour sa carte officielle Conakry Web",
                    holderName = memberName.ifBlank { "Membre" },
                    photoUri = photoUri,
                    keyHint = "tmember_${group.id}",
                    accentColor = Color(0xFFD97706),
                    onPhotoSelected = { photoUri = it }
                )
                OutlinedTextField(
                    value = memberName,
                    onValueChange = { memberName = it },
                    label = { Text("Nom complet du membre *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = memberPhone,
                    onValueChange = { memberPhone = it },
                    label = { Text("Téléphone WhatsApp *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = turnStr,
                    onValueChange = { v -> turnStr = v.filter { it.isDigit() } },
                    label = { Text("Numéro de tour / rang") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (memberName.isNotBlank() && memberPhone.isNotBlank()) {
                        onSave(memberName, memberPhone, (turnStr.toIntOrNull() ?: defaultTurn).coerceAtLeast(1), photoUri)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
            ) { Text("Générer Carte 30 jours") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
