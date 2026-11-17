package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.remote.supabase.RestaurantTableRecord
import com.example.ui.components.KonabEmptyState
import com.example.ui.theme.*
import com.example.util.QrCodeHelper
import com.example.util.RestaurantQrCardHelper
import com.example.util.ShopLogoAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RestaurantTablesQrTab(
    shopName: String,
    city: String,
    phone: String,
    logoUri: String? = null,
    cloudShopId: Long = 0L,
    tables: List<RestaurantTableRecord>,
    onSaveTable: (RestaurantTableRecord) -> Unit,
    onGenerateBatchTables: (Int, String) -> Unit,
    onDeleteTable: (String) -> Unit,
    onPreviewTableMenu: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddTableDialog by remember { mutableStateOf(false) }
    var posterPreviewTable by remember { mutableStateOf<RestaurantTableRecord?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("restaurant_tables_qr_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Info & Batch Action Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShopLogoAvatar(
                            logoUri = logoUri,
                            size = 42.dp,
                            shape = CircleShape,
                            contentDescription = "Logo $shopName"
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "QR Codes Tables Designer",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Affiches personnalisées avec logo du restaurant, N° de table et signature Konab POS.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddTableDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_add_single_table")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Nouvelle table", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { onGenerateBatchTables(4, "Salle Principale") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_generate_batch_tables")
                        ) {
                            Icon(Icons.Default.TableBar, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ 4 Tables", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (tables.isEmpty()) {
            item {
                KonabEmptyState(
                    icon = Icons.Default.QrCode2,
                    title = "Aucune table configurée",
                    subtitle = "Créez vos tables pour générer automatiquement leurs QR codes PNG prêts à être imprimés et posés.",
                    accentColor = Color(0xFFEA580C),
                    actionLabel = "Générer 4 tables",
                    onAction = { onGenerateBatchTables(4, "Salle Principale") }
                )
            }
        } else {
            items(tables, key = { it.id }) { table ->
                val effectiveTable = remember(table, cloudShopId) {
                    if (table.shopId <= 1L && cloudShopId > 1L) table.copy(shopId = cloudShopId) else table
                }
                val qrUri = remember(effectiveTable.id, effectiveTable.shopId, effectiveTable.tableNumber, shopName) {
                    effectiveTable.buildQrUri(shopName)
                }
                val qrThumb: Bitmap = remember(qrUri) {
                    QrCodeHelper.generateQrBitmap(qrUri, sizePx = 240, margin = 3)
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.28f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_table_qr_${table.tableNumber}")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Branded Header Strip with Restaurant Logo & Name + Prominent Table Pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF06245E), Color(0xFF073B9A))
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ShopLogoAvatar(
                                logoUri = logoUri,
                                size = 36.dp,
                                shape = CircleShape,
                                contentDescription = "Logo $shopName"
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shopName.ifBlank { "Mon Restaurant" },
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${table.zone} • ${table.seats} places",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDBEAFE),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFF7ED),
                                border = BorderStroke(1.5.dp, Color(0xFFFF7900))
                            ) {
                                Text(
                                    text = table.tableNumber.trim().ifBlank { "TABLE" }.uppercase(Locale.ROOT),
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF9A3412),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteTable(table.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_delete_table_${table.tableNumber}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Supprimer ${table.tableNumber}",
                                    tint = Color(0xFFFCA5A5)
                                )
                            }
                        }

                        // Thin Orange Brand Accent Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF7900), Color(0xFFFFA43A))
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(2.dp, Color(0xFFFF7900).copy(alpha = 0.7f)),
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(94.dp)
                                ) {
                                    Image(
                                        bitmap = qrThumb.asImageBitmap(),
                                        contentDescription = "QR Code ${table.tableNumber}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = table.tableNumber,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White,
                                            border = BorderStroke(1.dp, KonabBlue.copy(alpha = 0.25f)),
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_konab_logo),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Text(
                                            text = "KONAB POS • Menu Digital",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KonabBlue
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { posterPreviewTable = effectiveTable },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_preview_poster_${table.tableNumber}")
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Aperçu Affiche PNG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Action Bar: Télécharger PNG / Imprimer / Partager / Tester Menu
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val poster = withContext(Dispatchers.IO) {
                                                RestaurantQrCardHelper.buildTableQrCardBitmap(
                                                    shopName = shopName,
                                                    table = effectiveTable,
                                                    city = city,
                                                    phone = phone,
                                                    context = context,
                                                    logoUri = logoUri
                                                )
                                            }
                                            val res = withContext(Dispatchers.IO) {
                                                RestaurantQrCardHelper.saveTableQrPngToDevice(
                                                    context, poster, shopName, effectiveTable.tableNumber
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                res.getOrElse { it.localizedMessage ?: "Erreur d'enregistrement PNG" },
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_download_png_${table.tableNumber}")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PNG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val poster = withContext(Dispatchers.IO) {
                                                RestaurantQrCardHelper.buildTableQrCardBitmap(
                                                    shopName = shopName,
                                                    table = effectiveTable,
                                                    city = city,
                                                    phone = phone,
                                                    context = context,
                                                    logoUri = logoUri
                                                )
                                            }
                                            val res = RestaurantQrCardHelper.printTableQrCard(
                                                context, poster, shopName, effectiveTable.tableNumber
                                            )
                                            if (res.isFailure) {
                                                Toast.makeText(
                                                    context,
                                                    res.exceptionOrNull()?.localizedMessage ?: "Impression indisponible",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_print_qr_${table.tableNumber}")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tirer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val poster = withContext(Dispatchers.IO) {
                                                RestaurantQrCardHelper.buildTableQrCardBitmap(
                                                    shopName = shopName,
                                                    table = effectiveTable,
                                                    city = city,
                                                    phone = phone,
                                                    context = context,
                                                    logoUri = logoUri
                                                )
                                            }
                                            val res = RestaurantQrCardHelper.shareTableQrPng(
                                                context, poster, shopName, effectiveTable.tableNumber
                                            )
                                            if (res.isFailure) {
                                                Toast.makeText(context, "Partage indisponible", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(48.dp)
                                        .testTag("btn_share_qr_${table.tableNumber}")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Partager PNG", modifier = Modifier.size(16.dp))
                                }

                                OutlinedButton(
                                    onClick = { onPreviewTableMenu(table.tableNumber) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("btn_test_menu_${table.tableNumber}")
                                ) {
                                    Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Menu", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTableDialog) {
        AddRestaurantTableDialog(
            nextTableNumber = "Table ${tables.size + 1}",
            onSave = { table ->
                onSaveTable(table)
                showAddTableDialog = false
            },
            onDismiss = { showAddTableDialog = false }
        )
    }

    posterPreviewTable?.let { table ->
        TablePosterPreviewDialog(
            shopName = shopName,
            city = city,
            phone = phone,
            logoUri = logoUri,
            table = table,
            onDismiss = { posterPreviewTable = null }
        )
    }
}

@Composable
private fun TablePosterPreviewDialog(
    shopName: String,
    city: String,
    phone: String,
    logoUri: String?,
    table: RestaurantTableRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val qrUri = remember(shopName, table) { table.buildQrUri(shopName) }
    val posterBitmap by produceState<Bitmap?>(
        initialValue = null,
        shopName, city, phone, logoUri, table
    ) {
        value = withContext(Dispatchers.IO) {
            RestaurantQrCardHelper.buildTableQrCardBitmap(
                shopName = shopName,
                table = table,
                city = city,
                phone = phone,
                context = context,
                logoUri = logoUri
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .safeDrawingPadding()
                .testTag("dialog_table_poster_preview"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Carte QR • ${table.tableNumber}",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(900f / 1240f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = posterBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Affiche QR ${table.tableNumber}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        CircularProgressIndicator(color = KonabBlue)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = posterBitmap ?: return@Button
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    RestaurantQrCardHelper.saveTableQrPngToDevice(
                                        context, bmp, shopName, table.tableNumber
                                    )
                                }
                                Toast.makeText(
                                    context,
                                    res.getOrElse { it.localizedMessage ?: "Erreur" },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = posterBitmap != null,
                        colors = ButtonDefaults.buttonColors(containerColor = KonabBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Télécharger PNG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val bmp = posterBitmap ?: return@Button
                            RestaurantQrCardHelper.printTableQrCard(context, bmp, shopName, table.tableNumber)
                        },
                        enabled = posterBitmap != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Imprimer / Tirer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(qrUri))
                        Toast.makeText(context, "Lien QR copié : $qrUri", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_copy_table_qr_uri")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copier le lien QR (${table.tableNumber})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AddRestaurantTableDialog(
    nextTableNumber: String,
    onSave: (RestaurantTableRecord) -> Unit,
    onDismiss: () -> Unit
) {
    var tableNumber by remember { mutableStateOf(nextTableNumber) }
    var zone by remember { mutableStateOf("Salle Principale") }
    var seatsText by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle Table Restaurant", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = tableNumber,
                    onValueChange = { tableNumber = it },
                    label = { Text("Nom / N° Table *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("Zone (ex: Salle, Terrasse, VIP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = seatsText,
                    onValueChange = { seatsText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Nombre de places") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = tableNumber.trim().ifBlank { nextTableNumber }
                    onSave(
                        RestaurantTableRecord(
                            id = "",
                            tableNumber = cleanName,
                            zone = zone.trim().ifBlank { "Salle Principale" },
                            seats = (seatsText.toIntOrNull() ?: 4).coerceIn(1, 30)
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Text("Créer & Générer QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
