package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.LaunchImageCarousel
import com.example.ui.theme.*
import com.example.util.BusinessTypePresets

@Composable
fun OnboardingScreen(
    onComplete: (shopName: String, slogan: String, phone: String, address: String, city: String, businessType: String, adminName: String, adminPhone: String, adminPin: String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    // Step 2 State
    var shopName by remember { mutableStateOf("Boutique Kaba") }
    var slogan by remember { mutableStateOf("Mode - Chaussures - Accessoires") }
    var phone by remember { mutableStateOf("65413799") }
    var address by remember { mutableStateOf("Bobo-Dioulasso, Burkina Faso") }
    var city by remember { mutableStateOf("Bobo-Dioulasso") }

    // Step 3 State
    var selectedBusinessType by remember { mutableStateOf("Boutique") }

    // Step 5 State (Local Admin Account via DataStore)
    var adminName by remember { mutableStateOf("Gérant") }
    var adminPhone by remember { mutableStateOf("65413799") }
    var adminPin by remember { mutableStateOf("1234") }
    var confirmPin by remember { mutableStateOf("1234") }
    var pinError by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (step > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Assistant de configuration",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabGreen
                            )
                            Text(
                                text = "Étape $step / 6",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { step / 6f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = KonabGreen,
                            trackColor = KonabGreenLight
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { step-- },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_onboarding_prev")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retour")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            when (step) {
                                1 -> step = 2
                                2 -> {
                                    if (shopName.isNotBlank() && phone.isNotBlank()) {
                                        step = 3
                                    }
                                }
                                3 -> step = 4
                                4 -> step = 5
                                5 -> {
                                    if (adminName.isBlank()) {
                                        pinError = "Veuillez saisir votre nom d'administrateur"
                                    } else if (adminPin.length < 4) {
                                        pinError = "Le code PIN doit comporter 4 chiffres"
                                    } else if (adminPin != confirmPin) {
                                        pinError = "Les codes PIN ne correspondent pas"
                                    } else {
                                        pinError = ""
                                        step = 6
                                    }
                                }
                                6 -> {
                                    onComplete(
                                        shopName,
                                        slogan,
                                        phone,
                                        address,
                                        city,
                                        selectedBusinessType,
                                        adminName,
                                        adminPhone,
                                        adminPin
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_onboarding_next")
                    ) {
                        Text(
                            text = if (step == 6) "Démarrer Konab POS" else if (step == 1) "Commencer" else "Continuer",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (step) {
                1 -> Step1LaunchScreen(onStart = { step = 2 })
                2 -> Step2ShopDetails(
                    shopName = shopName, onShopNameChange = { shopName = it },
                    slogan = slogan, onSloganChange = { slogan = it },
                    phone = phone, onPhoneChange = { phone = it },
                    address = address, onAddressChange = { address = it },
                    city = city, onCityChange = { city = it }
                )
                3 -> Step3BusinessChoice(
                    selectedType = selectedBusinessType,
                    onSelect = { selectedBusinessType = it }
                )
                4 -> Step4AutoCustomization(businessType = selectedBusinessType)
                5 -> Step5AdminSecurity(
                    adminName = adminName, onAdminNameChange = { adminName = it },
                    adminPhone = adminPhone, onAdminPhoneChange = { adminPhone = it },
                    adminPin = adminPin, onAdminPinChange = { adminPin = it },
                    confirmPin = confirmPin, onConfirmPinChange = { confirmPin = it },
                    pinError = pinError
                )
                6 -> Step6FeatureTour(
                    shopName = shopName,
                    businessType = selectedBusinessType
                )
            }
        }
    }
}

@Composable
private fun Step1LaunchScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo
        Surface(
            modifier = Modifier.size(110.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 8.dp,
            border = BorderStroke(2.dp, KonabBlueContainer)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_konab_logo),
                contentDescription = "Konab POS Logo",
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        // 3 Key Pillars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KonabGreenLight, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillarBadge("Simple", Icons.Default.ThumbUp)
            Text("•", color = KonabGreen)
            PillarBadge("Rapide", Icons.Default.Bolt)
            Text("•", color = KonabGreen)
            PillarBadge("Hors ligne", Icons.Default.CloudOff)
        }
    }
}

@Composable
private fun PillarBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Bold, color = KonabGreenDark, fontSize = 13.sp)
    }
}

