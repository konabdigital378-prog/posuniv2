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
import com.example.util.BusinessTypePresets

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
        preloadDemoData: Boolean
    ) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDestination = SetupWizardDestination.fromRoute(currentRoute)
    val allDestinations = SetupWizardDestination.allSteps

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
                                    .background(KonabGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "K",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Assistant Konab POS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = KonabTextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KonabGreenLight
                        ) {
                            Text(
                                text = "Étape ${currentDestination.stepNumber} sur ${allDestinations.size}",
                                color = KonabGreenDark,
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
                                    viewModel.completeSetup {
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
                                            uiState.preloadDemoData
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
                WizardStepWelcome()
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
                    selectedLogoColor = Color(uiState.selectedLogoColorHex), onLogoColorChange = { viewModel.updateLogoColorHex(it.value.toLong()) },
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
                    onRegisterClick = {
                        viewModel.completeSetup {
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
                                uiState.preloadDemoData
                            )
                        }
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 1: WELCOME
// -------------------------------------------------------------
@Composable
private fun WizardStepWelcome() {
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
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

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.height(18.dp))

        // Multiple scrolling images carousel for launch screen
        LaunchImageCarousel(
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KonabOrangeContainer, RoundedCornerShape(14.dp))
                .border(1.dp, KonabOrange.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
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
// STEP 7: ENREGISTRER MA BOUTIQUE SUR KONAB POS
// -------------------------------------------------------------
@Composable
private fun WizardStepRegisterShop(
    uiState: com.example.ui.viewmodel.SetupUiState,
    onRegisterClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = KonabGreenLight,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = KonabGreenDark,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Étape 7 sur 7 • Enregistrement Officiel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = KonabGreenDark
                )
            }
        }

        Text(
            text = "Enregistrer ma boutique sur KONAB POS",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = KonabTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Validez la configuration de votre établissement. Vos informations seront enregistrées et synchronisées en toute sécurité avec votre espace Cloud.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Business Identity Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
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
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.shopName.ifBlank { "Ma Boutique" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = KonabTextPrimary
                        )
                        if (uiState.slogan.isNotBlank()) {
                            Text(
                                text = "« ${uiState.slogan} »",
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${uiState.selectedBusinessType} • ${uiState.city}",
                            fontSize = 12.sp,
                            color = KonabGreenDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                ShopDetailRow(label = "Téléphone caisse :", value = uiState.phone)
                Spacer(modifier = Modifier.height(8.dp))
                ShopDetailRow(label = "Adresse & Ville :", value = "${uiState.address}, ${uiState.city}")
                Spacer(modifier = Modifier.height(8.dp))
                ShopDetailRow(label = "Devise choisie :", value = uiState.currency, valueColor = KonabGreen)
                Spacer(modifier = Modifier.height(8.dp))
                ShopDetailRow(
                    label = "Gérant / Admin :",
                    value = "${uiState.adminName} (${uiState.adminPhone.ifBlank { uiState.phone }})"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShopDetailRow(label = "Formule KONAB :", value = "Premium POS • Prêt à l'emploi", valueColor = KonabGreenDark)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cloud Supabase Synchronization Notice Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = KonabGreenLight.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, KonabGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(KonabGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Synchronisation Cloud Automatique",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = KonabGreenDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Votre établissement dispose d'un espace sécurisé en ligne. Vos données de caisse sont synchronisées pour le suivi des ventes et le tableau de bord.",
                        fontSize = 11.sp,
                        color = KonabTextPrimary.copy(alpha = 0.8f),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large Action Button
        Button(
            onClick = onRegisterClick,
            enabled = !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = KonabGreen,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_register_shop_cloud")
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Enregistrement Cloud en cours...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Valider & Enregistrer ma boutique sur KONAB POS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
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
