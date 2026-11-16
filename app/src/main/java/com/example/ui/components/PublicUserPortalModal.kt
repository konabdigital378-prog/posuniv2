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
 * Portail d'Identification Utilisateur Public (Créer un compte / Se connecter / Espace Services & Carte).
 * Les comptes créés sont immédiatement enregistrés dans Supabase (`app_users`) et donnent accès aux
 * Cantines & Restaurants, Couture, Cartes Poubelles et Cartes Tontine 30 jours.
 */
@Composable
fun PublicUserPortalModal(
    activeUser: PublicUserAccount?,
    isSyncing: Boolean,
    wasteCardsCount: Int,
    tontineCardsCount: Int,
    onRegisterUser: (
        fullName: String,
        phone: String,
        city: String,
        neighborhood: String,
        pin: String,
        photoUri: String,
        preferredActivity: String,
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
    var regCity by remember { mutableStateOf("Conakry") }
    var regNeighborhood by remember { mutableStateOf("") }
    var regPin by remember { mutableStateOf("0000") }
    var regPhotoUri by remember { mutableStateOf("") }
    var regTheme by remember { mutableStateOf(CardActivityTheme.UTILISATEUR) }

    var loginPhoneOrId by remember { mutableStateOf("") }
    var loginPin by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                        modifier = Modifier.fillMaxWidth(),
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
                                text = if (activeUser != null) "Mon Espace Utilisateur" else "Compte Utilisateur",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeUser != null) {
                                    "${activeUser.fullName} • ${activeUser.phone}"
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
                                title = "Photo de profil (Carte Conakry Web)",
                                subtitle = "Affichée sur votre carte QR personnelle",
                                holderName = regName.ifBlank { "Utilisateur" },
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
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = regNeighborhood,
                                    onValueChange = { regNeighborhood = it },
                                    label = { Text("Quartier") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = regPin,
                                onValueChange = { if (it.length <= 6) regPin = it },
                                label = { Text("Code PIN secret (4 chiffres) *") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_user_reg_pin")
                            )

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
                                        regTheme.name
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ActivityDigitalCard(
                            theme = activeTheme,
                            holderName = activeUser.fullName,
                            codeId = activeUser.id,
                            organizationTitle = "Membre Conakry Web • ${activeUser.city.ifBlank { "Conakry" }}",
                            phone = activeUser.phone,
                            zoneOrAddress = activeUser.neighborhood.ifBlank { activeUser.city },
                            qrPayload = activeUser.buildQrUri(),
                            photoUri = activeUser.photoUri,
                            statusBadge = "Compte Utilisateur Actif (Supabase Sync)",
                            statsLine = "Poubelles : $wasteCardsCount • Tontines : $tontineCardsCount",
                            onPhotoSelected = onUpdatePhoto
                        )

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
                            organizationTitle = "Membre Conakry Web",
                            phone = activeUser.phone,
                            zoneOrAddress = activeUser.neighborhood.ifBlank { activeUser.city },
                            qrPayload = activeUser.buildQrUri(),
                            photoUri = activeUser.photoUri,
                            statusBadge = "Compte Utilisateur Actif",
                            statsLine = "Poubelles : $wasteCardsCount • Tontines : $tontineCardsCount"
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
                }
            }
        }
    }
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
