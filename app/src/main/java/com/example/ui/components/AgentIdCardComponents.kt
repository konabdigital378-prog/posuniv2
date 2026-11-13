package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.remote.supabase.AgentDeploymentStorage
import com.example.ui.screens.AgentDirectory
import com.example.ui.screens.AgentUser
import com.example.ui.theme.*
import com.example.util.AgentIdCardHelper
import com.example.util.QrCodeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the agent's uploaded profile photo or a crisp monogram fallback when none is set.
 */
@Composable
fun AgentPhotoAvatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    borderColor: Color = Color(0xFFFFA43A),
    showEditBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val baseModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Box(
        modifier = baseModifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            color = KonabOrange,
            border = BorderStroke(2.dp, borderColor),
            shadowElevation = 2.dp
        ) {
            if (!photoUri.isNullOrBlank()) {
                val bmp = remember(photoUri) {
                    AgentIdCardHelper.loadAgentPhotoBitmap(context, photoUri)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Photo de profil $name",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Photo de profil $name",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFF7900), Color(0xFFEA580C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "A" },
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = (size.value * 0.42f).sp
                    )
                }
            }
        }

        if (showEditBadge) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF06245E))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Changer la photo",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Reusable Photo Picker uploader card for Agent registration & profile editing
 * using the zero-permission Android Photo Picker.
 */
