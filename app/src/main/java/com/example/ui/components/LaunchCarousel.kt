package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.supabase.AdCarouselSettings
import com.example.data.remote.supabase.ShopAdRecord
import com.example.ui.theme.*
import com.example.util.ShopLogoAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LaunchSlide(
    val imageRes: Int,
    val tag: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color = KonabOrange
)

val defaultLaunchSlides = listOf(
    LaunchSlide(
        imageRes = R.drawable.hero_entrepreneur,
        tag = "Caisse Intelligente",
        title = "Konab POS : La Caisse Mobile",
        description = "Une solution tactile moderne et intuitive conçue spécialement pour les commerçants d'Afrique de l'Ouest.",
        icon = Icons.Default.Storefront,
        accentColor = KonabOrange
    ),
    LaunchSlide(
        imageRes = R.drawable.img_slide_caisse_pos,
        tag = "Vente Rapide",
        title = "Encaissement Express & Mobile Money",
        description = "Enregistrez vos ventes, scannez les codes-barres et acceptez Cash, Orange Money et Moov Money en quelques clics.",
        icon = Icons.Default.PointOfSale,
        accentColor = KonabGreen
    ),
    LaunchSlide(
        imageRes = R.drawable.img_slide_stock_stats,
        tag = "Gestion de Stock",
        title = "Inventaire en Temps Réel & Alertes",
        description = "Surveillez vos stocks disponibles, recevez des alertes automatiques et pilotez votre rentabilité au quotidien.",
        icon = Icons.Default.Inventory,
        accentColor = Color(0xFF0284C7)
    ),
    LaunchSlide(
        imageRes = R.drawable.burkina_commerce_faso,
        tag = "100% Hors-Ligne",
        title = "Fonctionne Sans Connexion Internet",
        description = "Vos données restent en sécurité sur votre appareil. Vendez en continu même en cas de coupure de réseau.",
        icon = Icons.Default.CloudOff,
        accentColor = Color(0xFF10B981)
    ),
    LaunchSlide(
        imageRes = R.drawable.burkina_ouaga_market,
        tag = "Crédits & Clients",
        title = "Carnet de Dettes & Reçus Bluetooth",
        description = "Suivez les dettes de vos clients réguliers, imprimez des tickets de caisse et envoyez les reçus sur WhatsApp.",
        icon = Icons.Default.People,
        accentColor = KonabGoldDark
    )
)

private fun resolveBackdropForBusinessType(businessType: String): Int {
    val lower = businessType.lowercase()
    return when {
        "resto" in lower || "restaurant" in lower || "maquis" in lower || "boulangerie" in lower -> R.drawable.img_prod_resto
        "mode" in lower || "boutique" in lower || "couture" in lower -> R.drawable.burkina_commerce_faso
        "quincaillerie" in lower || "btp" in lower || "mécanique" in lower -> R.drawable.img_prod_quincaillerie
        "épicerie" in lower || "alimentation" in lower || "kiosque" in lower -> R.drawable.burkina_ouaga_market
        "coiffure" in lower || "salon" in lower -> R.drawable.hero_entrepreneur
        else -> R.drawable.burkina_bobo_dioulasso
    }
}

