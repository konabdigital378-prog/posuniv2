package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.entity.SaleEntity
import com.example.data.local.entity.SaleItemEntity
import com.example.data.local.entity.ShopProfileEntity
import com.example.ui.theme.KonabGreen
import com.example.util.BluetoothPrinterDevice
import com.example.util.BluetoothPrinterHelper
import com.example.util.PaperWidth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPrinterModal(
    shopProfile: ShopProfileEntity? = null,
    sale: SaleEntity? = null,
    items: List<SaleItemEntity> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var savedPrinter by remember { mutableStateOf(BluetoothPrinterHelper.getSavedPrinter(context)) }
    var selectedPaperWidth by remember { mutableStateOf(BluetoothPrinterHelper.getSavedPaperWidth(context)) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothPrinterDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<BluetoothPrinterDevice?>(savedPrinter) }

    var isPrinting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun refreshDevices() {
        val list = BluetoothPrinterHelper.getBondedPrinters(context)
        pairedDevices = list
        if (selectedDevice == null && list.isNotEmpty()) {
            selectedDevice = list.first()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val connectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }
        if (connectGranted) {
            refreshDevices()
        } else {
            statusMessage = "Permission Bluetooth requise pour communiquer avec l'imprimante."
            isSuccess = false
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasConnect = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (hasConnect) {
                refreshDevices()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        } else {
            refreshDevices()
        }
    }

    fun triggerPrint(testMode: Boolean) {
        val target = selectedDevice
        if (target == null) {
            statusMessage = "Veuillez sélectionner une imprimante Bluetooth dans la liste."
            isSuccess = false
            return
        }

        isPrinting = true
        statusMessage = "Envoi des données vers ${target.name}..."
        isSuccess = null

        coroutineScope.launch {
            BluetoothPrinterHelper.savePrinter(context, target)
            BluetoothPrinterHelper.savePaperWidth(context, selectedPaperWidth)

            val bytes = if (testMode || sale == null || shopProfile == null) {
                BluetoothPrinterHelper.buildTestReceiptBytes(selectedPaperWidth)
            } else {
                BluetoothPrinterHelper.buildSaleReceiptBytes(shopProfile, sale, items, selectedPaperWidth)
            }

            val result = BluetoothPrinterHelper.printBytes(context, target.address, bytes)
            isPrinting = false
            result.onSuccess {
                isSuccess = true
                statusMessage = if (testMode) {
                    "Ticket de test imprimé avec succès !"
                } else {
                    "Reçu imprimé avec succès sur ${target.name} !"
                }
                Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                isSuccess = false
                statusMessage = err.message ?: "Échec de l'impression Bluetooth"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(KonabGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = KonabGreen)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Imprimante Thermique Bluetooth", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Standard ESC/POS 58mm & 80mm", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                IconButton(
                    onClick = { refreshDevices() },
                    modifier = Modifier.testTag("btn_refresh_printers")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                }
            }

            // Paper Size Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Format du papier thermique", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PaperWidth.entries.forEach { pw ->
                        val isSelected = selectedPaperWidth == pw
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPaperWidth = pw
                                    BluetoothPrinterHelper.savePaperWidth(context, pw)
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) KonabGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, KonabGreen) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    pw.name.replace("WIDTH_", ""),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) KonabGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${pw.columns} colonnes",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Printer list
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Appareils Bluetooth associés (${pairedDevices.size})",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                if (pairedDevices.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Aucune imprimante Bluetooth trouvée",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Text(
                                "Appairez d'abord votre imprimante (POS-58, POS-80, etc.) dans les paramètres Bluetooth d'Android, puis cliquez sur actualiser.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pairedDevices.forEach { dev ->
                            val isSelected = selectedDevice?.address == dev.address
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) KonabGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) KonabGreen else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedDevice = dev }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isSelected) KonabGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(dev.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(dev.address, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedDevice = dev },
                                    colors = RadioButtonDefaults.colors(selectedColor = KonabGreen)
                                )
                            }
                        }
                    }
                }
            }

            // Status message
            if (statusMessage != null) {
                val isOk = isSuccess == true
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            statusMessage ?: "",
                            fontSize = 12.sp,
                            color = if (isOk) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { triggerPrint(testMode = true) },
                    enabled = !isPrinting && selectedDevice != null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_print_test_ticket"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ticket test", fontSize = 13.sp)
                }

                Button(
                    onClick = { triggerPrint(testMode = false) },
                    enabled = !isPrinting && selectedDevice != null,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("btn_print_receipt_bluetooth"),
                    colors = ButtonDefaults.buttonColors(containerColor = KonabGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Impression...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sale != null) "Imprimer Reçu" else "Imprimer", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
