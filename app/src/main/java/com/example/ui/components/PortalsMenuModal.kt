package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.PublicUserAccount
import com.example.ui.screens.AgentUser
import com.example.ui.theme.*

@Composable
fun PortalsMenuModal(
    activeAgent: AgentUser?,
    currentPublicUser: PublicUserAccount? = null,
    connectedShopsCount: Int,
    wasteClientsCount: Int,
    tontineCardsCount: Int,
    onOpenUserPortal: () -> Unit = {},
    onOpenRestaurants: () -> Unit,
    onOpenCouture: () -> Unit,
    onOpenWastePortal: () -> Unit,
    onOpenTontinePortal: () -> Unit,
    onOpenMultiShop: () -> Unit,
    onOpenScanner: () -> Unit,
    onRegisterAgent: () -> Unit,
    onLoginAgent: () -> Unit,
    onOpenAgentDeployments: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .heightIn(max = 780.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(KonabBlueDeep, KonabBlueDark, KonabBlue)
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(KonabOrange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Menu des portails",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Menu Konab POS",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Portails services, carte & gestion boutique",
                                        fontSize = 11.sp,
                                        color = Color(0xFFD6E4FA),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_close_portals_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer le menu",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Mon compte utilisateur
                        PortalMenuCard(
                            title = if (currentPublicUser != null) {
                                currentPublicUser.fullName.ifBlank { "Mon compte" }
                            } else {
                                "Mon Compte & Ma Carte"
                            },
                            subtitle = if (currentPublicUser != null) {
                                listOfNotNull(
                                    currentPublicUser.id.takeIf { it.isNotBlank() },
                                    currentPublicUser.phone.takeIf { it.isNotBlank() },
                                    currentPublicUser.city.takeIf { it.isNotBlank() }
                                ).joinToString(" • ").ifBlank { "Profil actif" }
                            } else {
                                "Gérer votre profil, carte digitale et commandes"
                            },
                            icon = if (currentPublicUser != null) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                            accentColor = if (currentPublicUser != null) KonabGreen else KonabBlue,
                            badge = if (currentPublicUser != null) "Profil actif" else "Connexion",
                            testTag = "btn_menu_user_portal",
                            onClick = {
                                onDismiss()
                                onOpenUserPortal()
                            }
                        )

                        Text(
                            text = "SERVICES & PORTAILS MÉTIERS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        // Clean 2x2 equal-height grid for Services
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PortalGridTile(
                                title = "Restaurants & Cantines",
                                subtitle = "Menu QR & commande",
                                icon = Icons.Default.RestaurantMenu,
                                accentColor = KonabOrange,
                                badge = "Menu QR",
                                testTag = "btn_welcome_scan_restaurant_qr",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onDismiss()
                                    onOpenRestaurants()
                                }
                            )
                            PortalGridTile(
                                title = "Couture & Tenues",
                                subtitle = "Modèles & suivi",
                                icon = Icons.Default.Checkroom,
                                accentColor = Color(0xFF7C3AED),
                                badge = "Atelier",
                                testTag = "btn_welcome_couture_portal",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onDismiss()
                                    onOpenCouture()
                                }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PortalGridTile(
                                title = "Poubelles & Salubrité",
                                subtitle = "Collectes & familles",
                                icon = Icons.Default.DeleteSweep,
                                accentColor = KonabGreen,
                                badge = if (wasteClientsCount > 0) "$wasteClientsCount" else "Portail",
                                testTag = "btn_menu_waste_portal",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onDismiss()
                                    onOpenWastePortal()
                                }
                            )
                            PortalGridTile(
                                title = "Tontine Numérique",
                                subtitle = "Carnet 30 jours",
                                icon = Icons.Default.Savings,
                                accentColor = Color(0xFFD97706),
                                badge = if (tontineCardsCount > 0) "$tontineCardsCount" else "30 jours",
                                testTag = "btn_menu_tontine_portal",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onDismiss()
                                    onOpenTontinePortal()
                                }
                            )
                        }

                        Text(
                            text = "ACCÈS RAPIDES",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        PortalMenuCard(
                            title = "Scanner QR Universel",
                            subtitle = "Table resto, couture, carte poubelles ou tontine",
                            icon = Icons.Default.QrCodeScanner,
                            accentColor = KonabBlue,
                            badge = "Scanner",
                            testTag = "btn_menu_universal_qr",
                            onClick = {
                                onDismiss()
                                onOpenScanner()
                            }
                        )

                        PortalMenuCard(
                            title = "Multiboutique Gérant",
                            subtitle = "Accéder à votre boutique à distance (ventes & stock)",
                            icon = Icons.Default.Domain,
                            accentColor = KonabBlueDark,
                            badge = if (connectedShopsCount > 0) "$connectedShopsCount" else "Cloud",
                            testTag = "btn_welcome_multishop_login",
                            onClick = {
                                onDismiss()
                                onOpenMultiShop()
                            }
                        )

                        // Agent section inside Menu
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.45f)),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(KonabOrange.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = "Espace Agent Déployeur",
                                            tint = KonabOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (activeAgent != null) {
                                                "Agent : ${activeAgent.name}"
                                            } else {
                                                "Espace Agent Déployeur"
                                            },
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (activeAgent != null) {
                                                listOfNotNull(activeAgent.id, activeAgent.zone.takeIf { it.isNotBlank() }).joinToString(" • ")
                                            } else {
                                                "Déployer et gérer les boutiques partenaires"
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (activeAgent != null) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenAgentDeployments()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlueDark),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_menu_agent_deployments")
                                    ) {
                                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Mon Profil, Ma Carte & Boutiques", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onRegisterAgent()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_welcome_register_agent")
                                        ) {
                                            Text(
                                                "Créer profil",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onLoginAgent()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_welcome_login_agent")
                                        ) {
                                            Text(
                                                "Se connecter",
                                                fontWeight = FontWeight.SemiBold,
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
            }
        }
    }
}

@Composable
private fun PortalGridTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        tonalElevation = 1.dp,
        modifier = modifier
            .konabPressScale(pressedScale = 0.975f)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = badge,
                        color = accentColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PortalMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .konabPressScale(pressedScale = 0.985f)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
