package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.*
import com.example.util.BusinessTypePresets
import com.example.util.LicenseHelper
import com.example.util.ShopLogoAvatar
import com.example.util.ShopLogoHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseSyncManager
import kotlinx.coroutines.launch

data class AgentUser(
    val id: String,
    val name: String,
    val phone: String,
    val zone: String,
    val pin: String,
    val role: String = "Agent Déployeur"
)

object AgentDirectory {
    val PRESET_AGENTS = listOf(
        AgentUser("AGT-101", "Moussa Ouédraogo", "70 12 34 56", "Ouagadougou Centre", "1234"),
        AgentUser("AGT-102", "Fatou Traoré", "76 98 76 54", "Bobo-Dioulasso", "1234"),
        AgentUser("AGT-103", "Ibrahim Sawadogo", "78 55 44 33", "Koudougou & Nord", "1234"),
        AgentUser("SUPERVISEUR", "Direction Konab POS", "65 41 37 99", "National Burkina", "9999", "Superviseur Réseau")
    )

    fun findAgent(id: String, pin: String, context: Context? = null): AgentUser? {
        val cleanId = id.trim().uppercase()
        val cleanPin = pin.trim()
        val preset = PRESET_AGENTS.find { it.id.equals(cleanId, ignoreCase = true) && it.pin == cleanPin }
        if (preset != null) return preset
        if (context != null) {
            val cached = AgentDeploymentStorage.getCloudAgents(context)
            val match = cached.find {
                (it.id.equals(cleanId, ignoreCase = true) || it.phone.replace(" ", "") == cleanId.replace(" ", "")) &&
                it.pin == cleanPin
            }
            if (match != null) return match
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPortalScreen(
    shopProfile: ShopProfileEntity?,
    onUpdateProfile: (ShopProfileEntity) -> Unit,
    onActivateByAgent: (tier: String, days: Int, agentName: String, agentPhone: String, notes: String) -> Boolean,
    onPreloadCatalogue: (businessType: String, onComplete: () -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("konab_agent_session_prefs", Context.MODE_PRIVATE) }

    // Session State
    var currentAgent by remember {
        val savedId = prefs.getString("agent_id", null)
        val savedName = prefs.getString("agent_name", null)
        val savedPhone = prefs.getString("agent_phone", null)
        val savedZone = prefs.getString("agent_zone", null)
        val savedRole = prefs.getString("agent_role", "Agent Déployeur")
        if (!savedId.isNullOrBlank() && !savedName.isNullOrBlank()) {
            mutableStateOf<AgentUser?>(AgentUser(savedId, savedName, savedPhone ?: "", savedZone ?: "", "", savedRole ?: ""))
        } else {
            mutableStateOf<AgentUser?>(null)
        }
    }

    // Login Form Inputs
    var loginIdInput by remember { mutableStateOf("") }
    var loginPinInput by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }

    // Tabs inside Agent Portal
    var selectedPortalTab by remember { mutableStateOf(0) }

    // Activation State
    var activationTier by remember { mutableStateOf(shopProfile?.subscriptionTier ?: "Professionnel") }
    var activationDays by remember { mutableStateOf(365) }
    var activationNotes by remember { mutableStateOf("Installation & formation sur site") }
    var latestActivationKey by remember { mutableStateOf<String?>(null) }
    var showActivationSuccessDialog by remember { mutableStateOf(false) }

    // Shop Config Form inside Agent Portal
    var configName by remember(shopProfile) { mutableStateOf(shopProfile?.name ?: "") }
    var configSlogan by remember(shopProfile) { mutableStateOf(shopProfile?.slogan ?: "") }
    var configPhone by remember(shopProfile) { mutableStateOf(shopProfile?.phone ?: "") }
    var configAddress by remember(shopProfile) { mutableStateOf(shopProfile?.address ?: "") }
    var configCity by remember(shopProfile) { mutableStateOf(shopProfile?.city ?: "") }
    var configCurrency by remember(shopProfile) { mutableStateOf(shopProfile?.currency ?: "FCFA") }
    var configBusinessType by remember(shopProfile) { mutableStateOf(shopProfile?.businessType ?: "Boutique") }
    var configAdminPin by remember(shopProfile) { mutableStateOf(shopProfile?.adminPin ?: "1234") }
    var configCashierPin by remember(shopProfile) { mutableStateOf(shopProfile?.cashierPin ?: "0000") }
    var configReceiptFooter by remember(shopProfile) { mutableStateOf(shopProfile?.receiptFooter ?: "") }
    var configTaxRate by remember(shopProfile) { mutableStateOf(shopProfile?.taxRate?.toString() ?: "0.0") }
    var configLogoUri by remember(shopProfile) { mutableStateOf(shopProfile?.logoUri) }
    var showLogoDialog by remember { mutableStateOf(false) }

    // Photo picker for logo inside agent portal
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ShopLogoHelper.saveLogoToInternalStorage(context, uri)
            if (savedPath != null) {
                configLogoUri = savedPath
                Toast.makeText(context, "Logo importé avec succès !", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Portail Agent Konab",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Surface(
                                color = KonabOrange,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (currentAgent != null) "Agent : ${currentAgent?.name} (${currentAgent?.zone})" else "Connexion déployeur agréé",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_agent_portal")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (currentAgent != null) {
                        IconButton(
                            onClick = {
                                prefs.edit().clear().apply()
                                currentAgent = null
                                Toast.makeText(context, "Agent déconnecté", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("btn_logout_agent")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = "Déconnexion", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val agent = currentAgent
            if (agent == null) {
                // ==========================================
                // VUE 1 : CONNEXION AGENT REQUIS
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Agent Portal Hero Badge
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF06245E))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(KonabOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Espace Agent Commercial & Technique",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Réservé aux agents officiels Konab POS pour la configuration des boutiques, la gestion des abonnements et l'activation des licences sur le terrain.",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // Login Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Identification de l'Agent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = loginIdInput,
                                onValueChange = {
                                    loginIdInput = it
                                    loginError = ""
                                },
                                label = { Text("Code Agent ou Identifiant") },
                                placeholder = { Text("Ex: AGT-101 ou SUPERVISEUR") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_agent_id")
                            )

                            OutlinedTextField(
                                value = loginPinInput,
                                onValueChange = {
                                    loginPinInput = it
                                    loginError = ""
                                },
                                label = { Text("Code PIN Agent") },
                                placeholder = { Text("Ex: 1234") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_agent_pin")
                            )

                            if (loginError.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = loginError,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val matched = AgentDirectory.findAgent(loginIdInput, loginPinInput)
                                    if (matched != null) {
                                        prefs.edit()
                                            .putString("agent_id", matched.id)
                                            .putString("agent_name", matched.name)
                                            .putString("agent_phone", matched.phone)
                                            .putString("agent_zone", matched.zone)
                                            .putString("agent_role", matched.role)
                                            .apply()
                                        currentAgent = matched
                                        Toast.makeText(context, "Bienvenue ${matched.name} !", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginError = "Code Agent ou PIN incorrect. Vérifiez vos identifiants ou utilisez un compte test ci-dessous."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_agent_login"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ouvrir le portail agent", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Quick Agent Selector (Aide au déploiement rapide)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Agents de Déploiement Enregistrés (Sélection rapide) :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AgentDirectory.PRESET_AGENTS.forEach { agt ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            loginIdInput = agt.id
                                            loginPinInput = agt.pin
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "${agt.name} (${agt.id})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${agt.zone} • ${agt.phone} • PIN: ${agt.pin}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = "Remplir",
                                            tint = KonabBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // VUE 2 : PORTAIL AGENT ACTIF & CONNECTÉ
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Agent Active Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF06245E)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(KonabOrange),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = agent.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = agent.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${agent.role} • ${agent.zone}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }

                            // Current shop status badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = shopProfile?.name ?: "Boutique",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Navigation Tabs
                    PrimaryTabRow(
                        selectedTabIndex = selectedPortalTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedPortalTab == 0,
                            onClick = { selectedPortalTab = 0 },
                            text = { Text("Abonnement", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 1,
                            onClick = { selectedPortalTab = 1 },
                            text = { Text("Configuration", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 2,
                            onClick = { selectedPortalTab = 2 },
                            text = { Text("Catalogue", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 3,
                            onClick = { selectedPortalTab = 3 },
                            text = { Text("Historique", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    // Tab Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedPortalTab) {
                            // ==========================================
                            // TAB 0 : ACTIVATION D'ABONNEMENT
                            // ==========================================
                            0 -> {
                                // Status Card
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "État Actuel de la Boutique",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = shopProfile?.name ?: "Boutique non nommée",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 16.sp
                                                )
                                                Text(
                                                    text = "Tél : ${shopProfile?.phone} • ${shopProfile?.city}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = shopProfile?.subscriptionTier?.uppercase() ?: "GRATUIT",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFB45309),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                        val expiresAt = shopProfile?.subscriptionExpiresAt ?: 0L
                                        val now = System.currentTimeMillis()
                                        val isExpired = expiresAt > 0L && expiresAt < now
                                        val daysRemaining = if (expiresAt > now) ((expiresAt - now) / 86400000L).toInt() else 0

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (expiresAt <= 0L) "Aucune licence active" else if (isExpired) "Licence Expirée" else "Valable encore $daysRemaining jour(s)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isExpired || expiresAt <= 0L) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                            )
                                            if (expiresAt > 0L) {
                                                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                                Text(
                                                    text = "Expire le : ${sdf.format(Date(expiresAt))}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                // Activation Form
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "Activer un Nouvel Abonnement",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        // Formula selection
                                        Text("1. Choisir la formule :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        val tiersList = listOf("Standard", "Professionnel", "Entreprise", "Premium")
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            tiersList.forEach { t ->
                                                FilterChip(
                                                    selected = activationTier == t,
                                                    onClick = { activationTier = t },
                                                    label = { Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // Duration selection
                                        Text("2. Choisir la durée :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        val durations = listOf(
                                            Pair("1 Mois", 30),
                                            Pair("3 Mois", 90),
                                            Pair("6 Mois", 180),
                                            Pair("1 An", 365),
                                            Pair("Permanent", 3650)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            durations.take(3).forEach { d ->
                                                FilterChip(
                                                    selected = activationDays == d.second,
                                                    onClick = { activationDays = d.second },
                                                    label = { Text(d.first, fontSize = 10.sp) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            durations.drop(3).forEach { d ->
                                                FilterChip(
                                                    selected = activationDays == d.second,
                                                    onClick = { activationDays = d.second },
                                                    label = { Text(d.first, fontSize = 11.sp) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // Note input
                                        OutlinedTextField(
                                            value = activationNotes,
                                            onValueChange = { activationNotes = it },
                                            label = { Text("Note d'intervention / Référence de paiement") },
                                            placeholder = { Text("Ex: Reçu Orange Money #784512") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Summary Box
                                        val newExpiry = System.currentTimeMillis() + (activationDays.toLong() * 86400000L)
                                        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFECFDF5),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Récapitulatif de l'activation :",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF065F46)
                                                )
                                                Text(
                                                    text = "Formule $activationTier • Durée $activationDays jours",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF047857)
                                                )
                                                Text(
                                                    text = "Date d'expiration : ${sdf.format(Date(newExpiry))}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF065F46)
                                                )
                                            }
                                        }

                                        // Action Button
                                        Button(
                                            onClick = {
                                                val success = onActivateByAgent(
                                                    activationTier,
                                                    activationDays,
                                                    agent.name,
                                                    agent.phone,
                                                    activationNotes
                                                )
                                                if (success) {
                                                    val durationCode = when (activationDays) {
                                                        30 -> "1M"
                                                        90 -> "3M"
                                                        180 -> "6M"
                                                        3650 -> "VIE"
                                                        else -> "1A"
                                                    }
                                                    val keyData = LicenseHelper.generateKey(
                                                        tier = activationTier,
                                                        durationCode = durationCode,
                                                        beneficiary = shopProfile?.name ?: "Boutique",
                                                        note = "Agent: ${agent.name} • ${agent.phone}"
                                                    )
                                                    latestActivationKey = keyData.key
                                                    showActivationSuccessDialog = true
                                                    Toast.makeText(context, "Abonnement $activationTier activé avec succès !", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_agent_activate_sub"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                                        ) {
                                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Activer et Valider l'Abonnement", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            // ==========================================
                            // TAB 1 : CONFIGURATION DE LA BOUTIQUE
                            // ==========================================
                            1 -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "Configuration Complète de la Boutique",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        // Logo Section
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showLogoDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                ShopLogoAvatar(
                                                    logoUri = configLogoUri,
                                                    size = 56.dp,
                                                    shape = RoundedCornerShape(14.dp),
                                                    showEditBadge = true
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Logo de la boutique", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        if (configLogoUri.isNullOrBlank()) "Logo Konab officiel par défaut" else "Logo personnalisé actif",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { showLogoDialog = true },
                                                    modifier = Modifier.height(34.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Changer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = configName,
                                            onValueChange = { configName = it },
                                            label = { Text("Nom de la boutique / Commerce *") },
                                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("input_agent_shop_name")
                                        )

                                        OutlinedTextField(
                                            value = configSlogan,
                                            onValueChange = { configSlogan = it },
                                            label = { Text("Slogan / Spécialité") },
                                            leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = configPhone,
                                            onValueChange = { configPhone = it },
                                            label = { Text("Téléphone / WhatsApp de contact *") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("input_agent_shop_phone")
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = configCity,
                                                onValueChange = { configCity = it },
                                                label = { Text("Ville") },
                                                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = configCurrency,
                                                onValueChange = { configCurrency = it },
                                                label = { Text("Devise") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = configAddress,
                                            onValueChange = { configAddress = it },
                                            label = { Text("Adresse / Emplacement exact") },
                                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Business Type selection
                                        Text("Type d'activité commerciale :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        val types = BusinessTypePresets.getAllTypes()
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            types.chunked(3).forEach { rowTypes ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    rowTypes.forEach { t ->
                                                        FilterChip(
                                                            selected = configBusinessType == t,
                                                            onClick = { configBusinessType = t },
                                                            label = { Text(t, fontSize = 10.sp) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // PIN Codes Section
                                        Text("Sécurité & Accès de la Caisse :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = configAdminPin,
                                                onValueChange = { configAdminPin = it },
                                                label = { Text("PIN Gérant") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = configCashierPin,
                                                onValueChange = { configCashierPin = it },
                                                label = { Text("PIN Caisse") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = configReceiptFooter,
                                            onValueChange = { configReceiptFooter = it },
                                            label = { Text("Message de remerciement sur les tickets") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                                            singleLine = false,
                                            maxLines = 2,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Button(
                                            onClick = {
                                                val current = shopProfile ?: ShopProfileEntity()
                                                val updated = current.copy(
                                                    name = configName.trim().ifBlank { current.name },
                                                    slogan = configSlogan.trim(),
                                                    phone = configPhone.trim().ifBlank { current.phone },
                                                    address = configAddress.trim(),
                                                    city = configCity.trim(),
                                                    currency = configCurrency.trim().ifBlank { "FCFA" },
                                                    businessType = configBusinessType,
                                                    adminPin = configAdminPin.trim().ifBlank { "1234" },
                                                    cashierPin = configCashierPin.trim().ifBlank { "0000" },
                                                    receiptFooter = configReceiptFooter.trim(),
                                                    taxRate = configTaxRate.toDoubleOrNull() ?: 0.0,
                                                    logoUri = configLogoUri,
                                                    isConfigured = true
                                                )
                                                onUpdateProfile(updated)
                                                Toast.makeText(context, "Configuration de la boutique enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_agent_save_shop"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Enregistrer et Appliquer la Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            // ==========================================
                            // TAB 2 : CATALOGUE PRODUITS DE DÉMARRAGE
                            // ==========================================
                            2 -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(
                                            text = "Initialisation Rapide du Catalogue",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Text(
                                            text = "L'agent peut injecter directement le pack de produits standards pour le type de commerce sélectionné afin que le commerçant puisse commencer à vendre immédiatement.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )

                                        val currentPreset = BusinessTypePresets.getPreset(configBusinessType)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "Catalogue : ${currentPreset.title} (${currentPreset.defaultProducts.size} articles prêts)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "Catégories incluses : ${currentPreset.defaultCategories.joinToString(", ")}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onPreloadCatalogue(configBusinessType) {
                                                    Toast.makeText(context, "Catalogue de ${currentPreset.defaultProducts.size} produits ajouté avec succès !", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("btn_agent_preload_catalogue"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Injecter le catalogue dans la boutique", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            // ==========================================
                            // TAB 3 : HISTORIQUE DES LICENCES & REÇUS
                            // ==========================================
                            3 -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Licences Générées & Historique",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        val savedLicences = LicenseHelper.getSavedLicenses(context)
                                        if (savedLicences.isEmpty()) {
                                            Text(
                                                text = "Aucune licence enregistrée pour l'instant.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        } else {
                                            savedLicences.take(10).forEach { lic ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = lic.key,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = Color(0xFFFEF3C7)
                                                            ) {
                                                                Text(
                                                                    text = lic.tier,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 10.sp,
                                                                    color = Color(0xFFB45309),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "Bénéficiaire : ${lic.beneficiary.ifBlank { "Client" }} • Durée : ${lic.durationLabel}",
                                                            fontSize = 11.sp
                                                        )
                                                        if (lic.note.isNotBlank()) {
                                                            Text(
                                                                text = lic.note,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    val shareText = """
                                                                        🧾 *ATTESTATION D'ACTIVATION KONAB POS*
                                                                        
                                                                        Boutique : ${lic.beneficiary}
                                                                        Formule : ${lic.tier}
                                                                        Durée : ${lic.durationLabel}
                                                                        Clé de licence : ${lic.key}
                                                                        Agent Konab : ${agent.name} (${agent.phone})
                                                                        
                                                                        Merci pour votre confiance avec Konab POS !
                                                                    """.trimIndent()

                                                                    val sendIntent = Intent().apply {
                                                                        action = Intent.ACTION_SEND
                                                                        putExtra(Intent.EXTRA_SUBJECT, "Attestation d'activation Konab POS")
                                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                                        type = "text/plain"
                                                                    }
                                                                    context.startActivity(Intent.createChooser(sendIntent, "Partager le reçu"))
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Partager l'attestation", fontSize = 11.sp)
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
                }
            }
        }
    }

    // ==========================================
    // MODAL : SÉLECTEUR DE LOGO DANS LE PORTAIL
    // ==========================================
    if (showLogoDialog) {
        AlertDialog(
            onDismissRequest = { showLogoDialog = false },
            title = { Text("Logo de la boutique", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                            showLogoDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF073B9A))
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choisir une image dans la galerie")
                    }

                    HorizontalDivider()

                    Text("Ou choisir un logo prédéfini :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    ShopLogoHelper.PRESET_LOGOS.forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    configLogoUri = preset.key
                                    showLogoDialog = false
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = preset.drawableRes),
                                    contentDescription = preset.label,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(preset.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogoDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // ==========================================
    // MODAL : SUCCÈS ACTIVATION ABONNEMENT
    // ==========================================
    if (showActivationSuccessDialog) {
        val agent = currentAgent
        AlertDialog(
            onDismissRequest = { showActivationSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF059669))
                    Text("Abonnement Activé !", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("L'abonnement a été immédiatement configuré et appliqué à la boutique.")
                    Text("Formule : $activationTier ($activationDays jours)", fontWeight = FontWeight.Bold)
                    if (latestActivationKey != null) {
                        Text("Clé générée : $latestActivationKey", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Un reçu numérique d'activation est disponible pour être transmis au commerçant.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareText = """
                            🎉 *CONFIRMATION D'ACTIVATION KONAB POS*
                            
                            Boutique : ${shopProfile?.name}
                            Formule : $activationTier
                            Durée : $activationDays jours
                            Clé de licence : $latestActivationKey
                            Agent Déployeur : ${agent?.name} (${agent?.phone})
                            
                            L'application est 100% fonctionnelle et prête pour vos encaissements !
                        """.trimIndent()

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_SUBJECT, "Reçu d'activation Konab POS")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Envoyer au commerçant"))
                        showActivationSuccessDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Partager l'Attestation WhatsApp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActivationSuccessDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}
