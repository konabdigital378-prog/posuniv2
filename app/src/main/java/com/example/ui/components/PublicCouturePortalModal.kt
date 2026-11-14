package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.CoutureClientAccount
import com.example.data.remote.supabase.CoutureModelRecord
import com.example.data.remote.supabase.CoutureOrderRecord
import com.example.data.remote.supabase.CoutureStage
import com.example.data.remote.supabase.PublicCouturierListing
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabOrange
import com.example.ui.theme.OutfitFontFamily
import com.example.util.CouturePhotoImage
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PortalViolet = Color(0xFF5B21B6)
private val PortalVioletLight = Color(0xFFF5F3FF)

@Composable
fun PublicCouturePortalModal(
    couturiers: List<PublicCouturierListing>,
    modelsFeed: List<CoutureModelRecord>,
    isLoadingDirectory: Boolean,
    activeClient: CoutureClientAccount?,
    clientOrders: List<CoutureOrderRecord>,
    isLoadingClientOrders: Boolean,
    onRefreshDirectory: () -> Unit,
    onRegisterOrLoginClient: (firstName: String, lastName: String, phone: String, city: String) -> Unit,
    onLogoutClient: () -> Unit,
    onRefreshClientProgress: (overridePhone: String?) -> Unit,
    onOpenCouturierCatalog: (PublicCouturierListing, initialModelId: String?) -> Unit,
    onScanAnyQr: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (activeClient != null) 0 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }

    // Account form states when not signed in
    var firstNameInput by remember(activeClient) { mutableStateOf(activeClient?.firstName ?: "") }
    var lastNameInput by remember(activeClient) { mutableStateOf(activeClient?.lastName ?: "") }
    var phoneInput by remember(activeClient) { mutableStateOf(activeClient?.phone ?: "") }
    var cityInput by remember(activeClient) { mutableStateOf(activeClient?.city ?: "") }
    var showAccountSheet by remember { mutableStateOf(false) }

    LaunchedEffect(activeClient?.phone) {
        if (activeClient != null) {
            onRefreshClientProgress(activeClient.phone)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("modal_public_couture_portal"),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(PortalViolet, Color(0xFF4C1D95)))
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer l'espace couture", tint = Color.White)
                                }
                                Column {
                                    Text(
                                        text = "Espace Couture & Suivi Tenue",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (activeClient != null) {
                                            "Connecté : ${activeClient.fullName} (${activeClient.phone})"
                                        } else {
                                            "Couturiers, Modèles & Progression en direct"
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFFEDE9FE),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { showQrScanner = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_couture_portal_scan_qr")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner le QR d'un couturier")
                            }
                        }
                    }

                    // User account status strip
                    Surface(
                        color = if (activeClient != null) KonabGreen.copy(alpha = 0.1f) else PortalVioletLight,
                        border = BorderStroke(
                            1.dp,
                            if (activeClient != null) KonabGreen.copy(alpha = 0.35f) else PortalViolet.copy(alpha = 0.25f)
                        ),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (activeClient != null) Icons.Default.VerifiedUser else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (activeClient != null) KonabGreen else PortalViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (activeClient != null) {
                                        "${activeClient.fullName} • ${clientOrders.size} couture(s) suivie(s)"
                                    } else {
                                        "Créez votre compte utilisateur pour suivre votre couture"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = { showAccountSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (activeClient != null) "Compte" else "Créer / Connecter",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PortalViolet,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Couturiers & Modèles", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                if (activeClient != null) onRefreshClientProgress(activeClient.phone)
                            },
                            text = {
                                val badge = if (clientOrders.isNotEmpty()) " (${clientOrders.size})" else ""
                                Text("Ma Couture$badge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            },
                            icon = { Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedTab == 0) {
                    val filteredCouturiers = remember(couturiers, searchQuery) {
                        val q = searchQuery.trim()
                        if (q.isBlank()) couturiers else couturiers.filter {
                            it.name.contains(q, ignoreCase = true) ||
                                it.city.contains(q, ignoreCase = true) ||
                                it.slogan.contains(q, ignoreCase = true)
                        }
                    }
                    val filteredModels = remember(modelsFeed, searchQuery) {
                        val q = searchQuery.trim()
                        if (q.isBlank()) modelsFeed else modelsFeed.filter {
                            it.title.contains(q, ignoreCase = true) ||
                                it.category.contains(q, ignoreCase = true) ||
                                it.shopName.contains(q, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Rechercher couturier, ville ou modèle...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalIconButton(
                                    onClick = onRefreshDirectory,
                                    enabled = !isLoadingDirectory,
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    if (isLoadingDirectory) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                                    }
                                }
                            }
                        }

                        if (filteredModels.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Modèles de Couture Populaires (${filteredModels.size})",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filteredModels.take(16), key = { it.id }) { model ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .width(164.dp)
                                                .clickable {
                                                    val shop = couturiers.firstOrNull { it.shopId == model.shopId }
                                                        ?: PublicCouturierListing(
                                                            shopId = model.shopId,
                                                            name = model.shopName.ifBlank { "Atelier Couture" }
                                                        )
                                                    onOpenCouturierCatalog(shop, model.id)
                                                }
                                        ) {
                                            Column {
                                                CouturePhotoImage(
                                                    uriOrKey = model.imageUri,
                                                    contentDescription = model.title,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(118.dp),
                                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                                )
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = model.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (model.shopName.isNotBlank()) {
                                                        Text(
                                                            text = model.shopName,
                                                            fontSize = 10.5.sp,
                                                            color = PortalViolet,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = CurrencyFormatter.formatAmount(model.price, "FCFA"),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 12.5.sp,
                                                        color = KonabOrange
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Ateliers & Couturiers Inscrits (${filteredCouturiers.size})",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp
                            )
                        }

                        if (filteredCouturiers.isEmpty() && !isLoadingDirectory) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Checkroom, contentDescription = null, tint = PortalViolet, modifier = Modifier.size(36.dp))
                                        Text("Aucun atelier couture trouvé", fontWeight = FontWeight.Bold)
                                        Text(
                                            "Vous pouvez scanner directement le QR code d'un couturier avec le bouton en haut.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredCouturiers, key = { "${it.shopId}_${it.name}" }) { couturier ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, PortalViolet.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenCouturierCatalog(couturier, null) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    com.example.util.ShopLogoAvatar(
                                        logoUri = couturier.logoUri,
                                        size = 52.dp,
                                        shape = CircleShape,
                                        contentDescription = couturier.name
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = couturier.name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp
                                        )
                                        val loc = listOfNotNull(
                                            couturier.city.takeIf { it.isNotBlank() },
                                            couturier.phone.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ").ifBlank { couturier.slogan.ifBlank { "Atelier de Couture Sur-Mesure" } }
                                        Text(
                                            text = loc,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (couturier.modelsCount > 0) {
                                            Text(
                                                text = "${couturier.modelsCount} modèle(s) au catalogue",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PortalViolet
                                            )
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { onOpenCouturierCatalog(couturier, null) },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = PortalVioletLight,
                                            contentColor = PortalViolet
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.heightIn(min = 42.dp)
                                    ) {
                                        Text("Voir modèles", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2 : SUIVI DE LA PROGRESSION DE MA COUTURE
                    ClientProgressTrackerTab(
                        activeClient = activeClient,
                        orders = clientOrders,
                        isLoading = isLoadingClientOrders,
                        onOpenAccountPrompt = { showAccountSheet = true },
                        onRefresh = { phone -> onRefreshClientProgress(phone) }
                    )
                }
            }
        }
    }

    if (showAccountSheet) {
        Dialog(onDismissRequest = { showAccountSheet = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (activeClient != null) "Mon Compte Client Couture" else "Créer mon Compte Couture",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Votre numéro de téléphone permet de retrouver automatiquement toutes vos coutures, photos de tissus et rendez-vous.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = firstNameInput,
                            onValueChange = { firstNameInput = it },
                            label = { Text("Prénom *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("input_couture_client_firstname")
                        )
                        OutlinedTextField(
                            value = lastNameInput,
                            onValueChange = { lastNameInput = it },
                            label = { Text("Nom *") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("input_couture_client_lastname")
                        )
                    }
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Numéro de téléphone *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input_couture_client_phone")
                    )
                    OutlinedTextField(
                        value = cityInput,
                        onValueChange = { cityInput = it },
                        label = { Text("Ville (optionnel)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeClient != null) {
                            OutlinedButton(
                                onClick = {
                                    onLogoutClient()
                                    showAccountSheet = false
                                },
                                modifier = Modifier.weight(0.45f).height(48.dp)
                            ) {
                                Text("Déconnexion", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showAccountSheet = false },
                                modifier = Modifier.weight(0.45f).height(48.dp)
                            ) {
                                Text("Annuler")
                            }
                        }
                        Button(
                            onClick = {
                                if (firstNameInput.isNotBlank() && phoneInput.isNotBlank()) {
                                    onRegisterOrLoginClient(firstNameInput, lastNameInput, phoneInput, cityInput)
                                    showAccountSheet = false
                                    selectedTab = 1
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PortalViolet),
                            modifier = Modifier
                                .weight(0.55f)
                                .height(48.dp)
                                .testTag("btn_save_couture_client_account")
                        ) {
                            Text("Valider & Suivre", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        RealBarcodeScannerModal(
            onDismiss = { showQrScanner = false },
            onBarcodeScanned = { scanned ->
                showQrScanner = false
                onScanAnyQr(scanned)
            }
        )
    }
}

@Composable
private fun ClientProgressTrackerTab(
    activeClient: CoutureClientAccount?,
    orders: List<CoutureOrderRecord>,
    isLoading: Boolean,
    onOpenAccountPrompt: () -> Unit,
    onRefresh: (overridePhone: String?) -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("EEE dd MMM yyyy 'à' HH:mm", Locale.FRENCH) }
    var manualPhoneQuery by remember(activeClient?.phone) { mutableStateOf(activeClient?.phone ?: "") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, PortalViolet.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Suivre la progression de ma tenue",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualPhoneQuery,
                            onValueChange = { manualPhoneQuery = it },
                            label = { Text("Mon N° de téléphone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onRefresh(manualPhoneQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = PortalViolet),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Rechercher")
                            }
                        }
                    }
                    if (activeClient == null) {
                        TextButton(onClick = onOpenAccountPrompt) {
                            Text("Créer ou connecter mon profil utilisateur →", fontWeight = FontWeight.Bold, color = PortalViolet)
                        }
                    }
                }
            }
        }

        if (orders.isEmpty() && !isLoading) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Timeline, contentDescription = null, tint = PortalViolet, modifier = Modifier.size(40.dp))
                        Text("Aucune couture active trouvée", fontWeight = FontWeight.Bold)
                        Text(
                            "Choisissez un modèle chez un couturier ou vérifiez que votre numéro correspond à celui enregistré lors de la prise de mesures.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(orders, key = { it.id }) { order ->
            val progressColor = when (order.status) {
                CoutureStage.ESSAYAGE_PRET, CoutureStage.LIVRE_ENCAISSE -> KonabGreen
                CoutureStage.DEMANDE_CLIENT -> KonabOrange
                else -> PortalViolet
            }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, progressColor.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = order.shopName.ifBlank { "Atelier Couture" },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PortalViolet
                            )
                            Text(
                                text = order.modelTitle,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Fiche ${order.orderNumber} • ${order.fullCustomerName}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = progressColor
                        ) {
                            Text(
                                text = "${order.status.progressPercent}%",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { order.status.progressPercent / 100f },
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    // Étapes de progression visuelles
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CoutureStage.entries.forEach { step ->
                            val reached = order.status.stepIndex >= step.stepIndex
                            val isCurrent = order.status == step
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (reached) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isCurrent) progressColor else if (reached) KonabGreen else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = step.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (order.appointmentAt > 0L) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = Color(0xFF92400E))
                                Column {
                                    Text("Rendez-vous de récupération", fontSize = 11.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                                    Text(
                                        text = dateFmt.format(Date(order.appointmentAt)),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }
                        }
                    }

                    // Photos du modèle et du tissu pris par le couturier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (order.fabricPhotoUri.isNotBlank()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Photo de votre tissu", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                CouturePhotoImage(
                                    uriOrKey = order.fabricPhotoUri,
                                    contentDescription = "Photo du tissu confié",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modèle choisi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            CouturePhotoImage(
                                uriOrKey = order.modelImageUri,
                                contentDescription = order.modelTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    val entries = order.measurements.nonEmptyEntries()
                    if (entries.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PortalVioletLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Mesures enregistrées (${order.measurements.bodyProfile})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PortalViolet
                                )
                                Text(
                                    text = entries.joinToString(" • ") { "${it.first}: ${it.second}" },
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF3B0764)
                                )
                            }
                        }
                    }

                    if (order.totalPrice > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tarif : ${CurrencyFormatter.formatAmount(order.totalPrice, "FCFA")}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(
                                text = "Reste : ${CurrencyFormatter.formatAmount(order.remainingBalance, "FCFA")}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = if (order.remainingBalance == 0L) KonabGreen else KonabOrange
                            )
                        }
                    }
                }
            }
        }
    }
}
