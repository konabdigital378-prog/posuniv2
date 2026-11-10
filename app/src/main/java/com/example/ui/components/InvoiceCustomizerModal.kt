package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ShopLogoAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCustomizerModal(
    shopProfile: ShopProfileEntity?,
    onSave: (ShopProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (shopProfile == null) return

    // Editable State
    var headerStyle by remember { mutableStateOf(shopProfile.invoiceHeaderStyle) }
    var paperSize by remember { mutableStateOf(shopProfile.invoicePaperSize) }
    var showLogo by remember { mutableStateOf(shopProfile.invoiceShowLogo) }
    var showCashier by remember { mutableStateOf(shopProfile.invoiceShowCashier) }
    var showQrCode by remember { mutableStateOf(shopProfile.invoiceShowQrCode) }
    var qrType by remember { mutableStateOf(shopProfile.invoiceQrType) }
    var qrCustomText by remember { mutableStateOf(shopProfile.invoiceQrCustomText) }
    var taxNumber by remember { mutableStateOf(shopProfile.invoiceTaxNumber) }
    var legalNotice by remember { mutableStateOf(shopProfile.invoiceLegalNotice) }
    var paymentDetails by remember {
        mutableStateOf(
            if (shopProfile.invoicePaymentDetails.isNotBlank()) shopProfile.invoicePaymentDetails
            else "Orange Money: ${shopProfile.phone} | Wave: ${shopProfile.phone}"
        )
    }
    var showPaymentDetails by remember { mutableStateOf(shopProfile.invoiceShowPaymentDetails) }
    var receiptFooter by remember { mutableStateOf(shopProfile.receiptFooter) }

    var selectedSection by remember { mutableStateOf(0) } // 0: Design & Format, 1: Coordonnées & Fiscalité, 2: Options & QR

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Personnalisation des Factures",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Surface(
                                color = KonabBlue,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Ajustez la présentation, les mentions légales et le format d'impression",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_invoice_modal")) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                HorizontalDivider()

                // Sub-tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        text = { Text("Style & Format", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        text = { Text("Fiscal & Paiement", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        text = { Text("Options & QR", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // LIVE PREVIEW ACCORDION CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, tint = KonabBlue, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Aperçu en Direct du Ticket",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = KonabBlueDark
                                    )
                                }
                                Surface(
                                    color = if (paperSize == "A4_INVOICE") Color(0xFFE0E7FF) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = when (paperSize) {
                                            "THERMAL_80" -> "Format 80mm"
                                            "A4_INVOICE" -> "Format Facture A4"
                                            else -> "Format 58mm (Mobile)"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (paperSize == "A4_INVOICE") Color(0xFF3730A3) else Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Miniature Simulated Receipt Container
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 1. Logo
                                    if (showLogo) {
                                        ShopLogoAvatar(
                                            logoUri = shopProfile.logoUri,
                                            size = 40.dp,
                                            shape = CircleShape
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    // 2. Header Style Rendering
                                    when (headerStyle) {
                                        "CADRE" -> {
                                            Surface(
                                                color = Color(0xFFEFF6FF),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(shopProfile.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 14.sp, color = KonabBlue)
                                                    if (shopProfile.slogan.isNotBlank()) Text(shopProfile.slogan, fontSize = 10.sp, color = KonabOrange)
                                                    Text("${shopProfile.address} • Tél: ${shopProfile.phone}", fontSize = 9.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                        "MINIMALISTE" -> {
                                            Text(shopProfile.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Tél: ${shopProfile.phone}", fontSize = 9.sp, color = Color.Gray)
                                        }
                                        "CLASSIQUE" -> {
                                            Text(shopProfile.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (shopProfile.slogan.isNotBlank()) Text(shopProfile.slogan, fontSize = 10.sp, color = Color.DarkGray)
                                            Text(shopProfile.address, fontSize = 9.sp, color = Color.Gray)
                                            Text("Tél : ${shopProfile.phone}", fontSize = 9.sp, color = Color.Gray)
                                        }
                                        else -> { // MODERNE
                                            Text(shopProfile.name.uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = KonabBlueDark)
                                            if (shopProfile.slogan.isNotBlank()) Text(shopProfile.slogan, fontSize = 10.sp, color = KonabOrange, fontWeight = FontWeight.Medium)
                                            Text("${shopProfile.address} • Tél: ${shopProfile.phone}", fontSize = 9.sp, color = Color.Gray)
                                        }
                                    }

                                    // Tax registration number if set
                                    if (taxNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("NIF / IFU / RCCM : $taxNumber", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Receipt Meta Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Ticket #20261109-01", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Text("Aujourd'hui, 14:30", fontSize = 9.sp, color = Color.Gray)
                                    }
                                    if (showCashier) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Client : Comptant", fontSize = 9.sp, color = Color.Gray)
                                            Text("Caissier : Agent Konab", fontSize = 9.sp, color = Color.Gray)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider(color = Color(0xFFEEEEEE))

                                    // Sample items
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Exemple Article 1 x2", fontSize = 9.sp)
                                        Text("5 000 FCFA", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Total Row
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("TOTAL NET", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = KonabTextPrimary)
                                            Text("5 000 FCFA", fontWeight = FontWeight.Black, fontSize = 11.sp, color = KonabBlue)
                                        }
                                    }

                                    // Payment Details Box if enabled
                                    if (showPaymentDetails && paymentDetails.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp)) {
                                                Text("Paiement Mobile Money accepté :", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                                Text(paymentDetails, fontSize = 8.sp, color = Color(0xFF475569))
                                            }
                                        }
                                    }

                                    // QR Code placeholder if enabled
                                    if (showQrCode) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = null,
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = when (qrType) {
                                                "WHATSAPP" -> "[ QR WhatsApp Boutique ]"
                                                "CUSTOM" -> "[ QR Lien Web ]"
                                                else -> "[ QR Authenticité Konab ]"
                                            },
                                            fontSize = 7.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // Legal & Footer
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (legalNotice.isNotBlank()) {
                                        Text(legalNotice, fontSize = 8.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                    }
                                    Text(receiptFooter, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    // TAB 0 : STYLE & FORMAT
                    if (selectedSection == 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Format de Papier d'Impression", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                                val paperOptions = listOf(
                                    Triple("THERMAL_58", "58 mm (Standard Mobile)", "Imprimantes Bluetooth thermiques de poche"),
                                    Triple("THERMAL_80", "80 mm (Grand format)", "Imprimantes caisses de comptoir grand format"),
                                    Triple("A4_INVOICE", "Facture A4 / Demi-A4", "Factures officielles et reçus PDF exportables")
                                )

                                paperOptions.forEach { (key, label, desc) ->
                                    val isSelected = paperSize == key
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, if (isSelected) KonabBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { paperSize = key }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(selected = isSelected, onClick = { paperSize = key })
                                            Column {
                                                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Text("Style de l'En-tête de la Boutique", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                                val styleOptions = listOf(
                                    Pair("MODERNE", "Moderne (Accent bleu & slogan mis en valeur)"),
                                    Pair("CADRE", "Encadré Officiel (Bordure élégante)"),
                                    Pair("CLASSIQUE", "Classique (Sobre, centré traditionnel)"),
                                    Pair("MINIMALISTE", "Minimaliste (Économie maximale de papier thermique)")
                                )

                                styleOptions.forEach { (key, label) ->
                                    val isSelected = headerStyle == key
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, if (isSelected) KonabBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { headerStyle = key }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(selected = isSelected, onClick = { headerStyle = key })
                                            Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1 : FISCAL & PAIEMENT
                    if (selectedSection == 1) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Mentions Fiscales & Administratives", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                                OutlinedTextField(
                                    value = taxNumber,
                                    onValueChange = { taxNumber = it },
                                    label = { Text("Numéro IFU / NIF / RCCM") },
                                    placeholder = { Text("Ex: IFU 0012345678A - RCCM BF-BBD-2024") },
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_invoice_tax_number")
                                )

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Afficher les Coordonnées de Paiement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Permet au client de payer rapidement par Orange Money, Moov Money ou Wave en voyant vos numéros sur le reçu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = showPaymentDetails,
                                        onCheckedChange = { showPaymentDetails = it },
                                        modifier = Modifier.testTag("switch_show_payment_details")
                                    )
                                }

                                if (showPaymentDetails) {
                                    OutlinedTextField(
                                        value = paymentDetails,
                                        onValueChange = { paymentDetails = it },
                                        label = { Text("Comptes Mobile Money & RIB") },
                                        placeholder = { Text("Ex: Orange Money: 65413799 | Moov: 70123456 | Wave: 65413799") },
                                        leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                                        minLines = 2,
                                        modifier = Modifier.fillMaxWidth().testTag("input_invoice_payment_details")
                                    )
                                }
                            }
                        }
                    }

                    // TAB 2 : OPTIONS & QR CODE
                    if (selectedSection == 2) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Éléments Visuels Visibles", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Afficher le Logo de la boutique", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Switch(checked = showLogo, onCheckedChange = { showLogo = it }, modifier = Modifier.testTag("switch_show_logo"))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Afficher le Nom du Caissier", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Switch(checked = showCashier, onCheckedChange = { showCashier = it }, modifier = Modifier.testTag("switch_show_cashier"))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Afficher le QR Code sur le ticket", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Switch(checked = showQrCode, onCheckedChange = { showQrCode = it }, modifier = Modifier.testTag("switch_show_qr"))
                                }

                                if (showQrCode) {
                                    HorizontalDivider()
                                    Text("Destination du QR Code :", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                                    val qrOptions = listOf(
                                        Pair("VERIFICATION", "Certificat d'authenticité & détails de vente Konab POS"),
                                        Pair("WHATSAPP", "Contact direct WhatsApp de la boutique (Service client)"),
                                        Pair("CUSTOM", "Lien personnalisé (Site web, catalogue ou réseau social)")
                                    )

                                    qrOptions.forEach { (key, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { qrType = key }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            RadioButton(selected = qrType == key, onClick = { qrType = key })
                                            Text(label, fontSize = 12.sp)
                                        }
                                    }

                                    if (qrType == "CUSTOM") {
                                        OutlinedTextField(
                                            value = qrCustomText,
                                            onValueChange = { qrCustomText = it },
                                            label = { Text("URL ou texte du QR Code") },
                                            placeholder = { Text("https://moncommerce.com") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("input_qr_custom_text")
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Text("Textes & Mentions de Bas de Reçu", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

                                OutlinedTextField(
                                    value = legalNotice,
                                    onValueChange = { legalNotice = it },
                                    label = { Text("Mentions légales de retour / garantie") },
                                    placeholder = { Text("Marchandises vendues ne sont ni reprises ni échangées") },
                                    singleLine = false,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("input_invoice_legal_notice")
                                )

                                OutlinedTextField(
                                    value = receiptFooter,
                                    onValueChange = { receiptFooter = it },
                                    label = { Text("Phrase de remerciement (Pied de page)") },
                                    placeholder = { Text("Merci pour votre confiance ! À très bientôt !") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_invoice_footer")
                                )
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Annuler")
                        }

                        Button(
                            onClick = {
                                val updated = shopProfile.copy(
                                    invoiceHeaderStyle = headerStyle,
                                    invoicePaperSize = paperSize,
                                    invoiceShowLogo = showLogo,
                                    invoiceShowCashier = showCashier,
                                    invoiceShowQrCode = showQrCode,
                                    invoiceQrType = qrType,
                                    invoiceQrCustomText = qrCustomText.trim(),
                                    invoiceTaxNumber = taxNumber.trim(),
                                    invoiceLegalNotice = legalNotice.trim(),
                                    invoicePaymentDetails = paymentDetails.trim(),
                                    invoiceShowPaymentDetails = showPaymentDetails,
                                    receiptFooter = receiptFooter.trim().ifBlank { "Merci pour votre confiance ! À très bientôt !" }
                                )
                                onSave(updated)
                                Toast.makeText(context, "Modèle de facture enregistré avec succès !", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("btn_save_invoice_customization"),
                            colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enregistrer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
