package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.entity.ShopProfileEntity
import com.example.data.remote.supabase.AdCarouselSettings
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.data.remote.supabase.ShopAdRecord
import com.example.data.remote.supabase.ShopAdStorage
import com.example.data.remote.supabase.SupabaseConfig
import com.example.data.remote.supabase.SupabaseSyncManager
import com.example.ui.theme.*
import com.example.util.ShopLogoAvatar
import com.example.util.ShopLogoHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

object ShopAdContactHelper {
    fun dialShop(context: Context, rawPhone: String, shopName: String) {
        val cleanDial = rawPhone.filter { it.isDigit() || it == '+' }.trim()
        if (cleanDial.isBlank()) {
            Toast.makeText(context, "Aucun numéro de téléphone enregistré pour $shopName", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanDial")))
        } catch (_: Exception) {
            Toast.makeText(context, "Appelez $shopName au $rawPhone", Toast.LENGTH_LONG).show()
        }
    }

    fun openWhatsApp(context: Context, rawPhone: String, shopName: String, adTitle: String) {
        val waPhone = ShopAdStorage.formatInternationalWaPhone(rawPhone)
        if (waPhone.isBlank()) {
            Toast.makeText(context, "Aucun numéro WhatsApp enregistré pour $shopName", Toast.LENGTH_SHORT).show()
            return
        }
        val msg = "Bonjour $shopName, j'ai vu votre publicité « $adTitle » sur KONAB POS et je souhaite obtenir plus d'informations."
        val uri = Uri.parse("https://wa.me/$waPhone?text=${Uri.encode(msg)}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$msg ($rawPhone)")
            }
            try {
                context.startActivity(Intent.createChooser(fallback, "Contacter $shopName"))
            } catch (_: Exception) {
                Toast.makeText(context, "WhatsApp $shopName : $rawPhone", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Lecteur vidéo Media3 ExoPlayer réutilisable, plafonné strictement à 60 secondes (1 minute)
 * via ClippingConfiguration.
 */
@OptIn(UnstableApi::class)
@Composable
fun ShopAdVideoSurface(
    videoUri: String,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false,
    isPlaying: Boolean = true,
    loop: Boolean = true,
    resizeZoom: Boolean = false,
    replayTrigger: Int = 0,
    onProgressChange: ((positionSec: Int, durationSec: Int) -> Unit)? = null,
    onEnded: (() -> Unit)? = null,
    onPlaybackError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasError by remember(videoUri) { mutableStateOf(false) }

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            val clippedMediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(ShopAdStorage.MAX_AD_VIDEO_DURATION_MS)
                        .build()
                )
                .build()
            setMediaItem(clippedMediaItem)
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            playWhenReady = isPlaying
            prepare()
        }
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        exoPlayer.playWhenReady = isPlaying
        if (isPlaying && !hasError) {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer, replayTrigger) {
        if (replayTrigger > 0) {
            hasError = false
            exoPlayer.seekTo(0L)
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                onPlaybackError?.invoke(error.localizedMessage ?: "Lecture vidéo indisponible")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onEnded?.invoke()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> if (isPlaying && !hasError) exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, videoUri) {
        while (true) {
            val posSec = (exoPlayer.currentPosition / 1000L).toInt()
                .coerceIn(0, ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC)
            val rawDurMs = exoPlayer.duration
            val durSec = if (rawDurMs > 0L && rawDurMs != androidx.media3.common.C.TIME_UNSET) {
                ((rawDurMs + 500L) / 1000L).toInt().coerceIn(1, ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC)
            } else {
                ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC
            }
            onProgressChange?.invoke(posSec, durSec)
            delay(250L)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.view_konab_ad_player, null) as PlayerView
                view.apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (resizeZoom) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = if (resizeZoom) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Modal de lecture vidéo 60s + Contact direct pour tous les utilisateurs depuis le carrousel d'accueil.
 */
@Composable
fun ShopAdVideoPlayerDialog(
    ad: ShopAdRecord,
    onDismiss: () -> Unit,
    onEditAd: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var replayTrigger by remember { mutableIntStateOf(0) }
    var currentPosSec by remember { mutableIntStateOf(0) }
    var totalDurSec by remember {
        mutableIntStateOf(
            if (ad.videoDurationSec > 0) ad.videoDurationSec.coerceIn(1, ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC)
            else ShopAdStorage.MAX_AD_VIDEO_DURATION_SEC
        )
    }
    var videoError by remember(ad.videoUri) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .testTag("dialog_shop_ad_player")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Boutique & Logo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(KonabBlueDeep, KonabBlue)))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShopLogoAvatar(
                            logoUri = ad.logoUri.ifBlank { null },
                            size = 46.dp,
                            shape = CircleShape,
                            contentDescription = "Logo ${ad.shopName}"
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ad.shopName,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = KonabOrange
                                ) {
                                    Text(
                                        text = if (ad.hasVideo) "PUB 60s" else "BOUTIQUE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val subInfo = listOfNotNull(
                                ad.businessType.takeIf { it.isNotBlank() },
                                ad.city.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            if (subInfo.isNotBlank()) {
                                Text(
                                    text = subInfo,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFD6E4FA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                .testTag("btn_close_ad_player")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Zone Vidéo 60s (si configurée)
                if (ad.hasVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(235.dp)
                            .background(Color.Black)
                            .clickable { isPlaying = !isPlaying }
                    ) {
                        if (videoError == null) {
                            ShopAdVideoSurface(
                                videoUri = ad.videoUri,
                                modifier = Modifier.fillMaxSize(),
                                isMuted = isMuted,
                                isPlaying = isPlaying,
                                loop = true,
                                resizeZoom = false,
                                replayTrigger = replayTrigger,
                                onProgressChange = { pos, dur ->
                                    currentPosSec = pos
                                    totalDurSec = dur
                                },
                                onPlaybackError = { err -> videoError = err }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = KonabOrangeLight,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Vidéo temporairement indisponible hors-ligne",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Contactez directement la boutique ci-dessous.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // Badge Temps 00:XX / 01:00 (max 60s)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                KonabLivePulseDot(color = KonabOrange, size = 6.dp)
                                Text(
                                    text = "${ShopAdStorage.formatSecondsAsTimer(currentPosSec)} / ${ShopAdStorage.formatSecondsAsTimer(totalDurSec)}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Contrôles Son / Replay / Play-Pause
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = { replayTrigger++ },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = "Rejouer", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Activer le son" else "Couper le son",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!isPlaying && videoError == null) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(KonabOrange.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Lire", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        // Barre de progression 60s
                        val progress = if (totalDurSec > 0) (currentPosSec.toFloat() / totalDurSec.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            color = KonabOrange,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }

                // Détails publicité + Boutons Contact Direct
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = ad.effectiveTitle,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = ad.effectiveDescription,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    val displayPhone = ad.phone.ifBlank { ad.whatsappPhone }
                    if (displayPhone.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KonabBlue.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Contact direct : $displayPhone",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ShopAdContactHelper.dialShop(
                                    context = context,
                                    rawPhone = ad.phone.ifBlank { ad.whatsappPhone },
                                    shopName = ad.shopName
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ad_call_shop")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Appeler", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                ShopAdContactHelper.openWhatsApp(
                                    context = context,
                                    rawPhone = ad.effectiveWhatsapp,
                                    shopName = ad.shopName,
                                    adTitle = ad.effectiveTitle
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_ad_whatsapp_shop")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (onEditAd != null) {
                        TextButton(
                            onClick = {
                                onDismiss()
                                onEditAd()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configurer / Modifier cette publicité", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Système complet de configuration de publicité lancé directement depuis le petit cadre carrousel de l'écran d'accueil.
 */
@Composable
fun ShopAdConfigModal(
    currentShop: ShopProfileEntity?,
    registeredShops: List<ShopProfileEntity>,
    initialAdToEdit: ShopAdRecord? = null,
    onDismiss: () -> Unit,
    onPublished: (ShopAdRecord) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { SupabaseSyncManager(context) }

    var activeTab by remember { mutableIntStateOf(0) } // 0: Configurer, 1: Boutiques partenaires, 2: Défilement
    var savedAds by remember {
        mutableStateOf(ShopAdStorage.getAllAds(context).filter { !it.isFallbackShopOnly })
    }
    var carouselSettings by remember {
        mutableStateOf(ShopAdStorage.getCarouselSettings(context))
    }

    val candidateShops = remember(currentShop, registeredShops) {
        val map = LinkedHashMap<String, ShopProfileEntity>()
        if (currentShop != null && currentShop.name.isNotBlank()) {
            map[AgentDeploymentStorage.normalizeShopName(currentShop.name)] = currentShop
        }
        registeredShops.filter { it.name.isNotBlank() }.forEach { s ->
            val k = AgentDeploymentStorage.normalizeShopName(s.name)
            if (!map.containsKey(k)) map[k] = s
        }
        map.values.toList()
    }

    var partnerShopsRevision by remember { mutableIntStateOf(0) }
    val partnerShopEntries = remember(candidateShops, savedAds, partnerShopsRevision) {
        ShopAdStorage.buildManageablePartnerShops(
            registeredShops = candidateShops,
            ads = savedAds,
            localShop = currentShop,
            localDeployments = AgentDeploymentStorage.getAllDeployments(context)
        )
    }

    val defaultSource = initialAdToEdit
    var editingAdId by remember {
        mutableStateOf(
            if (defaultSource != null && !defaultSource.isFallbackShopOnly) defaultSource.id
            else "AD-${System.currentTimeMillis()}"
        )
    }
    var shopName by remember { mutableStateOf(defaultSource?.shopName ?: currentShop?.name.orEmpty()) }
    var businessType by remember { mutableStateOf(defaultSource?.businessType ?: currentShop?.businessType ?: "Boutique") }
    var city by remember { mutableStateOf(defaultSource?.city ?: currentShop?.city.orEmpty()) }
    var phone by remember { mutableStateOf(defaultSource?.phone ?: currentShop?.phone.orEmpty()) }
    var whatsappPhone by remember { mutableStateOf(defaultSource?.whatsappPhone ?: currentShop?.phone.orEmpty()) }
    var logoUri by remember { mutableStateOf(defaultSource?.logoUri ?: currentShop?.logoUri.orEmpty()) }
    var localLogoFile by remember { mutableStateOf<File?>(null) }

    var adTitle by remember { mutableStateOf(defaultSource?.adTitle ?: currentShop?.name.orEmpty()) }
    var adDescription by remember { mutableStateOf(defaultSource?.adDescription ?: currentShop?.slogan.orEmpty()) }
    var ctaLabel by remember { mutableStateOf(defaultSource?.ctaLabel ?: "Contacter") }
    var isActive by remember { mutableStateOf(defaultSource?.isActive ?: true) }

    var attachPromoVideo by remember { mutableStateOf(!defaultSource?.videoUri.isNullOrBlank()) }
    var videoUri by remember { mutableStateOf(defaultSource?.videoUri.orEmpty()) }
    var videoDurationSec by remember { mutableIntStateOf(defaultSource?.videoDurationSec ?: 0) }
    var originalDurationNote by remember { mutableStateOf<String?>(null) }
    var localVideoFile by remember { mutableStateOf<File?>(null) }
    var videoUrlInput by remember {
        mutableStateOf(
            if (defaultSource?.videoUri?.startsWith("http") == true) defaultSource.videoUri else ""
        )
    }

    var isPublishing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadAdIntoEditor(ad: ShopAdRecord, forceEnableVideoOption: Boolean = false) {
        editingAdId = if (ad.isFallbackShopOnly) "AD-${System.currentTimeMillis()}" else ad.id
        shopName = ad.shopName
        businessType = ad.businessType
        city = ad.city
        phone = ad.phone
        whatsappPhone = ad.whatsappPhone
        logoUri = ad.logoUri
        localLogoFile = null
        adTitle = ad.adTitle.ifBlank { ad.shopName }
        adDescription = ad.adDescription
        ctaLabel = ad.ctaLabel
        isActive = ad.isActive
        attachPromoVideo = forceEnableVideoOption || ad.videoUri.isNotBlank()
        videoUri = ad.videoUri
        videoDurationSec = ad.videoDurationSec
        localVideoFile = null
        videoUrlInput = if (ad.videoUri.startsWith("http")) ad.videoUri else ""
        originalDurationNote = null
        activeTab = 0
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val res = ShopAdStorage.copyAdLogoToInternalStorage(context, uri)
            if (res != null) {
                logoUri = res.first
                localLogoFile = res.second
                Toast.makeText(context, "Logo importé pour la publicité", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Impossible de lire ce logo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val picked = ShopAdStorage.copyVideoToInternalStorage(context, uri)
            if (picked != null) {
                attachPromoVideo = true
                videoUri = picked.localUri
                localVideoFile = picked.localFile
                videoUrlInput = ""
                videoDurationSec = picked.effectiveDurationSec
                originalDurationNote = if (picked.wasTrimmedTo60s) {
                    "Vidéo originale (${picked.originalDurationSec}s) limitée automatiquement à 60 secondes (1 min max)."
                } else {
                    "Vidéo prête : ${picked.effectiveDurationSec}s / 60s max"
                }
            } else {
                Toast.makeText(context, "Impossible de charger la vidéo sélectionnée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .widthIn(max = 620.dp)
                .testTag("dialog_shop_ad_config")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(KonabBlueDeep, KonabBlue)))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(KonabOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Régie Publicitaire Boutiques",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Diffusez votre boutique & vidéo 60s sur l'écran d'accueil",
                                fontSize = 11.5.sp,
                                color = Color(0xFFD6E4FA)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                .testTag("btn_close_ad_config")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Tabs (3 onglets : Configurer Pub & Vidéo 60s / Boutiques partenaires / Paramètres défilement)
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = KonabBlue
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Configurer", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                        modifier = Modifier.testTag("tab_ad_config_editor")
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = {
                            savedAds = ShopAdStorage.getAllAds(context).filter { !it.isFallbackShopOnly }
                            partnerShopsRevision++
                            activeTab = 1
                        },
                        text = { Text("Boutiques (${partnerShopEntries.size})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                        modifier = Modifier.testTag("tab_ad_partner_shops")
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("Défilement", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) },
                        modifier = Modifier.testTag("tab_ad_scroll_settings")
                    )
                }

                if (activeTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Sélecteur rapide d'une boutique inscrite
                        if (candidateShops.isNotEmpty()) {
                            Text(
                                text = "Boutique inscrite (pré-remplissage rapide)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(candidateShops) { shop ->
                                    val isSelected = AgentDeploymentStorage.normalizeShopName(shop.name) ==
                                        AgentDeploymentStorage.normalizeShopName(shopName)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            shopName = shop.name
                                            businessType = shop.businessType.ifBlank { "Boutique" }
                                            city = shop.city
                                            phone = shop.phone
                                            if (whatsappPhone.isBlank()) whatsappPhone = shop.phone
                                            if (!shop.logoUri.isNullOrBlank()) {
                                                logoUri = shop.logoUri
                                                localLogoFile = null
                                            }
                                            if (adTitle.isBlank()) adTitle = shop.name
                                            if (adDescription.isBlank()) adDescription = shop.slogan
                                        },
                                        leadingIcon = {
                                            ShopLogoAvatar(
                                                logoUri = shop.logoUri,
                                                size = 20.dp,
                                                shape = CircleShape
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = shop.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Section 1 : Logo & Identité boutique
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "1. Boutique & Logo",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = KonabBlue
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ShopLogoAvatar(
                                        logoUri = logoUri.ifBlank { null },
                                        size = 64.dp,
                                        shape = CircleShape,
                                        showEditBadge = true,
                                        onClick = {
                                            logoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                logoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.heightIn(min = 42.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Choisir le logo (Galerie)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "Ou sélectionnez un logo rapide ci-dessous :",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(ShopLogoHelper.PRESET_LOGOS) { preset ->
                                        val isChosen = logoUri == preset.key
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = if (isChosen) 2.5.dp else 1.dp,
                                                    color = if (isChosen) KonabOrange else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    logoUri = preset.key
                                                    localLogoFile = null
                                                }
                                        ) {
                                            ShopLogoAvatar(
                                                logoUri = preset.key,
                                                size = 46.dp,
                                                shape = CircleShape
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = shopName,
                                    onValueChange = { shopName = it; errorMessage = null },
                                    label = { Text("Nom de la boutique *") },
                                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ad_shop_name")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = businessType,
                                        onValueChange = { businessType = it },
                                        label = { Text("Activité") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it },
                                        label = { Text("Ville") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Section 2 : Option Vidéo Promotionnelle 60 secondes (1 min max)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = KonabOrange.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "2. Vidéo Promotionnelle (60s max)",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.5.sp,
                                            color = KonabOrange
                                        )
                                        Text(
                                            text = "Option : associer une vidéo de 60s au logo partenaire",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (attachPromoVideo && videoUri.isNotBlank()) KonabOrange else KonabBlueDark.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = if (attachPromoVideo && videoUri.isNotBlank()) {
                                                    "${ShopAdStorage.clampVideoDurationSec(videoDurationSec, true)}s / 60s"
                                                } else {
                                                    "MAX 60s"
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Switch(
                                            checked = attachPromoVideo,
                                            onCheckedChange = { enabled ->
                                                attachPromoVideo = enabled
                                            },
                                            modifier = Modifier.testTag("switch_attach_promo_video")
                                        )
                                    }
                                }

                                if (attachPromoVideo) {
                                    Text(
                                        text = "Une fois publiée, cette vidéo promotionnelle de 60 secondes défilera sur le modal d'accueil pour tous les utilisateurs.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("btn_pick_ad_video")
                                        ) {
                                            Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (videoUri.isBlank()) "Importer vidéo (≤ 60s)" else "Changer la vidéo",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }

                                        if (videoUri.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    videoUri = ""
                                                    localVideoFile = null
                                                    videoDurationSec = 0
                                                    videoUrlInput = ""
                                                    originalDurationNote = null
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Retirer vidéo", tint = KonabRed)
                                            }
                                        }
                                    }

                                    if (!originalDurationNote.isNullOrBlank()) {
                                        Text(
                                            text = originalDurationNote!!,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KonabGreen
                                        )
                                    }

                                    OutlinedTextField(
                                        value = videoUrlInput,
                                        onValueChange = { url ->
                                            videoUrlInput = url
                                            val clean = url.trim()
                                            if (clean.startsWith("http://") || clean.startsWith("https://")) {
                                                attachPromoVideo = true
                                                videoUri = clean
                                                localVideoFile = null
                                                if (videoDurationSec <= 0) videoDurationSec = 60
                                            } else if (clean.isBlank() && localVideoFile == null) {
                                                videoUri = ""
                                                videoDurationSec = 0
                                            }
                                        },
                                        label = { Text("Ou coller un lien vidéo MP4 public (HTTPS)") },
                                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Aperçu vidéo intégré
                                    if (videoUri.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Black)
                                        ) {
                                            ShopAdVideoSurface(
                                                videoUri = videoUri,
                                                modifier = Modifier.fillMaxSize(),
                                                isMuted = true,
                                                isPlaying = true,
                                                loop = true,
                                                resizeZoom = true
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Black.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "Aperçu en boucle (max 60s)",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Mode Logo & Bannière uniquement : activez l'interrupteur ci-dessus si vous souhaitez associer une vidéo promotionnelle de 60 secondes.",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Section 3 : Message & Contact Direct Client
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "3. Message Publicitaire & Contact Direct",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = KonabBlue
                                )

                                OutlinedTextField(
                                    value = adTitle,
                                    onValueChange = { adTitle = it; errorMessage = null },
                                    label = { Text("Titre accrocheur de la publicité *") },
                                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ad_title")
                                )

                                OutlinedTextField(
                                    value = adDescription,
                                    onValueChange = { adDescription = it },
                                    label = { Text("Description / Offre promotionnelle") },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ad_description")
                                )

                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = {
                                        phone = it
                                        errorMessage = null
                                    },
                                    label = { Text("Téléphone d'appel direct *") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ad_phone")
                                )

                                OutlinedTextField(
                                    value = whatsappPhone,
                                    onValueChange = { whatsappPhone = it },
                                    label = { Text("Numéro WhatsApp") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                                    trailingIcon = {
                                        if (phone.isNotBlank() && whatsappPhone != phone) {
                                            TextButton(onClick = { whatsappPhone = phone }) {
                                                Text("= Tél", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ad_whatsapp")
                                )

                                Text(
                                    text = "Bouton d'action sur l'affiche :",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(listOf("Contacter", "Commander", "Profiter de l'offre", "Appeler")) { labelOption ->
                                        FilterChip(
                                            selected = ctaLabel == labelOption,
                                            onClick = { ctaLabel = labelOption },
                                            label = { Text(labelOption, fontSize = 11.5.sp) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Diffuser sur l'écran d'accueil",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Visible par tous les utilisateurs de KONAB POS",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isActive,
                                        onCheckedChange = { isActive = it }
                                    )
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage!!,
                                color = KonabRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Footer Action
                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Annuler", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    val cleanShop = shopName.trim()
                                    val cleanPhone = phone.trim()
                                    if (cleanShop.isBlank()) {
                                        errorMessage = "Veuillez renseigner le nom de la boutique."
                                        return@Button
                                    }
                                    if (cleanPhone.isBlank() && whatsappPhone.isBlank()) {
                                        errorMessage = "Veuillez saisir au moins un numéro de contact (Téléphone ou WhatsApp)."
                                        return@Button
                                    }
                                    isPublishing = true
                                    errorMessage = null
                                    val activeAgent = AgentDeploymentStorage.getSavedAgentSession(context)
                                    val activeVideoUri = if (attachPromoVideo) videoUri.trim() else ""
                                    val activeVideoFile = if (attachPromoVideo) localVideoFile else null
                                    val record = ShopAdRecord(
                                        id = editingAdId,
                                        shopId = currentShop?.id?.toLong() ?: 0L,
                                        shopName = cleanShop,
                                        businessType = businessType.trim().ifBlank { "Boutique" },
                                        city = city.trim(),
                                        phone = cleanPhone.ifBlank { whatsappPhone.trim() },
                                        whatsappPhone = whatsappPhone.trim().ifBlank { cleanPhone },
                                        logoUri = logoUri.trim(),
                                        adTitle = adTitle.trim().ifBlank { cleanShop },
                                        adDescription = adDescription.trim(),
                                        videoUri = activeVideoUri,
                                        videoDurationSec = ShopAdStorage.clampVideoDurationSec(
                                            videoDurationSec,
                                            activeVideoUri.isNotBlank()
                                        ),
                                        ctaLabel = ctaLabel.trim().ifBlank { "Contacter" },
                                        isActive = isActive,
                                        updatedAt = System.currentTimeMillis(),
                                        deployerId = activeAgent?.id.orEmpty(),
                                        isSynced = false,
                                        isFallbackShopOnly = false
                                    )
                                    scope.launch {
                                        val res = syncManager.publishShopAd(
                                            ad = record,
                                            localLogoFile = localLogoFile,
                                            localVideoFile = activeVideoFile
                                        )
                                        isPublishing = false
                                        if (res.isSuccess) {
                                            val published = res.getOrThrow()
                                            val statusText = if (published.isSynced) {
                                                "Publicité publiée et diffusée à tous les utilisateurs !"
                                            } else if (SupabaseConfig.isConfigured) {
                                                "Publicité enregistrée localement (synchronisation Cloud en attente)."
                                            } else {
                                                "Publicité enregistrée !"
                                            }
                                            Toast.makeText(context, statusText, Toast.LENGTH_LONG).show()
                                            onPublished(published)
                                            onDismiss()
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Erreur lors de la publication."
                                        }
                                    }
                                },
                                enabled = !isPublishing,
                                colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_publish_shop_ad")
                            ) {
                                if (isPublishing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Publication...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Publier la publicité", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (activeTab == 1) {
                    // Tab 1 : Gestion complète des logos des boutiques partenaires dans le défilement
                    if (partnerShopEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = KonabOrange, modifier = Modifier.size(44.dp))
                                Text("Aucune boutique partenaire", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = "Configurez une boutique partenaire avec son logo et une vidéo promo de 60s pour la diffuser à l'écran d'accueil.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { activeTab = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabOrange)
                                ) {
                                    Text("Configurer une boutique", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = KonabBlue.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.ViewCarousel, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Activez ou mettez en pause chaque logo partenaire dans le modal d'accueil et associez-lui une vidéo promo 60s.",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }

                            items(partnerShopEntries, key = { "${it.id}_${it.shopName}" }) { ad ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (ad.isActive) KonabBlue.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            ShopLogoAvatar(
                                                logoUri = ad.logoUri.ifBlank { null },
                                                size = 44.dp,
                                                shape = CircleShape
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = ad.shopName,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 14.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (ad.hasVideo) KonabOrange else KonabBlue.copy(alpha = 0.14f)
                                                    ) {
                                                        Text(
                                                            text = if (ad.hasVideo) "VIDÉO ${ad.videoDurationSec}s" else "LOGO",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (ad.hasVideo) Color.White else KonabBlue,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${ad.businessType}${if (ad.city.isNotBlank()) " • ${ad.city}" else ""} • ${if (ad.isActive) "En défilement" else "En pause"}",
                                                    fontSize = 11.5.sp,
                                                    color = if (ad.isActive) KonabGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = ad.isActive,
                                                onCheckedChange = { checked ->
                                                    scope.launch {
                                                        val targetId = if (ad.isFallbackShopOnly) "AD-${ad.shopName.hashCode()}-${System.currentTimeMillis()}" else ad.id
                                                        val updated = ad.copy(
                                                            id = targetId,
                                                            isActive = checked,
                                                            isFallbackShopOnly = false,
                                                            updatedAt = System.currentTimeMillis()
                                                        )
                                                        syncManager.publishShopAd(updated)
                                                        savedAds = ShopAdStorage.getAllAds(context).filter { !it.isFallbackShopOnly }
                                                        partnerShopsRevision++
                                                        onPublished(updated)
                                                    }
                                                }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (ad.isActive) {
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            val targetId = if (ad.isFallbackShopOnly) "AD-${ad.shopName.hashCode()}-${System.currentTimeMillis()}" else ad.id
                                                            val promoted = ad.copy(
                                                                id = targetId,
                                                                isActive = true,
                                                                isFallbackShopOnly = false,
                                                                updatedAt = System.currentTimeMillis()
                                                            )
                                                            syncManager.publishShopAd(promoted)
                                                            savedAds = ShopAdStorage.getAllAds(context).filter { !it.isFallbackShopOnly }
                                                            partnerShopsRevision++
                                                            onPublished(promoted)
                                                            Toast.makeText(context, "« ${ad.shopName} » placé en tête du défilement.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("En tête", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            TextButton(
                                                onClick = { loadAdIntoEditor(ad, forceEnableVideoOption = !ad.hasVideo) }
                                            ) {
                                                Icon(
                                                    imageVector = if (ad.hasVideo) Icons.Default.Edit else Icons.Default.VideoCall,
                                                    contentDescription = null,
                                                    tint = KonabOrange,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (ad.hasVideo) "Modifier" else "Vidéo 60s",
                                                    color = KonabOrange,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (!ad.isFallbackShopOnly) {
                                                TextButton(
                                                    onClick = {
                                                        scope.launch {
                                                            syncManager.deleteShopAd(ad.id)
                                                            savedAds = ShopAdStorage.getAllAds(context).filter { !it.isFallbackShopOnly }
                                                            partnerShopsRevision++
                                                            onPublished(ad)
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = KonabRed, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Supprimer", color = KonabRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Tab 2 : Paramètres du défilement des logos & vidéos dans le modal d'accueil
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val activeCount = partnerShopEntries.count { it.isActive }
                        val videoCount = partnerShopEntries.count { it.isActive && it.hasVideo }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = KonabBlueContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(KonabBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ViewCarousel, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Modal d'accueil • Défilement actif",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "$activeCount boutique(s) partenaire(s) • $videoCount vidéo(s) promo 60s",
                                        fontSize = 12.sp,
                                        color = KonabBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Défilement automatique des logos", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text(
                                            text = "Fait défiler automatiquement les logos partenaires sur l'accueil",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = carouselSettings.autoScrollEnabled,
                                        onCheckedChange = { checked ->
                                            val updated = carouselSettings.copy(autoScrollEnabled = checked)
                                            carouselSettings = updated
                                            ShopAdStorage.saveCarouselSettings(context, updated)
                                            onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                        },
                                        modifier = Modifier.testTag("switch_ad_autoscroll")
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Vitesse de passage par boutique (${carouselSettings.scrollIntervalSeconds}s)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(listOf(3 to "Rapide (3s)", 4 to "Standard (4s)", 6 to "Confort (6s)", 8 to "Lecture (8s)")) { (sec, label) ->
                                            FilterChip(
                                                selected = carouselSettings.scrollIntervalSeconds == sec,
                                                enabled = carouselSettings.autoScrollEnabled,
                                                onClick = {
                                                    val updated = carouselSettings.copy(scrollIntervalSeconds = sec)
                                                    carouselSettings = updated
                                                    ShopAdStorage.saveCarouselSettings(context, updated)
                                                    onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                                },
                                                label = { Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Afficher toutes les boutiques partenaires", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text(
                                            text = "Inclut automatiquement le logo de chaque boutique inscrite",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = carouselSettings.includePartnerLogosInScroll,
                                        onCheckedChange = { checked ->
                                            val updated = carouselSettings.copy(includePartnerLogosInScroll = checked)
                                            carouselSettings = updated
                                            ShopAdStorage.saveCarouselSettings(context, updated)
                                            onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                        },
                                        modifier = Modifier.testTag("switch_include_partner_logos")
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Prioriser les vidéos promo 60s", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text(
                                            text = "Affiche d'abord les boutiques associées à une vidéo promotionnelle",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = carouselSettings.prioritizeVideoAds,
                                        onCheckedChange = { checked ->
                                            val updated = carouselSettings.copy(prioritizeVideoAds = checked)
                                            carouselSettings = updated
                                            ShopAdStorage.saveCarouselSettings(context, updated)
                                            onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                        },
                                        modifier = Modifier.testTag("switch_prioritize_video_ads")
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Aperçu vidéo 60s dans le cadre d'accueil", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text(
                                            text = "Lance un aperçu visuel muet de la vidéo directement dans le modal d'accueil",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = carouselSettings.autoPlayVideoPreviewInCarousel,
                                        onCheckedChange = { checked ->
                                            val updated = carouselSettings.copy(autoPlayVideoPreviewInCarousel = checked)
                                            carouselSettings = updated
                                            ShopAdStorage.saveCarouselSettings(context, updated)
                                            onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                        },
                                        modifier = Modifier.testTag("switch_autoplay_inline_video")
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                ShopAdStorage.saveCarouselSettings(context, carouselSettings)
                                onPublished(partnerShopEntries.firstOrNull() ?: ShopAdRecord(id = "SETTINGS", shopName = shopName))
                                Toast.makeText(context, "Réglages de défilement enregistrés !", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_save_scroll_settings")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Appliquer au modal d'accueil", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
