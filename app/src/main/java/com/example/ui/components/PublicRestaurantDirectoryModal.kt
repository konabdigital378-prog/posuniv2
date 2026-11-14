package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.supabase.PublicRestaurantListing
import com.example.data.remote.supabase.RestaurantDirectoryHelper
import com.example.ui.theme.*
import com.example.util.ShopLogoAvatar

/**
 * Annuaire public des restaurants inscrits sur la plateforme Konab.
 * Accessible dès le téléchargement de l'application pour toute personne lambda :
 * 1. Parcours & recherche parmi tous les restaurants enregistrés.
 * 2. Sélection du restaurant concerné.
 * 3. Scan du QR Code de table OU ouverture directe du Menu Web (catalogue visuel).
 */
@Composable
fun PublicRestaurantDirectoryModal(
    restaurants: List<PublicRestaurantListing>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onScanAnyQr: (String) -> Unit,
    onScanTableQrForRestaurant: (String, PublicRestaurantListing) -> Unit,
    onOpenRestaurantMenu: (PublicRestaurantListing) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedRestaurant by remember { mutableStateOf<PublicRestaurantListing?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    val filteredRestaurants = remember(restaurants, searchQuery) {
        RestaurantDirectoryHelper.mergeAndFilterDirectory(
            remoteListings = restaurants,
            query = searchQuery
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .widthIn(max = 640.dp)
                .safeDrawingPadding()
                .imePadding()
                .testTag("modal_public_restaurant_directory"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF7C2D12), Color(0xFFEA580C), KonabOrange)
                            )
                        )
                        .konabHeroShimmer()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedRestaurant != null) {
                            IconButton(
                                onClick = { selectedRestaurant = null },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_directory_back_to_list")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour à la liste des restaurants",
                                    tint = Color.White
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = "Restaurants inscrits",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedRestaurant?.name ?: "Restaurants sur Konab",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedRestaurant != null) {
                                    "konab.menu/${selectedRestaurant!!.toWebSlug()}"
                                } else {
                                    "${restaurants.size} restaurant(s) inscrits • Sélectionnez le vôtre"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFFFFEDD5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (selectedRestaurant == null) {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_refresh_restaurant_directory")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualiser les restaurants",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_close_restaurant_directory")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer l'annuaire",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Body
                val currentSelection = selectedRestaurant
                if (currentSelection != null) {
                    SelectedRestaurantActionView(
                        restaurant = currentSelection,
                        onScanTableQrClick = { showScanner = true },
                        onOpenWebMenuClick = { onOpenRestaurantMenu(currentSelection) },
                        onChooseAnotherClick = { selectedRestaurant = null },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KonabSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Rechercher un restaurant, ville, spécialité...",
                                modifier = Modifier.testTag("input_restaurant_directory_search")
                            )

                            // Bannière scan rapide direct pour les clients qui ont déjà le QR sous la main
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFF7ED),
                                border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showScanner = true }
                                    .testTag("btn_directory_quick_scan_any")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEA580C)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "Scanner QR direct",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Scanner directement un QR Code de table",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF9A3412)
                                        )
                                        Text(
                                            text = "Ouvre immédiatement le menu web du restaurant correspondant",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        when {
                            isLoading && filteredRestaurants.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFEA580C))
                                        Text(
                                            text = "Recherche des restaurants inscrits...",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp
                                        )
                                    }
                                }
                            }

                            errorMessage != null && filteredRestaurants.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KonabEmptyState(
                                        icon = Icons.Default.WifiOff,
                                        title = "Connexion requise",
                                        subtitle = errorMessage,
                                        accentColor = KonabRed,
                                        actionLabel = "Réessayer",
                                        onAction = onRefresh
                                    )
                                }
                            }

                            filteredRestaurants.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    KonabEmptyState(
                                        icon = Icons.Default.RestaurantMenu,
                                        title = if (searchQuery.isBlank()) "Aucun restaurant trouvé" else "Aucun résultat",
                                        subtitle = if (searchQuery.isBlank()) {
                                            "Aucun restaurant inscrit pour le moment. Scannez un QR Code de table ou actualisez la liste."
                                        } else {
                                            "Aucun restaurant ne correspond à « $searchQuery »."
                                        },
                                        accentColor = Color(0xFFEA580C),
                                        actionLabel = "Actualiser",
                                        onAction = onRefresh
                                    )
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isLoading) {
                                        item {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp)),
                                                color = Color(0xFFEA580C)
                                            )
                                        }
                                    }
                                    items(filteredRestaurants, key = { "${it.shopId}_${it.name}" }) { shop ->
                                        PublicRestaurantCard(
                                            restaurant = shop,
                                            onSelect = { selectedRestaurant = shop }
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

    if (showScanner) {
        RealBarcodeScannerModal(
            onDismiss = { showScanner = false },
            onBarcodeScanned = { scanned ->
                showScanner = false
                val sel = selectedRestaurant
                if (sel != null) {
                    onScanTableQrForRestaurant(scanned, sel)
                } else {
                    onScanAnyQr(scanned)
                }
            }
        )
    }
}

@Composable
private fun PublicRestaurantCard(
    restaurant: PublicRestaurantListing,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .konabPressScale(pressedScale = 0.985f)
            .clickable(onClick = onSelect)
            .testTag("card_public_restaurant_${restaurant.shopId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShopLogoAvatar(
                logoUri = restaurant.logoUri ?: "drawable:img_prod_resto",
                size = 58.dp,
                shape = RoundedCornerShape(14.dp),
                contentDescription = "Logo ${restaurant.name}"
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = restaurant.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        color = Color(0xFFFFEDD5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = restaurant.businessType.ifBlank { "Restaurant" },
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9A3412),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (restaurant.slogan.isNotBlank()) {
                    Text(
                        text = restaurant.slogan,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val location = listOf(restaurant.city, restaurant.address)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    if (location.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = location,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        color = KonabBlue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "konab.menu/${restaurant.toWebSlug()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KonabBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEA580C)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Choisir ${restaurant.name}",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedRestaurantActionView(
    restaurant: PublicRestaurantListing,
    onScanTableQrClick: () -> Unit,
    onOpenWebMenuClick: () -> Unit,
    onChooseAnotherClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, Color(0xFFEA580C).copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ShopLogoAvatar(
                            logoUri = restaurant.logoUri ?: "drawable:img_prod_resto",
                            size = 72.dp,
                            shape = RoundedCornerShape(18.dp),
                            contentDescription = "Logo ${restaurant.name}"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = restaurant.name,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                            if (restaurant.slogan.isNotBlank()) {
                                Text(
                                    text = restaurant.slogan,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = KonabBlue.copy(alpha = 0.09f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = KonabBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "https://konab.menu/${restaurant.toWebSlug()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KonabBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    val details = listOfNotNull(
                        restaurant.city.takeIf { it.isNotBlank() },
                        restaurant.address.takeIf { it.isNotBlank() },
                        restaurant.phone.takeIf { it.isNotBlank() }?.let { "Tél: $it" }
                    )
                    if (details.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = details.joinToString(" • "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action 1: Scanner le QR code de la table du restaurant sélectionné
        item {
            Button(
                onClick = onScanTableQrClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("btn_directory_scan_selected_qr")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Scanner le QR Code de ma table",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }

        // Action 2: Ouvrir directement le mini-site Web du restaurant (catalogue avec photos)
        item {
            OutlinedButton(
                onClick = onOpenWebMenuClick,
                border = BorderStroke(1.5.dp, KonabBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KonabBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_directory_open_web_menu")
            ) {
                Icon(
                    imageVector = Icons.Default.RestaurantMenu,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Accéder au Menu Web (Catalogue)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
            }
        }

        // Explication parcours client
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Comment commander chez ${restaurant.name} ?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    DirectoryStepRow("1", "Scannez le QR code posé sur votre table ou ouvrez le menu web.")
                    DirectoryStepRow("2", "Parcourez le catalogue illustré du restaurant avec les photos et prix.")
                    DirectoryStepRow("3", "Sélectionnez vos plats et envoyez la commande : le gérant transmet à la cuisine.")
                }
            }
        }

        item {
            TextButton(
                onClick = onChooseAnotherClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Choisir un autre restaurant", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DirectoryStepRow(stepNumber: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFEDD5)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF9A3412)
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
