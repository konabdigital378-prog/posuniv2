package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import androidx.compose.ui.graphics.asImageBitmap
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.QrCodeHelper
import com.example.util.ReceiptPrinterHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptViewScreen(
    shopProfile: ShopProfileEntity?,
    sale: SaleEntity?,
    items: List<SaleItemEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showBluetoothPrinterModal by remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reçu de vente", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (shopProfile != null && sale != null) {
                        IconButton(
                            onClick = { showBluetoothPrinterModal = true },
                            modifier = Modifier.testTag("btn_receipt_bluetooth_printer")
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = "Imprimante Bluetooth", tint = KonabGreen)
                        }
                        IconButton(
                            onClick = {
                                ReceiptPrinterHelper.shareReceiptToWhatsApp(context, shopProfile, sale, items)
                            },
                            modifier = Modifier.testTag("btn_receipt_whatsapp")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Partager WhatsApp", tint = Color(0xFF25D366))
                        }
                        IconButton(
                            onClick = {
                                val pdf = ReceiptPrinterHelper.generateReceiptPdf(context, shopProfile, sale, items)
                                Toast.makeText(context, "Reçu exporté en PDF (${pdf.name})", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("btn_receipt_print")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Imprimer PDF", tint = KonabGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        if (sale == null || shopProfile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucune transaction sélectionnée", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Receipt Thermal Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, KonabBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Shop Header & Official Konab POS Logo
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 3.dp,
                            border = BorderStroke(2.dp, KonabBlueContainer),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_konab_logo),
                                contentDescription = "Logo Officiel KONAB POS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = shopProfile.name.ifBlank { "KONAB POS" },
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = KonabBlueDark,
                            textAlign = TextAlign.Center
                        )
                        if (shopProfile.slogan.isNotBlank()) {
                            Text(
                                text = shopProfile.slogan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = KonabOrange,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = shopProfile.address,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tél : ${shopProfile.phone}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFDDDDDD))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "REÇU DE VENTE",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "N° : ${sale.transactionNumber}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = CurrencyFormatter.formatDateTime(sale.timestamp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Client : ${sale.customerName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFDDDDDD))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Produit", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                            Text("Qté", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.5f))
                            Text("Prix", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                            Text("Total", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFEEEEEE))

                        // Items rows
                        items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.productName, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                Text("${item.quantity}", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(0.5f))
                                Text(CurrencyFormatter.formatNumber(item.unitPrice), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                                Text(CurrencyFormatter.formatNumber(item.totalPrice), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFDDDDDD))

                        if (sale.discountAmount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Sous-total", fontSize = 12.sp)
                                Text(CurrencyFormatter.formatFCFA(sale.subtotal), fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Réduction", fontSize = 12.sp, color = KonabRed)
                                Text("-${CurrencyFormatter.formatFCFA(sale.discountAmount)}", fontSize = 12.sp, color = KonabRed)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Highlighted Total Net Row
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL NET TTC", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = KonabTextPrimary)
                                Text(
                                    text = CurrencyFormatter.formatFCFA(sale.totalAmount),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 19.sp,
                                    color = KonabBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mode de règlement :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${sale.paymentMethod} (${sale.paymentStatus})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KonabTextPrimary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer Text
                        Text(
                            text = shopProfile.receiptFooter,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // QR Code Authentic Verification
                        val qrPayload = "KONAB-POS|TICKET:${sale.transactionNumber}|MONTANT:${sale.totalAmount}|DATE:${sale.timestamp}|BOUTIQUE:${shopProfile.name}"
                        val qrBitmap = remember(sale.transactionNumber) {
                            try {
                                QrCodeHelper.generateQrBitmap(qrPayload, sizePx = 300, margin = 2)
                            } catch (_: Exception) {
                                null
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code officiel du reçu de vente",
                                    modifier = Modifier
                                        .size(130.dp)
                                        .padding(4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scannez pour vérifier l'authenticité",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KonabTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Ticket N° : ${sale.transactionNumber}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KonabBlue,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Thermal Print Button
                Button(
                    onClick = { showBluetoothPrinterModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_receipt_print_thermal")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Imprimer Ticket Thermique (58/80mm)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            ReceiptPrinterHelper.shareReceiptToWhatsApp(context, shopProfile, sale, items)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_receipt_whatsapp_bottom")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdf = ReceiptPrinterHelper.generateReceiptPdf(context, shopProfile, sale, items)
                            ReceiptPrinterHelper.openReceiptPdf(context, pdf)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KonabBlue),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_receipt_pdf_bottom")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = KonabBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ouvrir PDF", color = KonabBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showBluetoothPrinterModal) {
        com.example.ui.components.BluetoothPrinterModal(
            shopProfile = shopProfile,
            sale = sale,
            items = items,
            onDismiss = { showBluetoothPrinterModal = false }
        )
    }
}
