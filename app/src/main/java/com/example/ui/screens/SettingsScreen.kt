package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*
import com.example.util.BackupRestoreHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    shopProfile: ShopProfileEntity?,
    onUpdateProfile: (ShopProfileEntity) -> Unit,
    onNavigate: (KonabScreen) -> Unit,
    onLockApp: () -> Unit,
    onBackup: (Context, onResult: (File?) -> Unit) -> Unit,
    onRestore: (String, onResult: (Boolean) -> Unit) -> Unit,
    onClearDemoData: (keepShopProfile: Boolean, onResult: () -> Unit) -> Unit = { _, _ -> },
    supabaseSyncState: com.example.data.remote.supabase.SupabaseSyncState? = null,
    onSyncWithSupabase: () -> Unit = {},
    onToggleAutoSync: (Boolean) -> Unit = {},
    onTestSupabase: (String?, String?) -> Unit = { _, _ -> },
    onSaveSupabaseCredentials: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showClearDemoDialog by remember { mutableStateOf(false) }
    var showFullResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showBluetoothPrinterModal by remember { mutableStateOf(false) }
    var showSupabaseSyncModal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Configuration & Paramètres",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Gestion globale du point de vente",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_settings")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLockApp,
                        modifier = Modifier.testTag("btn_lock_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Verrouiller",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // VITRINE & FIL D'IMAGES DU BURKINA FASO
            // ==========================================
            BurkinaFasoImageFilBanner()

            // Hero Brand & Merchant Profile Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_merchant_profile")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Gradient Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        KonabGreen,
                                        Color(0xFF047857),
                                        Color(0xFF065F46)
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(text = "🇧🇫", fontSize = 12.sp)
                                    Text(
                                        text = "Commerce Certifié Faso",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Subscription Badge
                            Surface(
                                color = KonabGoldContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = KonabGoldDark,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = shopProfile?.subscriptionTier ?: "Premium",
                                        color = KonabGoldDark,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Store Details & Avatar Overlap
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .offset(y = (-24).dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Store Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(3.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                                .background(KonabGreen, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Store Name & Type
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = shopProfile?.name ?: "Boutique Kaba",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${shopProfile?.businessType ?: "Boutique Générale"} • ${shopProfile?.city ?: "Ouagadougou"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Quick Edit Icon
                        FilledTonalIconButton(
                            onClick = { showEditInfoDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("btn_edit_profile_hero")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Modifier",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Status Chips Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .offset(y = (-10).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cloud Sync Status Pill (Automatique & Cliquable)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showSupabaseSyncModal = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (supabaseSyncState?.isConfigured == true) {
                                Color(0xFFECFDF5)
                            } else {
                                Color(0xFFFFFBEB)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (supabaseSyncState?.isConfigured == true) Color(0xFF10B981).copy(alpha = 0.3f)
                                else Color(0xFFF59E0B).copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (supabaseSyncState?.isConfigured == true) {
                                                if (supabaseSyncState.isAutoSyncEnabled) Color(0xFF10B981) else Color(0xFF0284C7)
                                            } else Color(0xFFF59E0B),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (supabaseSyncState?.isConfigured == true) {
                                        if (supabaseSyncState.isAutoSyncEnabled) "Auto-Sync Actif (Direct)" else "Cloud Connecté"
                                    } else "Mode Local",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (supabaseSyncState?.isConfigured == true) Color(0xFF047857) else Color(0xFFB45309),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Currency Pill
                        Surface(
                            modifier = Modifier.weight(0.7f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Devise : ${shopProfile?.currency ?: "FCFA"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // ==========================================
            // SECTION 1 : BOUTIQUE & IDENTITÉ COMMERCIALE
            // ==========================================
            SettingsSectionCard(title = "Boutique & Identité", icon = Icons.Default.Store) {
                SettingsModernRow(
                    icon = Icons.Default.Storefront,
                    iconTint = KonabGreen,
                    iconBg = KonabGreenLight,
                    title = "Informations de la boutique",
                    subtitle = "Nom, slogan, adresse et téléphone",
                    trailingValue = shopProfile?.name ?: "Configurer",
                    onClick = { showEditInfoDialog = true },
                    testTag = "row_shop_info"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.Category,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0xFFE0F2FE),
                    title = "Type d'activité & Presets",
                    subtitle = "Catalogue et préréglages de démarrage",
                    trailingValue = shopProfile?.businessType ?: "Boutique",
                    onClick = { onNavigate(KonabScreen.ONBOARDING) },
                    testTag = "row_activity_type"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    iconTint = Color(0xFF7C3AED),
                    iconBg = Color(0xFFEDE9FE),
                    title = "Personnalisation des tickets",
                    subtitle = "Pied de page, mentions et remerciements",
                    trailingValue = "Éditer",
                    onClick = { showEditInfoDialog = true },
                    testTag = "row_receipt_custom"
                )
            }

            // ==========================================
            // SECTION 2 : PÉRIPHÉRIQUES & MATÉRIEL POS
            // ==========================================
            SettingsSectionCard(title = "Périphériques & Matériel", icon = Icons.Default.DevicesOther) {
                SettingsModernRow(
                    icon = Icons.Default.Print,
                    iconTint = Color(0xFF059669),
                    iconBg = Color(0xFFD1FAE5),
                    title = "Imprimante thermique Bluetooth",
                    subtitle = "Tickets de caisse ESC/POS (58mm / 80mm)",
                    trailingValue = "Connecter",
                    badgeText = "ESC/POS",
                    badgeColor = KonabGreen,
                    onClick = { showBluetoothPrinterModal = true },
                    testTag = "row_bluetooth_printer"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.QrCodeScanner,
                    iconTint = Color(0xFFD97706),
                    iconBg = Color(0xFFFEF3C7),
                    title = "Lecteur de Code-barres",
                    subtitle = "Caméra appareil & douchettes laser USB/BT",
                    trailingValue = "Actif",
                    onClick = { onNavigate(KonabScreen.POS) },
                    testTag = "row_barcode_scanner"
                )
            }

            // ==========================================
            // SECTION 2.5 : SYNCHRONISATION SUPABASE CLOUD
            // ==========================================
            SettingsSectionCard(title = "Synchronisation Cloud Supabase", icon = Icons.Default.Cloud) {
                // Auto-Sync Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (supabaseSyncState?.isAutoSyncEnabled == true) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = if (supabaseSyncState?.isAutoSyncEnabled == true) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Synchronisation Automatique",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                if (supabaseSyncState?.isAutoSyncEnabled == true) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                }
                            }
                            Text(
                                "Envoie chaque vente, produit et caisse vers Supabase en direct et toutes les 45s",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = supabaseSyncState?.isAutoSyncEnabled == true,
                        onCheckedChange = { onToggleAutoSync(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.CloudUpload,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0xFFE0F2FE),
                    title = "Synchroniser maintenant",
                    subtitle = if (supabaseSyncState?.isSyncing == true) "Synchronisation en cours..." else "Forcer l'envoi et la réception immédiate",
                    trailingValue = if (supabaseSyncState?.isSyncing == true) "En cours..." else "Synchroniser",
                    badgeText = if (supabaseSyncState?.isConfigured == true) "EN LIGNE" else null,
                    badgeColor = Color(0xFF10B981),
                    onClick = { onSyncWithSupabase() },
                    testTag = "row_sync_now"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.Settings,
                    iconTint = Color(0xFF6B7280),
                    iconBg = Color(0xFFF3F4F6),
                    title = "Paramètres Supabase & Schéma SQL",
                    subtitle = "Configurer les identifiants cloud et générer le script SQL",
                    trailingValue = "Gérer",
                    onClick = { showSupabaseSyncModal = true },
                    testTag = "row_supabase_settings"
                )
            }

            // ==========================================
            // SECTION 3 : SAUVEGARDE & RESTAURATION
            // ==========================================
            SettingsSectionCard(title = "Sauvegarde & Restauration", icon = Icons.Default.Backup) {
                SettingsModernRow(
                    icon = Icons.Default.CloudDownload,
                    iconTint = Color(0xFF0284C7),
                    iconBg = Color(0xFFE0F2FE),
                    title = "Exporter une sauvegarde locale",
                    subtitle = "Sauvegarde complète au format JSON (produits, ventes, clients)",
                    trailingValue = "Exporter",
                    onClick = { showBackupDialog = true },
                    testTag = "row_backup_export"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.SettingsBackupRestore,
                    iconTint = Color(0xFF4B5563),
                    iconBg = Color(0xFFF3F4F6),
                    title = "Restaurer une sauvegarde locale",
                    subtitle = "Réimporter vos données à partir d'un fichier de sauvegarde",
                    trailingValue = "Restaurer",
                    onClick = { showRestoreDialog = true },
                    testTag = "row_backup_restore"
                )
            }

            // ==========================================
            // SECTION 3.5 : DONNÉES & NETTOYAGE DÉMO
            // ==========================================
            SettingsSectionCard(title = "Gestion des Données & Démo", icon = Icons.Default.DeleteSweep) {
                SettingsModernRow(
                    icon = Icons.Default.DeleteSweep,
                    iconTint = Color(0xFFDC2626),
                    iconBg = Color(0xFFFEE2E2),
                    title = "Effacer les données démo",
                    subtitle = "Vider articles, ventes tests, dettes et mouvements fictifs",
                    trailingValue = "Vider",
                    badgeText = "NETTOYER",
                    badgeColor = Color(0xFFDC2626),
                    onClick = { showClearDemoDialog = true },
                    testTag = "row_clear_demo_data"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.RestartAlt,
                    iconTint = Color(0xFFB91C1C),
                    iconBg = Color(0xFFFEF2F2),
                    title = "Réinitialiser toute l'application",
                    subtitle = "Efface tout et relance l'assistant d'accueil initial",
                    trailingValue = "Reset",
                    onClick = { showFullResetDialog = true },
                    testTag = "row_full_reset_app"
                )
            }
            // SECTION 4 : SÉCURITÉ & CODES D'ACCÈS
            // ==========================================
            SettingsSectionCard(title = "Sécurité & Contrôle", icon = Icons.Default.Security) {
                SettingsModernRow(
                    icon = Icons.Default.Pin,
                    iconTint = Color(0xFFDC2626),
                    iconBg = Color(0xFFFEE2E2),
                    title = "Codes PIN Administrateur & Caisse",
                    subtitle = "Protection contre les modifications non autorisées",
                    trailingValue = "Modifier",
                    onClick = { showPinChangeDialog = true },
                    testTag = "row_pin_security"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.LockClock,
                    iconTint = Color(0xFFEA580C),
                    iconBg = Color(0xFFFFEDD5),
                    title = "Verrouillage immédiat",
                    subtitle = "Verrouille l'écran jusqu'au prochain code PIN",
                    trailingValue = "Verrouiller",
                    onClick = onLockApp,
                    testTag = "row_lock_now"
                )
            }

            // ==========================================
            // SECTION 5 : ABONNEMENT, AIDE & SUPPORT
            // ==========================================
            SettingsSectionCard(title = "Abonnement & Assistance", icon = Icons.Default.WorkspacePremium) {
                SettingsModernRow(
                    icon = Icons.Default.WorkspacePremium,
                    iconTint = KonabGoldDark,
                    iconBg = KonabGoldContainer,
                    title = "Formule & Licence Pro",
                    subtitle = "Abonnement Premium • Dépôt au 65413799",
                    trailingValue = shopProfile?.subscriptionTier ?: "Premium",
                    badgeText = "ACTIF",
                    badgeColor = KonabGoldDark,
                    onClick = { onNavigate(KonabScreen.SUBSCRIPTION) },
                    testTag = "row_subscription"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.SupportAgent,
                    iconTint = Color(0xFF059669),
                    iconBg = Color(0xFFD1FAE5),
                    title = "Aide & Support Client",
                    subtitle = "Guide pratique et contact WhatsApp / Tel",
                    trailingValue = "Consulter",
                    onClick = { showHelpDialog = true },
                    testTag = "row_help_support"
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsModernRow(
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF6B7280),
                    iconBg = Color(0xFFF3F4F6),
                    title = "À propos de Konab POS",
                    subtitle = "Version 1.0 • Solution 100% Hors-ligne & Cloud",
                    trailingValue = "V1.0",
                    onClick = { showAboutDialog = true },
                    testTag = "row_about_app"
                )
            }

            // Lock / Sign Out Footer Button
            OutlinedButton(
                onClick = onLockApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_lock_settings_footer"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verrouiller la caisse", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Text(
                text = "Konab POS • Conçu pour l'Afrique francophone • Tous droits réservés © 2026",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 11.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // ==========================================
    // DIALOGS & MODALS
    // ==========================================

    // Edit Shop Info Dialog
    if (showEditInfoDialog && shopProfile != null) {
        var name by remember { mutableStateOf(shopProfile.name) }
        var slogan by remember { mutableStateOf(shopProfile.slogan) }
        var phone by remember { mutableStateOf(shopProfile.phone) }
        var address by remember { mutableStateOf(shopProfile.address) }
        var city by remember { mutableStateOf(shopProfile.city) }
        var currency by remember { mutableStateOf(shopProfile.currency) }
        var receiptFooter by remember { mutableStateOf(shopProfile.receiptFooter) }

        val currencies = listOf("FCFA", "EUR", "USD", "GNF", "NGN")

        AlertDialog(
            onDismissRequest = { showEditInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(KonabGreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = KonabGreenDark, modifier = Modifier.size(20.dp))
                    }
                    Text("Modifier la boutique", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nom du commerce *") },
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_shop_name")
                    )

                    OutlinedTextField(
                        value = slogan,
                        onValueChange = { slogan = it },
                        label = { Text("Slogan / Sous-titre") },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Numéro de téléphone *") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_phone")
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Adresse / Quartier") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Ville (Burkina Faso)") },
                        leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Villes populaires du Burkina Faso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf("Ouagadougou", "Bobo-Dioulasso", "Koudougou", "Banfora").forEach { bCity ->
                            FilterChip(
                                selected = city.equals(bCity, ignoreCase = true),
                                onClick = { city = bCity },
                                label = { Text(bCity, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabGreen.copy(alpha = 0.15f),
                                    selectedLabelColor = KonabGreenDark
                                )
                            )
                        }
                    }

                    // Currency Selector Chips
                    Text("Devise principale :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currencies.forEach { curr ->
                            FilterChip(
                                selected = currency == curr,
                                onClick = { currency = curr },
                                label = { Text(curr, fontSize = 11.sp, fontWeight = if (currency == curr) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = KonabGreen.copy(alpha = 0.15f),
                                    selectedLabelColor = KonabGreenDark
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it },
                        label = { Text("Pied de page du ticket") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = shopProfile.copy(
                            name = name.trim().ifBlank { shopProfile.name },
                            slogan = slogan.trim(),
                            phone = phone.trim().ifBlank { shopProfile.phone },
                            address = address.trim(),
                            city = city.trim(),
                            currency = currency,
                            receiptFooter = receiptFooter.trim()
                        )
                        onUpdateProfile(updated)
                        Toast.makeText(context, "Informations de la boutique enregistrées", Toast.LENGTH_SHORT).show()
                        showEditInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier.testTag("btn_save_shop_info")
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Change PIN Dialog
    if (showPinChangeDialog && shopProfile != null) {
        var currentPinInput by remember { mutableStateOf("") }
        var newAdminPinInput by remember { mutableStateOf("") }
        var newCashierPinInput by remember { mutableStateOf(shopProfile.cashierPin) }
        var errorMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    }
                    Text("Sécurité des Codes PIN", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pour modifier les codes, veuillez d'abord saisir le code PIN Administrateur actuel :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { if (it.length <= 4) currentPinInput = it },
                        label = { Text("Code PIN Admin actuel (4 chiffres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_current_pin")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    OutlinedTextField(
                        value = newAdminPinInput,
                        onValueChange = { if (it.length <= 4) newAdminPinInput = it },
                        label = { Text("Nouveau PIN Admin (4 chiffres)") },
                        placeholder = { Text("Ex: 1234") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_new_admin_pin")
                    )

                    OutlinedTextField(
                        value = newCashierPinInput,
                        onValueChange = { if (it.length <= 4) newCashierPinInput = it },
                        label = { Text("PIN Caissier (4 chiffres)") },
                        placeholder = { Text("Ex: 0000") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().testTag("input_new_cashier_pin")
                    )

                    if (errorMsg.isNotBlank()) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPinInput != shopProfile.adminPin) {
                            errorMsg = "Le code PIN actuel est incorrect"
                        } else if (newAdminPinInput.length < 4) {
                            errorMsg = "Le nouveau code Admin doit comporter 4 chiffres"
                        } else {
                            onUpdateProfile(
                                shopProfile.copy(
                                    adminPin = newAdminPinInput,
                                    cashierPin = newCashierPinInput.ifBlank { "0000" }
                                )
                            )
                            Toast.makeText(context, "Codes PIN mis à jour avec succès", Toast.LENGTH_SHORT).show()
                            showPinChangeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier.testTag("btn_confirm_pin_change")
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinChangeDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Backup Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(KonabGreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = KonabGreenDark, modifier = Modifier.size(20.dp))
                    }
                    Text("Sauvegarde Locale (100% Hors-ligne)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Une archive JSON chiffrée contenant toutes vos données (produits, ventes, tickets, clients, fournisseurs, caisse) sera créée sur votre appareil.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(18.dp))
                            Text(
                                "Vous pourrez envoyer le fichier par WhatsApp, Email ou Bluetooth pour le conserver en lieu sûr.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBackup(context) { file ->
                            if (file != null) {
                                BackupRestoreHelper.shareBackupFile(context, file)
                            } else {
                                Toast.makeText(context, "Erreur lors de la création de la sauvegarde", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showBackupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Exporter la sauvegarde")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupDialog = false
                    showRestoreDialog = true
                }) {
                    Text("Restaurer un fichier...")
                }
            }
        )
    }

    // Restore Dialog
    if (showRestoreDialog) {
        var jsonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE0F2FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                    }
                    Text("Restaurer des Données", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Collez ci-dessous le contenu textuel de votre fichier de sauvegarde JSON :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = { jsonInput = it },
                        placeholder = { Text("{\"version\":1, \"app\":\"Konab POS\"...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestore(jsonInput) { success ->
                            if (success) {
                                Toast.makeText(context, "Données restaurées avec succès !", Toast.LENGTH_LONG).show()
                                showRestoreDialog = false
                            } else {
                                Toast.makeText(context, "Fichier invalide ou format incorrect", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Text("Restaurer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Clear Demo Data Dialog
    if (showClearDemoDialog) {
        AlertDialog(
            onDismissRequest = { showClearDemoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    }
                    Text("Effacer les données démo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Cette action va supprimer définitivement :",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Tous les articles et stocks de test", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Toutes les ventes et tickets enregistrés", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Tous les clients et dettes de test", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Tous les fournisseurs et achats de test", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• L'historique des mouvements et sessions de caisse", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Votre profil de commerce, logo, nom et code PIN seront conservés. Votre application sera 100% vierge et prête pour vos vrais encaissements.",
                            fontSize = 11.sp,
                            color = Color(0xFF065F46),
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearDemoData(true) {
                            Toast.makeText(context, "Toutes les données démo ont été effacées avec succès !", Toast.LENGTH_LONG).show()
                        }
                        showClearDemoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Oui, effacer la démo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDemoDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Full Reset Dialog
    if (showFullResetDialog) {
        AlertDialog(
            onDismissRequest = { showFullResetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEF2F2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = Color(0xFFB91C1C), modifier = Modifier.size(20.dp))
                    }
                    Text("Réinitialisation Complète", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Attention : Cette opération efface absolument TOUT sur l'appareil (données, paramètres, profil commerçant et codes PIN).",
                        fontSize = 13.sp,
                        color = Color(0xFFB91C1C),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "L'application reviendra à l'écran initial de configuration de bienvenue.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearDemoData(false) {
                            Toast.makeText(context, "Application réinitialisée à zéro.", Toast.LENGTH_LONG).show()
                        }
                        showFullResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                ) {
                    Text("Réinitialiser à zéro")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullResetDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(KonabGreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = KonabGreenDark, modifier = Modifier.size(20.dp))
                    }
                    Text("Guide & Support Konab POS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HelpPointItem(
                        icon = Icons.Default.PointOfSale,
                        title = "Encaissement Express",
                        desc = "Cliquez sur les articles pour les ajouter au panier ou scannez directement les codes-barres."
                    )
                    HelpPointItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Gestion de Caisse",
                        desc = "Ouvrez votre session chaque matin avec le montant en espèces, puis effectuez la clôture (Z de caisse) le soir."
                    )
                    HelpPointItem(
                        icon = Icons.Default.CloudDone,
                        title = "Synchronisation Automatique",
                        desc = "Vos ventes, stocks et clients sont automatiquement synchronisés en arrière-plan pour ne jamais perdre vos données."
                    )
                    Surface(
                        color = KonabGoldContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Ligne officielle support & activation :", fontSize = 11.sp, color = KonabGoldDark)
                                Text("+226 65 41 37 99", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KonabGoldDark)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Support Phone", "65413799"))
                                    Toast.makeText(context, "Numéro copié : 65413799", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = KonabGoldDark, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Fermer", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(KonabGreenLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("K", fontWeight = FontWeight.ExtraBold, color = KonabGreenDark, fontSize = 20.sp)
                    }
                    Text("À propos de Konab POS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Konab POS - Système de Point de Vente Professionnel",
                        fontWeight = FontWeight.Bold,
                        color = KonabGreenDark,
                        fontSize = 13.sp
                    )
                    Text(
                        "Spécialement développé pour les commerçants du Burkina Faso et de la sous-région ouest-africaine. Fonctionne sans connexion Internet grâce à la technologie SQLite Room, avec synchronisation Cloud automatique.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version 1.0.0 (Build 2026)", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("Développé avec fierté par Konab Digital © 2026", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Fermer", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modals
    if (showBluetoothPrinterModal) {
        com.example.ui.components.BluetoothPrinterModal(
            shopProfile = shopProfile,
            onDismiss = { showBluetoothPrinterModal = false }
        )
    }

    if (showSupabaseSyncModal && supabaseSyncState != null) {
        com.example.ui.components.SupabaseSyncDialog(
            syncState = supabaseSyncState,
            onTestConnection = onTestSupabase,
            onSaveAndSync = onSaveSupabaseCredentials,
            onSyncNow = onSyncWithSupabase,
            onToggleAutoSync = onToggleAutoSync,
            onDismiss = { showSupabaseSyncModal = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KonabGreenDark,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
            )
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsModernRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    trailingValue: String? = null,
    badgeText: String? = null,
    badgeColor: Color = KonabGreen,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (trailingValue != null) {
                Text(
                    text = trailingValue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HelpPointItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(KonabGreenLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = KonabGreenDark, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

data class BurkinaSlide(
    val imageRes: Int,
    val title: String,
    val location: String,
    val subtitle: String,
    val badge: String
)

@Composable
fun BurkinaFasoImageFilBanner(
    modifier: Modifier = Modifier
) {
    val slides = remember {
        listOf(
            BurkinaSlide(
                imageRes = R.drawable.burkina_ouaga_market,
                title = "Grand Marché Rood-Woko",
                location = "Ouagadougou",
                subtitle = "Le cœur battant du commerce burkinabè",
                badge = "Commerce & Vivres"
            ),
            BurkinaSlide(
                imageRes = R.drawable.burkina_monument_heros,
                title = "Monument des Héros Nationaux",
                location = "Ouaga 2000",
                subtitle = "Fierté, bravoure et dignité du Faso",
                badge = "Patrimoine & Histoire"
            ),
            BurkinaSlide(
                imageRes = R.drawable.burkina_commerce_faso,
                title = "Boutique & Faso Dan Fani",
                location = "Production Locale",
                subtitle = "Consommer local & digitalisation des commerces",
                badge = "Textile & Artisanat"
            ),
            BurkinaSlide(
                imageRes = R.drawable.burkina_bobo_dioulasso,
                title = "Grande Mosquée de Dioulassoba",
                location = "Bobo-Dioulasso",
                subtitle = "Carrefour historique et culturel d'exception",
                badge = "Architecture Sahélienne"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    // Défilement automatique toutes les 4 secondes
    LaunchedEffect(pagerState.currentPage) {
        delay(4000)
        val next = (pagerState.currentPage + 1) % slides.size
        pagerState.animateScrollToPage(next)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("burkina_image_fil_carousel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Barre supérieure patriotique & informative
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🇧🇫", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Burkina Faso • Terre d'Hommes Intègres",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Vitrine culturelle & commerciale du Faso",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )
                    }
                }

                Surface(
                    color = Color(0xFF059669).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${slides.size}",
                        color = Color(0xFF34D399),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            // Image Carousel Pager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val slide = slides[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = slide.imageRes),
                            contentDescription = slide.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Dégradé sombre pour lisibilité maximale des textes
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.88f)
                                        )
                                    )
                                )
                        )

                        // Badge de localisation en haut à gauche
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = KonabGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${slide.location} • ${slide.badge}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Légende et texte en bas
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.82f)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = slide.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = slide.subtitle,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Points de pagination cliquables en bas à droite
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slides.indices.forEach { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 18.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) KonabGreen else Color.White.copy(alpha = 0.5f)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }
            }

            // Bandeau inférieur : Rayonnement régional
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ouagadougou • Bobo • Koudougou • Banfora",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Franc CFA (XOF)",
                    fontSize = 10.5.sp,
                    color = KonabGreenDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
