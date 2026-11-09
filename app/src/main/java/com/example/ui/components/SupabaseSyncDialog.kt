package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.supabase.SupabaseConfig
import com.example.data.remote.supabase.SupabaseSyncState
import com.example.ui.theme.KonabGreen
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseSyncDialog(
    syncState: SupabaseSyncState,
    onTestConnection: (url: String?, key: String?) -> Unit = { _, _ -> },
    onSaveAndSync: (url: String, key: String) -> Unit = { _, _ -> },
    onSyncNow: () -> Unit = {},
    onSyncFullStore: () -> Unit = {},
    onToggleAutoSync: (Boolean) -> Unit = {},
    onOpenAuth: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showSqlSchemaModal by remember { mutableStateOf(false) }
    var showCredentialsFields by remember { mutableStateOf(!syncState.isConfigured) }

    var inputUrl by remember {
        mutableStateOf(SupabaseConfig.rawUrl)
    }
    var inputKey by remember {
        mutableStateOf(SupabaseConfig.rawAnonKey)
    }
    var showKeyPassword by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3ECF8E).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Base de Données Supabase", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Synchronisation automatique de la configuration d'entreprise", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_supabase_modal")) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            // Connection Status Banner
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        syncState.isSyncing -> Color(0xFFE0F2FE)
                        syncState.isError -> Color(0xFFFFEBEE)
                        syncState.isConfigured -> Color(0xFFE8F5E9)
                        else -> Color(0xFFFFF8E1)
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        syncState.isSyncing -> Color(0xFF38BDF8)
                        syncState.isError -> Color(0xFFEF5350)
                        syncState.isConfigured -> Color(0xFF4CAF50)
                        else -> Color(0xFFFFB300)
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (syncState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF0284C7)
                                )
                            } else {
                                Icon(
                                    imageVector = when {
                                        syncState.isError -> Icons.Default.Error
                                        syncState.isConfigured -> Icons.Default.CheckCircle
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        syncState.isError -> Color(0xFFC62828)
                                        syncState.isConfigured -> Color(0xFF2E7D32)
                                        else -> Color(0xFFF57F17)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    syncState.isSyncing -> "Test / Synchro en cours..."
                                    syncState.isError -> "Échec du Test Supabase"
                                    syncState.isConfigured -> "Supabase Opérationnel"
                                    else -> "Configuration requise"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = when {
                                    syncState.isSyncing -> Color(0xFF0369A1)
                                    syncState.isError -> Color(0xFFB71C1C)
                                    syncState.isConfigured -> Color(0xFF1B5E20)
                                    else -> Color(0xFFE65100)
                                }
                            )
                        }

                        if (syncState.isConfigured && !syncState.isError) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "EN LIGNE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (syncState.statusMessage != null) {
                        Text(
                            syncState.statusMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (syncState.lastSyncTimestamp != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Dernière synchro : ${CurrencyFormatter.formatDateTime(syncState.lastSyncTimestamp)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Supabase Account / Authentication Card
            val isAuth = com.example.data.remote.supabase.SupabaseAuthManager.isAuthenticated
            val authUser = com.example.data.remote.supabase.SupabaseAuthManager.currentUser

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAuth) Color(0xFFF0FDF4) else Color(0xFFFEF3C7).copy(alpha = 0.6f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAuth) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                                .clip(CircleShape)
                                .background(if (isAuth) Color(0xFF10B981) else Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAuth) Icons.Default.Person else Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isAuth) (authUser?.name ?: "Utilisateur Cloud") else "Compte Utilisateur Supabase",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isAuth) "${authUser?.email} • ${if (authUser?.role == "ADMIN") "Gérant" else "Caissier"}" else "Non connecté (mode anonyme)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (onOpenAuth != null) {
                        FilledTonalButton(
                            onClick = onOpenAuth,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isAuth) "Mon Profil" else "Connexion",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Auto-Sync Continuous Toggle Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncState.isAutoSyncEnabled) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (syncState.isAutoSyncEnabled) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Synchronisation Automatique (Configuration Entreprise)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (syncState.isAutoSyncEnabled) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                            )
                            if (syncState.isAutoSyncEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                 )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Seules les informations de configuration de l'entreprise (nom boutique, téléphone, adresse, devise, reçus) sont envoyées sur Supabase Cloud. Vos ventes, stocks, clients et caisses restent 100% locaux et sécurisés.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = syncState.isAutoSyncEnabled,
                        onCheckedChange = { onToggleAutoSync(it) }
                    )
                }
            }

            // Collapsible / Editable Credentials Section
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Identifiants du Projet Supabase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { showCredentialsFields = !showCredentialsFields },
                            modifier = Modifier.size(28.dp).testTag("btn_toggle_supabase_credentials")
                        ) {
                            Icon(
                                if (showCredentialsFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Afficher/Masquer"
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCredentialsFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // URL Field
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                label = { Text("URL du Projet Supabase", fontSize = 12.sp) },
                                placeholder = { Text("https://xxxxxxxx.supabase.co", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth().testTag("input_supabase_url"),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                            if (pasted.startsWith("http")) {
                                                inputUrl = pasted.trim()
                                                Toast.makeText(context, "URL collée !", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Coller URL", modifier = Modifier.size(18.dp))
                                    }
                                }
                            )

                            // Anon Key Field
                            OutlinedTextField(
                                value = inputKey,
                                onValueChange = { inputKey = it },
                                label = { Text("Clé Publique Anon (API Key)", fontSize = 12.sp) },
                                placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...", fontSize = 12.sp) },
                                singleLine = true,
                                visualTransformation = if (showKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("input_supabase_key"),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = { showKeyPassword = !showKeyPassword }) {
                                            Icon(
                                                if (showKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Afficher/Masquer",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                                            if (clip != null && clip.itemCount > 0) {
                                                val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                                if (pasted.isNotBlank()) {
                                                    inputKey = pasted.trim()
                                                    Toast.makeText(context, "Clé Anon collée !", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Coller Clé", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )

                            Text(
                                "Ces paramètres sont enregistrés de façon sécurisée sur votre appareil et dans vos variables d'environnement.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Test Button
                OutlinedButton(
                    onClick = {
                        val testUrl = inputUrl.trim().takeIf { it.isNotBlank() }
                        val testKey = inputKey.trim().takeIf { it.isNotBlank() }
                        onTestConnection(testUrl, testKey)
                    },
                    enabled = !syncState.isSyncing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_test_supabase_connection"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tester", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Sync / Save & Sync Button
                Button(
                    onClick = {
                        val targetUrl = inputUrl.trim()
                        val targetKey = inputKey.trim()
                        if (targetUrl.isNotBlank() && targetKey.isNotBlank()) {
                            onSaveAndSync(targetUrl, targetKey)
                        } else {
                            onSyncNow()
                        }
                    },
                    enabled = !syncState.isSyncing && (syncState.isConfigured || (inputUrl.isNotBlank() && inputKey.isNotBlank())),
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("btn_sync_now_supabase"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Entreprise", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Optionnel : Synchronisation complète de tout le magasin
            OutlinedButton(
                onClick = { onSyncFullStore() },
                enabled = !syncState.isSyncing && syncState.isConfigured,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_sync_full_store"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF0284C7))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Synchroniser Tout le Magasin (Produits, Ventes, Caisse)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // SQL Schema Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DataObject, contentDescription = null, tint = KonabGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Script SQL Supabase", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        TextButton(
                            onClick = { showSqlSchemaModal = true },
                            modifier = Modifier.testTag("btn_show_sql_schema")
                        ) {
                            Text("Afficher & Copier", fontSize = 12.sp, color = KonabGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Générez les 11 tables (produits, ventes, tickets, clients, fournisseurs, achats, caisse, notifications, catégories) et règles RLS dans votre console Supabase en 1 clic.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSqlSchemaModal) {
        val schemaSql = remember { SupabaseConfig.generateSqlSchema() }
        AlertDialog(
            onDismissRequest = { showSqlSchemaModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = KonabGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Schéma SQL Supabase", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Copiez ce script et collez-le dans Supabase (SQL Editor > New Query > Run) :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = schemaSql,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF81C784),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Konab Supabase SQL", schemaSql)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Script SQL copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                        showSqlSchemaModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    modifier = Modifier.testTag("btn_copy_sql_schema")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copier le script")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSqlSchemaModal = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}
