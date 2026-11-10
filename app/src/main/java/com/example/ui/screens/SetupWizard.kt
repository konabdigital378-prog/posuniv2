package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Brush
import com.example.R
import com.example.ui.components.LaunchImageCarousel
import com.example.ui.navigation.SetupWizardDestination
import com.example.ui.theme.*
import com.example.ui.viewmodel.SetupViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.entity.ShopProfileEntity
import com.example.util.LicenseHelper
import com.example.util.BusinessTypePresets
import androidx.compose.ui.graphics.toArgb
import com.example.data.remote.supabase.AgentDeploymentRecord
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.SupabaseSyncManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.compose.ui.text.font.FontFamily

enum class SetupWizardStep(val stepNumber: Int, val title: String) {
    WELCOME(1, "Bienvenue"),
    BUSINESS_INFO(2, "Commerce"),
    ACTIVITY_TYPE(3, "Activité"),
    CUSTOMIZATION(4, "Personnalisation"),
    ADMIN_ACCOUNT(5, "Compte Admin"),
    READY(6, "Récapitulatif"),
    REGISTER_SHOP(7, "Enregistrer ma boutique")
}

fun SetupWizardStep.toDestination(): SetupWizardDestination = when (this) {
    SetupWizardStep.WELCOME -> SetupWizardDestination.Welcome
    SetupWizardStep.BUSINESS_INFO -> SetupWizardDestination.BusinessInfo
    SetupWizardStep.ACTIVITY_TYPE -> SetupWizardDestination.ActivityType
    SetupWizardStep.CUSTOMIZATION -> SetupWizardDestination.Customization
    SetupWizardStep.ADMIN_ACCOUNT -> SetupWizardDestination.AdminAccount
    SetupWizardStep.READY -> SetupWizardDestination.Ready
    SetupWizardStep.REGISTER_SHOP -> SetupWizardDestination.RegisterShop
}

fun SetupWizardDestination.toStep(): SetupWizardStep = when (this) {
    is SetupWizardDestination.Welcome -> SetupWizardStep.WELCOME
    is SetupWizardDestination.BusinessInfo -> SetupWizardStep.BUSINESS_INFO
    is SetupWizardDestination.ActivityType -> SetupWizardStep.ACTIVITY_TYPE
    is SetupWizardDestination.Customization -> SetupWizardStep.CUSTOMIZATION
    is SetupWizardDestination.AdminAccount -> SetupWizardStep.ADMIN_ACCOUNT
    is SetupWizardDestination.Ready -> SetupWizardStep.READY
    is SetupWizardDestination.RegisterShop -> SetupWizardStep.REGISTER_SHOP
}

