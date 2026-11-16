package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.PublicUserAccount
import com.example.ui.theme.*

/**
 * Portail d'Identification Utilisateur Public (Créer un compte / Se connecter / Modifier profil / Espace Services).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicUserPortalModal(
    activeUser: PublicUserAccount?,
    isSyncing: Boolean,
    wasteCardsCount: Int,
    tontineCardsCount: Int,
    availableShopNames: List<String> = emptyList(),
    onRegisterUser: (
        fullName: String,
        phone: String,
        city: String,
        neighborhood: String,
        pin: String,
        photoUri: String,
        preferredActivity: String,
        isSeekingAccompaniment: Boolean,
        accompanimentDetails: String,
        isShopMember: Boolean,
        memberShopName: String,
        memberShopRole: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onUpdateProfile: (
        fullName: String,
        phone: String,
        city: String,
        neighborhood: String,
        newPin: String,
        photoUri: String?,
        preferredActivity: String?,
        isSeekingAccompaniment: Boolean,
        accompanimentDetails: String,
        isShopMember: Boolean,
        memberShopName: String,
        memberShopRole: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onLoginUser: (
        phoneOrId: String,
        pin: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit,
    onUpdatePhoto: (String) -> Unit,
    onUpdatePreferredTheme: (CardActivityTheme) -> Unit,
    onLogoutUser: () -> Unit,
    onOpenRestaurants: () -> Unit,
    onOpenCouture: () -> Unit,
    onOpenWastePortal: () -> Unit,
    onOpenTontinePortal: () -> Unit,
    onOpenScanner: () -> Unit,
    onDismiss: () -> Unit
) {
    var authTab by remember { mutableIntStateOf(0) } // 0: Créer compte, 1: Se connecter

    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regCity by remember { mutableStateOf("") }
    var regNeighborhood by remember { mutableStateOf("") }
    var regPin by remember { mutableStateOf("") }
    var regPhotoUri by remember { mutableStateOf("") }
    var regTheme by remember { mutableStateOf(CardActivityTheme.UTILISATEUR) }
    var regIsSeekingAccompaniment by remember { mutableStateOf(false) }
    var regAccompanimentDetails by remember { mutableStateOf("") }
    var regIsShopMember by remember { mutableStateOf(false) }
    var regMemberShopName by remember { mutableStateOf("") }
    var regMemberShopRole by remember { mutableStateOf("") }

    var loginPhoneOrId by remember { mutableStateOf("") }
    var loginPin by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.93f)
                .widthIn(max = 640.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_public_user_portal"),
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
                                listOf(Color(0xFF0F172A), KonabBlueDark, KonabOrange)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (activeUser != null) Modifier.clickable { showEditProfileDialog = true }
                                else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Espace Utilisateur",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeUser != null) "Mon Profil Utilisateur" else "Compte Utilisateur",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeUser != null) {
                                    listOfNotNull(
                                        activeUser.fullName.takeIf { it.isNotBlank() },
                                        activeUser.phone.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ").ifBlank { "Touchez pour modifier votre profil" }
                                } else {
                                    "Créez votre compte et accédez aux services"
                                },
                                fontSize = 11.5.sp,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (activeUser != null) {
                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_header_edit_profile")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier le profil", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_close_user_portal")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                if (activeUser == null) {
                    TabRow(
                        selectedTabIndex = authTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = KonabBlue
                    ) {
                        Tab(
                            selected = authTab == 0,
                            onClick = {
                                authTab = 0
                                feedbackMsg = null
                            },
                            text = {
                                Text(
                                    text = "Créer un compte",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        Tab(
                            selected = authTab == 1,
                            onClick = {
                                authTab = 1
                                feedbackMsg = null
                            },
                            text = {
                                Text(
                                    text = "Se connecter",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        feedbackMsg?.let { (ok, msg) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (ok) KonabSuccess.copy(alpha = 0.14f) else KonabRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (ok) KonabSuccess else KonabRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (ok) KonabSuccess else KonabRed,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        if (authTab == 0) {
                            ActivityPhotoPickerRow(
                                title = "Photo de profil",
                                subtitle = "Affichée sur votre carte QR personnelle",
                                holderName = regName.ifBlank { "Mon Profil" },
                                photoUri = regPhotoUri,
                                keyHint = "user_reg",
                                accentColor = KonabOrange,
                                onPhotoSelected = { regPhotoUri = it }
                            )

                            OutlinedTextField(
                                value = regName,
                                onValueChange = { regName = it },
                                label = { Text("Nom complet *") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_reg_name")
                            )

                            OutlinedTextField(
                                value = regPhone,
                                onValueChange = { regPhone = it },
                                label = { Text("Numéro de téléphone *") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_reg_phone")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = regCity,
                                    onValueChange = { regCity = it },
                                    label = { Text("Ville") },
                                    placeholder = { Text("Votre ville") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = regNeighborhood,
                                    onValueChange = { regNeighborhood = it },
                                    label = { Text("Quartier") },
                                    placeholder = { Text("Votre quartier") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = regPin,
                                onValueChange = { if (it.length <= 6) regPin = it },
                                label = { Text("Code PIN secret (4 à 6 chiffres) *") },
                                placeholder = { Text("Ex: 4820") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_reg_pin")
                            )

                            // Options facultatives : Accompagnement & Membre d'une boutique
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Accompagnement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                "Permettre aux gens / agents de vous accompagner",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = regIsSeekingAccompaniment,
                                            onCheckedChange = { regIsSeekingAccompaniment = it }
                                        )
                                    }
                                    if (regIsSeekingAccompaniment) {
                                        OutlinedTextField(
                                            value = regAccompanimentDetails,
                                            onValueChange = { regAccompanimentDetails = it },
                                            label = { Text("Domaine ou besoin d'accompagnement") },
                                            placeholder = { Text("Ex: Ouvrir ma boutique, suivi stock...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 2
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Membre d'une boutique", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                "Indiquer à quelle boutique vous appartenez",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = regIsShopMember,
                                            onCheckedChange = { regIsShopMember = it }
                                        )
                                    }
                                    if (regIsShopMember) {
                                        OutlinedTextField(
                                            value = regMemberShopName,
                                            onValueChange = { regMemberShopName = it },
                                            label = { Text("Nom de la boutique") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (availableShopNames.isNotEmpty()) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                availableShopNames.take(5).forEach { shopName ->
                                                    FilterChip(
                                                        selected = regMemberShopName.equals(shopName, ignoreCase = true),
                                                        onClick = { regMemberShopName = shopName },
                                                        label = { Text(shopName, fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = regMemberShopRole,
                                            onValueChange = { regMemberShopRole = it },
                                            label = { Text("Statut / Rôle (ex: Membre, Caissier, Client)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Thème visuel de votre carte :",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CardActivityTheme.entries) { theme ->
                                    FilterChip(
                                        selected = regTheme == theme,
                                        onClick = { regTheme = theme },
                                        label = {
                                            Text(
                                                text = theme.shortBadge,
                                                maxLines = 1,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onRegisterUser(
                                        regName,
                                        regPhone,
                                        regCity,
                                        regNeighborhood,
                                        regPin,
                                        regPhotoUri,
                                        regTheme.name,
                                        regIsSeekingAccompaniment,
                                        regAccompanimentDetails,
                                        regIsShopMember,
                                        regMemberShopName,
                                        regMemberShopRole
                                    ) { ok, msg ->
                                        feedbackMsg = ok to msg
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabBlueDark),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("btn_user_register_submit")
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Créer mon compte & obtenir ma carte",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = loginPhoneOrId,
                                onValueChange = { loginPhoneOrId = it },
                                label = { Text("Téléphone ou ID Utilisateur *") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_login_phone")
                            )
                            OutlinedTextField(
                                value = loginPin,
                                onValueChange = { if (it.length <= 6) loginPin = it },
                                label = { Text("Code PIN *") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_login_pin")
                            )
                            Button(
                                onClick = {
                                    onLoginUser(loginPhoneOrId, loginPin) { ok, msg ->
                                        feedbackMsg = ok to msg
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("btn_user_login_submit")
                            ) {
                                Text(
                                    text = "Se connecter",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    val activeTheme = CardActivityTheme.fromRaw(activeUser.preferredActivity)
                    val userCityZone = listOf(activeUser.neighborhood.trim(), activeUser.city.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    val shopOrgLine = if (activeUser.isShopMember && activeUser.memberShopName.isNotBlank()) {
                        val rolePart = if (activeUser.memberShopRole.isNotBlank()) " • ${activeUser.memberShopRole}" else ""
                        "Boutique : ${activeUser.memberShopName}$rolePart"
                    } else ""
                    val cardStatusBadge = when {
                        activeUser.isSeekingAccompaniment -> "Accompagnement ouvert"
                        activeUser.isShopMember && activeUser.memberShopName.isNotBlank() -> "Membre boutique"
                        else -> "Profil actif"
                    }
                    val activeStatsParts = buildList {
                        if (wasteCardsCount > 0) add("Poubelles : $wasteCardsCount")
                        if (tontineCardsCount > 0) add("Tontines : $tontineCardsCount")
                    }.joinToString(" • ")

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        feedbackMsg?.let { (ok, msg) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (ok) KonabSuccess.copy(alpha = 0.14f) else KonabRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (ok) KonabSuccess else KonabRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (ok) KonabSuccess else KonabRed,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Carte numérique cliquable : un clic ouvre l'éditeur de profil complet
                        ActivityDigitalCard(
                            theme = activeTheme,
                            holderName = activeUser.fullName,
                            codeId = activeUser.id,
                            organizationTitle = shopOrgLine,
                            phone = activeUser.phone,
                            zoneOrAddress = userCityZone,
                            qrPayload = activeUser.buildQrUri(),
                            photoUri = activeUser.photoUri,
                            statusBadge = cardStatusBadge,
                            statsLine = activeStatsParts,
                            onPhotoSelected = onUpdatePhoto,
                            onCardClick = { showEditProfileDialog = true },
                            editHintLabel = "Modifier profil"
                        )

                        // Carte récapitulative des informations du profil (cliquable pour modifier)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.28f)),
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEditProfileDialog = true }
                                .testTag("card_user_profile_details")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Informations du profil",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showEditProfileDialog = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("btn_edit_public_profile")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Modifier", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                ProfileInfoLine(
                                    icon = Icons.Default.LocationOn,
                                    label = "Ville / Quartier",
                                    value = userCityZone.ifBlank { "Non renseignée (touchez pour définir)" },
                                    isMuted = userCityZone.isBlank()
                                )
                                ProfileInfoLine(
                                    icon = Icons.Default.Handshake,
                                    label = "Accompagnement",
                                    value = if (activeUser.isSeekingAccompaniment) {
                                        activeUser.accompanimentDetails.ifBlank { "Disponible pour être accompagné(e)" }
                                    } else {
                                        "Désactivé (touchez pour activer)"
                                    },
                                    isMuted = !activeUser.isSeekingAccompaniment,
                                    accent = KonabGreen
                                )
                                ProfileInfoLine(
                                    icon = Icons.Default.Storefront,
                                    label = "Boutique affiliée",
                                    value = if (activeUser.isShopMember && activeUser.memberShopName.isNotBlank()) {
                                        buildString {
                                            append(activeUser.memberShopName)
                                            if (activeUser.memberShopRole.isNotBlank()) append(" (${activeUser.memberShopRole})")
                                        }
                                    } else {
                                        "Aucune boutique indiquée"
                                    },
                                    isMuted = !(activeUser.isShopMember && activeUser.memberShopName.isNotBlank()),
                                    accent = KonabOrange
                                )
                            }
                        }

                        Text(
                            text = "Style de carte par activité :",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CardActivityTheme.entries) { theme ->
                                FilterChip(
                                    selected = activeTheme == theme,
                                    onClick = { onUpdatePreferredTheme(theme) },
                                    label = {
                                        Text(
                                            text = theme.shortBadge,
                                            maxLines = 1,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                )
                            }
                        }

                        ActivityCardExportBar(
                            theme = activeTheme,
                            holderName = activeUser.fullName,
                            codeId = activeUser.id,
                            organizationTitle = shopOrgLine,
                            phone = activeUser.phone,
                            zoneOrAddress = userCityZone,
                            qrPayload = activeUser.buildQrUri(),
                            photoUri = activeUser.photoUri,
                            statusBadge = cardStatusBadge,
                            statsLine = activeStatsParts
                        )

                        Text(
                            text = "VOS SERVICES UTILISATEUR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        UserServiceTile(
                            title = "Cantines & Restaurants",
                            subtitle = "Parcourir les menus du jour et passer commande en ligne ou par QR",
                            icon = Icons.Default.RestaurantMenu,
                            accent = KonabOrange,
                            testTag = "btn_user_open_restaurants",
                            onClick = {
                                onDismiss()
                                onOpenRestaurants()
                            }
                        )
                        UserServiceTile(
                            title = "Couture & Suivi Tenue",
                            subtitle = "Voir les catalogues, commander un modèle et suivre votre tenue",
                            icon = Icons.Default.Checkroom,
                            accent = Color(0xFF7C3AED),
                            testTag = "btn_user_open_couture",
                            onClick = {
                                onDismiss()
                                onOpenCouture()
                            }
                        )
                        UserServiceTile(
                            title = "Ma Carte Poubelle",
                            subtitle = "Vérifier le nombre de passages effectués et l'historique exact",
                            icon = Icons.Default.DeleteSweep,
                            accent = KonabGreen,
                            badge = if (wasteCardsCount > 0) "$wasteCardsCount" else null,
                            testTag = "btn_user_open_waste",
                            onClick = {
                                onDismiss()
                                onOpenWastePortal()
                            }
                        )
                        UserServiceTile(
                            title = "Mes Tontines 30 Jours",
                            subtitle = "Consulter vos pointages en temps réel et vos cartes soldées",
                            icon = Icons.Default.Savings,
                            accent = Color(0xFFD97706),
                            badge = if (tontineCardsCount > 0) "$tontineCardsCount" else null,
                            testTag = "btn_user_open_tontine",
                            onClick = {
                                onDismiss()
                                onOpenTontinePortal()
                            }
                        )
                        UserServiceTile(
                            title = "Scanner QR Universel",
                            subtitle = "Scanner une table de cantine/resto, couture, carte poubelle ou tontine",
                            icon = Icons.Default.QrCodeScanner,
                            accent = KonabBlue,
                            testTag = "btn_user_open_scanner",
                            onClick = {
                                onDismiss()
                                onOpenScanner()
                            }
                        )

                        OutlinedButton(
                            onClick = onLogoutUser,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabRed),
                            border = BorderStroke(1.dp, KonabRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_user_logout")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Déconnecter ce compte utilisateur", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showEditProfileDialog) {
                        EditPublicUserProfileDialog(
                            user = activeUser,
                            availableShopNames = availableShopNames,
                            isSaving = isSyncing,
                            onDismiss = { showEditProfileDialog = false },
                            onSave = { name, phone, city, neighborhood, newPin, photoUri, isSeekingAcc, accDetails, isShopMember, memberShopName, memberShopRole ->
                                onUpdateProfile(
                                    name,
                                    phone,
                                    city,
                                    neighborhood,
                                    newPin,
                                    photoUri,
                                    null,
                                    isSeekingAcc,
                                    accDetails,
                                    isShopMember,
                                    memberShopName,
                                    memberShopRole
                                ) { ok, msg ->
                                    feedbackMsg = ok to msg
                                    if (ok) showEditProfileDialog = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoLine(
    icon: ImageVector,
    label: String,
    value: String,
    isMuted: Boolean,
    accent: Color = KonabBlue
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontWeight = if (isMuted) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPublicUserProfileDialog(
    user: PublicUserAccount,
    availableShopNames: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        fullName: String,
        phone: String,
        city: String,
        neighborhood: String,
        newPin: String,
        photoUri: String,
        isSeekingAccompaniment: Boolean,
        accompanimentDetails: String,
        isShopMember: Boolean,
        memberShopName: String,
        memberShopRole: String
    ) -> Unit
) {
    var fullName by remember(user) { mutableStateOf(user.fullName) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    var city by remember(user) { mutableStateOf(user.city) }
    var neighborhood by remember(user) { mutableStateOf(user.neighborhood) }
    var newPin by remember { mutableStateOf("") }
    var photoUri by remember(user) { mutableStateOf(user.photoUri) }
    var isSeekingAccompaniment by remember(user) { mutableStateOf(user.isSeekingAccompaniment) }
    var accompanimentDetails by remember(user) { mutableStateOf(user.accompanimentDetails) }
    var isShopMember by remember(user) { mutableStateOf(user.isShopMember) }
    var memberShopName by remember(user) { mutableStateOf(user.memberShopName) }
    var memberShopRole by remember(user) { mutableStateOf(user.memberShopRole) }
    var localError by remember { mutableStateOf("") }

    val quickRoles = listOf("Membre", "Client fidèle", "Caissier(ère)", "Employé(e)", "Associé(e)", "Gérant(e)")
    val quickAccompanimentTopics = listOf(
        "Développer mon activité",
        "Accompagnement gestion caisse",
        "Trouver des partenaires",
        "Suivi & mentorat"
    )

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = KonabBlue)
                Column {
                    Text("Modifier mon profil", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = user.id,
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
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ActivityPhotoPickerRow(
                    title = "Photo de profil",
                    subtitle = "Apparaît sur votre carte",
                    holderName = fullName.ifBlank { "Profil" },
                    photoUri = photoUri,
                    keyHint = "edit_${user.id}",
                    accentColor = KonabOrange,
                    onPhotoSelected = { photoUri = it }
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; localError = "" },
                    label = { Text("Nom complet *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_profile_name")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; localError = "" },
                    label = { Text("Numéro de téléphone *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_profile_phone")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Ville") },
                        placeholder = { Text("Votre ville") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_edit_profile_city")
                    )
                    OutlinedTextField(
                        value = neighborhood,
                        onValueChange = { neighborhood = it },
                        label = { Text("Quartier") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Section Accompagnement
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KonabGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Accompagnement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Pour que des personnes ou agents puissent vous accompagner",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isSeekingAccompaniment,
                                onCheckedChange = { isSeekingAccompaniment = it },
                                modifier = Modifier.testTag("switch_edit_profile_accompaniment")
                            )
                        }
                        if (isSeekingAccompaniment) {
                            OutlinedTextField(
                                value = accompanimentDetails,
                                onValueChange = { accompanimentDetails = it },
                                label = { Text("Précisez votre besoin ou message") },
                                placeholder = { Text("Ex: Besoin d'accompagnement pour...") },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_profile_accompaniment_details")
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                quickAccompanimentTopics.forEach { topic ->
                                    FilterChip(
                                        selected = accompanimentDetails.equals(topic, ignoreCase = true),
                                        onClick = { accompanimentDetails = topic },
                                        label = { Text(topic, fontSize = 10.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Membre d'une boutique
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KonabOrange.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Membre d'une boutique", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Indiquer votre boutique et votre statut",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isShopMember,
                                onCheckedChange = { isShopMember = it },
                                modifier = Modifier.testTag("switch_edit_profile_shop_member")
                            )
                        }
                        if (isShopMember) {
                            OutlinedTextField(
                                value = memberShopName,
                                onValueChange = { memberShopName = it; localError = "" },
                                label = { Text("Nom de la boutique *") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_profile_shop_name")
                            )
                            if (availableShopNames.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    availableShopNames.take(6).forEach { sName ->
                                        FilterChip(
                                            selected = memberShopName.equals(sName, ignoreCase = true),
                                            onClick = { memberShopName = sName; localError = "" },
                                            label = { Text(sName, fontSize = 10.5.sp) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = memberShopRole,
                                onValueChange = { memberShopRole = it },
                                label = { Text("Rôle / Statut dans la boutique") },
                                placeholder = { Text("Ex: Membre, Client fidèle, Caissier...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_profile_shop_role")
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                quickRoles.forEach { roleLabel ->
                                    FilterChip(
                                        selected = memberShopRole.equals(roleLabel, ignoreCase = true),
                                        onClick = { memberShopRole = roleLabel },
                                        label = { Text(roleLabel, fontSize = 10.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6) newPin = it; localError = "" },
                    label = { Text("Nouveau code PIN (optionnel)") },
                    placeholder = { Text("Laisser vide pour conserver l'actuel") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (localError.isNotBlank()) {
                    Text(
                        text = localError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank()) {
                        localError = "Le nom complet est obligatoire."
                        return@Button
                    }
                    if (phone.filter { it.isDigit() }.length < 6) {
                        localError = "Numéro de téléphone invalide."
                        return@Button
                    }
                    if (isShopMember && memberShopName.isBlank()) {
                        localError = "Indiquez le nom de la boutique ou décochez l'option."
                        return@Button
                    }
                    if (newPin.isNotBlank() && newPin.trim().length < 4) {
                        localError = "Le PIN doit comporter au moins 4 chiffres."
                        return@Button
                    }
                    onSave(
                        fullName.trim(),
                        phone.trim(),
                        city.trim(),
                        neighborhood.trim(),
                        newPin.trim(),
                        photoUri,
                        isSeekingAccompaniment,
                        accompanimentDetails.trim(),
                        isShopMember,
                        memberShopName.trim(),
                        memberShopRole.trim()
                    )
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = KonabBlueDark),
                modifier = Modifier.testTag("btn_save_public_profile")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun UserServiceTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    badge: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(6.dp), color = accent) {
                            Text(
                                text = badge,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
