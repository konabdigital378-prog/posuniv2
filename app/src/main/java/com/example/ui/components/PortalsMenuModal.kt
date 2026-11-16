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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(KonabBlueDeep, KonabBlueDark, KonabBlue)
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KonabOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Menu des portails",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Menu des Portails",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Services métiers, cartes numériques & espaces connectés",
                                    fontSize = 11.5.sp,
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
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MON COMPTE UTILISATEUR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )

                    PortalMenuCard(
                        title = if (currentPublicUser != null) {
                            "Utilisateur : ${currentPublicUser.fullName}"
                        } else {
                            "Compte Utilisateur & Ma Carte"
                        },
                        subtitle = if (currentPublicUser != null) {
                            "ID ${currentPublicUser.id} • ${currentPublicUser.phone} • Accès Cantines, Restos, Couture, Poubelles & Tontine"
                        } else {
                            "Créez votre compte dans la base de données pour commander aux cantines & suivre vos cartes"
                        },
                        icon = if (currentPublicUser != null) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                        accentColor = if (currentPublicUser != null) KonabGreen else KonabBlue,
                        badge = if (currentPublicUser != null) "Connecté" else "Inscription",
                        testTag = "btn_menu_user_portal",
                        onClick = {
                            onDismiss()
                            onOpenUserPortal()
                        }
                    )

                    Text(
                        text = "SERVICES & PORTAILS MÉTIERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )

                    PortalMenuCard(
                        title = "Restaurants & Cantines (Menu Web)",
                        subtitle = "Voir les cantines et restaurants, commander à table par QR code en direct",
                        icon = Icons.Default.RestaurantMenu,
                        accentColor = KonabOrange,
                        badge = "Menu QR",
                        testTag = "btn_welcome_scan_restaurant_qr",
                        onClick = {
                            onDismiss()
                            onOpenRestaurants()
                        }
                    )

                    PortalMenuCard(
                        title = "Couture, Modèles & Suivi Tenue",
                        subtitle = "Choisissez un couturier, scannez son catalogue et suivez votre tenue",
                        icon = Icons.Default.Checkroom,
                        accentColor = Color(0xFF7C3AED),
                        badge = "Atelier",
                        testTag = "btn_welcome_couture_portal",
                        onClick = {
                            onDismiss()
                            onOpenCouture()
                        }
                    )

                    PortalMenuCard(
                        title = "Gestion Poubelles & Salubrité",
                        subtitle = "Éboueurs Pro (clients, prospects, cartes) & suivi des passages Famille",
                        icon = Icons.Default.DeleteSweep,
                        accentColor = KonabGreen,
                        badge = if (wasteClientsCount > 0) "$wasteClientsCount familles" else "Nouveau",
                        testTag = "btn_menu_waste_portal",
                        onClick = {
                            onDismiss()
                            onOpenWastePortal()
                        }
                    )

                    PortalMenuCard(
                        title = "Tontine Numérique • Carte 30 Jours",
                        subtitle = "Carnet digital 30 jours en temps réel, suivi Mes Tontines & alerte solde",
                        icon = Icons.Default.Savings,
                        accentColor = Color(0xFFD97706),
                        badge = if (tontineCardsCount > 0) "$tontineCardsCount cartes" else "30 Jours",
                        testTag = "btn_menu_tontine_portal",
                        onClick = {
                            onDismiss()
                            onOpenTontinePortal()
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ACCÈS RAPIDES & GESTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )

                    PortalMenuCard(
                        title = "Scanner QR Universel",
                        subtitle = "Table Restaurant, Catalogue Couture, Carte Poubelle ou Carte Tontine",
                        icon = Icons.Default.QrCodeScanner,
                        accentColor = KonabBlue,
                        badge = "Caméra / PNG",
                        testTag = "btn_menu_universal_qr",
                        onClick = {
                            onDismiss()
                            onOpenScanner()
                        }
                    )

                    PortalMenuCard(
                        title = "Connexion Gérant • Multiboutique",
                        subtitle = "Accéder à votre boutique partout (catalogue, logo, reçus et ventes)",
                        icon = Icons.Default.Domain,
                        accentColor = KonabBlueDark,
                        badge = if (connectedShopsCount > 0) "$connectedShopsCount boutique(s)" else "Cloud",
                        testTag = "btn_welcome_multishop_login",
                        onClick = {
                            onDismiss()
                            onOpenMultiShop()
                        }
                    )

                    // Agent section inside Menu
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, KonabOrange.copy(alpha = 0.45f)),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(KonabOrange.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = "Espace Agent Déployeur",
                                        tint = KonabOrange,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (activeAgent != null) {
                                            "Agent Actif : ${activeAgent.name}"
                                        } else {
                                            "Espace Agent Déployeur PRO"
                                        },
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (activeAgent != null) {
                                            "ID: ${activeAgent.id} • ${activeAgent.zone}"
                                        } else {
                                            "Déployer, configurer et synchroniser les boutiques clients"
                                        },
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Text("Mon Profil, Ma Carte & Boutiques", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                            .weight(1.05f)
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .konabPressScale(pressedScale = 0.985f)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
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
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
