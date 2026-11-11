package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    shopProfile: ShopProfileEntity?,
    onActivate: (code: String, tier: String) -> Boolean,
    onNavigateToAgentPortal: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTier by remember { mutableStateOf(shopProfile?.subscriptionTier ?: "Premium") }
    var activationCodeInput by remember { mutableStateOf("") }
    var showActivationDialog by remember { mutableStateOf(false) }

    val tiers = listOf(
        Triple("Gratuit", "0 FCFA", "10 produits max, fonctions de base caisse"),
        Triple("Standard", "5 000 FCFA / mois", "Produits illimités, reçus WhatsApp, gestion caisse"),
        Triple("Professionnel", "10 000 FCFA / mois", "Rapports avancés, dettes clients, gestion stocks et alertes"),
        Triple("Entreprise", "25 000 FCFA / mois", "Multi-utilisateurs, sauvegarde automatique, support prioritaire 24/7")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abonnements Konab", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (onNavigateToAgentPortal != null) {
                        IconButton(onClick = onNavigateToAgentPortal) {
                            Icon(Icons.Default.Badge, contentDescription = "Portail Agent", tint = KonabOrange)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Official Konab Deposit Notice
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = KonabGoldContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(KonabGoldDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("Numéro officiel de dépôt Konab :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("65413799", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = KonabGoldDark)
                        Text("Pour toute activation ou renouvellement de licence.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (onNavigateToAgentPortal != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF06245E)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFA43A).copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAgentPortal() }
                        .testTag("card_agent_portal_shortcut")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF06245E),
                                        Color(0xFF073B9A)
                                    )
                                )
                            )
                            .padding(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    color = Color(0xFFFF7900),
                                    border = BorderStroke(2.dp, Color(0xFFFFA43A))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Badge, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Espace Agent Déployeur", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
                                        Surface(
                                            color = Color(0xFFFF7900),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "OFFICIEL",
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Portail réservé aux techniciens et commerciaux agréés",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFFFFA43A), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Security Policy Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = KonabRedContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = KonabRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Règle de sécurité : Ne jamais considérer une capture d'écran comme une preuve automatique de paiement validé.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = KonabRed
                    )
                }
            }

            // Tiers List
            Text("Formules disponibles :", fontWeight = FontWeight.Bold, fontSize = 15.sp)

            tiers.forEach { (name, price, desc) ->
                val isCurrent = (shopProfile?.subscriptionTier ?: "Premium") == name
                val isSelected = selectedTier == name

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) KonabGreenLight else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) KonabGreen else KonabBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedTier = name }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isSelected) KonabGreenDark else MaterialTheme.colorScheme.onSurface)

                            if (isCurrent) {
                                Surface(
                                    color = KonabGreen,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Actif sur cet appareil", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(price, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = KonabGreen)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Super Admin Manual Activation Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Espace Super Administrateur Konab", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "L'activation des formules est manuelle et réservée au Super Administrateur Konab après validation effective du dépôt au 65413799.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showActivationDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_super_admin_activate")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activer avec une clé de licence", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // License Activation Dialog
    if (showActivationDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showActivationDialog = false },
            title = { Text("Validation Super Admin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Formule à activer : $selectedTier", fontWeight = FontWeight.Bold)
                    Text("Saisissez la clé d'activation fournie par Konab après contrôle au 65413799 :", fontSize = 12.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        placeholder = { Text("Ex: KONAB-2026-PRO ou 65413799") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onActivate(code, selectedTier)
                        if (success) {
                            Toast.makeText(context, "Formule $selectedTier activée avec succès !", Toast.LENGTH_LONG).show()
                            showActivationDialog = false
                        } else {
                            Toast.makeText(context, "Clé de licence invalide", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Activer la formule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
