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
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
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
                        // Shop Header & Konab POS Circular Logo
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 2.dp,
                            border = BorderStroke(1.dp, KonabBorder),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_konab_logo),
                                contentDescription = "Logo KONAB POS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(2.dp)
                                    .fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = shopProfile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = KonabBlueDark
                        )
                        if (shopProfile.slogan.isNotBlank()) {
                            Text(
                                text = shopProfile.slogan,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                CurrencyFormatter.formatFCFA(sale.totalAmount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = KonabGreenDark
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mode de paiement :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sale.paymentMethod, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Barcode representation
                        Canvas(modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(30.dp)) {
                            val barPattern = intArrayOf(2, 1, 3, 1, 1, 4, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 4, 1, 2, 1)
                            var currentX = 0f
                            val h = size.height
                            for (w in barPattern) {
                                drawLine(
                                    color = Color.Black,
                                    start = androidx.compose.ui.geometry.Offset(currentX, 0f),
                                    end = androidx.compose.ui.geometry.Offset(currentX, h),
                                    strokeWidth = (w * 2).toFloat()
                                )
                                currentX += (w * 2 + 3).toFloat()
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sale.transactionNumber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showBluetoothPrinterModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_receipt_print_thermal")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Imprimer Ticket Thermique (58/80mm)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdf = ReceiptPrinterHelper.generateReceiptPdf(context, shopProfile, sale, items)
                            Toast.makeText(context, "Reçu PDF exporté: ${pdf.name}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_receipt_pdf_bottom")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = KonabGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", color = KonabGreen, fontWeight = FontWeight.Bold)
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
