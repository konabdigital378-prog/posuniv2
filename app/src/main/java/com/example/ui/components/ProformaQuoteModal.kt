package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.KonabGreen
import com.example.ui.theme.KonabGreenDark
import com.example.ui.theme.KonabGreenLight
import com.example.ui.viewmodel.CartItem
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProformaQuoteModal(
    shopProfile: ShopProfileEntity?,
    cart: List<CartItem>,
    customer: CustomerEntity?,
    totalAmount: Long,
    discountAmount: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var customerNameInput by remember { mutableStateOf(customer?.name ?: "") }
    var customerPhoneInput by remember { mutableStateOf(customer?.phone ?: "") }
    var validityDays by remember { mutableStateOf("30") }

    val quoteDate = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }
    val quoteNumber = remember {
        val rand = (1000..9999).random()
        "PRO-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-$rand"
    }

    val generateQuoteText = {
        val shopName = shopProfile?.name ?: "Boutique Kaba"
        val phone = shopProfile?.phone ?: "65413799"
        val currency = shopProfile?.currency ?: "FCFA"

        val sb = StringBuilder()
        sb.append("====================================\n")
        sb.append("      FACTURE PROFORMA / DEVIS      \n")
        sb.append("====================================\n")
        sb.append("Émetteur : $shopName\n")
        sb.append("Téléphone : $phone\n")
        sb.append("N° Devis : $quoteNumber\n")
        sb.append("Date : $quoteDate (Valable $validityDays jours)\n")
        sb.append("Client : ${customerNameInput.ifBlank { "Client à préciser" }}\n")
        if (customerPhoneInput.isNotBlank()) sb.append("Contact : $customerPhoneInput\n")
        sb.append("------------------------------------\n")
        sb.append("ARTICLES :\n")
        cart.forEachIndexed { i, item ->
            sb.append("${i + 1}. ${item.product.name}\n")
            sb.append("   ${item.quantity} x ${CurrencyFormatter.formatFCFA(item.product.sellPrice)} = ${CurrencyFormatter.formatFCFA(item.totalPrice)}\n")
        }
        sb.append("------------------------------------\n")
        if (discountAmount > 0) {
            sb.append("Remise accordée : -${CurrencyFormatter.formatFCFA(discountAmount)}\n")
        }
        sb.append("TOTAL NET À PAYER : ${CurrencyFormatter.formatFCFA(totalAmount)}\n")
        sb.append("------------------------------------\n")
        sb.append("Conditions : Paiement comptant à la livraison ou Mobile Money.\n")
        sb.append("Merci de votre confiance !\n")
        sb.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("modal_proforma_quote"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0284C7))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Devis & Facture Proforma",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "N° $quoteNumber • $quoteDate",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Client Inputs
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Informations du destinataire :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(
                                value = customerNameInput,
                                onValueChange = { customerNameInput = it },
                                label = { Text("Nom du client ou entreprise") },
                                placeholder = { Text("Ex: Société SOGEFAS, M. Ouédraogo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_quote_customer_name")
                            )
                            OutlinedTextField(
                                value = customerPhoneInput,
                                onValueChange = { customerPhoneInput = it },
                                label = { Text("Téléphone WhatsApp (optionnel)") },
                                placeholder = { Text("65413799") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_quote_customer_phone")
                            )
                        }
                    }

                    // Proforma Paper Preview
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = shopProfile?.name ?: "Boutique Kaba",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "PROFORMA",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0284C7),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Client : ${customerNameInput.ifBlank { "Client comptant" }}",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            // Items Table
                            cart.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                        Text("${item.quantity} x ${CurrencyFormatter.formatFCFA(item.product.sellPrice)}", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                    Text(CurrencyFormatter.formatFCFA(item.totalPrice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL PROFORMA :", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                Text(
                                    CurrencyFormatter.formatFCFA(totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0284C7)
                                )
                            }
                            Text(
                                text = "Validité de l'offre : 30 jours à compter du $quoteDate",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Actions Footer
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = generateQuoteText()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Devis Proforma", text))
                                Toast.makeText(context, "Texte du devis copié !", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copier", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val text = generateQuoteText()
                                val cleanPhone = customerPhoneInput.replace(" ", "").replace("-", "")
                                val intlPhone = if (cleanPhone.length == 8) "226$cleanPhone" else cleanPhone.trimStart('+')
                                try {
                                    val encoded = java.net.URLEncoder.encode(text, "UTF-8")
                                    val uri = if (cleanPhone.isNotBlank()) {
                                        Uri.parse("https://wa.me/$intlPhone?text=$encoded")
                                    } else {
                                        Uri.parse("https://api.whatsapp.com/send?text=$encoded")
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Partager le devis proforma"))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f).height(48.dp).testTag("btn_share_quote_whatsapp")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Partager WhatsApp", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
