package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.ui.screens.AgentDirectory
import com.example.ui.screens.AgentUser
import com.example.ui.theme.*
import com.example.util.BiometricSecurityHelper

/**
 * Modal demandant le code gérant (PIN administrateur) avant d'entrer dans une boutique déjà configurée.
 */
@Composable
fun ManagerPinEntryModal(
    shopProfile: ShopProfileEntity,
    onDismiss: () -> Unit,
    onPinVerified: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val canUseBiometric = remember { BiometricSecurityHelper.canUseBiometricUnlock(context) }

    fun attemptVerify() {
        val cleanEntered = enteredPin.trim()
        val expectedPin = shopProfile.adminPin.trim()
        when {
            cleanEntered.isEmpty() -> {
                errorMessage = "Veuillez saisir le code gérant."
            }
            cleanEntered == expectedPin -> {
                errorMessage = ""
                onPinVerified()
            }
            else -> {
                errorMessage = "Code gérant incorrect. Veuillez réessayer."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_manager_pin_entry"),
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
                // Header Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(KonabBlueDark, KonabBlue)
                            )
                        )
                        .konabHeroShimmer()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Code Gérant",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Code Gérant Requis",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            Text(
                                text = shopProfile.name.ifBlank { "Ma Boutique" },
                                color = Color(0xFFD6E4FA),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
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
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = KonabBlueContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = KonabBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${shopProfile.businessType} • ${shopProfile.city.ifBlank { "Burkina Faso" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KonabBlueDark
                            )
                        }
                    }

                    Text(
                        text = "Entrez le code gérant (PIN administrateur) pour ouvrir la caisse de votre boutique :",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }.take(8)
                            enteredPin = filtered
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        label = { Text("Code Gérant (PIN) *") },
                        placeholder = { Text("Saisissez votre code secret") },
                        leadingIcon = {
                            Icon(Icons.Default.Password, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { pinVisible = !pinVisible },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (pinVisible) "Masquer le code" else "Afficher le code"
                                )
                            }
                        },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { attemptVerify() }
                        ),
                        isError = errorMessage.isNotBlank(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_manager_pin")
                    )

                    if (errorMessage.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = KonabRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errorMessage,
                                color = KonabRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_cancel_manager_pin")
                        ) {
                            Text("Annuler", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { attemptVerify() },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("btn_verify_manager_pin")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Entrer", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (canUseBiometric) {
                        OutlinedButton(
                            onClick = {
                                BiometricSecurityHelper.authenticateWithBiometrics(
                                    context = context,
                                    title = "Accès Boutique • ${shopProfile.name}",
                                    subtitle = "Confirmez votre empreinte digitale pour entrer",
                                    negativeButtonText = "Saisir le code Gérant",
                                    onSuccess = {
                                        errorMessage = ""
                                        onPinVerified()
                                    },
                                    onError = { msg -> errorMessage = msg }
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_modal_biometric_entry")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Empreinte digitale",
                                tint = KonabGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Entrer avec l'empreinte digitale",
                                color = KonabGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Annuaire public des agents déployeurs affiché lorsqu'aucune boutique n'est encore configurée :
 * affiche chaque agent avec sa ville/zone et son numéro, permettant d'appeler ou de laisser un message.
 * Aucun code PIN ou identifiant sensible n'est affiché.
 */
@Composable
fun AgentContactDirectoryModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val syncManager = remember { SupabaseSyncManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCityFilter by remember { mutableStateOf("Toutes") }
    var isRefreshing by remember { mutableStateOf(false) }
    var cloudRevision by remember { mutableStateOf(0) }
    var expandedMessagePhone by remember { mutableStateOf<String?>(null) }
    var showConapesVerifier by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRefreshing = true
        try {
            syncManager.refreshAgentsFromCloud()
            cloudRevision++
        } catch (_: Exception) {}
        isRefreshing = false
    }

    val allPublicAgents = remember(cloudRevision) {
        val combined = mutableListOf<AgentUser>()
        val seenPhones = mutableSetOf<String>()

        // Prioritise cloud/registered agents, then presets
        val candidates = AgentDeploymentStorage.getCloudAgents(context) + AgentDirectory.PRESET_AGENTS
        candidates.forEach { agent ->
            val digits = agent.phone.filter { it.isDigit() }
            val normalizedKey = if (digits.length >= 8) digits.takeLast(8) else digits
            if (digits.isNotBlank() && seenPhones.add(normalizedKey)) {
                combined.add(agent)
            }
        }
        combined
    }

    val availableCities = remember(allPublicAgents) {
        listOf("Toutes") + allPublicAgents
            .map { it.zone.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val filteredAgents = remember(allPublicAgents, searchQuery, selectedCityFilter) {
        val q = searchQuery.trim().lowercase()
        allPublicAgents.filter { agent ->
            val matchesCity = selectedCityFilter == "Toutes" ||
                agent.zone.equals(selectedCityFilter, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                agent.name.lowercase().contains(q) ||
                agent.zone.lowercase().contains(q) ||
                agent.phone.replace(" ", "").contains(q.replace(" ", ""))
            matchesCity && matchesSearch
        }
    }

    val widthClass = LocalKonabWidthClass.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .widthIn(max = if (widthClass == KonabWidthClass.COMPACT) 560.dp else 820.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_agent_directory"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF06245E), Color(0xFF073B9A))
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
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(KonabOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "Agents Konab POS",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contacter un Agent Konab POS",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Boutique non configurée • Création réservée aux agents",
                                color = Color(0xFFFED7AA),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
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

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Explanatory Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = KonabOrange.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = KonabOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Aucune boutique n'est encore activée sur cet appareil. Choisissez un agent selon votre ville pour l'appeler ou lui laisser un message.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showConapesVerifier = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Vérifier une carte KONAB POS",
                                    tint = KonabOrange
                                )
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher une ville ou un agent...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Effacer la recherche")
                                }
                            } else if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_agent_city")
                    )

                    // City Filter Chips
                    if (availableCities.size > 2) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableCities) { city ->
                                FilterChip(
                                    selected = selectedCityFilter == city,
                                    onClick = { selectedCityFilter = city },
                                    label = {
                                        Text(
                                            text = city,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selectedCityFilter == city) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (city != "Toutes") {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    // Agent List (Adaptive Grid on wide screens, 1-column on phones)
                    if (filteredAgents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            KonabEmptyState(
                                icon = Icons.Default.PersonSearch,
                                title = "Aucun agent trouvé",
                                subtitle = "Aucun agent ne correspond à cette recherche ou à cette ville.",
                                accentColor = KonabOrange,
                                actionLabel = "Afficher tous les agents",
                                onAction = {
                                    searchQuery = ""
                                    selectedCityFilter = "Toutes"
                                }
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = if (widthClass == KonabWidthClass.COMPACT) {
                                GridCells.Fixed(1)
                            } else {
                                GridCells.Adaptive(minSize = 290.dp)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = filteredAgents,
                                key = { index, agent -> "${agent.phone}_$index" }
                            ) { index, agent ->
                                val isMessageOpen = expandedMessagePhone == agent.phone
                                KonabStaggeredReveal(index = index) {
                                    AgentContactCard(
                                        agent = agent,
                                        index = index,
                                        isMessageExpanded = isMessageOpen,
                                        onToggleMessage = {
                                            expandedMessagePhone = if (isMessageOpen) null else agent.phone
                                        },
                                        onCallClick = {
                                            val cleanDial = agent.phone.filter { it.isDigit() || it == '+' }
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanDial"))
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Appelez ${agent.name} au ${agent.phone}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        onSendSmsClick = { message ->
                                            val cleanDial = agent.phone.filter { it.isDigit() || it == '+' }
                                            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanDial")).apply {
                                                putExtra("sms_body", message)
                                            }
                                            try {
                                                context.startActivity(smsIntent)
                                            } catch (_: Exception) {
                                                val fallback = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, message)
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(fallback, "Envoyer un message à ${agent.name}"))
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Numéro agent : ${agent.phone}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        onSendWhatsAppClick = { message ->
                                            val waPhone = formatInternationalWaPhone(agent.phone)
                                            val waUri = Uri.parse("https://wa.me/$waPhone?text=${Uri.encode(message)}")
                                            val waIntent = Intent(Intent.ACTION_VIEW, waUri)
                                            try {
                                                context.startActivity(waIntent)
                                            } catch (_: Exception) {
                                                val fallback = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, "$message (Agent: ${agent.name} - ${agent.phone})")
                                                }
                                                try {
                                                    context.startActivity(Intent.createChooser(fallback, "Contacter ${agent.name}"))
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Contact WhatsApp : ${agent.phone}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer Close
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_close_agent_directory")
                        ) {
                            Text("Fermer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showConapesVerifier) {
        ConapesCardReferenceVerifierModal(
            onDismiss = { showConapesVerifier = false }
        )
    }
}

@Composable
private fun AgentContactCard(
    agent: AgentUser,
    index: Int,
    isMessageExpanded: Boolean,
    onToggleMessage: () -> Unit,
    onCallClick: () -> Unit,
    onSendSmsClick: (String) -> Unit,
    onSendWhatsAppClick: (String) -> Unit
) {
    val defaultMsg = remember(agent.name, agent.zone) {
        "Bonjour ${agent.name}, je souhaite créer et activer ma boutique sur KONAB POS (${agent.zone.ifBlank { "Ma ville" }})."
    }
    var customMessage by remember(agent.phone) { mutableStateOf(defaultMsg) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isMessageExpanded) KonabOrange else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .konabPressScale(pressedScale = 0.985f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val context = LocalContext.current
                val effectivePhoto = remember(agent.id, agent.photoUri) {
                    AgentDeploymentStorage.getEffectivePhotoUri(context, agent)
                }
                AgentPhotoAvatar(
                    name = agent.name,
                    photoUri = effectivePhoto,
                    size = 48.dp,
                    borderColor = KonabBlue
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = agent.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = KonabOrange.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = agent.role.ifBlank { "Agent Déployeur" },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Ville",
                            tint = KonabOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = agent.zone.ifBlank { "Burkina Faso" },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KonabLivePulseDot(color = KonabGreen, size = 6.dp)
                        Text(
                            text = agent.phone,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Call & Message Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCallClick,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .konabPressScale()
                        .testTag("btn_call_agent_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Appeler", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onToggleMessage,
                    border = BorderStroke(1.5.dp, KonabOrange),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabOrangeDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .konabPressScale()
                        .testTag("btn_message_agent_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMessageExpanded) "Masquer" else "Laisser un message",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Inline Message Composer
            AnimatedVisibility(visible = isMessageExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        label = { Text("Votre message pour ${agent.name}") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_agent_message_$index")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSendWhatsAppClick(customMessage.ifBlank { defaultMsg }) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_send_whatsapp_agent_$index")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }

                        OutlinedButton(
                            onClick = { onSendSmsClick(customMessage.ifBlank { defaultMsg }) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_send_sms_agent_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Envoyer SMS", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatInternationalWaPhone(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
        digits.startsWith("00226") -> digits.drop(2)
        digits.startsWith("226") && digits.length >= 11 -> digits
        digits.length == 8 -> "226$digits"
        else -> digits
    }
}