@Composable
private fun Step2ShopDetails(
    shopName: String, onShopNameChange: (String) -> Unit,
    slogan: String, onSloganChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    address: String, onAddressChange: (String) -> Unit,
    city: String, onCityChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Étape 2 sur 6",
            style = MaterialTheme.typography.labelLarge,
            color = KonabGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Informations de votre commerce",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ces coordonnées apparaîtront sur vos reçus et rapports imprimés.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = shopName,
            onValueChange = onShopNameChange,
            label = { Text("Nom de la boutique / commerce *") },
            leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_shop_name")
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = slogan,
            onValueChange = onSloganChange,
            label = { Text("Slogan ou sous-titre") },
            leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Téléphone (Orange / Moov) *") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_shop_phone")
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text("Adresse / Quartier") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = city,
            onValueChange = onCityChange,
            label = { Text("Ville") },
            leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = KonabGoldContainer)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = KonabGoldDark)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Devise par défaut", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Franc CFA (FCFA) • Zone UEMOA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Step3BusinessChoice(
    selectedType: String,
    onSelect: (String) -> Unit
) {
    val types = listOf(
        Pair("Boutique", Icons.Default.ShoppingBag),
        Pair("Coiffure", Icons.Default.ContentCut),
        Pair("Kiosque", Icons.Default.Store),
        Pair("Restaurant", Icons.Default.Restaurant),
        Pair("Pharmacie", Icons.Default.LocalPharmacy),
        Pair("Quincaillerie", Icons.Default.Build),
        Pair("Commerce général", Icons.Default.Category),
        Pair("Autre", Icons.Default.MoreHoriz)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Étape 3 sur 6",
            style = MaterialTheme.typography.labelLarge,
            color = KonabGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Quel est votre métier ?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choisissez votre activité pour une expérience adaptée à vos besoins quotidiens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(types) { (type, icon) ->
                val isSelected = selectedType == type
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) KonabGreenLight else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .height(115.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) KonabGreen else KonabBorder,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelect(type) }
                        .testTag("type_$type")
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) KonabGreen else KonabGoldContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = type,
                                tint = if (isSelected) Color.White else KonabGoldDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = type,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) KonabGreenDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step4AutoCustomization(businessType: String) {
    val preset = BusinessTypePresets.getPreset(businessType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Étape 4 sur 6",
            style = MaterialTheme.typography.labelLarge,
            color = KonabGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Personnalisation automatique",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Konab POS a préparé pour votre ${preset.title.lowercase()} :",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Categories Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Label, contentDescription = null, tint = KonabGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Catégories préconfigurées", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
                preset.defaultCategories.forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Starter Products Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory, contentDescription = null, tint = KonabGoldDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Produits de démonstration prêts à la vente", fontWeight = FontWeight.Bold)
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
                        Text("${p.sellPrice} FCFA", fontSize = 13.sp, color = KonabGreen, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "+ ${preset.defaultProducts.size - 4} autres articles inclus...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun Step5AdminSecurity(
    adminName: String, onAdminNameChange: (String) -> Unit,
    adminPhone: String, onAdminPhoneChange: (String) -> Unit,
    adminPin: String, onAdminPinChange: (String) -> Unit,
    confirmPin: String, onConfirmPinChange: (String) -> Unit,
    pinError: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Étape 5 sur 6",
            style = MaterialTheme.typography.labelLarge,
            color = KonabGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Création du compte administrateur",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Vos accès locaux et autorisations seront enregistrés de manière sécurisée via Jetpack DataStore sur cet appareil.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // DataStore Local Security Badge
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = KonabGreenLight)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = KonabGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Compte local sécurisé (DataStore)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = KonabGreenDark
                    )
                    Text(
                        text = "Aucun mot de passe n'est envoyé sur internet. Fonctionnement 100% hors-ligne garanti.",
                        fontSize = 11.sp,
                        color = KonabGreenDark.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = adminName,
            onValueChange = onAdminNameChange,
            label = { Text("Nom complet du gérant / propriétaire *") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_admin_name")
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = adminPhone,
            onValueChange = onAdminPhoneChange,
            label = { Text("Téléphone personnel de l'administrateur *") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = adminPin,
            onValueChange = { if (it.length <= 4) onAdminPinChange(it) },
            label = { Text("Code PIN Administrateur (4 chiffres) *") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_admin_pin")
        )

        Spacer(modifier = Modifier.height(14.dp))

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

        if (pinError.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = pinError,
                color = KonabRed,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Step6FeatureTour(
    shopName: String,
    businessType: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(KonabGreenLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tout est prêt !",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = KonabGreen
        )

        Text(
            text = "$shopName • $businessType",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FeatureHighlightRow(
            icon = Icons.Default.PointOfSale,
            title = "Vente ultra-rapide",
            desc = "Enregistrez les ventes en espèces, Orange Money et Moov Money en quelques secondes."
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureHighlightRow(
            icon = Icons.Default.Share,
            title = "Reçus PDF & WhatsApp",
            desc = "Envoyez instantanément le reçu professionnel aux clients ou imprimez en Bluetooth."
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureHighlightRow(
            icon = Icons.Default.People,
            title = "Gestion des dettes clients",
            desc = "Suivez les crédits accordés et enregistrez facilement les remboursements."
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureHighlightRow(
            icon = Icons.Default.CloudOff,
            title = "100% Hors-ligne",
            desc = "Pas de connexion internet requise. Vos données commerciales restent toujours disponibles."
        )
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(KonabGreenLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