@Composable
fun SetupWizard(
    viewModel: SetupViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    shopProfile: ShopProfileEntity? = null,
    onEnterShop: () -> Unit = {},
    onComplete: (
        shopName: String,
        slogan: String,
        phone: String,
        address: String,
        city: String,
        currency: String,
        logoIconName: String,
        businessType: String,
        adminName: String,
        adminPhone: String,
        adminPin: String,
        preloadDemoData: Boolean,
        subscriptionTier: String
    ) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDestination = SetupWizardDestination.fromRoute(currentRoute)
    val allDestinations = SetupWizardDestination.allSteps

    // Agent Mode & Plan Activation State
    var activeAgent by remember { mutableStateOf<AgentUser?>(AgentDeploymentStorage.getSavedAgentSession(context)) }
    var showAgentLoginDialog by remember { mutableStateOf(false) }
    var showPlanActivationSuccessDialog by remember { mutableStateOf(false) }
    var activatedPlanInfo by remember { mutableStateOf<Triple<String, String, String>?>(null) } // tier, key, agentName

    // Synchroniser l'étape active avec le ViewModel
    LaunchedEffect(currentDestination) {
        viewModel.goToStep(currentDestination.toStep())
    }

    // Gestion du bouton retour physique et geste
    BackHandler(enabled = currentDestination.stepNumber > 1) {
        navController.popBackStack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Agent Mode Persistent Banner
                if (activeAgent != null) {
                    Surface(
                        color = Color(0xFF06245E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF25D366)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Mode Déploiement Agent : ${activeAgent?.name}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "ID: ${activeAgent?.id} • ${activeAgent?.zone} • Configuration Client",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    AgentDeploymentStorage.clearAgentSession(context)
                                    activeAgent = null
                                    Toast.makeText(context, "Mode agent quitté", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Quitter",
                                    color = Color(0xFFFFA43A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (activeAgent != null) Color(0xFFFF7900) else KonabGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (activeAgent != null) "A" else "K",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (activeAgent != null) "Configuration Client (Agent)" else "Assistant Konab POS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KonabTextPrimary
                                    )
                                    if (activeAgent != null) {
                                        Text(
                                            text = "Déploiement pour : ${uiState.shopName.ifBlank { "Nouveau client" }}",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFF7900),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (activeAgent != null) Color(0xFFFFF7ED) else KonabGreenLight
                            ) {
                                Text(
                                    text = "Étape ${currentDestination.stepNumber} sur ${allDestinations.size}",
                                    color = if (activeAgent != null) Color(0xFFFF7900) else KonabGreenDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                    // Animated Progress Bar
                    LinearProgressIndicator(
                        progress = { currentDestination.stepNumber.toFloat() / allDestinations.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = KonabGreen,
                        trackColor = KonabGreenLight
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Breadcrumb step pills with Navigation Compose
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allDestinations) { dest ->
                            val isCompleted = dest.stepNumber < currentDestination.stepNumber
                            val isCurrent = dest == currentDestination
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isCurrent -> KonabGreen
                                            isCompleted -> KonabGreenLight
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        navController.navigate(dest.route) {
                                            popUpTo(SetupWizardDestination.Welcome.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${dest.stepNumber}. ${dest.title}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isCurrent -> Color.White
                                        isCompleted -> KonabGreenDark
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    },
    bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentDestination.stepNumber > 1) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("btn_wizard_previous")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retour")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            when (currentDestination) {
                                is SetupWizardDestination.Welcome -> {
                                    navController.navigate(SetupWizardDestination.BusinessInfo.route)
                                }
                                is SetupWizardDestination.BusinessInfo -> {
                                    if (viewModel.validateBusinessInfo()) {
                                        navController.navigate(SetupWizardDestination.ActivityType.route)
                                    }
                                }
                                is SetupWizardDestination.ActivityType -> {
                                    navController.navigate(SetupWizardDestination.Customization.route)
                                }
                                is SetupWizardDestination.Customization -> {
                                    navController.navigate(SetupWizardDestination.AdminAccount.route)
                                }
                                is SetupWizardDestination.AdminAccount -> {
                                    if (viewModel.validateAdminAccount()) {
                                        navController.navigate(SetupWizardDestination.Ready.route)
                                    }
                                }
                                is SetupWizardDestination.Ready -> {
                                    navController.navigate(SetupWizardDestination.RegisterShop.route)
                                }
                                is SetupWizardDestination.RegisterShop -> {
                                    viewModel.completeSetup(tier = uiState.selectedSubscriptionTier) {
                                        onComplete(
                                            uiState.shopName,
                                            uiState.slogan,
                                            uiState.phone,
                                            uiState.address,
                                            uiState.city,
                                            uiState.currency,
                                            uiState.selectedLogoIcon,
                                            uiState.selectedBusinessType,
                                            uiState.adminName,
                                            uiState.adminPhone,
                                            uiState.adminPin,
                                            uiState.preloadDemoData,
                                            uiState.selectedSubscriptionTier
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !uiState.isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_wizard_next")
                    ) {
                        Text(
                            text = when (currentDestination) {
                                is SetupWizardDestination.Welcome -> "Démarrer"
                                is SetupWizardDestination.Ready -> "Étape suivante (Enregistrement)"
                                is SetupWizardDestination.RegisterShop -> if (uiState.isSubmitting) "Enregistrement..." else "Valider & Enregistrer"
                                else -> "Continuer"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SetupWizardDestination.Welcome.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            composable(SetupWizardDestination.Welcome.route) {
                WizardStepWelcome(
                    shopProfile = shopProfile,
                    onEnterShopClick = {
                        if (shopProfile != null && shopProfile.isConfigured) {
                            onEnterShop()
                        } else {
                            navController.navigate(SetupWizardDestination.BusinessInfo.route)
                        }
                    },
                    onAgentClick = {
                        showAgentLoginDialog = true
                    }
                )
            }
            composable(SetupWizardDestination.BusinessInfo.route) {
                WizardStepBusinessInfo(
                    shopName = uiState.shopName, onShopNameChange = viewModel::updateShopName,
                    slogan = uiState.slogan, onSloganChange = viewModel::updateSlogan,
                    phone = uiState.phone, onPhoneChange = viewModel::updatePhone,
                    address = uiState.address, onAddressChange = viewModel::updateAddress,
                    city = uiState.city, onCityChange = viewModel::updateCity,
                    currency = uiState.currency, onCurrencyChange = viewModel::updateCurrency,
                    selectedLogoIcon = uiState.selectedLogoIcon, onLogoIconChange = viewModel::updateLogoIcon,
                    selectedLogoColor = Color(uiState.selectedLogoColorHex), onLogoColorChange = { viewModel.updateLogoColorHex((it.toArgb().toLong() and 0xFFFFFFFFL)) },
                    errorMessage = uiState.validationError ?: ""
                )
            }
            composable(SetupWizardDestination.ActivityType.route) {
                WizardStepActivityType(
                    selectedType = uiState.selectedBusinessType,
                    onSelectType = viewModel::updateBusinessType
                )
            }
            composable(SetupWizardDestination.Customization.route) {
                WizardStepCustomization(
                    businessType = uiState.selectedBusinessType,
                    shopName = uiState.shopName,
                    currency = uiState.currency,
                    preloadDemoData = uiState.preloadDemoData,
                    onTogglePreload = viewModel::updatePreloadDemoData
                )
            }
            composable(SetupWizardDestination.AdminAccount.route) {
                WizardStepAdminAccount(
                    adminName = uiState.adminName, onAdminNameChange = viewModel::updateAdminName,
                    adminPhone = uiState.adminPhone, onAdminPhoneChange = viewModel::updateAdminPhone,
                    adminPin = uiState.adminPin, onAdminPinChange = viewModel::updateAdminPin,
                    confirmPin = uiState.confirmPin, onConfirmPinChange = viewModel::updateConfirmPin,
                    errorMessage = uiState.validationError ?: ""
                )
            }
            composable(SetupWizardDestination.Ready.route) {
                WizardStepReadySummary(
                    shopName = uiState.shopName,
                    businessType = uiState.selectedBusinessType,
                    phone = uiState.phone,
                    address = uiState.address,
                    city = uiState.city,
                    currency = uiState.currency,
                    adminName = uiState.adminName,
                    selectedLogoIcon = uiState.selectedLogoIcon,
                    selectedLogoColor = Color(uiState.selectedLogoColorHex)
                )
            }
            composable(SetupWizardDestination.RegisterShop.route) {
                WizardStepRegisterShop(
                    uiState = uiState,
                    activeAgent = activeAgent,
                    onActivatePlan = { chosenTier ->
                        val durationCode = when (chosenTier) {
                            "Premium" -> "VIE"
                            "Standard" -> "1M"
                            "Starter" -> "1M"
                            else -> "1A"
                        }
                        val keyData = LicenseHelper.generateKey(
                            tier = chosenTier,
                            durationCode = durationCode,
                            beneficiary = uiState.shopName.ifBlank { "Client Konab POS" },
                            note = "Activé par ${activeAgent?.name ?: "Agent Konab"}"
                        )

                        // Enregistrer la boutique déployée au nom de cet agent et synchroniser sur Supabase
                        val deployAgent = activeAgent ?: AgentUser("AGT-101", "Moussa Ouédraogo", "70 12 34 56", "Ouagadougou Centre", "1234")
                        val deployment = AgentDeploymentRecord(
                            id = "DEP-${System.currentTimeMillis()}",
                            agentId = deployAgent.id,
                            agentName = deployAgent.name,
                            agentPhone = deployAgent.phone,
                            shopName = uiState.shopName.ifBlank { "Client Konab POS" },
                            shopPhone = uiState.phone,
                            shopCity = uiState.city,
                            tier = chosenTier,
                            durationDays = when (chosenTier) { "Premium" -> 3650; "Standard" -> 30; "Starter" -> 30; else -> 365 },
                            licenseKey = keyData.key,
                            notes = "Activité: ${uiState.selectedBusinessType}",
                            timestamp = System.currentTimeMillis(),
                            isSynced = false
                        )
                        AgentDeploymentStorage.saveDeployment(context, deployment)
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                SupabaseSyncManager(context).syncAgentDeployments()
                            } catch (_: Exception) {}
                        }

                        viewModel.updateSubscriptionTier(chosenTier)
                        viewModel.completeSetup(tier = chosenTier) {
                            activatedPlanInfo = Triple(
                                chosenTier,
                                keyData.key,
                                deployAgent.name
                            )
                            showPlanActivationSuccessDialog = true
                        }
                    },
                    onRegisterClick = {
                        viewModel.completeSetup(tier = uiState.selectedSubscriptionTier) {
                            onComplete(
                                uiState.shopName,
                                uiState.slogan,
                                uiState.phone,
                                uiState.address,
                                uiState.city,
                                uiState.currency,
                                uiState.selectedLogoIcon,
                                uiState.selectedBusinessType,
                                uiState.adminName,
                                uiState.adminPhone,
                                uiState.adminPin,
                                uiState.preloadDemoData,
                                uiState.selectedSubscriptionTier
                            )
                        }
                    }
                )
            }
        }

        // Dialog : Espace Agent Déployeur (Inscription, Connexion, Profil & Boutiques)
        if (showAgentLoginDialog) {
            AgentLoginModal(
                initialAgent = activeAgent,
                onDismiss = { showAgentLoginDialog = false },
                onLoginSuccess = { agent ->
                    activeAgent = agent
                    showAgentLoginDialog = false
                    Toast.makeText(context, "Bienvenue ${agent.name} ! Mode déploiement client activé.", Toast.LENGTH_SHORT).show()
                    navController.navigate(SetupWizardDestination.BusinessInfo.route)
                }
            )
        }

        // Dialog : Succès d'Activation de Plan par l'Agent
        if (showPlanActivationSuccessDialog && activatedPlanInfo != null) {
            val (planTier, licKey, agentName) = activatedPlanInfo!!
            PlanActivationSuccessModal(
                shopName = uiState.shopName.ifBlank { "Ma Boutique" },
                planTier = planTier,
                agentName = agentName,
                licenseKey = licKey,
                onEnterShop = {
                    showPlanActivationSuccessDialog = false
                    onComplete(
                        uiState.shopName,
                        uiState.slogan,
                        uiState.phone,
                        uiState.address,
                        uiState.city,
                        uiState.currency,
                        uiState.selectedLogoIcon,
                        uiState.selectedBusinessType,
                        uiState.adminName,
                        uiState.adminPhone,
                        uiState.adminPin,
                        uiState.preloadDemoData,
                        planTier
                    )
                }
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 1: WELCOME
// -------------------------------------------------------------
@Composable
private fun WizardStepWelcome(
    shopProfile: ShopProfileEntity? = null,
    onEnterShopClick: () -> Unit = {},
    onAgentClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFD6E4FA),
                        Color(0xFFC2DCF9),
                        Color(0xFFB2D4F7)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(90.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, KonabBlueContainer)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_konab_logo),
                contentDescription = "Logo KONAB POS",
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "KONAB POS",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                color = KonabBlue,
                letterSpacing = 1.sp
            )
        )

        Text(
            text = "Plus proche de vous",
            style = MaterialTheme.typography.titleMedium.copy(
                color = KonabOrange,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Multiple scrolling images carousel for launch screen
        LaunchImageCarousel(
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KonabOrangeContainer, RoundedCornerShape(14.dp))
                .border(1.dp, KonabOrange.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = KonabOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Simple", fontWeight = FontWeight.Bold, color = KonabOrangeDark, fontSize = 12.sp)
            }
            Text("•", color = KonabOrange)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = KonabOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rapide", fontWeight = FontWeight.Bold, color = KonabOrangeDark, fontSize = 12.sp)
            }
            Text("•", color = KonabOrange)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = KonabOrange, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hors ligne", fontWeight = FontWeight.Bold, color = KonabOrangeDark, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // =============================================================
        // LES DEUX GRANDS BOUTONS : ENTRER DANS MA BOUTIQUE OU AGENT
        // =============================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnterShopClick() }
                .testTag("btn_enter_my_shop"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF073B9A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = "Boutique",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Entrer dans ma boutique",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.5.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (shopProfile != null && shopProfile.isConfigured) {
                            "Accéder directement à votre caisse (« ${shopProfile.name} »)"
                        } else {
                            "Démarrer la configuration de votre point de vente"
                        },
                        fontSize = 11.5.sp,
                        color = Color(0xFFC5DAF4),
                        lineHeight = 15.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAgentClick() }
                .testTag("btn_agent_mode_launch"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, Color(0xFFFF7900)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF7ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Agent",
                        tint = Color(0xFFFF7900),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Agent Déployeur",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.5.sp,
                            color = Color(0xFF17243B)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF7900)
                        ) {
                            Text(
                                text = "PRO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Configurer la boutique d'un client et activer son plan en 1 clic",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFFF7900),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 2: BUSINESS INFORMATION (NAME, LOGO PLACEHOLDER, ADDRESS, CURRENCY)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardStepBusinessInfo(
    shopName: String, onShopNameChange: (String) -> Unit,
    slogan: String, onSloganChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    address: String, onAddressChange: (String) -> Unit,
    city: String, onCityChange: (String) -> Unit,
    currency: String, onCurrencyChange: (String) -> Unit,
    selectedLogoIcon: String, onLogoIconChange: (String) -> Unit,
    selectedLogoColor: Color, onLogoColorChange: (Color) -> Unit,
    errorMessage: String
) {
    val logoIcons = listOf(
        Pair("storefront", Icons.Default.Storefront),
        Pair("shopping_bag", Icons.Default.ShoppingBag),
        Pair("checkroom", Icons.Default.Checkroom),
        Pair("restaurant", Icons.Default.Restaurant),
        Pair("content_cut", Icons.Default.ContentCut),
        Pair("local_pharmacy", Icons.Default.LocalPharmacy),
        Pair("build", Icons.Default.Build),
        Pair("diamond", Icons.Default.Diamond),
        Pair("local_cafe", Icons.Default.LocalCafe)
    )

    val logoColors = listOf(
        KonabGreen,
        Color(0xFF0284C7), // Blue
        Color(0xFFD97706), // Amber
        Color(0xFF7C3AED), // Purple
        Color(0xFFDC2626), // Red
        Color(0xFF0F766E)  // Teal
    )

    val currencies = listOf("FCFA", "GNF", "EUR (€)", "USD ($)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Informations de votre commerce",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Personnalisez l'identité de votre boutique pour vos reçus de caisse et rapports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Interactive Logo Placeholder & Live Preview
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aperçu du Logo du commerce",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Logo Avatar Placeholder
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(selectedLogoColor),
                    contentAlignment = Alignment.Center
                ) {
                    val currentIconVector = logoIcons.find { it.first == selectedLogoIcon }?.second ?: Icons.Default.Storefront
                    Icon(
                        imageVector = currentIconVector,
                        contentDescription = "Logo boutique",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = shopName.ifBlank { "Votre Boutique" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                if (slogan.isNotBlank()) {
                    Text(
                        text = slogan,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text("Choisir une icône de logo :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(logoIcons) { (name, icon) ->
                        val isSelected = selectedLogoIcon == name
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) KonabGreenLight else KonabBackground)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) KonabGreen else KonabBorder,
                                    shape = CircleShape
                                )
                                .clickable { onLogoIconChange(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = name,
                                tint = if (isSelected) KonabGreen else KonabTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Choisir la couleur de la marque :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    logoColors.forEach { col ->
                        val isSelected = selectedLogoColor == col
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onLogoColorChange(col) }
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Form fields
        OutlinedTextField(
            value = shopName,
            onValueChange = onShopNameChange,
            label = { Text("Nom du commerce *") },
            placeholder = { Text("Ex: Boutique Kaba, Kiosque Faso") },
            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_wizard_shop_name")
        )

        OutlinedTextField(
            value = slogan,
            onValueChange = onSloganChange,
            label = { Text("Slogan ou sous-titre") },
            placeholder = { Text("Ex: Mode - Chaussures - Accessoires") },
            leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Téléphone du commerce *") },
            placeholder = { Text("65413799") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_wizard_phone")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Adresse / Rue") },
                placeholder = { Text("Grand Marché") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )

            OutlinedTextField(
                value = city,
                onValueChange = onCityChange,
                label = { Text("Ville") },
                placeholder = { Text("Bobo-Dioulasso") },
                singleLine = true,
                modifier = Modifier.weight(0.8f)
            )
        }

        // Currency Selector Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = KonabGoldDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Devise du commerce :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    currencies.forEach { cur ->
                        val isSelected = currency == cur
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCurrencyChange(cur) },
                            label = { Text(cur, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KonabGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = KonabRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 3: ACTIVITY TYPE
// -------------------------------------------------------------
@Composable
private fun WizardStepActivityType(
    selectedType: String,
    onSelectType: (String) -> Unit
) {
    val types = listOf(
        Triple("Boutique", Icons.Default.ShoppingBag, "Prêt-à-porter, vêtements, sacs"),
        Triple("Coiffure", Icons.Default.ContentCut, "Coupes hommes, tresses, cosmétiques"),
        Triple("Kiosque", Icons.Default.Storefront, "Café Touba, thé, sandwiches, petits déjeuners"),
        Triple("Restaurant", Icons.Default.Restaurant, "Plats chauds, grillades, boissons locales"),
        Triple("Pharmacie", Icons.Default.LocalPharmacy, "Médicaments, premiers secours, vitamines"),
        Triple("Quincaillerie", Icons.Default.Build, "Ciment, outillage, plomberie, électricité"),
        Triple("Commerce général", Icons.Default.Category, "Alimentation générale, riz, savons"),
        Triple("Autre", Icons.Default.MoreHoriz, "Services et commerces divers")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Quel est votre métier ?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Sélectionnez votre activité pour préconfigurer automatiquement votre catalogue et vos raccourcis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(types) { (type, icon, description) ->
                val isSelected = selectedType == type
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) KonabGreenLight else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .height(130.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) KonabGreen else KonabBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectType(type) }
                        .testTag("type_card_$type")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) KonabGreen else KonabGoldContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = type,
                                tint = if (isSelected) Color.White else KonabGoldDark,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = type,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) KonabGreenDark else MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = description,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 4: AUTOMATED CUSTOMIZATION PREVIEW
// -------------------------------------------------------------
@Composable
private fun WizardStepCustomization(
    businessType: String,
    shopName: String,
    currency: String,
    preloadDemoData: Boolean,
    onTogglePreload: (Boolean) -> Unit
) {
    val preset = BusinessTypePresets.getPreset(businessType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Configuration du Catalogue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configurez les catégories et décidez si vous souhaitez démarrer avec une base propre ou précharger des données de test.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Clean Database / Demo Mode Switch Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!preloadDemoData) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (!preloadDemoData) Color(0xFF86EFAC) else Color(0xFFFDE68A)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (!preloadDemoData) Icons.Default.CheckCircle else Icons.Default.Science,
                                contentDescription = null,
                                tint = if (!preloadDemoData) KonabGreen else Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!preloadDemoData) "Base Vierge (Recommandé)" else "Mode Démo / Essai activé",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (!preloadDemoData) Color(0xFF166534) else Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!preloadDemoData)
                                "Zéro produit fictif, zéro vente test. Prêt pour vos vrais articles et clients."
                            else
                                "Des articles et clients d'exemple seront créés pour tester la caisse.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preloadDemoData,
                        onCheckedChange = onTogglePreload,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD97706)
                        )
                    )
                }
            }
        }

        // Categories Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = KonabGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Catégories suggérées pour votre activité", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                preset.defaultCategories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat, fontSize = 13.sp)
                    }
                }
            }
        }

        // Optional Demo Products Preview Card (Shown only if toggle is enabled)
        if (preloadDemoData) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = KonabGoldDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aperçu des articles de test", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    preset.defaultProducts.take(4).forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${p.sellPrice} $currency", fontSize = 13.sp, color = KonabGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 5: ADMIN ACCOUNT VIA DATASTORE
// -------------------------------------------------------------
@Composable
private fun WizardStepAdminAccount(
    adminName: String, onAdminNameChange: (String) -> Unit,
    adminPhone: String, onAdminPhoneChange: (String) -> Unit,
    adminPin: String, onAdminPinChange: (String) -> Unit,
    confirmPin: String, onConfirmPinChange: (String) -> Unit,
    errorMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Compte Administrateur Local",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ce compte sera enregistré localement via Jetpack DataStore pour protéger vos bénéfices et opérations de caisse.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // DataStore Notice
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = KonabGreenLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = KonabGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Stockage sécurisé Android DataStore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = KonabGreenDark
                    )
                    Text(
                        text = "Fonctionne intégralement sans connexion Internet (100% hors-ligne).",
                        fontSize = 11.sp,
                        color = KonabGreenDark.copy(alpha = 0.85f)
                    )
                }
            }
        }

        OutlinedTextField(
            value = adminName,
            onValueChange = onAdminNameChange,
            label = { Text("Nom de l'administrateur / propriétaire *") },
            placeholder = { Text("Ex: Ibrahim Kaba") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_wizard_admin_name")
        )

        OutlinedTextField(
            value = adminPhone,
            onValueChange = onAdminPhoneChange,
            label = { Text("Téléphone de l'administrateur *") },
            placeholder = { Text("65413799") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = adminPin,
            onValueChange = { if (it.length <= 4) onAdminPinChange(it) },
            label = { Text("Code PIN secret (4 chiffres) *") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_wizard_admin_pin")
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4) onConfirmPinChange(it) },
            label = { Text("Confirmer le code PIN *") },
            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = KonabRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// -------------------------------------------------------------
// STEP 6: READY & SUMMARY
// -------------------------------------------------------------
@Composable
private fun WizardStepReadySummary(
    shopName: String,
    businessType: String,
    phone: String,
    address: String,
    city: String,
    currency: String,
    adminName: String,
    selectedLogoIcon: String,
    selectedLogoColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(KonabGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(44.dp))
        }

        Text(
            text = "Votre commerce est prêt !",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KonabGreen
        )

        Text(
            text = "Récapitulatif de la configuration :",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Summary Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(selectedLogoColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(shopName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("$businessType • $city", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Téléphone :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(phone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Adresse :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$address, $city", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Devise :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currency, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KonabGreen)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Administrateur (DataStore) :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(adminName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 7: ENREGISTRER MA BOUTIQUE SUR KONAB POS & ACTIVATION DU PLAN
// -------------------------------------------------------------
@Composable
private fun WizardStepRegisterShop(
    uiState: com.example.ui.viewmodel.SetupUiState,
    activeAgent: AgentUser? = null,
    onActivatePlan: (tier: String) -> Unit = {},
    onRegisterClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (activeAgent != null) Color(0xFFFFF7ED) else KonabGreenLight,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (activeAgent != null) Icons.Default.Verified else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = if (activeAgent != null) Color(0xFFFF7900) else KonabGreenDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeAgent != null) {
                        "Étape 7 sur 7 • Déploiement & Activation par Agent"
                    } else {
                        "Étape 7 sur 7 • Enregistrement & Activation"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeAgent != null) Color(0xFFFF7900) else KonabGreenDark
                )
            }
        }

        Text(
            text = "Enregistrement & Activation du Forfait",
            fontSize = 21.sp,
            fontWeight = FontWeight.Black,
            color = KonabTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (activeAgent != null) {
                "Agent ${activeAgent.name} : Choisissez le forfait convenu avec le commerçant et cliquez sur le bouton pour activer la boutique immédiatement."
            } else {
                "Vérifiez les informations saisies et activez le forfait de votre choix pour démarrer votre caisse."
            },
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Business Identity Recap Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(KonabGreen, KonabGreenDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.shopName.ifBlank { "Ma Boutique" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = KonabTextPrimary
                        )
                        if (uiState.slogan.isNotBlank()) {
                            Text(
                                text = "« ${uiState.slogan} »",
                                fontSize = 11.5.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${uiState.selectedBusinessType} • ${uiState.city}",
                            fontSize = 11.5.sp,
                            color = KonabGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                ShopDetailRow(label = "Téléphone caisse :", value = uiState.phone)
                Spacer(modifier = Modifier.height(6.dp))
                ShopDetailRow(label = "Adresse & Ville :", value = "${uiState.address}, ${uiState.city}")
                Spacer(modifier = Modifier.height(6.dp))
                ShopDetailRow(label = "Devise choisie :", value = uiState.currency, valueColor = KonabGreen)
                Spacer(modifier = Modifier.height(6.dp))
                ShopDetailRow(
                    label = "Gérant / Propriétaire :",
                    value = "${uiState.adminName} (${uiState.adminPhone.ifBlank { uiState.phone }})"
                )
                if (activeAgent != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ShopDetailRow(
                        label = "Agent Déployeur :",
                        value = "${activeAgent.name} (${activeAgent.id})",
                        valueColor = Color(0xFFFF7900)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // =============================================================
        // SECTION ACTIVATION DES FORFAITS (ESPACE AGENT & ACTIVATION 1-CLIC)
        // =============================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Activer le Forfait de la Boutique",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = KonabTextPrimary
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (activeAgent != null) Color(0xFFFFF7ED) else KonabGreenLight
            ) {
                Text(
                    text = if (activeAgent != null) "AGENT 1-CLIC" else "SÉLECTION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (activeAgent != null) Color(0xFFFF7900) else KonabGreenDark,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. FORFAIT PROFESSIONNEL (Recommandé)
        PlanOfferCard(
            title = "Plan Professionnel (Recommandé)",
            durationLabel = "Licence 1 An (365 jours)",
            price = "35 000 FCFA / an",
            badge = "RECOMMANDÉ",
            accentColor = KonabGreen,
            features = listOf(
                "Caisse tactile ultra-rapide & ventes",
                "Gestion complète du stock & alertes",
                "Suivi des dettes et crédits clients",
                "Reçus WhatsApp & imprimante thermique Bluetooth",
                "Clôtures de caisse & rapports comptables"
            ),
            buttonLabel = if (activeAgent != null) "⚡ Activer le Plan Professionnel (Agent)" else "Activer le Plan Professionnel",
            onClick = { onActivatePlan("Professionnel") },
            testTag = "btn_activate_plan_pro"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. FORFAIT PREMIUM PRO (Illimité)
        PlanOfferCard(
            title = "Plan Premium Pro Illimité",
            durationLabel = "Licence Permanente (10 ans)",
            price = "75 000 FCFA (À vie)",
            badge = "VIP & CLOUD",
            accentColor = Color(0xFF073B9A),
            features = listOf(
                "Toutes les fonctionnalités Professionnel",
                "Synchronisation Cloud automatique continue",
                "Multi-utilisateurs & permissions caissiers",
                "Sauvegardes automatiques en ligne",
                "Support technique prioritaire dédié"
            ),
            buttonLabel = if (activeAgent != null) "⚡ Activer le Plan Premium Pro (Agent)" else "Activer le Plan Premium Pro",
            onClick = { onActivatePlan("Premium") },
            testTag = "btn_activate_plan_premium"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. FORFAIT STANDARD (Mensuel)
        PlanOfferCard(
            title = "Plan Standard",
            durationLabel = "Licence 1 Mois (30 jours)",
            price = "5 000 FCFA / mois",
            badge = null,
            accentColor = Color(0xFF0284C7),
            features = listOf(
                "Caisse tactile de base",
                "Gestion du stock simple",
                "Reçus de vente imprimables"
            ),
            buttonLabel = if (activeAgent != null) "⚡ Activer le Plan Standard (Agent)" else "Activer le Plan Standard",
            onClick = { onActivatePlan("Standard") },
            testTag = "btn_activate_plan_standard"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. FORFAIT STARTER (Gratuit)
        PlanOfferCard(
            title = "Plan Starter Découverte",
            durationLabel = "Essai Gratuit 30 jours",
            price = "Gratuit",
            badge = "DÉCOUVERTE",
            accentColor = Color(0xFFD97706),
            features = listOf(
                "Découverte gratuite sans engagement",
                "Test de la caisse 100% hors-ligne",
                "Catalogue de démonstration prêt à l'emploi"
            ),
            buttonLabel = if (activeAgent != null) "⚡ Activer le Plan Starter Gratuit (Agent)" else "Activer le Plan Starter",
            onClick = { onActivatePlan("Starter") },
            testTag = "btn_activate_plan_starter"
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Bouton de validation standard alternatif
        OutlinedButton(
            onClick = onRegisterClick,
            enabled = !uiState.isSubmitting,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_register_shop_cloud")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isSubmitting) "Enregistrement en cours..." else "Valider sans activation immédiate",
                fontWeight = FontWeight.SemiBold
            )
        }

        val syncMessage = uiState.cloudSyncMessage
        if (!syncMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (uiState.isCloudSyncSuccess) KonabGreenLight else MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (uiState.isCloudSyncSuccess) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (uiState.isCloudSyncSuccess) KonabGreenDark else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncMessage,
                        fontSize = 12.sp,
                        color = if (uiState.isCloudSyncSuccess) KonabGreenDark else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun PlanOfferCard(
    title: String,
    durationLabel: String,
    price: String,
    badge: String?,
    accentColor: Color,
    features: List<String>,
    buttonLabel: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = accentColor
                    )
                    Text(
                        text = "$durationLabel • $price",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                features.forEach { feat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = feat,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag(testTag)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ShopDetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "—" },
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// -------------------------------------------------------------
// DIALOG: ESPACE AGENT DÉPLOYEUR (INSCRIPTION, CONNEXION, PROFIL & BOUTIQUES)
// -------------------------------------------------------------
@Composable
fun AgentLoginModal(
    initialAgent: AgentUser? = null,
    onDismiss: () -> Unit,
    onLoginSuccess: (AgentUser) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncManager = remember { SupabaseSyncManager(context) }

    var currentAgent by remember { mutableStateOf(initialAgent) }
    var selectedAuthTab by remember { mutableStateOf(0) } // 0: Connexion, 1: Inscription

    // Login Form State
    var agentIdInput by remember { mutableStateOf("") }
    var agentPinInput by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf("") }

    // Registration Form State
    var regNameInput by remember { mutableStateOf("") }
    var regPhoneInput by remember { mutableStateOf("") }
    var regZoneInput by remember { mutableStateOf("Ouagadougou Centre") }
    var regIdInput by remember { mutableStateOf("AGT-${(100..999).random()}") }
    var regPinInput by remember { mutableStateOf("") }
    var regPinConfirmInput by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var regErrorMessage by remember { mutableStateOf("") }

    // Deployed shops state for current agent
    var agentDeployments by remember {
        mutableStateOf(currentAgent?.let { AgentDeploymentStorage.getDeploymentsForAgent(context, it.id) } ?: emptyList())
    }
    var isRefreshingShops by remember { mutableStateOf(false) }

    // Load deployments whenever currentAgent changes
    LaunchedEffect(currentAgent) {
        val agt = currentAgent
        if (agt != null) {
            agentDeployments = AgentDeploymentStorage.getDeploymentsForAgent(context, agt.id)
            isRefreshingShops = true
            try {
                val res = syncManager.refreshDeploymentsForAgent(agt.id)
                if (res.isSuccess) {
                    agentDeployments = res.getOrDefault(AgentDeploymentStorage.getDeploymentsForAgent(context, agt.id))
                }
            } catch (_: Exception) {}
            isRefreshingShops = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF7ED)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Agent",
                        tint = Color(0xFFFF7900),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = if (currentAgent != null) "Profil Agent Déployeur" else "Espace Agent Déployeur",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = if (currentAgent != null) "Synchronisé Supabase Cloud ☁️" else "Gestion réseau & déploiement de boutiques",
                        fontSize = 11.sp,
                        color = if (currentAgent != null) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val agent = currentAgent
                if (agent != null) {
                    // ==========================================
                    // VUE : PROFIL AGENT & BOUTIQUES ACTIVÉES
                    // ==========================================
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF06245E))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF7900)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = agent.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = agent.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Matricule: ${agent.id} • ${agent.zone}",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF16A34A)
                                ) {
                                    Text(
                                        text = "CLOUD ACTIF",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (agent.phone.isNotBlank()) {
                                Text(
                                    text = "Téléphone: ${agent.phone} | Rôle: ${agent.role}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // GRAND BOUTON PRINCIPAL : ACTIVER UNE NOUVELLE BOUTIQUE
                    Button(
                        onClick = {
                            onLoginSuccess(agent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_activate_new_shop"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900))
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ Activer une nouvelle boutique",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Section Liste des Boutiques Déployées
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Boutiques Déployées (${agentDeployments.size}) :",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isRefreshingShops = true
                                    try {
                                        val res = syncManager.refreshDeploymentsForAgent(agent.id)
                                        if (res.isSuccess) {
                                            agentDeployments = res.getOrDefault(emptyList())
                                            Toast.makeText(context, "Boutiques synchronisées depuis Supabase !", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (_: Exception) {}
                                    isRefreshingShops = false
                                }
                            },
                            modifier = Modifier.testTag("btn_refresh_agent_deployments")
                        ) {
                            if (isRefreshingShops) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualiser", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (agentDeployments.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Aucune boutique activée pour le moment.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Cliquez sur 'Activer une nouvelle boutique' pour configurer et déployer votre premier client !",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        agentDeployments.take(15).forEach { dep ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dep.shopName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (dep.tier) {
                                                "Premium" -> Color(0xFFDBEAFE)
                                                "Professionnel" -> Color(0xFFDCFCE7)
                                                "Standard" -> Color(0xFFE0F2FE)
                                                else -> Color(0xFFFEF3C7)
                                            }
                                        ) {
                                            Text(
                                                text = dep.tier,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (dep.tier) {
                                                    "Premium" -> Color(0xFF1E40AF)
                                                    "Professionnel" -> Color(0xFF166534)
                                                    "Standard" -> Color(0xFF0369A1)
                                                    else -> Color(0xFFB45309)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${dep.shopCity.ifBlank { "Burkina Faso" }} ${if (dep.shopPhone.isNotBlank()) "• ${dep.shopPhone}" else ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (dep.licenseKey.isNotBlank()) {
                                        Text(
                                            text = "Licence: ${dep.licenseKey}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dateStr = try {
                                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dep.timestamp))
                                        } catch (_: Exception) { "" }
                                        Text(
                                            text = "Déployé le $dateStr",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )

                                        IconButton(
                                            onClick = {
                                                val shareText = """
                                                    🧾 *ATTESTATION DE DÉPLOIEMENT KONAB POS*
                                                    Boutique : ${dep.shopName}
                                                    Ville : ${dep.shopCity}
                                                    Formule : ${dep.tier}
                                                    Clé Licence : ${dep.licenseKey}
                                                    Agent Déployeur : ${agent.name} (${agent.id})
                                                    Date : $dateStr
                                                    
                                                    Application déployée et synchronisée avec succès !
                                                """.trimIndent()
                                                val intent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Partager l'attestation"))
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Partager", modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bouton changer d'agent / déconnexion
                    OutlinedButton(
                        onClick = {
                            AgentDeploymentStorage.clearAgentSession(context)
                            currentAgent = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_logout_agent_modal"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Changer d'Agent / Se déconnecter", fontSize = 12.sp)
                    }
                } else {
                    // ==========================================
                    // VUE : CONNEXION OU INSCRIPTION AGENT
                    // ==========================================
                    PrimaryTabRow(
                        selectedTabIndex = selectedAuthTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Tab(
                            selected = selectedAuthTab == 0,
                            onClick = { selectedAuthTab = 0 },
                            text = { Text("Se Connecter", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.testTag("btn_agent_tab_login")
                        )
                        Tab(
                            selected = selectedAuthTab == 1,
                            onClick = { selectedAuthTab = 1 },
                            text = { Text("S'inscrire (Nouveau)", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.testTag("btn_agent_tab_register")
                        )
                    }

                    if (selectedAuthTab == 0) {
                        // ------------------------------------------
                        // FORMULAIRE : CONNEXION AGENT
                        // ------------------------------------------
                        Text(
                            text = "Entrez vos identifiants d'agent pour accéder à votre profil et synchroniser vos boutiques :",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = agentIdInput,
                            onValueChange = {
                                agentIdInput = it
                                loginErrorMessage = ""
                            },
                            label = { Text("Identifiant Agent ou Téléphone (ex: AGT-101)") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_agent_id")
                        )

                        OutlinedTextField(
                            value = agentPinInput,
                            onValueChange = {
                                agentPinInput = it
                                loginErrorMessage = ""
                            },
                            label = { Text("Code PIN / Mot de passe") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_agent_pin")
                        )

                        if (loginErrorMessage.isNotBlank()) {
                            Text(
                                text = loginErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (agentIdInput.isBlank() || agentPinInput.isBlank()) {
                                    loginErrorMessage = "Veuillez renseigner votre identifiant et code PIN."
                                    return@Button
                                }
                                isLoggingIn = true
                                loginErrorMessage = ""
                                coroutineScope.launch {
                                    val res = syncManager.loginAgentOnline(agentIdInput, agentPinInput)
                                    isLoggingIn = false
                                    val matched = res.getOrNull()
                                    if (matched != null) {
                                        currentAgent = matched
                                        AgentDeploymentStorage.saveAgentSession(context, matched)
                                        Toast.makeText(context, "Bienvenue ${matched.name} !", Toast.LENGTH_SHORT).show()
                                    } else {
                                        loginErrorMessage = "Identifiant ou code PIN incorrect. Vérifiez vos accès ou inscrivez-vous."
                                    }
                                }
                            },
                            enabled = !isLoggingIn,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_submit_agent_login")
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vérification Supabase Cloud...")
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Se Connecter & Ouvrir Mon Profil", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Puces d'accès rapide agents certifiés
                        Text(
                            text = "Accès rapide agents pré-enregistrés :",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    agentIdInput = "AGT-101"
                                    agentPinInput = "1234"
                                    loginErrorMessage = ""
                                },
                                label = { Text("AGT-101 (Moussa)", fontSize = 10.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    agentIdInput = "AGT-102"
                                    agentPinInput = "1234"
                                    loginErrorMessage = ""
                                },
                                label = { Text("AGT-102 (Fatou)", fontSize = 10.sp) }
                            )
                            AssistChip(
                                onClick = {
                                    agentIdInput = "SUPERVISEUR"
                                    agentPinInput = "9999"
                                    loginErrorMessage = ""
                                },
                                label = { Text("SUPERVISEUR", fontSize = 10.sp) }
                            )
                        }
                    } else {
                        // ------------------------------------------
                        // FORMULAIRE : INSCRIPTION NOUVEL AGENT DÉPLOYEUR
                        // ------------------------------------------
                        Text(
                            text = "Remplissez vos informations d'agent. Vos identifiants seront automatiquement synchronisés sur Supabase Cloud :",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = regNameInput,
                            onValueChange = { regNameInput = it; regErrorMessage = "" },
                            label = { Text("Nom complet de l'agent *") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_name")
                        )

                        OutlinedTextField(
                            value = regPhoneInput,
                            onValueChange = { regPhoneInput = it; regErrorMessage = "" },
                            label = { Text("Numéro de téléphone *") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_phone")
                        )

                        OutlinedTextField(
                            value = regZoneInput,
                            onValueChange = { regZoneInput = it; regErrorMessage = "" },
                            label = { Text("Ville / Zone d'intervention *") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_zone")
                        )

                        OutlinedTextField(
                            value = regIdInput,
                            onValueChange = { regIdInput = it.uppercase(); regErrorMessage = "" },
                            label = { Text("Identifiant Agent / Matricule souhaité *") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_id")
                        )

                        OutlinedTextField(
                            value = regPinInput,
                            onValueChange = { regPinInput = it; regErrorMessage = "" },
                            label = { Text("Code PIN secret (4 à 8 chiffres) *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_pin")
                        )

                        OutlinedTextField(
                            value = regPinConfirmInput,
                            onValueChange = { regPinConfirmInput = it; regErrorMessage = "" },
                            label = { Text("Confirmer le Code PIN *") },
                            leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_reg_agent_pin_confirm")
                        )

                        if (regErrorMessage.isNotBlank()) {
                            Text(
                                text = regErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (regNameInput.isBlank() || regPhoneInput.isBlank() || regIdInput.isBlank() || regPinInput.isBlank()) {
                                    regErrorMessage = "Tous les champs obligatoires (*) doivent être remplis."
                                    return@Button
                                }
                                if (regPinInput.length < 4) {
                                    regErrorMessage = "Le code PIN doit comporter au moins 4 chiffres."
                                    return@Button
                                }
                                if (regPinInput != regPinConfirmInput) {
                                    regErrorMessage = "Les codes PIN ne correspondent pas."
                                    return@Button
                                }

                                isRegistering = true
                                regErrorMessage = ""
                                val newAgent = AgentUser(
                                    id = regIdInput.trim().uppercase(),
                                    name = regNameInput.trim(),
                                    phone = regPhoneInput.trim(),
                                    zone = regZoneInput.trim().ifBlank { "National" },
                                    pin = regPinInput.trim(),
                                    role = "Agent Déployeur"
                                )

                                coroutineScope.launch {
                                    val res = syncManager.registerAgentOnline(newAgent)
                                    isRegistering = false
                                    if (res.isSuccess) {
                                        currentAgent = newAgent
                                        Toast.makeText(context, "Compte Agent créé et synchronisé sur Supabase !", Toast.LENGTH_LONG).show()
                                    } else {
                                        regErrorMessage = "Erreur lors de l'enregistrement : ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !isRegistering,
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_submit_agent_register")
                        ) {
                            if (isRegistering) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enregistrement sur Supabase...")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("S'inscrire & Synchroniser sur Supabase", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

// -------------------------------------------------------------
// DIALOG: CONFIRMATION D'ACTIVATION DU PLAN PAR L'AGENT
// -------------------------------------------------------------
@Composable
fun PlanActivationSuccessModal(
    shopName: String,
    planTier: String,
    agentName: String,
    licenseKey: String,
    onEnterShop: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onEnterShop,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Text(
                text = "Boutique Activée avec Succès !",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "La boutique a été configurée et activée selon le formulaire demandé par le client.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Boutique :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(shopName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Forfait activé :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(planTier, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Agent Déployeur :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(agentName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (licenseKey.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Clé Licence :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = licenseKey,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Statut Cloud :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Synchronisé Supabase ☁️", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEnterShop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_enter_activated_shop"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
            ) {
                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Entrer dans la boutique", fontWeight = FontWeight.Bold)
            }
        }
    )
}

