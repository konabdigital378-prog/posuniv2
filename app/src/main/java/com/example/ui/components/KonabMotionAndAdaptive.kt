package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.KonabScreen
import com.example.ui.theme.*
import kotlinx.coroutines.delay

enum class KonabWidthClass {
    COMPACT,   // < 600.dp (smartphones portrait)
    MEDIUM,    // 600.dp .. 839.dp (foldables, landscape phones, small tablets)
    EXPANDED   // >= 840.dp (tablets, desktop/ChromeOS multi-window)
}

fun widthClassFromDp(width: Dp): KonabWidthClass = when {
    width >= 840.dp -> KonabWidthClass.EXPANDED
    width >= 600.dp -> KonabWidthClass.MEDIUM
    else -> KonabWidthClass.COMPACT
}

val LocalKonabWidthClass = compositionLocalOf { KonabWidthClass.COMPACT }

/**
 * Centre automatiquement le contenu et limite sa largeur maximale sur grand écran
 * tout en utilisant 100% de la largeur disponible sur téléphone.
 */
@Composable
fun ResponsiveMaxWidthBox(
    maxWidth: Dp = 840.dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth),
            content = content
        )
    }
}

/**
 * Effet tactile ressort (spring scale) non-intrusif lors de l'appui sur les cartes et boutons.
 */
fun Modifier.konabPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    if (!enabled) return@composed this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "konab_press_scale"
    )
    this
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Press -> isPressed = true
                        PointerEventType.Release, PointerEventType.Exit -> isPressed = false
                        PointerEventType.Move -> {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                val dist = (change.position - change.previousPosition).getDistance()
                                if (dist > 14f) isPressed = false
                            }
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Variante basée sur InteractionSource (idéale avec Card/Surface cliquables).
 */
fun Modifier.konabPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "konab_interaction_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Apparition en cascade fluide (fade + slide vertical < 280ms) pour cartes et listes.
 */
@Composable
fun KonabStaggeredReveal(
    index: Int = 0,
    modifier: Modifier = Modifier,
    stepDelayMs: Int = 35,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cappedDelay = (index.coerceIn(0, 6) * stepDelayMs).toLong()
        if (cappedDelay > 0L) delay(cappedDelay)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { (it * 0.14f).toInt().coerceAtLeast(16) }
            )
    ) {
        content()
    }
}

/**
 * Point d'état en direct avec halo pulsant doux (Cloud connecté, Caisse ouverte, Agent actif).
 */
@Composable
fun KonabLivePulseDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(size * 2f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Reflet lumineux diagonal subtil pour les bannières principales et cartes Héros.
 */
fun Modifier.konabHeroShimmer(
    shimmerColor: Color = Color.White,
    maxAlpha: Float = 0.11f
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    this.drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        val startX = width * progress - width * 0.25f
        val endX = startX + width * 0.45f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    shimmerColor.copy(alpha = maxAlpha),
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(endX, height)
            )
        )
    }
}

/**
 * État vide animé et accueillant conforme aux directives Material 3.
 */
@Composable
fun KonabEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_bob")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_y"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { translationY = offsetY }
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Rail de navigation adaptatif pour écrans Medium / Expanded (tablettes, pliables, paysage).
 */
@Composable
fun KonabNavigationRail(
    currentScreen: KonabScreen,
    onNavigate: (KonabScreen) -> Unit,
    cartItemCount: Int = 0
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    val navItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val isMoreSelected = showMoreMenu || currentScreen in listOf(
        KonabScreen.CUSTOMERS,
        KonabScreen.SUPPLIERS,
        KonabScreen.CASH,
        KonabScreen.TRANSACTIONS,
        KonabScreen.SETTINGS,
        KonabScreen.SUBSCRIPTION
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        tonalElevation = 2.dp
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Start + WindowInsetsSides.Vertical
            ),
            header = {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, bottom = 12.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(KonabBlue, KonabBlueDark))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }
            }
        ) {
            NavigationRailItem(
                modifier = Modifier.testTag("nav_dashboard"),
                selected = currentScreen == KonabScreen.DASHBOARD,
                onClick = { onNavigate(KonabScreen.DASHBOARD) },
                icon = {
                    Icon(
                        imageVector = if (currentScreen == KonabScreen.DASHBOARD) Icons.Default.Dashboard else Icons.Default.Home,
                        contentDescription = "Accueil"
                    )
                },
                label = {
                    Text(
                        text = "Accueil",
                        fontWeight = if (currentScreen == KonabScreen.DASHBOARD) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationRailItem(
                modifier = Modifier.testTag("nav_pos"),
                selected = currentScreen == KonabScreen.POS || currentScreen == KonabScreen.PAYMENT,
                onClick = { onNavigate(KonabScreen.POS) },
                icon = {
                    BadgedBox(badge = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = cartItemCount > 0,
                            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = scaleOut()
                        ) {
                            Badge(
                                containerColor = KonabOrange,
                                contentColor = Color.White
                            ) {
                                Text("$cartItemCount", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }) {
                        Icon(Icons.Default.PointOfSale, contentDescription = "Ventes")
                    }
                },
                label = {
                    Text(
                        text = "Caisse",
                        fontWeight = if (currentScreen == KonabScreen.POS || currentScreen == KonabScreen.PAYMENT) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationRailItem(
                modifier = Modifier.testTag("nav_stock"),
                selected = currentScreen == KonabScreen.STOCK,
                onClick = { onNavigate(KonabScreen.STOCK) },
                icon = { Icon(Icons.Default.Inventory2, contentDescription = "Stock") },
                label = {
                    Text(
                        text = "Stock",
                        fontWeight = if (currentScreen == KonabScreen.STOCK) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            NavigationRailItem(
                modifier = Modifier.testTag("nav_reports"),
                selected = currentScreen == KonabScreen.REPORTS,
                onClick = { onNavigate(KonabScreen.REPORTS) },
                icon = { Icon(Icons.Default.Assessment, contentDescription = "Stats") },
                label = {
                    Text(
                        text = "Stats",
                        fontWeight = if (currentScreen == KonabScreen.REPORTS) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )

            Spacer(modifier = Modifier.weight(1f))

            NavigationRailItem(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .testTag("nav_more"),
                selected = isMoreSelected,
                onClick = { showMoreMenu = true },
                icon = {
                    Box {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Plus")
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Historique des ventes", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.TRANSACTIONS)
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = KonabBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clients & Crédits", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.CUSTOMERS)
                                },
                                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = KonabBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Caisse & Dépenses", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.CASH)
                                },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = KonabSuccess) }
                            )
                            DropdownMenuItem(
                                text = { Text("Fournisseurs & Achats", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.SUPPLIERS)
                                },
                                leadingIcon = { Icon(Icons.Default.LocalShipping, contentDescription = null, tint = KonabBlue) }
                            )
                            DropdownMenuItem(
                                text = { Text("Abonnement Konab", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.SUBSCRIPTION)
                                },
                                leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = KonabOrange) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            DropdownMenuItem(
                                text = { Text("Paramètres", fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(KonabScreen.SETTINGS)
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                            )
                        }
                    }
                },
                label = {
                    Text(
                        text = "Plus",
                        fontWeight = if (isMoreSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = navItemColors
            )
        }
    }
}