@Composable
fun LaunchImageCarousel(
    modifier: Modifier = Modifier,
    slides: List<LaunchSlide> = defaultLaunchSlides,
    shopAds: List<ShopAdRecord> = emptyList(),
    carouselSettings: AdCarouselSettings = AdCarouselSettings(),
    autoScrollDelayMs: Long = 3800L,
    pauseAutoScroll: Boolean = false,
    onOpenAdConfig: (() -> Unit)? = null,
    onShopAdClick: ((ShopAdRecord) -> Unit)? = null
) {
    val showingAds = shopAds.isNotEmpty()
    val pageCount = if (showingAds) shopAds.size else slides.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.coerceAtLeast(1) })
    val coroutineScope = rememberCoroutineScope()
    val effectiveIntervalMs = (carouselSettings.scrollIntervalSeconds.coerceIn(2, 15) * 1000L)
        .takeIf { carouselSettings.scrollIntervalSeconds != 4 } ?: autoScrollDelayMs

    LaunchedEffect(pageCount) {
        if (pageCount > 0 && pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage(0)
        }
    }

    // Auto-scroll loop controlled by AdCarouselSettings when user is not manually interacting or watching modal
    LaunchedEffect(
        pagerState.isScrollInProgress,
        pageCount,
        pauseAutoScroll,
        showingAds,
        carouselSettings.autoScrollEnabled,
        effectiveIntervalMs
    ) {
        if (carouselSettings.autoScrollEnabled && !pagerState.isScrollInProgress && !pauseAutoScroll && pageCount > 1) {
            while (true) {
                val currentAdHasVideo = showingAds &&
                    carouselSettings.autoPlayVideoPreviewInCarousel &&
                    pagerState.currentPage in shopAds.indices &&
                    shopAds[pagerState.currentPage].hasVideo
                val waitMs = if (currentAdHasVideo) maxOf(effectiveIntervalMs, 9500L) else effectiveIntervalMs
                delay(waitMs)
                val nextPage = (pagerState.currentPage + 1) % pageCount
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val widthClass = LocalKonabWidthClass.current
    val carouselHeight = when (widthClass) {
        KonabWidthClass.COMPACT -> 226.dp
        KonabWidthClass.MEDIUM -> 252.dp
        KonabWidthClass.EXPANDED -> 282.dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Carousel Card with HorizontalPager (Exact Design Preserved)
        Card(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(carouselHeight)
                .testTag("launch_image_carousel")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (showingAds && page in shopAds.indices) {
                        val ad = shopAds[page]
                        val isSettledCurrentPage = pagerState.currentPage == page && !pagerState.isScrollInProgress && !pauseAutoScroll
                        var inlineVideoFailed by remember(ad.id, ad.videoUri) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = onShopAdClick != null) {
                                    onShopAdClick?.invoke(ad)
                                }
                        ) {
                            // Background Image (always rendered underneath for seamless loading)
                            Image(
                                painter = painterResource(id = resolveBackdropForBusinessType(ad.businessType)),
                                contentDescription = ad.effectiveTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Live 60s Video Preview right inside the home screen carousel frame
                            if (ad.hasVideo && carouselSettings.autoPlayVideoPreviewInCarousel && isSettledCurrentPage && !inlineVideoFailed) {
                                ShopAdVideoSurface(
                                    videoUri = ad.videoUri,
                                    modifier = Modifier.fillMaxSize(),
                                    isMuted = true,
                                    isPlaying = true,
                                    loop = true,
                                    resizeZoom = true,
                                    onPlaybackError = { inlineVideoFailed = true }
                                )
                            }

                            // Gradient Scrim for crisp text readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                KonabBlueDeep.copy(alpha = 0.34f),
                                                Color.Transparent,
                                                KonabBlueDeep.copy(alpha = 0.92f)
                                            )
                                        )
                                    )
                            )

                            // Top-Start Shop / Ad Badge
                            val accentColor = if (ad.hasVideo) KonabOrange else KonabGreen
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KonabBlueDark.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.80f)),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (ad.hasVideo) Icons.Default.PlayCircle else Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val tagText = if (ad.hasVideo) {
                                        "PUB VIDÉO • ${ad.videoDurationSec.coerceIn(1, 60)}s"
                                    } else {
                                        listOfNotNull(
                                            ad.businessType.takeIf { it.isNotBlank() },
                                            ad.city.takeIf { it.isNotBlank() }
                                        ).joinToString(" • ").ifBlank { "Boutique KONAB" }
                                    }
                                    Text(
                                        text = tagText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Bottom Shop Logo + Ad Title/Description + Watch/Contact Chip
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ShopLogoAvatar(
                                    logoUri = ad.logoUri.ifBlank { null },
                                    size = 46.dp,
                                    shape = CircleShape,
                                    contentDescription = "Logo ${ad.shopName}"
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ad.effectiveTitle,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!ad.effectiveTitle.equals(ad.shopName, ignoreCase = true)) {
                                        Text(
                                            text = ad.shopName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KonabOrangeLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = ad.effectiveDescription,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (onShopAdClick != null) {
                                    Surface(
                                        onClick = { onShopAdClick(ad) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (ad.hasVideo) KonabOrange else KonabGreen,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                                        modifier = Modifier.testTag("btn_carousel_watch_contact_${ad.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (ad.hasVideo) Icons.Default.PlayArrow else Icons.Default.Call,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (ad.hasVideo) "Voir" else ad.ctaLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val slide = slides[page.coerceIn(0, slides.lastIndex)]
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = slide.imageRes),
                                contentDescription = slide.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                KonabBlueDeep.copy(alpha = 0.28f),
                                                Color.Transparent,
                                                KonabBlueDeep.copy(alpha = 0.90f)
                                            )
                                        )
                                    )
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = KonabBlueDark.copy(alpha = 0.88f),
                                border = BorderStroke(1.dp, slide.accentColor.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = slide.icon,
                                        contentDescription = null,
                                        tint = slide.accentColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = slide.tag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = slide.title,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = slide.description,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Top-Right Controls inside the Carousel Modal: Ad Config Trigger + Slide Counter Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenAdConfig != null) {
                        Surface(
                            onClick = onOpenAdConfig,
                            shape = RoundedCornerShape(10.dp),
                            color = KonabOrange.copy(alpha = 0.92f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("btn_carousel_open_ad_config")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "Configurer une publicité",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Pub",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $pageCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                // Interactive Arrow Buttons on overlay
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Précédent",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (pagerState.currentPage < pageCount - 1) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Suivant",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Animated Page Indicator Dots / Pills
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            (0 until pageCount).forEach { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 28.dp else 8.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "indicator_width"
                )
                val color = if (isSelected) KonabOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                )
            }
        }
    }
}
