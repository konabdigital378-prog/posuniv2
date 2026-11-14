package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.ConnectedShopSession
import com.example.ui.theme.*
import com.example.util.ShopLogoAvatar

/**
 * Modal Multiboutique :
 * - Permet au gérant de se connecter partout où il est (à la boutique ou à distance)
 *   via le nom de la boutique ou son numéro de téléphone + code PIN gérant.
 * - Affiche la liste des boutiques déjà connectées pour basculer en 1 clic et actualiser produits & ventes.
 */
@Composable
fun MultiShopManagerModal(
    connectedShops: List<ConnectedShopSession>,
    activeCloudShopId: Long,
    isCurrentShopConfigured: Boolean,
    onConnectWithCredentials: (identifier: String, pin: String, onDone: (Boolean, String) -> Unit) -> Unit,
    onSwitchShop: (ConnectedShopSession, onDone: (Boolean, String) -> Unit) -> Unit,
    onRefreshActiveShop: ((onDone: (Boolean, String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf("") }

    fun submitCredentials() {
        val cleanId = identifier.trim()
        val cleanPin = pin.trim()
        when {
            cleanId.isBlank() -> {
                errorMessage = "Entrez le nom de la boutique ou le téléphone du gérant."
            }
            cleanPin.length < 4 -> {
                errorMessage = "Le code PIN gérant doit contenir au moins 4 chiffres."
            }
            else -> {
                errorMessage = ""
                statusBanner = ""
                isLoading = true
                onConnectWithCredentials(cleanId, cleanPin) { ok, msg ->
                    isLoading = false
                    if (ok) {
                        onDismiss()
                    } else {
                        errorMessage = msg
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_multishop_manager"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Royal Blue Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(KonabBlueDeep, KonabBlueDark, KonabBlue)
                            )
                        )
                        .konabHeroShimmer()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Domain,
                                contentDescription = "Multiboutique",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Système Multiboutique",
                                fontFamily = OutfitFontFamily,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Accès produits & ventes sur place et à distance",
                                color = Color(0xFFD6E4FA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = { if (!isLoading) onDismiss() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1 : Boutiques déjà connectées sur l'appareil (Bascule rapide)
                    if (connectedShops.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mes boutiques (${connectedShops.size})",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isCurrentShopConfigured && onRefreshActiveShop != null) {
                                TextButton(
                                    onClick = {
                                        if (!isLoading) {
                                            isLoading = true
                                            errorMessage = ""
                                            onRefreshActiveShop { ok, msg ->
                                                isLoading = false
                                                if (ok) statusBanner = msg else errorMessage = msg
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("btn_multishop_refresh_active")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Actualiser", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            connectedShops.forEach { session ->
                                val isActive = isCurrentShopConfigured &&
                                    activeCloudShopId > 0L &&
                                    session.cloudShopId == activeCloudShopId

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isActive) KonabBlueContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive) KonabBlue.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isLoading && !isActive) {
                                            isLoading = true
                                            errorMessage = ""
                                            onSwitchShop(session) { ok, msg ->
                                                isLoading = false
                                                if (ok) onDismiss() else errorMessage = msg
                                            }
                                        }
                                        .testTag("card_connected_shop_${session.cloudShopId}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ShopLogoAvatar(
                                            logoUri = session.logoUri,
                                            size = 42.dp,
                                            shape = CircleShape,
                                            contentDescription = session.shopName
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.shopName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${session.businessType} • ${session.city.ifBlank { session.phone }}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Gérant : ${session.adminName} (${session.adminPhone.ifBlank { session.phone }})",
                                                fontSize = 10.5.sp,
                                                color = KonabBlue,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = KonabGreen
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = {
                                                    isLoading = true
                                                    errorMessage = ""
                                                    onSwitchShop(session) { ok, msg ->
                                                        isLoading = false
                                                        if (ok) onDismiss() else errorMessage = msg
                                                    }
                                                },
                                                enabled = !isLoading,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 40.dp)
                                                    .testTag("btn_switch_shop_${session.cloudShopId}")
                                            ) {
                                                Text("Ouvrir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }

                    // Section 2 : Connexion Gérant à Distance / Ajouter une boutique
                    Text(
                        text = if (connectedShops.isEmpty()) {
                            "Connexion Gérant (À distance ou en boutique)"
                        } else {
                            "Connecter une autre boutique (Identifiants gérant)"
                        },
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = KonabOrange.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = KonabOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Saisissez le nom de la boutique ou le téléphone et le code PIN (Gérant, Cuisine, Serveur ou Caissier) pour synchroniser produits, commandes et ventes.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = {
                            identifier = it
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        label = { Text("Nom de la boutique ou Téléphone *") },
                        placeholder = { Text("Ex: Resto Kaba ou 65413799") },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_multishop_identifier")
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            pin = input.filter { it.isDigit() }.take(8)
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        label = { Text("Code PIN Gérant ou Équipe *") },
                        placeholder = { Text("Votre code secret (ex: 1234)") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { pinVisible = !pinVisible },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (pinVisible) "Masquer" else "Afficher"
                                )
                            }
                        },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (!isLoading) submitCredentials() }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_multishop_pin")
                    )

                    if (statusBanner.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KonabGreen.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(18.dp))
                                Text(statusBanner, fontSize = 12.sp, color = KonabGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (errorMessage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = KonabRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = KonabRed, modifier = Modifier.size(18.dp))
                                Text(errorMessage, fontSize = 12.sp, color = KonabRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Button(
                        onClick = { submitCredentials() },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_multishop_connect")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connexion & Chargement...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accéder à la boutique", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