@Composable
fun AgentPhotoUploadRow(
    agentName: String,
    photoUri: String,
    onPhotoSelected: (String) -> Unit,
    agentKeyHint: String = "reg",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = AgentIdCardHelper.saveAgentPhotoToInternalStorage(context, uri, agentKeyHint)
            if (saved != null) {
                onPhotoSelected(saved)
                Toast.makeText(context, "Photo de profil ajoutée !", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Impossible de charger l'image sélectionnée.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openPicker = {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { openPicker() }
            .testTag("btn_upload_agent_photo"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 1.dp,
            color = if (photoUri.isNotBlank()) KonabGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AgentPhotoAvatar(
                name = agentName.ifBlank { "Agent" },
                photoUri = photoUri.ifBlank { null },
                size = 58.dp,
                showEditBadge = true
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (photoUri.isBlank()) "Photo de profil Agent" else "Photo de profil ajoutée ✓",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (photoUri.isNotBlank()) KonabGreen else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Affichée sur votre carte officielle KONAB POS et dans l'application",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }

            OutlinedButton(
                onClick = openPicker,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (photoUri.isBlank()) "Uploader" else "Changer",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Interactive ISO/IEC 7810 ID-1 (CR80: 85.60mm x 53.98mm) CONAPES Agent ID Card composable
 * displaying the CONAPES logo, Agent Photo, Full Name, Reference ID, Zone, Phone, and QR Code.
 */
@Composable
fun ConapesAgentIdCard(
    agent: AgentUser,
    modifier: Modifier = Modifier,
    onQrClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val effectivePhoto = remember(agent.id, agent.photoUri) {
        AgentDeploymentStorage.getEffectivePhotoUri(context, agent)
    }
    val qrPayload = remember(agent.id, agent.name, agent.phone, agent.zone) {
        AgentIdCardHelper.buildConapesQrPayload(agent)
    }
    val qrBitmap = remember(qrPayload) {
        QrCodeHelper.generateQrBitmap(qrPayload, sizePx = 280, margin = 2)
    }
    val issuedDate = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())
    }
    val refCode = remember(agent.id) {
        AgentIdCardHelper.getConapesReferenceCode(agent.id)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AgentIdCardHelper.CR80_ASPECT_RATIO),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.5.dp, Color(0xFF06245E).copy(alpha = 0.85f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Navy CONAPES
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF06245E), Color(0xFF073B9A))
                            )
                        )
                        .konabHeroShimmer(maxAlpha = 0.08f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(34.dp),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_konab_logo),
                                    contentDescription = "Logo KONAB POS",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Column {
                                Text(
                                    text = "KONAB POS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "CARTE AGENT DÉPLOYEUR • ISO CR80",
                                    color = Color(0xFFFDE68A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF10B981)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                KonabLivePulseDot(color = Color.White, size = 5.dp)
                                Text(
                                    text = "VALIDE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }

                // Tricolor institutional accent bar
                Row(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                    Box(modifier = Modifier.weight(0.45f).fillMaxHeight().background(Color(0xFFFF7900)))
                    Box(modifier = Modifier.weight(0.33f).fillMaxHeight().background(Color(0xFFF59E0B)))
                    Box(modifier = Modifier.weight(0.22f).fillMaxHeight().background(Color(0xFF10B981)))
                }

                // Body (Photo + Details + QR Code)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left: Photo + Reference ID
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AgentPhotoAvatar(
                            name = agent.name,
                            photoUri = effectivePhoto,
                            size = 64.dp,
                            shape = RoundedCornerShape(10.dp),
                            borderColor = Color(0xFF073B9A)
                        )
                        Text(
                            text = refCode,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF06245E),
                            maxLines = 1
                        )
                    }

                    // Middle: Identity Details
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "NOM COMPLET",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = agent.name.ifBlank { "Agent KONAB POS" }.uppercase(Locale.ROOT),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "FONCTION",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = agent.role.ifBlank { "Agent Déployeur" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF073B9A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "ZONE : ${agent.zone.ifBlank { "National" }}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (agent.phone.isNotBlank()) {
                            Text(
                                text = "TÉL : ${agent.phone}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )
                        }
                    }

                    // Right: Scannable Verification QR Code
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .let { mod -> if (onQrClick != null) mod.clickable { onQrClick() } else mod }
                    ) {
                        Column(
                            modifier = Modifier.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code KONAB POS ${agent.id}",
                                modifier = Modifier.size(72.dp)
                            )
                            Text(
                                text = "RÉF. QR",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF073B9A)
                            )
                        }
                    }
                }

                // Bottom Institutional Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF06245E))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Émise le $issuedDate • ${agent.id.uppercase(Locale.ROOT)}",
                            color = Color.White,
                            fontSize = 8.sp
                        )
                        Text(
                            text = "85,6 × 54 mm • Réseau KONAB POS",
                            color = Color(0xFFFDE68A),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full Modal displaying the newly generated or active CONAPES CR80 Agent Card,
 * with Download (PNG), Export PDF, and Print actions.
 */
@Composable
fun AgentIdCardModal(
    agent: AgentUser,
    isFreshlyRegistered: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveAgent = remember(agent) {
        val photo = AgentDeploymentStorage.getEffectivePhotoUri(context, agent) ?: ""
        agent.copy(photoUri = photo)
    }
    val refCode = remember(effectiveAgent.id) {
        AgentIdCardHelper.getConapesReferenceCode(effectiveAgent.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .safeDrawingPadding()
                .testTag("modal_conapes_agent_card"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(38.dp),
                            border = BorderStroke(1.5.dp, KonabOrange.copy(alpha = 0.7f))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_konab_logo),
                                contentDescription = "Logo KONAB POS",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = if (isFreshlyRegistered) "Carte Agent KONAB POS Générée !" else "Ma Carte Officielle KONAB POS",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Format standard ISO/IEC 7810 ID-1 (85,60 × 53,98 mm)",
                                fontSize = 11.sp,
                                color = KonabGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                // Live CR80 Standard Card
                ConapesAgentIdCard(
                    agent = effectiveAgent,
                    onQrClick = {
                        AgentFieldHelper.copyToClipboard(
                            context,
                            "Référence KONAB POS",
                            refCode,
                            "Référence $refCode copiée !"
                        )
                    }
                )

                // Informational verification strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KonabBlue.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Votre QR Code certifié ($refCode) vous référence officiellement dans l'application KONAB POS avec votre photo de profil.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Primary Actions: Download PNG + Print
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { AgentIdCardHelper.downloadIdCardPng(context, effectiveAgent) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF073B9A)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .konabPressScale()
                            .testTag("btn_download_conapes_card")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Télécharger", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { AgentIdCardHelper.printIdCard(context, effectiveAgent) },
                        colors = ButtonDefaults.buttonColors(containerColor = KonabOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .konabPressScale()
                            .testTag("btn_print_conapes_card")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Imprimer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Secondary Actions: Share PDF + Copy Reference
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { AgentIdCardHelper.shareIdCardPdf(context, effectiveAgent) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_share_conapes_card_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exporter PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            AgentFieldHelper.copyToClipboard(
                                context,
                                "Référence KONAB POS",
                                refCode,
                                "Référence $refCode copiée"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier Réf.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continuer vers mon profil", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Modal allowing merchants or agents to scan a CONAPES Agent QR card (or type a reference)
 * to reference & verify an agent inside the app along with their profile photo.
 */
@Composable
fun ConapesCardReferenceVerifierModal(
    onDismiss: () -> Unit,
    onAgentReferenced: ((AgentUser) -> Unit)? = null
) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var manualRefInput by remember { mutableStateOf("") }
    var verifiedAgent by remember { mutableStateOf<AgentUser?>(null) }
    var verificationValid by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf("") }

    fun resolveReference(rawCode: String) {
        val parsed = AgentIdCardHelper.parseAndVerifyConapesQr(rawCode)
        val cleanId = parsed?.agentId ?: rawCode.trim().uppercase(Locale.ROOT).removePrefix("KONAB-").removePrefix("CONAPES-")
        val knownAgents = AgentDeploymentStorage.getCloudAgents(context) + AgentDirectory.PRESET_AGENTS
        val matched = knownAgents.find { it.id.equals(cleanId, ignoreCase = true) }

        when {
            parsed != null && matched != null -> {
                val photo = AgentDeploymentStorage.getEffectivePhotoUri(context, matched) ?: ""
                verifiedAgent = matched.copy(photoUri = photo)
                verificationValid = parsed.isValidChecksum
                errorMessage = ""
            }
            parsed != null -> {
                val localPhoto = AgentDeploymentStorage.getAgentPhotoUri(context, parsed.agentId) ?: ""
                verifiedAgent = AgentUser(
                    id = parsed.agentId,
                    name = parsed.name.ifBlank { "Agent ${parsed.agentId}" },
                    phone = parsed.phone,
                    zone = parsed.zone.ifBlank { "National" },
                    pin = "",
                    role = "Agent Déployeur KONAB POS",
                    photoUri = localPhoto
                )
                verificationValid = parsed.isValidChecksum
                errorMessage = ""
            }
            matched != null -> {
                val photo = AgentDeploymentStorage.getEffectivePhotoUri(context, matched) ?: ""
                verifiedAgent = matched.copy(photoUri = photo)
                verificationValid = true
                errorMessage = ""
            }
            else -> {
                verifiedAgent = null
                verificationValid = false
                errorMessage = "Carte ou référence KONAB POS non reconnue (« $rawCode »)."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(30.dp),
                    border = BorderStroke(1.dp, KonabOrange.copy(alpha = 0.6f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_konab_logo),
                        contentDescription = "KONAB POS",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                    )
                }
                Text("Référencer / Vérifier Agent KONAB POS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Scannez le QR code d'une carte officielle KONAB POS ou saisissez son matricule pour vérifier son accréditation et l'identifier avec sa photo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { showScanner = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF073B9A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_scan_conapes_qr")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanner le QR Code de la Carte", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = manualRefInput,
                    onValueChange = { manualRefInput = it; errorMessage = "" },
                    label = { Text("Ou saisir Matricule / Réf. KONAB POS") },
                    placeholder = { Text("Ex: KONAB-AGT-101") },
                    trailingIcon = {
                        IconButton(onClick = { if (manualRefInput.isNotBlank()) resolveReference(manualRefInput) }) {
                            Icon(Icons.Default.Search, contentDescription = "Vérifier")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                verifiedAgent?.let { found ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (verificationValid == true) KonabGreen.copy(alpha = 0.12f) else KonabOrange.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (verificationValid == true) KonabGreen else KonabOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AgentPhotoAvatar(
                                    name = found.name,
                                    photoUri = found.photoUri.ifBlank { null },
                                    size = 56.dp,
                                    borderColor = KonabGreen
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            imageVector = if (verificationValid == true) Icons.Default.Verified else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (verificationValid == true) KonabGreen else KonabOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (verificationValid == true) "CARTE KONAB POS VALIDE" else "RÉFÉRENCE TROUVÉE",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (verificationValid == true) KonabGreen else KonabOrange
                                        )
                                    }
                                    Text(found.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${AgentIdCardHelper.getConapesReferenceCode(found.id)} • ${found.zone}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (found.phone.isNotBlank()) {
                                        Text(
                                            text = "Tél : ${found.phone}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            if (onAgentReferenced != null) {
                                Button(
                                    onClick = { onAgentReferenced(found) },
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Sélectionner / Référencer cet Agent", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )

    if (showScanner) {
        RealBarcodeScannerModal(
            onBarcodeScanned = { scanned ->
                showScanner = false
                manualRefInput = scanned
                resolveReference(scanned)
            },
            onDismiss = { showScanner = false }
        )
    }
}
