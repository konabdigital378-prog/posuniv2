package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.example.data.remote.supabase.ParsedCoutureQr
import com.example.data.remote.supabase.PublicCouturierListing
import com.example.data.remote.supabase.SupabaseApiClient
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabOrange
import com.example.ui.theme.OutfitFontFamily
import com.example.util.CouturePhotoImage
import com.example.util.CurrencyFormatter

private val CatalogViolet = Color(0xFF5B21B6)
private val CatalogVioletLight = Color(0xFFF5F3FF)

@Composable
fun CustomerCoutureCatalogModal(
    target: ParsedCoutureQr,
    bundle: SupabaseApiClient.PublicCoutureCatalogBundle?,
    isLoading: Boolean,
    errorMessage: String?,
    activeClient: CoutureClientAccount?,
    onRetry: () -> Unit,
    onSubmitRequest: (
        bundle: SupabaseApiClient.PublicCoutureCatalogBundle,
        chosenModel: CoutureModelRecord?,
        isCustomModel: Boolean,
        customModelLabel: String,
        firstName: String,
        lastName: String,
        phone: String,
        notes: String,
        onResult: (Boolean, CoutureOrderRecord?, String) -> Unit
    ) -> Unit,
    onOpenMyProgressTracker: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val activeShopName = bundle?.shopName?.ifBlank { target.shopName } ?: target.shopName.ifBlank { "Atelier Couture" }
    val webSlug = remember(activeShopName, bundle?.shopId) {
        PublicCouturierListing(shopId = bundle?.shopId ?: target.shopId, name = activeShopName).toWebSlug()
    }

    var selectedCategory by remember { mutableStateOf("Tous") }
    var searchQuery by remember { mutableStateOf("") }
    var detailModel by remember { mutableStateOf<CoutureModelRecord?>(null) }

    // Formulaire de confirmation client (soit un modèle du catalogue, soit "Mon propre modèle")
    var bookingTargetModel by remember { mutableStateOf<CoutureModelRecord?>(null) }
    var bookingIsCustom by remember { mutableStateOf(false) }
    var showBookingSheet by remember { mutableStateOf(false) }
    var submittedOrder by remember { mutableStateOf<CoutureOrderRecord?>(null) }

    // Ouvrir automatiquement la fiche si le QR pointe sur un modelId précis
    LaunchedEffect(bundle?.models, target.modelId) {
        val targetModelId = target.modelId
        if (!targetModelId.isNullOrBlank() && bundle != null) {
            bundle.models.firstOrNull { it.id == targetModelId }?.let { matched ->
                detailModel = matched
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("modal_customer_couture_catalog"),
            topBar = {
                Surface(color = Color(0xFF0F172A), shadowElevation = 4.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Mini-browser Web bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer le catalogue", tint = Color.White)
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "konab.couture/$webSlug",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Atelier Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(listOf(CatalogViolet, Color(0xFF7C3AED)))
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    com.example.util.ShopLogoAvatar(
                                        logoUri = bundle?.logoUri,
                                        size = 46.dp,
                                        shape = CircleShape,
                                        contentDescription = activeShopName
                                    )
                                    Column {
                                        Text(
                                            text = activeShopName,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 17.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = listOfNotNull(
                                            bundle?.city?.takeIf { it.isNotBlank() },
                                            bundle?.slogan?.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ").ifBlank { "Catalogue Modèles & Sur-Mesure" }
                                        Text(
                                            text = meta,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFFEDE9FE),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${bundle?.models?.size ?: 0} modèles",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (bundle != null && submittedOrder == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    bookingTargetModel = null
                                    bookingIsCustom = true
                                    showBookingSheet = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_couture_bring_own_model")
                            ) {
                                Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("J'ai mon propre modèle", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = CatalogViolet)
                            Text("Chargement du catalogue $activeShopName...", fontWeight = FontWeight.Bold)
                        }
                    }
                    errorMessage != null && bundle == null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            Text(errorMessage, fontWeight = FontWeight.SemiBold)
                            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = CatalogViolet)) {
                                Text("Réessayer")
                            }
                        }
                    }
                    submittedOrder != null -> {
                        val order = submittedOrder!!
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(KonabGreen.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KonabGreen, modifier = Modifier.size(44.dp))
                            }
                            Text(
                                text = "Choix envoyé à $activeShopName !",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Référence ${order.orderNumber} • Modèle : ${order.modelTitle}",
                                fontWeight = FontWeight.Bold,
                                color = CatalogViolet
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CatalogVioletLight,
                                border = BorderStroke(1.dp, CatalogViolet.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("Prochaine étape chez le couturier :", fontWeight = FontWeight.ExtraBold, color = CatalogViolet)
                                    Text("1. Le couturier prend vos mensurations et la photo de votre tissu avec ses outils.", fontSize = 12.5.sp)
                                    Text("2. Il fixe votre date de rendez-vous pour récupérer votre tenue.", fontSize = 12.5.sp)
                                    Text("3. Vous suivez l'avancement en direct depuis votre espace Couture.", fontSize = 12.5.sp)
                                }
                            }
                            if (onOpenMyProgressTracker != null) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenMyProgressTracker()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CatalogViolet),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Icon(Icons.Default.Timeline, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Suivre ma couture en direct", fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = { submittedOrder = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Retourner au catalogue")
                            }
                        }
                    }
                    bundle != null -> {
                        val categories = remember(bundle.models) {
                            listOf("Tous") + bundle.models.map { it.category }.filter { it.isNotBlank() }.distinct()
                        }
                        val filtered = remember(bundle.models, selectedCategory, searchQuery) {
                            bundle.models.filter { m ->
                                (selectedCategory == "Tous" || m.category == selectedCategory) &&
                                    (searchQuery.isBlank() || m.title.contains(searchQuery, ignoreCase = true) ||
                                        m.description.contains(searchQuery, ignoreCase = true))
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Rechercher un modèle (boubou, robe, ensemble...)") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat, fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (filtered.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucun modèle ne correspond à votre recherche.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 165.dp),
                                    contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filtered, key = { it.id }) { model ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { detailModel = model }
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                CouturePhotoImage(
                                                    uriOrKey = model.imageUri,
                                                    contentDescription = model.title,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(145.dp),
                                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                                )
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = model.category,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CatalogViolet
                                                    )
                                                    Text(
                                                        text = model.title,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.5.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = CurrencyFormatter.formatAmount(model.price, bundle.currency),
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 13.sp,
                                                            color = KonabOrange
                                                        )
                                                        Text(
                                                            text = "~${model.estimatedDays}j",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Button(
                                                        onClick = {
                                                            bookingTargetModel = model
                                                            bookingIsCustom = false
                                                            showBookingSheet = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = CatalogViolet),
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                                    ) {
                                                        Text("Choisir ce modèle", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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

    // Fiche détaillée du modèle
    detailModel?.let { model ->
        val currency = bundle?.currency ?: "FCFA"
        Dialog(onDismissRequest = { detailModel = null }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CouturePhotoImage(
                        uriOrKey = model.imageUri,
                        contentDescription = model.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(model.category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CatalogViolet)
                        Text(model.title, fontFamily = OutfitFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(
                            text = CurrencyFormatter.formatAmount(model.price, currency) + " • Délai estimé : ${model.estimatedDays} jour(s)",
                            fontWeight = FontWeight.Bold,
                            color = KonabOrange
                        )
                        if (model.description.isNotBlank()) {
                            Text(model.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = { detailModel = null }, modifier = Modifier.weight(0.4f).height(48.dp)) {
                                Text("Fermer")
                            }
                            Button(
                                onClick = {
                                    bookingTargetModel = model
                                    bookingIsCustom = false
                                    detailModel = null
                                    showBookingSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CatalogViolet),
                                modifier = Modifier.weight(0.6f).height(48.dp)
                            ) {
                                Text("Choisir ce modèle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Formulaire de transmission au couturier
    if (showBookingSheet && bundle != null) {
        CoutureClientBookingDialog(
            bundle = bundle,
            chosenModel = bookingTargetModel,
            isCustomModel = bookingIsCustom,
            activeClient = activeClient,
            onSubmit = { customLabel, first, last, phone, notes, doneCb ->
                onSubmitRequest(
                    bundle,
                    bookingTargetModel,
                    bookingIsCustom,
                    customLabel,
                    first,
                    last,
                    phone,
                    notes
                ) { ok, createdOrder, msg ->
                    doneCb(ok, msg)
                    if (ok && createdOrder != null) {
                        showBookingSheet = false
                        submittedOrder = createdOrder
                    }
                }
            },
            onDismiss = { showBookingSheet = false }
        )
    }
}

@Composable
private fun CoutureClientBookingDialog(
    bundle: SupabaseApiClient.PublicCoutureCatalogBundle,
    chosenModel: CoutureModelRecord?,
    isCustomModel: Boolean,
    activeClient: CoutureClientAccount?,
    onSubmit: (
        customModelLabel: String,
        firstName: String,
        lastName: String,
        phone: String,
        notes: String,
        cb: (Boolean, String) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var firstName by remember { mutableStateOf(activeClient?.firstName ?: "") }
    var lastName by remember { mutableStateOf(activeClient?.lastName ?: "") }
    var phone by remember { mutableStateOf(activeClient?.phone ?: "") }
    var customModelTitle by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isCustomModel) "Apporter mon propre modèle" else "Confirmer : ${chosenModel?.title.orEmpty()}",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Atelier : ${bundle.shopName}",
                    fontSize = 12.sp,
                    color = CatalogViolet,
                    fontWeight = FontWeight.Bold
                )
                if (isCustomModel) {
                    OutlinedTextField(
                        value = customModelTitle,
                        onValueChange = { customModelTitle = it },
                        label = { Text("Décrivez votre modèle") },
                        placeholder = { Text("Ex: Robe longue pagne + manches bouffantes") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it; errorMsg = null },
                        label = { Text("Prénom *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it; errorMsg = null },
                        label = { Text("Nom *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text("Téléphone (pour suivre ma couture) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Tissu ou préférences (optionnel)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.4f).height(48.dp)) {
                        Text("Annuler")
                    }
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            isSubmitting = true
                            errorMsg = null
                            onSubmit(customModelTitle, firstName, lastName, phone, notes) { ok, msg ->
                                isSubmitting = false
                                if (!ok) errorMsg = msg
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CatalogViolet),
                        modifier = Modifier.weight(0.6f).height(48.dp)
                    ) {
                        Text(if (isSubmitting) "Envoi..." else "Envoyer au couturier", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
