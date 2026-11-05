package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.*

@Composable
fun AdminPanelModal(
    shopProfile: ShopProfileEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("konab_admin_prefs", Context.MODE_PRIVATE) }
    
    val defaultAppUrl = "https://ais-dev-qqnfvfeyqhirj2x6aqt2qe-265193538612.europe-west1.run.app/admin.html"
    val vercelDeployUrl = "https://vercel.com/new/clone?repository-url=https://github.com/konabdigital378-prog/POS-UNIV"

    var customVercelUrl by remember {
        mutableStateOf(prefs.getString("custom_vercel_url", "") ?: "")
    }
    var isEditingVercelUrl by remember { mutableStateOf(false) }

    val activeAdminUrl = if (customVercelUrl.isNotBlank()) customVercelUrl else defaultAppUrl

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("dialog_admin_panel_web"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E293B),
                                    Color(0xFF065F46)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = KonabGold.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "INDÉPENDANT DU MOBILE • CLOUD & VERCEL",
                                    color = KonabGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(KonabGreen, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LaptopMac,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Panel Admin Web & PC",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Supervision & pilotage sans le terminal de caisse",
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // Content Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Introduction text
                    Text(
                        text = "Le Panel Admin permet au gérant de superviser les ventes, gérer les stocks, relancer les débiteurs et clôturer les caisses depuis n'importe quel ordinateur, tablette ou smartphone sans dépendre de l'appareil de caisse.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // URL Card (Active Admin URL)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (customVercelUrl.isNotBlank()) "URL Production Vercel :" else "URL d'accès actuelle :",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = if (customVercelUrl.isNotBlank()) Color(0xFF0F172A) else KonabGreenLight,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (customVercelUrl.isNotBlank()) "VERCEL EN LIGNE" else "DEV PREVIEW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (customVercelUrl.isNotBlank()) Color.White else KonabGreenDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeAdminUrl,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KonabGreenDark,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Konab Admin URL", activeAdminUrl))
                                            Toast.makeText(context, "Lien copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copier",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Quick Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeAdminUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Aucun navigateur web détecté", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ouvrir dans le navigateur", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Portail Admin Konab POS pour la boutique ${shopProfile?.name ?: "Boutique"} :\n$activeAdminUrl\n\nAccédez à la gestion des stocks, ventes, caisses et statistiques depuis n'importe quel ordinateur."
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Partager le lien du Panel Admin"))
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // VERCEL DEPLOYMENT SECTION CARD
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Déploiement Vercel (1-Clic)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Hébergement gratuit & HTTPS mondial pour votre panel",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Text(
                                text = "Le fichier de configuration vercel.json est déjà inclus à la racine. Cliquez ci-dessous pour déployer automatiquement le panel sur Vercel à partir du dépôt GitHub konabdigital378-prog/POS-UNIV :",
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(vercelDeployUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur d'ouverture du navigateur", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A))
                            ) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0F172A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Lancer le Déploiement sur Vercel", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }

                            // Custom Vercel Domain configuration
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Votre domaine Vercel personnalisé :",
                                        fontSize = 10.5.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    TextButton(
                                        onClick = { isEditingVercelUrl = !isEditingVercelUrl },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = if (isEditingVercelUrl) "Fermer" else "Modifier",
                                            fontSize = 10.5.sp,
                                            color = KonabGold,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isEditingVercelUrl) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customVercelUrl,
                                            onValueChange = { customVercelUrl = it },
                                            placeholder = { Text("https://pos-univ.vercel.app", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = KonabGold,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        Button(
                                            onClick = {
                                                prefs.edit().putString("custom_vercel_url", customVercelUrl.trim()).apply()
                                                isEditingVercelUrl = false
                                                Toast.makeText(context, "URL Vercel enregistrée !", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabGold, contentColor = Color(0xFF0F172A))
                                        ) {
                                            Text("Sauver", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Features highlight list
                    Text(
                        text = "Fonctionnalités incluses dans le Panel Web :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AdminFeatureRow(
                        icon = Icons.Default.Dashboard,
                        title = "Tableau de Bord Exécutif & KPI",
                        desc = "Suivi en direct du chiffre d'affaires, marges bénéficiaires, tickets moyens et graphiques sur 7 et 30 jours."
                    )

                    AdminFeatureRow(
                        icon = Icons.Default.Inventory2,
                        title = "Gestion des Stocks & Produits",
                        desc = "Ajout, modification, seuils d'alertes, codes-barres, marges en % et export complet en tableur CSV."
                    )

                    AdminFeatureRow(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "Journal des Ventes & Reçus",
                        desc = "Consultation de toutes les transactions, réimpression de factures A4 et tickets de caisse."
                    )

                    AdminFeatureRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Contrôle de Caisse & Clôtures",
                        desc = "Suivi des mouvements d'espèces (entrées / dépenses), solde théorique et détection automatique des écarts."
                    )

                    AdminFeatureRow(
                        icon = Icons.Default.People,
                        title = "Carnet Clients & Dettes (Crédits)",
                        desc = "Gestion des créances impayées, encaissements d'échéances et bouton de relance directe par WhatsApp."
                    )

                    AdminFeatureRow(
                        icon = Icons.Default.CloudSync,
                        title = "Synchronisation Supabase (11 tables)",
                        desc = "Toutes les modifications faites sur le web ou sur l'application mobile se répercutent automatiquement."
                    )
                }

                // Footer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Fermer", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(KonabGreenLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KonabGreenDark,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
