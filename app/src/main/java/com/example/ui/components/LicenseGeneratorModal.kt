package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.LicenseHelper
import com.example.util.QrCodeHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseGeneratorModal(
    onDismiss: () -> Unit,
    onDirectActivate: ((code: String, tier: String) -> Boolean)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Générateur, 1: Historique, 2: Lot (Batch)

    // Form inputs
    var selectedTier by remember { mutableStateOf("Professionnel") }
    var selectedDurationCode by remember { mutableStateOf("1A") }
    var beneficiaryInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }

    // Generated Key State
    var latestGeneratedKey by remember {
        mutableStateOf<LicenseHelper.LicenseKeyData?>(null)
    }

    // Saved keys history
    var savedKeys by remember {
        mutableStateOf(LicenseHelper.getSavedLicenses(context))
    }

    // Batch Generation State
    var batchCount by remember { mutableStateOf(5) }
    var batchGeneratedKeys by remember { mutableStateOf<List<LicenseHelper.LicenseKeyData>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("dialog_license_generator"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Surface(
                    color = KonabBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(KonabGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = KonabBlueDark)
                            }
                            Column {
                                Text(
                                    text = "Générateur de Clés de Licence",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Konab POS • Espace Super Administrateur",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = KonabBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Générer une clé", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            savedKeys = LicenseHelper.getSavedLicenses(context)
                            selectedTab = 1
                        },
                        text = { Text("Historique (${savedKeys.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Génération en lot", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Content Area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 0: Single License Generator
                            Text(
                                "Paramètres de la licence à émettre :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            // 1. Plan / Formule
                            Text("1. Formule de l'abonnement", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LicenseHelper.AVAILABLE_TIERS.forEach { tier ->
                                    val isSelected = selectedTier == tier
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedTier = tier },
                                        label = { Text(tier, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = KonabGreenLight,
                                            selectedLabelColor = KonabGreenDark
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 2. Durée
                            Text("2. Durée de validité", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LicenseHelper.AVAILABLE_DURATIONS.forEach { duration ->
                                    val isSelected = selectedDurationCode == duration.code
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) KonabBlueContainer else MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) KonabBlue else KonabBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedDurationCode = duration.code }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = { selectedDurationCode = duration.code },
                                                    colors = RadioButtonDefaults.colors(selectedColor = KonabBlue)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(duration.label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            }
                                            Surface(
                                                color = if (duration.code == "VIE") KonabGoldContainer else Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = duration.code,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (duration.code == "VIE") KonabGoldDark else Color.DarkGray,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Optional Beneficiary & Notes
                            Text("3. Informations client (optionnel)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = beneficiaryInput,
                                onValueChange = { beneficiaryInput = it },
                                label = { Text("Nom du commerce ou Téléphone du client") },
                                placeholder = { Text("Ex: Boutique Kaba ou +226 65413799") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = noteInput,
                                onValueChange = { noteInput = it },
                                label = { Text("Référence de paiement / Dépôt") },
                                placeholder = { Text("Ex: Dépôt Orange Money 65413799 validé") },
                                leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Action Button: Generate
                            Button(
                                onClick = {
                                    val newLicense = LicenseHelper.generateKey(
                                        tier = selectedTier,
                                        durationCode = selectedDurationCode,
                                        beneficiary = beneficiaryInput.trim(),
                                        note = noteInput.trim()
                                    )
                                    LicenseHelper.saveGeneratedLicense(context, newLicense)
                                    latestGeneratedKey = newLicense
                                    savedKeys = LicenseHelper.getSavedLicenses(context)
                                    Toast.makeText(context, "Clé de licence générée avec succès !", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_do_generate_license")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Générer la Clé Officielle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Display Generated Key Result Card
                            latestGeneratedKey?.let { license ->
                                GeneratedLicenseDisplayCard(
                                    license = license,
                                    onDirectActivate = onDirectActivate,
                                    onKeySaved = {
                                        savedKeys = LicenseHelper.getSavedLicenses(context)
                                    }
                                )
                            }
                        }

                        1 -> {
                            // TAB 1: History of Generated Keys
                            Text(
                                text = "Historique des clés de licence émises :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            if (savedKeys.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Aucune clé enregistrée", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                savedKeys.forEach { license ->
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = when (license.tier) {
                                                        "Entreprise" -> KonabBlueContainer
                                                        "Professionnel" -> KonabGreenLight
                                                        "Premium" -> KonabGoldContainer
                                                        else -> Color(0xFFF1F5F9)
                                                    },
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "${license.tier} • ${license.durationLabel}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when (license.tier) {
                                                            "Entreprise" -> KonabBlue
                                                            "Professionnel" -> KonabGreenDark
                                                            "Premium" -> KonabGoldDark
                                                            else -> Color.DarkGray
                                                        },
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Clé Konab", license.key))
                                                        Toast.makeText(context, "Clé copiée : ${license.key}", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = KonabBlue, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = license.key,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            if (license.beneficiary.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Bénéficiaire : ${license.beneficiary}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            if (license.note.isNotBlank()) {
                                                Text(
                                                    text = "Note : ${license.note}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Émise le ${CurrencyFormatter.formatDateTime(license.createdAt)}",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TAB 2: Batch Generation
                            Text(
                                "Génération de clés en lot (Revendeurs / Promotions) :",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            Text("Formule du lot :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LicenseHelper.AVAILABLE_TIERS.forEach { tier ->
                                    val isSelected = selectedTier == tier
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedTier = tier },
                                        label = { Text(tier, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text("Nombre de clés à générer :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(3, 5, 10, 20).forEach { count ->
                                    val isSelected = batchCount == count
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) KonabBlue else MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, KonabBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { batchCount = count }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$count clés",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val batch = LicenseHelper.generateBatch(
                                        tier = selectedTier,
                                        durationCode = selectedDurationCode,
                                        count = batchCount,
                                        beneficiaryPrefix = "Lot $selectedTier"
                                    )
                                    batch.forEach { LicenseHelper.saveGeneratedLicense(context, it) }
                                    batchGeneratedKeys = batch
                                    savedKeys = LicenseHelper.getSavedLicenses(context)
                                    Toast.makeText(context, "$batchCount clés générées !", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Layers, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Générer le lot de $batchCount clés", fontWeight = FontWeight.Bold)
                            }

                            if (batchGeneratedKeys.isNotEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Lot généré (${batchGeneratedKeys.size})", fontWeight = FontWeight.Bold)
                                            TextButton(
                                                onClick = {
                                                    val allText = batchGeneratedKeys.joinToString("\n") {
                                                        "${it.key} (${it.tier} - ${it.durationLabel})"
                                                    }
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Lot Clés Konab", allText))
                                                    Toast.makeText(context, "Lot entier copié !", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tout copier")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        batchGeneratedKeys.forEachIndexed { idx, it ->
                                            Text(
                                                text = "${idx + 1}. ${it.key}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
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
    }
}

@Composable
private fun GeneratedLicenseDisplayCard(
    license: LicenseHelper.LicenseKeyData,
    onDirectActivate: ((code: String, tier: String) -> Boolean)?,
    onKeySaved: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(license.key) {
        try {
            QrCodeHelper.generateQrBitmap(license.key, sizePx = 240, margin = 1)
        } catch (_: Exception) {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, KonabGreen, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = KonabGreenLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "CLÉ OFFICIELLE KONAB",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = KonabGreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${license.tier} • ${license.durationLabel}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = KonabBlue
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Prominent Key String
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = license.key,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = KonabBlueDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scannable QR Code
            if (qrBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(130.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Code QR de la Clé de Licence",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scannez ce QR Code pour activer directement",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy Button
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Clé Konab POS", license.key))
                        Toast.makeText(context, "Clé copiée dans le presse-papier !", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copier", fontSize = 12.sp)
                }

                // WhatsApp Share Button
                Button(
                    onClick = {
                        val message = """
                            *KONAB POS — Clé de licence officielle*
                            
                            Formule : *${license.tier}*
                            Durée : *${license.durationLabel}*
                            Bénéficiaire : *${license.beneficiary.ifBlank { "Client Konab" }}*
                            
                            Clé d'activation :
                            *${license.key}*
                            
                            Pour activer : Ouvrez Konab POS > Abonnements > "Activer avec une clé" et collez votre clé.
                            Support dépôt : 65413799
                        """.trimIndent()

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            setPackage("com.whatsapp")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message)
                            }, "Partager la clé de licence")
                            context.startActivity(chooser)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 12.sp)
                }
            }

            // Direct Activation Button (if applicable)
            if (onDirectActivate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val ok = onDirectActivate(license.key, license.tier)
                        if (ok) {
                            Toast.makeText(context, "Formule ${license.tier} activée sur cet appareil !", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Activer immédiatement sur cette caisse", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
