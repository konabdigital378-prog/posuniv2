package com.example.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.RestaurantStaffAccount
import com.example.data.remote.supabase.RestaurantStaffRole
import com.example.ui.theme.*
import com.example.util.BtPeerCandidate
import com.example.util.BtSyncStatus
import com.example.util.QrCodeHelper
import com.example.util.RestaurantBluetoothMode
import com.example.util.RestaurantBluetoothSyncEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RestaurantBluetoothSetupModal(
    shopName: String,
    staffList: List<RestaurantStaffAccount>,
    activeStaff: RestaurantStaffAccount?,
    btSyncStatus: BtSyncStatus,
    btCandidates: List<BtPeerCandidate>,
    isScanningBt: Boolean,
    initialTab: Int = 0,
    onStartHub: () -> Unit,
    onConnectClient: (address: String, name: String) -> Unit,
    onStopBt: () -> Unit,
    onScanBtPeers: () -> Unit,
    onStopScanBtPeers: () -> Unit,
    onRequestFullSync: () -> Unit,
    onFlushOutboxToCloud: ((Boolean, String) -> Unit) -> Unit,
    buildTeamJoinQrUri: (RestaurantStaffAccount) -> String,
    onSaveQuickStaff: ((RestaurantStaffAccount) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var selectedStaffId by remember(staffList) {
        mutableStateOf(
            staffList.firstOrNull { it.role == RestaurantStaffRole.WAITER }?.id
                ?: staffList.firstOrNull { it.role == RestaurantStaffRole.KITCHEN }?.id
                ?: staffList.firstOrNull()?.id
                ?: ""
        )
    }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isFlushingCloud by remember { mutableStateOf(false) }

    val btEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(context, "Activez le Bluetooth pour continuer.", Toast.LENGTH_SHORT).show()
        }
        pendingAction = null
    }

    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        Toast.makeText(context, "Appareil visible 5 minutes pour les postes équipe.", Toast.LENGTH_SHORT).show()
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it } || RestaurantBluetoothSyncEngine.hasConnectPermission(context)
        if (allGranted) {
            if (!RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
                btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                pendingAction?.invoke()
                pendingAction = null
            }
        } else {
            pendingAction = null
            Toast.makeText(
                context,
                "Autorisez « Appareils à proximité » (Bluetooth) pour synchroniser les téléphones du restaurant.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun runWithBluetoothReady(action: () -> Unit) {
        val needed = RestaurantBluetoothSyncEngine.requiredBluetoothPermissions()
        val hasPerm = RestaurantBluetoothSyncEngine.hasConnectPermission(context) &&
            RestaurantBluetoothSyncEngine.hasScanPermission(context)
        if (!hasPerm && needed.isNotEmpty()) {
            pendingAction = action
            btPermissionLauncher.launch(needed)
        } else if (!RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
            pendingAction = action
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            action()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopScanBtPeers()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.90f)
                .safeDrawingPadding()
                .testTag("modal_restaurant_bluetooth_setup"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(KonabBlueDeep, KonabBlueDark, Color(0xFF0F766E))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothConnected,
                                    contentDescription = "Réseau Bluetooth Restaurant",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Réseau Restaurant & Équipe",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Bluetooth local sans Internet + Pont Cloud Supabase",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFCCFBF1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = KonabBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_bt_network"),
                        text = { Text("Réseau Bluetooth", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_bt_install_qr"),
                        text = { Text("Installer Équipe (QR)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedTab == 0) {
                        // STATUS BANNER
                        val badgeColor = when {
                            btSyncStatus.mode == RestaurantBluetoothMode.HUB && btSyncStatus.isRunning -> KonabGreen
                            btSyncStatus.mode == RestaurantBluetoothMode.CLIENT && btSyncStatus.isConnected -> KonabBlue
                            btSyncStatus.isRunning || btSyncStatus.isConnecting -> KonabOrange
                            else -> Color(0xFF64748B)
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
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
                                        Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
                                            Text(
                                                text = btSyncStatus.mode.shortBadge,
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = btSyncStatus.mode.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (btSyncStatus.lastSyncAt > 0L) {
                                        val timeTxt = remember(btSyncStatus.lastSyncAt) {
                                            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                                .format(Date(btSyncStatus.lastSyncAt))
                                        }
                                        Text(
                                            text = "Synchro $timeTxt",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = btSyncStatus.statusMessage,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (btSyncStatus.localBtName.isNotBlank()) {
                                    Text(
                                        text = "Nom Bluetooth de ce téléphone : ${btSyncStatus.localBtName}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KonabBlue
                                    )
                                }

                                if (btSyncStatus.connectedPeers.isNotEmpty()) {
                                    HorizontalDivider(color = badgeColor.copy(alpha = 0.25f))
                                    Text(
                                        text = "Postes connectés (${btSyncStatus.connectedPeers.size}) :",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    btSyncStatus.connectedPeers.forEach { peer ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "• ${peer.staffName} (${peer.deviceName})",
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = KonabBlue.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = peer.role.badgeLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = KonabBlue,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // HYBRID CLOUD OUTBOX BRIDGE CARD
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = if (btSyncStatus.pendingOutboxCount > 0) KonabOrange else KonabGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Pont Cloud Hybride (Gérant distant)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    Text(
                                        text = if (btSyncStatus.pendingOutboxCount > 0) {
                                            "${btSyncStatus.pendingOutboxCount} commande(s) en file d'attente vers Supabase"
                                        } else {
                                            "Toutes les commandes locales sont synchronisées"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        isFlushingCloud = true
                                        onFlushOutboxToCloud { ok, msg ->
                                            isFlushingCloud = false
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = !isFlushingCloud,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    if (isFlushingCloud) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Synchro Cloud", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // SECTION 1: HUB (CAISSE / GÉRANT PRINCIPAL)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Hub, contentDescription = null, tint = KonabGreen)
                                    Text(
                                        text = "1. Poste Principal (Hub — Caissier ou Gérant)",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "Ce téléphone centralise la salle sans Internet : Cuisine, Serveurs et Gérant s'y connectent en Bluetooth et reçoivent le menu & les commandes en direct.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { runWithBluetoothReady { onStartHub() } },
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_bt_start_hub")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (btSyncStatus.mode == RestaurantBluetoothMode.HUB && btSyncStatus.isRunning) {
                                                "Hub Actif"
                                            } else {
                                                "Activer Hub"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            runWithBluetoothReady {
                                                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                                                }
                                                discoverableLauncher.launch(intent)
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_bt_make_discoverable")
                                    ) {
                                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Visible 5 min", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (btSyncStatus.isRunning) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { selectedTab = 1 },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 44.dp)
                                        ) {
                                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(17.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Afficher QR d'installation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = onStopBt,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabRed),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 44.dp)
                                        ) {
                                            Text("Arrêter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION 2: CLIENT (CUISINE / SERVEUR / GÉRANT LOCAL)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = KonabBlue)
                                    Text(
                                        text = "2. Poste Client (Cuisine / Serveur / Salle)",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Text(
                                    text = "Connectez ce téléphone au Poste Principal (Hub) pour recevoir et envoyer les commandes en direct via Bluetooth.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { runWithBluetoothReady { onScanBtPeers() } },
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_bt_scan_peers")
                                    ) {
                                        if (isScanningBt) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recherche...", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Chercher le Hub", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        }
                                    }

                                    if (btSyncStatus.mode == RestaurantBluetoothMode.CLIENT && btSyncStatus.isConnected) {
                                        OutlinedButton(
                                            onClick = onRequestFullSync,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Menu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (btCandidates.isEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Appuyez sur « Chercher le Hub » pour lister les téléphones appairés ou à proximité (assurez-vous que le Hub a cliqué « Visible 5 min » s'il n'est pas encore appairé).",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    btCandidates.forEach { candidate ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (candidate.isSuggestedHub) KonabBlueContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (candidate.isSuggestedHub) KonabBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    runWithBluetoothReady {
                                                        onStopScanBtPeers()
                                                        onConnectClient(candidate.address, candidate.name)
                                                    }
                                                }
                                                .testTag("bt_candidate_${candidate.address}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PhoneAndroid,
                                                        contentDescription = null,
                                                        tint = if (candidate.isSuggestedHub) KonabBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Column {
                                                        Text(
                                                            text = candidate.name,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${candidate.address} • ${if (candidate.isBonded) "Appairé" else "À proximité"}",
                                                            fontSize = 10.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    color = if (candidate.isSuggestedHub) KonabGreen else KonabBlue,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text(
                                                        text = if (candidate.isSuggestedHub) "HUB • Connecter" else "Connecter",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.5.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB 2 : INSTALLER LES AUTRES TÉLÉPHONES PAR QR CODE
                        if (btSyncStatus.mode != RestaurantBluetoothMode.HUB || !btSyncStatus.isRunning) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = KonabOrange.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = KonabOrange)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Activez d'abord le Hub sur cet appareil",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        Text(
                                            text = "Pour que les autres téléphones reçoivent la carte sans Internet juste après le scan :",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { runWithBluetoothReady { onStartHub() } },
                                        colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text("Activer Hub", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Text(
                            text = "1. Choisissez le poste à installer :",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(staffList, key = { it.id }) { staff ->
                                val isSel = staff.id == selectedStaffId
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedStaffId = staff.id },
                                    label = {
                                        Text(
                                            text = "${staff.role.badgeLabel} • ${staff.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = KonabBlue,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("chip_qr_staff_${staff.role.name.lowercase()}")
                                )
                            }
                        }

                        if (onSaveQuickStaff != null) {
                            val missingRoles = RestaurantStaffRole.entries.filter { role ->
                                staffList.none { it.role == role }
                            }
                            if (missingRoles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    missingRoles.forEach { r ->
                                        OutlinedButton(
                                            onClick = {
                                                val defaultPin = when (r) {
                                                    RestaurantStaffRole.KITCHEN -> "2222"
                                                    RestaurantStaffRole.WAITER -> "1111"
                                                    RestaurantStaffRole.CASHIER -> "0000"
                                                    RestaurantStaffRole.MANAGER -> "1234"
                                                }
                                                onSaveQuickStaff(
                                                    RestaurantStaffAccount(
                                                        id = "",
                                                        name = r.displayName,
                                                        role = r,
                                                        pin = defaultPin
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 42.dp)
                                        ) {
                                            Text("+ ${r.badgeLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        val selectedStaff = staffList.firstOrNull { it.id == selectedStaffId } ?: activeStaff
                        if (selectedStaff != null) {
                            val qrPayload = remember(selectedStaff, shopName, btSyncStatus.localBtName) {
                                buildTeamJoinQrUri(selectedStaff)
                            }
                            val qrBitmap = remember(qrPayload) {
                                QrCodeHelper.generateQrBitmap(qrPayload, sizePx = 420, margin = 3)
                            }

                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, KonabBlue.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        color = KonabBlue,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "POSTE ${selectedStaff.role.badgeLabel} • ${selectedStaff.name.uppercase(Locale.getDefault())}",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = shopName.ifBlank { "Mon Restaurant" },
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color(0xFF0F172A),
                                        textAlign = TextAlign.Center
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = Color.White,
                                        shadowElevation = 2.dp,
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.size(236.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                                val strokeW = 3.dp.toPx()
                                                val len = 18.dp.toPx()
                                                val w = size.width
                                                val h = size.height
                                                drawLine(KonabBlue, Offset(0f, len), Offset(0f, 0f), strokeW, StrokeCap.Round)
                                                drawLine(KonabBlue, Offset(0f, 0f), Offset(len, 0f), strokeW, StrokeCap.Round)
                                                drawLine(KonabOrange, Offset(w - len, 0f), Offset(w, 0f), strokeW, StrokeCap.Round)
                                                drawLine(KonabOrange, Offset(w, 0f), Offset(w, len), strokeW, StrokeCap.Round)
                                                drawLine(KonabOrange, Offset(0f, h - len), Offset(0f, h), strokeW, StrokeCap.Round)
                                                drawLine(KonabOrange, Offset(0f, h), Offset(len, h), strokeW, StrokeCap.Round)
                                                drawLine(KonabBlue, Offset(w - len, h), Offset(w, h), strokeW, StrokeCap.Round)
                                                drawLine(KonabBlue, Offset(w, h - len), Offset(w, h), strokeW, StrokeCap.Round)
                                            }
                                            Image(
                                                bitmap = qrBitmap.asImageBitmap(),
                                                contentDescription = "QR d'installation ${selectedStaff.role.displayName}",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(14.dp)
                                                    .testTag("img_team_install_qr"),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    Text(
                                        text = "SCANNER AVEC KONAB POS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = KonabBlue
                                    )

                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "PIN du poste : ${selectedStaff.pin}",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = KonabBlue
                                            )
                                            if (btSyncStatus.localBtName.isNotBlank()) {
                                                Text(
                                                    text = "Téléphone Hub Bluetooth : ${btSyncStatus.localBtName}",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                            Text(
                                                text = "Sur l'autre téléphone : ouvrez Konab POS → « Rejoindre un restaurant » → « Scanner le QR Code d'installation ».",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF475569)
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
fun JoinRestaurantTeamModal(
    btSyncStatus: BtSyncStatus,
    btCandidates: List<BtPeerCandidate>,
    isScanningBt: Boolean,
    onScanBtPeers: () -> Unit,
    onStopScanBtPeers: () -> Unit,
    onJoinFromQr: (String, onResult: (Boolean, String) -> Unit) -> Unit,
    onJoinViaBluetooth: (btAddress: String, btName: String, pin: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onOpenRemoteMultiShop: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showQrScanner by remember { mutableStateOf(false) }
    var staffPinInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf("") }
    var errorFeedback by remember { mutableStateOf("") }
    var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val btEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
            pendingBtAction?.invoke()
        }
        pendingBtAction = null
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val ok = perms.values.all { it } || RestaurantBluetoothSyncEngine.hasConnectPermission(context)
        if (ok) {
            if (!RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
                btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                pendingBtAction?.invoke()
                pendingBtAction = null
            }
        } else {
            pendingBtAction = null
            errorFeedback = "Autorisez la permission Bluetooth « Appareils à proximité » pour détecter le poste principal."
        }
    }

    fun runWithBluetoothReady(action: () -> Unit) {
        val needed = RestaurantBluetoothSyncEngine.requiredBluetoothPermissions()
        val hasPerm = RestaurantBluetoothSyncEngine.hasConnectPermission(context) &&
            RestaurantBluetoothSyncEngine.hasScanPermission(context)
        if (!hasPerm && needed.isNotEmpty()) {
            pendingBtAction = action
            btPermissionLauncher.launch(needed)
        } else if (!RestaurantBluetoothSyncEngine.isBluetoothEnabled(context)) {
            pendingBtAction = action
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            action()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopScanBtPeers()
        }
    }

    Dialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_join_restaurant_team"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFEA580C), KonabBlueDark)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestaurantMenu,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Rejoindre un Restaurant",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Serveur • Cuisine • Caissier • Gérant",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (!isConnecting) onDismiss() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (statusFeedback.isNotBlank()) {
                        Surface(
                            color = KonabBlue.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isConnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                                Text(statusFeedback, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = KonabBlue)
                            }
                        }
                    }
                    if (errorFeedback.isNotBlank()) {
                        Surface(
                            color = KonabRed.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorFeedback,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 1. SCANNER QR D'INSTALLATION (1 CLIC)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        border = BorderStroke(1.2.dp, Color(0xFFEA580C).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1. Installation instantanée par QR Code",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color(0xFF9A3412)
                            )
                            Text(
                                text = "Scannez le QR d'équipe affiché sur l'appareil du Gérant ou du Caissier pour configurer automatiquement ce téléphone (même sans Internet).",
                                fontSize = 12.sp,
                                color = Color(0xFF7C2D12)
                            )
                            Button(
                                onClick = { showQrScanner = true },
                                enabled = !isConnecting,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_join_scan_team_qr")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanner le QR d'installation", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // 2. CONNEXION DIRECTE BLUETOOTH (SANS INTERNET)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "2. Connexion directe Bluetooth (sans Internet)",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            OutlinedTextField(
                                value = staffPinInput,
                                onValueChange = { staffPinInput = it.filter { c -> c.isDigit() }.take(8) },
                                label = { Text("Votre code PIN (ex: 1111 Serveur, 2222 Cuisine)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_join_bt_pin")
                            )
                            Button(
                                onClick = {
                                    errorFeedback = ""
                                    runWithBluetoothReady { onScanBtPeers() }
                                },
                                enabled = !isConnecting,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_join_bt_discover")
                            ) {
                                if (isScanningBt) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recherche des téléphones Hub…", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Chercher le téléphone principal (Hub)", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (btSyncStatus.isConnecting || btSyncStatus.statusMessage.isNotBlank() && isConnecting) {
                                Text(
                                    text = btSyncStatus.statusMessage,
                                    fontSize = 11.5.sp,
                                    color = KonabBlue
                                )
                            }

                            btCandidates.forEach { candidate ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isConnecting) {
                                            errorFeedback = ""
                                            isConnecting = true
                                            statusFeedback = "Connexion Bluetooth à ${candidate.name}…"
                                            runWithBluetoothReady {
                                                onStopScanBtPeers()
                                                onJoinViaBluetooth(candidate.address, candidate.name, staffPinInput) { ok, msg ->
                                                    isConnecting = false
                                                    if (ok) {
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        onDismiss()
                                                    } else {
                                                        statusFeedback = ""
                                                        errorFeedback = msg
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("btn_join_bt_candidate_${candidate.address}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(candidate.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "${candidate.address} • ${if (candidate.isBonded) "Appairé" else "Détecté"}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KonabBlue)
                                    }
                                }
                            }
                        }
                    }

                    // 3. CONNEXION À DISTANCE VIA INTERNET
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenRemoteMultiShop()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_join_remote_cloud")
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connexion à distance via Internet (Gérant)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        RealBarcodeScannerModal(
            title = "Scanner QR d'installation Équipe",
            subtitle = "Pointez la caméra vers le QR généré sur le poste principal du restaurant",
            onBarcodeScanned = { rawQr ->
                showQrScanner = false
                isConnecting = true
                errorFeedback = ""
                statusFeedback = "Installation du poste restaurant en cours…"
                onJoinFromQr(rawQr) { ok, msg ->
                    isConnecting = false
                    if (ok) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onDismiss()
                    } else {
                        statusFeedback = ""
                        errorFeedback = msg
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }
}
