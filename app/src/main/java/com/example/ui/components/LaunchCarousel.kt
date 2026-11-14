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
import com.example.ui.theme.*
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

@Composable
fun LaunchImageCarousel(
    modifier: Modifier = Modifier,
    slides: List<LaunchSlide> = defaultLaunchSlides,
    autoScrollDelayMs: Long = 3800L
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll loop when user is not manually interacting
    LaunchedEffect(pagerState.isScrollInProgress, slides.size) {
        if (!pagerState.isScrollInProgress && slides.isNotEmpty()) {
            while (true) {
                delay(autoScrollDelayMs)
                val nextPage = (pagerState.currentPage + 1) % slides.size
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
        // Main Carousel Card with HorizontalPager
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
                    val slide = slides[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background Image
                        Image(
                            painter = painterResource(id = slide.imageRes),
                            contentDescription = slide.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Gradient Scrim for crisp text readability
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

                        // Top Tag / Badge
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

                        // Top-Right Slide Counter Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${page + 1} / ${slides.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        // Bottom Text Overlay (Title & Description)
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

                if (pagerState.currentPage < slides.size - 1) {
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
            slides.indices.forEach { index ->
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
