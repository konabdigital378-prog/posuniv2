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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.ui.theme.*
import com.example.util.BusinessTypePresets
import com.example.util.LicenseHelper
import com.example.util.ShopLogoAvatar
import com.example.util.ShopLogoHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val preset = PRESET_AGENTS.find {
            (it.id.equals(cleanId, ignoreCase = true) || it.phone.replace(" ", "") == cleanId.replace(" ", "")) &&
            it.pin == cleanPin
        }
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
    val coroutineScope = rememberCoroutineScope()
    val syncManager = remember { SupabaseSyncManager(context) }

    // Session State
    var currentAgent by remember {
        mutableStateOf<AgentUser?>(AgentDeploymentStorage.getSavedAgentSession(context))
    }

    // Auth Mode when logged out: 0 = Connexion, 1 = Création de profil
    var authModeTab by remember { mutableStateOf(0) }

    // Login Form Inputs
    var loginIdInput by remember { mutableStateOf("") }
    var loginPinInput by remember { mutableStateOf("") }
    var showLoginPin by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Registration Form Inputs
    var regNameInput by remember { mutableStateOf("") }
    var regPhoneInput by remember { mutableStateOf("") }
    var regZoneInput by remember { mutableStateOf("Ouagadougou") }
    var regIdInput by remember { mutableStateOf("AGT-${(100..999).random()}") }
    var regPinInput by remember { mutableStateOf("") }
    var regPinConfirmInput by remember { mutableStateOf("") }
    var showRegPin by remember { mutableStateOf(false) }
    var regError by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }

    // Tabs inside Agent Portal when logged in
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
    var configAdminPin by remember(shopProfile) { mutableStateOf(shopProfile?.adminPin ?: "") }
    var configCashierPin by remember(shopProfile) { mutableStateOf(shopProfile?.cashierPin ?: "") }
    var showConfigPins by remember { mutableStateOf(false) }
    var configReceiptFooter by remember(shopProfile) { mutableStateOf(shopProfile?.receiptFooter ?: "") }
    var configTaxRate by remember(shopProfile) { mutableStateOf(shopProfile?.taxRate?.toString() ?: "0.0") }
    var configLogoUri by remember(shopProfile) { mutableStateOf(shopProfile?.logoUri) }
    var showLogoDialog by remember { mutableStateOf(false) }

    // Deployments list & search
    var deployedSearchQuery by remember { mutableStateOf("") }
    var isSyncingPortalDeployments by remember { mutableStateOf(false) }
    var portalDeployments by remember(currentAgent) {
        mutableStateOf(currentAgent?.let { AgentDeploymentStorage.getDeploymentsForAgent(context, it.id) } ?: emptyList())
    }

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

    // Refresh deployments on launch if agent is connected
    LaunchedEffect(currentAgent) {
        val agt = currentAgent
        if (agt != null) {
            portalDeployments = AgentDeploymentStorage.getDeploymentsForAgent(context, agt.id)
            try {
                val res = syncManager.refreshDeploymentsForAgent(agt.id)
                if (res.isSuccess) {
                    portalDeployments = res.getOrDefault(AgentDeploymentStorage.getDeploymentsForAgent(context, agt.id))
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFF7900),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Espace Agent Déployeur",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Surface(
                                    color = Color(0xFF06245E),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "OFFICIEL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFFA43A),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (currentAgent != null) "${currentAgent?.name} • ${currentAgent?.zone}" else "Portail d'authentification et de configuration",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                AgentDeploymentStorage.clearAgentSession(context)
                                currentAgent = null
                                Toast.makeText(context, "Session agent fermée avec succès", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("btn_logout_agent")
                        ) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Déconnexion",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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
                // VUE 1 : PORTAIL NON CONNECTÉ (CONNEXION & INSCRIPTION AGENT)
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Executive Hero Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF06245E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF06245E),
                                            Color(0xFF073B9A)
                                        )
                                    )
                                )
                                .padding(22.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    modifier = Modifier.size(68.dp),
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.12f),
                                    border = BorderStroke(2.dp, Color(0xFFFFA43A))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color(0xFFFFA43A),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Réseau Officiel Déployeurs Konab POS",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Portail d'intervention terrain pour l'initialisation des commerces, l'activation des abonnements et la synchronisation avec le cloud central.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 6.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Text(
                                                text = "Sécurisé Supabase Cloud",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6EE7B7)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "100% Fonctionnel Hors-Ligne",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Mode Toggle (Connexion / Inscription)
                    PrimaryTabRow(
                        selectedTabIndex = authModeTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFFFF7900),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        Tab(
                            selected = authModeTab == 0,
                            onClick = {
                                authModeTab = 0
                                loginError = ""
                            },
                            text = { Text("Se Connecter", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = authModeTab == 1,
                            onClick = {
                                authModeTab = 1
                                regError = ""
                            },
                            text = { Text("Créer un Profil", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    if (authModeTab == 0) {
                        // ==========================================
                        // FORMULAIRE DE CONNEXION AGENT
                        // ==========================================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.LockPerson, contentDescription = null, tint = Color(0xFF073B9A))
                                    Text(
                                        text = "Identification de l'Agent",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                OutlinedTextField(
                                    value = loginIdInput,
                                    onValueChange = {
                                        loginIdInput = it
                                        loginError = ""
                                    },
                                    label = { Text("Matricule ou N° Téléphone Agent") },
                                    placeholder = { Text("Ex: Votre identifiant ou numéro") },
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_agent_id")
                                )

                                OutlinedTextField(
                                    value = loginPinInput,
                                    onValueChange = {
                                        loginPinInput = it
                                        loginError = ""
                                    },
                                    label = { Text("Code PIN Agent") },
                                    placeholder = { Text("Code confidentiel (4 à 8 chiffres)") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showLoginPin = !showLoginPin }) {
                                            Icon(
                                                imageVector = if (showLoginPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showLoginPin) "Masquer" else "Afficher"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showLoginPin) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_agent_pin")
                                )

                                if (loginError.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Text(
                                                text = loginError,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (loginIdInput.isBlank() || loginPinInput.isBlank()) {
                                            loginError = "Veuillez renseigner votre identifiant et votre code PIN."
                                            return@Button
                                        }
                                        isLoggingIn = true
                                        loginError = ""
                                        coroutineScope.launch {
                                            val res = syncManager.loginAgentOnline(loginIdInput, loginPinInput)
                                            isLoggingIn = false
                                            val matched = res.getOrNull()
                                            if (matched != null) {
                                                AgentDeploymentStorage.saveAgentSession(context, matched)
                                                AgentDeploymentStorage.registerLocalAgent(context, matched)
                                                currentAgent = matched
                                                portalDeployments = AgentDeploymentStorage.getDeploymentsForAgent(context, matched.id)
                                                val depRes = syncManager.refreshDeploymentsForAgent(matched.id)
                                                if (depRes.isSuccess) {
                                                    portalDeployments = depRes.getOrDefault(portalDeployments)
                                                }
                                                Toast.makeText(context, "Session ouverte : ${matched.name}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                loginError = "Code Agent ou code PIN incorrect. Veuillez vérifier vos accès ou vous inscrire dans l'onglet 'Créer un Profil'."
                                            }
                                        }
                                    },
                                    enabled = !isLoggingIn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_agent_login"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF073B9A))
                                ) {
                                    if (isLoggingIn) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Vérification en cours...")
                                    } else {
                                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Ouvrir le portail agent", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // ==========================================
                        // FORMULAIRE DE CRÉATION DE PROFIL AGENT
                        // ==========================================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFFFF7900))
                                    Text(
                                        text = "Création du Profil Déployeur",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "Renseignez vos coordonnées professionnelles. Votre profil sera sauvegardé en local et synchronisé automatiquement avec Supabase Cloud.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )

                                OutlinedTextField(
                                    value = regNameInput,
                                    onValueChange = {
                                        regNameInput = it
                                        regError = ""
                                    },
                                    label = { Text("Nom et Prénoms de l'Agent *") },
                                    placeholder = { Text("Ex: Votre nom complet") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_name")
                                )

                                OutlinedTextField(
                                    value = regPhoneInput,
                                    onValueChange = {
                                        regPhoneInput = it
                                        regError = ""
                                    },
                                    label = { Text("Numéro WhatsApp / Téléphone *") },
                                    placeholder = { Text("Ex: +226 70 00 00 00") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_phone")
                                )

                                OutlinedTextField(
                                    value = regZoneInput,
                                    onValueChange = {
                                        regZoneInput = it
                                        regError = ""
                                    },
                                    label = { Text("Zone ou Ville d'intervention *") },
                                    placeholder = { Text("Ex: Ouagadougou Centre, Bobo, Koudougou...") },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_zone")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = regPinInput,
                                        onValueChange = {
                                            regPinInput = it
                                            regError = ""
                                        },
                                        label = { Text("Code PIN secret *") },
                                        placeholder = { Text("4 à 8 chiffres") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        visualTransformation = if (showRegPin) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).testTag("input_reg_agent_pin")
                                    )

                                    OutlinedTextField(
                                        value = regPinConfirmInput,
                                        onValueChange = {
                                            regPinConfirmInput = it
                                            regError = ""
                                        },
                                        label = { Text("Confirmer PIN *") },
                                        placeholder = { Text("Répéter") },
                                        visualTransformation = if (showRegPin) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).testTag("input_reg_agent_pin_confirm")
                                    )
                                }

                                if (regError.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = regError,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (regNameInput.isBlank() || regPhoneInput.isBlank()) {
                                            regError = "Veuillez renseigner votre nom et votre numéro de téléphone."
                                            return@Button
                                        }
                                        if (regPinInput.length < 4) {
                                            regError = "Le code PIN doit comporter au moins 4 chiffres."
                                            return@Button
                                        }
                                        if (regPinInput != regPinConfirmInput) {
                                            regError = "Les deux codes PIN ne correspondent pas."
                                            return@Button
                                        }
                                        isRegistering = true
                                        regError = ""
                                        coroutineScope.launch {
                                            val newAgent = AgentUser(
                                                id = regIdInput.trim().uppercase(),
                                                name = regNameInput.trim(),
                                                phone = regPhoneInput.trim(),
                                                zone = regZoneInput.trim().ifBlank { "National" },
                                                pin = regPinInput.trim(),
                                                role = "Agent Déployeur"
                                            )
                                            val regResult = syncManager.registerAgentOnline(newAgent)
                                            isRegistering = false
                                            if (regResult.isSuccess) {
                                                currentAgent = newAgent
                                                AgentDeploymentStorage.saveAgentSession(context, newAgent)
                                                portalDeployments = AgentDeploymentStorage.getDeploymentsForAgent(context, newAgent.id)
                                                Toast.makeText(context, "Profil créé avec succès ! Bienvenue ${newAgent.name}.", Toast.LENGTH_LONG).show()
                                            } else {
                                                regError = "Erreur lors de l'enregistrement. Veuillez réessayer."
                                            }
                                        }
                                    },
                                    enabled = !isRegistering,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("btn_submit_agent_register"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900))
                                ) {
                                    if (isRegistering) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Création du profil...")
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Valider & Activer mon Profil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Security & Confidentiality Notice
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF073B9A), modifier = Modifier.size(22.dp))
                            Text(
                                text = "Charte de Déploiement : L'agent s'engage à préserver la stricte confidentialité des données des commerçants et à ne jamais conserver leurs codes PIN de caisse.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // VUE 2 : PORTAIL AGENT ACTIF & CONNECTÉ
                // ==========================================
                Column(modifier = Modifier.fillMaxSize()) {
                    // Agent Executive Banner
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF06245E)
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
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(46.dp),
                                        shape = CircleShape,
                                        color = Color(0xFFFF7900),
                                        border = BorderStroke(2.dp, Color(0xFFFFA43A))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = agent.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = agent.name,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF10B981)
                                            ) {
                                                Text(
                                                    text = "AGRÉÉ",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Matricule: ${agent.id} • ${agent.zone}",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFFE2E8F0)
                                        )
                                        Text(
                                            text = "Tél: ${agent.phone}",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFA43A)
                                        )
                                    }
                                }

                                // Quick Status Indicator
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White.copy(alpha = 0.16f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Text(
                                                text = "Cloud Prêt",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${portalDeployments.size} boutique(s)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }

                    // Navigation Tabs
                    PrimaryTabRow(
                        selectedTabIndex = selectedPortalTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = Color(0xFFFF7900)
                    ) {
                        Tab(
                            selected = selectedPortalTab == 0,
                            onClick = { selectedPortalTab = 0 },
                            text = { Text("Abonnement", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                            icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(17.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 1,
                            onClick = { selectedPortalTab = 1 },
                            text = { Text("Configuration", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                            icon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(17.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 2,
                            onClick = { selectedPortalTab = 2 },
                            text = { Text("Catalogue", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                            icon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(17.dp)) }
                        )
                        Tab(
                            selected = selectedPortalTab == 3,
                            onClick = { selectedPortalTab = 3 },
                            text = { Text("Boutiques", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) },
                            icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(17.dp)) }
                        )
                    }

                    // Scrollable Tab Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Quick Context: Current Shop Status Bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Boutique en cours d'intervention :",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = shopProfile?.name?.ifBlank { "Boutique non nommée" } ?: "Boutique non nommée",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Tél: ${shopProfile?.phone?.ifBlank { "Non renseigné" } ?: "Non renseigné"} • ${shopProfile?.city?.ifBlank { "Burkina Faso" } ?: "Burkina Faso"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFEF3C7),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                    ) {
                                        Text(
                                            text = shopProfile?.subscriptionTier?.uppercase() ?: "GRATUIT",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (expiresAt <= 0L) "Aucune licence active" else if (isExpired) "Licence Expirée" else "Valable encore $daysRemaining jour(s)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpired || expiresAt <= 0L) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                    )
                                    if (expiresAt > 0L) {
                                        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                        Text(
                                            text = "Échéance : ${sdf.format(Date(expiresAt))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        when (selectedPortalTab) {
                            // ==========================================
                            // TAB 0 : ACTIVATION D'ABONNEMENT
                            // ==========================================
                            0 -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981))
                                            Text(
                                                text = "Attribution d'une Licence Officielle",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Formula selection
                                        Text("1. Sélectionner la Formule :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        Text("2. Durée de validité :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                                    label = { Text(d.first, fontSize = 10.5.sp) },
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
                                            label = { Text("Référence de paiement / Observation") },
                                            placeholder = { Text("Ex: Reçu Orange Money #123456 ou Moov") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Summary Box
                                        val newExpiry = System.currentTimeMillis() + (activationDays.toLong() * 86400000L)
                                        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFECFDF5),
                                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = "Récapitulatif de l'activation :",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF065F46)
                                                )
                                                Text(
                                                    text = "Formule $activationTier • Durée : $activationDays jours",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp,
                                                    color = Color(0xFF047857)
                                                )
                                                Text(
                                                    text = "Date d'expiration calculée : ${sdf.format(Date(newExpiry))}",
                                                    fontSize = 11.5.sp,
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
                                                        note = "Agent: ${agent.name} (${agent.phone})"
                                                    )
                                                    val deployment = AgentDeploymentRecord(
                                                        id = "DEP-${System.currentTimeMillis()}",
                                                        agentId = agent.id,
                                                        agentName = agent.name,
                                                        agentPhone = agent.phone,
                                                        shopName = shopProfile?.name ?: "Boutique",
                                                        shopPhone = shopProfile?.phone ?: "",
                                                        shopCity = shopProfile?.city ?: "",
                                                        tier = activationTier,
                                                        durationDays = activationDays,
                                                        licenseKey = keyData.key,
                                                        notes = activationNotes,
                                                        timestamp = System.currentTimeMillis(),
                                                        isSynced = false
                                                    )
                                                    AgentDeploymentStorage.saveDeployment(context, deployment)
                                                    portalDeployments = AgentDeploymentStorage.getDeploymentsForAgent(context, agent.id)

                                                    // Background Cloud Sync
                                                    coroutineScope.launch {
                                                        try {
                                                            syncManager.syncAgentDeployments()
                                                        } catch (_: Exception) {}
                                                    }
                                                    latestActivationKey = keyData.key
                                                    showActivationSuccessDialog = true
                                                    Toast.makeText(context, "Abonnement $activationTier validé avec succès !", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_agent_activate_sub"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Valider & Activer la Licence", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF073B9A))
                                            Text(
                                                text = "Paramétrage Complet du Commerce",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        // Logo Section
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showLogoDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
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
                                                        if (configLogoUri.isNullOrBlank()) "Logo standard Konab" else "Logo personnalisé actif",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { showLogoDialog = true },
                                                    modifier = Modifier.height(36.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Modifier", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = configName,
                                            onValueChange = { configName = it },
                                            label = { Text("Nom du Commerce / Raison Sociale *") },
                                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("input_agent_shop_name")
                                        )

                                        OutlinedTextField(
                                            value = configSlogan,
                                            onValueChange = { configSlogan = it },
                                            label = { Text("Slogan / Spécialité") },
                                            placeholder = { Text("Ex: Le meilleur rapport qualité-prix") },
                                            leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = configPhone,
                                            onValueChange = { configPhone = it },
                                            label = { Text("Téléphone / WhatsApp commercial *") },
                                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
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
                                                placeholder = { Text("Ex: Ouagadougou") },
                                                leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = configCurrency,
                                                onValueChange = { configCurrency = it },
                                                label = { Text("Devise") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = configAddress,
                                            onValueChange = { configAddress = it },
                                            label = { Text("Adresse / Quartier") },
                                            placeholder = { Text("Ex: Secteur 15, face au marché") },
                                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Business Type selection
                                        Text("Type d'activité commerciale :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        val types = BusinessTypePresets.getAllTypes()
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            types.chunked(3).forEach { rowTypes ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    rowTypes.forEach { t ->
                                                        val preset = BusinessTypePresets.getPreset(t)
                                                        FilterChip(
                                                            selected = configBusinessType == t,
                                                            onClick = { configBusinessType = t },
                                                            label = { Text("${preset.emoji} $t", fontSize = 10.sp, maxLines = 1) },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    repeat(3 - rowTypes.size) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        // PIN Codes Section
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Sécurité & Accès de Caisse :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            TextButton(onClick = { showConfigPins = !showConfigPins }) {
                                                Icon(
                                                    imageVector = if (showConfigPins) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (showConfigPins) "Masquer" else "Afficher", fontSize = 11.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = configAdminPin,
                                                onValueChange = { configAdminPin = it },
                                                label = { Text("PIN Gérant") },
                                                placeholder = { Text("4 chiffres") },
                                                visualTransformation = if (showConfigPins) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )

                                            OutlinedTextField(
                                                value = configCashierPin,
                                                onValueChange = { configCashierPin = it },
                                                label = { Text("PIN Caisse") },
                                                placeholder = { Text("4 chiffres") },
                                                visualTransformation = if (showConfigPins) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = configReceiptFooter,
                                            onValueChange = { configReceiptFooter = it },
                                            label = { Text("Message de remerciement sur les tickets") },
                                            placeholder = { Text("Ex: Merci pour votre visite et à très bientôt !") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                                            singleLine = false,
                                            maxLines = 2,
                                            shape = RoundedCornerShape(12.dp),
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
                                                    adminPin = configAdminPin.trim().ifBlank { current.adminPin },
                                                    cashierPin = configCashierPin.trim().ifBlank { current.cashierPin },
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF073B9A))
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Enregistrer les Paramètres", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFF0284C7))
                                            Text(
                                                text = "Pré-chargement du Catalogue Métier",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Text(
                                            text = "Injectez en 1 clic la base d'articles correspondant à l'activité choisie (${configBusinessType}) afin que le commerçant puisse commencer à vendre immédiatement sans saisie manuelle préalable.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 17.sp
                                        )

                                        val currentPreset = BusinessTypePresets.getPreset(configBusinessType)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Pack : ${currentPreset.title}",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 14.sp
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFE0F2FE)
                                                    ) {
                                                        Text(
                                                            text = "${currentPreset.defaultProducts.size} articles prêts",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0369A1),
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Familles : ${currentPreset.defaultCategories.joinToString(", ")}",
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onPreloadCatalogue(configBusinessType) {
                                                    Toast.makeText(context, "Pack de ${currentPreset.defaultProducts.size} articles injecté avec succès !", Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_agent_preload_catalogue"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Injecter les articles dans la caisse", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        }
                                    }
                                }
                            }

                            // ==========================================
                            // TAB 3 : BOUTIQUES DÉPLOYÉES & REÇUS
                            // ==========================================
                            3 -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Boutiques Déployées (${portalDeployments.size})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Historique rattaché au matricule ${agent.id}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF16A34A)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isSyncingPortalDeployments = true
                                                        try {
                                                            syncManager.syncAgentDeployments()
                                                            val res = syncManager.refreshDeploymentsForAgent(agent.id)
                                                            if (res.isSuccess) {
                                                                portalDeployments = res.getOrDefault(portalDeployments)
                                                                Toast.makeText(context, "Données synchronisées avec Supabase !", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (_: Exception) {}
                                                        isSyncingPortalDeployments = false
                                                    }
                                                }
                                            ) {
                                                if (isSyncingPortalDeployments) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = Color(0xFF073B9A))
                                                }
                                            }
                                        }

                                        // Search Bar
                                        if (portalDeployments.isNotEmpty()) {
                                            OutlinedTextField(
                                                value = deployedSearchQuery,
                                                onValueChange = { deployedSearchQuery = it },
                                                label = { Text("Rechercher un commerce...") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                trailingIcon = {
                                                    if (deployedSearchQuery.isNotBlank()) {
                                                        IconButton(onClick = { deployedSearchQuery = "" }) {
                                                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                                                        }
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        val filteredDeployments = portalDeployments.filter {
                                            deployedSearchQuery.isBlank() ||
                                            it.shopName.contains(deployedSearchQuery, ignoreCase = true) ||
                                            it.shopCity.contains(deployedSearchQuery, ignoreCase = true) ||
                                            it.licenseKey.contains(deployedSearchQuery, ignoreCase = true)
                                        }

                                        if (filteredDeployments.isEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (portalDeployments.isEmpty()) "Aucune boutique enregistrée pour le moment. Utilisez l'onglet 'Abonnement' pour activer vos premiers clients." else "Aucun résultat pour cette recherche.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(14.dp)
                                                )
                                            }
                                        } else {
                                            filteredDeployments.forEach { dep ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = dep.shopName,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = if (dep.isSynced) Color(0xFFDCFCE7) else Color(0xFFFFF7ED)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (dep.isSynced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                                                            contentDescription = null,
                                                                            tint = if (dep.isSynced) Color(0xFF16A34A) else Color(0xFFFF7900),
                                                                            modifier = Modifier.size(11.dp)
                                                                        )
                                                                        Text(
                                                                            text = if (dep.isSynced) "Cloud" else "En attente",
                                                                            fontSize = 9.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = if (dep.isSynced) Color(0xFF16A34A) else Color(0xFFFF7900)
                                                                        )
                                                                    }
                                                                }
                                                                Surface(
                                                                    shape = RoundedCornerShape(6.dp),
                                                                    color = Color(0xFFDBEAFE)
                                                                ) {
                                                                    Text(
                                                                        text = dep.tier,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 10.sp,
                                                                        color = Color(0xFF1E40AF),
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = "${dep.shopCity.ifBlank { "Burkina Faso" }} ${if (dep.shopPhone.isNotBlank()) "• ${dep.shopPhone}" else ""}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (dep.licenseKey.isNotBlank()) {
                                                            Text(
                                                                text = "Clé: ${dep.licenseKey}",
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 10.5.sp,
                                                                color = Color(0xFF073B9A),
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.End
                                                        ) {
                                                            TextButton(
                                                                onClick = {
                                                                    val shareText = """
                                                                        🧾 *ATTESTATION DE DÉPLOIEMENT KONAB POS*
                                                                        
                                                                        Boutique : ${dep.shopName}
                                                                        Ville : ${dep.shopCity}
                                                                        Formule : ${dep.tier} (${dep.durationDays} jours)
                                                                        Clé de licence : ${dep.licenseKey}
                                                                        Agent Déployeur : ${agent.name} (${agent.phone})
                                                                        
                                                                        Votre caisse enregistreuse est officiellement activée et prête pour vos ventes quotidiennes !
                                                                    """.trimIndent()

                                                                    val sendIntent = Intent().apply {
                                                                        action = Intent.ACTION_SEND
                                                                        putExtra(Intent.EXTRA_SUBJECT, "Attestation de déploiement Konab POS")
                                                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                                                        type = "text/plain"
                                                                    }
                                                                    context.startActivity(Intent.createChooser(sendIntent, "Partager le reçu"))
                                                                }
                                                            ) {
                                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF073B9A))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Partager l'Attestation WhatsApp", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF073B9A))
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
            title = { Text("Logo du Commerce", fontWeight = FontWeight.Bold) },
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

                    Text("Ou sélectionner un visuel prédéfini :", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Licence Activée !", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("L'abonnement a été immédiatement configuré et appliqué à la caisse.")
                    Text("Formule : $activationTier ($activationDays jours)", fontWeight = FontWeight.Bold)
                    if (latestActivationKey != null) {
                        Text(
                            "Clé : $latestActivationKey",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF073B9A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("Vous pouvez transmettre l'attestation numérique d'activation directement au commerçant.")
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
                            
                            L'application est 100% opérationnelle pour vos encaissements quotidiens !
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transmettre sur WhatsApp")
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
